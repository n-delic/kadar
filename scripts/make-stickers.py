#!/usr/bin/env python3
"""Draws Kadar's bundled sticker pack into app/src/main/assets/stickers.

Every sticker is flat vector-style art: bold fills with one thick dark outline,
drawn at twice the output size and scaled down for smooth edges. The outline is
the shape's silhouette dilated minus itself, so shapes that overlap (a bubble
with its tail, a skull with its jaw) get one clean contour instead of seams.
Own work (Kadar contributors), GPL-3.0-or-later; see ATTRIBUTION.md.
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/stickers"

SIZE = 1024  # drawn at, downscaled to 512
OUTLINE = 44  # silhouette dilation, at SIZE
INK = (26, 26, 31, 255)

FONT = ROOT / "app/src/main/res/font/anton.ttf"


def canvas():
    return Image.new("L", (SIZE, SIZE), 0)


def outline_of(mask, width=OUTLINE):
    """1 where the dilated silhouette is outside [mask], 0 elsewhere."""
    big = mask.filter(ImageFilter.MaxFilter(width if width % 2 == 1 else width + 1))
    outside = Image.new("L", (SIZE, SIZE), 255)
    outside.paste(0, (0, 0), mask)
    return Image.composite(outside, Image.new("L", (SIZE, SIZE), 0), big)


def sticker(mask, color, width=OUTLINE):
    """Fill [mask] in [color] with an INK silhouette outline [width] wide."""
    s = Image.new("RGBA", (SIZE, SIZE), 0)
    s.paste(color, (0, 0), mask)
    s.paste(INK, (0, 0), outline_of(mask, width))
    return s


def polygon(points, width=None):
    m = canvas()
    ImageDraw.Draw(m).polygon([(x * SIZE, y * SIZE) for x, y in points], fill=255)
    return m


def ellipse(box):
    m = canvas()
    ImageDraw.Draw(m).ellipse([c * SIZE for c in box], fill=255)
    return m


def union(*masks):
    m = canvas()
    for other in masks:
        m.paste(255, (0, 0), other)
    return m


def star(points=5, inner=0.45, start=-90):
    pts = []
    import math
    for i in range(points * 2):
        r = 0.48 if i % 2 == 0 else 0.48 * inner
        a = math.radians(start + i * 180.0 / points)
        pts.append((0.5 + r * math.cos(a), 0.5 + r * math.sin(a)))
    return polygon(pts)


def text(text, fill, rotate=0, size=330):
    """A word in the meme font, outlined; [rotate] tilts it for fun."""
    img = Image.new("RGBA", (SIZE, SIZE), 0)
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(str(FONT), size)
    box = d.textbbox((0, 0), text, font=f, stroke_width=26)
    at = ((SIZE - (box[2] - box[0])) / 2 - box[0], (SIZE - (box[3] - box[1])) / 2 - box[1])
    d.text(at, text, font=f, fill=fill, stroke_width=26, stroke_fill=INK)
    if rotate:
        img = img.rotate(rotate, resample=Image.BICUBIC, expand=True)
        # Re-center the expanded canvas.
        w, h = img.size
        left, top = max(0, (w - SIZE) // 2), max(0, (h - SIZE) // 2)
        img = img.crop((left, top, min(w, left + SIZE), min(h, top + SIZE)))
        base = Image.new("RGBA", (SIZE, SIZE), 0)
        base.paste(img, ((SIZE - img.size[0]) // 2, (SIZE - img.size[1]) // 2), img)
        img = base
    return img


LIME = (200, 241, 53, 255)
YELLOW = (255, 214, 0, 255)
ORANGE = (255, 138, 0, 255)
RED = (240, 62, 62, 255)
PINK = (255, 105, 150, 255)
CYAN = (60, 200, 230, 255)
GREEN = (80, 200, 90, 255)
PURPLE = (150, 90, 240, 255)
GOLD = (255, 190, 40, 255)
WHITE = (255, 255, 255, 255)


def smiley_mask():
    return ellipse((0.08, 0.08, 0.92, 0.92))


def face(s, mouth=True):
    d = ImageDraw.Draw(s)
    for x in (0.36, 0.64):
        d.ellipse([(x - 0.055) * SIZE, 0.36 * SIZE, (x + 0.055) * SIZE, 0.52 * SIZE], fill=INK)
    if mouth:
        d.arc([0.28 * SIZE, 0.38 * SIZE, 0.72 * SIZE, 0.78 * SIZE], 20, 160, fill=INK, width=int(SIZE * 0.045))
    return s


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    pack = {}

    pack["star"] = sticker(star(), YELLOW)
    pack["heart"] = sticker(polygon(heart_points()), PINK)
    pack["bolt"] = sticker(polygon(BOLT), YELLOW)
    pack["crown"] = sticker(union(polygon(CROWN), polygon([(0.1, 0.72), (0.9, 0.72), (0.9, 0.88), (0.1, 0.88)])), GOLD)
    pack["gem"] = sticker(polygon(GEM), CYAN)
    boom = sticker(star(points=12, inner=0.62), ORANGE, width=36)
    d = ImageDraw.Draw(boom)
    d.regular_polygon((SIZE * 0.5, SIZE * 0.5, SIZE * 0.30), 12, rotation=15, fill=YELLOW)
    pack["boom"] = boom
    pack["smiley"] = face(sticker(smiley_mask(), YELLOW))
    cool = face(sticker(smiley_mask(), YELLOW), mouth=False)
    d = ImageDraw.Draw(cool)
    d.rounded_rectangle([0.17 * SIZE, 0.38 * SIZE, 0.47 * SIZE, 0.52 * SIZE], SIZE * 0.03, fill=INK)
    d.rounded_rectangle([0.53 * SIZE, 0.38 * SIZE, 0.83 * SIZE, 0.52 * SIZE], SIZE * 0.03, fill=INK)
    d.line([0.47 * SIZE, 0.43 * SIZE, 0.53 * SIZE, 0.43 * SIZE], fill=INK, width=int(SIZE * 0.035))
    d.arc([0.38 * SIZE, 0.5 * SIZE, 0.62 * SIZE, 0.72 * SIZE], 30, 150, fill=INK, width=int(SIZE * 0.035))
    pack["cool"] = cool
    alien = sticker(ellipse((0.18, 0.06, 0.82, 0.86)), GREEN)
    d = ImageDraw.Draw(alien)
    for cx in (0.38, 0.62):
        d.ellipse([(cx - 0.09) * SIZE, 0.3 * SIZE, (cx + 0.09) * SIZE, 0.58 * SIZE], fill=INK)
    pack["alien"] = alien

    flame = sticker(polygon(FLAME_POINTS), ORANGE)
    inner = flame_points(0.55)
    d = ImageDraw.Draw(flame)
    d.polygon([(x * SIZE, y * SIZE) for x, y in inner], fill=YELLOW)
    pack["flame"] = flame

    skull_mask = union(ellipse((0.16, 0.06, 0.84, 0.72)), polygon([(0.22, 0.6), (0.78, 0.6), (0.78, 0.94), (0.22, 0.94)]))
    skull = sticker(skull_mask, WHITE)
    d = ImageDraw.Draw(skull)
    for cx in (0.36, 0.64):
        d.ellipse([(cx - 0.11) * SIZE, 0.3 * SIZE, (cx + 0.11) * SIZE, 0.5 * SIZE], fill=INK)
    d.polygon([(0.5, 0.54), (0.44, 0.64), (0.56, 0.64)], fill=INK)
    for x in (0.42, 0.5, 0.58):
        d.line([x * SIZE, 0.76 * SIZE, x * SIZE, 0.9 * SIZE], fill=INK, width=int(SIZE * 0.025))
    pack["skull"] = skull

    speech_mask = union(ellipse((0.08, 0.1, 0.92, 0.72)), polygon([(0.3, 0.62), (0.52, 0.62), (0.22, 0.94)]))
    pack["speech"] = sticker(speech_mask, WHITE)
    thought = sticker(ellipse((0.1, 0.08, 0.9, 0.68)), WHITE)
    thought = Image.alpha_composite(thought, sticker(ellipse((0.16, 0.68, 0.38, 0.86)), WHITE, width=30))
    thought = Image.alpha_composite(thought, sticker(ellipse((0.06, 0.84, 0.2, 0.98)), WHITE, width=26))
    pack["thought"] = thought

    pack["arrow"] = sticker(polygon(ARROW), RED)
    planet = sticker(ellipse((0.1, 0.16, 0.9, 0.84)), PURPLE)
    d = ImageDraw.Draw(planet)
    d.ellipse([-0.06 * SIZE, 0.36 * SIZE, 1.06 * SIZE, 0.64 * SIZE], outline=(220, 200, 255, 255), width=int(SIZE * 0.05))
    pack["planet"] = planet
    exclaim = union(polygon([(0.4, 0.06), (0.6, 0.06), (0.55, 0.62), (0.45, 0.62)]), ellipse((0.42, 0.72, 0.58, 0.9)))
    pack["exclaim"] = sticker(exclaim, RED)

    pack["lol"] = text("LOL", LIME, rotate=8)
    pack["wow"] = text("WOW", ORANGE, rotate=-6)
    pack["100"] = text("100", CYAN, rotate=0)

    for name, img in pack.items():
        img.resize((512, 512), Image.LANCZOS).save(OUT / f"{name}.png")
    print(f"wrote {len(pack)} stickers to {OUT}")


def heart_points():
    import math
    pts = []
    for i in range(64):
        t = math.radians(i * 360 / 64)
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((0.5 + x / 40, 0.5 - y / 40))
    return pts


def flame_points(k=1.0):
    """A teardrop leaning left, its inner copy scaled toward the base for the hot core."""
    pts = [
        (0.50, 0.04), (0.62, 0.16), (0.66, 0.30), (0.78, 0.40), (0.84, 0.58),
        (0.78, 0.76), (0.64, 0.90), (0.50, 0.94), (0.36, 0.90), (0.22, 0.76),
        (0.16, 0.58), (0.24, 0.42), (0.34, 0.34), (0.40, 0.18),
    ]
    return [(0.5 + (x - 0.5) * k, 0.94 + (y - 0.94) * k) for x, y in pts]


BOLT = [(0.58, 0.02), (0.22, 0.56), (0.44, 0.56), (0.34, 0.98), (0.78, 0.40), (0.52, 0.40), (0.70, 0.02)]
CROWN = [(0.08, 0.70), (0.08, 0.30), (0.28, 0.52), (0.50, 0.16), (0.72, 0.52), (0.92, 0.30), (0.92, 0.70)]
GEM = [(0.30, 0.14), (0.70, 0.14), (0.92, 0.44), (0.50, 0.92), (0.08, 0.44)]
ARROW = [(0.62, 0.04), (0.96, 0.38), (0.80, 0.42), (0.86, 0.56), (0.60, 0.48), (0.34, 0.86), (0.14, 0.66), (0.40, 0.30), (0.52, 0.20)]
FLAME_POINTS = [
    (0.50, 0.04), (0.62, 0.16), (0.66, 0.30), (0.78, 0.40), (0.84, 0.58),
    (0.78, 0.76), (0.64, 0.90), (0.50, 0.94), (0.36, 0.90), (0.22, 0.76),
    (0.16, 0.58), (0.24, 0.42), (0.34, 0.34), (0.40, 0.18),
]

if __name__ == "__main__":
    main()
