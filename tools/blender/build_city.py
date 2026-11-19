"""城市島 v2（#215）：3×3 台式騎騎樓街、貼圖殼體、東側港灣＋北岸棧橋。

  python3 tools/blender/gen_city_textures.py
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_city.py -- \\
    server/src/main/resources/public/models/city.glb
"""
from __future__ import annotations

import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import bpy
import kit
from kit import Builder, clear_scene, prepare_scene, argv_out, set_palette_overrides
from city_atlas_uv import REGIONS, atlas_uv

W, D = 16.5, 16.5
GRID = 4.85
BLOCK = 2.75
ROAD = 1.95

_SIGN_UV = dict(flip_u=True, flip_v=True)

ROOF_BY_WALL = {
    'city_wall_coral': 'roof_tile_red',
    'city_wall_mint': 'roof_tile_green',
    'city_wall_lilac': 'roof_tile_pink',
    'city_wall_yellow': 'roof_tile_orange',
    'city_wall_teal': 'roof_tile_blue',
}


def _roof_key(wall_mat):
    return ROOF_BY_WALL.get(wall_mat, 'roof_tile')


def _roof_slab(walls, lx, lz, bw, bd, z, roof_key=None):
    rk = roof_key or 'roof_tile'
    if rk in REGIONS:
        walls.box_tex(f'@{rk}', _au(rk), bw, 0.14, bd, lx, lz, z)
    elif rk == 'roof_tile':
        walls.box_tex(f'@{rk}', _au('roof_tile'), bw, 0.14, bd, lx, lz, z)
    else:
        walls.box(rk, bw, 0.14, bd, lx, lz, z)


def _au(name):
    return atlas_uv(name)


def _wall_tex(walls, mat, sx, sy, sz, x, y, z, **kw):
    if mat in REGIONS:
        walls.box_tex(f'@{mat}', _au(mat), sx, sy, sz, x, y, z, **kw)
    elif mat == 'wood':
        walls.box_tex('@wood', _au('wood'), sx, sy, sz, x, y, z, **kw)
    else:
        walls.box_tex('@city_facade', _au('city_facade'), sx, sy, sz, x, y, z, **kw)


def _facade_extras(walls, props, lx, fy, street, bw, bh, tex_wall):
    """窗框、陽台、室外機、騎樓樑。"""
    uv_facade = _au('city_facade')
    _wall_tex(walls, 'wood', bw - 0.5, 0.10, 1.15, lx, fy - street * 0.32, 0.15)
    for ox in (-bw * 0.34, 0.0, bw * 0.34):
        walls.box_tex('@city_facade', uv_facade, 0.17, bh * 0.85, 0.16,
                      lx + ox, fy - street * 0.08, bh * 0.44)
    for fz in (bh * 0.50, bh * 0.76):
        walls.box_tex('@city_facade', uv_facade, bw - 0.30, 0.26, 0.12, lx, fy - street * 0.18, fz)
        for wx in (-bw * 0.30, bw * 0.30):
            walls.box('lab_glass', 0.42, bh * 0.28, 0.06, lx + wx, fy - street * 0.04, fz + 0.10)
            _wall_tex(walls, 'wood', 0.46, bh * 0.32, 0.04, lx + wx, fy - street * 0.06, fz + 0.10)
            walls.box_tex('@city_facade', uv_facade, 0.32, 0.08, 0.22, lx + wx + street * 0.02,
                          fy - street * 0.14, fz - 0.08)
        props.box('city_traffic_dark', 0.22, 0.16, 0.20, lx + bw * 0.34, fy - street * 0.06, fz + 0.02)
        props.box('city_traffic_dark', 0.16, 0.14, 0.18, lx - bw * 0.34, fy - street * 0.06, fz + 0.04)


