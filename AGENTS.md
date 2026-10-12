# AGENTS.md

Guide for AI coding agents (and humans) working in this repo.

## What this is

**Kadar**, a free, open-source (GPL-3.0-or-later) photo and video editor for Android, started for a meme page and released publicly. Everything runs on the phone and offline; the only network use is the in-app image browser (and a one-time download of ML Kit's background-removal model through Play services).

The owner's phone is a Galaxy S25; it also runs on older phones, hence `minSdk 29`.

## What it does today

- **Photo**: open a photo, blank canvas, or web image; add text (meme outline style), emoji, images and saved stickers as layers; move, pinch-resize, rotate; remove backgrounds on-device; change canvas aspect and background; export JPEG/PNG to `Pictures/Kadar`.
- **Video (NLE)**: one clip with trim, speed (0.25x to 4x), video volume, a music track (volume, start offset, loops to fit). Text/sticker layers sit on their own timeline tracks above the clip, each with a start and end time; export H.264/AAC MP4 to `Movies/Kadar`.
- **Undo / redo** for every edit (gestures and timeline drags count once per drag).
- **Snapping** to the canvas center and 0°/90°, caption boxes behind text, a Layers panel (photos only, top-bar button; video shows layers as timeline tracks), keep-pitch for speed changes.
- **Eraser / restore brush and crop** for image layers (`ui/ImageTools.kt`). `ImageLayer.source` + `sourceX/Y` remember the untouched original and where the layer's bitmap sits in it; keep them right in any new operation that resizes or crops a bitmap.
- **Settings** (`Settings.kt`, `ui/SettingsScreen.kt`): image search engine, default export size (per-project override in the Canvas panels), About with the bundled license texts (`third_party/` is packaged as app assets).
- **Share to Kadar**: `MainActivity` (singleTask) takes `ACTION_SEND` images and videos and calls `EditorViewModel.openShared`.
- **Projects**: every edit autosaves into the app's private storage and shows up on the home screen; reopen or long-press to delete.
- **Web images**: an in-app browser (Google Images by default). Long-press an image to download it into the project, never into the gallery.
- **My stickers**: cut-outs saved for reuse across projects.

## Stack

- Kotlin, Jetpack Compose with Material 3, one `:app` module
- AGP 9.4 (built-in Kotlin), Kotlin 2.4, Gradle 9.6.1 wrapper
- `compileSdk 37`, `targetSdk 35`, `minSdk 29`
- Media3 Transformer for video export and `CompositionPlayer` for the live preview (the same `Composition` drives both)
- ML Kit Subject Segmentation (Play services, on-device) for background removal
- `org.json` for project files, Coroutines/Flow for async
- No DI framework, no navigation library: `MainActivity` builds one `EditorViewModel` with `viewModel()` and switches screens on `uiState.project` plus a `browsing` flag
- **Not Material-looking**: Kadar's own dark UI with a lime accent from `ui/Theme.kt` (`Ink` colors, Plus Jakarta Sans, `IconTool`, `CircleButton`, `KSlider`, `ColorRow`, `TextTabs`, `KDialog`). Material 3 is only the plumbing underneath; don't use stock M3 buttons, chips or top bars in screens.
- Icons are Material Symbols Rounded (weight 300) vector drawables `res/drawable/ic_*.xml`, added with `scripts/add-icons.sh <name>...`

Versions live in `gradle/libs.versions.toml`.

## Layout

```
app/src/main/java/com/ndelic/kadar/
  Layers.kt           Transform (placement + hit test), Layer / ImageLayer / TextLayer, drawLayer, renderLayers, opaqueBounds
  Projects.kt         Project model, ProjectStore (one folder per project: project.json, thumb.jpg, images), JSON mapping
  Media.kt            Bitmap decode/download, video info and frames, gallery export (MediaStore), ML Kit cut-out
  VideoEdit.kt        VideoClip / Music, VideoClip.composition(), exportVideo() via Transformer
  EditorViewModel.kt  Single ViewModel: projects, layers, stickers, canvas, video settings, export, autosave
  MainActivity.kt     Theme, screen switching, snackbars (effects), busy overlay, share sheet
  ui/Theme.kt         Ink palette, fonts, shared components (IconTool, CircleButton, KSlider, ColorRow, TextTabs, KDialog)
  ui/HomeScreen.kt    Create tiles (video / photo / blank / web), project grid
  ui/EditorScreen.kt  Top bar (close, undo/redo, save, share), Stage (canvas, handles, video preview), tool row,
                      NLE Timeline (layer tracks + clip), TrimStrip, full-screen TextEntry
  ui/Panels.kt        Tool panels: stickers, canvas, speed, audio, font
  ui/WebImportScreen.kt  WebView browser with long-press-to-import
  ui/ImageTools.kt    EraseScreen (erase/restore brush) and CropScreen for image layers
app/src/main/res/font/anton.ttf          Meme font (SIL OFL, license in third_party/)
app/src/main/res/font/jakarta.ttf        UI font, Plus Jakarta Sans variable (SIL OFL)
scripts/add-icons.sh                     Fetches Material Symbols as vector drawables
app/src/test/.../LayersTest.kt           JVM tests for transform math, transparent-border trimming, timeline segments
```

## Commands

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # R8-shrunk APK, signed if keystore.properties exists
git tag v0.5.0 && git push --tags   # CI builds, signs and publishes the release (versionName must match)
./gradlew installDebug        # install on the running emulator/device
emulator -avd pixel_7_api35   # reference emulator (Google Play image, needed for ML Kit)
```

## Rules that matter

1. **One drawing path.** The stage, the photo export, the video overlay and the thumbnails all go through `drawLayer`/`renderLayers`. Never draw a layer type a second way, or exports stop matching the screen.
2. **Canvas pixels are the unit.** Layer transforms are in full-resolution canvas pixels (photo size or video display size). The stage only scales for display; gestures divide by that scale.
3. **Never lose a project.** Project writes go through `writeAtomic` (write then rename) under `saveLock`. A missing image drops only its layer, never the whole project. If you change the JSON format, bump `version` in `Project.toJson`, keep reading older files, and default new fields with `opt*`.
4. **Images live in the project folder.** Every bitmap a layer uses is written once with `ProjectStore.writeImage` and referenced by file name; `ProjectStore.cleanUp` removes unreferenced ones on close. Videos and music are referenced by content URI with a persisted read grant (`keepAccess`); only when that fails (a one-time share grant) is the video copied into the project as `video.mp4`.
5. **Nothing goes to the gallery except an export.** Web downloads, cut-outs and stickers stay in app storage. Exports go to `Pictures/Kadar` or `Movies/Kadar` through `saveToGallery` (no storage permission needed).
6. **Offline first.** No analytics, no accounts, no cloud APIs. The browser is the only feature that needs internet; don't add features that silently require it.
7. **Treat web input as untrusted.** `downloadImage` accepts only `http(s)` and base64 `data:` URLs, caps the size, and must fail with a message, not a crash.
8. **The preview is the export.** Video settings change `VideoClip`; `VideoClip.composition()` builds both the `CompositionPlayer` preview and the Transformer export. Add new video features there, not in only one of them.
9. **Layer times are output time** (after trim and speed), the same clock as `CompositionPlayer.currentPosition`. `endMs = Long.MAX_VALUE` means "to the end". The stage shows only layers visible at the playhead (plus the selected one); the export uses `timedOverlay`, which sits on the composition (not the clip) so it sees output timestamps.
10. **Every edit is undoable.** Change the project only through `updateProject`/`updateLayer`; continuous gestures pass `record = first` so one drag is one undo step.
11. **Long tasks go through `EditorViewModel.work`.** It shows the busy overlay, runs one task at a time, and turns exceptions into a snackbar. Transformer must be called on the main thread.
12. **Match existing patterns.** State is one `StateFlow<EditorUiState>`, one-shot messages go through the `effects` channel, panels are composables in `ui/Panels.kt` that call the ViewModel directly. Reuse the components in `ui/Theme.kt` before writing new widgets.
13. **Edge drags need `systemGestureExclusion()`**, or the system back gesture steals them (the timeline does this).
14. **Clean room, open assets.** Never copy, decompile or look at another editing app's code, and never take its icons, images, fonts, stickers, presets or distinctive UI design; build features from public descriptions of what they do. Every bundled asset must be openly licensed (OFL, CC0, Apache/MIT, own work) and get a row in `ATTRIBUTION.md` plus its license file in `third_party/`. Don't name other apps in the UI or store text.
15. **Known patents to stay clear of** (checked 2026-10-12, assignee search, not a legal opinion): US11989808B2 covers recording a creator's edit steps as a "replay" others apply to their own photo; read its claims before building anything like edit-sharing templates. USD1056949S1 is a design patent on a staggered two-column card feed with a five-tab bottom bar and a central round "+"; don't build the home screen that way.
16. **Smallest change that fully works.** No new dependencies, layers or abstractions without a clear need. UI text is English and inline for now (no translation yet).

## Verifying a change

1. `./gradlew testDebugUnitTest assembleDebug` passes.
2. Install on the emulator and try the affected flow end to end, including **Export** and checking the file (`adb pull` it).
3. For project-format changes: open an existing project from before the change and confirm nothing is lost.
4. For video changes: export, then check the result with `ffprobe` (duration, rotation) and look at a frame.
