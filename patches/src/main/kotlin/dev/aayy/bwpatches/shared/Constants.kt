package dev.aayy.bwpatches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    // Both entries declare a single version-agnostic target (version = null, not experimental).
    // Morphe only treats specific listed versions as "supported" and everything else as
    // unsupported/experimental; with no version listed it accepts any app version silently, so
    // there's no "unsupported version" / "experimental" prompt whatever Bitwarden build you feed it.

    /** Bitwarden Password Manager, Play Store build (split bundle on APKMirror). */
    val COMPATIBILITY_BITWARDEN_PLAY = Compatibility(
        name = "Bitwarden",
        packageName = "com.x8bit.bitwarden",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x175DDC,
        targets = listOf(
            AppTarget(version = null),
        ),
    )

    /** Bitwarden Password Manager, F-Droid build (single APK from Bitwarden's GitHub releases). */
    val COMPATIBILITY_BITWARDEN_FDROID = Compatibility(
        name = "Bitwarden",
        packageName = "com.x8bit.bitwarden",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x175DDC,
        targets = listOf(
            AppTarget(version = null),
        ),
    )
}
