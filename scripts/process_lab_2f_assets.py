#!/usr/bin/env python3
"""前處理研發二樓虛擬辦公室素材 → server/src/main/resources/public/assets/lab-2f/"""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RAW = Path(os.environ.get("LAB_2F_RAW", str(Path.home() / "PunchClock-oki" / "assets-lab2f-raw")))
OUT = ROOT / "server" / "src" / "main" / "resources" / "public" / "assets" / "lab-2f"
MAX_BG_W = 1600

CHIBI_NAMES = ("oki", "tiger", "kitten", "reviewer", "artist3d")
PORTRAIT_FILES = {
    "oki": "portrait-oki.jpg",
    "tiger": "portrait-tiger.jpg",
    "kitten": "portrait-kitten.jpg",
    "reviewer": "portrait-reviewer.jpg",
    "artist3d": "portrait-artist3d.jpg",
}

# 參考圖中 Q 版大致站位（用空景覆蓋烤死的小人）
CHIBI_ERASE_BOXES = (
    (95, 395, 230, 655),
    (265, 405, 395, 665),
    (435, 400, 565, 670),
    (605, 405, 735, 665),
    (780, 395, 920, 660),
)

# 中央工位／白板／書櫃：從參考圖貼到空景的矩形（避開地板小人帶）
DESK_PASTE_BOXES = (
    (185, 155, 505, 395),
    (395, 145, 1125, 385),
    (900, 120, 1210, 520),
)


def ensure_dirs() -> None:
    OUT.mkdir(parents=True, exist_ok=True)


def resize_if_needed(img: Image.Image, max_w: int) -> Image.Image:
    w, h = img.size
    if w <= max_w:
        return img
    nh = int(h * max_w / w)
    return img.resize((max_w, nh), Image.Resampling.LANCZOS)


def build_office_bg() -> Image.Image:
    empty = Image.open(RAW / "office-2f-empty.jpg").convert("RGB")
    ref = Image.open(RAW / "office-2f-desks-ref.jpg").convert("RGB")
    if empty.size != ref.size:
        ref = ref.resize(empty.size, Image.Resampling.LANCZOS)
    out = empty.copy()
    for box in DESK_PASTE_BOXES:
        patch = ref.crop(box)
        out.paste(patch, box[:2])
    for box in CHIBI_ERASE_BOXES:
        patch = empty.crop(box)
        out.paste(patch, box[:2])
    # 保險：中央地板帶與空景 diff 大於門檻處，若仍像角色（飽和）則再貼空景
    diff = ImageChops.difference(out, empty).convert("L")
    diff = diff.filter(ImageFilter.GaussianBlur(radius=2))
    pix = diff.load()
    epix = empty.load()
    opix = out.load()
    w, h = out.size
    for y in range(360, min(h, 680)):
        for x in range(80, w - 80):
            if pix[x, y] > 28:
                opix[x, y] = epix[x, y]
    out = resize_if_needed(out, MAX_BG_W)
    return out


def white_alpha(rgb: tuple[int, int, int], tol: int = 42) -> bool:
    r, g, b = rgb
    return r >= 255 - tol and g >= 255 - tol and b >= 255 - tol


def extract_chibi(slice_img: Image.Image, target_h: int) -> Image.Image:
    rgba = slice_img.convert("RGBA")
    w, h = rgba.size
    data = rgba.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = data[x, y]
            if white_alpha((r, g, b)):
                data[x, y] = (r, g, b, 0)
            elif r > 240 and g > 240 and b > 240:
                data[x, y] = (r, g, b, 0)
    # 去孤立點
    alpha = rgba.split()[3]
    alpha = alpha.filter(ImageFilter.MedianFilter(size=3))
    rgba.putalpha(alpha)
    bbox = rgba.getbbox()
    if not bbox:
        return rgba
    cropped = rgba.crop(bbox)
    cw, ch = cropped.size
    scale = target_h / ch
    nw = max(1, int(cw * scale))
    nh = target_h
    resized = cropped.resize((nw, nh), Image.Resampling.LANCZOS)
    return resized


def build_chibis(target_h: int = 220) -> dict[str, Image.Image]:
    sheet = Image.open(RAW / "chibis-sheet.jpg").convert("RGB")
    sw, sh = sheet.size
    n = len(CHIBI_NAMES)
    slot_w = sw // n
    out: dict[str, Image.Image] = {}
    for i, name in enumerate(CHIBI_NAMES):
        left = i * slot_w
        right = sw if i == n - 1 else (i + 1) * slot_w
        pad = 4
        crop = sheet.crop((left + pad, pad, right - pad, sh - pad))
        out[name] = extract_chibi(crop, target_h)
    return out


def trim_artist_left(img: Image.Image) -> Image.Image:
    """裁掉左側「3D 美術師」字與果汁機圖示。"""
    rgb = img.convert("RGB")
    w, h = rgb.size
    # 左側約 34% 多為字與圖示
    cut = int(w * 0.34)
    trimmed = rgb.crop((cut, 0, w, h))
    # 若右側仍有色塊殘影，用左緣平均牆色抹平窄
    avg = trimmed.resize((1, h), Image.Resampling.BILINEAR)
    out = trimmed.copy()
    strip = int(trimmed.size[0] * 0.04)
    for x in range(strip):
        out.paste(avg, (x, 0))
    return out


def crop_portrait_bust(img: Image.Image, is_artist: bool = False) -> Image.Image:
    if is_artist:
        img = trim_artist_left(img)
    w, h = img.size
    side = min(w, h)
    left = (w - side) // 2
    top = max(0, int(h * 0.02))
    crop = img.crop((left, top, left + side, top + side))
    # 半身：保留上方 78%
    bust_h = int(side * 0.78)
    bust = crop.crop((0, 0, side, bust_h))
    tw = 480
    th = int(bust_h * tw / side)
    bust = bust.resize((tw, th), Image.Resampling.LANCZOS)
    return bust


def save_webp(img: Image.Image, path: Path, quality: int = 82) -> None:
    img.save(path, "WEBP", quality=quality, method=6)


def main() -> None:
    ensure_dirs()
    bg = build_office_bg()
    save_webp(bg, OUT / "office-bg.webp", quality=85)

    chibis = build_chibis()
    for name, im in chibis.items():
        im.save(OUT / f"chibi-{name}.png", "PNG", optimize=True)

    for key, fname in PORTRAIT_FILES.items():
        src = Image.open(RAW / fname).convert("RGB")
        bust = crop_portrait_bust(src, is_artist=(key == "artist3d"))
        save_webp(bust, OUT / f"portrait-{key}.webp", quality=88)

    print("lab-2f assets written to", OUT)
    for p in sorted(OUT.iterdir()):
        im = Image.open(p)
        print(f"  {p.name}: {im.size[0]}x{im.size[1]} {im.mode}")


if __name__ == "__main__":
    main()