def _shophouse(b, walls, sign_part, neon_part, lx, lz, bw, bd, bh, tex_wall, sign_key,
               face_north=True, awning='city_awning'):
    street = -1 if face_north else 1
    fy = lz + street * (bd / 2 - 0.08)
    uv = _au(tex_wall)
    rk = _roof_key(tex_wall)
    walls.box_tex(f'@{tex_wall}', uv, bw, bh, bd, lx, lz, bh / 2 + 0.06)
    props = b.part('props')
    _facade_extras(walls, props, lx, fy, street, bw, bh, tex_wall)
    walls.box_tex(f'@{awning}', _au(awning), bw + 0.26, 0.06, 0.38, lx, fy + street * 0.22, bh + 0.54)
    _roof_slab(walls, lx, lz + street * 0.12, bw + 0.40, bd + 0.18, bh + 0.12, rk)
    sw = min(3.55, bw * 0.88)
    sh = 1.42
    sz = bh + 0.36
    y_out = fy + street * 0.48
    pitch = 0.06
    frame = b.part(sign_key + '_frame')
    frame.box(rk, sw + 0.16, 0.06, sh + 0.12, lx, y_out, sz, rx=pitch)
    sign_part.plane('sign', sw, sh, lx, y_out + street * 0.05, sz, rx=pitch, ry=math.pi, **_SIGN_UV)
    neon_part.box('neon_glow', sw + 0.12, 0.05, 0.06, lx, y_out + street * 0.03, sz + sh / 2 - 0.05, rx=pitch)
    neon_part.box('neon_glow', sw + 0.12, 0.05, 0.06, lx, y_out + street * 0.03, sz - sh / 2 + 0.05, rx=pitch)


def _residential_block(walls, props, lx, lz, bw, bd, bh, mat, face_south=True):
    if mat in REGIONS:
        walls.box_tex(f'@{mat}', _au(mat), bw, bh, bd, lx, lz, bh / 2)
    else:
        _wall_tex(walls, mat, bw, bh, bd, lx, lz, bh / 2)
    _roof_slab(walls, lx, lz, bw + 0.12, bd + 0.12, bh + 0.08, _roof_key(mat))
    street = 1 if face_south else -1
    fy = lz + street * (bd / 2 - 0.08)
    _facade_extras(walls, props, lx, fy, street, bw, bh, mat)
    walls.box_tex('@city_awning', _au('city_awning'), bw + 0.22, 0.06, 0.32,
                  lx, fy + street * 0.18, bh + 0.52)
    for sx in (-1, 1):
        for sz in (-1, 1):
            px, pz = lx + sx * bw * 0.32, lz + sz * bd * 0.32
            walls.box('lab_glass', 0.38, 0.34, 0.07, px, pz, bh * 0.55)
            walls.box('wood', 0.42, 0.38, 0.04, px, pz, bh * 0.55)
            props.box('city_traffic_dark', 0.20, 0.14, 0.16, px + sx * 0.08, pz, bh * 0.82)


def _plaza_711(b, walls, props, ground, sign_part, neon_part):
    """中央 7-11 廣場：開闊鋪面＋超商立面（朝南、預設俯視可讀）。"""
    bw, bd, bh = 2.85, 2.55, 1.68
    lx, lz = 0.0, 0.0
    walls.box_tex('@city_wall_yellow', _au('city_wall_yellow'), bw, bh, bd, lx, lz, bh / 2 + 0.04)
    _roof_slab(walls, lx, lz, bw + 0.35, bd + 0.35, bh + 0.10, 'roof_tile_green')
    fy_south = lz + bd / 2 - 0.10
    for mat, zf in (
        ('city_traffic_grn', 0.62),
        ('shop_orange', 0.44),
        ('city_traffic_red', 0.28),
    ):
        walls.box(mat, bw - 0.28, 0.08, 0.04, lx, fy_south - 0.02, bh * zf)
    _facade_extras(walls, props, lx, fy_south, 1, bw, bh, 'city_wall_yellow')
    sw, sh = 2.55, 1.28
    sz = bh * 0.74
    pitch = 0.06
    frame = b.part('sign_face_711_frame')
    frame.box('roof_tile_green', sw + 0.18, 0.06, sh + 0.14, lx, fy_south + 0.54, sz, rx=pitch)
    sign = b.part('sign_face_711')
    sign.plane('sign', sw, sh, lx, fy_south + 0.60, sz, rx=pitch, ry=math.pi, **_SIGN_UV)
    neo = b.part('shop_neon')
    neo.box('neon_glow', sw + 0.10, 0.05, 0.06, lx, fy_south + 0.58, sz + sh / 2 - 0.05, rx=pitch)
    ground.box_tex('@city_sidewalk', _au('city_sidewalk'), 3.45, 3.45, 0.06, lx, lz, 0.042)


