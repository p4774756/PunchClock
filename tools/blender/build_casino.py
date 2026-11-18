"""賭場（casino）：w=2.4 d=1.9，門朝本地 +Z。拉斯維加斯霓虹本體（#154）。

無頭重建：
  blender -b --factory-startup -P tools/blender/build_casino.py -- \\
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

W, D = 2.4, 1.9
H = 2.15
T = 0.14
BASE = 0.08


def _semicircle_boxes(part, mat, cx, y, z0, radius, nseg, sx, sy, sz, rx=0.0, ry=0.0):
    """在 XZ 平面畫半圓拱（開口朝下），沿 Y 厚度。"""
    for i in range(nseg):
        a0 = math.pi * i / nseg
        a1 = math.pi * (i + 1) / nseg
        mx = cx + radius * math.cos((a0 + a1) / 2)
        mz = z0 + radius * math.sin((a0 + a1) / 2)
        part.box(mat, sx, sy, sz, mx, y, mz, rx=rx, ry=ry)


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
    door_w = 0.72
    door_top = BASE + 1.44

    # 門前紅毯與石坪
    p = b.part('plaza')
    p.box('paving', 2.15, 1.70, 0.04, 0, FRONT - 0.85, 0.02)
    p.box('shop_trim', 1.90, 1.45, 0.03, 0, FRONT - 0.85, 0.04)
    for i, h in enumerate((0.16, 0.11, 0.06)):
        p.box('cas_carpet', 0.86 - i * 0.02, 0.42, h, 0, FRONT - 0.28 - i * 0.38, h / 2 + 0.01)
    p.box('cas_carpet', 0.80, 0.28, 0.03, 0, FRONT - 0.10, 0.155)

    # 台基
    p = b.part('base')
    p.box('gold', W + 0.40, D + 0.40, 0.12, 0, 0, 0.06)
    p.box('cas_dark', W - 0.20, D - 0.20, 0.08, 0, 0, 0.12)

    # 立面：中央凸起的門廊 bay + 側柱
    p = b.part('walls')
    p.box('cas_wall', W, T, H, 0, BACK - T / 2, wall_z)
    p.box('cas_wall', T, D, H, -HX + T / 2, 0, wall_z)
    p.box('cas_wall', T, D, H, HX - T / 2, 0, wall_z)
    side = (W - door_w) / 2
    p.box('cas_wall', side, T, H, -HX + side / 2, FRONT + T / 2, wall_z)
    p.box('cas_wall', side, T, H, HX - side / 2, FRONT + T / 2, wall_z)
    # 門廊凸起立面
    p.box('cas_purple', door_w + 0.38, 0.11, H - 0.08, 0, FRONT - 0.05, wall_z)
    for sx in (-HX + 0.20, HX - 0.20):
        p.box('cas_dark', 0.24, 0.12, H - 0.22, sx, FRONT - 0.04, wall_z + 0.06)
        p.box('gold', 0.06, 0.14, H - 0.18, sx, FRONT - 0.10, wall_z)
    # 腰線與簷口線腳
    for zband, hh in ((BASE + 0.55, 0.06), (BASE + H - 0.38, 0.07), (BASE + H - 0.14, 0.05)):
        p.box('gold', W + 0.02, 0.06, hh, 0, FRONT - 0.02, zband)
    # 拱形門洞上缘：半圓拱石 + 中央楣梁
    arch_r = door_w / 2 + 0.02
    _semicircle_boxes(p, 'cas_wall', 0, FRONT + T / 2, door_top, arch_r, 11, 0.17, T + 0.02, 0.11)
    p.box('gold', door_w + 0.12, T + 0.03, 0.07, 0, FRONT, door_top + arch_r - 0.02)

    # 霓虹燈管
    neo = b.part('neon')
    neo.box('neon_glow', door_w + 0.26, 0.03, 0.03, 0, FRONT - 0.05, door_top - 0.04)
    _semicircle_boxes(neo, 'neon_glow', 0, FRONT - 0.06, door_top, arch_r + 0.06, 13, 0.16, 0.03, 0.03)
    neo.box('neon_glow', 0.03, 0.03, 1.38, -door_w / 2 - 0.07, FRONT - 0.05, BASE + 0.74)
    neo.box('neon_glow', 0.03, 0.03, 1.38, door_w / 2 + 0.07, FRONT - 0.05, BASE + 0.74)
    eave_z = BASE + H + 0.10
    neo.box('neon_glow', W + 0.62, 0.04, 0.04, 0, FRONT - 0.28, eave_z)
    neo.box('neon_glow', W + 0.62, 0.04, 0.04, 0, BACK + 0.28, eave_z)
    neo.box('neon_glow', 0.04, D + 0.56, 0.04, -HX - 0.28, 0, eave_z)
    neo.box('neon_glow', 0.04, D + 0.56, 0.04, HX + 0.28, 0, eave_z)
    neo.box('neon_glow', W - 0.35, 0.03, 0.03, 0, FRONT - 0.12, BASE + 1.12)

    # 拱形大門
    door = b.part('door')
    door.box('cas_dark', door_w, 0.08, 1.44, 0, FRONT + 0.02, BASE + 0.72)
    door.box('gold', door_w + 0.10, 0.10, 0.06, 0, FRONT, door_top + 0.02)
    door.box('gold', 0.05, 0.10, 1.44, -door_w / 2, FRONT, BASE + 0.72)
    door.box('gold', 0.05, 0.10, 1.44, door_w / 2, FRONT, BASE + 0.72)
    door.box('cas_gold_light', 0.04, 0.04, 1.40, 0, FRONT - 0.01, BASE + 0.72)
    g = b.part('windows_glow')
    g.box('windows_glow', 0.28, 0.03, 0.34, -0.16, FRONT - 0.03, BASE + 0.72 + 0.22)
    g.box('windows_glow', 0.28, 0.03, 0.34, 0.16, FRONT - 0.03, BASE + 0.72 + 0.22)
    door.box('gold', 0.05, 0.05, 0.08, 0.22, FRONT - 0.04, BASE + 0.67)

    p.box('gold', 0.90, 0.55, 0.08, 0, FRONT - 0.35, BASE + 0.02)

    win_z = BASE + 1.15
    b.framed_window(HX, win_z, 0.55, 0.62, -0.35, normal='+x', frame='gold')
    b.framed_window(HX, win_z, 0.55, 0.62, 0.40, normal='+x', frame='gold')
    b.framed_window(-HX, win_z, 0.55, 0.58, 0.05, normal='-x', frame='gold')
    b.framed_window(0, win_z + 0.10, 0.70, 0.50, BACK, normal='+y', frame='gold')

    roof_y = BASE + H + 0.12
    rf = b.part('roof')
    rf.box('cas_purple', W + 0.20, D + 0.20, 0.08, 0, 0, roof_y - 0.14)
    rf.box('cas_roof', W + 0.55, D + 0.55, 0.28, 0, 0, roof_y)
    ow, od, et, eh = W + 0.70, D + 0.70, 0.10, 0.07
    rf.box('gold', ow, et, eh, 0, -(od - et) / 2, roof_y + 0.16)
    rf.box('gold', ow, et, eh, 0, (od - et) / 2, roof_y + 0.16)
    rf.box('gold', et, od, eh, -(ow - et) / 2, 0, roof_y + 0.16)
    rf.box('gold', et, od, eh, (ow - et) / 2, 0, roof_y + 0.16)
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        rf.box('gold', 0.12, 0.12, 0.10, sx * (HX + 0.28), sy * (HY + 0.28), roof_y + 0.22)
        rf.cyl('gold', 0.03, 0.0, 0.12, sx * (HX + 0.28), sy * (HY + 0.28), roof_y + 0.32, seg=5)

    sp = b.part('dome')
    sp.cyl('gold', 0.48, 0.48, 0.06, 0, 0, roof_y + 0.22, seg=10)
    wheel = b.part('roulette')
    wheel.cyl('cas_dark', 0.42, 0.42, 0.08, 0, 0, roof_y + 0.40, seg=12)
    wheel.cyl('cas_wall', 0.34, 0.34, 0.05, 0, 0, roof_y + 0.47, seg=12)
    wheel.cyl('gold', 0.12, 0.10, 0.04, 0, 0, roof_y + 0.52, seg=8)
    for i in range(8):
        ang = (i / 8) * math.pi * 2
        col = 'neon_glow' if i % 2 == 0 else 'cas_dark'
        wheel.box(col, 0.08, 0.045, 0.22, math.cos(ang) * 0.32, math.sin(ang) * 0.32,
                  roof_y + 0.50, rz=ang)
    sp.cyl('gold', 0.10, 0.07, 0.16, 0, 0, roof_y + 0.78, seg=6)
    sp.cyl('gold', 0.03, 0.03, 0.28, 0, 0, roof_y + 0.98, seg=5)
    b.part('lanterns_glow').sphere('lantern_glow', 0.08, 0, 0, roof_y + 1.08, seg=6, rings=4)

    lp_roof = b.part('roof_lamps')
    glow = b.part('lanterns_glow')
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        x, y = sx * (HX + 0.18), sy * (HY + 0.18)
        lp_roof.box('gold', 0.05, 0.05, 0.62, x, y, roof_y + 0.48)
        lp_roof.box('gold', 0.10, 0.10, 0.04, x, y, roof_y + 0.80)
        glow.sphere('lantern_glow', 0.07, x, y, roof_y + 0.88, seg=6, rings=4)

    rx = -1.05
    cy, cz = FRONT - 0.12, roof_y + 0.36
    b.tilted_sign(1.70, 0.50, cy, cz, rx=rx, frame='gold', board='cas_dark')

    def on_sign(lx, lz, dist=0.06):
        nx, ny, nz = 0.0, -math.cos(rx), -math.sin(rx)
        return (
            lx + nx * dist,
            cy - lz * math.sin(rx) + ny * dist,
            cz + lz * math.cos(rx) + nz * dist,
        )

    arch = b.part('marquee')
    bulbs = b.part('marquee_lamps')
    for lx, lz, sx, sy, sz in (
        (0.0, -0.38, 1.95, 0.06, 0.08),
        (-0.95, 0.02, 0.08, 0.06, 0.74),
        (0.95, 0.02, 0.08, 0.06, 0.74),
        (0.0, 0.22, 1.75, 0.05, 0.06),
    ):
        x, y, z = on_sign(lx, lz, 0.02)
        arch.box('gold', sx, sy, sz, x, y, z, rx=rx)
    radius, nseg = 0.95, 11
    prev = None
    for i in range(nseg + 1):
        ang = math.pi * i / nseg
        lx = -radius * math.cos(ang)
        lz = 0.08 + radius * math.sin(ang) * 0.55
        x, y, z = on_sign(lx, lz, 0.03)
        if prev is not None:
            mx, my, mz = (x + prev[0]) / 2, (y + prev[1]) / 2, (z + prev[2]) / 2
            arch.box('gold', 0.20, 0.06, 0.08, mx, my, mz, rx=rx)
        prev = (x, y, z)
        bulbs.sphere('lantern_glow', 0.045, *on_sign(lx, lz, 0.08), seg=5, rings=4)
    for i in range(7):
        lx = -0.90 + i * 0.30
        bulbs.sphere('lantern_glow', 0.04, *on_sign(lx, -0.38, 0.08), seg=5, rings=4)
    x, y, z = on_sign(0.0, -0.28, 0.05)
    neo.box('neon_glow', 1.85, 0.035, 0.03, x, y, z, rx=rx)

    seven = b.part('seven_sign')
    for ox in (-0.15, 0.0, 0.15):
        seven.box('neon_glow', 0.09, 0.36, 0.03, -HX - 0.18 + ox, FRONT - 0.15, BASE + 1.65)
    cards = b.part('poker_cards')
    for sx, rz in ((-0.95, 0.35), (0.95, -0.35)):
        cards.box('cas_dark', 0.38, 0.02, 0.54, sx, FRONT - 0.04, BASE + 1.32, rz=rz)
        cards.box('neon_glow', 0.42, 0.03, 0.58, sx, FRONT - 0.06, BASE + 1.32, rz=rz)

    lp = b.part('lamp_posts')
    for sx in (-0.55, 0.55):
        lp.box('gold', 0.08, 0.08, 1.35, sx, FRONT - 0.55, 0.72)
        b.part('lanterns_glow').sphere('lantern_glow', 0.10, sx, FRONT - 0.55, 1.45, seg=6, rings=4)

    trim = b.part('gold_trim')
    for sx in (-0.85, 0.85):
        trim.box('gold', 0.06, 0.06, 0.45, sx, FRONT - 0.25, 0.28)

    palms = b.part('palms')
    for sx in (-1.05, 1.05):
        y = FRONT - 1.15
        palms.cyl('pot', 0.14, 0.11, 0.18, sx, y, 0.10, seg=7)
        palms.box('shop_wood', 0.22, 0.22, 0.04, sx, y, 0.03)
        palms.cyl('shop_wood', 0.04, 0.055, 0.72, sx, y, 0.52, seg=6)
        rxp, h, n = 1.15, 0.52, 6
        for i in range(n):
            a = (i / n) * math.pi * 2
            ox = math.sin(a) * math.sin(rxp) * (h / 2)
            oy = -math.cos(a) * math.sin(rxp) * (h / 2)
            oz = math.cos(rxp) * (h / 2)
            palms.cyl('palm_leaf', 0.09, 0.012, h, sx + ox, y + oy, 0.86 + oz,
                      seg=5, rx=rxp, rz=a)

    label_blender = (0.0, -0.10, roof_y + 1.25)

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