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

## Next

- [ ] Text shadow, letter spacing, alignment
- [ ] Drag tracks up/down to reorder
- [ ] Blurred-background video canvas (Media3's blur-with-frame effect squeezed the frame on the emulator; needs another approach)

## Later / maybe

- [ ] Several video clips in a row, with transitions
- [ ] Zoomable timeline for long videos
- [ ] Filters (looks / LUTs made in-house)
- [ ] More free fonts (Google Fonts, OFL) and a CC0 sticker pack
- [ ] Templates, once it's clear which layouts get used most
- [ ] Bosnian translation

## Not planned

- Accounts, cloud sync, analytics or ads
- Copying assets, code or designs from other editing apps
