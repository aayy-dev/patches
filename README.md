# Aayy Bitwarden Patches (Morphe)

Personal [Morphe](https://morphe.software) patches for the official Bitwarden Android app. Morphe
patches the official APK **on the device** (ReVanced-style), so there is no custom fork to install
and no build server to run — you patch the real Bitwarden release on your phone.

> Private repo. Patch bundles are built against the official release and signed by Morphe with your
> own on-device key.

## Patches

| Patch | Default | What it does |
|-------|---------|--------------|
| **No screen lock passkey** | On | Skips the master-password / PIN prompt for passkey sign-in and creation on a device with **no secure lock screen**. Devices with a PIN, pattern, password, or biometric lock are unchanged, and the per-item re-prompt still applies. |

The patch is name-agnostic: it finds Bitwarden's internal "user verified" flag by a kept string
resource, not by an obfuscated class name, so a Bitwarden update that only reshuffles obfuscated
names keeps working. If an update breaks it, the patch fails to apply (Morphe reports it) rather
than silently misbehaving.

## Use it from Morphe Manager (on your phone)

1. Install **Morphe Manager** from [morphe.software](https://morphe.software).
2. Because this repo is **private**, give Morphe a GitHub token so it can read it:
   **Settings → Advanced → GitHub token (PAT)** → paste a token with `repo` (read) scope.
3. **Sources → + → Remote**, paste:
   ```
   github.com/aayy-dev/bw-morphe-patches
   ```
   Add it. Morphe reads `patches-bundle.json` from `main` and downloads the bundle.
4. On the **Apps** screen choose Bitwarden (Expert mode → **Select other apps** if it isn't listed),
   provide the official APK when asked (download the Bitwarden APK from APKMirror, or let Morphe's
   download helper fetch it), pick the patches, and **Patch**.
5. Install the patched APK. Morphe signs it with your on-device key; keep that key backed up
   (**Settings → Signing key → Export**) so future re-patches install as updates.

### Which base APK?

- **Play Store build** (from APKMirror): keeps Bitwarden's native push, so vault changes sync
  almost instantly with no extra patch. Recommended.
- **F-Droid build** (from Bitwarden's GitHub releases): no push, so sync is periodic/manual. A
  future off-by-default live-sync patch will target this case.

Patching + re-signing does **not** break push on the Play build (FCM delivery depends on the
package name and embedded Firebase config, not the signing certificate).

## Sideloading a bundle without the repo

You can also test a locally built `.mpp` without adding the source:
**Sources → + → Local**, pick `patches-<version>.mpp`.

## Development

- Build the bundle: `./gradlew buildAndroid` (JDK 21). Needs read access to Morphe's GitHub
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

1. `./gradlew generatePatchesList`
2. Update `patches-bundle.json` `version` + `download_url`, and the version in `gradle.properties`.
3. Commit, tag `vX.Y.Z`, push.
4. Create a GitHub release `vX.Y.Z` and attach `patches/build/libs/patches-X.Y.Z.mpp`.

Morphe only needs a new release when the **patches** change or need to support a new Bitwarden
version — the APK itself is rebuilt on-device each time.

## License

GPLv3. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
