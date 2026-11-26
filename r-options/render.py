"""Render 4 R-logo options and compose into one grid image."""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
SIZE = 320  # tile size (square)

BLACK = (0, 0, 0)
WHITE = (255, 255, 255)
ACCENT = (29, 155, 240)

FONT_CANDIDATES = {
    "rounded_black": [
        "/System/Library/Fonts/SFCompactRounded.ttf",
        "/System/Library/Fonts/SFNSRounded.ttf",
    ],
    "modern_black": [
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/HelveticaNeue.ttc",
    ],
    "serif_bold": [
        "/System/Library/Fonts/Supplemental/Georgia.ttf",
        "/System/Library/Fonts/Supplemental/Times New Roman Bold.ttf",
        "/System/Library/Fonts/Times.ttc",
    ],
    "condensed": [
        "/System/Library/Fonts/Supplemental/Impact.ttf",
        "/System/Library/Fonts/Supplemental/Oswald.ttf",
        "/System/Library/Fonts/Supplemental/Arial Narrow Bold.ttf",
        "/System/Library/Fonts/HelveticaNeue.ttc",
    ],
}


def load_font(key, size):
    for p in FONT_CANDIDATES[key]:
        if os.path.exists(p):
            try:
                if p.endswith(".ttc"):
                    for idx in (2, 1, 0, 3):
                        try:
                            return ImageFont.truetype(p, size, index=idx)
                        except Exception:
                            continue
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    return ImageFont.load_default()


def tile_r(out_name, font, stroke=6, y_offset=0, scale_x=1.0):
    img = Image.new("RGB", (SIZE, SIZE), BLACK)
    d = ImageDraw.Draw(img)
    # Measure
    bbox = d.textbbox((0, 0), "R", font=font, stroke_width=stroke)
    w = (bbox[2] - bbox[0]) * scale_x
    h = bbox[3] - bbox[1]
    x = (SIZE - w) / 2 - bbox[0] * scale_x
    y = (SIZE - h) / 2 - bbox[1] + y_offset
    if scale_x != 1.0:
        # Draw to intermediate, resize horizontally
        tmp = Image.new("RGBA", (SIZE * 2, SIZE), (0, 0, 0, 0))
        dt = ImageDraw.Draw(tmp)
        tb = dt.textbbox((0, 0), "R", font=font, stroke_width=stroke)
        tw = tb[2] - tb[0]
        th = tb[3] - tb[1]
        tx = (SIZE * 2 - tw) / 2 - tb[0]
        ty = (SIZE - th) / 2 - tb[1] + y_offset
        dt.text((tx, ty), "R", font=font, fill=WHITE,
                stroke_width=stroke, stroke_fill=WHITE)
        new_w = int(tmp.width * scale_x)
        tmp = tmp.resize((new_w, tmp.height), Image.LANCZOS)
        crop_x = (tmp.width - SIZE) // 2
        tmp = tmp.crop((crop_x, 0, crop_x + SIZE, SIZE))
        img.paste(tmp, (0, 0), tmp)
    else:
        d.text((x, y), "R", font=font, fill=WHITE,
               stroke_width=stroke, stroke_fill=WHITE)
    img.save(os.path.join(OUT, out_name))


# Option 1 — Rounded Friendly (soft, bubbly, current family)
tile_r("1-rounded.png", load_font("rounded_black", 240), stroke=10)

# Option 2 — Modern Sans (sharp tech look)
tile_r("2-modern.png", load_font("modern_black", 240), stroke=8)

# Option 3 — Classic Serif (editorial, premium)
tile_r("3-serif.png", load_font("serif_bold", 240), stroke=4)

# Option 4 — Condensed Tall (narrow, dramatic, high-impact)
tile_r("4-condensed.png", load_font("condensed", 260), stroke=4)


# Compose a 2x2 grid with labels under each tile
CELL_W, CELL_H = SIZE, SIZE + 44
PAD = 24
GRID_W = 2 * CELL_W + PAD * 3
GRID_H = 2 * CELL_H + PAD * 3

grid = Image.new("RGB", (GRID_W, GRID_H), (14, 14, 14))
gd = ImageDraw.Draw(grid)

label_font = load_font("modern_black", 22)
tiles = [
    ("1-rounded.png", "1 — Rounded (soft, friendly)"),
    ("2-modern.png", "2 — Modern (sharp tech)"),
    ("3-serif.png", "3 — Serif (editorial, premium)"),
    ("4-condensed.png", "4 — Condensed (tall, dramatic)"),
]
for i, (fname, label) in enumerate(tiles):
    col, row = i % 2, i // 2
    x = PAD + col * (CELL_W + PAD)
    y = PAD + row * (CELL_H + PAD)
    im = Image.open(os.path.join(OUT, fname))
    grid.paste(im, (x, y))
    # Label below the tile
    gd.text((x + 8, y + SIZE + 10), label, font=label_font, fill=(230, 230, 230))

grid_out = os.path.join(OUT, "all-options.png")
grid.save(grid_out)
print(f"wrote {grid_out} ({os.path.getsize(grid_out)} bytes)")
