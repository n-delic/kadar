# Contributing

## Setup

1. JDK 17 or newer (developed on JDK 25).
2. Android SDK with platform `android-37.0` and build-tools. With the command-line tools:
   ```bash
   sdkmanager "platforms;android-37.0" "platform-tools" "emulator"
   ```
3. Create `local.properties` (it is git-ignored) pointing at your SDK:
   ```
   sdk.dir=/home/<you>/Android/Sdk
   ```
4. An emulator or a phone with USB debugging. The reference AVD is `pixel_7_api35` (Pixel 7, API 35, **Google Play image**: background removal needs Play services).

Android Studio works too: open the project folder and let it sync.

## Build, test, run

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # install on the connected device/emulator
```

To get test media onto the emulator:

```bash
adb push clip.mp4 /sdcard/Movies/ && adb push song.mp3 /sdcard/Music/
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/clip.mp4
```

The first **Remove BG** on a device downloads ML Kit's model (a few MB) through Play services; after that it works offline.

## Releases

Debug builds install as a separate app, **Kadar Dev** (`com.ndelic.kadar.debug`), so testing never touches real projects. CI (`.github/workflows/ci.yml`) runs the unit tests, lint and a debug build on every push to `main` and every pull request.

To publish a release:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` (and `STAGE` when leaving alpha or beta; it shows on the home screen) and add the release to `ROADMAP.md`.
2. Commit, then tag with the same version: `git tag v0.5.0 && git push && git push --tags`.
3. `.github/workflows/release.yml` checks the tag matches `versionName`, builds a signed, R8-shrunk APK and publishes it with a SHA-256 file as a GitHub release.

The workflow signs with the repository secrets `KADAR_KEYSTORE_BASE64` (the `.jks`, base64) and `KADAR_KEYSTORE_PASSWORD`. If they're missing it fails rather than publish an unsigned APK.

### The signing key

Android only updates an app signed with the same key, so the key never changes. Losing it means every user has to uninstall (and lose their projects) to get updates. Locally it's described by `keystore.properties` (git-ignored):

```
storeFile=/home/<you>/.android/kadar-release.jks
storePassword=...
keyAlias=kadar
keyPassword=...
```

Create the key once:

```bash
keytool -genkeypair -keystore ~/.android/kadar-release.jks -storetype PKCS12 \
  -alias kadar -keyalg RSA -keysize 4096 -validity 36500
```

Only create a new key when starting a new app. Back up the `.jks` file and its password somewhere safe outside the repo. With `keystore.properties` in place, `./gradlew assembleRelease` writes a signed `app/build/outputs/apk/release/app-release.apk` you can install directly. To (re)load the CI secrets:

```bash
base64 -w0 ~/.android/kadar-release.jks | gh secret set KADAR_KEYSTORE_BASE64
gh secret set KADAR_KEYSTORE_PASSWORD   # paste the password
```

Release builds run R8. Libraries that create classes by reflection need keep rules in `app/proguard-rules.pro` (ML Kit does); always try background removal and a video export on a release build before tagging.

## Clean room and assets

Kadar is an independent implementation. Don't copy, decompile or study another editing app's code, and don't use its icons, images, fonts, stickers or presets. Every asset you add needs an open license, its license file in `third_party/`, and a row in `ATTRIBUTION.md`. Use CC0 or public-domain media in screenshots and tests.

By contributing you agree your contribution is licensed under the GPL-3.0-or-later, with the additional permission in `NOTICE`.

## Making a change

1. Read `AGENTS.md`, especially "Rules that matter". The drawing-path and project-safety rules are not optional.
2. Keep the change small and focused on one thing.
3. Put pure logic (math, parsing) in plain Kotlin and cover it with a test in `app/src/test`.
4. For UI, reuse the helpers in `ui/Panels.kt` and follow how the other panels are built.
5. Run the tests and the build, then try the change on an emulator, including an export.

### Changing the project format

Saved projects are the user's work, so format changes need care:

1. Change `Project.toJson` and `ProjectStore.load` together.
2. Read new fields with `opt*` and a default, so projects saved by older versions still open.
3. Bump `version` in `Project.toJson` if old readers would misread the new data.
4. Open a project saved before your change and confirm nothing is lost.

### Adding a dependency

Avoid it if the platform or an existing library already does the job. If you must, add it to `gradle/libs.versions.toml` with an exact stable version. Fonts and other assets must be under a license that allows redistribution; put the license in `third_party/`.

## Commits and pull requests

- Use Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `build:`.
- One logical change per commit.
- In the PR description, say what changed, why, and how you tested it. Add a screenshot for UI changes.
- Before opening a PR, `./gradlew testDebugUnitTest assembleDebug` must pass.

## Ideas and bugs

Planned work is in `ROADMAP.md`. Pick something from there or open an issue describing the problem first.
