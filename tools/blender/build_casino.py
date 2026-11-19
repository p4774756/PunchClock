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


def _north_vertical_sign(b, width, height, z_center, north_y):
    """主招牌：垂直朝北（+Y / BACK），正對 EXPLORE 鏡頭；sign_face → casinoSignMap。"""
    fr = b.part('sign')
    face = b.part('sign_face')
    neo = b.part('neon')
    y_frame, y_board, y_face = north_y + 0.07, north_y + 0.09, north_y + 0.115
    y_neon = north_y + 0.12
    fr.box('gold', width + 0.20, 0.13, height + 0.18, 0, y_frame, z_center)
    fr.box('cas_dark', width + 0.04, 0.055, height + 0.06, 0, y_board, z_center)
    neo.box('neon_glow', width + 0.26, 0.058, height + 0.22, 0, y_neon, z_center)
    face.plane('sign', width, height, 0, y_face, z_center, ry=math.pi, flip_u=True)
    neo.box('neon_glow', width + 0.16, 0.050, 0.055, 0, y_neon, z_center + height / 2 + 0.04)
    neo.box('neon_glow', width + 0.16, 0.050, 0.055, 0, y_neon, z_center - height / 2 - 0.04)
    for dx in (-width / 2 - 0.04, width / 2 + 0.04):
        neo.box('neon_glow', 0.050, 0.050, height + 0.10, dx, y_neon, z_center)


def _north_marquee(arch, bulbs, neo, north_y, z_top, width):
    """北立面垂直霓虹拱（XZ 拱門、法線朝 +Y）。"""
    y = north_y + 0.10
    pillar_h = 0.58
    z_base = z_top - pillar_h
    for lx in (-width * 0.47, width * 0.47):
        arch.box('gold', 0.15, 0.12, pillar_h, lx, y, z_base + pillar_h / 2)
    radius, nseg = width * 0.45, 12
    prev = None
    for i in range(nseg + 1):
        ang = math.pi * i / nseg
        lx = radius * math.cos(ang)
        lz = z_top + radius * 0.50 * math.sin(ang)
        if prev is not None:
            mx, mz = (lx + prev[0]) / 2, (lz + prev[1]) / 2
            arch.box('gold', 0.24, 0.11, 0.11, mx, y, mz)
        prev = (lx, lz)
        bulbs.sphere('lantern_glow', 0.052, lx, y + 0.08, lz, seg=5, rings=4)
    neo.box('neon_glow', width * 0.94, 0.052, 0.060, 0, y + 0.10, z_base - 0.02)
    for i in range(9):
        lx = -width * 0.42 + i * (width * 0.84 / 8)
        bulbs.sphere('lantern_glow', 0.044, lx, y + 0.09, z_base + 0.05, seg=5, rings=4)


def _giant_poker_card(cards, neo, x, north_y, z_center):
    """朝北的巨型撲克牌（牆面突出）。"""
    y = north_y + 0.09
    cw, ch = 0.82, 1.12
    cards.box('windows_glow', cw - 0.06, 0.055, ch - 0.06, x, y, z_center)
    cards.box('cas_dark', cw, 0.065, ch, x, y - 0.02, z_center)
    neo.box('neon_glow', cw + 0.10, 0.075, ch + 0.10, x, y + 0.05, z_center)
    cards.box('cas_carpet', 0.16, 0.07, 0.20, x - cw * 0.30, y + 0.04, z_center + ch * 0.28)
    cards.box('gold', 0.20, 0.07, 0.30, x + cw * 0.22, y + 0.04, z_center - ch * 0.22)


