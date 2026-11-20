#!/usr/bin/env python3
"""前處理研發二樓虛擬辦公室素材 → server/src/main/resources/public/assets/lab-2f/"""

from __future__ import annotations

import os
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

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

# 工位整區（含人；人用下方多邊形依左右鄰近像素抹掉，不蓋螢幕以外的大塊）。
FURNITURE_FROM_REF = (
    (420, 290, 1055, 705),
    (145, 118, 255, 200),
    (396, 42, 536, 152),
    (672, 40, 806, 146),
)

# 空景蓋回玻璃隔間，去掉烤在裡面的人。
RESTORE_EMPTY = (
    (520, 130, 700, 410),
)

# 人的輪廓。水平方向用輪廓外的鄰近像素填，椅面與桌面留在輪廓外。
PERSON_POLYS = (
    ((458, 472), (540, 468), (552, 530), (548, 640), (496, 652), (466, 590), (454, 520)),
    ((600, 398), (690, 394), (698, 472), (678, 522), (636, 516), (612, 458), (596, 424)),
    ((706, 346), (792, 342), (804, 424), (792, 492), (744, 496), (712, 440), (700, 384)),
    ((852, 246), (958, 242), (966, 322), (954, 388), (906, 392), (894, 328), (846, 286)),
    ((870, 380), (944, 376), (948, 478), (934, 534), (892, 536), (866, 472), (862, 412)),
)


def ensure_dirs() -> None:
    OUT.mkdir(parents=True, exist_ok=True)


def paste_box(dst: Image.Image, src: Image.Image, box: tuple[int, int, int, int]) -> None:
    dst.paste(src.crop(box), box[:2])


def remove_cat(img: Image.Image) -> None:
    """只蓋橘色貓身，同一列往左找沙發的紫色椅面，避免整塊矩形色差。"""
    px = img.load()

    def sofa(r: int, g: int, b: int) -> bool:
        return b > r + 8 and b > 175 and 140 < r < 235

    def cat(r: int, g: int, b: int) -> bool:
        if r > 165 and r > b + 18 and g < 210:
            return True
        if r > 230 and g > 200 and b > 190 and r >= b:
            return True
        if r < 130 and g < 100 and b < 120 and r + 15 >= b:
            return True
        return False

    for y in range(288, 336):
        for x in range(176, 260):
            r, g, b = px[x, y]
            if not cat(r, g, b):
                continue
            src = None
            for x2 in range(x - 1, 140, -1):
                sr, sg, sb = px[x2, y]
                if sofa(sr, sg, sb) and not cat(sr, sg, sb):
                    src = (sr, sg, sb)
                    break
            if src is None:
                src = px[150, y]
            px[x, y] = src


def remove_people(img: Image.Image) -> None:
    """多邊形內每列用左右外側像素接上，把人物整塊抹掉。"""
    mask = Image.new("L", img.size, 0)
    draw = ImageDraw.Draw(mask)
    for poly in PERSON_POLYS:
        draw.polygon(poly, fill=255)
    mask = mask.filter(ImageFilter.MaxFilter(3))
    mp = mask.load()
    px = img.load()
    w, h = img.size
    for y in range(h):
        x = 0
        while x < w:
            if not mp[x, y]:
                x += 1
                continue
            start = x
            while x < w and mp[x, y]:
                x += 1
            end = x - 1
            left = px[start - 1, y] if start > 0 else None
            right = px[end + 1, y] if end + 1 < w else None
            for i in range(start, end + 1):
                if left and right:
                    px[i, y] = left if (i - start) <= (end - i) else right
                elif left:
                    px[i, y] = left
                elif right:
                    px[i, y] = right


