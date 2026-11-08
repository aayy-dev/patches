package dev.aayy.bwpatches.passkey

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import dev.aayy.bwpatches.shared.Constants.COMPATIBILITY_BITWARDEN_FDROID
import dev.aayy.bwpatches.shared.Constants.COMPATIBILITY_BITWARDEN_PLAY

private const val R_STRING_CLASS = "Lcom/bitwarden/ui/R\$string;"
private const val SIGNATURE_MISMATCH_RESOURCE =
    "passkey_operation_failed_because_browser_signature_does_not_match"
private const val PREFIX_HTTPS_METHOD = "prefixHttpsIfNecessary"
private const val QUOTE_LITERAL = "\""
private const val INIT_METHOD = "<init>"

private fun methodDescriptor(reference: MethodReference) =
    "${reference.definingClass}->${reference.name}" +
        "(${reference.parameterTypes.joinToString("") { it.toString() }})${reference.returnType}"

private fun singletonFieldBefore(
    instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>,
    index: Int,
): FieldReference? {
    for (back in index - 1 downTo maxOf(0, index - 12)) {
        val instruction = instructions[back]
        if (instruction.opcode == Opcode.SGET_OBJECT && instruction is ReferenceInstruction) {
            val field = instruction.reference as? FieldReference ?: continue
            if (field.type == field.definingClass) return field
        }
    }
    return null
}

@Suppress("unused")
val trustAllBrowsersPatch = patch(
    name = "Trust all browsers for passkeys",
    description = "Removes Bitwarden's privileged-browser signature check: a browser whose signing " +
        "certificate isn't recognized (e.g. a Morphe-patched, re-signed browser) is accepted " +
        "instead of being rejected with \"signature does not match\", with no prompt. This lowers " +
        "a security check, so only use it on a device you control.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_BITWARDEN_PLAY, COMPATIBILITY_BITWARDEN_FDROID)

    execute {
        // Identify the "signature mismatch" error object via the error string it maps to.
        var signatureMismatchField: FieldReference? = null
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@forEach
                instructions.forEachIndexed { index, instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    if (reference is FieldReference &&
                        reference.definingClass == R_STRING_CLASS &&
                        reference.name == SIGNATURE_MISMATCH_RESOURCE
                    ) {
                        singletonFieldBefore(instructions, index)?.let { signatureMismatchField = it }
                    }
                }
            }
        }
        val signatureMismatch = signatureMismatchField ?: throw PatchException(
            "Could not identify the signature-mismatch result; passkey origin validation may have " +
                "changed.",
        )
        val signatureMismatchKey =
            "${signatureMismatch.definingClass}->${signatureMismatch.name}:${signatureMismatch.type}"

        // The privileged-app validator is the only method that both scans the allow list (quote
        // literal) and builds a success result (prefixHttpsIfNecessary + a constructor). On a
        // signature mismatch it throws, and the catch block returns the mismatch error. Rewrite that
        // catch block to build the same success result it produces for a matching browser, so a
        // re-signed browser is accepted. The method structure (and its try/catch) is left intact.
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

                val prefixReference = (instructions[prefixIndex] as ReferenceInstruction)
                    .reference as MethodReference
                val relyingPartyRegister = (instructions[prefixIndex] as Instruction35c).registerC

                val successType = (prefixIndex - 1 downTo 0)
                    .firstOrNull { instructions[it].opcode == Opcode.NEW_INSTANCE }
                    ?.let { (instructions[it] as ReferenceInstruction).reference.toString() }
                    ?: return@forEach

                val initReference = (prefixIndex until instructions.size)
                    .firstNotNullOfOrNull {
                        val candidate = (instructions[it] as? ReferenceInstruction)?.reference
                        (candidate as? MethodReference)?.takeIf {
                            it.name == INIT_METHOD && it.definingClass == successType
                        }
                    } ?: return@forEach

                // The catch block: load the mismatch error into a register, then return it.
                val mismatchIndex = instructions.indexOfFirst {
                    it.opcode == Opcode.SGET_OBJECT &&
                        it is ReferenceInstruction &&
                        (it.reference as? FieldReference)
                            ?.let { f -> "${f.definingClass}->${f.name}:${f.type}" } ==
                        signatureMismatchKey
                }
                if (mismatchIndex == -1) return@forEach
                val resultRegister =
                    (instructions[mismatchIndex] as OneRegisterInstruction).registerA

                val mutableMethod = mutableClassDefBy(classDef).methods.first {
                    it.name == method.name &&
                        it.parameterTypes == method.parameterTypes &&
                        it.returnType == method.returnType
                }
                // Remove the error load and insert a success result for the relying party; the
                // existing `return` right after then returns success.
                mutableMethod.removeInstruction(mismatchIndex)
                mutableMethod.addInstructions(
                    mismatchIndex,
                    """
                        new-instance v$resultRegister, $successType
                        invoke-static {v$relyingPartyRegister}, ${methodDescriptor(prefixReference)}
                        move-result-object v$relyingPartyRegister
                        invoke-direct {v$resultRegister, v$relyingPartyRegister}, ${methodDescriptor(initReference)}
                    """,
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
