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


def _wall_tex(walls, mat, sx, sy, sz, x, y, z, **kw):
    """盡量走圖集，避免素色大白盒。"""
    if mat in REGIONS:
        walls.box_tex(f'@{mat}', _au(mat), sx, sy, sz, x, y, z, **kw)
    elif mat == 'wood':
        walls.box_tex('@wood', _au('wood'), sx, sy, sz, x, y, z, **kw)
    else:
        walls.box_tex('@city_facade', _au('city_facade'), sx, sy, sz, x, y, z, **kw)


def _shophouse(b, walls, sign_part, neon_part, lx, lz, bw, bd, bh, tex_wall, sign_key,
               face_north=True, awning='city_awning'):
    """多層騎樓：圖集外牆＋柱列＋遮陽棚＋朝北（-Y／glTF +Z）立面大招牌。"""
    street = -1 if face_north else 1
    fy = lz + street * (bd / 2 - 0.08)
    uv = _au(tex_wall)
    uv_facade = _au('city_facade')
    walls.box_tex(f'@{tex_wall}', uv, bw, bh, bd, lx, lz, bh / 2 + 0.06)
    _wall_tex(walls, 'wood', bw - 0.6, 0.12, 1.25, lx, fy - street * 0.38, 0.16)
    for ox in (-bw * 0.38, 0.0, bw * 0.38):
        walls.box_tex('@city_facade', uv_facade, 0.18, bh * 0.88, 0.18,
                      lx + ox, fy - street * 0.10, bh * 0.46)
    walls.box_tex(f'@{awning}', _au(awning), bw + 0.55, 0.10, 1.15, lx, fy + street * 0.52, bh + 0.06)
    walls.box_tex('@roof_tile', _au('roof_tile'), bw + 0.45, 0.12, bd + 0.22, lx, lz + street * 0.14, bh + 0.12)
    for fz in (bh * 0.52, bh * 0.78):
        walls.box_tex('@city_facade', uv_facade, bw - 0.35, 0.28, 0.14, lx, fy - street * 0.20, fz)
        _wall_tex(walls, tex_wall, 0.28, 0.20, 0.16, lx + bw * 0.30, fy - street * 0.12, fz + 0.04)
        for wx in (-bw * 0.32, bw * 0.32):
            walls.box('lab_glass', 0.44, bh * 0.30, 0.07, lx + wx, fy - street * 0.05, fz + 0.12)
            _wall_tex(walls, 'wood', 0.48, bh * 0.34, 0.05, lx + wx, fy - street * 0.08, fz + 0.12)
    walls.box_tex('@city_facade', uv_facade, min(1.2, bw * 0.58), bh * 0.48, 0.09,
                  lx, fy - street * 0.06, bh * 0.38)
    sw = min(3.35, bw * 0.82)
    sh = 1.28
    sz = bh * 0.82
    y_face = fy + (-0.38 if street < 0 else 0.38)
    pitch = 0.14 if street < 0 else -0.12
    frame = b.part(sign_key + '_frame')
    frame.box_tex('@roof_tile', _au('roof_tile'), sw + 0.16, 0.06, sh + 0.12,
                  lx, y_face - street * 0.04, sz, rx=pitch)
    if street < 0:
        sign_part.plane('sign', sw, sh, lx, y_face - 0.08, sz, rx=pitch, flip_u=False)
    else:
        sign_part.plane('sign', sw, sh, lx, y_face + 0.07, sz, rx=pitch, ry=math.pi, flip_u=True)
    neon_part.box('neon_glow', sw + 0.12, 0.05, 0.06, lx, y_face - street * 0.06, sz + sh / 2 - 0.06, rx=pitch)
    neon_part.box('neon_glow', sw + 0.12, 0.05, 0.06, lx, y_face - street * 0.06, sz - sh / 2 + 0.06, rx=pitch)


def _corner_tower(walls, lx, lz, bw, bd, bh, mat, accent):
    _wall_tex(walls, mat, bw, bh, bd, lx, lz, bh / 2)
    walls.box_tex('@roof_tile', _au('roof_tile'), bw + 0.18, 0.22, bd + 0.18, lx, lz, bh + 0.10)
    for ox, oz in ((-bw * 0.35, -bd * 0.35), (bw * 0.35, -bd * 0.35),
                   (-bw * 0.35, bd * 0.35), (bw * 0.35, bd * 0.35)):
        walls.box('lab_glass', 0.40, 0.36, 0.08, lx + ox, lz + oz, bh * 0.58)


