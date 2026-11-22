#!/usr/bin/env python3
"""Repair alpha on secretary status sprites (idle / typing / sleep).

Removes near-white fringe pixels then inpaints interior holes from opaque neighbors.
"""
from __future__ import annotations

import argparse
import sys
from collections import deque
from pathlib import Path
from typing import List, Optional, Tuple

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parents[1]
ASSET_DIR = REPO / "server/src/main/resources/public/assets/private-team"

STATUS_SPRITES = (
    "secretary-idle-tea.webp",
    "secretary-typing-a.webp",
    "secretary-sleep.webp",
)

EXTRA_SPRITES = (
    "secretary-typing-b.webp",
    "secretary-wave.webp",
    "secretary-yeah.webp",
)


def dilate(mask: np.ndarray, radius: int = 1) -> np.ndarray:
    h, w = mask.shape
    out = mask.copy()
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return out
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            yy = ys + dy
            xx = xs + dx
            ok = (yy >= 0) & (yy < h) & (xx >= 0) & (xx < w)
            out[yy[ok], xx[ok]] = True
    return out


def adjacent_to(mask: np.ndarray) -> np.ndarray:
    return dilate(mask, 1) & ~mask


def flood_from_edges(mask: np.ndarray) -> np.ndarray:
    h, w = mask.shape
    seen = np.zeros((h, w), dtype=bool)
    q: deque = deque()

    def push(x: int, y: int) -> None:
        if 0 <= x < w and 0 <= y < h and mask[y, x] and not seen[y, x]:
            seen[y, x] = True
            q.append((x, y))

    for x in range(w):
        push(x, 0)
        push(x, h - 1)
    for y in range(h):
        push(0, y)
        push(w - 1, y)
    while q:
        x, y = q.popleft()
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            push(nx, ny)
    return seen


def enclosed_holes(alpha: np.ndarray, thresh: int = 10) -> np.ndarray:
    transparent = alpha < thresh
    return transparent & ~flood_from_edges(transparent)


def content_bbox(alpha: np.ndarray, thresh: int = 16) -> Optional[Tuple[int, int, int, int]]:
    ys, xs = np.where(alpha > thresh)
    if len(xs) == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def is_sheet_rgb(r: int, g: int, b: int) -> bool:
    return (
        r >= 248
        and g >= 247
        and b >= 243
        and abs(r - g) <= 5
        and abs(g - b) <= 8
        and abs(r - b) <= 10
    )


def is_brown_rgb(r: int, g: int, b: int) -> bool:
    return (g - b) >= 12 and (r - b) >= 22 and b < 205 and r < 245


