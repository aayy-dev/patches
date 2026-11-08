package dev.aayy.bwpatches.nativealpha

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import dev.aayy.bwpatches.shared.Constants.COMPATIBILITY_NATIVE_ALPHA

private const val EXTENSION_CLASS = "Ldev/aayy/bwpatches/extension/AdaptiveShortcutIcon;"
private const val ICON_COMPAT = "Landroidx/core/graphics/drawable/IconCompat;"
private const val BITMAP = "Landroid/graphics/Bitmap;"

private const val DEFAULT_ICON_SIZE = 100

/**
 * `ShortcutDialogFragment.addShortcutToHomeScreen(Bitmap)` builds the pinned shortcut. When an
 * icon was picked (or a favicon fetched) it calls `IconCompat.createWithBitmap(bitmap)`.
 * Native Alpha's release builds are not minified, so these names are stable.
 */
internal object AddShortcutToHomeScreenFingerprint : Fingerprint(
    definingClass = "Lcom/cylonid/nativealpha/ShortcutDialogFragment;",
    name = "addShortcutToHomeScreen",
    returnType = "V",
    parameters = listOf(BITMAP),
    filters = listOf(
        methodCall(definingClass = ICON_COMPAT, name = "createWithBitmap"),
    ),
)

@Suppress("unused")
val keepShortcutIconShapePatch = patch(
    name = "Keep shortcut icon shape",
    description = "Makes home screen shortcuts created in Native Alpha keep the shape of the " +
        "picked icon image, including its transparent areas, instead of being forced into the " +
        "launcher's icon shape.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_NATIVE_ALPHA)

    extendWith("extensions/extension.mpe")

    val iconSize by intOption(
        key = "iconSize",
        default = DEFAULT_ICON_SIZE,
        title = "Icon size (%)",
        description = "100 is the largest size that no launcher icon shape can clip. Higher " +
            "makes the icon bigger, but round launcher shapes may then cut its corners. " +
            "50 to 150.",
        validator = { it == null || it in 50..150 },
    )

    execute {
        // 1. Bake the chosen size into the extension (its body is just "return 100").
        val size = iconSize ?: DEFAULT_ICON_SIZE
        mutableClassDefBy(EXTENSION_CLASS).methods.first { it.name == "sizePercent" }
            .addInstructions(
                0,
                """
                    const/16 v0, $size
                    return v0
                """,
            )

        // 2. Replace
        //      invoke-static {vB}, IconCompat->createWithBitmap(Bitmap)
        //    with
        //      invoke-static {vB}, AdaptiveShortcutIcon->toAdaptive(Bitmap)
        //      move-result-object vB
        //      invoke-static {vB}, IconCompat->createWithAdaptiveBitmap(Bitmap)
        //    The original move-result-object that follows is kept. vB only ever holds a Bitmap
        //    here, so overwriting it with the padded Bitmap keeps the method's types intact.
        AddShortcutToHomeScreenFingerprint.let {
            val match = it.instructionMatches.first()
            val register = when (val instruction = match.instruction) {
                is FiveRegisterInstruction -> instruction.registerC
                is RegisterRangeInstruction -> instruction.startRegister
                else -> throw PatchException(
                    "Unexpected call to IconCompat.createWithBitmap in Native Alpha's " +
                        "shortcut dialog; the patch needs updating.",
                )
            }
            val invoke = if (register < 16) {
                "invoke-static {v$register}"
            } else {
                "invoke-static/range {v$register .. v$register}"
            }

            it.method.replaceInstruction(
                match.index,
                "$invoke, $ICON_COMPAT->createWithAdaptiveBitmap($BITMAP)$ICON_COMPAT",
            )
            it.method.addInstructions(
                match.index,
                """
                    $invoke, $EXTENSION_CLASS->toAdaptive($BITMAP)$BITMAP
                    move-result-object v$register
                """,
            )
        }
    }
}
