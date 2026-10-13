# AGENTS.md

Guide for AI coding agents (and humans) working in this repo.

## What this is

**Kadar**, a free, open-source (GPL-3.0-or-later) photo and video editor for Android, started for a meme page and released publicly. Everything runs on the phone and offline; the only network use is the in-app image browser (and a one-time download of ML Kit's background-removal model through Play services).

The owner's phone is a Galaxy S25; it also runs on older phones, hence `minSdk 29`.

## What it does today

- **Photo**: open a photo, blank canvas, or web image; add text (meme outline style), emoji, images and saved stickers as layers; move, pinch-resize, rotate; remove backgrounds on-device; change canvas aspect and background; export JPEG/PNG to `Pictures/Kadar`.
- **Video (NLE)**: one clip with trim, speed (0.25x to 4x), video volume, a music track (volume, start offset, loops to fit, fade in/out over 1.5 s: `video/MusicFade.kt`), and voiceovers recorded from the playhead in the Audio panel (`ui/editor/VoiceoverPanel.kt`; `VideoClip.voices`, each its own audio-only sequence). Text/sticker layers sit on their own timeline tracks above the clip, each with a start and end time, an eye and a lock at its left, and long-press-drag to reorder. Pinch the timeline to zoom, slide two fingers to scroll (view-only, `TimelineViewport`); one finger always edits or scrubs. Video layers (picture-in-picture) put more clips on top, placed like stickers, stacked with text and stickers in track order (a caption under a video layer's track is covered by it), with their own track, opacity, volume, in-point (`clipStartMs`, the Edit panel `ui/editor/VideoLayerPanel.kt`) and crop (`VideoLayer.crop`, a `CropRect` of the frame picked in the shared `CropScreen`; the layer's size is the cropped size, the export adds a Media3 `Crop` before its `Presentation`, the preview clips a full-frame `TextureView` to the layer box); export H.264/AAC MP4 to `Movies/Kadar`.
- **Animations** (`model/Animation.kt`, `ui/editor/AnimationPanel.kt`): video text, sticker and shape layers get an entrance and an exit (None, Fade, Pop, Slide up), 300 ms each, at most half the layer's time, and a loop (`Loop`: Wiggle, Pulse, Bounce) all the time they show. Text also gets per-letter entrances (`Anim.perLetter`: Typewriter, Letters pop, 1.2 s): the `Motion` carries a `Reveal`, `letterScale` says how big each letter draws, and `TextLayer.drawContent(canvas, reveal)` draws the letters one by one at the layout's positions, so it stays the one text drawing path. `motionAt` turns a layer and an output time into alpha, scale, offset, rotation and reveal, and `Canvas.drawLayer(layer, motion, canvasHeight)` applies it around `drawLayer`; the stage uses it with the player position (every frame while playing), the export's `timedOverlay` with each frame's timestamp (a looping layer's stretch is drawn per frame all through).
- **Text look** (`TextLayer`): glow (`glow` color, `glowSize`, `ui/editor/GlowPanel.kt`), opacity (the whole layer through one offscreen layer), `lineSpacing` and `wrap` (where the text wraps, in layer pixels; null keeps the lines as typed). The side handles of a selected text drag its wrap with the other edge kept in place (`TextLayer.wrapDragged`). Presets (`model/TextPresets.kt`, `ui/editor/TextPresetsPanel.kt`, tiles drawn with `drawLayer`) set font, colors, box/bubble, glow, shadow and spacing in one undo step.
- **Imported fonts**: Font > Add font copies a picked `.ttf`/`.otf` into `filesDir/fonts` (`media/Media.kt` `importFont`: size-capped, kept only if `loadFont` can load it), listed after the bundled fonts in `EditorUiState.fonts` and used in every project, like stickers. Projects store the font name (the file name); a missing one falls back to the default font.
- **Undo / redo** for every edit (gestures and timeline drags count once per drag).
- **Snapping** to the canvas center and 0°/90°, caption boxes behind text, a Layers panel (photos only, top-bar button; video shows layers as timeline tracks), keep-pitch for speed changes.
- **Speech bubbles** (`ui/editor/BubblePanel.kt`, geometry in `model/Shapes.kt`): a text layer's box has a `Bubble` style (the caption `Box`, or `Speech`, `Thought`, `Shout`) with a fill (`box`) and an outline (`boxOutline`). Bubbles wrap the text (`bubbleWrapWidth`) into an ellipse, and their tail points at `TextLayer.tail` (layer pixels from its center; null is the default spot), dragged with a third handle on the stage (`Transform.toLocal`). Body and tail are merged into one `Path` (`Path.op`), so the outline never crosses the inside. `Layer.reach` covers the tail, so a fade's offscreen layer doesn't clip it.
- **Shapes** (`ShapeLayer` in `model/Layers.kt`, points in `model/Shapes.kt`, `ui/editor/ShapePanel.kt`): rectangle, rounded rectangle, ellipse, arrow, star and line, `w`×`h` in their own pixels, with a fill and an outline (either none), an outline width and an opacity (one offscreen layer, so the outline doesn't show through the fill). The Shape tool adds one (`newShape`); with a shape selected it changes the kind, the stretch (width to height) and the opacity.
- **Drawing** (`model/Strokes.kt`, `ui/editor/DrawScreen.kt`): the Draw tool opens a full-screen pad (one finger draws, two zoom/pan) with marker and highlighter pens, colors, size and undo-last-stroke. Apply makes one `DrawLayer` (strokes in its own pixels, centered on its origin by `withStrokes`, smoothed by `smoothSegments`); Draw on a selected drawing adds strokes to it.
- **Filters** (`model/Looks.kt`, `ui/editor/FiltersPanel.kt`): in-house looks as 4x5 color matrices, blended by a strength and stored on `Adjust` (`look`, `amount`). `Adjust.filterMatrix()` (look, then the sliders) is the one matrix for the image layer paint and, through `glRgbMatrix`, the video's Media3 `RgbMatrix`, so photos and video match.
- **Eraser / restore brush and crop** for image layers (`ui/editor/ImageTools.kt`). `ImageLayer.source` + `sourceX/Y` remember the untouched original and where the layer's bitmap sits in it; keep them right in any new operation that resizes or crops a bitmap.
- **Settings** (`model/Settings.kt`, `ui/home/SettingsScreen.kt`): image search engine, default export size (per-project override in the Canvas panels), video export quality (Standard is Media3's bitrate, High is `highBitrate` in `video/VideoEdit.kt`), the home grid's sort order, About with the bundled license texts (`third_party/` is packaged as app assets).
- **Share to Kadar**: `MainActivity` (singleTask) takes `ACTION_SEND` images and videos and calls `EditorViewModel.openShared`.
- **Projects**: every edit autosaves into the app's private storage and shows up on the home screen; reopen, or long-press to select and duplicate, rename or delete. Each has an optional `name` in `project.json` (unnamed ones show "Photo · Oct 12" from the type and the folder's creation timestamp); `ProjectStore.rename` edits only that field and keeps the file's modified time. The grid sorts by Recent / Name / Type (videos first), remembered in `AppSettings.projectSort`, and gets a search field past 6 projects; the filtering and sorting is `model/ProjectList.kt`.
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
- **Not Material-looking**: Kadar's own dark UI with a lime accent from `ui/common/Theme.kt` (`Ink` colors, Plus Jakarta Sans, `IconTool`, `CircleButton`, `KSlider`, `ColorRow`, `TextTabs`, `KDialog`, `KTextField`). Material 3 is only the plumbing underneath; don't use stock M3 buttons, chips or top bars in screens.
- Icons are Material Symbols Rounded (weight 300) vector drawables `res/drawable/ic_*.xml`, added with `scripts/add-icons.sh <name>...`

Versions live in `gradle/libs.versions.toml`.

## Layout

```
app/src/main/java/com/ndelic/kadar/
  MainActivity.kt          Screen switching (home / editor / browser / settings), snackbars, busy overlay, share sheet
  EditorViewModel.kt       The one ViewModel: projects, layers, stickers, canvas, video, settings, export, autosave, undo
  model/                   Data and pure logic, no UI
    Layers.kt              Transform (placement + hit test), Layer / ImageLayer / TextLayer / VideoLayer / DrawLayer / ShapeLayer,
                           Bubble, ShapeKind, bubble paths, Adjust, drawLayer, renderLayers, opaqueBounds
    Shapes.kt              Shape and bubble geometry: star and arrow points, bubble wrap width, tail, thought trail, cloud bumps, spikes
    Looks.kt               Filters: Look matrices, Adjust.filterMatrix(), glRgbMatrix() for Media3
    Animation.kt           Animations: Anim (in/out, per-letter), Loop, motionAt() (alpha/scale/offset/rotation/reveal at a time),
                           letterScale, drawLayer with a Motion
    TextPresets.kt         Text style presets (TextPreset) and TextLayer.styled
    Strokes.kt             Freehand strokes: PenStroke, smoothing, bounds, DrawLayer.withStrokes (re-centering)
    Projects.kt            Project, ProjectStore (one folder per project: project.json, thumb.jpg, images, voice .m4a), JSON
    ProjectList.kt         Home grid logic: default project label, search filter, ProjectSort
    Settings.kt            AppSettings (search engine, export size, export quality, project sort), SettingsStore
    UiText.kt              UiText (string resource, plural or raw text, resolved by the UI) and UserError
  media/
    Media.kt               Bitmap decode/download, emoji render, video info and frames, gallery export, ML Kit cut-out, font import
  video/
    VideoEdit.kt           VideoClip / Music, VideoClip.composition() (preview + export), exportVideo()
    VideoOverlay.kt        Pure math for video layers: Transform and crop to compositor coordinates, when a layer plays, alpha, frame rate,
                           source time at an output time and when the preview must resync, drawnGroups (stacking around video layers)
    Unpremultiply.kt       GL effect that divides color by alpha, for layers drawn into a compositor input of their own
    MusicFade.kt           Music fade timing: fade gain at an output time, the song's repeats, fade windows per repeat
  ui/common/
    Theme.kt               Ink palette, fonts, shared components (IconTool, CircleButton, KSlider, ColorRow, TextTabs,
                           KToggle, KDialog, KTextField, PanelLabel)
  ui/home/
    HomeScreen.kt          Start cards, project grid (names, sort, search), multi-select bar
    RenameDialog.kt        Project name dialog
    Intro.kt               Launch animation
    SettingsScreen.kt      Settings and About (license texts)
  ui/editor/
    EditorScreen.kt        Top bar, Stage (canvas, gestures, handles, view zoom, video preview), tool row, TextEntry
    Timeline.kt            NLE timeline (layer tracks + clip) and TrimStrip
    TimelineMath.kt        TimelineViewport (zoom/scroll: time to x, pinch, follow the playhead), ruler steps, filmstrip frames
    Panels.kt              Tool panels: stickers, canvas, speed, audio, font (with Add font), style, adjust, layers
    FiltersPanel.kt        Look tiles and strength slider
    AnimationPanel.kt      In, Out and Loop animation choices for a video text/sticker/shape layer
    BubblePanel.kt         Box or bubble behind the selected text: style, fill and outline colors
    GlowPanel.kt           Glow color and size of the selected text
    TextPresetsPanel.kt    Text preset tiles, each a sample drawn in its look
    ShapePanel.kt          Shape kinds (adds one, or changes the selected), stretch, opacity, fill and outline panels
    VideoLayerPanel.kt     Opacity, sound and "Starts at" (in-point) of the selected video layer
    VideoLayerPreview.kt   A video layer on the stage: its own ExoPlayer in a TextureView, placed like the layer, synced to the main preview
    BlurBackgroundPreview.kt  The blurred canvas on the stage: the clip again in its own muted ExoPlayer, blurred by the view
    VoiceoverPanel.kt      Audio panel's voiceover part: record from the playhead (preview muted), list, volume, delete
    ImageTools.kt          EraseScreen (erase/restore brush), CropScreen (image layers and video layer frames), ToolTopBar
    DrawScreen.kt          Freehand drawing pad (marker / highlighter) that makes or extends a DrawLayer
  ui/web/
    WebImportScreen.kt     WebView image browser with long-press-to-import
app/src/test/java/com/ndelic/kadar/   Same packages as main: model/, video/, ui/editor/ (JVM tests for the pure logic)
app/src/main/res/font/                anton.ttf (meme font), jakarta.ttf (UI font), eight caption fonts from Google Fonts
                                      (EditorViewModel.fonts), all SIL OFL
app/src/main/res/drawable/ic_*.xml    Material Symbols (scripts/add-icons.sh) plus ic_mark / ic_launcher_* (own work)
app/src/main/res/values/strings.xml   All UI text in English; values-bs/strings.xml has the Bosnian twin of every entry
app/src/main/res/xml/locales_config.xml  Languages offered in Android 13+ per-app language settings
scripts/                              add-icons.sh, make-logo.py
docs/                                 screenshots/ and brand/ for the README
third_party/                          License texts, also packaged as app assets
```

### Where new code goes

| You're adding | Put it in |
|---|---|
| A field on a layer, project, clip or setting | The type in `model/` or `video/`, plus its JSON read/write in `model/Projects.kt` (`opt*` default) |
| A new layer type | `model/Layers.kt` (type + `drawContent`), `model/Projects.kt` (JSON), then UI |
| Math, geometry, matrices, time calculations | A plain function in the matching `model/` or `video/` file (or a new focused file there) + a test in `app/src/test` under the same package |
| Anything that touches files, MediaStore, the network or ML Kit | `media/` |
| Anything that builds or exports video | `video/` |
| A widget used by two screens | `ui/common/Theme.kt` (or a new file in `ui/common/` if it's big) |
| A new editor panel or overlay screen | Its own file in `ui/editor/` (`EditorScreen.kt` should shrink, not grow) |
| A new top-level screen | Its own file in the `ui/` package of its area, or a new `ui/<area>/` package |
| State and actions | `EditorViewModel` (orchestrates; delegates logic to `model/`, `media/`, `video/`) |
| Any text the user sees | Both `res/values/strings.xml` and `res/values-bs/strings.xml`; from outside the UI as a `UiText` / `UserError` |
| A new language | `res/values-<lang>/strings.xml` with every entry, plus a `<locale>` in `res/xml/locales_config.xml` |

Dependencies point one way: `ui/*` may use `model`, `media`, `video` and `EditorViewModel`; `model/` uses nothing from the app (string resource ids from `R` are fine); `media/` and `video/` use only `model/`. Never import `ui` from `model`, `media` or `video`.

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

1. **One drawing path.** The stage, the photo export, the video overlay and the thumbnails all go through `drawLayer`/`renderLayers`. Never draw a layer type a second way, or exports stop matching the screen. The one exception is `VideoLayer`: its pixels come from a video player (the export's Media3 compositor, the stage's `VideoLayerPreview`), so its `drawContent` draws nothing and the stage canvas draws just its selection box.
2. **Canvas pixels are the unit.** Layer transforms are in full-resolution canvas pixels (photo size or video display size). The stage only scales for display; gestures divide by that scale.
3. **Never lose a project.** Project writes go through `writeAtomic` (write then rename) under `saveLock`. A missing image drops only its layer, never the whole project. If you change the JSON format, bump `version` in `Project.toJson`, keep reading older files, and default new fields with `opt*`.
4. **Images live in the project folder.** Every bitmap a layer uses is written once with `ProjectStore.writeImage` and referenced by file name; `ProjectStore.cleanUp` removes unreferenced ones on close. Voice recordings are `.m4a` files in the same folder (`ProjectStore.newVoiceFile`), stored by name in `VideoClip.voices`; `cleanUp` keeps every one the project references. Videos and music are referenced by content URI with a persisted read grant (`keepAccess`); only when that fails (a one-time share grant) is the video copied into the project as `video.mp4`.
5. **Nothing goes to the gallery except an export.** Web downloads, cut-outs and stickers stay in app storage. Exports go to `Pictures/Kadar` or `Movies/Kadar` through `saveToGallery` (no storage permission needed).
6. **Offline first.** No analytics, no accounts, no cloud APIs. The browser is the only feature that needs internet; don't add features that silently require it.
7. **Treat web input as untrusted.** `downloadImage` accepts only `http(s)` and base64 `data:` URLs, caps the size, and must fail with a message, not a crash.
8. **The preview is the export.** Video settings change `VideoClip`; `VideoClip.composition()` builds both the `CompositionPlayer` preview and the Transformer export. Add new video features there, not in only one of them. Media3's `DefaultVideoCompositor` takes every output frame time from its primary input (sequence 0) and always draws it on top, so with video layers sequence 0 is the frame clock (`blankStill()`), a blank still at the clip's frame rate (`VideoClip.frameRate`) drawn at alpha 0; then the video layers (topmost first), padded with gaps to the clip's length, gap frames hidden by alpha, each followed by the drawn layers right under it in the list if any (`drawnGroups`): another blank still, presented at the canvas size, with those layers as a `timedOverlay` and `Unpremultiply` (the overlay on a transparent frame comes out premultiplied and the compositor blends straight alpha), full-frame in the compositor; then the clip; with a blurred canvas (`VideoClip.blur`) the next sequence, drawn under the clip, is the same clip again (muted, same trim and speed), filled, blurred and darkened, showing through the clip's letterbox bars, which Media3 leaves transparent. Without video layers the clip itself is sequence 0. Audio-only sequences come last: the music (one sequence per repeat, a gap then the song, so each repeat knows its output time for the fades; several items in one audio sequence left the single-input preview black), then one per voiceover, a gap until it starts and a gap to the end. The layers above the topmost video layer (all of them without video layers, so those projects get no extra sequence) are the bitmap overlay on the composition, after compositing. The preview leaves video layers out of its composition: `ui/editor/VideoLayerPreview.kt` draws each with its own `ExoPlayer` in a `TextureView` over the main preview, in list order with a canvas of the drawn layers under it before each (the same `drawnGroups`) and under the top stage canvas (the layers above every video layer, selection, handles and gestures), placed from the same `Transform` and timed with the same `overlaySpan`/`overlaySourceMs` as the export, kept in step with the main player (`overlayNeedsSeek`), so a drag only moves a view. The blurred canvas is left out the same way (`ui/editor/BlurBackgroundPreview.kt`: the clip in a muted `ExoPlayer`, blurred and darkened with a `RenderEffect` on Android 12+, behind the main preview, whose letterbox bars are transparent), so the preview player always stays single-input: Media3 1.11's multi-input preview fails mid-playback on devices.
9. **Layer times are output time** (after trim and speed), the same clock as `CompositionPlayer.currentPosition`. `endMs = Long.MAX_VALUE` means "to the end". The stage shows only layers visible at the playhead (plus the selected one); the export uses `timedOverlay`, which sits on the composition (not the clip) so it sees output timestamps (layers under a video layer: on a blank still from 0 with no speed change, the same clock). `timedOverlay` caches one bitmap per stretch where nothing changes and draws only animated stretches per frame (one reused bitmap, from layer copies, since that runs on the GL thread); never cache per-frame bitmaps.
10. **Every edit is undoable.** Change the project only through `updateProject`/`updateLayer`; continuous gestures pass `record = first` so one drag is one undo step.
11. **Long tasks go through `EditorViewModel.work`.** It shows the busy overlay (which keeps the screen on), runs one task at a time, and turns exceptions into a snackbar. A task can fill the overlay's ring by setting `progress` (percent) and offer Cancel by setting `cancellable` only while stopping is safe (video export: during the Transformer run, not the gallery copy); `cancelWork` cancels the coroutine, so cleanup goes in `finally`, and `work` always clears `busy`. Transformer must be called on the main thread.
12. **Match existing patterns.** State is one `StateFlow<EditorUiState>`, one-shot messages go through the `effects` channel, panels are composables in `ui/editor/` that call the ViewModel directly. Reuse the components in `ui/common/Theme.kt` before writing new widgets.
13. **Edge drags need `systemGestureExclusion()`**, or the system back gesture steals them (the timeline does this).
14. **Clean room, open assets.** Never copy, decompile or look at another editing app's code, and never take its icons, images, fonts, stickers, presets or distinctive UI design; build features from public descriptions of what they do. Every bundled asset must be openly licensed (OFL, CC0, Apache/MIT, own work) and get a row in `ATTRIBUTION.md` plus its license file in `third_party/`. Don't name other apps in the UI or store text.
15. **Known patents to stay clear of** (checked 2026-10-12, assignee search, not a legal opinion): US11989808B2 covers recording a creator's edit steps as a "replay" others apply to their own photo; read its claims before building anything like edit-sharing templates. USD1056949S1 is a design patent on a staggered two-column card feed with a five-tab bottom bar and a central round "+"; don't build the home screen that way.
16. **Smallest change that fully works.** No new dependencies, layers or abstractions without a clear need.
17. **No inline UI text.** Every user-visible string (labels, content descriptions, dialogs, snackbars) is a resource in `res/values/strings.xml` (English) and `res/values-bs/strings.xml` (Bosnian, Latin script); `lintDebug` fails on a missing translation. Composables use `stringResource` / `pluralStringResource` with format args, never concatenation. Text made outside the UI is a `UiText` (`model/UiText.kt`): busy labels and `EditorEffect.Message` carry one, and a failure the user should read is `throw UserError(R.string.x)`; other exception messages show as they are. Enum labels are `@StringRes` ids; names stored in projects or settings (enum names, `Font.name`) are data and never translated. Toolbar labels must fit a 68dp `IconTool` (about 11 characters at 11sp).

## Keep the codebase clean

Several agents work on this repo in parallel, so structure matters as much as features.

1. **Reuse before you write.** Search for an existing helper first: shared UI pieces live in `ui/common/Theme.kt` (`IconTool`, `CircleButton`, `KSlider`, `ColorRow`, `TextTabs`, `KToggle`, `KDialog`, `KTextField`, `PanelLabel`), drawing in `model/Layers.kt`, media I/O in `media/Media.kt`, video in `video/VideoEdit.kt`, persistence in `model/Projects.kt`. Extend one of those instead of writing a second copy. If you need something twice, extract it once to the right file.
2. **One responsibility per file, in the right package.** Follow "Where new code goes" above. A new screen or self-contained component gets its own file. `ui/editor/EditorScreen.kt` is the biggest file: put new editor parts (panels, timeline pieces, overlays) in their own files instead of growing it; move code out only when you are already changing it, as its own commit.
3. **Pure logic outside composables and the ViewModel.** Math, geometry, parsing, matrix and time calculations go in plain Kotlin functions in `model/` or `video/` with a JVM test in `app/src/test` under the same package. Composables render and forward events; the ViewModel orchestrates and owns state.
4. **Model types live with their data.** Layer types and their drawing in `model/Layers.kt`; `Project` and its JSON in `model/Projects.kt`; video settings in `video/VideoEdit.kt`; app settings in `model/Settings.kt`. A new field touches its type, its JSON read/write (with an `opt*` default) and nothing else by surprise.
5. **Name things after what they are**, match the existing naming and formatting, no dead code, no commented-out code, no `TODO` without context. Comments explain *why*, never *what*.
6. **Small, focused diffs.** Don't reformat or reorganize code you aren't changing; it makes parallel work impossible to merge. Refactors and file moves are their own commits, and a move commit changes nothing but `package`/`import` lines.
7. **Keep this document true.** When you add a file, a package or a pattern, update "Layout" and "Where new code goes" in the same commit.

## Verifying a change

1. `./gradlew testDebugUnitTest assembleDebug` passes.
2. Install on the emulator and try the affected flow end to end, including **Export** and checking the file (`adb pull` it).
3. For project-format changes: open an existing project from before the change and confirm nothing is lost.
4. For video changes: export, then check the result with `ffprobe` (duration, rotation) and look at a frame.
