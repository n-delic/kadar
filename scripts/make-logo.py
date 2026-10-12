# Usage: uv run --with fonttools --with pillow python scripts/make-logo.py app/src/main/res/font/jakarta.ttf docs/brand
# Builds Kadar's logo: SVG mark, horizontal SVG lockups (text as outlines) and PNG renders.
import sys, io
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from PIL import Image, ImageDraw, ImageFont

font_path, out = sys.argv[1], sys.argv[2]
LIME, TEAL, WHITE, INK = "#C6F432", "#2DE1C2", "#FFFFFF", "#0E0F11"

# The mark on a 108-unit grid, same geometry as the launcher icon.
CORNERS = "M33,45V39a6,6 0,0 1,6 -6h6 M63,33h6a6,6 0,0 1,6 6v6 M75,63v6a6,6 0,0 1,-6 6h-6 M45,75h-6a6,6 0,0 1,-6 -6v-6"
PLAY = "M48,44.5L64,54L48,63.5Z"

def mark_group(x, y, size, tile=True):
    s = size / 66  # the 66-unit safe zone fills the tile
    g = f'<g transform="translate({x},{y}) scale({s}) translate(-21,-21)">'
    if tile:
        g += f'<rect x="21" y="21" width="66" height="66" rx="16" fill="url(#bg)"/>'
    g += (f'<path d="{CORNERS}" fill="none" stroke="{LIME}" stroke-width="5" stroke-linecap="round"/>'
          f'<path d="{PLAY}" fill="{TEAL}" stroke="{TEAL}" stroke-width="3" stroke-linejoin="round"/></g>')
    return g

DEFS = ('<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
        '<stop offset="0" stop-color="#26282D"/><stop offset="1" stop-color="#0E0F11"/></linearGradient></defs>')

# Wordmark as outlines in Plus Jakarta Sans ExtraBold, so the SVG needs no font.
var = TTFont(font_path)
inst = instantiateVariableFont(var, {"wght": 800})
glyphs, cmap, upm = inst.getGlyphSet(), inst.getBestCmap(), inst["head"].unitsPerEm
hmtx = inst["hmtx"]

def word_path(text, height, x0, baseline, tracking=-0.02):
    scale = height / upm
    pen = SVGPathPen(glyphs)
    x = 0.0
    for ch in text:
        name = cmap[ord(ch)]
        tp = TransformPen(pen, (scale, 0, 0, -scale, x0 + x, baseline))
        glyphs[name].draw(tp)
        x += (hmtx[name][0] + tracking * upm) * scale
    return pen.getCommands(), x

def lockup(text_color):
    h = 120
    word_h = 96   # font size in px (em)
    d, w = word_path("Kadar", word_h, h + 34, h * 0.5 + word_h * 0.36)
    total = int(h + 34 + w + 8)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total} {h}" width="{total}" height="{h}">'
            f'{DEFS}{mark_group(0, 0, h)}<path d="{d}" fill="{text_color}"/></svg>')

open(f"{out}/kadar-mark.svg", "w").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">{DEFS}{mark_group(0, 0, 512)}</svg>')
open(f"{out}/kadar-logo-light-text.svg", "w").write(lockup(WHITE))
open(f"{out}/kadar-logo-dark-text.svg", "w").write(lockup(INK))
print("svgs ok")
