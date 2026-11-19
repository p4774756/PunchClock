"""程序生成城市島貼圖圖集（1024²）；供 build_city.py 殼體 UV 使用。"""
from __future__ import annotations

import math
import os
import random

from PIL import Image, ImageDraw

from city_atlas_uv import COLS, ROWS

_HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(_HERE, 'textures', 'city_atlas.png')
CELL = 256
W, H = CELL * COLS, CELL * ROWS


def _cell(col, row):
    return col * CELL, row * CELL


def _noise_fill(draw, x0, y0, x1, y1, base, jitter=12):
    for y in range(y0, y1, 4):
        for x in range(x0, x1, 4):
            c = tuple(max(0, min(255, base[i] + random.randint(-jitter, jitter))) for i in range(3))
            draw.rectangle([x, y, x + 3, y + 3], fill=c)


def _asphalt(draw, ox, oy):
    base = (58, 60, 68)
    _noise_fill(draw, ox, oy, ox + CELL, oy + CELL, base, 14)
    for i in range(0, CELL, 28):
        draw.rectangle([ox + 4, oy + i, ox + CELL - 4, oy + i + 3], fill=(75, 76, 82))
    for _ in range(80):
        px, py = ox + random.randint(0, CELL - 1), oy + random.randint(0, CELL - 1)
        draw.point((px, py), fill=(40, 42, 48))


def _asphalt_wet(draw, ox, oy):
    _asphalt(draw, ox, oy)
    for y in range(oy, oy + CELL, 6):
        draw.line([(ox, y), (ox + CELL, y)], fill=(90, 110, 140), width=1)


def _sidewalk(draw, ox, oy):
    for row in range(8):
        for col in range(8):
            x, y = ox + col * 32, oy + row * 32
            tone = 200 - (col + row) % 3 * 12
            draw.rectangle([x, y, x + 31, y + 31], fill=(tone, tone - 18, tone - 30))
            draw.rectangle([x, y, x + 31, y + 31], outline=(175, 160, 140), width=1)


def _crosswalk(draw, ox, oy):
    draw.rectangle([ox, oy, ox + CELL, oy + CELL], fill=(58, 60, 68))
    for i in range(6):
        draw.rectangle([ox + 18 + i * 38, oy + 8, ox + 48 + i * 38, oy + CELL - 8], fill=(235, 235, 230))


def _wall_band(draw, ox, oy, rgb, accent=None):
    _noise_fill(draw, ox, oy, ox + CELL, oy + CELL, rgb, 10)
    for y in range(oy + 20, oy + CELL - 10, 48):
        draw.rectangle([ox + 8, y, ox + CELL - 8, y + 4], fill=(255, 247, 239))
    if accent:
        for x in range(ox + 16, ox + CELL - 16, 56):
            draw.rectangle([x, oy + 30, x + 36, oy + 78], fill=accent)
            draw.rectangle([x - 2, oy + 28, x + 38, oy + 80], outline=(90, 70, 55), width=2)


def _facade(draw, ox, oy):
    _wall_band(draw, ox, oy, (232, 218, 198), (168, 205, 228))
    for row in range(4):
        for col in range(3):
            x, y = ox + 24 + col * 72, oy + 24 + row * 58
            draw.rectangle([x, y, x + 52, y + 42], fill=(198, 228, 248))
            draw.rectangle([x - 3, y - 3, x + 55, y + 45], outline=(120, 95, 70), width=2)


def _awning(draw, ox, oy):
    for i in range(0, CELL, 24):
        c = (245, 88, 72) if (i // 24) % 2 == 0 else (255, 248, 240)
        draw.rectangle([ox, oy + i, ox + CELL, oy + i + 24], fill=c)


def _roof(draw, ox, oy):
    for row in range(16):
        for col in range(16):
            c = (110 + (row % 2) * 8, 125 + (col % 2) * 6, 145)
            draw.rectangle([ox + col * 16, oy + row * 16, ox + col * 16 + 15, oy + row * 16 + 15], fill=c)


def _wood(draw, ox, oy):
    base = (140, 92, 52)
    _noise_fill(draw, ox, oy, ox + CELL, oy + CELL, base, 8)
    for y in range(oy, oy + CELL, 18):
        draw.line([(ox, y), (ox + CELL, y)], fill=(110, 70, 38), width=2)


def _water(draw, ox, oy):
    draw.rectangle([ox, oy, ox + CELL, oy + CELL], fill=(72, 118, 168))
    for _ in range(40):
        y = oy + random.randint(0, CELL - 1)
        draw.arc([ox, y - 8, ox + CELL, y + 8], 0, 180, fill=(130, 175, 220), width=1)


def _palm_tex(draw, ox, oy):
    draw.rectangle([ox, oy, ox + CELL, oy + CELL], fill=(62, 120, 58))
    for _ in range(25):
        cx, cy = ox + CELL // 2, oy + CELL // 2
        ang = random.random() * math.pi * 2
        ex = cx + math.cos(ang) * random.randint(40, 110)
        ey = cy + math.sin(ang) * random.randint(20, 60)
        draw.line([(cx, cy), (ex, ey)], fill=(78, 145, 68), width=random.randint(6, 14))


def _metal(draw, ox, oy):
    _noise_fill(draw, ox, oy, ox + CELL, oy + CELL, (70, 72, 78), 6)
    for y in range(oy + 8, oy + CELL, 32):
        draw.line([(ox + 4, y), (ox + CELL - 4, y)], fill=(95, 98, 105), width=1)


# 圖集格：(col, row) → 繪製函式（格位與 city_atlas_uv 對齊）
_PAINT = {
    'city_asphalt': (0, 3, _asphalt),
    'city_asphalt_wet': (1, 3, _asphalt_wet),
    'city_sidewalk': (2, 3, _sidewalk),
    'city_cross_white': (3, 3, _crosswalk),
    'city_wall_coral': (0, 2, lambda d, ox, oy: _wall_band(d, ox, oy, (244, 168, 160), (255, 200, 190))),
    'city_wall_mint': (1, 2, lambda d, ox, oy: _wall_band(d, ox, oy, (184, 232, 216), (160, 210, 255))),
    'city_wall_lilac': (2, 2, lambda d, ox, oy: _wall_band(d, ox, oy, (212, 200, 240), (180, 168, 230))),
    'city_wall_yellow': (3, 2, lambda d, ox, oy: _wall_band(d, ox, oy, (255, 240, 184), (255, 220, 140))),
    'city_facade': (0, 1, _facade),
    'city_awning': (1, 1, _awning),
    'roof_tile': (2, 1, _roof),
    'city_trim_white': (3, 1, lambda d, ox, oy: draw_fill(d, ox, oy, (255, 247, 239))),
    'wood': (0, 0, _wood),
    'city_water_edge': (1, 0, _water),
    'palm_leaf': (2, 0, _palm_tex),
    'city_traffic_dark': (3, 0, _metal),
}


def draw_fill(draw, ox, oy, rgb):
    draw.rectangle([ox, oy, ox + CELL, oy + CELL], fill=rgb)


def main():
    random.seed(205)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img = Image.new('RGB', (W, H), (128, 128, 128))
    draw = ImageDraw.Draw(img)
    for _name, (col, row, fn) in _PAINT.items():
        ox, oy = _cell(col, row)
        fn(draw, ox, oy)
    img.save(OUT, optimize=True)
    print(f'WROTE {OUT} {os.path.getsize(OUT)} bytes')


if __name__ == '__main__':
    main()