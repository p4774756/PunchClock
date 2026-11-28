#!/usr/bin/env python3
"""Repair alpha on secretary status sprites and celebrate clap frames.

Removes near-white fringe pixels then inpaints interior holes from opaque neighbors.

Clap sprites are checked for a clean white cardigan (no enclosed cloth holes) and
sane size/dimensions/alpha. Natural transparent gaps between hair locks must stay
open — do not run fill_interior_transparent / fix_clap_sprite on them.
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
    "secretary-read.webp",
    "secretary-phone.webp",
)

WAIST_GAP_SPRITES = frozenset(
    {
        "secretary-yeah.webp",
        "secretary-idle-tea.webp",
        "secretary-read.webp",
        "secretary-phone.webp",
    }
)

REF_WAIST_SPRITE = "secretary-clap-a.webp"

CLAP_SPRITES = ("secretary-clap-a.webp", "secretary-clap-b.webp")
CLAP_SIZE_MIN = 22_000
CLAP_SIZE_MAX = 62_000
# Sanity cap on all enclosed transparent pixels, including natural hair-lock gaps.
CLAP_HAIR_GAP_MAX = 2_500

# 比讚跳躍時鍵盤與大腿間的透明腰洞（與 issue 描述列區間對齊）
CELEBRATE_WAIST_BOX = (552, 590, 80, 126)
WAIST_GAP_BOX = (500, 620, 60, 180)


def dilate(mask: np.ndarray, radius: int = 1) -> np.ndarray:
    h, w = mask.shape
    out = mask.copy()
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return out
    r2 = radius * radius
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            if dy * dy + dx * dx > r2:
                continue
            yy = ys + dy
            xx = xs + dx
            ok = (yy >= 0) & (yy < h) & (xx >= 0) & (xx < w)
            out[yy[ok], xx[ok]] = True
    return out


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


def is_white_cloth_rgb(r: int, g: int, b: int) -> bool:
    """Sheer / solid white cardigan (high value, low saturation)."""
    return r >= 220 and g >= 218 and b >= 210 and (max(r, g, b) - min(r, g, b)) <= 28


def cardigan_enclosed_hole_count(
    alpha: np.ndarray, rgb: np.ndarray, thresh: int = 10
) -> int:
    """Count enclosed transparent pixels whose opaque neighbors are white cloth.

    Hair-lock gaps (neighbors are pink hair or skin) are ignored so they can
    stay transparent. A non-zero count means the white cardigan was punched.
    """
    holes = enclosed_holes(alpha, thresh)
    if not int(holes.sum()):
        return 0
    h, w = alpha.shape
    seen = np.zeros_like(holes)
    total = 0
    ys, xs = np.where(holes)
    for y0, x0 in zip(ys, xs):
        if seen[y0, x0]:
            continue
        q: deque = deque([(int(x0), int(y0))])
        seen[y0, x0] = True
        size = 0
        cloth = 0
        other = 0
        while q:
            x, y = q.popleft()
            size += 1
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if not (0 <= nx < w and 0 <= ny < h):
                    continue
                if holes[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    q.append((nx, ny))
                elif alpha[ny, nx] >= 40:
                    r, g, b = (int(rgb[ny, nx, 0]), int(rgb[ny, nx, 1]), int(rgb[ny, nx, 2]))
                    if is_white_cloth_rgb(r, g, b):
                        cloth += 1
                    else:
                        other += 1
        if cloth > other and cloth >= 4:
            total += size
    return total


def fill_interior_transparent(
    alpha: np.ndarray, rgb: np.ndarray, thresh: int = 12, max_rounds: int = 500
) -> Tuple[np.ndarray, np.ndarray]:
    """Fill every interior transparent pixel (including white-shirt holes)."""
    a = alpha.copy()
    c = rgb.copy()
    h, w = a.shape
    for _ in range(max_rounds):
        transparent = a < thresh
        interior = transparent & ~flood_from_edges(transparent)
        if not int(interior.sum()):
            break
        pending = interior.copy()
        q: deque = deque((int(x), int(y)) for y, x in zip(*np.where(pending)))
        filled = 0
        while q:
            x, y = q.popleft()
            if not pending[y, x]:
                continue
            n_c: List[np.ndarray] = []
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if 0 <= nx < w and 0 <= ny < h and a[ny, nx] >= thresh:
                    n_c.append(c[ny, nx])
            if not n_c:
                continue
            rr, gg, bb = map(int, np.median(np.stack(n_c, axis=0), axis=0))
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


def clear_nearwhite_fringe(alpha: np.ndarray, rgb: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    a = alpha.copy()
    c = rgb.copy()
    exterior = flood_from_edges(a < 12)

    def semi_mask() -> np.ndarray:
        return (
            (a > 40)
            & (a < 245)
            & (c[:, :, 0] >= 230)
            & (c[:, :, 1] >= 230)
            & (c[:, :, 2] >= 230)
        )

    drop = semi_mask() & dilate(exterior, 2)
    if int(drop.sum()):
        a[drop] = 0
    body = a > 200
    for _ in range(8):
        snap = semi_mask() & dilate(body, 1)
        if not int(snap.sum()):
            break
        a[snap] = 255
        body = a > 200
    return a, c


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


def is_matte_rgb(rgb: np.ndarray) -> np.ndarray:
    """Cool or neutral near-white, including roughly (225, 230, 237)."""
    r = rgb[:, :, 0].astype(np.int16)
    g = rgb[:, :, 1].astype(np.int16)
    b = rgb[:, :, 2].astype(np.int16)
    mn = np.minimum(np.minimum(r, g), b)
    mx = np.maximum(np.maximum(r, g), b)
    chroma = mx - mn
    cool = (mn >= 205) & (chroma <= 36) & ((b + 8) >= r)
    pure = (mn >= 242) & (chroma <= 18)
    return cool | pure


def is_saturated_skin(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    r = rgb[:, :, 0].astype(np.int16)
    g = rgb[:, :, 1].astype(np.int16)
    b = rgb[:, :, 2].astype(np.int16)
    return (alpha > 180) & (r > 175) & (g > 130) & ((r - b) >= 28) & ~is_matte_rgb(rgb)


def _paint_from_skin(rgb: np.ndarray, alpha: np.ndarray, fill: np.ndarray) -> None:
    """Color new pixels from nearby skin so the fill does not stay near-white."""
    r = rgb[:, :, 0].astype(np.int16)
    b = rgb[:, :, 2].astype(np.int16)
    strong = is_saturated_skin(rgb, alpha) & ((r - b) >= 30)
    soft = (alpha > 160) & (r > 150) & ((r - b) >= 14) & ~is_matte_rgb(rgb)
    ys, xs = np.where(fill)
    h, w = alpha.shape
    for y, x in zip(ys.tolist(), xs.tolist()):
        cols = []
        for dy in range(-10, 11):
            yy = y + dy
            if yy < 0 or yy >= h:
                continue
            row = strong[yy]
            for dx in range(-10, 11):
                xx = x + dx
                if 0 <= xx < w and row[xx]:
                    cols.append(rgb[yy, xx])
        if not cols:
            for dy in range(-12, 13):
                yy = y + dy
                if yy < 0 or yy >= h:
                    continue
                row = soft[yy]
                for dx in range(-12, 13):
                    xx = x + dx
                    if 0 <= xx < w and row[xx]:
                        cols.append(rgb[yy, xx])
        if not cols:
            continue
        rgb[y, x] = np.median(np.stack(cols, axis=0), axis=0).astype(np.uint8)
        alpha[y, x] = 255


def _clear_matte_specks(alpha: np.ndarray, rgb: np.ndarray, region: np.ndarray) -> None:
    matte_all = is_matte_rgb(rgb)
    matte = matte_all & (alpha >= 40) & region
    if not int(matte.sum()):
        return
    seen = np.zeros_like(matte)
    h, w = alpha.shape
    ys, xs = np.where(matte)
    for y0, x0 in zip(ys, xs):
        if seen[y0, x0]:
            continue
        q = deque([(int(x0), int(y0))])
        seen[y0, x0] = True
        comp: List[Tuple[int, int]] = []
        touches_body = False
        while q:
            x, y = q.popleft()
            comp.append((x, y))
            for nx, ny in (
                (x - 1, y),
                (x + 1, y),
                (x, y - 1),
                (x, y + 1),
                (x - 1, y - 1),
                (x + 1, y - 1),
                (x - 1, y + 1),
                (x + 1, y + 1),
            ):
                if not (0 <= nx < w and 0 <= ny < h):
                    continue
                if matte[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    q.append((nx, ny))
                elif alpha[ny, nx] > 160 and not matte_all[ny, nx]:
                    touches_body = True
        if len(comp) <= 80 and not touches_body:
            for x, y in comp:
                alpha[y, x] = 0


def _fill_foot_slits(alpha: np.ndarray, region: np.ndarray, max_width: int = 14) -> np.ndarray:
    """Fill narrow transparent slits that have opaque foot on both sides."""
    opq = alpha > 180
    fill = np.zeros_like(opq)
    h, w = alpha.shape
    ys, xs = np.where(region)
    if len(xs) == 0:
        return fill
    y0, y1 = int(ys.min()), int(ys.max()) + 1
    x1 = int(xs.max()) + 1
    for y in range(y0, y1):
        row = opq[y, :x1]
        xs_op = np.where(row)[0]
        if len(xs_op) < 2:
            continue
        for i in range(len(xs_op) - 1):
            gap_l = int(xs_op[i]) + 1
            gap_r = int(xs_op[i + 1])
            width = gap_r - gap_l
            if width <= 0 or width > max_width:
                continue
            if not region[y, gap_l:gap_r].all():
                continue
            if int((alpha[y, gap_l:gap_r] > 12).sum()):
                continue
            above = max(y0, y - 10)
            below = min(h, y + 11)
            supported = False
            for x in range(gap_l, gap_r):
                if int(opq[above:y, x].sum()) or int(opq[y + 1 : below, x].sum()):
                    supported = True
                    break
            if supported:
                fill[y, gap_l:gap_r] = True
    return fill


def _disk_close(mask: np.ndarray, radius: int) -> np.ndarray:
    return ~dilate(~dilate(mask, radius), radius)


def _close_foot_notches(alpha: np.ndarray, y1: int, radius: int = 11) -> np.ndarray:
    """Fill stair notches and the straight seam inside the left foot only."""
    h, w = alpha.shape
    roi = np.zeros_like(alpha, dtype=bool)
    roi[max(0, y1 - 100) : y1, : min(w, 180)] = True
    closed = _disk_close(alpha > 180, radius)
    return closed & roi & (alpha < 12)


def repair_foot_and_fringe(alpha: np.ndarray, rgb: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    a = alpha.copy()
    c = rgb.copy()
    bb = content_bbox(a, 16)
    if bb is None:
        return a, c
    _, _, _, y1 = bb
    h, w = a.shape
    y1 = min(y1, h)
    foot = np.zeros_like(a, dtype=bool)
    foot[max(0, y1 - 110) : y1, : min(w, 220)] = True
    _clear_matte_specks(a, c, foot)

    # Peel the chair-edge band and the foot halo before closing, so the close is the last contour edit.
    y_cut = max(0, y1 - 46)
    for _ in range(6):
        matte = is_matte_rgb(c) & (a >= 40)
        exterior = flood_from_edges(a < 12)
        below = np.zeros_like(a, dtype=bool)
        below[:-1] |= exterior[1:]
        if h > 2:
            below[:-2] |= exterior[2:]
        zone = np.zeros_like(a, dtype=bool)
        zone[y_cut:y1, :] = True
        drop = matte & below & zone
        if not int(drop.sum()):
            break
        a[drop] = 0
    for _ in range(8):
        matte = is_matte_rgb(c) & (a >= 40) & foot
        exterior = flood_from_edges(a < 12)
        drop = matte & dilate(exterior, 1)
        if not int(drop.sum()):
            break
        a[drop] = 0
    _clear_matte_specks(a, c, foot)

    bb2 = content_bbox(a, 16)
    if bb2 is not None:
        y1 = min(bb2[3], h)
    matte = is_matte_rgb(c) & (a >= 40) & foot
    skin = is_saturated_skin(c, a)
    left = np.zeros_like(matte)
    right = np.zeros_like(matte)
    for d in range(1, 7):
        left[:, d:] |= skin[:, :-d]
        right[:, :-d] |= skin[:, d:]
    seam = matte & left & right
    if int(seam.sum()):
        _paint_from_skin(c, a, seam)

    fill = _fill_foot_slits(a, foot, max_width=8)
    fill |= _close_foot_notches(a, y1, radius=11)
    fill &= a < 12
    if int(fill.sum()):
        _paint_from_skin(c, a, fill)
    return a, c


def repair_waist_gaps_from_reference(
    alpha: np.ndarray, rgb: np.ndarray, ref: np.ndarray, box: Tuple[int, int, int, int]
) -> Tuple[np.ndarray, np.ndarray]:
    """Fill exterior-connected waist transparency using a pose with a closed torso (clap-a)."""
    a = alpha.copy()
    c = rgb.copy()
    ref_rgb = ref[:, :, :3]
    ref_a = ref[:, :, 3]
    matte_ref = is_matte_rgb(ref)
    y0, y1, x0, x1 = box
    h, w = a.shape
    y1 = min(y1, h)
    x1 = min(x1, w)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if a[y, x] >= 12:
                continue
            if ref_a[y, x] < 180:
                continue
            if matte_ref[y, x] or is_brown_rgb(int(ref_rgb[y, x, 0]), int(ref_rgb[y, x, 1]), int(ref_rgb[y, x, 2])):
                continue
            c[y, x] = ref_rgb[y, x]
            a[y, x] = int(ref_a[y, x])
    rem = np.zeros_like(a, dtype=bool)
    rem[y0:y1, x0:x1] = True
    rem &= a < 12
    if int(rem.sum()):
        _paint_from_skin(c, a, rem)
    return a, c


def celebrate_waist_gap_pixels(alpha: np.ndarray) -> int:
    y0, y1, x0, x1 = CELEBRATE_WAIST_BOX
    return int((alpha[y0:y1, x0:x1] < 12).sum())


def waist_gap_band_pixels(alpha: np.ndarray) -> int:
    y0, y1, x0, x1 = WAIST_GAP_BOX
    return int((alpha[y0:y1, x0:x1] < 12).sum())


def snap_alpha(alpha: np.ndarray) -> np.ndarray:
    a = alpha.copy()
    a[a < 12] = 0
    a[a > 242] = 255
    return a


def fix_clap_sprite(im: Image.Image) -> Image.Image:
    arr = np.array(im.convert("RGBA"))
    rgb = arr[:, :, :3].copy()
    alpha = arr[:, :, 3].copy()
    alpha, rgb = repair_foot_and_fringe(alpha, rgb)
    alpha, rgb = fill_interior_transparent(alpha, rgb)
    alpha, rgb = repair_foot_and_fringe(alpha, rgb)
    alpha, rgb = clear_nearwhite_fringe(alpha, rgb)
    alpha = snap_alpha(alpha)
    return Image.fromarray(np.dstack([rgb, alpha]).astype(np.uint8))


def fix_sprite(im: Image.Image, name: str = "") -> Image.Image:
    arr = np.array(im.convert("RGBA"))
    rgb = arr[:, :, :3].copy()
    alpha = arr[:, :, 3].copy()
    if name in WAIST_GAP_SPRITES:
        ref_path = ASSET_DIR / REF_WAIST_SPRITE
        if ref_path.is_file():
            ref = np.array(Image.open(ref_path).convert("RGBA"))
            alpha, rgb = repair_waist_gaps_from_reference(alpha, rgb, ref, WAIST_GAP_BOX)
    alpha, rgb = repair_foot_and_fringe(alpha, rgb)
    alpha, rgb = inpaint_holes(alpha, rgb, max_passes=48)
    alpha = snap_alpha(alpha)
    return Image.fromarray(np.dstack([rgb, alpha]).astype(np.uint8))


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
    # Clap frames may have enclosed transparent gaps between hair locks; those
    # must stay open. The tight check is "no holes in the white cardigan".
    hole_limit = (
        CLAP_HAIR_GAP_MAX
        if path.name in CLAP_SPRITES
        else (230 if path.name == "secretary-sleep.webp" else 100)
    )
    if holes > hole_limit:
        errs.append(f"{path.name}: {holes} interior enclosed holes (limit {hole_limit})")
    if path.name in CLAP_SPRITES:
        nbytes = path.stat().st_size
        if nbytes < CLAP_SIZE_MIN or nbytes > CLAP_SIZE_MAX:
            errs.append(
                f"{path.name}: file size {nbytes} bytes (want {CLAP_SIZE_MIN}-{CLAP_SIZE_MAX})"
            )
        cloth_holes = cardigan_enclosed_hole_count(alpha, rgb)
        if cloth_holes > 0:
            errs.append(
                f"{path.name}: {cloth_holes} white-cardigan enclosed holes (limit 0)"
            )
    waist = waist_hole_pixels(alpha)
    if waist > 120:
        errs.append(f"{path.name}: {waist} waist enclosed holes (limit 120)")
    if path.name in WAIST_GAP_SPRITES:
        gap = celebrate_waist_gap_pixels(alpha)
        if gap > 0:
            errs.append(f"{path.name}: {gap} celebrate waist gap px (limit 0)")
        band = waist_gap_band_pixels(alpha)
        band_limit = 220 if path.name == "secretary-yeah.webp" else 320
        if band > band_limit:
            errs.append(f"{path.name}: {band} waist gap band px (limit {band_limit})")
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
    errs.extend(_edge_defect_errors(path.name, alpha, rgb))
    return errs


def _matte_run_stats(band: np.ndarray) -> Tuple[int, int]:
    count = int(band.sum())
    longest = 0
    ys, xs = np.where(band)
    if len(xs) == 0:
        return 0, 0
    for y in range(int(ys.min()), int(ys.max()) + 1):
        row = np.where(band[y])[0]
        if len(row) == 0:
            continue
        run = 1
        best = 1
        for i in range(1, len(row)):
            if int(row[i]) == int(row[i - 1]) + 1:
                run += 1
                best = max(best, run)
            else:
                run = 1
        longest = max(longest, best)
    return count, longest


def _edge_defect_errors(name: str, alpha: np.ndarray, rgb: np.ndarray) -> List[str]:
    """Chair-edge fringe and left-foot contour defects the round-1 threshold missed."""
    errs: List[str] = []
    bb = content_bbox(alpha, 16)
    if bb is None:
        return errs
    h, w = alpha.shape
    _, _, _, y1 = bb
    # Underside of the figure, not only the image's last 40 rows. Colors near (225, 230, 237) count.
    y_cut = max(0, y1 - 40)
    matte = is_matte_rgb(rgb) & (alpha >= 40)
    exterior = flood_from_edges(alpha < 12)
    below = np.zeros_like(alpha, dtype=bool)
    if h > 1:
        below[:-1] |= exterior[1:]
    zone = np.zeros_like(alpha, dtype=bool)
    zone[y_cut:y1, :] = True
    band_n, longest = _matte_run_stats(matte & below & zone)
    if band_n > 60:
        errs.append(f"{name}: chair-edge matte band {band_n} px (limit 60)")
    if longest > 22:
        errs.append(f"{name}: chair-edge matte run {longest} px (limit 22)")

    foot = np.zeros_like(alpha, dtype=bool)
    foot[max(0, y1 - 110) : y1, : min(w, 200)] = True
    specks = _detached_matte_count(alpha, rgb, foot)
    if specks > 12:
        errs.append(f"{name}: detached foot matte specks {specks} px (limit 12)")
    halo = matte & foot & dilate(exterior, 1)
    halo_n = int(halo.sum())
    if halo_n > 28:
        errs.append(f"{name}: left-foot matte halo {halo_n} px (limit 28)")
    notches = int(_close_foot_notches(alpha, y1, radius=11).sum())
    if notches > 90:
        errs.append(f"{name}: left-foot open notches {notches} px (limit 90)")
    return errs


def _detached_matte_count(alpha: np.ndarray, rgb: np.ndarray, region: np.ndarray) -> int:
    matte_all = is_matte_rgb(rgb)
    matte = matte_all & (alpha >= 40) & region
    seen = np.zeros_like(matte)
    h, w = alpha.shape
    count = 0
    for y0, x0 in zip(*np.where(matte)):
        if seen[y0, x0]:
            continue
        q = deque([(int(x0), int(y0))])
        seen[y0, x0] = True
        comp: List[Tuple[int, int]] = []
        touches_body = False
        while q:
            x, y = q.popleft()
            comp.append((x, y))
            for nx, ny in (
                (x - 1, y),
                (x + 1, y),
                (x, y - 1),
                (x, y + 1),
                (x - 1, y - 1),
                (x + 1, y - 1),
                (x - 1, y + 1),
                (x + 1, y + 1),
            ):
                if not (0 <= nx < w and 0 <= ny < h):
                    continue
                if matte[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    q.append((nx, ny))
                elif alpha[ny, nx] > 160 and not matte_all[ny, nx]:
                    touches_body = True
        if len(comp) <= 80 and not touches_body:
            count += len(comp)
    return count


def run_fix_clap() -> None:
    """Refuse to rewrite clap sprites.

    fill_interior_transparent / fix_clap_sprite fills hair-lock gaps and
    sheer-cardigan folds from opaque neighbors, which punched dark-gray
    blotches into the white cardigan. The regenerated clap frames must be
    left as-is; use --check instead.
    """
    print(
        "refusing --clap: fix_clap_sprite fills hair gaps and stains the "
        "white cardigan. Use --check only.",
        file=sys.stderr,
    )
    raise SystemExit(2)


def run_fix(extra: bool = False) -> None:
    names = list(STATUS_SPRITES)
    if extra:
        names.extend(EXTRA_SPRITES)
    for name in names:
        path = ASSET_DIR / name
        if not path.exists():
            print(f"skip missing {name}", file=sys.stderr)
            continue
        im = fix_sprite(Image.open(path), name=name)
        save_webp(im, path)
        print(f"wrote {name}")


def run_check() -> int:
    errs: List[str] = []
    checked = set(STATUS_SPRITES) | WAIST_GAP_SPRITES | set(CLAP_SPRITES)
    for name in sorted(checked):
        path = ASSET_DIR / name
        if path.is_file():
            errs.extend(check_sprite(path))
    if errs:
        for e in errs:
            print(e, file=sys.stderr)
        return 1
    print("ok")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify status and clap sprites")
    parser.add_argument("--extra", action="store_true", help="also fix typing-b, wave, yeah")
    parser.add_argument("--clap", action="store_true", help="disabled: will not rewrite clap sprites (see run_fix_clap)")
    args = parser.parse_args()
    if args.check:
        return run_check()
    if args.clap:
        run_fix_clap()
    if not args.clap:
        run_fix(extra=args.extra)
    return run_check()


if __name__ == "__main__":
    raise SystemExit(main())