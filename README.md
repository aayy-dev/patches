# Aayy Patches

Personal patches for use with [Morphe](https://morphe.software). Morphe patches an app's official APK
**on the device** (ReVanced-style), so there is no custom fork to install and no build server to run.
This is an independent project, not affiliated with the Morphe project.

Patched APKs are signed by Morphe with your own on-device key.

## Patches

### Bitwarden (`com.x8bit.bitwarden`)

| Patch | Default | What it does |
|-------|---------|--------------|
| **No screen lock passkey** | On | Skips the master-password / PIN prompt for passkey sign-in and creation on a device with **no secure lock screen**. Devices with a PIN, pattern, password, or biometric lock are unchanged, and the per-item re-prompt still applies. |

The patch is name-agnostic: it finds Bitwarden's internal "user verified" flag by a kept string
resource, not by an obfuscated class name, so a Bitwarden update that only reshuffles obfuscated
names keeps working. If an update breaks it, the patch fails to apply (Morphe reports it) rather
than silently misbehaving.

## Use it from Morphe Manager (on your phone)

1. Install **Morphe Manager** from [morphe.software](https://morphe.software).
2. On your phone, open
   [morphe.software/add-source?github=aayy-dev/patches](https://morphe.software/add-source?github=aayy-dev/patches)
   to add this source in one tap. Or add it by hand: **Sources → + → Remote**, paste:
   ```
   github.com/aayy-dev/patches
   ```
   Add it. Morphe reads `patches-bundle.json` from `main` and downloads the bundle.
3. On the **Apps** screen choose Bitwarden (Expert mode → **Select other apps** if it isn't listed),
   provide the official APK when asked (download the Bitwarden APK from APKMirror, or let Morphe's
   download helper fetch it), pick the patches, and **Patch**.
4. Install the patched APK. Morphe signs it with your on-device key; keep that key backed up
   (**Settings → Signing key → Export**) so future re-patches install as updates.

### Which base APK?

**Use the Play Store build** (from APKMirror — distributed as a split bundle, which Morphe merges
before patching). It keeps Bitwarden's native push, so vault changes sync almost instantly with no
extra patch. Patching + re-signing does **not** break push (FCM delivery depends on the package name
and embedded Firebase config, not the signing certificate).

The F-Droid build (single APK from Bitwarden's GitHub releases) also works, but it has no push, so
sync is periodic/manual. There is deliberately **no live-sync patch**: on the obfuscated app the push
path is stripped and the networking is unreachable from an extension, so a reliable live-sync patch
isn't feasible — the Play build's native push is the supported way to get instant sync.

## Sideloading a bundle without the repo

You can also test a locally built `.mpp` without adding the source:
**Sources → + → Local**, pick `patches-<version>.mpp`.

## Development

- Build the bundle: `./gradlew buildAndroid` (JDK 21). **Requires a `local.properties` with `sdk.dir=/path/to/Android/sdk`** — without it the build silently ships a bundle with no `classes.dex`, which Morphe Manager then loads as **0 patches** (the Desktop CLI still works, so it's easy to miss). Confirm the output `.mpp` contains `classes.dex` (`unzip -l patches/build/libs/patches-*.mpp | grep dex`). Needs read access to Morphe's GitHub
  Packages — set `gpr.user` / `gpr.key` (a GitHub PAT with `read:packages`) in a local, gitignored
  `gradle.properties`. Output: `patches/build/libs/patches-*.mpp`.
- Apply headlessly for testing: use the Morphe Desktop CLI jar (JDK 26+):
  ```bash
  java -jar morphe-desktop-*-all.jar patch \
    --patches patches/build/libs/patches-1.0.0.mpp \
    -e "No screen lock passkey" --exclusive -f \
    -o patched.apk  path/to/official-bitwarden.apk
  ```

### Cutting a release (manual)

CI (`.github/workflows/release.yml`, semantic-release) is present but disabled until its secrets are
set. Until then, releases are cut by hand:

1. `./gradlew generatePatchesList && ./gradlew buildAndroid` — run `buildAndroid` **last**, because
   `generatePatchesList` triggers a plain `build` that overwrites the `.mpp` with a non-dexed (JAR-only)
   one. Then confirm `unzip -l patches/build/libs/patches-*.mpp | grep classes.dex` shows the dex.
2. Update `patches-bundle.json`: `version`, `download_url`, and `created_at` (format
   `YYYY-MM-DDTHH:MM:SS`, UTC, **no trailing `Z`** — Morphe parses it as a local date-time and a
   `Z` makes the whole source fail to load). Also bump the version in `gradle.properties`.
3. Commit, tag `vX.Y.Z`, push.
4. Create a GitHub release `vX.Y.Z` and attach `patches/build/libs/patches-X.Y.Z.mpp`.

Morphe only needs a new release when the **patches** change or need to support a new Bitwarden
version — the APK itself is rebuilt on-device each time.

## License

GPLv3. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