def _mount_sign(b, part_name, lx, ly, lz, sw, sh, rx=0.08, roof='roof_tile_orange', face_south=True):
    """face_south=True：招牌法線朝南，預設探索鏡頭可讀。"""
    face = b.part(part_name)
    fr = b.part(part_name + '_frame')
    neo = b.part(part_name + '_neon')
    if face_south:
        y_face = ly + 0.52
        fr.box(roof, sw + 0.20, 0.06, sh + 0.16, lx, y_face, lz, rx=rx)
        face.plane('sign', sw, sh, lx, y_face + 0.06, lz, rx=rx, ry=math.pi, **_SIGN_UV)
        neo.box('neon_glow', sw + 0.10, 0.05, 0.06, lx, y_face + 0.05, lz + sh / 2 - 0.05, rx=rx)
    else:
        y_face = ly - 0.18
        fr.box(roof, sw + 0.20, 0.06, sh + 0.16, lx, y_face - 0.03, lz, rx=rx)
        face.plane('sign', sw, sh, lx, y_face + 0.10, lz, rx=rx, **_SIGN_UV)
        neo.box('neon_glow', sw + 0.10, 0.05, 0.06, lx, y_face + 0.09, lz + sh / 2 - 0.05, rx=rx)


def _zebra(ground, cx, cz, along_x=True):
    for i in range(-4, 5):
        if along_x:
            ground.box_tex('@city_cross_white', _au('city_cross_white'), 0.32, 0.05, 0.52,
                           cx + i * 0.40, cz, 0.032)
        else:
            ground.box_tex('@city_cross_white', _au('city_cross_white'), 0.52, 0.05, 0.32,
                           cx, cz + i * 0.40, 0.032)


def _palm(props, x, y):
    props.cyl('pot', 0.18, 0.14, 0.22, x, y, 0.11, seg=7)
    props.cyl('shop_wood', 0.055, 0.065, 1.05, x, y, 0.65, seg=6)
    n, h, rxp = 8, 0.62, 1.15
    for i in range(n):
        a = (i / n) * math.pi * 2 + 0.2
        ox = math.sin(a) * math.sin(rxp) * (h / 2)
        oy = -math.cos(a) * math.sin(rxp) * (h / 2)
        oz = math.cos(rxp) * (h / 2)
        props.cyl('palm_leaf', 0.11, 0.014, h, x + ox, y + oy, 1.05 + oz, seg=5, rx=rxp, rz=a)
        props.box_tex('@palm_leaf', _au('palm_leaf'), 0.22, 0.04, 0.14,
                      x + ox * 0.6, y + oy * 0.6, 1.12 + oz, rz=a)