def build_office_bg() -> Image.Image:
    empty = Image.open(RAW / "office-2f-empty.jpg").convert("RGB")
    ref = Image.open(RAW / "office-2f-desks-ref.jpg").convert("RGB")
    if empty.size != ref.size:
        ref = ref.resize(empty.size, Image.Resampling.LANCZOS)
    out = empty.copy()
    remove_cat(out)
    for box in FURNITURE_FROM_REF:
        paste_box(out, ref, box)
    for box in RESTORE_EMPTY:
        paste_box(out, empty, box)
    remove_people(out)
    w, h = out.size
    if w > MAX_BG_W:
        nh = int(h * MAX_BG_W / w)
        out = out.resize((MAX_BG_W, nh), Image.Resampling.LANCZOS)
    return out


def _near_white(rgb: tuple[int, int, int], tol: int = 12) -> bool:
    r, g, b = rgb
    return r >= 255 - tol and g >= 255 - tol and b >= 255 - tol


def extract_chibi(slice_img: Image.Image, target_h: int) -> Image.Image:
    """只從邊緣淹掉白底。上衣、眼白等內部淺色留住。"""
    rgba = slice_img.convert("RGBA")
    w, h = rgba.size
    px = rgba.load()
    seen = [[False] * w for _ in range(h)]
    q: deque[tuple[int, int]] = deque()
    for x in range(w):
        q.append((x, 0))
        q.append((x, h - 1))
    for y in range(h):
        q.append((0, y))
        q.append((w - 1, y))
    while q:
        x, y = q.popleft()
        if x < 0 or y < 0 or x >= w or y >= h or seen[y][x]:
            continue
        r, g, b, _a = px[x, y]
        if not _near_white((r, g, b)):
            continue
        seen[y][x] = True
        px[x, y] = (r, g, b, 0)
        q.append((x + 1, y))
        q.append((x - 1, y))
        q.append((x, y + 1))
        q.append((x, y - 1))
    # 貼邊的近白抗鋸齒，只剝與透明相鄰的像素，不進封閉輪廓裡
    for _ in range(2):
        kill: list[tuple[int, int]] = []
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                if a == 0 or not _near_white((r, g, b), tol=6):
                    continue
                if any(
                    0 <= nx < w and 0 <= ny < h and px[nx, ny][3] == 0
                    for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                ):
                    kill.append((x, y))
        for x, y in kill:
            r, g, b, _a = px[x, y]
            px[x, y] = (r, g, b, 0)
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
    resized = cropped.resize((nw, target_h), Image.Resampling.LANCZOS)
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
    cut = int(w * 0.34)
    trimmed = rgb.crop((cut, 0, w, h))
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
    bust_h = int(side * 0.78)
    bust = crop.crop((0, 0, side, bust_h))
    tw = 480
    th = int(bust_h * tw / side)
    bust = bust.resize((tw, th), Image.Resampling.LANCZOS)
    return bust


def save_webp(img: Image.Image, path: Path, quality: int = 82) -> None:
    img.save(path, "WEBP", quality=quality, method=6)


def _shirt_punch_pixels(im: Image.Image) -> int:
    """上衣被當成白底挖掉時，會留下高 RGB、低 alpha 的洞。"""
    w, h = im.size
    px = im.load()
    n = 0
    for y in range(int(h * 0.40), int(h * 0.68)):
        for x in range(int(w * 0.30), int(w * 0.74)):
            r, g, b, a = px[x, y]
            if a < 40 and r > 210 and g > 210 and b > 200:
                n += 1
    return n


def main() -> None:
    ensure_dirs()
    bg = build_office_bg()
    save_webp(bg, OUT / "office-bg.webp", quality=85)

    chibis = build_chibis()
    for name, im in chibis.items():
        im.save(OUT / f"chibi-{name}.png", "PNG", optimize=True)
        if im.size[1] != 220 or im.mode != "RGBA":
            raise SystemExit(f"{name} expected 220 RGBA, got {im.size} {im.mode}")
        punch = _shirt_punch_pixels(im)
        if punch > 80:
            raise SystemExit(f"{name} shirt looks punched out ({punch} px)")

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
