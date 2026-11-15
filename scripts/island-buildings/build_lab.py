"""研發樓（computer-site）：w=2.3 d=1.8，門朝本地 +Z。玻璃鋼構、屋頂光帶。"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
import kit
from kit import Builder, clear_scene, prepare_scene, argv_out

W, D = 2.3, 1.8
H = 2.05
T = 0.10
BASE = 0.08


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()
    kit.set_palette_overrides({
        'windows_glow': (0xb7e4ff, 0x7ec8ea),
        'lantern_glow': (0xa8d8f0, 0x7ec8ea),
        'neon_glow': (0x7ec8ea, 0x7ec8ea),
    })
    b = Builder('computer_lab', W, D)
    FRONT, BACK = b.front, b.hy
    HX, HY = b.hx, b.hy
    wall_z = BASE + H / 2
    door_w = 0.62

    # 前坪 + 既有電纜裝飾
    p = b.part('plaza')
    p.box('paving', 1.55, 1.20, 0.04, 0, FRONT - 0.70, 0.02)
    p.box('lab_ground', 1.30, 0.95, 0.03, 0, FRONT - 0.70, 0.035)
    cab = b.part('cables')
    cab.box('lab_cable', 0.05, 0.90, 0.03, 0.62, FRONT - 0.48, 0.055)
    cab.box('lab_cable', 0.55, 0.05, 0.03, 0.32, FRONT - 0.12, 0.055)
    # 側邊一條電纜管
    cab.cyl('lab_cable', 0.025, 0.025, 0.80, HX + 0.06, 0.20, 0.12, seg=6, ry=math.pi / 2)
    cab.cyl('lab_steel', 0.035, 0.035, 0.08, HX + 0.06, -0.18, 0.12, seg=6)
    cab.cyl('lab_steel', 0.035, 0.035, 0.08, HX + 0.06, 0.58, 0.12, seg=6)

    # 台基
    p = b.part('base')
    p.box('lab_steel', W + 0.32, D + 0.32, 0.12, 0, 0, 0.06)
    p.box('lab_ground', W - 0.16, D - 0.16, 0.08, 0, 0, 0.12)

    # 結構框：四柱 + 上下梁，中間嵌玻璃
    st = b.part('frame')
    # 四角鋼柱
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        st.box('lab_steel_dark', 0.10, 0.10, H, sx * HX, sy * HY, wall_z)
    # 中柱（正面門兩側、背面）
    st.box('lab_steel_dark', 0.08, 0.08, H, -door_w / 2 - 0.04, FRONT + 0.02, wall_z)
    st.box('lab_steel_dark', 0.08, 0.08, H, door_w / 2 + 0.04, FRONT + 0.02, wall_z)
    st.box('lab_steel_dark', 0.08, 0.08, H, 0, BACK - 0.02, wall_z)
    # 樓層橫梁（兩道，做出兩層玻璃的感覺）
    for z in (BASE + 0.12, BASE + H * 0.52, BASE + H - 0.08):
        st.box('lab_frame', W + 0.02, D + 0.02, 0.07, 0, 0, z)

    # 不透明側板（下半截實牆，上半玻璃）
    wll = b.part('panels')
    # 背面下牆
    wll.box('lab_panel', W - 0.16, T, H * 0.38, 0, BACK - T / 2, BASE + H * 0.22)
    # 兩側下牆
    wll.box('lab_panel', T, D - 0.16, H * 0.38, -HX + T / 2, 0, BASE + H * 0.22)
    wll.box('lab_panel', T, D - 0.16, H * 0.38, HX - T / 2, 0, BASE + H * 0.22)
    # 正面門兩側下牆
    side = (W - door_w) / 2
    wll.box('lab_panel', side - 0.06, T, H * 0.38, -HX + side / 2, FRONT + T / 2, BASE + H * 0.22)
    wll.box('lab_panel', side - 0.06, T, H * 0.38, HX - side / 2, FRONT + T / 2, BASE + H * 0.22)

    # 大片玻璃（上半 + 部分下半），用 windows_glow 以便夜間發青光
    # 這裡玻璃材質名稱必須是 windows_glow（code 依名稱換 emissive）
    g = b.part('windows_glow')
    # 正面上門楣玻璃帶
    g.box('windows_glow', W - 0.20, 0.03, 0.70, 0, FRONT - 0.03, BASE + 1.45)
    # 兩側大玻璃
    g.box('windows_glow', 0.03, D - 0.22, 0.85, -HX - 0.03, 0, BASE + 1.40)
    g.box('windows_glow', 0.03, D - 0.22, 0.85, HX + 0.03, 0, BASE + 1.40)
    # 背面玻璃
    g.box('windows_glow', W - 0.22, 0.03, 0.70, 0, BACK + 0.03, BASE + 1.45)
    # 室內隱約的機櫃剪影（從玻璃看出去）
    racks = b.part('racks')
    for i, x in enumerate((-0.55, 0.0, 0.55)):
        racks.box('lab_steel_dark', 0.32, 0.28, 0.70, x, 0.15, BASE + 1.15)
        b.part('neon').box('neon_glow', 0.26, 0.02, 0.02, x, 0.15 - 0.15, BASE + 1.05 + (i % 2) * 0.2)

    # 玻璃上的窗框分格
    fr = b.part('mullions')
    for x in (-0.55, 0.0, 0.55):
        fr.box('lab_frame', 0.03, 0.04, 0.70, x, FRONT - 0.02, BASE + 1.45)
    for z in (BASE + 1.20, BASE + 1.70):
        fr.box('lab_frame', W - 0.16, 0.04, 0.03, 0, FRONT - 0.02, z)
    for y in (-0.45, 0.0, 0.45):
        fr.box('lab_frame', 0.04, 0.03, 0.85, HX + 0.04, y, BASE + 1.40)
        fr.box('lab_frame', 0.04, 0.03, 0.85, -HX - 0.04, y, BASE + 1.40)

    # 玻璃門
    door = b.part('door')
    door.box('lab_steel_dark', door_w, 0.05, 1.32, 0, FRONT + 0.02, BASE + 0.66)
    g.box('windows_glow', 0.50, 0.03, 1.10, 0, FRONT - 0.02, BASE + 0.70)
    door.box('lab_accent', door_w + 0.10, 0.07, 0.05, 0, FRONT, BASE + 0.66 + 0.68)
    door.box('lab_accent', 0.045, 0.07, 1.32, -door_w / 2, FRONT, BASE + 0.66)
    door.box('lab_accent', 0.045, 0.07, 1.32, door_w / 2, FRONT, BASE + 0.66)
    door.box('lab_accent', 0.04, 0.04, 0.07, 0.18, FRONT - 0.04, BASE + 0.64)

    # 屋頂平板 + 一圈發光屋簷（neon_glow）
    roof_y = BASE + H + 0.08
    rf = b.part('roof')
    rf.box('lab_panel', W + 0.16, D + 0.16, 0.10, 0, 0, roof_y - 0.10)
    rf.box('lab_steel_dark', W + 0.40, D + 0.34, 0.16, 0, 0, roof_y)
    neo = b.part('neon')
    neo.box('neon_glow', W + 0.50, D + 0.44, 0.05, 0, 0, roof_y + 0.11)
    # 內縮一圈暗邊，讓光帶更像「邊緣」
    rf.box('lab_steel', W + 0.22, D + 0.18, 0.04, 0, 0, roof_y + 0.14)

    # 天線（既有地標：桿 + 發光球）
    ant = b.part('antenna')
    ant.box('lab_panel', 0.08, 0.08, 0.62, 0.32, 0.12, roof_y + 0.42)
    b.part('lanterns_glow').sphere('lantern_glow', 0.09, 0.32, 0.12, roof_y + 0.80, seg=6, rings=5)
    # 小碟形天線
    ant.cyl('lab_steel', 0.16, 0.02, 0.04, 0.32, 0.12, roof_y + 0.55, seg=8, rx=0.6)
    ant.box('lab_steel', 0.02, 0.02, 0.12, 0.32, 0.12, roof_y + 0.48)

    # 招牌：屋頂前緣、向後仰（既有 tiltX ≈ -0.16，再仰一點）
    b.tilted_sign(1.35, 0.46, FRONT - 0.10, roof_y + 0.24, rx=-1.05, frame='lab_accent', board='lab_steel_dark')

    # 門旁小燈
    for sx in (-0.85, 0.85):
        b.part('lanterns_glow').sphere('lantern_glow', 0.06, sx, FRONT - 0.12, BASE + 1.25, seg=6, rings=4)
        st.box('lab_steel', 0.03, 0.03, 0.12, sx, FRONT - 0.12, BASE + 1.16)

    # label：既有 (0, roofY+1.05, 0.12)
    label_blender = (0.0, -0.12, roof_y + 1.05)

    return b.finish(
        out_path,
        door_local=[0.0, 0.0, HY],
        label_anchor=label_blender,
        extras={'building_id': 'computer-site'},
    )


if __name__ == '__main__':
    build(argv_out('/workspace/glb-proto/computer-site.glb'))
