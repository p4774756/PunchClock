"""城市島 v2（#205）：3×3 台式騎騎樓街、貼圖殼體、港灣棧橋。

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

W, D = 14.0, 14.0


def _au(name):
    return atlas_uv(name)


def _shophouse(b, walls, sign_part, lx, lz, bw, bd, bh, tex_wall, street_sign,
               face_north=True, awning='city_awning'):
    """多層騎樓：圖集外牆＋柱列＋遮陽棚＋陽台窗框。"""
    street = 1 if face_north else -1
    fy = lz + street * (bd / 2 - 0.08)
    uv = _au(tex_wall)
    uv_facade = _au('city_facade')
    walls.box_tex(f'@{tex_wall}', uv, bw, bh, bd, lx, lz, bh / 2 + 0.06)
    # 騎樓走廊（凹進）
    walls.box('wood', bw - 0.6, 0.12, 1.25, lx, fy - street * 0.38, 0.16)
    for ox in (-bw * 0.38, 0.0, bw * 0.38):
        walls.box_tex('@city_trim_white', _au('city_trim_white'), 0.16, bh * 0.88, 0.16,
                      lx + ox, fy - street * 0.10, bh * 0.46)
    walls.box_tex(f'@{awning}', _au(awning), bw + 0.55, 0.10, 1.15, lx, fy + street * 0.52, bh + 0.06)
    walls.box_tex('@wood', _au('wood'), bw + 0.45, 0.06, bd + 0.22, lx, lz + street * 0.14, bh + 0.12)
    # 陽台層
    for fz in (bh * 0.52, bh * 0.78):
        walls.box_tex('@city_facade', uv_facade, bw - 0.35, 0.28, 0.14, lx, fy - street * 0.20, fz)
        walls.box('lab_grey', 0.26, 0.20, 0.16, lx + bw * 0.30, fy - street * 0.12, fz + 0.04)
        for wx in (-bw * 0.32, bw * 0.32):
            walls.box('lab_glass', 0.44, bh * 0.30, 0.07, lx + wx, fy - street * 0.05, fz + 0.12)
            walls.box('wood', 0.48, bh * 0.34, 0.05, lx + wx, fy - street * 0.08, fz + 0.12)
    # 店面大窗
    walls.box_tex('@city_facade', uv_facade, min(1.1, bw * 0.55), bh * 0.45, 0.09,
                  lx, fy - street * 0.06, bh * 0.38)
    # 招牌（朝街、俯視鏡頭可讀）
    sw = min(2.55, bw * 0.68)
    sh = 0.82
    sx, sy, sz = lx, fy + street * 0.34, bh * 0.90
    pitch = -0.14 if street > 0 else 0.12
    sign_part.box('gold', sw + 0.18, 0.06, sh + 0.14, sx, sy, sz)
    if street > 0:
        sign_part.plane('sign', sw, sh, sx, sy + 0.07, sz, rx=pitch, ry=math.pi, flip_u=True)
    else:
        sign_part.plane('sign', sw, sh, sx, sy - 0.06, sz, rx=pitch)


def _corner_tower(walls, lx, lz, bw, bd, bh, mat, accent):
    walls.box(mat, bw, bh, bd, lx, lz, bh / 2)
    walls.box_tex('@roof_tile', _au('roof_tile'), bw + 0.18, 0.22, bd + 0.18, lx, lz, bh + 0.10)
    for ox, oz in ((-bw * 0.35, -bd * 0.35), (bw * 0.35, -bd * 0.35),
                   (-bw * 0.35, bd * 0.35), (bw * 0.35, bd * 0.35)):
        walls.box(accent, 0.40, 0.36, 0.08, lx + ox, lz + oz, bh * 0.58)


def _mount_sign(b, part_name, lx, ly, lz, sw, sh, rx=-0.12, ry=math.pi):
    """北向立面；法線朝 +Y 對準 EXPLORE 鏡頭。"""
    face = b.part(part_name)
    fr = b.part(part_name + '_frame')
    fr.box('gold', sw + 0.18, 0.06, sh + 0.16, lx, ly - 0.05, lz, rx=rx, ry=ry)
    face.plane('sign', sw, sh, lx, ly + 0.08, lz, rx=rx, ry=ry, flip_u=True)


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
    """小綿羊：圓潤前罩、踏板、座椅、輪拱。"""
    props.box('city_trim_white', 0.64, 0.06, 0.28, x, y - 0.04, 0.11, rz=rz)
    props.sphere('city_moto_cream', 0.19, x, y + 0.16, 0.24, sz=0.82, seg=8, rings=5)
    props.box('city_moto_cream', 0.40, 0.18, 0.30, x, y + 0.02, 0.26, rz=rz)
    props.box('city_moto_dark', 0.36, 0.11, 0.32, x, y - 0.10, 0.36, rz=rz)
    props.cyl('city_traffic_dark', 0.028, 0.028, 0.34, x, y + 0.06, 0.42, seg=5, rz=rz)
    props.box('city_traffic_dark', 0.38, 0.05, 0.05, x, y + 0.20, 0.46, rz=rz)
    props.sphere('windows_glow', 0.065, x, y + 0.24, 0.20, seg=6, rings=4)
    for wx in (-0.21, 0.21):
        props.cyl('city_traffic_dark', 0.10, 0.10, 0.07, x + wx, y, 0.10, seg=7, rx=math.pi / 2, rz=rz)
        props.sphere('city_moto_cream', 0.13, x + wx, y, 0.14, sz=0.55, seg=6, rings=4)
    props.sphere('city_traffic_red', 0.04, x, y - 0.24, 0.22, seg=5, rings=4)


def _vending(props, x, y, ry=0.0):
    props.box_tex('@city_wall_mint', _au('city_wall_mint'), 0.58, 0.78, 0.44, x, y, 0.39, ry=ry)
    props.box('windows_glow', 0.42, 0.05, 0.52, x, y - 0.20, 0.48, ry=ry)
    props.box_tex('@city_trim_white', _au('city_trim_white'), 0.50, 0.08, 0.14,
                  x, y - 0.20, 0.86, ry=ry)
    props.box('shop_orange', 0.38, 0.02, 0.22, x, y + 0.24, 0.62, ry=ry)


def _harbor(ground, walls, props):
    """北側碼頭：岸線、T 型棧橋、遊艇剪影（全景可辨）。"""
    ground.box_tex('@city_water_edge', _au('city_water_edge'), 7.0, 1.05, 0.14, 0, 6.55, 0.06)
    ground.box_tex('@wood', _au('wood'), 1.25, 6.4, 0.11, 0, 6.12, 0.055)
    ground.box_tex('@wood', _au('wood'), 2.6, 0.95, 0.11, 0.75, 6.65, 0.055)
    for ox in (-1.15, 1.15):
        ground.box_tex('@wood', _au('wood'), 1.05, 5.2, 0.14, ox, 6.25, 0.075)
        for z in (5.55, 6.2, 6.85):
            walls.box('city_rail', 0.10, 0.12, 0.62, ox, z, 0.34)
    for px, pz in ((-1.2, 6.6), (1.2, 6.6), (-0.6, 6.8), (0.6, 6.8), (0.75, 6.95)):
        props.cyl('city_rail', 0.06, 0.045, 0.48, px, pz, 0.26, seg=6)
    # 遊艇
    props.box('city_trim_white', 2.0, 0.42, 0.28, -1.85, 6.95, 0.16)
    props.box('shop_navy', 1.45, 0.28, 0.22, -1.85, 6.95, 0.36)
    props.box('lab_glass', 0.55, 0.32, 0.32, -1.85, 6.95, 0.58)
    props.cyl('city_trim_white', 0.05, 0.05, 0.62, -1.85, 6.95, 0.72, seg=5)
    props.box('city_trim_white', 0.95, 0.22, 0.18, 1.55, 6.88, 0.14)
    props.box('shop_navy', 0.72, 0.16, 0.14, 1.55, 6.88, 0.28)
    ground.box('city_sand', 3.2, 1.45, 0.09, -1.6, 6.82, 0.045)


def _traffic(b):
    pole = b.part('traffic_pole')
    housing = b.part('traffic_housing')
    red = b.part('traffic_red')
    yellow = b.part('traffic_yellow')
    green = b.part('traffic_green')
    tx, ty = 1.15, 1.05
    pole.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.10, 0.10, 2.55, tx, ty, 1.28)
    housing.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.32, 0.18, 0.62, tx, ty + 0.22, 2.58)
    housing.box_tex('@city_trim_white', _au('city_trim_white'), 0.36, 0.05, 0.66, tx, ty + 0.22, 2.58)
    red.sphere('city_traffic_red', 0.14, tx, ty + 0.22, 2.90, seg=8, rings=6)
    yellow.sphere('city_traffic_yel', 0.14, tx, ty + 0.22, 2.56, seg=8, rings=6)
    green.sphere('city_traffic_grn', 0.14, tx, ty + 0.22, 2.22, seg=8, rings=6)
    housing.box('city_traffic_dark', 0.68, 0.08, 0.10, tx, ty, 2.54)
    housing.box('lab_steel', 0.42, 0.05, 0.06, tx, ty + 0.32, 2.16)


def _mini_block(walls, lx, lz, bw, bd, bh, mat):
    if mat in REGIONS:
        walls.box_tex(f'@{mat}', _au(mat), bw, bh, bd, lx, lz, bh / 2)
    else:
        walls.box(mat, bw, bh, bd, lx, lz, bh / 2)
    walls.box_tex('@roof_tile', _au('roof_tile'), bw + 0.12, 0.16, bd + 0.12, lx, lz, bh + 0.07)


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

    # —— 道路：深色柏油柏油＋濕地反光條＋人行道 ——
    ground.box_tex('@city_asphalt', _au('city_asphalt'), W - 0.6, D - 0.6, 0.11, 0, 0, 0.055)
    for slab in ((2.2, 9.4, 0, 0.6), (9.4, 2.2, 0, 0.6)):
        sw, sd, ox, oz = slab
        ground.box_tex('@city_asphalt_wet', _au('city_asphalt_wet'), sw, sd, 0.07, ox, oz, 0.038)
    for ox, oz in ((0, 3.55), (0, -2.55), (-3.85, 0.55), (3.85, 0.55)):
        ground.box_tex('@city_sidewalk', _au('city_sidewalk'), 8.8, 1.12, 0.06, ox, oz, 0.042)
    for z in (-2.6, 1.6, 4.2):
        ground.box_tex('@city_cross_white', _au('city_cross_white'), 0.09, 0.05, 4.8, 0.95, z, 0.040)
    _zebra(ground, 0, 1.25, along_x=True)
    _zebra(ground, 0, 1.25, along_x=False)

    _harbor(ground, walls, props)

    # —— 主幹三棟（碰撞對齊）——
    aw = b.part('sign_face_arcade')
    _shophouse(b, walls, aw, -3.8, 0.55, 3.35, 4.55, 2.35, 'city_wall_coral', 'arcade', True)
    tea = b.part('sign_face_bubble')
    _shophouse(b, walls, tea, 3.8, 0.55, 3.35, 4.55, 2.35, 'city_wall_mint', 'bubble', True)
    claw = b.part('sign_face_claw')
    _shophouse(b, walls, claw, 0, -4.15, 3.55, 2.75, 1.95, 'city_wall_lilac', 'claw', False, 'city_awning')

    # —— 3×3 街區其餘角樓（純視覺，不增碰撞）——
    blocks = [
        (-5.1, 5.0, 1.55, 1.55, 2.05, 'city_wall_yellow'),
        (5.1, 5.0, 1.55, 1.55, 2.05, 'city_wall_mint'),
        (-5.1, -5.0, 1.45, 1.45, 1.85, 'city_wall_lilac'),
        (5.1, -5.0, 1.45, 1.45, 1.85, 'city_wall_coral'),
        (-5.1, 0.0, 1.35, 1.35, 1.75, 'city_wall_mint'),
        (5.1, 0.0, 1.35, 1.35, 1.75, 'city_wall_yellow'),
        (0, 5.0, 1.65, 1.65, 1.95, 'city_wall_mint'),
        (-2.6, 2.6, 1.25, 1.25, 1.55, 'city_wall_coral'),
        (2.6, 2.6, 1.25, 1.25, 1.55, 'city_wall_mint'),
    ]
    for lx, lz, bw, bd, bh, mat in blocks:
        if abs(lx) < 2.5 and abs(lz) < 2.5:
            continue
        _mini_block(walls, lx, lz, bw, bd, bh, mat)

    _corner_tower(walls, -5.0, 5.05, 1.48, 1.48, 2.48, 'city_wall_yellow', 'lab_glass')
    _corner_tower(walls, 5.0, 5.05, 1.48, 1.48, 2.48, 'city_wall_mint', 'lab_glass')

    _mount_sign(b, 'sign_face_game', -5.0, 5.72, 1.92, 1.95, 0.68)
    _mount_sign(b, 'sign_face_711', 5.0, 5.72, 1.92, 1.55, 0.68)
    _mount_sign(b, 'sign_face_snack', -1.35, 5.72, 1.55, 1.75, 0.58)

    for lx, ly in ((-3.0, 4.55), (3.0, 4.55), (-3.0, -3.45), (3.0, -3.45), (0, 5.4)):
        props.cyl('city_traffic_dark', 0.045, 0.055, 1.42, lx, ly, 0.72, seg=6)
        props.box('city_trim_white', 0.26, 0.09, 0.26, lx, ly, 1.44)
        glow.sphere('lantern_glow', 0.090, lx, ly, 1.34, seg=6, rings=4)

    for px, py in ((-4.6, 2.85), (4.6, 2.85), (-2.3, -1.15), (2.5, -0.75),
                   (-5.2, -2.45), (0.8, 4.2), (-1.5, 4.5)):
        _palm(props, px, py)

    for px, py, rz in ((-2.05, 3.25, 0.3), (2.25, 2.95, -0.2), (-0.85, -0.30, 1.0),
                       (1.65, 0.15, -0.45), (-3.8, -3.2, 0.7), (3.5, -2.5, -0.3)):
        _scooter(props, px, py, rz)

    _vending(props, -4.25, -0.45, ry=0.38)
    _vending(props, 4.05, -1.75, ry=-0.22)

    _traffic(b)

    neo = b.part('neon_trim')
    for lx, ly, sw in ((-3.8, 3.25, 2.85), (3.8, 3.25, 2.65), (0, -3.45, 2.45),
                       (-5.0, 5.35, 1.35), (5.0, 5.35, 1.35)):
        neo.box('neon_glow', sw, 0.05, 0.06, lx, ly, 2.18)

    return b.finish(out_path, extras={'building_id': 'city', 'island_id': 'city'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city.glb')
    build(argv_out(_default))