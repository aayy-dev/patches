package dev.aayy.bwpatches.passkey

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import dev.aayy.bwpatches.shared.Constants.COMPATIBILITY_BITWARDEN_FDROID
import dev.aayy.bwpatches.shared.Constants.COMPATIBILITY_BITWARDEN_PLAY

private const val PREFIX_HTTPS_METHOD = "prefixHttpsIfNecessary"

// The quote character literal Bitwarden wraps a package name in when scanning the allow list text.
private const val QUOTE_LITERAL = "\""

@Suppress("unused")
val trustAllBrowsersPatch = patch(
    name = "Trust all browsers for passkeys",
    description = "Removes Bitwarden's privileged-browser signature check entirely: every browser " +
        "is treated as trusted, with no prompt. Needed when the browser itself is re-signed (e.g. " +
        "Morphe-patched). This lowers a security check — any app that reaches the browser passkey " +
        "path is accepted — so only use it on a device you control.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_BITWARDEN_PLAY, COMPATIBILITY_BITWARDEN_FDROID)

    execute {
        // The privileged-app validator builds a success result as:
        //   new-instance <Success>; if-eqz <isVerifiedSource> ...; prefixHttpsIfNecessary(<rpId>);
        //   <Success>.<init>(origin); return
        // It is the only method that both references the quote literal (allow-list scan) and calls
        // prefixHttpsIfNecessary. Force it, at entry, to take the verified-success path for the
        // relying party, so no signature is ever checked.
        var patched = 0
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@forEach

                val hasQuoteLiteral = instructions.any {
                    it.opcode == Opcode.CONST_STRING &&
                        ((it as? ReferenceInstruction)?.reference as? StringReference)
                            ?.string == QUOTE_LITERAL
                }
                if (!hasQuoteLiteral) return@forEach

                val prefixIndex = instructions.indexOfFirst {
                    (it.opcode == Opcode.INVOKE_STATIC || it.opcode == Opcode.INVOKE_STATIC_RANGE) &&
                        ((it as? ReferenceInstruction)?.reference as? MethodReference)
                            ?.name == PREFIX_HTTPS_METHOD
                }
                if (prefixIndex == -1) return@forEach

                // Success object is created just before the prefix call.
                val newInstanceIndex = (prefixIndex - 1 downTo 0)
                    .firstOrNull { instructions[it].opcode == Opcode.NEW_INSTANCE }
                    ?: return@forEach

                // The isVerifiedSource flag gates the prefix call between those two points.
                val isVerifiedRegister = (prefixIndex - 1 downTo newInstanceIndex)
                    .firstOrNull { instructions[it].opcode == Opcode.IF_EQZ }
                    ?.let { (instructions[it] as OneRegisterInstruction).registerA }
                    ?: return@forEach

                val mutableMethod = mutableClassDefBy(classDef).methods.first {
                    it.name == method.name &&
                        it.parameterTypes == method.parameterTypes &&
                        it.returnType == method.returnType
                }

                // Force the verified-source branch, then jump straight to building the success
                // result for the relying party, skipping the package and signature checks.
                mutableMethod.addInstructionsWithLabels(
                    0,
                    """
                        const/16 v$isVerifiedRegister, 0x1
                        goto :bw_trust_all
                    """,
                    ExternalLabel("bw_trust_all", mutableMethod.getInstruction(newInstanceIndex)),
                )
                patched++
            }
        }

        if (patched == 0) {
            throw PatchException(
                "Could not locate Bitwarden's privileged-browser validator. Its passkey origin " +
                    "validation may have changed; this patch needs updating.",
            )
        }
    }
}
