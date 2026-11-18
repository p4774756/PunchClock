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
    # 三級紅毯台階，由門口往前遞降，階高拉開好辨識
    for i, h in enumerate((0.16, 0.11, 0.06)):
        p.box('cas_carpet', 0.86 - i * 0.02, 0.42, h, 0, FRONT - 0.28 - i * 0.38, h / 2 + 0.01)
    p.box('cas_carpet', 0.80, 0.28, 0.03, 0, FRONT - 0.10, 0.155)

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
    # 屋頂輪盤 + 金塔
    sp = b.part('dome')
    sp.cyl('gold', 0.48, 0.48, 0.06, 0, 0, roof_y + 0.22, seg=10)
    wheel = b.part('roulette')
    wheel.cyl('cas_dark', 0.40, 0.40, 0.07, 0, 0, roof_y + 0.38, seg=12)
    wheel.cyl('cas_wall', 0.32, 0.32, 0.04, 0, 0, roof_y + 0.44, seg=12)
    for i in range(8):
        ang = (i / 8) * math.pi * 2
        wheel.box('neon_glow', 0.07, 0.04, 0.20, math.cos(ang) * 0.30, math.sin(ang) * 0.30,
                  roof_y + 0.46, rz=ang)
    sp.cyl('gold', 0.10, 0.07, 0.16, 0, 0, roof_y + 0.72, seg=6)
    sp.cyl('gold', 0.03, 0.03, 0.28, 0, 0, roof_y + 0.92, seg=5)
    b.part('lanterns_glow').sphere('lantern_glow', 0.08, 0, 0, roof_y + 1.08, seg=6, rings=4)

    # 屋頂四角燈柱
    lp_roof = b.part('roof_lamps')
    glow = b.part('lanterns_glow')
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        x, y = sx * (HX + 0.18), sy * (HY + 0.18)
        lp_roof.box('gold', 0.05, 0.05, 0.62, x, y, roof_y + 0.48)
        lp_roof.box('gold', 0.10, 0.10, 0.04, x, y, roof_y + 0.80)
        glow.sphere('lantern_glow', 0.07, x, y, roof_y + 0.88, seg=6, rings=4)

    # 拱形燈泡招牌框（與招牌同一斜面）
    rx = -1.05
    cy, cz = FRONT - 0.12, roof_y + 0.36
    b.tilted_sign(1.70, 0.50, cy, cz, rx=rx, frame='gold', board='cas_dark')
    # 沿斜面座標 (lx, lz) 放燈泡與拱框：Rx 後 y'=-lz*sin(rx), z'=lz*cos(rx)
    def on_sign(lx, lz, dist=0.06):
        nx, ny, nz = 0.0, -math.cos(rx), -math.sin(rx)
        return (
            lx + nx * dist,
            cy - lz * math.sin(rx) + ny * dist,
            cz + lz * math.cos(rx) + nz * dist,
        )
    arch = b.part('marquee')
    bulbs = b.part('lanterns_glow')
    # 底橫樑與兩側柱
    for lx, lz, sx, sy, sz in (
        (0.0, -0.38, 1.95, 0.05, 0.07),
        (-0.95, 0.02, 0.07, 0.05, 0.72),
        (0.95, 0.02, 0.07, 0.05, 0.72),
    ):
        x, y, z = on_sign(lx, lz, 0.02)
        arch.box('gold', sx, sy, sz, x, y, z, rx=rx)
    # 半圓拱（約 11 段）
    radius, nseg = 0.95, 11
    prev = None
    for i in range(nseg + 1):
        ang = math.pi * i / nseg
        lx = -radius * math.cos(ang)
        lz = 0.08 + radius * math.sin(ang) * 0.55
        x, y, z = on_sign(lx, lz, 0.03)
        if prev is not None:
            mx, my, mz = (x + prev[0]) / 2, (y + prev[1]) / 2, (z + prev[2]) / 2
            arch.box('gold', 0.20, 0.05, 0.07, mx, my, mz, rx=rx)
        prev = (x, y, z)
        bulbs.sphere('lantern_glow', 0.045, *on_sign(lx, lz, 0.08), seg=5, rings=4)
    # 底邊燈泡
    for i in range(7):
        lx = -0.90 + i * 0.30
        bulbs.sphere('lantern_glow', 0.04, *on_sign(lx, -0.38, 0.08), seg=5, rings=4)
    # 拱下再一條霓虹
    x, y, z = on_sign(0.0, -0.28, 0.05)
    neo.box('neon_glow', 1.85, 0.035, 0.03, x, y, z, rx=rx)

    # 側牆 777 霓虹與門前撲克牌造型
    seven = b.part('seven_sign')
    seven.box('neon_glow', 0.09, 0.36, 0.03, -HX - 0.22, FRONT - 0.15, BASE + 1.65)
    seven.box('neon_glow', 0.09, 0.36, 0.03, -HX - 0.08, FRONT - 0.15, BASE + 1.65)
    seven.box('neon_glow', 0.09, 0.36, 0.03, HX + 0.08, FRONT - 0.15, BASE + 1.65)
    cards = b.part('poker_cards')
    for sx, rz in ((-0.95, 0.35), (0.95, -0.35)):
        cards.box('cas_dark', 0.38, 0.02, 0.54, sx, FRONT - 0.04, BASE + 1.32, rz=rz)
        cards.box('neon_glow', 0.42, 0.03, 0.58, sx, FRONT - 0.06, BASE + 1.32, rz=rz)

    # 門前金柱燈
    lp = b.part('lamp_posts')
    for sx in (-0.55, 0.55):
        lp.box('gold', 0.08, 0.08, 1.35, sx, FRONT - 0.55, 0.72)
        b.part('lanterns_glow').sphere('lantern_glow', 0.10, sx, FRONT - 0.55, 1.45, seg=6, rings=4)

    # 階前小金欄
    for sx in (-0.85, 0.85):
        trim.box('gold', 0.06, 0.06, 0.45, sx, FRONT - 0.25, 0.28)

    # 前庭兩側盆栽棕櫚（不擋紅毯走道）
    palms = b.part('palms')
    for sx in (-1.05, 1.05):
        y = FRONT - 1.15
        palms.cyl('pot', 0.14, 0.11, 0.18, sx, y, 0.10, seg=7)
        palms.box('shop_wood', 0.22, 0.22, 0.04, sx, y, 0.03)
        palms.cyl('shop_wood', 0.04, 0.055, 0.72, sx, y, 0.52, seg=6)
        # 葉：先繞 X 倒下再繞 Z 轉一圈，中心沿軸外移，看起來才像傘狀葉冠
        rx, h, n = 1.15, 0.52, 6
        for i in range(n):
            a = (i / n) * math.pi * 2
            ox = math.sin(a) * math.sin(rx) * (h / 2)
            oy = -math.cos(a) * math.sin(rx) * (h / 2)
            oz = math.cos(rx) * (h / 2)
            palms.cyl('palm_leaf', 0.09, 0.012, h, sx + ox, y + oy, 0.86 + oz,
                      seg=5, rx=rx, rz=a)

    # label：既有 (0, roofY+1.05, 0.1)
    # Want glTF (0, roof_y+1.05, 0.1) → Blender (0, -0.1, roof_y+1.05)
    label_blender = (0.0, -0.10, roof_y + 1.25)

    return b.finish(
        out_path,
        door_local=[0.0, 0.0, HY],
        label_anchor=label_blender,
        extras={'building_id': 'casino'},
    )


if __name__ == '__main__':
    build(argv_out('/workspace/glb-proto/casino.glb'))