def near_white_mask(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    r = rgb[:, :, 0]
    g = rgb[:, :, 1]
    b = rgb[:, :, 2]
    return (alpha > 0) & (alpha < 250) & (r >= 230) & (g >= 228) & (b >= 224)


def inpaint_holes(alpha: np.ndarray, rgb: np.ndarray, max_passes: int = 96) -> Tuple[np.ndarray, np.ndarray]:
    a = alpha.copy()
    c = rgb.copy()
    h, w = a.shape
    for _ in range(max_passes):
        pending = enclosed_holes(a, 10)
        if not int(pending.sum()):
            break
        q = deque((int(x), int(y)) for y, x in zip(*np.where(pending)))
        filled = 0
        while q:
            x, y = q.popleft()
            if not pending[y, x]:
                continue
            n_c: List[np.ndarray] = []
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if 0 <= nx < w and 0 <= ny < h and a[ny, nx] > 180:
                    n_c.append(c[ny, nx])
            if len(n_c) < 1:
                continue
            rr, gg, bb = map(int, np.median(np.stack(n_c, axis=0), axis=0))
            if is_sheet_rgb(rr, gg, bb) or is_brown_rgb(rr, gg, bb):
                pending[y, x] = False
                continue
            a[y, x] = 255
            c[y, x] = (rr, gg, bb)
            pending[y, x] = False
            filled += 1
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if 0 <= nx < w and 0 <= ny < h and pending[ny, nx]:
                    q.append((nx, ny))
        if filled == 0:
            break
    return a, c


def scrub_desk_scraps(alpha: np.ndarray, rgb: np.ndarray) -> np.ndarray:
    a = alpha.copy()
    r = rgb[:, :, 0].astype(np.int16)
    g = rgb[:, :, 1].astype(np.int16)
    b = rgb[:, :, 2].astype(np.int16)
    bright_skin = (r > 232) & (b > 176) & ((r - g) < 32)
    tan = (
        (r > 188)
        & (r < 250)
        & (g > 150)
        & (b < 196)
        & ((g - b) >= 10)
        & ((r - b) >= 22)
        & ((r - g) >= 30)
        & ~bright_skin
    )
    bb = content_bbox(a, 20)
    if bb is None:
        return a
    y_cut = bb[1] + int((bb[3] - bb[1]) * 0.62)
    low = np.zeros(a.shape, dtype=bool)
    low[y_cut:, :] = True
    for _ in range(4):
        fringe = (a > 0) & tan & low & adjacent_to(a == 0)
        if not fringe.any():
            break
        a[fringe] = 0
    return a


def strip_bottom_band(alpha: np.ndarray, rgb: np.ndarray, band_px: int, y_min: Optional[int] = None) -> np.ndarray:
    a = alpha.copy()
    h = a.shape[0]
    y0 = y_min if y_min is not None else max(0, h - band_px)
    r = rgb[:, :, 0]
    g = rgb[:, :, 1]
    b = rgb[:, :, 2]
    bot = np.zeros_like(a, dtype=bool)
    bot[y0:, :] = True
    drop = bot & (r >= 228) & (g >= 226) & (b >= 222) & (a > 24)
    a[drop] = 0
    return a


def heal_left_foot(alpha: np.ndarray, rgb: np.ndarray) -> np.ndarray:
    a = alpha.copy()
    bb = content_bbox(a, 24)
    if bb is None:
        return a
    _, _, _, y1 = bb
    h, w = a.shape
    region = np.zeros_like(a, dtype=bool)
    region[int(y1 * 0.52) : y1, : min(w, 150)] = True
    fringe = region & near_white_mask(rgb, a) & adjacent_to(a == 0)
    a[fringe] = 0
    gap = region & (a < 12) & dilate(a > 200, 2)
    for y, x in zip(*np.where(gap)):
        if y >= h - 44:
            continue
        n = sum(
            1
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
            if 0 <= nx < w and 0 <= ny < h and a[ny, nx] > 180
        )
        if n >= 2:
            a[y, x] = 255
    return a


def snap_alpha(alpha: np.ndarray) -> np.ndarray:
    a = alpha.copy()
    a[a < 12] = 0
    a[a > 242] = 255
    return a


def fix_sprite(im: Image.Image, sleep: bool = False) -> Image.Image:
    arr = np.array(im.convert("RGBA"))
    rgb = arr[:, :, :3].copy()
    alpha = arr[:, :, 3]
    fringe = near_white_mask(rgb, alpha)
    alpha[fringe] = 0
    alpha, rgb = inpaint_holes(alpha, rgb, max_passes=120 if sleep else 96)
    alpha = scrub_desk_scraps(alpha, rgb)
    alpha = heal_left_foot(alpha, rgb)
    if sleep:
        alpha = strip_bottom_band(alpha, rgb, 40, y_min=728)
    else:
        alpha = strip_bottom_band(alpha, rgb, 40)
    alpha = snap_alpha(alpha)
    return Image.fromarray(np.dstack([rgb, alpha]).astype(np.uint8), "RGBA")


def save_webp(im: Image.Image, path: Path) -> None:
    im.save(path, format="WEBP", quality=88, method=6)


def interior_hole_count(alpha: np.ndarray) -> int:
    bb = content_bbox(alpha, 40)
    if bb is None:
        return 0
    x0, y0, x1, y1 = bb
    sub = enclosed_holes(alpha, 10)
    return int(sub[y0:y1, x0:x1].sum())


def waist_hole_pixels(alpha: np.ndarray) -> int:
    bb = content_bbox(alpha, 40)
    if bb is None:
        return 0
    x0, y0, x1, y1 = bb
    mid = slice(y0 + int((y1 - y0) * 0.42), y0 + int((y1 - y0) * 0.72))
    inner = enclosed_holes(alpha, 10)
    return int(inner[mid, x0:x1].sum())


def check_sprite(path: Path) -> List[str]:
    im = Image.open(path)
    if im.size != (624, 768):
        return [f"{path.name}: expected 624x768 got {im.size}"]
    arr = np.array(im.convert("RGBA"))
    alpha = arr[:, :, 3]
    rgb = arr[:, :, :3]
    errs: List[str] = []
    border = np.zeros_like(alpha, dtype=bool)
    border[0, :] = border[-1, :] = border[:, 0] = border[:, -1] = True
    if int((alpha[border] > 8).sum()) > 0:
        errs.append(f"{path.name}: non-transparent image border")
    holes = interior_hole_count(alpha)
    hole_limit = 230 if path.name == "secretary-sleep.webp" else 100
    if holes > hole_limit:
        errs.append(f"{path.name}: {holes} interior enclosed holes (limit {hole_limit})")
    waist = waist_hole_pixels(alpha)
    if waist > 120:
        errs.append(f"{path.name}: {waist} waist enclosed holes (limit 120)")
    h = alpha.shape[0]
    sl = slice(h - 40, h)
    bad = (
        (rgb[sl, :, 0] >= 228)
        & (rgb[sl, :, 1] >= 226)
        & (rgb[sl, :, 2] >= 222)
        & (alpha[sl] > 24)
    )
    if int(bad.sum()) > 25:
        errs.append(f"{path.name}: bottom near-white fringe {int(bad.sum())} px (limit 25)")
    semi_mask = (
        (alpha > 40)
        & (alpha < 245)
        & (rgb[:, :, 0] >= 230)
        & (rgb[:, :, 1] >= 230)
        & (rgb[:, :, 2] >= 230)
    )
    if int(semi_mask.sum()) > 30:
        errs.append(f"{path.name}: visible near-white fringe {int(semi_mask.sum())} px (limit 30)")
    return errs


def run_fix(extra: bool = False) -> None:
    names = list(STATUS_SPRITES)
    if extra:
        names.extend(EXTRA_SPRITES)
    for name in names:
        path = ASSET_DIR / name
        if not path.exists():
            print(f"skip missing {name}", file=sys.stderr)
            continue
        sleep = name == "secretary-sleep.webp"
        im = fix_sprite(Image.open(path), sleep=sleep)
        save_webp(im, path)
        print(f"wrote {name}")


def run_check() -> int:
    errs: List[str] = []
    for name in STATUS_SPRITES:
        errs.extend(check_sprite(ASSET_DIR / name))
    if errs:
        for e in errs:
            print(e, file=sys.stderr)
        return 1
    print("ok")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify status sprites only")
    parser.add_argument("--extra", action="store_true", help="also fix typing-b, wave, yeah")
    args = parser.parse_args()
    if args.check:
        return run_check()
    run_fix(extra=args.extra)
    return run_check()


if __name__ == "__main__":
    raise SystemExit(main())