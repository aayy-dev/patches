package dev.aayy.bwpatches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * Bitwarden Password Manager, Play Store build.
     *
     * Distributed on APKMirror as a split bundle (base APK + splits); Morphe merges it before
     * patching. This build keeps Bitwarden's native push, so vault changes sync without any extra
     * patch. Recommended base.
     */
    val COMPATIBILITY_BITWARDEN_PLAY = Compatibility(
        name = "Bitwarden",
        packageName = "com.x8bit.bitwarden",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x175DDC,
        targets = listOf(
            AppTarget(
                version = null,
                isExperimental = true,
            ),
            AppTarget(
                version = "2026.11.0",
            ),
        ),
    )

    /**
     * Bitwarden Password Manager, F-Droid build (single APK from Bitwarden's GitHub releases).
     *
     * No native push, so sync is periodic/manual. The passkey patch below was developed and
     * confirmed against this build (v2026.11.0).
     */
    val COMPATIBILITY_BITWARDEN_FDROID = Compatibility(
        name = "Bitwarden",
        packageName = "com.x8bit.bitwarden",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x175DDC,
        targets = listOf(
            AppTarget(
                version = null,
                isExperimental = true,
            ),
            AppTarget(
                version = "2026.11.0",
            ),
        ),
    )
}
