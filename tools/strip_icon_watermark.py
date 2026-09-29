"""Strip the tool-added "AI生成 / WORKBUDDY" overlay from ImageGen output.

The overlay sits in a fixed bottom-right band and is always lighter than the
artwork background in this icon set, so it can be removed by luminance-gating
inside that band instead of cropping (cropping would de-center the subject).

Usage: strip_icon_watermark.py <src.png> <dst.png> [<src2> <dst2> ...]
"""

import sys
from PIL import Image, ImageFilter

# Bottom-right band that holds the overlay, in pixels of a 1024x1024 image.
BAND_LEFT = 800
BAND_TOP = 915
# Clean strip used to sample the flat background colour.
SAMPLE_BOX = (950, 600, 1024, 700)

LUM_OFFSET = 40  # how much lighter than the background a pixel must be to be overlay


def luminance(px):
    r, g, b = px[0], px[1], px[2]
    return 0.299 * r + 0.587 * g + 0.114 * b


def clean(src, dst):
    im = Image.open(src).convert("RGB")
    w, h = im.size

    # Sample the background from a guaranteed-clean strip.
    sx0, sy0, sx1, sy1 = SAMPLE_BOX
    strip = im.crop((sx0, sy0, sx1, sy1))
    px = list(strip.getdata())
    px.sort(key=luminance)
    bg = px[len(px) // 2]  # median by luminance
    bg_lum = luminance(bg)

    bx0 = int(BAND_LEFT / 1024 * w)
    by0 = int(BAND_TOP / 1024 * h)
    band = im.crop((bx0, by0, w, h))

    mask = Image.new("L", band.size, 0)
    bp = band.load()
    mp = mask.load()
    for y in range(band.size[1]):
        for x in range(band.size[0]):
            mp[x, y] = 255 if luminance(bp[x, y]) > bg_lum + LUM_OFFSET else 0

    # Grow the mask so anti-aliased overlay edges are covered too.
    mask = mask.filter(ImageFilter.MaxFilter(5))
    mask = mask.filter(ImageFilter.GaussianBlur(1.5))

    patch = Image.new("RGB", band.size, bg)
    band.paste(patch, (0, 0), mask)
    im.paste(band, (bx0, by0))

    im.save(dst)
    print(f"{src} -> {dst}  size={w}x{h}  bg={bg}")


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) % 2:
        raise SystemExit(__doc__)
    for i in range(0, len(args), 2):
        clean(args[i], args[i + 1])
