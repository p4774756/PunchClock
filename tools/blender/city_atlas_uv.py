"""城市圖集 UV 區塊（Blender 與 gen_city_textures 共用）。"""
from __future__ import annotations

COLS, ROWS = 4, 4

# (col, row) — 名稱對應 gen_city_textures.REGIONS 繪製區
_ATLAS_CELLS = {
    'city_asphalt': (0, 3),
    'city_asphalt_wet': (1, 3),
    'city_sidewalk': (2, 3),
    'city_cross_white': (3, 3),
    'city_wall_coral': (0, 2),
    'city_wall_mint': (1, 2),
    'city_wall_lilac': (2, 2),
    'city_wall_yellow': (3, 2),
    'city_facade': (0, 1),
    'city_awning': (1, 1),
    'roof_tile': (2, 1),
    'city_trim_white': (3, 1),
    'wood': (0, 0),
    'city_water_edge': (1, 0),
    'palm_leaf': (2, 0),
    'city_traffic_dark': (3, 0),
}

REGIONS = _ATLAS_CELLS


def atlas_uv(name):
    col, row = _ATLAS_CELLS.get(name, (0, 0))
    u0 = col / COLS
    u1 = (col + 1) / COLS
    v0 = row / ROWS
    v1 = (row + 1) / ROWS
    return (u0, v0, u1, v1)