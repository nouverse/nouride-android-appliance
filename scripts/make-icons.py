#!/usr/bin/env python3
"""The app's icons, from the Android app's cat (assets/icon_cat.webp, the dashboard's head with its sparkles cut).

macOS draws an app icon as a rounded square on Apple's grid (an 824-pixel body on a 1024 canvas), so the cat's head
sits on its lavender there, as it sits on a disc on Android; the Dev build's is the same cat on yellow. The menu bar's
icon is the head's silhouette as a template image (black and alpha only), which macOS draws in the menu bar's colour.

Run from the repository's root: python3 scripts/make-icons.py, then `bunx tauri icon` on each source (README).
"""
from PIL import Image, ImageDraw

CANVAS = 1024
BODY = 824
RADIUS = 185
RELEASE = "#EFEAFB"  # Android's icon_background, the cat's hoodie lavender
DEV = "#FFD84D"  # Android's debug icon_background


def app_icon(color: str, out: str) -> None:
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    plate = Image.new("RGBA", (BODY, BODY), (0, 0, 0, 0))
    ImageDraw.Draw(plate).rounded_rectangle((0, 0, BODY - 1, BODY - 1), RADIUS, fill=color)
    cat = Image.open("assets/icon_cat.webp").convert("RGBA")
    size = int(BODY * 0.84)
    cat = cat.resize((size, size), Image.LANCZOS)
    plate.alpha_composite(cat, ((BODY - size) // 2, (BODY - size) // 2))
    off = (CANVAS - BODY) // 2
    canvas.alpha_composite(plate, (off, off))
    canvas.save(out)


def tray_icon(out: str, px: int) -> None:
    cat = Image.open("assets/icon_cat.webp").convert("RGBA")
    box = cat.getbbox()
    cat = cat.crop(box)
    side = max(cat.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(cat, ((side - cat.size[0]) // 2, (side - cat.size[1]) // 2))
    alpha = square.split()[3].resize((px, px), Image.LANCZOS)
    template = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    template.putalpha(alpha)
    template.save(out)


if __name__ == "__main__":
    app_icon(RELEASE, "apps/desktop/icons/source.png")
    app_icon(DEV, "apps/desktop/icons-dev/source.png")
    tray_icon("apps/desktop/icons/tray.png", 44)
    print("icons written")
