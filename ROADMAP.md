# Roadmap

## v0.1: done

- Photo editor: layers (photo, image, text, emoji, sticker), drag / pinch / rotate, bring to front, delete
- Meme text: Anton font plus system fonts, fill and outline colors, size slider
- On-device background removal (ML Kit), cut-out keeps its place on the canvas
- My stickers: save a cut-out, reuse it in any project
- Canvas: aspect ratios (original, 1:1, 4:5, 9:16, 16:9), white / black / transparent background
- Video editor: trim, speed 0.25x to 4x, video volume, music track with volume and start offset, text and stickers burned in, live preview
- Projects autosave and reopen; images live inside the project, not the gallery
- In-app image browser: long-press to download into the project
- Export to gallery (JPEG, PNG for transparent, MP4 H.264/AAC) with a Share button

## v0.2: done

- New dark editor UI, icon toolbar, round top-bar buttons, gradient create tiles, Plus Jakarta Sans, new app icon, dark launch screen
- Selected layer handles: delete (top-left), scale + rotate with one finger (bottom-right); double-tap text to edit
- Full-screen text entry with color dots
- Undo / redo
- NLE timeline for video: a track per text/sticker with its own start and end, drag to move or resize, tap the clip to trim, scrub anywhere
- Layer order: Front and Back
- Share button exports and opens the share sheet directly

## v0.3: done

- Snap to the canvas center lines and to 0°/90°, with guide lines and a haptic tick
- Caption box behind text (any color)
- Keep voice pitch when changing speed (on by default; off for the chipmunk / slow-mo voice)
- Layers panel: the whole stack, tap to select, arrows to reorder (photos and video tracks)
- "Share to Kadar" from the gallery or any app; shared videos are copied into the project when the grant can't be kept
- Unused images are deleted from a project folder when it's closed

## v0.4: done

- Eraser / restore brush for image layers (one finger paints, two fingers zoom and pan); restore paints the original back after a cut-out or crop
- Crop an image layer (free or 1:1, 4:5, 9:16, 16:9), the kept part stays where it was

## v0.5: done (first public release)

- Text style: shadow, letter spacing, left / center / right alignment; long captions start sized to fit
- Hide and lock layers from the Layers panel
- Adjust brightness, contrast and saturation for image layers and the video
- Video canvas: 1:1, 4:5, 9:16, 16:9 with black bars
- New look: lime accent, new home screen and app icon
- Signed release APKs built by GitHub Actions on every version tag; CI on every push
- Separate "Kadar Dev" debug app; R8 keep rules so background removal works in release builds
- GPL-3.0-or-later, NOTICE and ATTRIBUTION.md
- Fix: the keyboard no longer shrinks the editor while typing text

## v0.5.1: done

- Video over video: add more clips as layers on top of the main one, picture-in-picture style. Move, pinch-resize and rotate them on the stage, set when they show on their own timeline track, give them their own volume (muted by default), and export them with the main clip
- Draw: circle things, draw arrows and scribble with a marker or a see-through highlighter, any color and size, with undo inside the draw screen. Each drawing is a layer you can move, resize, rotate, time on the video timeline, duplicate or draw more onto
- Project names: unnamed projects show their type and day ("Photo · Oct 12"); select one project on the home screen and tap Rename. Sort the grid by Recent, Name or Type (videos first), remembered between launches, and search by name once there are more than 6 projects
- In/out animations for text and stickers in videos: None, Fade, Pop or Slide up, for the entrance and the exit separately (Animate tool); the preview plays them and the export matches
- Eight more caption fonts, all free (OFL) and bundled, so they work offline: Tall (Bebas Neue), Narrow (Oswald), Heavy (Archivo Black), Comic (Bangers), Marker (Caveat Brush), Casual (Comic Neue), Retro (Pacifico) and Horror (Creepster). The font row scrolls and marks the chosen one in lime
- Bosnian translation: the app follows the phone's language, or pick it per app in Android 13+ settings (App languages). Error messages and busy labels are translated too
- Filters: eight in-house looks (Mono, Noir, Sepia, Warm, Cool, Vivid, Fade, Teal & Orange) with a strength slider, for image layers and the whole video
- Timeline tracks do everything the photo Layers panel does: long-press a track and drag it up/down to reorder, hide and lock with the eye and lock at its left (the Layers button is photo-only)
- Zoomable timeline for long videos: pinch to zoom (down to about a second across), slide two fingers to scroll, a time ruler on top, and the view pages along with the playhead while playing; one finger edits and scrubs as before
- Video layers: opacity, and "Starts at" to play them from the middle of their clip (Edit panel)
- Export frame timing with video layers follows the main clip's frame rate (at most 60 fps), not the topmost overlay's, so a 30 fps or screen-recorded overlay no longer makes the export choppy
- Blurred-background video canvas: on a 1:1, 4:5, 9:16 or 16:9 canvas, Canvas > Blur fills the bars with a blurred, darkened, zoomed copy of the video instead of black (the same clip as a second compositor sequence underneath)
- Fixes from a code review: no crash when dragging a track end after trimming or when cropping past the image edge, scrubbing keeps working after the preview refreshes, undo records the first move of a snapped layer, the editor stays open if saving fails, image downloads are size-capped and don't pass browser cookies to redirect targets, Eraser and Draw ask before Back throws work away
- Settings: image search engine (Google, DuckDuckGo, Bing, Brave) and default export size, with a per-project override; About with the license texts
- Home: launch animation, logo, project multi-select (duplicate, delete), PHOTO / VIDEO badges; pinch to zoom the photo canvas, tap around it to deselect

