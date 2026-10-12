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

## Installing on family phones

Android only updates an app signed with the same key, so pick one key and keep it. Describe it in `keystore.properties` (git-ignored):

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

Back up the `.jks` file and its password. Then `./gradlew assembleRelease` writes `app/build/outputs/apk/release/app-release.apk`; send it to the phone and open it (allow "install unknown apps" for the file manager or messenger). Without `keystore.properties` the release APK is unsigned and won't install. Bump `versionCode` and `versionName` in `app/build.gradle.kts` for every release.

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
