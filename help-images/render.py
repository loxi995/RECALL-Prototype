"""Render 7 annotated mockup PNGs for the HOW TO USE RECALL dialog."""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 960, 380

BG = (0, 0, 0)
TEXT = (255, 255, 255)
MUTED = (113, 118, 123)
ACCENT = (29, 155, 240)
STATUS_LIVE = (0, 186, 124)
DIVIDER = (47, 51, 54)
ARROW_COL = (255, 206, 86)  # yellow — stands out against dark app
BALANCE_BG = (10, 26, 42)


def load_font(size, bold=True):
    candidates = [
        "/System/Library/Fonts/HelveticaNeue.ttc",
        "/System/Library/Fonts/SFNS.ttf",
    ]
    for p in candidates:
        if not os.path.exists(p):
            continue
        try:
            if p.endswith(".ttc"):
                for idx in (2, 1, 0):
                    try:
                        return ImageFont.truetype(p, size, index=idx)
                    except Exception:
                        continue
            return ImageFont.truetype(p, size)
        except Exception:
            continue
    return ImageFont.load_default()


def card(d, x, y, w, h, radius=24, fill=None, outline=DIVIDER, outline_w=2):
    d.rounded_rectangle(
        [x, y, x + w, y + h],
        radius=radius, fill=fill, outline=outline, width=outline_w,
    )


def text_at(d, x, y, s, font, color, anchor="la"):
    d.text((x, y), s, font=font, fill=color, anchor=anchor)


def arrow(d, start, end, color=ARROW_COL, thickness=16, head=30):
    """Straight arrow from start to end with a triangular head."""
    d.line([start, end], fill=color, width=thickness)
    import math
    dx, dy = end[0] - start[0], end[1] - start[1]
    length = math.hypot(dx, dy) or 1
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    tip = end
    base = (end[0] - ux * head, end[1] - uy * head)
    p1 = (base[0] + px * head * 0.55, base[1] + py * head * 0.55)
    p2 = (base[0] - px * head * 0.55, base[1] - py * head * 0.55)
    d.polygon([tip, p1, p2], fill=color)


def tap_label(d, x, y, line1, line2=None, color=ARROW_COL, anchor="la"):
    f = load_font(26)
    text_at(d, x, y, line1, f, color, anchor=anchor)
    if line2:
        text_at(d, x, y + 34, line2, f, color, anchor=anchor)


def make(name, fn):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    fn(img, d)
    img.save(os.path.join(OUT, name))
    print(f"wrote {name}")


# ========== 1. Turn on listening ==========
def s1(img, d):
    cx, cy, cw, ch = 60, 100, 820, 200
    card(d, cx, cy, cw, ch)
    text_at(d, cx + 36, cy + 50, "Always listening", load_font(34), TEXT)
    text_at(d, cx + 36, cy + 110, "OFF", load_font(22), MUTED)
    # Switch (OFF)
    sw_x, sw_y = cx + cw - 190, cy + 70
    d.rounded_rectangle(
        [sw_x, sw_y, sw_x + 130, sw_y + 60], radius=30, fill=(74, 74, 74)
    )
    d.ellipse([sw_x + 5, sw_y + 5, sw_x + 55, sw_y + 55], fill=TEXT)
    # Arrow
    arrow(d, (sw_x - 220, sw_y + 30), (sw_x - 30, sw_y + 30))
    tap_label(d, sw_x - 225, sw_y - 32, "TAP TO ENABLE", anchor="la")


# ========== 2. Let it capture ==========
def s2(img, d):
    cx, cy, cw, ch = 60, 100, 820, 200
    card(d, cx, cy, cw, ch)
    # Green dot
    d.ellipse([cx + 36, cy + 54, cx + 68, cy + 86], fill=STATUS_LIVE)
    text_at(d, cx + 88, cy + 48, "Listening", load_font(32), TEXT)
    text_at(d, cx + 36, cy + 118, "Captured so far · 2 min 15 sec",
            load_font(22), MUTED)
    # Highlight ring around status
    d.rounded_rectangle(
        [cx + 24, cy + 40, cx + cw - 24, cy + 100],
        radius=16, outline=ARROW_COL, width=4,
    )
    # Arrow + label
    arrow(d, (cx + cw - 280, cy + ch + 40), (cx + cw - 180, cy + ch - 10))
    tap_label(d, cx + cw - 300, cy + ch + 50,
              "KEEP RECALL", "RUNNING", anchor="la")


