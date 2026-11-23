#!/usr/bin/env python3
"""Remove office-chair pixels from secretary status sprites (bathtub scene)."""
from __future__ import annotations

import argparse
import sys
from collections import deque
from pathlib import Path
from typing import Callable, List, Tuple

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

NAVY_SEEDS = (
    (320, 181),
    (539, 316),
    (556, 442),
    (200, 420),
    (450, 520),
)

WOOD_SEEDS = (
    (360, 520),
    (280, 540),
    (480, 560),
)


def is_navy(r: int, g: int, b: int, a: int) -> bool:
    return (
        a > 40
        and 40 <= r <= 95
        and 55 <= g <= 105
        and 88 <= b <= 145
        and b > r + 20
    )


def is_chair_wood(r: int, g: int, b: int, a: int) -> bool:
    return (
        a > 40
        and 100 <= r <= 200
        and 60 <= g <= 130
        and 35 <= b <= 95
        and r - b >= 35
    )


def flood_mask(
    rgba: np.ndarray,
    pred: Callable[[int, int, int, int], bool],
    seeds: Tuple[Tuple[int, int], ...],
) -> np.ndarray:
    r = rgba[:, :, 0]
    g = rgba[:, :, 1]
    b = rgba[:, :, 2]
    a = rgba[:, :, 3]
    h, w = a.shape
    seen = np.zeros((h, w), dtype=bool)
    for sx, sy in seeds:
        if not pred(int(r[sy, sx]), int(g[sy, sx]), int(b[sy, sx]), int(a[sy, sx])):
            continue
        q: deque = deque([(sx, sy)])
        while q:
            x, y = q.popleft()
            if not (0 <= x < w and 0 <= y < h) or seen[y, x]:
                continue
            if not pred(int(r[y, x]), int(g[y, x]), int(b[y, x]), int(a[y, x])):
                continue
            seen[y, x] = True
            q.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return seen


def dilate(mask: np.ndarray, radius: int = 2) -> np.ndarray:
    if radius <= 0:
        return mask
    h, w = mask.shape
    out = mask.copy()
    ys, xs = np.where(mask)
    for y, x in zip(ys, xs):
        y0 = max(0, y - radius)
        y1 = min(h, y + radius + 1)
        x0 = max(0, x - radius)
        x1 = min(w, x + radius + 1)
        out[y0:y1, x0:x1] = True
    return out


def clear_chair(rgba: np.ndarray, include_wood: bool) -> np.ndarray:
    out = rgba.copy()
    drop = flood_mask(out, is_navy, NAVY_SEEDS)
    if include_wood:
        drop |= flood_mask(out, is_chair_wood, WOOD_SEEDS)
    drop = dilate(drop, 2)
    out[drop, 3] = 0
    return out


def process(path: Path, include_wood: bool) -> None:
    im = Image.open(path)
    if im.size != (624, 768):
        raise SystemExit(f"{path.name}: expected 624x768 got {im.size}")
    cleared = clear_chair(np.array(im.convert("RGBA")), include_wood)
    Image.fromarray(cleared).save(path, format="WEBP", quality=88, method=6)
    print(f"wrote {path.name}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--extra", action="store_true", help="also process typing-b, wave, yeah")
    args = parser.parse_args()
    names: List[str] = list(STATUS_SPRITES)
    if args.extra:
        names.extend(EXTRA_SPRITES)
    for name in names:
        path = ASSET_DIR / name
        if not path.exists():
            print(f"skip missing {name}", file=sys.stderr)
            continue
        include_wood = name != "secretary-sleep.webp"
        process(path, include_wood)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())