def _mount_sign(b, part_name, lx, ly, lz, sw, sh, rx=0.14):
    """朝 -Y（glTF +Z）街面；探索鏡頭由北往南俯視可讀。"""
    face = b.part(part_name)
    fr = b.part(part_name + '_frame')
    neo = b.part(part_name + '_neon')
    y_face = ly - 0.14
    fr.box_tex('@roof_tile', _au('roof_tile'), sw + 0.20, 0.06, sh + 0.16,
              lx, y_face - 0.05, lz, rx=rx)
    face.plane('sign', sw, sh, lx, y_face + 0.08, lz, rx=rx, flip_u=False)
    neo.box('neon_glow', sw + 0.10, 0.05, 0.06, lx, y_face + 0.11, lz + sh / 2 - 0.05, rx=rx)
    neo.box('neon_glow', sw + 0.10, 0.05, 0.06, lx, y_face + 0.11, lz - sh / 2 + 0.05, rx=rx)


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
    """小綿羊：車頭、車把、雙輪、座椅分離。"""
    props.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.52, 0.09, 0.30, x, y - 0.06, 0.08, rz=rz)
    props.box('city_moto_cream', 0.38, 0.22, 0.32, x, y + 0.02, 0.24, rz=rz)
    props.box('city_moto_dark', 0.34, 0.16, 0.36, x, y - 0.12, 0.28, rz=rz)
    props.box('city_moto_cream', 0.22, 0.18, 0.22, x, y + 0.18, 0.34, rz=rz)
    props.cyl('city_traffic_dark', 0.028, 0.028, 0.36, x + 0.02, y + 0.08, 0.48, seg=5, rz=rz + 0.15)
    props.cyl('city_traffic_dark', 0.022, 0.022, 0.26, x + 0.08, y + 0.20, 0.46, seg=5, rx=0.55, rz=rz)
    props.cyl('city_traffic_dark', 0.022, 0.022, 0.26, x - 0.08, y + 0.20, 0.46, seg=5, rx=0.55, rz=rz)
    props.sphere('windows_glow', 0.055, x, y + 0.22, 0.24, seg=6, rings=4)
    for wx, wy in ((-0.22, 0.14), (0.22, -0.16)):
        props.cyl('city_traffic_dark', 0.11, 0.11, 0.06, x + wx, y + wy, 0.11, seg=8, rx=math.pi / 2, rz=rz)
        props.cyl('city_trim_white', 0.06, 0.06, 0.04, x + wx, y + wy, 0.11, seg=6, rx=math.pi / 2, rz=rz)
    props.sphere('city_traffic_red', 0.045, x, y - 0.26, 0.22, seg=5, rings=4)


def _vending(props, x, y, ry=0.0):
    props.box_tex('@city_wall_mint', _au('city_wall_mint'), 0.58, 0.78, 0.44, x, y, 0.39, ry=ry)
    props.box('windows_glow', 0.42, 0.05, 0.52, x, y - 0.20, 0.48, ry=ry)
    props.box_tex('@city_facade', _au('city_facade'), 0.50, 0.08, 0.14,
                  x, y - 0.20, 0.86, ry=ry)
    props.box('shop_orange', 0.38, 0.02, 0.22, x, y + 0.24, 0.62, ry=ry)


def _harbor(ground, walls, props, b):
    """北側港灣：寬岸線、T 棧橋、雙遊艇；全景可辨。"""
    ground.box_tex('@city_water_edge', _au('city_water_edge'), 9.5, 1.35, 0.14, 0, 6.45, 0.055)
    ground.box_tex('@city_water_edge', _au('city_water_edge'), 8.8, 1.85, 0.08, 0, 6.35, 0.04)
    ground.box_tex('@wood', _au('wood'), 1.35, 7.2, 0.12, 0, 6.05, 0.055)
    ground.box_tex('@wood', _au('wood'), 3.4, 1.05, 0.12, 1.05, 6.55, 0.055)
    for ox in (-1.35, 1.35, 0.0):
        ground.box_tex('@wood', _au('wood'), 1.15, 5.8, 0.14, ox, 6.20, 0.075)
        for z in (5.45, 6.05, 6.65, 7.15):
            walls.box_tex('@wood', _au('wood'), 0.12, 0.14, 0.55, ox, z, 0.34)
    for px, pz in ((-1.45, 6.55), (1.45, 6.55), (-0.7, 6.85), (0.7, 6.85), (0.0, 7.05),
                   (-2.2, 6.75), (2.2, 6.75)):
        props.cyl('city_rail', 0.065, 0.05, 0.52, px, pz, 0.28, seg=6)
    props.box('city_trim_white', 2.55, 0.48, 0.32, -2.05, 6.95, 0.18)
    props.box('shop_navy', 1.85, 0.32, 0.26, -2.05, 6.95, 0.40)
    props.box('lab_glass', 0.62, 0.38, 0.36, -2.05, 6.95, 0.62)
    props.cyl('city_trim_white', 0.06, 0.06, 0.72, -2.05, 6.95, 0.78, seg=5)
    props.box('city_trim_white', 1.25, 0.28, 0.20, 2.05, 6.90, 0.16)
    props.box('shop_navy', 0.95, 0.20, 0.16, 2.05, 6.90, 0.30)
    props.box('lab_glass', 0.48, 0.26, 0.28, 2.05, 6.90, 0.48)
    neo = b.part('harbor_neon')
    neo.box('neon_glow', 0.55, 0.12, 0.06, 0, 6.25, 0.62)


