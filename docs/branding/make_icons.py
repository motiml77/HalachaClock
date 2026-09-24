"""
Builds every app icon from the one logo source.

    python docs/branding/make_icons.py [path/to/logo_source.png]

Input: the square logo as delivered — a blue rounded square on BLACK
(docs/branding/logo_source.png by default). Needs Pillow and numpy.

Outputs, all overwritten:
  docs/branding/logo_transparent.png        the blue square, black made transparent
  desktop/.../branding/logo.png  (256)      window, tray and reminder icon
  desktop/.../branding/app.ico   (16..256)  the installer and .exe icon
  app/.../mipmap-*/ic_launcher_foreground.png   Android adaptive icon
  app/.../mipmap-*/ic_launcher_monochrome.png   Android 13+ themed icon
  docs/branding/play_store_512.png          Play listing icon (full square)
  docs/branding/preview_circle.png / preview_squircle.png   launcher previews

WHY THE ANDROID ICON IS NOT SIMPLY THE ROUNDED SQUARE
An adaptive icon is cut to the LAUNCHER's shape (circle on a Pixel, squircle
on a Samsung, ...), from a 108dp canvas of which only the middle 72dp is ever
shown. Shipping the rounded square as-is would draw a square inside the
launcher's circle, or clip the artwork. Instead the blue is carried edge to
edge (the logo's own interior, extended outward by normalized convolution) and
the artwork is scaled so its farthest point, the tips of the book, stays inside
the mask on every shape. The launcher's own shape becomes the blue frame.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/branding/logo_source.png"

# The blue rounded square, in source pixels, measured from the delivered
# 1254px logo: edges at the half-coverage point of the anti-aliased border,
# and a circular corner of r=245 matches its profile row by row to ~2px.
SRC_SIZE = 1254
RECT = (51.7, 56.4, 1197.3, 1193.6)
CORNER = 245.0
# Pulled in so no pixel that was blended with the black survives at the edge.
INSET = 1.5

# The gold artwork's centre and its farthest point from that centre (the book's
# tips), for fitting it inside the launcher mask.
ART_CENTER = (620.5, 629.0)
ART_RADIUS = 627.0


def rounded_mask(size, rect, radius, ss=4):
    """Anti-aliased rounded-rect coverage, supersampled ss x ss."""
    w, h = size
    big = Image.new("L", (w * ss, h * ss), 0)
    x0, y0, x1, y1 = (v * ss for v in rect)
    ImageDraw.Draw(big).rounded_rectangle((x0, y0, x1 - 1, y1 - 1), radius * ss, fill=255)
    return np.asarray(big.resize((w, h), Image.LANCZOS)).astype(np.float64) / 255.0


def gauss_blur(a, sigma):
    """Separable Gaussian via FFT on each axis; a is (H, W) or (H, W, C)."""
    def blur_axis(x, axis):
        n = x.shape[axis]
        pad = int(3 * sigma) + 1
        widths = [(0, 0)] * x.ndim
        widths[axis] = (pad, pad)
        xp = np.pad(x, widths, mode="constant")
        m = xp.shape[axis]
        k = np.exp(-0.5 * ((np.arange(m) - m // 2) / sigma) ** 2)
        k /= k.sum()
        k = np.fft.ifftshift(k)
        shape = [1] * x.ndim
        shape[axis] = m
        out = np.real(np.fft.ifft(np.fft.fft(xp, axis=axis) * np.fft.fft(k).reshape(shape), axis=axis))
        sl = [slice(None)] * x.ndim
        sl[axis] = slice(pad, pad + n)
        return out[tuple(sl)]
    return blur_axis(blur_axis(a, 0), 1)


def save_png(arr, path, size=None):
    im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA" if arr.shape[2] == 4 else "RGB")
    if size:
        im = im.resize((size, size), Image.LANCZOS)
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, optimize=True)
    print("wrote", path.relative_to(ROOT), im.size)
    return im


def main():
    src = Image.open(SRC).convert("RGB")
    assert src.size == (SRC_SIZE, SRC_SIZE), f"expected the {SRC_SIZE}px logo, got {src.size}"
    rgb = np.asarray(src).astype(np.float64)

    # ---- 1. The rounded square alone, black made transparent -------------
    x0, y0, x1, y1 = RECT
    cover = rounded_mask(src.size, (x0 + INSET, y0 + INSET, x1 - INSET, y1 - INSET), CORNER - INSET)
    rgba = np.dstack([rgb, cover * 255.0])
    # Square crop around the shape, so every icon made from it is centred.
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    half = int(np.ceil(max(x1 - x0, y1 - y0) / 2)) + 1
    box = (int(cx) - half, int(cy) - half, int(cx) - half + 2 * half, int(cy) - half + 2 * half)
    square = rgba[box[1]:box[3], box[0]:box[2]]
    transparent = save_png(square, ROOT / "docs/branding/logo_transparent.png")

    # ---- 2. Desktop ------------------------------------------------------
    branding = ROOT / "desktop/src/main/resources/branding"
    transparent.resize((256, 256), Image.LANCZOS).save(branding / "logo.png", optimize=True)
    print("wrote", (branding / "logo.png").relative_to(ROOT), (256, 256))
    ico_sizes = [16, 24, 32, 48, 64, 128, 256]
    transparent.resize((256, 256), Image.LANCZOS).save(
        branding / "app.ico", sizes=[(s, s) for s in ico_sizes],
    )
    print("wrote", (branding / "app.ico").relative_to(ROOT), ico_sizes)

    # ---- 3. Full-bleed blue: the logo's interior carried outward ---------
    # Pad generously, then fill everything outside the shape (and its own
    # rim highlight and bevel, 40px deep) by normalized convolution: a
    # weighted average of the nearby interior, so the extension continues
    # the logo's own gradient instead of a flat colour with a seam.
    pad = 520
    big = np.pad(rgb, ((pad, pad), (pad, pad), (0, 0)), mode="constant")
    inner = rounded_mask(
        (SRC_SIZE + 2 * pad, SRC_SIZE + 2 * pad),
        (x0 + pad + 40, y0 + pad + 40, x1 + pad - 40, y1 + pad - 40),
        CORNER - 40,
    )
    feather = gauss_blur(inner, 12)
    # Coarse to fine: a wide kernel reaches the far corners of the canvas
    # (where a narrow one's weights underflow to nothing and the fill would
    # turn black), a narrow one keeps the local colour next to the shape.
    # Only the blue ground feeds the fill — averaging the gold in as well
    # tints the far corners olive.
    blue = np.clip((big[..., 2] - big[..., 0] - 20) / 40, 0, 1)
    source = inner * blue
    fill = None
    for sigma in (500, 200, 70):
        weight = gauss_blur(source, sigma)
        f = gauss_blur(big * source[..., None], sigma) / np.maximum(weight, 1e-12)[..., None]
        trust = np.clip(weight / 1e-3, 0, 1)[..., None]
        fill = f if fill is None else f * trust + fill * (1 - trust)
    bleed = big * feather[..., None] + fill * (1 - feather[..., None])

    def canvas(center, side):
        """Square [side] crop of the bleed, centred on a source-pixel point."""
        ccx, ccy = center[0] + pad, center[1] + pad
        l, t = int(round(ccx - side / 2)), int(round(ccy - side / 2))
        return bleed[t:t + side, l:l + side]

    # ---- 4. Android adaptive icon ---------------------------------------
    # 108dp canvas; the mask shows the middle 72dp (radius 36dp on a circle).
    # The book's tips land at 34.5dp from centre: inside every mask shape.
    side = int(round(ART_RADIUS / 34.5 * 108))
    fg = canvas(ART_CENTER, side)
    fg_rgba = np.dstack([fg, np.full(fg.shape[:2], 255.0)])
    res = ROOT / "app/src/main/res"
    densities = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    for d, px in densities.items():
        save_png(fg_rgba, res / f"mipmap-{d}/ic_launcher_foreground.png", px)

    # Themed icon: the gold artwork alone as an alpha mask; the system tints it.
    r, g, b = fg[..., 0], fg[..., 1], fg[..., 2]
    # Warm and bright enough to be the gold, including the ring's shaded side;
    # the blue ground and the dark shadows under the book score zero.
    goldness = np.clip(((r - b) - 40) / 70, 0, 1) * np.clip((r - 80) / 60, 0, 1)
    mono = np.dstack([np.zeros_like(r), np.zeros_like(r), np.zeros_like(r), goldness * 255.0])
    for d, px in densities.items():
        save_png(mono, res / f"mipmap-{d}/ic_launcher_monochrome.png", px)

    # ---- 5. Play Store: full square, Google rounds the corners itself ----
    play = canvas((cx, cy), 2 * half)
    save_png(play, ROOT / "docs/branding/play_store_512.png", 512)

    # ---- 6. Launcher previews (what a circle / squircle launcher shows) --
    fg_img = Image.fromarray(np.clip(fg, 0, 255).astype(np.uint8)).resize((432, 432), Image.LANCZOS)
    shown = fg_img.crop((72, 72, 360, 360)).resize((432, 432), Image.LANCZOS)
    for name, shape in (("circle", "ellipse"), ("squircle", "rounded")):
        m = Image.new("L", (432 * 4, 432 * 4), 0)
        dr = ImageDraw.Draw(m)
        if shape == "ellipse":
            dr.ellipse((0, 0, 432 * 4 - 1, 432 * 4 - 1), fill=255)
        else:
            dr.rounded_rectangle((0, 0, 432 * 4 - 1, 432 * 4 - 1), int(432 * 4 * 0.30), fill=255)
        m = m.resize((432, 432), Image.LANCZOS)
        out = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
        out.paste(shown, (0, 0), m)
        out.save(ROOT / f"docs/branding/preview_{name}.png", optimize=True)
        print("wrote", f"docs/branding/preview_{name}.png")


if __name__ == "__main__":
    main()
