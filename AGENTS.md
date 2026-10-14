# AGENTS.md

Guide for AI coding agents (and humans) working in this repo.

## What this is

**Kadar**, a free, open-source (GPL-3.0-or-later) photo and video editor for Android, started for a meme page and released publicly. Everything runs on the phone and offline; the only network use is the in-app image browser (and a one-time download of ML Kit's background-removal model through Play services).

The owner's phone is a Galaxy S25; it also runs on older phones, hence `minSdk 29`.

## What it does today

- **Photo**: open a photo, a blank canvas (`ui/home/BlankDialog.kt` asks its shape, 1080 px on the short side), or web image; add text (meme outline style), emoji, images and saved stickers as layers; move, pinch-resize, rotate; mirror or flip images, stickers and drawings (`Transform.flipX/flipY`: `drawLayer` mirrors the content inside its unflipped box, so handles and hit tests don't change; content-pixel offsets such as a crop or cut-out go through `Transform.shiftedContent`, and the eraser and crop screens show the image flipped); image and sticker opacity (`ImageLayer.opacity`); remove backgrounds on-device; change canvas aspect (`CANVAS_RATIOS` in `ui/common/Theme.kt`, shared with the video canvas) and background, a color or a blur (`Project.blur`: `model/Backdrop.kt` blurs the bottom image layer small, and the stage, the export and the thumbnail all draw that one bitmap stretched under the layers, through `renderLayers(backdrop =)`); export JPEG/PNG to `Pictures/Kadar` (picked in the export dialog, remembered as `AppSettings.photoFormat`; a transparent canvas is always PNG: `Project.transparent`).
- **Video (NLE)**: a main track of clips in a row (`VideoClip.segments`, each a `Segment` with its own trim, speed (a slider from 0.2x to 10x, caught at 0.5x, 1x and 2x: `speedAt`), volume, keep-pitch and filter/adjust; photos are stills of 3 s, trimmable to 30 s, copied into the project folder). The "+" after the clip strip adds a video or photo; tap a clip to pick it (the clip tools act on it, else on the clip under the playhead), drag the picked clip's ends to trim it (`edgeTrim`, committed on release as one undo step; a second tap opens the full trim strip), long-press-drag to reorder, Split at the playhead, Duplicate, Delete (not the last); Apply to all in the Speed, Filters and Adjust panels copies the clip's speed (video clips only) or its look and sliders to every clip (`updateAllSegments`); the size and frame rate come from the first clip and the others are fitted into its frame. Project-wide: songs (`VideoClip.music`, added at the playhead) and voiceovers recorded from the playhead in the Audio panel (`ui/editor/VoiceoverPanel.kt`; `VideoClip.voices`), each an audio track under the clip strip with an `AudioPart` (`video/AudioParts.kt`: output start, offset into the source, length, volume, fade in and out lengths up to 5 s). Tap a track to select it (`EditorUiState.selectedAudio`, an `AudioRef`), drag its middle to move it, its ends to trim it (committed on release as one undo step: `AudioPart.dragged`), Split it at the playhead into two that sound the same (no fades at the cut: `splitAt`) or Delete it; the Audio panel then shows its volume, fades and, for a song, where in the song it starts. A song loops from that point to the end of its part; a part of `Long.MAX_VALUE` runs to the end of the video, which is what an old project's single song reads as (`video/MusicFade.kt` for the repeats and fades). Text/sticker layers sit on their own timeline tracks above the clip, each with a start and end time (a selected track shows trim handles at its ends; trimming a video layer's start moves its in-point with it, `VideoLayer.trimmedStart`), an eye and a lock at its left, and long-press-drag to reorder; a selected layer's Split cuts it at the playhead into two with the same look (no exit/entrance at the cut; a video layer's second part gets the later in-point) and Move here starts it at the playhead (`layerSplitPoint`, `layerMovedTo` in `video/Segments.kt`). Pinch the timeline to zoom, slide two fingers to scroll (view-only, `TimelineViewport`); one finger always edits or scrubs. Video layers (picture-in-picture) put more clips on top, placed like stickers, stacked with text and stickers in track order (a caption under a video layer's track is covered by it), with their own track, opacity, volume, in-point (`clipStartMs`, the Edit panel `ui/editor/VideoLayerPanel.kt`) and crop (`VideoLayer.crop`, a `CropRect` of the frame picked in the shared `CropScreen`; the layer's size is the cropped size, the export adds a Media3 `Crop` before its `Presentation`, the preview clips a full-frame `TextureView` to the layer box); export H.264/AAC MP4 to `Movies/Kadar`, or a GIF to `Pictures/Kadar`.
- **Export dialog** (`ui/editor/ExportDialog.kt`, opened by Save and Share): a photo's format; a video's MP4 or GIF, and for MP4 the frame rate (24, 25, 30, 50, 60, starting at the clip's own `outputFrameRate`), the quality (starting at the Settings one) and the estimated size (`VideoClip.estimatedSize`: duration times Media3's bitrate for that quality, size and rate, plus AAC). The clip's own rate exports as before; any other copies the `VideoClip` with that `frameRate` and passes `composition(frameClock = true)`, so the frame clock (`blankStill()`) sets every output frame time even without video layers. GIF (`video/Gif.kt`): the same composition rendered by Transformer to a small MP4 (long side `GIF_LONG_SIDE` 480, `GIF_FPS` 10 through the frame clock), then `mp4ToGif` reads its frames with `MediaMetadataRetriever` and `GifWriter` writes them (per-frame 256-color median-cut palette, LZW); videos longer than `GIF_MAX_MS` (15 s) are refused.
- **Transitions** (`video/Transitions.kt`, GL effects in `video/TransitionEffects.kt`, `ui/editor/TransitionPanel.kt`): a button on every cut of the clip strip opens the panel: None, Dissolve, Black, White, Zoom, Blur, Wipe/Slide/Push left or up, a length (0.2 to 1.5 s, at most half the shorter neighbour: `maxTransitionMs`) and Apply to all. Stored as `Segment.transition`, the transition into that clip (the first clip's is unused; a split's second half gets none). Kinds that `overlaps` (dissolve, wipes, slides, pushes) play the incoming clip's head over the end of the clip before, so the video gets that much shorter: `VideoClip.played` is the clips without their heads (`headsMs`), `outputDurationMs`, `segmentAt` and `segmentStart` count with it, and the clip strip draws the played clips. The others are centered on the cut, one clip at a time. `transitionSpans` says where each plays in output time.
- **Animations** (`model/Animation.kt`, `ui/editor/AnimationPanel.kt`): video text, sticker and shape layers get an entrance and an exit (None, Fade, Pop, Zoom out, Slide up/down/left/right; a slide moves `Motion.dx`/`dy`), 300 ms each unless set (`Layer.animInMs`/`animOutMs`, null for the kind's own length), at most half the layer's time, and a loop (`Loop`: Wiggle, Pulse, Bounce) all the time they show. Text also gets per-letter entrances (`Anim.perLetter`: Typewriter, Letters pop, 1.2 s): the `Motion` carries a `Reveal`, `letterScale` says how big each letter draws, and `TextLayer.drawContent(canvas, reveal)` draws the letters one by one at the layout's positions, so it stays the one text drawing path. `motionAt` turns a layer and an output time into alpha, scale, offset, rotation and reveal, and `Canvas.drawLayer(layer, motion, canvasHeight)` applies it around `drawLayer`; the stage uses it with the player position (every frame while playing), the export's `timedOverlay` with each frame's timestamp (a looping layer's stretch is drawn per frame all through).
- **Text look** (`TextLayer`): outline color and `outlineWidth` (`ui/editor/OutlinePanel.kt`), shadow (`shadow` color, `shadowBlur`, `shadowDistance`, all in multiples of `size`, `ui/editor/ShadowPanel.kt`; old projects' `"shadow": true` reads as `DEFAULT_SHADOW`), glow (`glow` color, `glowSize`, `ui/editor/GlowPanel.kt`), `boxOpacity` (the box or bubble fill only), format toggles `bold`/`italic` (the platform's `Typeface.create`, faked for one-style fonts), `underline` and `caps` (draws `text.uppercase()`, the stored text stays as typed) in the Style panel, a Size slider in the Font panel (the placement scale, like a pinch; 100% is `defaultTextSize`), opacity (the whole layer through one offscreen layer), `lineSpacing` and `wrap` (where the text wraps, in layer pixels; null keeps the lines as typed). The side handles of a selected text drag its wrap with the other edge kept in place (`TextLayer.wrapDragged`). Presets (`model/TextPresets.kt`, `ui/editor/TextPresetsPanel.kt`, tiles drawn with `drawLayer`) set font, colors, box/bubble, glow, shadow and spacing in one undo step.
- **Imported fonts**: Font > Add font copies a picked `.ttf`/`.otf` into `filesDir/fonts` (`media/Media.kt` `importFont`: size-capped, kept only if `loadFont` can load it), listed after the bundled fonts in `EditorUiState.fonts` and used in every project, like stickers. Projects store the font name (the file name); a missing one falls back to the default font.
- **Undo / redo** for every edit (gestures and timeline drags count once per drag).
- **Snapping** to the canvas center and 0°/90°, caption boxes behind text, a Layers panel (photos only, top-bar button; video shows layers as timeline tracks), keep-pitch for speed changes.
- **Speech bubbles** (`ui/editor/BubblePanel.kt`, geometry in `model/Shapes.kt`): a text layer's box has a `Bubble` style (the caption `Box`, or `Speech`, `Thought`, `Shout`) with a fill (`box`) and an outline (`boxOutline`). Bubbles wrap the text (`bubbleWrapWidth`) into an ellipse, and their tail points at `TextLayer.tail` (layer pixels from its center; null is the default spot), dragged with a third handle on the stage (`Transform.toLocal`). Body and tail are merged into one `Path` (`Path.op`), so the outline never crosses the inside. `Layer.reach` covers the tail, so a fade's offscreen layer doesn't clip it.
- **Shapes** (`ShapeLayer` in `model/Layers.kt`, points in `model/Shapes.kt`, `ui/editor/ShapePanel.kt`): rectangle, rounded rectangle, ellipse, arrow, star and line, `w`×`h` in their own pixels, with a fill and an outline (either none), an outline width and an opacity (one offscreen layer, so the outline doesn't show through the fill). The Shape tool adds one (`newShape`); with a shape selected it changes the kind, the stretch (width to height) and the opacity.
- **Drawing** (`model/Strokes.kt`, `ui/editor/DrawScreen.kt`): the Draw tool opens a full-screen pad (one finger draws, two zoom/pan) with marker and highlighter pens, an eraser (`erased`: takes out the points it reaches, so it splits strokes and keeps them vectors), colors, size, opacity and undo/redo (whole stroke lists, strokes are immutable). Apply makes one `DrawLayer` (strokes in its own pixels, centered on its origin by `withStrokes`, smoothed by `smoothSegments`); Draw on a selected drawing edits it: its strokes come in as canvas pixels (`canvasStrokes`) and go back with its rotation and scale kept; erased to nothing, the layer is deleted.
- **Filters** (`model/Looks.kt`, `ui/editor/FiltersPanel.kt`): in-house looks as 4x5 color matrices, blended by a strength and stored on `Adjust` (`look`, `amount`). `Adjust.filterMatrix()` (look, then the sliders) is the one matrix for the image layer paint and, through `glRgbMatrix`, the video's Media3 `RgbMatrix`, so photos and video match. The Adjust panel's sliders (brightness, contrast, saturation, warmth, tint, hue, fade; one at a time, picked from tabs) are matrices too, and a color slider at 0 is left out, so old projects keep their exact matrix. Video layers have an `Adjust` of their own: the export puts its `RgbMatrix` first in the layer's effects, the preview sets the same matrix as the `TextureView`'s layer paint. Hold to compare (`CompareButton` in Filters and Adjust) shows `Project.withoutAdjust()` on the stage and the preview while pressed, view-only.
- **Eraser / restore brush and crop** for image layers (`ui/editor/ImageTools.kt`). The brush has a hardness (soft edge, a `BlurMaskFilter`) and undo/redo: strokes are kept and replayed onto the layer's bitmap, and each move repaints the stroke in progress over the picture from before it, so a soft edge doesn't build up. Crop has ratios, and for image layers quarter turns and straightening (±45°, an angle dial; `model/Crop.kt` for the math, `turnBitmap` in `media/`); a turned crop turns the source too (`turnedSourceOffset`), so restore still lines up, and bakes a flipped layer's flip into both (the new layer is unflipped). `ImageLayer.source` + `sourceX/Y` remember the untouched original and where the layer's bitmap sits in it; keep them right in any new operation that resizes or crops a bitmap.
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
    Backdrop.kt            Blurred photo canvas: Project.backdrop (the bottom picture, covered, darkened, box-blurred small), boxBlur
    Crop.kt                Crop math: a turned picture's bounds, the largest straightened rectangle, turned source offset
    Strokes.kt             Freehand strokes: PenStroke, smoothing, bounds, erasing, DrawLayer.withStrokes (re-centering) / canvasStrokes
    Projects.kt            Project, ProjectStore (one folder per project: project.json, thumb.jpg, images, voice .m4a), JSON
    ProjectList.kt         Home grid logic: default project label, search filter, ProjectSort
    Settings.kt            AppSettings (search engine, export size, export quality, project sort, photo format), SettingsStore
    UiText.kt              UiText (string resource, plural or raw text, resolved by the UI) and UserError
  media/
    Media.kt               Bitmap decode/download, emoji render, video info and frames, gallery export, ML Kit cut-out, font import,
                           turnBitmap (crop rotation)
  video/
    VideoEdit.kt           VideoClip / Segment / Music / VoiceClip, VideoClip.composition() (preview + export), exportVideo()
    Segments.kt            Pure math for the main track: output length, clip at an output time, source time, split point, reorder,
                           edge trim, the speed slider's scale; layer tracks: split point and Move here
    Transitions.kt         TransitionKind / Transition, length caps, heads cut off by overlapping kinds, spans in output time, the
                           look at a point of one (alpha, offsets, wipe rectangle, dip)
    TransitionEffects.kt   Time-based GL effects for transitions: dipEffects (black/white matrix, zoom, blur) and WipeMask
    VideoOverlay.kt        Pure math for video layers: Transform and crop to compositor coordinates, when a layer plays, alpha, frame rate,
                           source time at an output time and when the preview must resync, drawnGroups (stacking around video layers)
    Unpremultiply.kt       GL effect that divides color by alpha, for layers drawn into a compositor input of their own
    MusicFade.kt           Audio fade timing: fade gain at an output time, a song's repeats, fade windows per repeat
    Gif.kt                 GIF export: GifWriter (GIF89a bytes, median-cut palette, LZW), mp4ToGif (frames of a rendered MP4), GIF size and rate
    AudioParts.kt          AudioPart (where a song or voiceover plays, its volume and fades), AudioRef, drag, split, withAudio
  ui/common/
    Theme.kt               Ink palette, fonts, shared components (IconTool, CircleButton, KSlider, ColorRow, TextTabs,
                           KToggle, KDialog, KTextField, PanelLabel, AspectTile, CANVAS_RATIOS)
    ColorPicker.kt         ColorRow's custom color dialog (hue, saturation/brightness, hex), recent custom colors, and
                           LocalEyedropper: the editor provides it around its panels and the stage picks from
                           EditorViewModel.stagePicture (a touch on the canvas while it's on)
  ui/home/
    HomeScreen.kt          Start cards, project grid (names, sort, search), multi-select bar
    RenameDialog.kt        Project name dialog
    BlankDialog.kt         Shape of a new blank canvas
    Intro.kt               Launch animation
    SettingsScreen.kt      Settings and About (license texts)
  ui/editor/
    EditorScreen.kt        Top bar, Stage (canvas, gestures, handles, view zoom, video preview), tool row, TextEntry
    Timeline.kt            NLE timeline (layer tracks + clip strip with +, pick, reorder + audio tracks) and TrimStrip (one clip)
    TimelineMath.kt        TimelineViewport (zoom/scroll: time to x, pinch, follow the playhead), ruler steps, filmstrip frames
    Panels.kt              Tool panels: stickers, canvas, speed, audio, font (with Add font), style, adjust, layers
    FiltersPanel.kt        Look tiles and strength slider, CompareButton (hold to see the picture without looks and adjustments)
    AnimationPanel.kt      In, Out and Loop animation choices (one part at a time) and in/out lengths for a video text/sticker/shape layer
    BubblePanel.kt         Box or bubble behind the selected text: style, fill and outline colors
    GlowPanel.kt           Glow color and size of the selected text
    OutlinePanel.kt        Outline color and width of the selected text
    ShadowPanel.kt         Shadow color, blur and distance of the selected text
    TextPresetsPanel.kt    Text preset tiles, each a sample drawn in its look
    ShapePanel.kt          Shape kinds (adds one, or changes the selected), stretch, opacity, fill and outline panels
    VideoLayerPanel.kt     Opacity, sound and "Starts at" (in-point) of the selected video layer
    TransitionPanel.kt     The transition into a clip: kind, length, Apply to all
    ExportDialog.kt        Save / Share dialog: photo JPG or PNG; video MP4 (frame rate, quality, estimated size) or GIF
    IncomingClipPreview.kt The incoming clip of an overlapping transition on the stage: its own ExoPlayer, faded, moved or clipped
    VideoLayerPreview.kt   A video layer on the stage: its own ExoPlayer in a TextureView, placed like the layer, its look as the view's
                           layer paint, synced to the main preview
    BlurBackgroundPreview.kt  The blurred canvas on the stage: the clip under the playhead again in its own muted ExoPlayer (a photo clip as its picture), blurred by the view
    VoiceoverPanel.kt      Audio panel's voiceover part: record from the playhead (preview muted)
    ImageTools.kt          EraseScreen (erase/restore brush), CropScreen (image layers and video layer frames), ToolTopBar
    DrawScreen.kt          Freehand drawing pad (marker / highlighter / eraser) that makes or edits a DrawLayer
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
4. **Images live in the project folder.** Every bitmap a layer or a photo clip uses is written once with `ProjectStore.writeImage` and referenced by file name; `ProjectStore.cleanUp` removes unreferenced ones on close. Voice recordings are `.m4a` files in the same folder (`ProjectStore.newVoiceFile`), stored by name in `VideoClip.voices`; `cleanUp` keeps every one the project references. Videos and music are referenced by content URI with a persisted read grant (`keepAccess`); only when that fails (a one-time share grant) is the video copied into the project as `video.mp4`.
5. **Nothing goes to the gallery except an export.** Web downloads, cut-outs and stickers stay in app storage. Exports go to `Pictures/Kadar` or `Movies/Kadar` through `saveToGallery` (no storage permission needed).
6. **Offline first.** No analytics, no accounts, no cloud APIs. The browser is the only feature that needs internet; don't add features that silently require it.
7. **Treat web input as untrusted.** `downloadImage` accepts only `http(s)` and base64 `data:` URLs, caps the size, and must fail with a message, not a crash.
8. **The preview is the export.** Video settings change `VideoClip`; `VideoClip.composition()` builds both the `CompositionPlayer` preview and the Transformer export. Add new video features there, not in only one of them. Media3's `DefaultVideoCompositor` takes every output frame time from its primary input (sequence 0) and always draws it on top, so with video layers sequence 0 is the frame clock (`blankStill()`), a blank still at the clip's frame rate (`VideoClip.frameRate`) drawn at alpha 0; then the video layers (topmost first), padded with gaps to the output's length (the clips' output lengths added up), gap frames hidden by alpha, each followed by the drawn layers right under it in the list if any (`drawnGroups`): another blank still, presented at the canvas size, with those layers as a `timedOverlay` and `Unpremultiply` (the overlay on a transparent frame comes out premultiplied and the compositor blends straight alpha), full-frame in the compositor; then, with overlapping transitions, the incoming heads (`incomingSequence`: one sequence, a gap, a head, a gap and so on, each head playing `headTailsMs` past its cut so the frame before the cut never pairs with a hidden gap frame, faded or moved by `incomingSettings` and wiped by the `WipeMask` item effect; a push moves the clips' input by `outgoingSettings`; the compositor pairs each output frame with the nearest frame of every input and evaluates settings at that input frame's own time, so placements go through `outputFrameUs`; anchors only take -1..1, so a whole canvas away is both anchors at half, `shifted`); then the clips, one item per played `Segment` (a photo is an image item with `setImageDurationMs` and the frame rate; clips of another size are fitted with a `Presentation`; with sound in any clip the sequence declares an audio track so photos get silence; the centered transitions are item effects on the clips either side of the cut, `dipEffects`, which Media3 hands frames at their time in the sequence, output time, in the preview and the export alike); with a blurred canvas (`VideoClip.blur`) the next sequence, drawn under the clips, is the same clips again (muted, same trims and speeds), filled, blurred and darkened, showing through the clip's letterbox bars, which Media3 leaves transparent. Without video layers the clips are sequence 0. Audio-only sequences come last, one per repeat of each song part, then one per voiceover part (`audioSequences`): a gap until it plays, that stretch of the source, a gap to the end, so each repeat knows its output time for the fades; several items in one audio sequence left the single-input preview black. The layers above the topmost video layer (all of them without video layers, so those projects get no extra sequence) are the bitmap overlay on the composition, after compositing. The preview leaves video layers out of its composition: `ui/editor/VideoLayerPreview.kt` draws each with its own `ExoPlayer` in a `TextureView` over the main preview, in list order with a canvas of the drawn layers under it before each (the same `drawnGroups`) and under the top stage canvas (the layers above every video layer, selection, handles and gestures), placed from the same `Transform` and timed with the same `overlaySpan`/`overlaySourceMs` as the export, kept in step with the main player (`overlayNeedsSeek`), so a drag only moves a view. The blurred canvas is left out the same way (`ui/editor/BlurBackgroundPreview.kt`: the clips as a playlist in a muted `ExoPlayer` that seeks to the clip under the playhead and takes its speed, blurred and darkened with a `RenderEffect` on Android 12+ at 1/8 of its size and scaled up, behind the main preview, whose letterbox bars are transparent). The main preview is a `SurfaceView` (frames shown at their time without the UI thread; a `TextureView` judders and drops frames when the UI is busy), except with the blurred canvas, which needs a `TextureView` so the bars show what's under them. The stage redraws animated layers once per video frame (`frameStartMs`), not per screen refresh. Sped-up clips get a frame drop effect in the export when the clips are sequence 0, so 10x doesn't hand the encoder 300 fps; the preview leaves it out (`dropFrames = false`), since Media3 1.11's preview runs on past the end with it. The heads of overlapping transitions are left out too (`composition(incoming = false)`): `ui/editor/IncomingClipPreview.kt` plays them in its own `ExoPlayer` over the main preview (under the video layers), placed and timed by the same `TransitionKind` functions, and pushes move the main preview's view. So the preview player always stays single-input: Media3 1.11's multi-input preview fails mid-playback on devices. Media3 1.11's preview also stalls for good when it plays into an AV1 clip that starts between keyframes (a trim, or a head an overlapping transition cuts off; the export is fine), so the editor seeks to the playhead when the player has been buffering for `STALL_MS` while playing. The editor keeps the screen on while the preview plays.
9. **Layer times are output time** (the clips end to end, each after its trim and speed, less the overlapping transitions; reordering or splitting clips or adding a transition leaves layers where they are in time), the same clock as `CompositionPlayer.currentPosition`. `endMs = Long.MAX_VALUE` means "to the end". The stage shows only layers visible at the playhead (plus the selected one); the export uses `timedOverlay`, which sits on the composition (not the clip) so it sees output timestamps (layers under a video layer: on a blank still from 0 with no speed change, the same clock). `timedOverlay` caches one bitmap per stretch where nothing changes and draws only animated stretches per frame (one reused bitmap, from layer copies, since that runs on the GL thread); never cache per-frame bitmaps.
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
