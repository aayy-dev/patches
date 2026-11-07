package dev.aayy.bwpatches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * Bitwarden Password Manager.
     *
     * The published GitHub release ships a single F-Droid APK (`com.x8bit.bitwarden-fdroid.apk`)
     * for `com.x8bit.bitwarden`. The exact target below is the version these patches were
     * developed and confirmed against; `version = null` keeps them applying to newer builds too.
     */
    val COMPATIBILITY_BITWARDEN = Compatibility(
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
