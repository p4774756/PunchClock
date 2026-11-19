"""賭場（casino）：v2 放大、北立面朝向 EXPLORE 鏡頭（#163）。

無頭重建：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_casino.py -- \\
    server/src/main/resources/public/models/casino.glb
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

# 佔地對齊 island-layout.js（約 1.75× #154）
W, D = 4.2, 3.4
H = 3.12
T = 0.16
BASE = 0.08


def _semicircle_boxes(part, mat, cx, y, z0, radius, nseg, sx, sy, sz, rx=0.0, ry=0.0):
    for i in range(nseg):
        a0 = math.pi * i / nseg
        a1 = math.pi * (i + 1) / nseg
        mx = cx + radius * math.cos((a0 + a1) / 2)
        mz = z0 + radius * math.sin((a0 + a1) / 2)
        part.box(mat, sx, sy, sz, mx, y, mz, rx=rx, ry=ry)


def _back_marquee(b, neo, arch, bulbs, back_y, z_mid, width, height):
    """北側（BACK）馬戲篷霓虹拱，俯視鏡頭可辨識。"""
    rx = 2.12
    nx, ny, nz = 0.0, -math.cos(rx), math.sin(rx)

    def on_back(lx, lz, dist=0.05):
        return lx + nx * dist, back_y + ny * dist, z_mid + lz + nz * dist

    for lx, lz, sx, sy, sz in (
        (0.0, -0.52, width, 0.07, 0.10),
        (-width * 0.5, 0.0, 0.10, 0.07, height * 0.82),
        (width * 0.5, 0.0, 0.10, 0.07, height * 0.82),
        (0.0, 0.30, width * 0.92, 0.06, 0.08),
    ):
        x, y, z = on_back(lx, lz, 0.02)
        arch.box('gold', sx, sy, sz, x, y, z, rx=rx)
    radius, nseg = width * 0.48, 12
    prev = None
    for i in range(nseg + 1):
        ang = math.pi * i / nseg
        lx = -radius * math.cos(ang)
        lz = 0.10 + radius * math.sin(ang) * 0.55
        x, y, z = on_back(lx, lz, 0.03)
        if prev is not None:
            mx, my, mz = (x + prev[0]) / 2, (y + prev[1]) / 2, (z + prev[2]) / 2
            arch.box('gold', 0.24, 0.07, 0.09, mx, my, mz, rx=rx)
        prev = (x, y, z)
        bulbs.sphere('lantern_glow', 0.050, *on_back(lx, lz, 0.09), seg=5, rings=4)
    for i in range(9):
        lx = -width * 0.42 + i * (width * 0.84 / 8)
        bulbs.sphere('lantern_glow', 0.042, *on_back(lx, -0.50, 0.09), seg=5, rings=4)
    x, y, z = on_back(0.0, -0.38, 0.05)
    neo.box('neon_glow', width * 0.94, 0.038, 0.035, x, y, z, rx=rx)


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()

    set_palette_overrides({
        'neon_glow': (0xe050b8, 0xff6ad8),
        'windows_glow': (0xf6e3bd, 0xffb15a),
        'lantern_glow': (0xf0a848, 0xffc56a),
    })

    b = Builder('casino', W, D)
    FRONT, BACK = b.front, b.hy
    HX, HY = b.hx, b.hy
    wall_z = BASE + H / 2
    door_w = 1.05
    door_top = BASE + 2.05

    # 門前紅毯與石坪（西側進門，維持本地 +Z）
    p = b.part('plaza')
    p.box('paving', 3.05, 2.55, 0.05, 0, FRONT - 1.18, 0.02)
    p.box('shop_trim', 2.75, 2.20, 0.035, 0, FRONT - 1.18, 0.045)
    for i, h in enumerate((0.22, 0.15, 0.08)):
        p.box('cas_carpet', 1.25 - i * 0.04, 0.58, h, 0, FRONT - 0.42 - i * 0.48, h / 2 + 0.01)
    p.box('cas_carpet', 1.15, 0.38, 0.035, 0, FRONT - 0.14, 0.195)
    _semicircle_boxes(p, 'cas_carpet', 0, FRONT - 0.06, BASE + 0.02, 0.58, 9, 0.14, 0.12, 0.025)

    p = b.part('base')
    p.box('gold', W + 0.48, D + 0.48, 0.14, 0, 0, 0.07)
    p.box('cas_dark', W - 0.24, D - 0.24, 0.09, 0, 0, 0.13)

    # 主體牆面
    p = b.part('walls')
    p.box('cas_wall', W, T, H, 0, BACK - T / 2, wall_z)
    p.box('cas_wall', T, D, H, -HX + T / 2, 0, wall_z)
    p.box('cas_wall', T, D, H, HX - T / 2, 0, wall_z)
    side = (W - door_w) / 2
    p.box('cas_wall', side, T, H, -HX + side / 2, FRONT + T / 2, wall_z)
    p.box('cas_wall', side, T, H, HX - side / 2, FRONT + T / 2, wall_z)
    p.box('cas_purple', door_w + 0.50, 0.13, H - 0.10, 0, FRONT - 0.06, wall_z)
    for sx in (-HX + 0.32, HX - 0.32):
        p.box('cas_dark', 0.36, 0.14, H - 0.28, sx, FRONT - 0.05, wall_z + 0.08)
        p.box('gold', 0.08, 0.16, H - 0.22, sx, FRONT - 0.12, wall_z)
    for zband, hh in ((BASE + 0.78, 0.08), (BASE + H - 0.52, 0.09), (BASE + H - 0.18, 0.06)):
        p.box('gold', W + 0.04, 0.07, hh, 0, FRONT - 0.03, zband)
        p.box('gold', W + 0.04, 0.07, hh, 0, BACK + 0.03, zband)
    arch_r = door_w / 2 + 0.04
    _semicircle_boxes(p, 'cas_wall', 0, FRONT + T / 2, door_top, arch_r, 12, 0.20, T + 0.02, 0.12)
    p.box('gold', door_w + 0.14, T + 0.03, 0.08, 0, FRONT, door_top + arch_r - 0.02)

    # 北側 Vegas 展示立面（朝向 EXPLORE 鏡頭）
    vegas = b.part('vegas_facade')
    facade_z = BASE + 1.72
    vegas.box('cas_purple', W - 0.35, 0.15, 2.55, 0, BACK + 0.06, facade_z)
    vegas.box('cas_dark', W - 0.55, 0.10, 2.35, 0, BACK + 0.04, facade_z - 0.05)
    for sx in (-1.35, 1.35):
        vegas.box('gold', 0.10, 0.16, 2.20, sx, BACK + 0.02, facade_z)

    neo = b.part('neon')
    neo.box('neon_glow', W - 0.42, 0.038, 2.62, 0, BACK + 0.10, facade_z)
    neo.box('neon_glow', W - 0.20, 0.030, 0.45, 0, BACK + 0.08, BASE + 2.65)

    arch = b.part('marquee')
    bulbs = b.part('marquee_lamps')
    _back_marquee(b, neo, arch, bulbs, BACK - 0.04, facade_z + 0.95, 2.65, 1.05)

    seven = b.part('seven_sign')
    for ox in (-0.24, 0.0, 0.24):
        seven.box('neon_glow', 0.12, 0.62, 0.038, ox, BACK - 0.06, BASE + 2.35)

    cards = b.part('poker_cards')
    for sx, rz in ((-1.58, 0.30), (1.58, -0.30)):
        cards.box('cas_dark', 0.62, 0.028, 0.88, sx, BACK - 0.05, BASE + 1.62, rz=rz)
        cards.box('neon_glow', 0.66, 0.032, 0.92, sx, BACK - 0.07, BASE + 1.62, rz=rz)
        cards.box('gold', 0.08, 0.08, 0.08, sx, BACK - 0.09, BASE + 1.25, rz=rz)

    # 門側霓虹（進門面）
    neo.box('neon_glow', door_w + 0.32, 0.035, 0.035, 0, FRONT - 0.06, door_top - 0.05)
    _semicircle_boxes(neo, 'neon_glow', 0, FRONT - 0.07, door_top, arch_r + 0.07, 13, 0.20, 0.035, 0.035)
    neo.box('neon_glow', 0.035, 0.035, 1.95, -door_w / 2 - 0.09, FRONT - 0.06, BASE + 1.05)
    neo.box('neon_glow', 0.035, 0.035, 1.95, door_w / 2 + 0.09, FRONT - 0.06, BASE + 1.05)
    eave_z = BASE + H + 0.12
    neo.box('neon_glow', W + 0.75, 0.045, 0.045, 0, FRONT - 0.35, eave_z)
    neo.box('neon_glow', W + 0.75, 0.045, 0.045, 0, BACK + 0.35, eave_z)
    neo.box('neon_glow', 0.045, D + 0.65, 0.045, -HX - 0.35, 0, eave_z)
    neo.box('neon_glow', 0.045, D + 0.65, 0.045, HX + 0.35, 0, eave_z)

    door = b.part('door')
    door.box('cas_dark', door_w, 0.09, 2.02, 0, FRONT + 0.02, BASE + 1.01)
    door.box('gold', door_w + 0.12, 0.11, 0.07, 0, FRONT, door_top + 0.02)
    door.box('gold', 0.06, 0.11, 2.02, -door_w / 2, FRONT, BASE + 1.01)
    door.box('gold', 0.06, 0.11, 2.02, door_w / 2, FRONT, BASE + 1.01)
    door.box('cas_gold_light', 0.045, 0.045, 1.96, 0, FRONT - 0.01, BASE + 1.01)
    g = b.part('windows_glow')
    g.box('windows_glow', 0.38, 0.035, 0.45, -0.22, FRONT - 0.03, BASE + 1.01 + 0.28)
    g.box('windows_glow', 0.38, 0.035, 0.45, 0.22, FRONT - 0.03, BASE + 1.01 + 0.28)
    door.box('gold', 0.06, 0.06, 0.09, 0.28, FRONT - 0.05, BASE + 0.94)
    p.box('gold', 1.15, 0.68, 0.09, 0, FRONT - 0.48, BASE + 0.02)

    win_z = BASE + 1.62
    b.framed_window(HX, win_z, 0.78, 0.82, -0.55, normal='+x', frame='gold')
    b.framed_window(HX, win_z, 0.78, 0.82, 0.62, normal='+x', frame='gold')
    b.framed_window(-HX, win_z, 0.78, 0.78, 0.08, normal='-x', frame='gold')
    b.framed_window(0, win_z + 0.12, W - 0.65, 1.05, BACK - 0.02, normal='+y', frame='gold')
    b.framed_window(0, win_z + 0.08, 0.95, 0.88, FRONT - 0.04, normal='-y', frame='gold')

    roof_y = BASE + H + 0.14
    rf = b.part('roof')
    rf.box('cas_purple', W + 0.28, D + 0.28, 0.10, 0, 0, roof_y - 0.16)
    rf.box('cas_roof', W + 0.68, D + 0.68, 0.34, 0, 0, roof_y)
    ow, od, et, eh = W + 0.82, D + 0.82, 0.12, 0.08
    rf.box('gold', ow, et, eh, 0, -(od - et) / 2, roof_y + 0.18)
    rf.box('gold', ow, et, eh, 0, (od - et) / 2, roof_y + 0.18)
    rf.box('gold', et, od, eh, -(ow - et) / 2, 0, roof_y + 0.18)
    rf.box('gold', et, od, eh, (ow - et) / 2, 0, roof_y + 0.18)
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        rf.box('gold', 0.14, 0.14, 0.12, sx * (HX + 0.38), sy * (HY + 0.38), roof_y + 0.26)
        rf.cyl('gold', 0.035, 0.0, 0.14, sx * (HX + 0.38), sy * (HY + 0.38), roof_y + 0.38, seg=5)

    sp = b.part('dome')
    sp.cyl('gold', 0.58, 0.58, 0.08, 0, 0, roof_y + 0.28, seg=10)
    wheel = b.part('roulette')
    wheel.cyl('cas_dark', 0.52, 0.52, 0.10, 0, 0, roof_y + 0.48, seg=12)
    wheel.cyl('cas_wall', 0.42, 0.42, 0.06, 0, 0, roof_y + 0.56, seg=12)
    wheel.cyl('gold', 0.15, 0.12, 0.05, 0, 0, roof_y + 0.62, seg=8)
    for i in range(10):
        ang = (i / 10) * math.pi * 2
        col = 'neon_glow' if i % 2 == 0 else 'cas_dark'
        wheel.box(col, 0.10, 0.050, 0.26, math.cos(ang) * 0.40, math.sin(ang) * 0.40,
                  roof_y + 0.60, rz=ang)
    sp.cyl('gold', 0.12, 0.08, 0.20, 0, 0, roof_y + 0.92, seg=6)
    sp.cyl('gold', 0.035, 0.035, 0.34, 0, 0, roof_y + 1.12, seg=5)
    b.part('lanterns_glow').sphere('lantern_glow', 0.095, 0, 0, roof_y + 1.24, seg=6, rings=4)

    lp_roof = b.part('roof_lamps')
    glow = b.part('lanterns_glow')
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        x, y = sx * (HX + 0.24), sy * (HY + 0.24)
        lp_roof.box('gold', 0.06, 0.06, 0.72, x, y, roof_y + 0.54)
        lp_roof.box('gold', 0.12, 0.12, 0.05, x, y, roof_y + 0.92)
        glow.sphere('lantern_glow', 0.082, x, y, roof_y + 1.02, seg=6, rings=4)

    # 招牌：北側女兒牆朝鏡頭（sign_face / casinoSignMap）
    sign_y = BACK - 0.07
    sign_z = roof_y + 0.22
    b.tilted_sign(3.18, 0.95, sign_y, sign_z, rx=2.12, facing='back', frame='gold', board='cas_dark')
    b.roof_readable_sign(1.95, 0.72, 0.0, BACK - 0.32, roof_y + 0.18, rx=math.pi / 2 - 0.16,
                         ry=0.08, frame='gold', board='cas_dark')

    lp = b.part('lamp_posts')
    for sx in (-0.82, 0.82):
        lp.box('gold', 0.10, 0.10, 1.62, sx, FRONT - 0.78, 0.86)
        b.part('lanterns_glow').sphere('lantern_glow', 0.12, sx, FRONT - 0.78, 1.72, seg=6, rings=4)

    trim = b.part('gold_trim')
    for sx in (-1.25, 1.25):
        trim.box('gold', 0.08, 0.08, 0.55, sx, FRONT - 0.38, 0.32)

    palms = b.part('palms')
    for sx in (-1.45, 1.45):
        y = FRONT - 1.55
        palms.cyl('pot', 0.18, 0.14, 0.22, sx, y, 0.12, seg=7)
        palms.box('shop_wood', 0.28, 0.28, 0.05, sx, y, 0.035)
        palms.cyl('shop_wood', 0.05, 0.065, 0.88, sx, y, 0.62, seg=6)
        rxp, h, n = 1.15, 0.62, 6
        for i in range(n):
            a = (i / n) * math.pi * 2
            ox = math.sin(a) * math.sin(rxp) * (h / 2)
            oy = -math.cos(a) * math.sin(rxp) * (h / 2)
            oz = math.cos(rxp) * (h / 2)
            palms.cyl('palm_leaf', 0.11, 0.014, h, sx + ox, y + oy, 1.02 + oz,
                      seg=5, rx=rxp, rz=a)

    label_blender = (0.0, sign_y - 0.14, sign_z + 0.30)

    return b.finish(
        out_path,
        door_local=[0.0, 0.0, HY],
        label_anchor=label_blender,
        extras={'building_id': 'casino'},
    )


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'casino.glb')
    build(argv_out(_default))