"""賭場（casino）：w=2.4 d=1.9，門朝本地 +Z。華麗、金邊、neon_glow。"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
import kit
from kit import Builder, clear_scene, prepare_scene, argv_out

W, D = 2.4, 1.9
H = 2.15
T = 0.14
BASE = 0.08


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()
    b = Builder('casino', W, D)
    FRONT, BACK = b.front, b.hy
    HX, HY = b.hx, b.hy
    wall_z = BASE + H / 2
    door_w = 0.72

    # 門前紅毯與石坪（既有外觀有這塊）
    p = b.part('plaza')
    p.box('paving', 2.15, 1.70, 0.04, 0, FRONT - 0.85, 0.02)
    p.box('shop_trim', 1.90, 1.45, 0.03, 0, FRONT - 0.85, 0.04)
    p.box('cas_carpet', 0.78, 1.55, 0.025, 0, FRONT - 0.95, 0.055)

    # 金台基
    p = b.part('base')
    p.box('gold', W + 0.40, D + 0.40, 0.12, 0, 0, 0.06)
    p.box('cas_dark', W - 0.20, D - 0.20, 0.08, 0, 0, 0.12)

    # 牆
    p = b.part('walls')
    p.box('cas_wall', W, T, H, 0, BACK - T / 2, wall_z)
    p.box('cas_wall', T, D, H, -HX + T / 2, 0, wall_z)
    p.box('cas_wall', T, D, H, HX - T / 2, 0, wall_z)
    side = (W - door_w) / 2
    p.box('cas_wall', side, T, H, -HX + side / 2, FRONT + T / 2, wall_z)
    p.box('cas_wall', side, T, H, HX - side / 2, FRONT + T / 2, wall_z)
    p.box('cas_wall', door_w + 0.10, T, 0.42, 0, FRONT + T / 2, BASE + H - 0.21)
    p.box('gold', door_w + 0.16, T + 0.04, 0.08, 0, FRONT, BASE + H - 0.48)
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        p.box('gold', 0.10, 0.10, H, sx * HX, sy * HY, wall_z)

    # 金腰線與垂直飾條
    trim = b.part('gold_trim')
    trim.box('gold', W + 0.04, D + 0.04, 0.05, 0, 0, BASE + 0.55)
    trim.box('gold', W + 0.04, D + 0.04, 0.05, 0, 0, BASE + H - 0.18)
    for sx in (-1, 1):
        trim.box('gold', 0.04, 0.04, H - 0.2, sx * (HX + 0.03), FRONT + 0.02, wall_z)
        trim.box('gold', 0.04, 0.04, H - 0.2, sx * (HX + 0.03), BACK - 0.02, wall_z)

    # 霓虹燈管（簷下、門框、腰線）
    neo = b.part('neon')
    # 正面門框一圈
    neo.box('neon_glow', door_w + 0.22, 0.03, 0.03, 0, FRONT - 0.04, BASE + 1.48)
    neo.box('neon_glow', 0.03, 0.03, 1.40, -door_w / 2 - 0.08, FRONT - 0.04, BASE + 0.75)
    neo.box('neon_glow', 0.03, 0.03, 1.40, door_w / 2 + 0.08, FRONT - 0.04, BASE + 0.75)
    # 簷口一圈
    eave_z = BASE + H + 0.10
    neo.box('neon_glow', W + 0.62, 0.04, 0.04, 0, FRONT - 0.28, eave_z)
    neo.box('neon_glow', W + 0.62, 0.04, 0.04, 0, BACK + 0.28, eave_z)
    neo.box('neon_glow', 0.04, D + 0.56, 0.04, -HX - 0.28, 0, eave_z)
    neo.box('neon_glow', 0.04, D + 0.56, 0.04, HX + 0.28, 0, eave_z)

    # 門
    door = b.part('door')
    door.box('cas_dark', door_w, 0.08, 1.44, 0, FRONT + 0.02, BASE + 0.72)
    door.box('gold', door_w + 0.10, 0.10, 0.06, 0, FRONT, BASE + 0.72 + 0.74)
    door.box('gold', 0.05, 0.10, 1.44, -door_w / 2, FRONT, BASE + 0.72)
    door.box('gold', 0.05, 0.10, 1.44, door_w / 2, FRONT, BASE + 0.72)
    door.box('cas_gold_light', 0.04, 0.04, 1.40, 0, FRONT - 0.01, BASE + 0.72)
    g = b.part('windows_glow')
    g.box('windows_glow', 0.28, 0.03, 0.34, -0.16, FRONT - 0.03, BASE + 0.72 + 0.22)
    g.box('windows_glow', 0.28, 0.03, 0.34, 0.16, FRONT - 0.03, BASE + 0.72 + 0.22)
    door.box('gold', 0.05, 0.05, 0.08, 0.22, FRONT - 0.04, BASE + 0.67)

    # 門檻
    p.box('gold', 0.90, 0.55, 0.08, 0, FRONT - 0.35, BASE + 0.02)

    # 窗（金色框、暖光）
    win_z = BASE + 1.15
    b.framed_window(HX, win_z, 0.55, 0.62, -0.35, normal='+x', frame='gold')
    b.framed_window(HX, win_z, 0.55, 0.62, 0.40, normal='+x', frame='gold')
    b.framed_window(-HX, win_z, 0.55, 0.58, 0.05, normal='-x', frame='gold')
    b.framed_window(0, win_z + 0.10, 0.70, 0.50, BACK, normal='+y', frame='gold')

    # 屋頂：深紫平板 + 金簷 + 錐頂寶珠（既有地標）
    roof_y = BASE + H + 0.12
    rf = b.part('roof')
    rf.box('cas_purple', W + 0.20, D + 0.20, 0.08, 0, 0, roof_y - 0.14)
    rf.box('cas_roof', W + 0.55, D + 0.55, 0.28, 0, 0, roof_y)
    # 金簷只做一圈邊，不要整片蓋住紫屋頂
    ow, od, et, eh = W + 0.70, D + 0.70, 0.10, 0.07
    rf.box('gold', ow, et, eh, 0, -(od - et) / 2, roof_y + 0.16)
    rf.box('gold', ow, et, eh, 0, (od - et) / 2, roof_y + 0.16)
    rf.box('gold', et, od, eh, -(ow - et) / 2, 0, roof_y + 0.16)
    rf.box('gold', et, od, eh, (ow - et) / 2, 0, roof_y + 0.16)
    # 角上金飾
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        rf.box('gold', 0.12, 0.12, 0.10, sx * (HX + 0.28), sy * (HY + 0.28), roof_y + 0.22)
        rf.cyl('gold', 0.03, 0.0, 0.12, sx * (HX + 0.28), sy * (HY + 0.28), roof_y + 0.32, seg=5)
    # 錐頂
    sp = b.part('spire')
    sp.cyl('gold', 0.42, 0.0, 0.55, 0, 0, roof_y + 0.55, seg=6)
    sp.sphere('cas_gold_light', 0.08, 0, 0, roof_y + 0.86, seg=5, rings=4)
    b.part('lanterns_glow').sphere('lantern_glow', 0.09, 0, 0, roof_y + 0.86, sz=1.0, seg=6, rings=4)

    # 招牌：屋頂前方，向後仰（既有 tiltX ≈ -0.35，這裡再仰一點方便高視角）
    b.tilted_sign(1.90, 0.58, FRONT - 0.12, roof_y + 0.36, rx=-1.05, frame='gold', board='cas_dark')
    # 招牌上下各一條霓虹
    neo.box('neon_glow', 2.05, 0.04, 0.035, 0, FRONT - 0.22, roof_y + 0.62, rx=-1.05)
    neo.box('neon_glow', 2.05, 0.04, 0.035, 0, FRONT - 0.22, roof_y + 0.10, rx=-1.05)

    # 門前金柱燈
    lp = b.part('lamp_posts')
    for sx in (-0.55, 0.55):
        lp.box('gold', 0.08, 0.08, 1.35, sx, FRONT - 0.55, 0.72)
        b.part('lanterns_glow').sphere('lantern_glow', 0.10, sx, FRONT - 0.55, 1.45, seg=6, rings=4)

    # 階前小金欄
    for sx in (-0.85, 0.85):
        trim.box('gold', 0.06, 0.06, 0.45, sx, FRONT - 0.25, 0.28)

    # label：既有 (0, roofY+1.05, 0.1)
    # Want glTF (0, roof_y+1.05, 0.1) → Blender (0, -0.1, roof_y+1.05)
    label_blender = (0.0, -0.10, roof_y + 1.05)

    return b.finish(
        out_path,
        door_local=[0.0, 0.0, HY],
        label_anchor=label_blender,
        extras={'building_id': 'casino'},
    )


if __name__ == '__main__':
    build(argv_out('/workspace/glb-proto/casino.glb'))