def _traffic(b):
    pole = b.part('traffic_pole')
    housing = b.part('traffic_housing')
    red = b.part('traffic_red')
    yellow = b.part('traffic_yellow')
    green = b.part('traffic_green')
    tx, ty = 1.15, 1.05
    pole.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.10, 0.10, 2.55, tx, ty, 1.28)
    housing.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.32, 0.18, 0.62, tx, ty + 0.22, 2.58)
    housing.box_tex('@city_facade', _au('city_facade'), 0.36, 0.05, 0.66, tx, ty + 0.22, 2.58)
    red.sphere('city_traffic_red', 0.14, tx, ty + 0.22, 2.90, seg=8, rings=6)
    yellow.sphere('city_traffic_yel', 0.14, tx, ty + 0.22, 2.56, seg=8, rings=6)
    green.sphere('city_traffic_grn', 0.14, tx, ty + 0.22, 2.22, seg=8, rings=6)
    housing.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.68, 0.08, 0.10, tx, ty, 2.54)
    housing.box_tex('@city_traffic_dark', _au('city_traffic_dark'), 0.42, 0.05, 0.06, tx, ty + 0.32, 2.16)


def _mini_block(walls, lx, lz, bw, bd, bh, mat):
    if mat in REGIONS:
        walls.box_tex(f'@{mat}', _au(mat), bw, bh, bd, lx, lz, bh / 2)
    else:
        _wall_tex(walls, mat, bw, bh, bd, lx, lz, bh / 2)
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
    neo = b.part('neon_trim')

    ground.box_tex('@city_asphalt', _au('city_asphalt'), W - 0.6, D - 0.6, 0.11, 0, 0, 0.055)
    for slab in ((2.2, 9.4, 0, 0.6), (9.4, 2.2, 0, 0.6), (3.5, 2.0, -2.2, -0.8), (3.5, 2.0, 2.2, 0.8)):
        sw, sd, ox, oz = slab
        ground.box_tex('@city_asphalt_wet', _au('city_asphalt_wet'), sw, sd, 0.07, ox, oz, 0.038)
    for ox, oz in ((0, 3.55), (0, -2.55), (-3.85, 0.55), (3.85, 0.55)):
        ground.box_tex('@city_sidewalk', _au('city_sidewalk'), 8.8, 1.12, 0.06, ox, oz, 0.042)
    for z in (-2.6, 1.6, 4.2):
        ground.box_tex('@city_cross_white', _au('city_cross_white'), 0.09, 0.05, 4.8, 0.95, z, 0.040)
    _zebra(ground, 0, 1.25, along_x=True)
    _zebra(ground, 0, 1.25, along_x=False)

    _harbor(ground, walls, props, b)

    neon_shop = b.part('shop_neon')

    aw = b.part('sign_face_arcade')
    _shophouse(b, walls, aw, neon_shop, -3.8, 0.55, 3.35, 4.55, 2.35,
               'city_wall_coral', 'sign_face_arcade', True)
    tea = b.part('sign_face_bubble')
    _shophouse(b, walls, tea, neon_shop, 3.8, 0.55, 3.35, 4.55, 2.35,
               'city_wall_mint', 'sign_face_bubble', True)
    claw = b.part('sign_face_claw')
    _shophouse(b, walls, claw, neon_shop, 0, -4.15, 3.55, 2.75, 1.95,
               'city_wall_lilac', 'sign_face_claw', False, 'city_awning')

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

    _mount_sign(b, 'sign_face_game', -5.0, 4.28, 2.05, 2.25, 1.05)
    _mount_sign(b, 'sign_face_711', 5.0, 4.28, 2.05, 2.10, 1.05)
    _mount_sign(b, 'sign_face_snack', 0.0, 4.28, 1.85, 2.05, 0.95)

    for lx, ly in ((-3.0, 4.55), (3.0, 4.55), (-3.0, -3.45), (3.0, -3.45), (0, 5.4)):
        props.cyl('city_traffic_dark', 0.045, 0.055, 1.42, lx, ly, 0.72, seg=6)
        props.box_tex('@city_facade', _au('city_facade'), 0.28, 0.09, 0.26, lx, ly, 1.44)
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

    for lx, ly, sw in ((-3.8, 2.55, 3.1), (3.8, 2.55, 2.9), (0, -3.95, 2.65),
                       (-5.0, 4.55, 1.55), (5.0, 4.55, 1.55)):
        neo.box('neon_glow', sw, 0.05, 0.06, lx, ly, 2.22)

    return b.finish(out_path, extras={'building_id': 'city', 'island_id': 'city'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city.glb')
    build(argv_out(_default))