## v0.5.2: done

- Video export: a progress ring with the percentage, a Cancel button (nothing is saved), the screen stays on while it runs, and a Video quality setting (Standard, or High for about twice the bitrate)
- Voiceover: in the Audio panel, record your voice from the playhead while the video plays (muted, so the speaker isn't recorded); recordings stay in the project, each with its own volume and a delete button; the preview plays them and the export mixes them in. The microphone permission is asked for only on the first Record
- Music fade in and fade out (1.5 s at the start and before the end of the video), also when the song loops
- Smoother video preview: video layers and the blurred canvas each play in their own view, so dragging a layer moves it instantly, and a blurred-canvas project no longer stops or jumps back to 0:00 while playing; with music the picture no longer goes black
- Crop video layers: pick the part of the clip to show on one of its frames (free or 1:1, 4:5, 9:16, 16:9); the kept part stays where it was, and Crop again shows the whole frame to change it
- Video layers stack with text, stickers and drawings in track order: drag a video layer's track above a caption's to cover it, in the preview and the export
- Fix: see-through parts of text, stickers and drawings (soft edges, shadows, fades, the highlighter) exported darker than the preview showed them
- Fixes: paused video layers and the blurred canvas show their frame right away (they could stay black until you scrubbed), the Audio panel no longer squeezes the preview to a thumbnail, voiceovers are listed by start time, a new project says "Just now"

## v0.6.0: done

- Speech bubbles: the text Bubble tool puts the caption in a speech bubble, a thought cloud or a shout burst (or the old box), white with a black outline to start, any fill and outline color. The text wraps inside, and the tail points wherever you drag its handle on the canvas
- Shapes: rectangle, rounded rectangle, ellipse, arrow, star and line from the new Shape tool, with fill and outline color (or none), outline width, stretch and opacity; moved, resized, rotated, duplicated, animated and timed on the video timeline like any layer
- Several clips in a row: add videos and photos (3 s stills) with the + after the clip strip, tap a clip to pick it, long-press to reorder, split at the playhead, duplicate, delete; trim, speed, volume, filter and adjust per clip
- Text: glow (any color, with a size), opacity, line height, and a wrap width you drag with the handles on the sides of the selected text (long captions still start sized to fit)
- Text presets: Meme, Caption, Neon, Title, Subtitle and Bubble set the font, colors, box and glow in one tap (one undo step)
- Loop animations for text, stickers and shapes in videos: Wiggle, Pulse or Bounce for as long as the layer shows; text also gets Typewriter (letters appear one by one) and Letters pop entrances. The preview and the export match
- Add font: import a .ttf or .otf file from the Font panel; it stays in the app (offline) for every project. A project whose imported font is gone uses the default font
- Fix: dragging a selection handle near the screen edge no longer triggers the system back gesture

## v0.6.1: done

- Transitions between clips: tap the button on a cut in the clip strip for None, Dissolve, Black, White, Zoom, Blur, Wipe, Slide or Push (left or up), a length from 0.2 to 1.5 s, and Apply to all. Dissolve, wipes, slides and pushes overlap the two clips, so the video gets shorter by their length; the others pass through black, white, a zoom or a blur at the cut

## v0.7.0: done

- Text: outline width, shadow color, blur and distance, box opacity, bold, italic, underline, all caps (only the drawing changes, not what you typed) and a size slider
- Custom colors everywhere there's a color row: a picker with hue, saturation/brightness and a hex field, recent custom colors, and an eyedropper that takes a color from the picture
- Images and stickers: mirror, flip and opacity
- Video layer tracks: Split at the playhead and Move here (starts the layer at the playhead)
- Animations: slide in or out from any side, Zoom out, and an In/Out length from 0.1 to 3 s
- Clips: drag a picked clip's ends on the strip to trim it; speed is a slider from 0.2x to 10x (catches at 0.5x, 1x and 2x); Apply to all for speed, filter and adjust
- Eraser and restore brush: a soft edge (hardness) and undo/redo inside the tool
- Crop: 3:4, 2:3, 4:3 and 3:2, straighten (±45°) and 90° turns for image layers
- Draw: an eraser that cuts strokes, pen opacity and redo; erase strokes of a drawing you already placed
- Canvas: 3:4, 4:3, 2:3, 3:2, 2:1 and 2.35:1 for photos and videos, a blurred background for photos, and Blank canvas asks for its shape first

## Next

Feature parity with the big phone editors, built from how their features behave, never from their code, assets or look (AGENTS.md rules 14 and 15). Offline only: no cloud AI.

### Existing tools that do less than the big editors

- [ ] Music and voiceovers as timeline tracks: start anywhere, move, trim and split (music always starts at 0 today); fade in and out lengths (fixed 1.5 s today)
- [ ] Video layers: filter and adjust, like the main clips
- [ ] Adjust: warmth, tint, hue and fade (color matrices, so video still matches photos); vignette and grain need shaders
- [ ] Export: frame rate (24 to 60), GIF, and the estimated file size shown before export; JPG or PNG chosen at save time for photos
- [ ] Hold to compare with the original while adjusting

### Video

- [ ] Replace a clip with another video or photo, keeping its place and settings
- [ ] Clip tools: freeze frame, reverse, rotate and flip, crop the main clip, capture the frame as a photo, a blank color clip
- [ ] Canvas: more ratios (3:4, 4:3, 2:3, 3:2, 2:1, 1:2, 2.35:1); zoom and move the clip inside the canvas; background color, blur strength or an image
- [ ] Speed ramps (presets that slow down or speed up within a clip)
- [ ] Timed video effects on their own track: slow zoom, jump zoom, shake, glitch, blur, flash, fade to black, film grain
- [ ] Audio: a bundled CC0 sound-effect pack (meme sounds), several music tracks, extract the sound of a video, voice effects (higher, deeper, robot), text-to-speech with the phone's offline voices
- [ ] Clip in/out animations (zoom, slide, spin) and keyframes for layers and clips (position, scale, rotation, opacity over time)
- [ ] Blur or pixelate part of the video (faces, usernames): a rectangle, circle or heart region on its own track, with strength and opacity; also mosaic, frosted glass and magnifier looks
- [ ] Green screen: remove a picked color from a video layer, with strength and edge-shadow sliders
- [ ] Video layers: blend modes (multiply, screen, overlay, darken, lighten) and shaped masks (linear, radial, rectangle, heart, star) with a soft edge
- [ ] More transitions: spin, flip, glitch, color split
- [ ] Precut: cut out the middle of a clip, and step one frame back or forward while trimming
- [ ] Mute a clip in one tap
- [ ] The preview stalls at the start when the first clip is faster than 5x (seen on the emulator; the same clip later in the video previews fine, and the export is right). Check on a phone, then find the cause in CompositionPlayer
- [ ] Overlay clips with long frame gaps (screen recordings that only emit frames when the screen changes) make Media3's compositor draw a stretched copy of the overlay over the main clip during the gap. Constant-frame-rate clips (phone camera) are fine. Fix idea: re-time such overlays to a constant rate before compositing

### Photo

- [ ] Effects: blur, pixelate, glitch, posterize, pop art, vignette, sharpen, noise, duotone, with strength
- [ ] Layer blend modes
- [ ] Canvas tools: free crop and rotate of the whole picture, resize, perspective, border and frame, fit with a blurred or colored background
- [ ] Clone and stretch/warp brushes, curves, tilt-shift, motion trail
- [ ] Collage: a grid of photos (2 to 9 cells) with inner spacing, outer padding, rounded corners and a background; long-press to swap two photos; a free layout; stitch photos into one long strip
- [ ] Curved text (a bend slider), gradient text fill, text perspective
- [ ] Bubble styles: pill and double outline
- [ ] Cut-out outlines: solid, dotted, glow and offset shadow around the cut-out; shape cut-outs (circle, heart, star, triangle, diamond); a lasso to pick the area
- [ ] Draw: more pens (neon, dotted, spray, soft)
- [ ] A CC0 sticker pack

## Later / maybe

- [ ] Templates, once it's clear which layouts get used most (check patent US11989808B2 first, AGENTS.md rule 15)

## Not planned

- Accounts, cloud sync, analytics or ads
- Copying assets, code or designs from other editing apps
