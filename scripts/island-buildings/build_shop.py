"""莫卡手搖（shop）：w=4.75 d=3.2。

正面（招牌、遮陽棚、戶外座）朝本地 +Z；真正的進門在西牆（本地 -X），
對齊 island-layout.js entry.at = { x: -3.09, z: -0.85 }。
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
import kit
from kit import Builder, clear_scene, prepare_scene, argv_out

W, D = 4.75, 3.2
H = 1.92
T = 0.14
BASE = 0.08
# 西門本地 glTF：(x, y, z) ≈ (-W/2, 0, -0.85)，站位在更外側 -3.09
DOOR_Z_GLTF = -0.85


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()
    kit.set_palette_overrides({
        'windows_glow': (0xb7e0ef, 0xffb15a),
    })
    b = Builder('shop', W, D)
    FRONT = b.front  # -D/2
    BACK = b.hy      # +D/2 in Blender = glTF -Z
    HX, HY = b.hx, b.hy
    wall_z = BASE + H / 2
    roof_plate = BASE + H + 0.14

    # 台基
    p = b.part('base')
    p.box('shop_trim', W + 0.36, D + 0.36, 0.12, 0, 0, 0.06)
    p.box('shop_wood', W - 0.28, D - 0.28, 0.08, 0, 0, 0.12)

    # 牆：後、左、右；正面中間開敞，兩側墩牆 + 上沿
    p = b.part('walls')
    p.box('shop_wall', W, T, H, 0, BACK - T / 2, wall_z)
    p.box('shop_wall', T, D, H, -HX + T / 2, 0, wall_z)
    p.box('shop_wall', T, D, H, HX - T / 2, 0, wall_z)
    pier = 1.1
    p.box('shop_wall', pier, T, H, -HX + pier / 2, FRONT + T / 2, wall_z)
    p.box('shop_wall', pier, T, H, HX - pier / 2, FRONT + T / 2, wall_z)
    p.box('shop_wall', W, T, 0.46, 0, FRONT + T / 2, BASE + H - 0.23)
    p.box('shop_orange', W - pier * 2 + 0.08, T + 0.04, 0.08, 0, FRONT, BASE + H - 0.5)
    # 角柱
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        p.box('shop_trim', 0.10, 0.10, H, sx * HX, sy * HY, wall_z)

    # 西牆的門（本地 -X）。Blender x = glTF x，Blender y = -glTF z
    door = b.part('door')
    door_x = -HX - 0.02
    door_y = -DOOR_Z_GLTF  # 0.85
    door_h, door_w = 1.28, 0.58
    door.box('shop_wood', 0.07, door_w, door_h, door_x, door_y, BASE + 0.64)
    door.box('shop_wall', 0.09, door_w + 0.10, 0.06, door_x - 0.01, door_y, BASE + 0.64 + 0.66)
    door.box('shop_wall', 0.09, 0.05, door_h, door_x - 0.01, door_y - door_w / 2 - 0.01, BASE + 0.64)
    door.box('shop_wall', 0.09, 0.05, door_h, door_x - 0.01, door_y + door_w / 2 + 0.01, BASE + 0.64)
    b.part('windows_glow').box('windows_glow', 0.03, 0.32, 0.36, door_x - 0.04, door_y, BASE + 0.64 + 0.28)
    door.box('shop_orange', 0.05, 0.05, 0.08, door_x - 0.05, door_y + 0.18, BASE + 0.64)

    # 窗
    win_z = BASE + 1.15
    b.framed_window(HX, win_z, 0.62, 0.58, -0.55, normal='+x', frame='shop_trim')
    b.framed_window(HX, win_z, 0.62, 0.58, 0.55, normal='+x', frame='shop_trim')
    b.framed_window(-HX, win_z, 0.55, 0.50, 0.15, normal='-x', frame='shop_trim')
    b.framed_window(0, win_z + 0.15, 0.70, 0.48, BACK, normal='+y', frame='shop_trim')

    # 室內可見的櫃檯與飲料（從開敞正面看得到）
    pr = b.part('interior_props')
    pr.box('shop_counter', 1.6, 0.46, 0.78, -0.7, BACK - 0.55, BASE + 0.39)
    pr.box('cup_lid', 1.6, 0.50, 0.06, -0.7, BACK - 0.55, BASE + 0.80)
    pr.box('shop_navy', 0.55, 0.50, 1.15, 1.55, BACK - 0.50, BASE + 0.58)
    # 杯子
    for col, x, y in (('cup_pink', -1.15, BACK - 0.55),
                      ('cup_yellow', -0.85, BACK - 0.50),
                      ('cup_green', -0.55, BACK - 0.58)):
        pr.cyl(col, 0.08, 0.07, 0.20, x, y, BASE + 0.84 + 0.10, seg=7)
        pr.cyl('cup_lid', 0.085, 0.085, 0.04, x, y, BASE + 0.84 + 0.22, seg=7)
        pr.cyl('straw', 0.012, 0.012, 0.16, x + 0.03, y, BASE + 0.84 + 0.32, seg=5)
    pr.box('straw', 0.28, 0.20, 0.22, 0.15, FRONT + 0.35, BASE + 0.95)

    # 屋頂：較溫和的茶館廡殿頂（深藍瓦）+ 既有扁平簷口感覺
    # 先做一層平板簷，再疊淺廡殿
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

    # 杯型吉祥物（既有建築屋頂上的大杯子）
    mas = b.part('mascot')
    mx, my, mz = 0.15, 0.20, ztop + 0.18
    mas.cyl('shop_orange', 0.29, 0.22, 0.67, mx, my, mz + 0.20, seg=7)
    mas.cyl('cup_lid', 0.32, 0.32, 0.10, mx, my, mz + 0.58, seg=7)
    mas.cyl('straw', 0.036, 0.036, 0.46, mx + 0.06, my, mz + 0.88, seg=5, rx=0.0, ry=0.2)

    # 招牌：架在前坡、大幅向後仰（對齊既有 tiltX ≈ -0.62）
    b.tilted_sign(2.50, 0.78, FRONT - 0.08, ztop + 0.16, rx=-1.10, frame='shop_orange', board='shop_navy')

    # 遮陽棚（橙白條紋）與支柱
    aw = b.part('awning')
    # 棚頂略向前向下傾
    aw_y = FRONT - 0.02
    aw_z = BASE + H - 0.42
    tilt = 0.38
    for i in range(7):
        col = 'shop_orange' if i % 2 else 'shop_wall'
        # 條紋沿 X，深度沿 -Y（前方）
        # 以 awning group 旋轉近似：中心在 (0, aw_y, aw_z)，繞 X 轉 tilt，條紋在本地 z=0、y 向前
        # 簡化：直接把長板放到傾斜位置
        cx = -1.14 + i * 0.38
        # 旋轉後中心約在前方 0.62、略低
        # 本地 (0, 0.62, 0) 繞 X rot tilt：y' = y cos - z sin, z' = y sin + z cos
        ly, lz = -0.70, 0.0
        yy = aw_y + ly * math.cos(tilt) - lz * math.sin(tilt)
        zz = aw_z + ly * math.sin(tilt) + lz * math.cos(tilt)
        aw.box(col, 0.36, 1.25, 0.045, cx, yy, zz, rx=tilt)
    # 前緣滾邊
    ly, lz = -1.28, -0.02
    yy = aw_y + ly * math.cos(tilt) - lz * math.sin(tilt)
    zz = aw_z + ly * math.sin(tilt) + lz * math.cos(tilt)
    aw.box('shop_orange', 2.60, 0.06, 0.06, 0, yy, zz, rx=tilt)
    # 支柱（左右，不擋西門）
    for sx in (-1.2, 1.2):
        aw.box('shop_wall', 0.06, 0.06, 1.28, sx, FRONT - 0.95, 0.64)

    # 戶外座：遮陽棚下左右各一組桌椅，中央走道淨空
    seat = b.part('seats')
    for sx in (-1.35, 1.35):
        sy = FRONT - 0.55
        seat.cyl('shop_wood', 0.22, 0.22, 0.06, sx, sy, 0.40, seg=8)
        seat.cyl('straw', 0.04, 0.04, 0.38, sx, sy, 0.20, seg=6)
        # 兩張凳
        for dy, dx in ((-0.32, 0.0), (0.28, 0.18)):
            seat.box('shop_trim', 0.22, 0.22, 0.05, sx + dx, sy + dy, 0.28)
            seat.box('shop_wood', 0.05, 0.05, 0.26, sx + dx, sy + dy, 0.14)
            seat.box('shop_trim', 0.22, 0.04, 0.16, sx + dx, sy + dy + 0.09, 0.38)
        # 桌上小杯
        seat.cyl('cup_pink' if sx < 0 else 'cup_yellow', 0.04, 0.035, 0.08, sx, sy, 0.48, seg=6)

    # 門邊盆栽（正面兩側，不擋西門）
    for sx in (-2.0, 2.0):
        pr.cyl('pot', 0.10, 0.08, 0.14, sx, FRONT - 0.25, 0.10)
        pr.sphere('plant', 0.13, sx, FRONT - 0.25, 0.28, sz=0.85, seg=7, rings=4)

    # 東側菜單／佈告板（不擋西門與遮陽棚中央走道）
    mb = b.part('menu_board')
    mx, my = 2.18, FRONT - 0.08
    mb.box('shop_wood', 0.06, 0.06, 1.20, mx, my, 0.60)
    mb.box('shop_navy', 0.58, 0.05, 0.88, mx, my + 0.04, 0.88)
    mb.box('shop_orange', 0.62, 0.06, 0.07, mx, my + 0.04, 1.35)
    mb.box('shop_wall', 0.46, 0.02, 0.10, mx, my + 0.06, 1.18)
    for i, col in enumerate(('cup_pink', 'cup_yellow', 'cup_green', 'shop_trim')):
        mb.box(col, 0.42, 0.02, 0.07, mx, my + 0.06, 1.02 - i * 0.14)
        mb.box('shop_wall', 0.22, 0.015, 0.03, mx - 0.06, my + 0.07, 1.02 - i * 0.14)

    # 東側戶外傘桌（在遮陽棚外側，避免與棚相交）
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

    # 棚下暖燈
    for sx in (-0.9, 0.9):
        b.part('lanterns_glow').sphere('lantern_glow', 0.07, sx, FRONT - 0.35, BASE + H - 0.55, sz=1.1, seg=7, rings=5)

    # label anchor：既有 (0.15, roofY+1.15, -0.05) 是本地 Three 座標（已含 group 在 y=0）
    # roofY = base + H + 0.14，base = G+0.08；GLB 的 base 從 0 起，約 roof_plate
    # 我們用 ztop + 1.15 當高度
    # Want glTF (0.15, ztop+1.15, -0.05) → Blender (0.15, 0.05, ztop+1.15)
    label_blender = (0.15, 0.05, ztop + 1.15)

    return b.finish(
        out_path,
        door_local=[-HX, 0.0, DOOR_Z_GLTF],
        label_anchor=label_blender,
        extras={'building_id': 'shop', 'door_side': '-X'},
    )


if __name__ == '__main__':
    build(argv_out('/workspace/glb-proto/shop.glb'))