def _neon_seven(seven, x, north_y, z_center):
    """單個 7 字形霓虹（朝北）。"""
    y = north_y + 0.11
    w, h, t = 0.26, 0.68, 0.065
    seven.box('neon_glow', w, t, 0.060, x, y, z_center + h / 2 - 0.04)
    seven.box('neon_glow', t, t, h, x + w * 0.36, y, z_center)


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
    arch_r = door_w / 2 + 0.11
    _semicircle_boxes(p, 'cas_wall', 0, FRONT + T / 2, door_top, arch_r, 14, 0.24, T + 0.03, 0.14)
    p.box('gold', door_w + 0.20, T + 0.04, 0.11, 0, FRONT, door_top + arch_r - 0.02)
    p.box('cas_wall', 0.18, 0.22, 2.05, -HX + 0.28, FRONT + 0.12, BASE + 1.08)

    north_y = BACK + 0.02
    sign_z = BASE + 1.78
    marquee_top = BASE + H - 0.02

    # 北側 Vegas 立面：層次退台 + 垂直招牌／拱門／777／撲克牌
    vegas = b.part('vegas_facade')
    for dz, ww, hh in ((sign_z - 0.55, W - 0.42, 1.05), (sign_z + 0.55, W - 0.58, 0.95)):
        vegas.box('cas_purple', ww, 0.12, hh, 0, north_y + 0.04, dz)
        vegas.box('cas_dark', ww - 0.18, 0.08, hh - 0.12, 0, north_y + 0.03, dz - 0.04)
    for sx in (-1.42, 1.42):
        vegas.box('gold', 0.11, 0.14, 2.35, sx, north_y + 0.03, sign_z)

    neo = b.part('neon')
    neo.box('neon_glow', W - 0.28, 0.050, 0.055, 0, north_y + 0.11, BASE + 0.55)
    neo.box('neon_glow', W - 0.18, 0.045, 0.050, 0, north_y + 0.10, marquee_top + 0.12)

    arch = b.part('marquee')
    bulbs = b.part('marquee_lamps')
    _north_marquee(arch, bulbs, neo, north_y, marquee_top, 2.85)

    seven = b.part('seven_sign')
    for ox in (-0.52, 0.0, 0.52):
        _neon_seven(seven, ox, north_y, BASE + 2.48)
    seven.box('gold', 2.35, 0.08, 0.10, 0, north_y + 0.08, BASE + 2.12)

    cards = b.part('poker_cards')
    for sx in (-1.52, 1.52):
        _giant_poker_card(cards, neo, sx, north_y, BASE + 1.62)

    _north_vertical_sign(b, 3.05, 1.02, sign_z, north_y)

    # 西北角門廊量體（真門仍在西側 FRONT）
    nw = b.part('nw_portico')
    corner_x = -HX + 0.22
    corner_y = north_y - 0.08
    nw.box('cas_wall', 0.26, 0.18, 2.08, corner_x, corner_y, BASE + 1.12)
    _semicircle_boxes(nw, 'gold', corner_x, corner_y + 0.08, BASE + 2.02, 0.42, 12, 0.16, 0.16, 0.12)
    p.box('cas_carpet', 0.62, 0.42, 0.045, -HX + 0.38, BACK - 0.18, BASE + 0.045)
    p.box('cas_carpet', 0.40, 0.28, 0.035, -HX + 0.28, FRONT + 0.52, BASE + 0.038)
    _semicircle_boxes(nw, 'cas_carpet', -HX + 0.55, BACK - 0.28, BASE + 0.03, 0.32, 8, 0.12, 0.10, 0.022)

    # 門側霓虹（進門面面＋繞到西北角）
    neo.box('neon_glow', door_w + 0.38, 0.042, 0.042, 0, FRONT - 0.06, door_top - 0.05)
    _semicircle_boxes(neo, 'neon_glow', 0, FRONT - 0.08, door_top, arch_r + 0.09, 15, 0.24, 0.042, 0.042)
    _semicircle_boxes(neo, 'neon_glow', corner_x, corner_y + 0.06, BASE + 2.05, 0.40, 11, 0.16, 0.042, 0.042)
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
    for sx in (-1.22, 1.22):
        b.framed_window(sx, win_z + 0.05, 0.62, 0.72, BACK - 0.02, normal='+y', frame='gold')
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

    # 主招牌在 _north_vertical_sign（北牆垂直）；屋頂不再放 sign_face 以免吃掉鏡頭

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

    label_blender = (0.0, BACK + 0.24, sign_z)

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