def _scooter(props, x, y, rz=0.0):
    """小綿羊：白車身、座椅、龍頭、雙輪。"""
    props.box('city_moto_cream', 0.30, 0.10, 0.52, x, y - 0.02, 0.08, rz=rz)
    props.box('city_moto_cream', 0.24, 0.08, 0.38, x, y + 0.08, 0.24, rz=rz)
    props.box('city_moto_cream', 0.20, 0.22, 0.30, x, y + 0.22, 0.34, rz=rz, rx=0.06)
    props.sphere('city_moto_cream', 0.12, x, y + 0.30, 0.26, seg=8, rings=5)
    props.box('city_moto_dark', 0.08, 0.10, 0.16, x, y + 0.28, 0.24, rz=rz)
    props.cyl('city_moto_dark', 0.026, 0.026, 0.36, x + 0.06, y + 0.16, 0.50, seg=6, rz=rz + 0.15)
    props.cyl('city_moto_dark', 0.024, 0.024, 0.30, x + 0.12, y + 0.28, 0.48, seg=5, rx=0.58, rz=rz)
    props.cyl('city_moto_dark', 0.024, 0.024, 0.30, x - 0.04, y + 0.28, 0.48, seg=5, rx=0.58, rz=rz)
    props.sphere('windows_glow', 0.042, x, y + 0.31, 0.25, seg=6, rings=4)
    for wx, wy in ((-0.24, 0.12), (0.22, -0.14)):
        props.cyl('city_traffic_dark', 0.12, 0.12, 0.07, x + wx, y + wy, 0.12, seg=10, rx=math.pi / 2, rz=rz)
        props.cyl('city_trim_white', 0.068, 0.068, 0.045, x + wx, y + wy, 0.12, seg=8, rx=math.pi / 2, rz=rz)
    props.sphere('city_traffic_red', 0.040, x, y - 0.30, 0.22, seg=6, rings=4)


def _vending(props, x, y, ry=0.0):
    props.box_tex('@city_wall_mint', _au('city_wall_mint'), 0.58, 0.78, 0.44, x, y, 0.39, ry=ry)
    props.box('windows_glow', 0.42, 0.05, 0.52, x, y - 0.20, 0.48, ry=ry)
    props.box_tex('@city_facade', _au('city_facade'), 0.50, 0.08, 0.14,
                  x, y - 0.20, 0.86, ry=ry)
    props.box('shop_orange', 0.38, 0.02, 0.22, x, y + 0.24, 0.62, ry=ry)


def _harbor_north(ground, walls, props, b):
    """北岸棧橋（對齊 BOAT_STOPS.city）。"""
    ground.box_tex('@city_water_edge', _au('city_water_edge'), 8.5, 1.25, 0.12, 0, 6.55, 0.052)
    ground.box_tex('@wood', _au('wood'), 1.25, 5.8, 0.12, 0, 6.35, 0.058)
    for ox in (-0.95, 0.95):
        ground.box_tex('@wood', _au('wood'), 1.10, 5.6, 0.13, ox, 6.25, 0.072)
        for z in (5.55, 6.15, 6.75):
            walls.box_tex('@wood', _au('wood'), 0.11, 0.13, 0.52, ox, z, 0.33)
    for px, pz in ((-1.0, 6.35), (1.0, 6.35), (0.0, 6.65)):
        props.cyl('city_rail', 0.065, 0.05, 0.50, px, pz, 0.27, seg=6)


