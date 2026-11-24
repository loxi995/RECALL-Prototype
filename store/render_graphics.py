"""
Render Play Store graphics for RECALL:
  - app-icon-512.png       (512x512, hi-res store icon)
  - feature-graphic-1024x500.png (1024x500 banner)

Run with:
  python3 render_graphics.py
Outputs land next to this script.
"""
import os
from PIL import Image, ImageDraw, ImageFont

FONT_CANDIDATES = [
    "/System/Library/Fonts/SFCompactRounded.ttf",
    "/System/Library/Fonts/SFNSRounded.ttf",
    "/System/Library/Fonts/HelveticaNeue.ttc",
    "/System/Library/Fonts/Helvetica.ttc",
    "/System/Library/Fonts/SFNS.ttf",
    "/Library/Fonts/Arial Bold.ttf",
]

BLACK = (0, 0, 0)
WHITE = (255, 255, 255)
ACCENT = (29, 155, 240)    # X-style blue, matches app
MUTED = (113, 118, 123)


def load_font(size, bold=True):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                if path.endswith(".ttc"):
                    # Try common bold indices for .ttc files
                    for idx in (1, 2, 0):
                        try:
                            return ImageFont.truetype(path, size, index=idx)
                        except Exception:
                            continue
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


def draw_centered_text(draw, text, font, cx, cy, fill, stroke=0):
    bbox = draw.textbbox((0, 0), text, font=font, stroke_width=stroke)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x = cx - w / 2 - bbox[0]
    y = cy - h / 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=fill,
              stroke_width=stroke, stroke_fill=fill)


def render_app_icon():
    """512x512 Play Store icon: black tile with large white R."""
    size = 512
    img = Image.new("RGB", (size, size), BLACK)
    draw = ImageDraw.Draw(img)
    font = load_font(360, bold=True)
    # Heavy stroke faux-bolds the R so it reads as a solid logo, not a letter.
    draw_centered_text(draw, "R", font, size / 2, size / 2 + 8, WHITE, stroke=14)
    out = os.path.join(os.path.dirname(__file__), "app-icon-512.png")
    img.save(out, "PNG")
    print(f"wrote {out} ({os.path.getsize(out)} bytes)")


def render_feature_graphic():
    """
    1024x500 feature graphic: large R on the left, 'RECALL' wordmark + tagline
    on the right. Gradient background from pure black to a deep navy tilt.
    """
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), BLACK)
    draw = ImageDraw.Draw(img)

    # Subtle vertical gradient (black at top -> near-black blueish at bottom)
    top = (0, 0, 0)
    bottom = (10, 24, 36)
    for y in range(H):
        t = y / (H - 1)
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        draw.line([(0, y), (W, y)], fill=(r, g, b))

    # Big R on the left
    r_font = load_font(380, bold=True)
    draw_centered_text(draw, "R", r_font, 200, H / 2 + 6, WHITE, stroke=12)

    # Wordmark 'RECALL' on the right
    name_font = load_font(110, bold=True)
    draw.text((380, 155), "RECALL", font=name_font, fill=WHITE,
              stroke_width=4, stroke_fill=WHITE)

    # Tagline under the wordmark
    tag_font = load_font(34, bold=False)
    draw.text(
        (384, 300),
        "Recover the audio you missed.",
        font=tag_font,
        fill=MUTED,
    )

    # Small accent bar between the R and the wordmark
    draw.rectangle([355, 160, 361, 340], fill=ACCENT)

    out = os.path.join(os.path.dirname(__file__), "feature-graphic-1024x500.png")
    img.save(out, "PNG")
    print(f"wrote {out} ({os.path.getsize(out)} bytes)")


if __name__ == "__main__":
    render_app_icon()
    render_feature_graphic()
    print("done.")
