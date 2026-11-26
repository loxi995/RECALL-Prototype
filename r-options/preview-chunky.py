"""Preview a chunky geometric R modeled on the user's reference screenshots."""
import os
from PIL import Image, ImageDraw

W, H = 480, 480
img = Image.new("RGB", (W, H), (0, 0, 0))
d = ImageDraw.Draw(img)

# Viewport 108, scale up to 480 for preview
S = W / 108.0

def p(x, y):
    return (x * S, y * S)

# Outer R: chunky stem on left + bowl top-right + strong diagonal leg
outer = [
    p(22, 18),   # top-left stem
    p(66, 18),   # top of bowl (top edge)
    p(82, 22),   # top-right bevel
    p(90, 34),   # upper-right corner
    p(90, 48),   # mid-right of bowl
    p(82, 58),   # start chamfer back
    p(68, 62),   # bowl bottom-right
    p(94, 94),   # leg end bottom-right
    p(74, 94),   # leg bottom-left
    p(50, 62),   # leg start top inside stem
    p(38, 62),   # inner stem notch
    p(38, 94),   # bottom-right of stem
    p(22, 94),   # bottom-left of stem
]
d.polygon(outer, fill=(255, 255, 255))

# Inner chevron cutout: right-pointing arrow shape inside the bowl
inner = [
    p(38, 30),
    p(58, 30),
    p(72, 40),
    p(58, 50),
    p(38, 50),
    p(48, 40),
]
d.polygon(inner, fill=(0, 0, 0))

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "chunky-preview.png")
img.save(out)
print(f"wrote {out}")