def _harbor_east(ground, walls, props, b):
    """東側港灣：伸入水中的 T 型棧型棧橋、欄杆、遊艇剪影。"""
    hx = W / 2 - 1.05
    ground.box_tex('@city_water_edge', _au('city_water_edge'), 5.2, 12.5, 0.14, hx, 0, 0.055)
    ground.box_tex('@wood', _au('wood'), 4.2, 1.15, 0.12, hx - 1.35, 0, 0.058)
    ground.box_tex('@wood', _au('wood'), 1.15, 8.5, 0.12, hx + 0.15, -3.2, 0.058)
    ground.box_tex('@wood', _au('wood'), 1.15, 8.5, 0.12, hx + 0.15, 3.2, 0.058)
    for oz in (-3.5, -1.75, 0, 1.75, 3.5):
        walls.box_tex('@wood', _au('wood'), 0.12, 0.14, 0.55, hx - 0.08, oz, 0.35)
        props.cyl('city_rail', 0.065, 0.050, 0.52, hx + 0.42, oz, 0.30, seg=6)
    # 遊艇（白船身＋藍彩條條＋駕駛艙）
    yx, yy = hx + 1.45, 0.85
    props.box('city_trim_white', 3.1, 0.52, 0.36, yx, yy, 0.16)
    props.box('shop_navy', 2.45, 0.38, 0.28, yx, yy, 0.34)
    props.box('shop_navy', 1.75, 0.24, 0.22, yx + 0.55, yy, 0.36, rx=-0.12)
    props.box('lab_glass', 0.58, 0.32, 0.38, yx + 0.95, yy + 0.1, 0.56)
    props.cyl('city_trim_white', 0.06, 0.05, 0.68, yx, yy, 0.74, seg=6)
    props.box('city_trim_white', 1.35, 0.30, 0.22, yx, yy - 0.55, 0.15)
    props.box('shop_navy', 1.05, 0.22, 0.18, yx, yy - 0.56, 0.26)
    props.box('city_trim_white', 0.85, 0.18, 0.14, yx - 1.25, yy, 0.20)
    neo = b.part('harbor_neon')
    neo.box('neon_glow', 0.58, 0.12, 0.06, hx - 0.42, -0.2, 0.64)
    neo.box('neon_glow', 0.48, 0.10, 0.05, hx - 0.38, 0.25, 0.64)


def _traffic(b, tx, ty):
    pole = b.part('traffic_pole')
    housing = b.part('traffic_housing')
    red = b.part('traffic_red')
    yellow = b.part('traffic_yellow')
    green = b.part('traffic_green')
    pole.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.10, 0.10, 2.55, tx, ty, 1.28)
    housing.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.32, 0.18, 0.62, tx, ty + 0.22, 2.58)
    housing.box_tex('@city_facade', _au('city_facade'), 0.36, 0.05, 0.66, tx, ty + 0.22, 2.58)
    red.sphere('city_traffic_red', 0.14, tx, ty + 0.22, 2.90, seg=8, rings=6)
    yellow.sphere('city_traffic_yel', 0.14, tx, ty + 0.22, 2.56, seg=8, rings=6)
    green.sphere('city_traffic_grn', 0.14, tx, ty + 0.22, 2.22, seg=8, rings=6)
    housing.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.68, 0.08, 0.10, tx, ty, 2.54)


