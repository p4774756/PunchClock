"""神之島手搖店（shop）：w=4.75 d=3.2，西門在本地 -X（#156）。

正面（北／預設預設鏡頭）＝ kit FRONT（Blender -Y = glTF +Z）；主招牌 sign_face 朝北可讀。
無頭重建：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_shop.py -- \\
    server/src/main/resources/public/models/shop.glb
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

W, D = 4.75, 3.2
H = 1.92
T = 0.14
BASE = 0.08
DOOR_Z_GLTF = -0.85


def _eaves_bulbs(b, part_name, xs, y, z, hang=0.0):
    glow = b.part(part_name)
    for i, x in enumerate(xs):
        d = hang + (i % 3) * 0.015
        glow.sphere('lantern_glow', 0.042, x, y, z - d, seg=5, rings=3)


def _north_promo_board(b, x, z, w=0.58, h=0.30):
    bd = b.part('promo_boards')
    y = b.front - 0.12
    bd.box('shop_orange', w + 0.08, 0.04, h + 0.08, x, y, z)
    bd.box('shop_navy', w, 0.025, h, x, y + 0.02, z)
    bd.box('shop_wall', w - 0.12, 0.015, 0.04, x, y + 0.03, z + 0.04)
    bd.box('cup_pink', 0.14, 0.015, 0.14, x - w * 0.28, y + 0.035, z - 0.02)


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()

    set_palette_overrides({
        'windows_glow': (0xb7e0ef, 0xffb15a),
        'lantern_glow': (0xfff0d0, 0xffc56a),
    })

    b = Builder('shop', W, D)
    FRONT = b.front
    BACK = b.hy
    HX, HY = b.hx, b.hy
    wall_z = BASE + H / 2
    roof_plate = BASE + H + 0.14

    p = b.part('base')
    p.box('shop_trim', W + 0.36, D + 0.36, 0.12, 0, 0, 0.06)
    p.box('shop_wood', W - 0.28, D - 0.28, 0.08, 0, 0, 0.12)

    p = b.part('walls')
    p.box('shop_wall', W, T, H, 0, BACK - T / 2, wall_z)
    p.box('shop_wall', T, D, H, -HX + T / 2, 0, wall_z)
    p.box('shop_wall', T, D, H, HX - T / 2, 0, wall_z)
    pier = 1.1
    p.box('shop_wall', pier, T, H, -HX + pier / 2, FRONT + T / 2, wall_z)
    p.box('shop_wall', pier, T, H, HX - pier / 2, FRONT + T / 2, wall_z)
    p.box('shop_wall', W - pier * 2, T, 0.52, 0, FRONT + T / 2, BASE + H - 0.20)
    p.box('shop_orange', W - pier * 2 + 0.08, T + 0.04, 0.08, 0, FRONT, BASE + H - 0.52)
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        p.box('shop_trim', 0.10, 0.10, H, sx * HX, sy * HY, wall_z)

    # 北立面層次：上下窗帶（俯視鏡頭主看點）
    win_z = BASE + 1.12
    b.framed_window(-1.05, win_z, 0.55, 0.52, FRONT, normal='-y', frame='shop_trim', frame_part='north_frames')
    b.framed_window(1.05, win_z, 0.55, 0.52, FRONT, normal='-y', frame='shop_trim', frame_part='north_frames')
    b.framed_window(0.0, win_z + 0.52, 0.85, 0.44, FRONT, normal='-y', frame='shop_navy', frame_part='north_frames')
    b.framed_window(HX, win_z, 0.62, 0.58, -0.55, normal='+x', frame='shop_trim')
    b.framed_window(HX, win_z, 0.62, 0.58, 0.55, normal='+x', frame='shop_trim')
    b.framed_window(-HX, win_z, 0.55, 0.50, 0.15, normal='-x', frame='shop_trim')
    b.framed_window(0, win_z + 0.15, 0.70, 0.48, BACK, normal='+y', frame='shop_trim')

    door = b.part('door')
    door_x = -HX - 0.02
    door_y = -DOOR_Z_GLTF
    door_h, door_w = 1.28, 0.58
    door.box('shop_wood', 0.07, door_w, door_h, door_x, door_y, BASE + 0.64)
    door.box('shop_wall', 0.09, door_w + 0.10, 0.06, door_x - 0.01, door_y, BASE + 0.64 + 0.66)
    door.box('shop_wall', 0.09, 0.05, door_h, door_x - 0.01, door_y - door_w / 2 - 0.01, BASE + 0.64)
    door.box('shop_wall', 0.09, 0.05, door_h, door_x - 0.01, door_y + door_w / 2 + 0.01, BASE + 0.64)
    b.part('windows_glow').box('windows_glow', 0.03, 0.32, 0.36, door_x - 0.04, door_y, BASE + 0.64 + 0.28)
    door.box('shop_orange', 0.05, 0.05, 0.08, door_x - 0.05, door_y + 0.18, BASE + 0.64)

    pr = b.part('interior_props')
    pr.box('shop_counter', 1.6, 0.46, 0.78, -0.7, BACK - 0.55, BASE + 0.39)
    pr.box('cup_lid', 1.6, 0.50, 0.06, -0.7, BACK - 0.55, BASE + 0.80)
    pr.box('shop_navy', 0.55, 0.50, 1.15, 1.55, BACK - 0.50, BASE + 0.58)
    for col, x, y in (('cup_pink', -1.15, BACK - 0.55),
                      ('cup_yellow', -0.85, BACK - 0.50),
                      ('cup_green', -0.55, BACK - 0.58)):
        pr.cyl(col, 0.08, 0.07, 0.20, x, y, BASE + 0.84 + 0.10, seg=7)
        pr.cyl('cup_lid', 0.085, 0.085, 0.04, x, y, BASE + 0.84 + 0.22, seg=7)
        pr.cyl('straw', 0.012, 0.012, 0.16, x + 0.03, y, BASE + 0.84 + 0.32, seg=5)
    pr.box('straw', 0.28, 0.20, 0.22, 0.15, FRONT + 0.35, BASE + 0.95)

    eaves = b.part('eaves_fascia')
    ez = BASE + H - 0.06
    eaves.box('shop_trim', W + 0.28, 0.14, 0.10, 0, FRONT + 0.02, ez)
    eaves.box('shop_orange', W + 0.22, 0.08, 0.06, 0, FRONT + 0.05, ez + 0.05)
    eaves.box('shop_trim', 0.12, D + 0.22, 0.09, -HX - 0.04, 0, ez)
    eaves.box('shop_trim', 0.12, D + 0.22, 0.09, HX + 0.04, 0, ez)
    eaves.box('shop_trim', W + 0.18, 0.10, 0.08, 0, BACK - 0.04, ez)

    rf = b.part('roof_plate')
    rf.box('shop_wall', W + 0.15, D + 0.15, 0.08, 0, 0, roof_plate - 0.16)
    Z0 = roof_plate - 0.02
    RISE = 0.42
    EHX, EHY = HX + 0.32, HY + 0.22
    RHX, RHY = 1.95, 0.18
    rings, nx, ny, nr = b.hip_roof(
        'roof', Z0, RISE, EHX, EHY, RHX, RHY,
        nx=12, ny=8, nr=5, lift=0.10, push=0.06,
        tile='shop_navy', tile_dark='shop_navy_dark',
        ridge_mat='shop_navy_dark', under='shop_wall',
    )
    rid = b.part('roof_ridge')
    ztop = Z0 + RISE
    rid.box('shop_navy_dark', 2 * RHX + 0.16, 0.12, 0.08, 0, 0, ztop + 0.04)
    rid.box('shop_orange', 2 * RHX + 0.16, 0.13, 0.02, 0, 0, ztop + 0.09)

    mas = b.part('mascot')
    mx, my, mz = 0.15, 0.20, ztop + 0.18
    mas.cyl('shop_orange', 0.29, 0.22, 0.67, mx, my, mz + 0.20, seg=7)
    mas.cyl('cup_lid', 0.32, 0.32, 0.10, mx, my, mz + 0.58, seg=7)
    mas.cyl('straw', 0.036, 0.036, 0.46, mx + 0.06, my, mz + 0.88, seg=5, rx=0.0, ry=0.2)

    # 主招牌：北坡屋頂略仰，俯視鏡頭可讀「神之島」（sign_face / shopSignMap）
    b.tilted_sign(2.50, 0.78, FRONT - 0.08, ztop + 0.16, rx=-1.10, facing='front',
                  frame='shop_orange', board='shop_navy')

    aw = b.part('awning')
    aw_y = FRONT - 0.02
    aw_z = BASE + H - 0.42
    tilt = 0.38
    for i in range(7):
        col = 'shop_orange' if i % 2 else 'shop_wall'
        cx = -1.14 + i * 0.38
        ly, lz = -0.70, 0.0
        yy = aw_y + ly * math.cos(tilt) - lz * math.sin(tilt)
        zz = aw_z + ly * math.sin(tilt) + lz * math.cos(tilt)
        aw.box(col, 0.36, 1.25, 0.045, cx, yy, zz, rx=tilt)
    ly, lz = -1.28, -0.02
    yy = aw_y + ly * math.cos(tilt) - lz * math.sin(tilt)
    zz = aw_z + ly * math.sin(tilt) + lz * math.cos(tilt)
    aw.box('shop_orange', 2.60, 0.06, 0.06, 0, yy, zz, rx=tilt)
    for sx in (-1.2, 1.2):
        aw.box('shop_wall', 0.06, 0.06, 1.28, sx, FRONT - 0.95, 0.64)

    # 屋簷串燈（emissive，取代 JS attachShopEavesDecor）
    bulbs_x = [round(-2.05 + i * (4.1 / 13), 3) for i in range(14)]
    wire = b.part('eave_wire')
    wire_y = FRONT - 0.08
    wire_z = BASE + H - 0.22
    wire.box('straw', 4.35, 0.018, 0.018, 0, wire_y, wire_z)
    _eaves_bulbs(b, 'eave_lamps', bulbs_x, wire_y + 0.02, wire_z - 0.03, hang=0.02)
    # 東北簷角一串（俯視可見）
    side_xs = [HX - 0.08 - i * 0.28 for i in range(5)]
    side_zs = [BASE + H - 0.18 - i * 0.02 for i in range(5)]
    wire.box('straw', 0.018, 1.12, 0.018, HX - 0.12, FRONT - 0.35, wire_z)
    for x, z in zip(side_xs, side_zs):
        b.part('eave_lamps').sphere('lantern_glow', 0.038, x, FRONT - 0.36, z, seg=5, rings=3)

    _north_promo_board(b, -1.42, BASE + H - 0.72)
    _north_promo_board(b, 1.35, BASE + H - 0.72)

    seat = b.part('seats')
    for sx in (-1.35, 1.35):
        sy = FRONT - 0.55
        seat.cyl('shop_wood', 0.22, 0.22, 0.06, sx, sy, 0.40, seg=8)
        seat.cyl('straw', 0.04, 0.04, 0.38, sx, sy, 0.20, seg=6)
        for dy, dx in ((-0.32, 0.0), (0.28, 0.18)):
            seat.box('shop_trim', 0.22, 0.22, 0.05, sx + dx, sy + dy, 0.28)
            seat.box('shop_wood', 0.05, 0.05, 0.26, sx + dx, sy + dy, 0.14)
            seat.box('shop_trim', 0.22, 0.04, 0.16, sx + dx, sy + dy + 0.09, 0.38)
        seat.cyl('cup_pink' if sx < 0 else 'cup_yellow', 0.04, 0.035, 0.08, sx, sy, 0.48, seg=6)

    for sx in (-2.0, 2.0):
        pr.cyl('pot', 0.10, 0.08, 0.14, sx, FRONT - 0.25, 0.10)
        pr.sphere('plant', 0.13, sx, FRONT - 0.25, 0.28, sz=0.85, seg=7, rings=4)

    mb = b.part('menu_board')
    mbx, mby = 2.18, FRONT - 0.08
    mb.box('shop_wood', 0.06, 0.06, 1.20, mbx, mby, 0.60)
    mb.box('shop_navy', 0.58, 0.05, 0.88, mbx, mby + 0.04, 0.88)
    mb.box('shop_orange', 0.62, 0.06, 0.07, mbx, mby + 0.04, 1.35)
    mb.box('shop_wall', 0.46, 0.02, 0.10, mbx, mby + 0.06, 1.18)
    for i, col in enumerate(('cup_pink', 'cup_yellow', 'cup_green', 'shop_trim')):
        mb.box(col, 0.42, 0.02, 0.07, mbx, mby + 0.06, 1.02 - i * 0.14)
        mb.box('shop_wall', 0.22, 0.015, 0.03, mbx - 0.06, mby + 0.07, 1.02 - i * 0.14)

    um = b.part('umbrella')
    tx, ty = 2.20, FRONT - 0.72
    um.box('shop_wood', 0.58, 0.58, 0.06, tx, ty, 0.42)
    for dx, dy in ((-0.22, -0.22), (0.22, -0.22), (-0.22, 0.22), (0.22, 0.22)):
        um.box('shop_wood', 0.05, 0.05, 0.40, tx + dx, ty + dy, 0.20)
    um.box('shop_trim', 0.22, 0.22, 0.05, tx - 0.42, ty + 0.28, 0.28)
    um.box('shop_wood', 0.05, 0.05, 0.26, tx - 0.42, ty + 0.28, 0.14)
    um.box('shop_trim', 0.22, 0.04, 0.16, tx - 0.42, ty + 0.38, 0.38)
    um.cyl('shop_wall', 0.025, 0.025, 1.22, tx, ty, 1.04, seg=6)
    um.cyl('umbrella_yellow', 0.62, 0.02, 0.20, tx, ty, 1.72, seg=8)
    um.sphere('cup_lid', 0.05, tx, ty, 1.84, seg=5, rings=3)
    um.cyl('cup_pink', 0.04, 0.035, 0.08, tx + 0.12, ty, 0.50, seg=6)

    for sx in (-0.9, 0.9):
        b.part('lanterns_glow').sphere('lantern_glow', 0.07, sx, FRONT - 0.35, BASE + H - 0.55, sz=1.1, seg=7, rings=5)

    label_blender = (0.15, 0.05, ztop + 1.15)

    return b.finish(
        out_path,
        door_local=[-HX, 0.0, DOOR_Z_GLTF],
        label_anchor=label_blender,
        extras={'building_id': 'shop', 'door_side': '-X'},
    )


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'shop.glb')
    build(argv_out(_default))