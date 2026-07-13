# Light SDK — local dev notes

## Prerequisites (this machine)

| Tool | Location / notes |
|------|------------------|
| JDK 17 | `/usr/local/opt/openjdk@17` — set `JAVA_HOME` to its `libexec/openjdk.jdk/Contents/Home` |
| Android Studio | `/Applications/Android Studio.app` |
| Android SDK | `/usr/local/share/android-commandlinetools` (`sdk.dir` in `local.properties`) |
| AVD | `LightPhoneIII` — 1080×1240 @ 420dpi, API 34 **`default;arm64-v8a` (AOSP `test-keys`)**. Do **not** use `google_apis` for LightOS system-app installs — those images use `dev-keys` and reject the AOSP platform signature. |

```bash
export JAVA_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/usr/local/share/android-commandlinetools"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

## GitHub Packages auth

`:sdk:ui` depends on `com.thelightphone.lp3keyboard:ui` from GitHub Packages. The default `gh` OAuth token often lacks `read:packages`, which causes `401 Unauthorized` on sync/build.

**Recommended:** create a classic PAT with `read:packages` (and `repo` if prompted), then either:

```bash
export GH_PACKAGES_USER=your_username
export GH_PACKAGES_TOKEN=your_pat_with_read_packages
./scripts/with-gh-packages.sh ./gradlew :tool:assembleDebug
```

or uncomment `gpr.user` / `gpr.key` in the gitignored `local.properties`.

`./scripts/with-gh-packages.sh` also falls back to `gh auth token` when those env vars are unset (works once `gh` has `read:packages`).

```bash
# one-time: add packages scope to your gh login
gh auth refresh -h github.com -s read:packages
```

## Common commands

```bash
# Build the scaffold tool
./scripts/with-gh-packages.sh ./gradlew :tool:assembleDebug

# Install on a running emulator/device
./scripts/with-gh-packages.sh ./gradlew :tool:installDebug
adb shell am start -n com.thelightphone.app/com.thelightphone.sdk.LightActivity

# UI kit demo
./scripts/with-gh-packages.sh ./gradlew :examples:ui-demo:installDebug

# Boot the LP3-sized AVD (use -writable-system for LightOS emulator system-app install)
emulator -avd LightPhoneIII -writable-system -no-audio -no-boot-anim
```

Confirm the image is AOSP test-keys before system-app work:

```bash
adb shell getprop ro.build.description
# must end with test-keys (not dev-keys / release-keys)
```

## LightOS emulator as system app

See [docs/system_app/README.md](docs/system_app/README.md). Summary: boot with `-writable-system` on an AOSP **`default`** image (`test-keys`), create `sdk/emulator/keys/platform.jks` from AOSP test keys (gitignored under `sdk/emulator/keys/`), `./gradlew :sdk:emulator:assembleDebug`, `adb disable-verity` + reboot, remount, push to `/system/priv-app/LightOSEmulator/`, reboot, verify `sharedUser=android.uid.system/1000`.

`tool/lighttool.toml` sets `serverPackage = "com.thelightphone.sdk.emulator"` for emulator testing. Use `com.lightos` only on real LP3 hardware.
