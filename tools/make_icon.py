#!/usr/bin/env python3
"""Builds the launcher icon from ADD/images/znak.png, the pedestrian crossing sign.

The figure is taken from the sign itself rather than redrawn: the sign's black pixels are split
into connected components, the zebra stripes are dropped, and what remains — torso, leading leg,
head, trailing arm — is the pedestrian. It is scaled to sit inside the adaptive icon's 66dp safe zone with
a solid bar for the road under it, standing in for the stripes, which turn to noise at icon size.

Run from the project root:  python3 tools/make_icon.py
"""

from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw

SOURCE = Path("ADD/images/znak.png")
RES = Path("app/src/main/res")

# Adaptive icons are a 108dp canvas of which only the middle 66dp is guaranteed to survive the
# launcher's mask. Everything drawn here stays inside that.
CANVAS = 108
SAFE_TOP = 21

# Where the figure starts and ends, and where the road goes, in canvas units. The figure keeps a
# little air inside the safe zone rather than filling it to the brim: level with the top of the
# zone it read as too big for the launcher's circle, and stood taller than the icons beside it.
FIGURE_TOP = 27
FIGURE_BOTTOM = 79
ROAD_Y = 84
ROAD_HALF_WIDTH = 28
ROAD_THICKNESS = 4.5

# The tab icon is the same figure at 24dp, without the road: a bar under a 24dp glyph is a smudge.
TAB_DP = 24
TAB_DENSITIES = {
    "mdpi": 24,
    "hdpi": 36,
    "xhdpi": 48,
    "xxhdpi": 72,
    "xxxhdpi": 96,
}

# One PNG per density: the launcher picks the one it needs. 108dp at each scale factor.
DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# A pixel this dark is part of the sign's black artwork.
BLACK = 90

# Below this many pixels a component is a compression artefact, not part of the drawing.
MIN_COMPONENT = 5_000

# The zebra stripes all sit in the bottom fifth and are short; the figure's parts are neither.
STRIPE_TOP_FRACTION = 0.70
STRIPE_MAX_HEIGHT_FRACTION = 0.20


def components(mask, width, height):
    """Every connected run of set pixels, as (points, bounding box)."""
    seen = [[False] * width for _ in range(height)]
    found = []
    for y in range(height):
        for x in range(width):
            if not mask[y][x] or seen[y][x]:
                continue
            queue = deque([(x, y)])
            seen[y][x] = True
            points = []
            while queue:
                cx, cy = queue.popleft()
                points.append((cx, cy))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = cx + dx, cy + dy
                    if 0 <= nx < width and 0 <= ny < height and mask[ny][nx] and not seen[ny][nx]:
                        seen[ny][nx] = True
                        queue.append((nx, ny))
            xs = [p[0] for p in points]
            ys = [p[1] for p in points]
            found.append((points, (min(xs), min(ys), max(xs), max(ys))))
    return found


def figure_pixels(image):
    """The pedestrian's pixels, with the zebra stripes and any speckle left behind."""
    width, height = image.size
    px = image.load()
    mask = [
        [all(channel < BLACK for channel in px[x, y][:3]) for x in range(width)]
        for y in range(height)
    ]

    kept = []
    for points, (x0, y0, x1, y1) in components(mask, width, height):
        if len(points) < MIN_COMPONENT:
            continue
        is_stripe = (
            y0 > height * STRIPE_TOP_FRACTION
            and (y1 - y0) < height * STRIPE_MAX_HEIGHT_FRACTION
        )
        if is_stripe:
            continue
        kept.extend(points)
    return kept


def build(source: Path) -> Image.Image:
    """The 432px foreground: white figure and road on transparency."""
    image = Image.open(source).convert("RGB")
    pixels = figure_pixels(image)
    if not pixels:
        raise SystemExit(f"{source}: found no figure to cut out")

    x0 = min(p[0] for p in pixels)
    x1 = max(p[0] for p in pixels)
    y0 = min(p[1] for p in pixels)
    y1 = max(p[1] for p in pixels)

    # Redraw the kept pixels alone, so the stripes cannot come along in the crop.
    cut = Image.new("L", (x1 - x0 + 1, y1 - y0 + 1), 0)
    cut_px = cut.load()
    for x, y in pixels:
        cut_px[x - x0, y - y0] = 255

    # Fit by height: the figure is much taller than it is wide.
    scale = DENSITIES["xxxhdpi"] / CANVAS
    target_height = (FIGURE_BOTTOM - FIGURE_TOP) * scale
    target_width = cut.width * (target_height / cut.height)
    figure = cut.resize((round(target_width), round(target_height)), Image.LANCZOS)

    size = DENSITIES["xxxhdpi"]
    alpha = Image.new("L", (size, size), 0)
    alpha.paste(figure, (round(size / 2 - figure.width / 2), round(FIGURE_TOP * scale)))

    draw = ImageDraw.Draw(alpha)
    half = ROAD_THICKNESS * scale / 2
    draw.rounded_rectangle(
        [
            (CANVAS / 2 - ROAD_HALF_WIDTH) * scale,
            ROAD_Y * scale - half,
            (CANVAS / 2 + ROAD_HALF_WIDTH) * scale,
            ROAD_Y * scale + half,
        ],
        radius=half,
        fill=255,
    )

    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    white.putalpha(alpha)
    return white


def build_tab_icon(source: Path) -> Image.Image:
    """The figure alone, filling a square glyph — tinted by the theme wherever it is drawn."""
    image = Image.open(source).convert("RGB")
    pixels = figure_pixels(image)

    x0 = min(p[0] for p in pixels)
    x1 = max(p[0] for p in pixels)
    y0 = min(p[1] for p in pixels)
    y1 = max(p[1] for p in pixels)

    cut = Image.new("L", (x1 - x0 + 1, y1 - y0 + 1), 0)
    cut_px = cut.load()
    for x, y in pixels:
        cut_px[x - x0, y - y0] = 255

    size = TAB_DENSITIES["xxxhdpi"]
    # A hair of margin so the glyph does not touch its own bounds.
    height = round(size * 0.92)
    width = round(cut.width * (height / cut.height))
    figure = cut.resize((width, height), Image.LANCZOS)

    alpha = Image.new("L", (size, size), 0)
    alpha.paste(figure, (round(size / 2 - width / 2), round(size / 2 - height / 2)))

    glyph = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    glyph.putalpha(alpha)
    return glyph


def main():
    if not SOURCE.exists():
        raise SystemExit(f"{SOURCE} not found — run this from the project root")

    master = build(SOURCE)
    for density, size in DENSITIES.items():
        out_dir = RES / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        master.resize((size, size), Image.LANCZOS).save(out_dir / "ic_launcher_foreground.png")
        print(f"mipmap-{density}/ic_launcher_foreground.png  {size}x{size}")

    tab = build_tab_icon(SOURCE)
    for density, size in TAB_DENSITIES.items():
        out_dir = RES / f"drawable-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        tab.resize((size, size), Image.LANCZOS).save(out_dir / "ic_walker.png")
        print(f"drawable-{density}/ic_walker.png  {size}x{size}")


if __name__ == "__main__":
    main()