def _road_grid(ground):
    """3×3 九宮格路網。"""
    ground.box_tex('@city_asphalt', _au('city_asphalt'), W - 0.5, D - 0.5, 0.11, 0, 0, 0.055)
    ground.box_tex('@city_asphalt_wet', _au('city_asphalt_wet'), ROAD, D - 1.0, 0.07, 0, 0, 0.038)
    ground.box_tex('@city_asphalt_wet', _au('city_asphalt_wet'), W - 1.0, ROAD, 0.07, 0, 0, 0.038)
    for gx in (-GRID, GRID):
        ground.box_tex('@city_asphalt_wet', _au('city_asphalt_wet'), ROAD, D - 1.2, 0.07, gx, 0, 0.038)
    for gz in (-GRID, GRID):
        ground.box_tex('@city_asphalt_wet', _au('city_asphalt_wet'), W - 1.2, ROAD, 0.07, 0, gz, 0.038)
    for cx, cz in ((0, GRID), (0, -GRID), (GRID, 0), (-GRID, 0)):
        ground.box_tex('@city_cross_white', _au('city_cross_white'), 0.09, 0.05, ROAD + 0.2, cx + 0.95, cz, 0.040)
    _zebra(ground, 0, 0, along_x=True)
    _zebra(ground, 0, 0, along_x=False)
    for ox, oz in ((0, GRID + 0.9), (0, -GRID - 0.9), (GRID + 0.9, 0), (-GRID - 0.9, 0)):
        ground.box_tex('@city_sidewalk', _au('city_sidewalk'), 2.6, 1.05, 0.06, ox, oz, 0.042)


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()

    set_palette_overrides({
        'windows_glow': (0xc8e8ff, 0xffb15a),
        'lantern_glow': (0xfff0d0, 0xffc56a),
        'neon_glow': (0xff88cc, 0xff6ad8),
        'city_asphalt_wet': (0x4a4a58, 0x6688cc),
    })

    b = Builder('city', W, D)
    ground = b.part('ground')
    props = b.part('props')
    walls = b.part('walls')
    glow = b.part('street_glow')
    neo = b.part('neon_trim')

    _road_grid(ground)
    _harbor_north(ground, walls, props, b)
    _harbor_east(ground, walls, props, b)

    neon_shop = b.part('shop_neon')
    bw, bd, bh = BLOCK, BLOCK, 2.05

    aw = b.part('sign_face_arcade')
    _shophouse(b, walls, aw, neon_shop, -GRID, 0, bw, bd, bh,
               'city_wall_coral', 'sign_face_arcade', True)
    tea = b.part('sign_face_bubble')
    _shophouse(b, walls, tea, neon_shop, GRID, 0, bw, bd, bh,
               'city_wall_mint', 'sign_face_bubble', True)
    claw = b.part('sign_face_claw')
    _shophouse(b, walls, claw, neon_shop, 0, -GRID, bw, bd, 1.85,
               'city_wall_lilac', 'sign_face_claw', False)

    _plaza_711(b, walls, props, ground, b.part('sign_face_711'), neon_shop)

    for lx, lz, mat in (
        (-GRID, GRID, 'city_wall_yellow'),
        (GRID, GRID, 'city_wall_teal'),
        (-GRID, -GRID, 'city_wall_lilac'),
        (GRID, -GRID, 'city_wall_coral'),
    ):
        _residential_block(walls, props, lx, lz, 2.55, 2.55, 1.85, mat)

    _mount_sign(b, 'sign_face_snack', GRID, GRID - 1.32, 0.28, 2.35, 1.12,
                roof='roof_tile_orange', face_south=True)

    for lx, ly in ((-GRID * 0.65, GRID + 0.65), (GRID * 0.65, GRID + 0.65),
                   (-2.5, -GRID - 0.5), (2.8, -GRID - 0.45), (0, GRID + 0.85)):
        props.cyl('city_traffic_dark', 0.045, 0.055, 1.42, lx, ly, 0.72, seg=6)
        props.box_tex('@city_facade', _au('city_facade'), 0.28, 0.09, 0.26, lx, ly, 1.44)
        glow.sphere('lantern_glow', 0.090, lx, ly, 1.34, seg=6, rings=4)

    for px, py in ((-GRID + 0.5, GRID - 0.4), (GRID - 0.4, GRID - 0.5),
                   (-2.8, 2.0), (3.0, 1.6), (-GRID, -GRID + 0.4), (GRID - 0.3, -GRID)):
        _palm(props, px, py)

    for px, py, rz in ((-3.6, 1.75, 0.25), (3.85, 1.45, -0.18), (-1.1, -2.55, 0.85),
                       (2.75, -2.05, -0.35), (-4.45, -3.45, 0.6), (4.25, -3.05, -0.25)):
        _scooter(props, px, py, rz)

    _vending(props, -GRID + 0.5, -1.2, ry=0.32)
    _vending(props, GRID - 0.6, -1.8, ry=-0.18)

    _traffic(b, -2.05, 2.65)

    for lx, ly, sw in ((-GRID, 0, 2.9), (GRID, 0, 2.9), (0, -GRID, 2.65),
                       (-GRID, GRID, 1.55), (GRID, GRID, 1.55)):
        neo.box('neon_glow', sw, 0.05, 0.06, lx, ly, 2.22)

    return b.finish(out_path, extras={'building_id': 'city', 'island_id': 'city'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city.glb')
    build(argv_out(_default))