# ========== 3. Pick how far back ==========
def s3(img, d):
    text_at(d, 80, 90, "How far back", load_font(22), MUTED)
    # Pill button
    bx, by, bw, bh = 80, 140, 800, 100
    d.rounded_rectangle(
        [bx, by, bx + bw, by + bh], radius=50,
        fill=None, outline=DIVIDER, width=3,
    )
    text_at(d, bx + bw // 2, by + bh // 2, "2 min 30 sec",
            load_font(32), TEXT, anchor="mm")
    # Arrow
    arrow(d, (bx + bw // 2, by + bh + 80), (bx + bw // 2, by + bh + 20))
    tap_label(d, bx + bw // 2, by + bh + 100,
              "TAP TO CHOOSE DURATION", anchor="ma")


# ========== 4. Recover the audio ==========
def s4(img, d):
    # Big white primary button
    bx, by, bw, bh = 80, 120, 800, 120
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=60, fill=TEXT)
    text_at(d, bx + bw // 2, by + bh // 2, "RECOVER AUDIO",
            load_font(36), BG, anchor="mm")
    # Arrow
    arrow(d, (bx + bw // 2 + 240, by + bh + 90), (bx + bw // 2 + 160, by + bh + 20))
    tap_label(d, bx + bw // 2 + 250, by + bh + 110,
              "TAP TO RECOVER", anchor="la")


# ========== 5. Play & save ==========
def s5(img, d):
    bx, bw, bh = 80, 800, 80
    # PLAY
    by1 = 60
    d.rounded_rectangle([bx, by1, bx + bw, by1 + bh],
                        radius=40, outline=DIVIDER, width=3)
    text_at(d, bx + bw // 2, by1 + bh // 2, "PLAY LAST RECOVERY",
            load_font(26), TEXT, anchor="mm")
    arrow(d, (bx + bw + 60, by1 + bh // 2), (bx + bw + 10, by1 + bh // 2))
    # SAVE
    by2 = 180
    d.rounded_rectangle([bx, by2, bx + bw, by2 + bh],
                        radius=40, outline=DIVIDER, width=3)
    text_at(d, bx + bw // 2, by2 + bh // 2, "SAVE TO DEVICE",
            load_font(26), TEXT, anchor="mm")
    arrow(d, (bx + bw + 60, by2 + bh // 2), (bx + bw + 10, by2 + bh // 2))
    # Label
    tap_label(d, bx + bw + 80, 300, "LISTEN,", "THEN SAVE", anchor="la")


# ========== 6. Buy minutes (+BUY MIN top-right) ==========
def s6(img, d):
    # Simulated top of app with RECALL title + BUY pill
    text_at(d, 60, 70, "RECALL", load_font(60), TEXT)
    # +BUY MIN pill (top-right)
    pw, ph = 230, 86
    px, py = W - pw - 60, 60
    d.rounded_rectangle(
        [px, py, px + pw, py + ph], radius=22,
        fill=BG, outline=ACCENT, width=4,
    )
    text_at(d, px + pw // 2, py + ph // 2, "+ BUY MIN",
            load_font(28), ACCENT, anchor="mm")
    # Arrow pointing up-right to pill
    arrow(d, (px - 180, py + ph + 150), (px - 30, py + ph // 2 + 10))
    tap_label(d, px - 200, py + ph + 160,
              "TAP TO ADD", "MORE MINUTES", anchor="la")


# ========== 7. Extend buffer window ==========
def s7(img, d):
    cx, cy, cw, ch = 60, 100, 820, 200
    card(d, cx, cy, cw, ch)
    text_at(d, cx + 36, cy + 32, "BUFFER WINDOW", load_font(20), MUTED)
    text_at(d, cx + 36, cy + 72, "1 day of audio", load_font(36), TEXT)
    text_at(d, cx + 36, cy + 130, "Default · tap to extend further",
            load_font(20), MUTED)
    # EXTEND label on the right (ASCII arrow — font-safe in the mockup)
    text_at(d, cx + cw - 36, cy + ch // 2, "EXTEND >",
            load_font(26), ACCENT, anchor="rm")
    # Highlight the card to show it's tappable
    d.rounded_rectangle(
        [cx - 6, cy - 6, cx + cw + 6, cy + ch + 6],
        radius=28, outline=ARROW_COL, width=4,
    )
    # Arrow + label (kept inside frame)
    arrow(d, (cx + cw // 2, cy + ch + 70), (cx + cw - 120, cy + ch + 6))
    tap_label(d, cx + cw // 2 - 20, cy + ch + 80,
              "TAP CARD TO EXTEND", anchor="ra")


make("help_1_switch.png", s1)
make("help_2_capture.png", s2)
make("help_3_duration.png", s3)
make("help_4_recover.png", s4)
make("help_5_playsave.png", s5)
make("help_6_buymin.png", s6)
make("help_7_buffer.png", s7)
print("done.")
