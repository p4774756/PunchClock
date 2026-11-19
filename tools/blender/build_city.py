"""城市島 v2（#205）：台式騎樓街、道路、港灣、紅綠燈；招牌面留 sign_face_* 供 JS 貼字。

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

W, D = 14.0, 14.0


def _arcade_building(b, part_walls, part_sign, lx, lz, bw, bd, bh, wall_mat, face):
    """多層騎樓街屋；face 'north' = 店面朝 +Z（Blender -Y 為後，+Y 為前 … 用 street 偏移）。"""
    street = 1 if face == 'north' else -1
    fy = lz + street * (bd / 2)
    part_walls.box(wall_mat, bw, bh, bd, lx, lz, bh / 2 + 0.06)
    # 騎樓柱
    for ox in (-bw * 0.38, 0, bw * 0.38):
        part_walls.box('city_trim_white', 0.18, bh * 0.92, 0.18, lx + ox, fy - street * 0.08, bh * 0.48)
    # 遮陽棚
    part_walls.box('city_trim_white', bw + 0.5, 0.08, 1.02, lx, fy + street * 0.48, bh + 0.04)
    part_walls.box('beam_teal', bw + 0.55, 0.05, bd + 0.2, lx, lz + street * 0.12, bh + 0.14)
    # 陽台、室外機
    for floor_z in (bh * 0.55, bh * 0.82):
        part_walls.box('city_wall_teal', bw - 0.4, 0.32, 0.12, lx, fy - street * 0.22, floor_z)
        part_walls.box('lab_grey', 0.28, 0.22, 0.18, lx + bw * 0.32, fy - street * 0.14, floor_z + 0.05)
    # 窗
    for wx in (-bw * 0.35, bw * 0.35):
        part_walls.box('lab_glass', 0.48, bh * 0.34, 0.08, lx + wx, fy - street * 0.06, bh * 0.62)
        part_walls.box('wood', 0.52, bh * 0.38, 0.05, lx + wx, fy - street * 0.09, bh * 0.62)
    part_walls.box('wood', min(1.0, bw * 0.3), bh * 0.68, 0.12, lx, fy - street * 0.08, bh * 0.36)
    # 招牌
    sw = min(1.75, bw * 0.48)
    sh = 0.52
    sx, sy, sz = lx, fy + street * 0.22, bh * 0.78
    part_sign.box('gold', sw + 0.12, 0.05, sh + 0.10, sx, sy, sz)
    part_sign.plane('sign', sw, sh, sx, sy + street * 0.04, sz,
                    rx=-0.08 if street > 0 else 0.08)


def _mount_sign_plane(b, sign_part, name, lx, ly, lz, sw, sh, rx=0.0, ry=0.0, rz=0.0):
    face = b.part(name)
    fr = b.part(name + '_frame')
    fr.box('gold', sw + 0.10, 0.045, sh + 0.08, lx, ly, lz, rx=rx, ry=ry, rz=rz)
    face.plane('sign', sw, sh, lx, ly + 0.02, lz, rx=rx, ry=ry, rz=rz)


def _zebra_stripes(ground, cx, cz, along_x=True):
    for i in range(-3, 4):
        if along_x:
            ground.box('city_cross_white', 0.35, 0.04, 0.55, cx + i * 0.42, cz, 0.028)
        else:
            ground.box('city_cross_white', 0.55, 0.04, 0.35, cx, cz + i * 0.42, 0.028)


def _palm(props, x, y):
    props.cyl('pot', 0.16, 0.13, 0.20, x, y, 0.10, seg=7)
    props.cyl('shop_wood', 0.045, 0.055, 0.95, x, y, 0.62, seg=6)
    n, h, rxp = 7, 0.58, 1.12
    for i in range(n):
        a = (i / n) * math.pi * 2
        ox = math.sin(a) * math.sin(rxp) * (h / 2)
        oy = -math.cos(a) * math.sin(rxp) * (h / 2)
        oz = math.cos(rxp) * (h / 2)
        props.cyl('palm_leaf', 0.10, 0.012, h, x + ox, y + oy, 1.02 + oz, seg=5, rx=rxp, rz=a)


def _parked_scooter(props, x, y, rz=0.0):
    g = props
    g.box('city_moto_dark', 0.52, 0.26, 0.20, x, y, 0.14, rz=rz)
    g.cyl('city_traffic_dark', 0.11, 0.11, 0.09, x - 0.20, y, 0.11, seg=6, rx=math.pi / 2, rz=rz)
    g.cyl('city_traffic_dark', 0.11, 0.11, 0.09, x + 0.20, y, 0.11, seg=6, rx=math.pi / 2, rz=rz)
    g.box('shop_navy', 0.18, 0.26, 0.12, x, y, 0.38, rz=rz)


def _vending_machine(props, x, y, ry=0.0):
    props.box('city_vending_grn', 0.55, 0.42, 0.72, x, y, 0.36, ry=ry)
    props.box('windows_glow', 0.38, 0.04, 0.48, x, y - 0.22, 0.52, ry=ry)
    props.box('city_trim_white', 0.48, 0.06, 0.12, x, y - 0.22, 0.88, ry=ry)


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

    # 地面、車道、人行道
    ground.box('city_sand', W - 1.0, D - 1.0, 0.12, 0, 0, 0.06)
    ground.box('city_asphalt_wet', 2.15, 9.2, 0.06, 0, 0.5, 0.034)
    ground.box('city_asphalt_wet', 9.2, 2.15, 0.06, 0, 0.5, 0.034)
    ground.box('city_sidewalk', 9.0, 1.05, 0.05, 0, 3.45, 0.038)
    ground.box('city_sidewalk', 9.0, 1.05, 0.05, 0, -2.45, 0.038)
    ground.box('city_sidewalk', 1.05, 3.6, 0.05, -3.8, 0.5, 0.038)
    ground.box('city_sidewalk', 1.05, 3.6, 0.05, 3.8, 0.5, 0.038)
    # 車道白線
    for z in (-2.5, 1.5, 4.0):
        ground.box('city_cross_white', 0.08, 0.04, 4.5, 0.95, z, 0.036)
    _zebra_stripes(ground, 0, 1.2, along_x=True)

    # 港灣岸線與棧橋（對齊 city-jetty）
    ground.box('city_water_edge', 5.4, 0.55, 0.10, 0, 6.35, 0.05)
    ground.box('city_rail', 0.11, 5.2, 0.52, -0.42, 6.2, 0.28)
    ground.box('city_rail', 0.11, 5.2, 0.52, 0.42, 6.2, 0.28)
    ground.box('city_asphalt', 0.88, 5.0, 0.07, 0, 5.95, 0.036)

    # 三排主要街屋（碰撞大致對齊 island-city-data）
    aw = b.part('sign_face_arcade')
    _arcade_building(b, walls, aw, -3.8, 0.5, 3.4, 4.6, 2.15, 'city_wall_coral', 'north')
    tea_sign = b.part('sign_face_bubble')
    _arcade_building(b, walls, tea_sign, 3.8, 0.5, 3.4, 4.6, 2.15, 'city_wall_mint', 'north')
    claw_sign = b.part('sign_face_claw')
    _arcade_building(b, walls, claw_sign, 0, -4.2, 3.6, 2.8, 1.92, 'city_wall_lilac', 'south')

    # 北西／北東角樓
    walls.box('city_wall_yellow', 1.48, 2.45, 1.48, -5.0, 5.05, 1.28)
    walls.box('city_wall_teal', 1.48, 2.45, 1.48, 5.0, 5.05, 1.28)
    for ox in (-5.0, 5.0):
        walls.box('lab_glass', 0.42, 0.38, 0.08, ox - 0.35, 5.38, 1.35)
        walls.box('lab_glass', 0.42, 0.38, 0.08, ox + 0.35, 5.38, 1.35)

    _mount_sign_plane(b, b.part('sign_face_game'), 'sign_face_game',
                      -5.0, 5.52, 1.65, 1.45, 0.48, rx=-0.12)
    _mount_sign_plane(b, b.part('sign_face_711'), 'sign_face_711',
                      5.0, 5.52, 1.65, 1.15, 0.48, rx=-0.12)
    _mount_sign_plane(b, b.part('sign_face_snack'), 'sign_face_snack',
                      -1.35, 5.52, 1.15, 1.35, 0.42, rx=-0.10)

    # 騎樓外街燈
    for lx, ly in ((-3.0, 4.5), (3.0, 4.5), (-3.0, -3.5), (3.0, -3.5)):
        props.cyl('city_traffic_dark', 0.04, 0.05, 1.35, lx, ly, 0.68, seg=6)
        props.box('city_trim_white', 0.24, 0.08, 0.24, lx, ly, 1.38)
        glow.sphere('lantern_glow', 0.088, lx, ly, 1.28, seg=6, rings=4)

    # 棕櫚、機車、販賣機
    for px, py in ((-4.5, 2.8), (4.5, 2.8), (-2.2, -1.2), (2.4, -0.8), (-5.2, -2.5)):
        _palm(props, px, py)
    for px, py, rz in ((-2.0, 3.2, 0.3), (2.2, 2.9, -0.2), (-0.9, -0.35, 1.1), (1.6, 0.1, -0.5)):
        _parked_scooter(props, px, py, rz)
    _vending_machine(props, -4.2, -0.5, ry=0.4)
    _vending_machine(props, 4.0, -1.8, ry=-0.25)

    # 紅綠燈（獨立 part 供 JS 換色）
    pole = b.part('traffic_pole')
    housing = b.part('traffic_housing')
    red = b.part('traffic_red')
    yellow = b.part('traffic_yellow')
    green = b.part('traffic_green')
    tx, ty = 1.2, 1.2
    pole.box('city_traffic_dark', 0.11, 0.11, 2.48, tx, ty, 1.24)
    housing.box('city_traffic_dark', 0.30, 0.16, 0.55, tx, ty + 0.24, 2.52)
    housing.box('city_trim_white', 0.34, 0.04, 0.60, tx, ty + 0.24, 2.52)
    red.sphere('city_traffic_red', 0.13, tx, ty + 0.24, 2.82, seg=8, rings=6)
    yellow.sphere('city_traffic_yel', 0.13, tx, ty + 0.24, 2.52, seg=8, rings=6)
    green.sphere('city_traffic_grn', 0.13, tx, ty + 0.24, 2.22, seg=8, rings=6)
    housing.box('city_traffic_dark', 0.60, 0.07, 0.08, tx, ty, 2.50)

    # 霓虹邊條
    neo = b.part('neon_trim')
    for lx, ly, sw in ((-3.8, 3.2, 2.8), (3.8, 3.2, 2.6), (0, -3.5, 2.4)):
        neo.box('neon_glow', sw, 0.04, 0.05, lx, ly, 2.12)

    return b.finish(out_path, extras={'building_id': 'city', 'island_id': 'city'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city.glb')
    build(argv_out(_default))