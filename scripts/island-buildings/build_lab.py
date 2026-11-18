"""研發樓（computer-site）：w=2.3 d=1.8，門朝本地 +Z。

白玻璃科技樓、青色 LED 窗格、屋頂太陽能板與衛星碟、機房櫃剪影、門口伺服器、地面青燈回路。
重建：python3 scripts/island-buildings/build_all.py
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
import kit
from kit import Builder, clear_scene, prepare_scene, argv_out

W, D = 2.3, 1.8
T = 0.10
BASE = 0.10
# 三層：每層約 0.95，總高約 3.05，屋頂再加 0.16
STOREYS = 3
STOREY_H = 0.95
H = STOREYS * STOREY_H  # 2.85
DOOR_W = 0.62


def window_band(b, z0, z1, HX, HY, FRONT, BACK, skip_front_center=False):
    """一層的玻璃帶 + 窗框分格。"""
    mid_z = (z0 + z1) / 2
    h = z1 - z0 - 0.08
    g = b.part('windows_glow')
    fr = b.part('mullions')
    # 正面：格條必須在玻璃外側，否則窗格會被蓋住
    if skip_front_center:
        side = (W - DOOR_W) / 2 - 0.08
        for sx in (-1, 1):
            cx = sx * (HX - side / 2 - 0.04)
            g.box('windows_glow', side, 0.03, h, cx, FRONT - 0.02, mid_z)
            fr.box('lab_frame', side + 0.04, 0.05, 0.035, cx, FRONT - 0.05, z1 - 0.04)
            fr.box('lab_frame', side + 0.04, 0.05, 0.035, cx, FRONT - 0.05, z0 + 0.04)
            fr.box('lab_frame', 0.04, 0.05, h, cx, FRONT - 0.05, mid_z)
    else:
        g.box('windows_glow', W - 0.22, 0.03, h, 0, FRONT - 0.02, mid_z)
        for x in (-0.65, -0.22, 0.22, 0.65):
            fr.box('lab_frame', 0.04, 0.05, h, x, FRONT - 0.05, mid_z)
        for z in (z0 + 0.04, mid_z, z1 - 0.04):
            fr.box('lab_frame', W - 0.18, 0.05, 0.035, 0, FRONT - 0.05, z)
    # 背面
    g.box('windows_glow', W - 0.22, 0.03, h, 0, BACK + 0.02, mid_z)
    for x in (-0.65, -0.22, 0.22, 0.65):
        fr.box('lab_frame', 0.04, 0.05, h, x, BACK + 0.05, mid_z)
    for z in (z0 + 0.04, mid_z, z1 - 0.04):
        fr.box('lab_frame', W - 0.18, 0.05, 0.035, 0, BACK + 0.05, z)
    # 兩側
    for sx, n in ((-1, -1), (1, 1)):
        g.box('windows_glow', 0.03, D - 0.22, h, sx * (HX + 0.03), 0, mid_z)
        for y in (-0.45, 0.0, 0.45):
            fr.box('lab_frame', 0.04, 0.03, h, sx * (HX + 0.04), y, mid_z)
        for z in (z0 + 0.04, mid_z, z1 - 0.04):
            fr.box('lab_frame', 0.04, D - 0.18, 0.03, sx * (HX + 0.04), 0, z)


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()
    kit.set_palette_overrides({
        'lab_panel': (0xf5fbff, 0xd7f4ff),
        'lab_grey': (0xe8eef2, 0xb8ccd8),
        'lab_steel': (0xc8d8e4, 0x8aa0b0),
        'lab_steel_dark': (0x9ab0c0, 0x5d7384),
        'windows_glow': (0x7ec8ea, 0x5ad0ff),
        'lantern_glow': (0xa8d8f0, 0x7ec8ea),
        'neon_glow': (0x3cf0ff, 0x3cf0ff),
    })
    b = Builder('computer_lab', W, D)
    FRONT, BACK = b.front, b.hy
    HX, HY = b.hx, b.hy

    # 前坪
    p = b.part('plaza')
    p.box('lab_steel_dark', 1.85, 1.45, 0.04, 0, FRONT - 0.82, 0.02)
    p.box('lab_ground', 1.65, 1.25, 0.03, 0, FRONT - 0.82, 0.04)

    # 地面電路紋路（neon_glow 細條，中央走道淨空）
    cir = b.part('circuits')
    # 主幹線沿兩側向前，再橫向分支出墊點
    for sx in (-0.55, 0.55):
        cir.box('neon_glow', 0.04, 1.15, 0.02, sx, FRONT - 0.75, 0.055)
        for i, dy in enumerate((-0.35, -0.75, -1.10)):
            cir.box('neon_glow', 0.28, 0.04, 0.02, sx + (0.18 if sx > 0 else -0.18), FRONT + dy, 0.055)
            cir.box('neon_glow', 0.10, 0.10, 0.025, sx + (0.28 if sx > 0 else -0.28), FRONT + dy, 0.06)
    # 門前一條橫線（不擋站位正中央，畫在門檻前較近處的兩側之間留缺口）
    cir.box('neon_glow', 0.45, 0.04, 0.02, -0.55, FRONT - 0.28, 0.055)
    cir.box('neon_glow', 0.45, 0.04, 0.02, 0.55, FRONT - 0.28, 0.055)

    # 台基
    st = b.part('base')
    st.box('lab_panel', W + 0.28, D + 0.28, 0.12, 0, 0, 0.06)
    st.box('lab_grey', W - 0.08, D - 0.08, 0.08, 0, 0, 0.12)

    # 四角鋼柱貫穿三層
    fr = b.part('frame')
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        fr.box('lab_steel_dark', 0.12, 0.12, H, sx * HX, sy * HY, BASE + H / 2)
    # 樓層橫梁
    for i in range(STOREYS + 1):
        z = BASE + i * STOREY_H
        fr.box('lab_frame', W + 0.04, D + 0.04, 0.08, 0, 0, z)
    # 層間不透明裙牆（每層底部一小截實牆，讓樓層更清楚）
    pan = b.part('panels')
    for i in range(STOREYS):
        z = BASE + i * STOREY_H + 0.10
        pan.box('lab_panel', W - 0.16, T, 0.16, 0, BACK - T / 2, z)
        pan.box('lab_grey', T, D - 0.16, 0.16, -HX + T / 2, 0, z)
        pan.box('lab_grey', T, D - 0.16, 0.16, HX - T / 2, 0, z)
        if i == 0:
            side = (W - DOOR_W) / 2
            pan.box('lab_grey', side - 0.06, T, 0.16, -HX + side / 2, FRONT + T / 2, z)
            pan.box('lab_grey', side - 0.06, T, 0.16, HX - side / 2, FRONT + T / 2, z)
        else:
            pan.box('lab_grey', W - 0.16, T, 0.16, 0, FRONT + T / 2, z)

    # 窗帶
    for i in range(STOREYS):
        z0 = BASE + i * STOREY_H + 0.20
        z1 = BASE + (i + 1) * STOREY_H - 0.06
        window_band(b, z0, z1, HX, HY, FRONT, BACK, skip_front_center=(i == 0))

    # 玻璃門
    door = b.part('door')
    door.box('lab_steel_dark', DOOR_W, 0.05, 1.28, 0, FRONT + 0.02, BASE + 0.68)
    b.part('windows_glow').box('windows_glow', 0.50, 0.03, 1.08, 0, FRONT - 0.02, BASE + 0.70)
    door.box('lab_accent', DOOR_W + 0.10, 0.07, 0.05, 0, FRONT, BASE + 0.68 + 0.66)
    door.box('lab_accent', 0.045, 0.07, 1.28, -DOOR_W / 2, FRONT, BASE + 0.68)
    door.box('lab_accent', 0.045, 0.07, 1.28, DOOR_W / 2, FRONT, BASE + 0.68)
    door.box('lab_accent', 0.04, 0.04, 0.07, 0.18, FRONT - 0.04, BASE + 0.62)

    # 門口伺服器機櫃（兩側，中央走道淨空）
    racks = b.part('racks')
    neo = b.part('neon')
    for sx in (-0.95, 0.95):
        racks.box('lab_steel_dark', 0.36, 0.32, 0.85, sx, FRONT - 0.42, BASE + 0.48)
        racks.box('lab_frame', 0.38, 0.34, 0.06, sx, FRONT - 0.42, BASE + 0.93)
        # LED 燈條
        for zi, col_z in enumerate((0.30, 0.48, 0.66)):
            neo.box('neon_glow', 0.26, 0.02, 0.03, sx, FRONT - 0.58, BASE + col_z)
        # 頂部指示燈
        b.part('lanterns_glow').sphere('lantern_glow', 0.04, sx, FRONT - 0.42, BASE + 1.00, seg=6, rings=4)

    # 屋頂
    roof_y = BASE + H + 0.08
    rf = b.part('roof')
    rf.box('lab_panel', W + 0.12, D + 0.12, 0.10, 0, 0, roof_y - 0.08)
    rf.box('lab_steel_dark', W + 0.36, D + 0.28, 0.14, 0, 0, roof_y)
    # 青光屋簷
    neo.box('neon_glow', W + 0.50, D + 0.42, 0.05, 0, 0, roof_y + 0.10)
    rf.box('lab_steel', W + 0.20, D + 0.14, 0.04, 0, 0, roof_y + 0.13)
    # 屋頂機櫃／空調
    rf.box('lab_grey', 0.55, 0.42, 0.28, -0.55, 0.15, roof_y + 0.28)
    rf.box('lab_frame', 0.50, 0.10, 0.06, -0.55, 0.15, roof_y + 0.44)
    rf.cyl('lab_steel', 0.06, 0.06, 0.16, -0.70, 0.28, roof_y + 0.36, seg=6)

    # 屋頂太陽能板
    rf.box('lab_steel_dark', 0.75, 0.45, 0.04, 0.45, -0.25, roof_y + 0.18)
    rf.box('lab_panel', 0.72, 0.42, 0.03, 0.45, -0.25, roof_y + 0.215)
    rf.box('lab_steel_dark', 0.42, 0.55, 0.04, -0.52, 0.05, roof_y + 0.18)
    rf.box('lab_panel', 0.4, 0.52, 0.03, -0.52, 0.05, roof_y + 0.215)

    # 衛星碟（屋頂右側；島上主題小碟已縮小，避免重複大碟）
    ant = b.part('antenna')
    ant.cyl('lab_steel', 0.05, 0.05, 0.48, 0.55, -0.15, roof_y + 0.40, seg=6)
    ant.cyl('lab_panel', 0.26, 0.05, 0.05, 0.68, -0.15, roof_y + 0.58, seg=8, rx=0.85)
    ant.box('lab_steel', 0.02, 0.02, 0.16, 0.62, -0.15, roof_y + 0.58)
    b.part('lanterns_glow').sphere('lantern_glow', 0.07, 0.55, -0.15, roof_y + 0.78, seg=6, rings=5)

    # 招牌：屋頂前緣向後仰，高視角可讀
    b.tilted_sign(1.40, 0.46, FRONT - 0.10, roof_y + 0.28, rx=-1.05, frame='lab_accent', board='lab_steel_dark')

    # 門旁小燈
    for sx in (-0.85, 0.85):
        b.part('lanterns_glow').sphere('lantern_glow', 0.055, sx, FRONT - 0.10, BASE + 1.20, seg=6, rings=4)
        fr.box('lab_steel', 0.03, 0.03, 0.12, sx, FRONT - 0.10, BASE + 1.10)

    label_blender = (0.0, -0.12, roof_y + 1.05)
    return b.finish(
        out_path,
        door_local=[0.0, 0.0, HY],
        label_anchor=label_blender,
        extras={'building_id': 'computer-site'},
    )


if __name__ == '__main__':
    build(argv_out('/workspace/glb-proto/computer-site.glb'))
