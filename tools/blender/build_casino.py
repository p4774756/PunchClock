"""賭場（casino）v3：深色石材多層、貼圖立面、維加斯暖色霓虹（#193）。

先產生貼圖：python3 tools/blender/gen_casino_textures.py

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
from kit import Builder, clear_scene, prepare_scene, argv_out, set_palette_overrides, tex_path

# 佔地對齊 island-layout.js
W, D = 4.2, 3.4
H = 3.35
T = 0.16
BASE = 0.08


def _semicircle_boxes(part, mat, cx, y, z0, radius, nseg, sx, sy, sz, rx=0.0, ry=0.0):
    for i in range(nseg):
        a0 = math.pi * i / nseg
        a1 = math.pi * (i + 1) / nseg
        mx = cx + radius * math.cos((a0 + a1) / 2)
        mz = z0 + radius * math.sin((a0 + a1) / 2)
        part.box(mat, sx, sy, sz, mx, y, mz, rx=rx, ry=ry)


def _marquee_tower(b, north_x, z_base, height, width):
    """獨立高大 marquee 柱：燈泡框 + sign_face（神之島賭場）。"""
    fr = b.part('marquee_tower')
    face = b.part('sign_face')
    bulbs = b.part('marquee_lamps')
    neo = b.part('marquee_neon')
    pitch = -0.12
    x_back = north_x + 0.03
    x_face = north_x + 0.22
    x_bulb = north_x + 0.26
    fr.box('cas_dark', 0.18, width + 0.12, height + 0.14, x_back, 0, z_base + height / 2)
    fr.box('gold', 0.26, width + 0.22, 0.07, x_back - 0.02, 0, z_base + height + 0.02, rx=pitch)
    fr.box('gold', 0.26, width + 0.22, 0.07, x_back - 0.02, 0, z_base - 0.02, rx=pitch)
    for dy in (-width / 2 - 0.06, width / 2 + 0.06):
        fr.box('gold', 0.26, 0.07, height + 0.10, x_back - 0.02, dy, z_base + height / 2, rx=pitch)
    for i in range(14):
        t = i / 13
        ly = -width / 2 - 0.04 + t * (width + 0.08)
        bulbs.sphere('lantern_glow', 0.046, x_bulb, ly, z_base + height + 0.05, seg=5, rings=4)
        bulbs.sphere('lantern_glow', 0.046, x_bulb, ly, z_base - 0.03, seg=5, rings=4)
    for j in range(8):
        lz = z_base + 0.12 + j * (height - 0.20) / 7
        bulbs.sphere('lantern_glow', 0.042, x_bulb, -width / 2 - 0.05, lz, seg=5, rings=4)
        bulbs.sphere('lantern_glow', 0.042, x_bulb, width / 2 + 0.05, lz, seg=5, rings=4)
    sign_h, sign_w = 1.22, 2.42
    face.plane('sign', sign_w, sign_h, x_face, 0, z_base + height * 0.52, rz=math.pi / 2, rx=pitch, flip_u=False)
    fr.box('cas_dark', 0.08, 0.55, 0.55, x_face + 0.04, -sign_w / 2 - 0.38, z_base + height * 0.78, rx=pitch)
    neo.box('neon_glow', 0.040, 0.38, 0.38, x_bulb, -sign_w / 2 - 0.38, z_base + height * 0.78, rx=pitch)


def _textured_poker(b, y, north_x, z_center, tex_name, flip=False):
    cards = b.part('poker_cards')
    neo = b.part('card_neon')
    x_board, x_face = north_x + 0.12, north_x + 0.155
    cw, ch = 0.64, 0.90
    cards.box('cas_dark', 0.038, cw + 0.06, ch + 0.06, x_board, y, z_center)
    cards.textured_plane(
        f'casino/{tex_name}', cw, ch, x_face, y, z_center, rz=math.pi / 2
    )
    et = 0.012
    x_n = x_face + 0.018
    neo.box('neon_glow', et, cw + 0.02, 0.010, x_n, y, z_center + ch / 2)
    neo.box('neon_glow', et, cw + 0.02, 0.010, x_n, y, z_center - ch / 2)


def _seven_box(b, north_x, z_center, oy):
    box = b.part('seven_sign')
    neo = b.part('seven_neon')
    x_back, x_face = north_x + 0.12, north_x + 0.155
    bw, bh = 1.95, 0.82
    box.box('gold', 0.075, bw + 0.10, bh + 0.10, x_back, oy, z_center)
    box.textured_plane('casino/cas_777.png', bw, bh, x_face, oy, z_center, rz=math.pi / 2)
    neo.box('neon_glow', 0.035, bw + 0.06, 0.035, x_face + 0.02, oy, z_center + bh / 2 + 0.02)
    neo.box('neon_glow', 0.035, bw + 0.06, 0.035, x_face + 0.02, oy, z_center - bh / 2 - 0.02)


def _glow_dice(b, north_x, z_center, oy):
    d = b.part('dice')
    neo = b.part('dice_neon')
    x_back, x_face = north_x + 0.12, north_x + 0.155
    s = 0.52
    d.box('cas_dark', 0.055, s + 0.06, s + 0.06, x_back, oy, z_center)
    d.textured_plane('casino/cas_dice.png', s, s, x_face, oy, z_center, rz=math.pi / 2)
    neo.box('neon_glow', 0.020, s + 0.04, 0.020, x_face + 0.015, oy, z_center)


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()

    set_palette_overrides({
        'neon_glow': (0xe8a838, 0xffb84a),
        'windows_glow': (0xf6e3bd, 0xffc56a),
        'lantern_glow': (0xf0c878, 0xffdd88),
    })

    b = Builder('casino', W, D)
    FRONT, BACK = b.front, b.hy
    HX, HY = b.hx, b.hy
    wall_z = BASE + H / 2
    door_w = 1.05
    door_top = BASE + 2.05
    north_x = HX + 0.02

    # 門前紅毯與石坪（本地 +Z 進門）
    p = b.part('plaza')
    p.box('paving', 3.05, 2.55, 0.05, 0, FRONT - 1.18, 0.02)
    for i, h in enumerate((0.22, 0.15, 0.08)):
        p.box('cas_carpet', 1.25 - i * 0.04, 0.58, h, 0, FRONT - 0.42 - i * 0.48, h / 2 + 0.01)
    p.box('cas_carpet', 1.15, 0.38, 0.035, 0, FRONT - 0.14, 0.195)
    _semicircle_boxes(p, 'cas_carpet', 0, FRONT - 0.06, BASE + 0.02, 0.58, 9, 0.14, 0.12, 0.025)

    p = b.part('base')
    p.box('gold', W + 0.42, D + 0.42, 0.14, 0, 0, 0.07)
    p.box('cas_dark', W - 0.22, D - 0.22, 0.09, 0, 0, 0.13)

    # 多層主體（中層退縮、上層再退）
    walls = b.part('walls')
    walls.box('cas_wall', W, T, H, 0, BACK - T / 2, wall_z)
    walls.box('cas_wall', T, D, H, -HX + T / 2, 0, wall_z)
    walls.box('cas_wall', T, D, H, HX - T / 2, 0, wall_z)
    side = (W - door_w) / 2
    walls.box('cas_wall', side, T, H, -HX + side / 2, FRONT + T / 2, wall_z)
    walls.box('cas_wall', side, T, H, HX - side / 2, FRONT + T / 2, wall_z)
    tier_specs = (
        (BASE + 0.72, W - 0.48, 0.88, 0.11),
        (BASE + 1.58, W - 0.62, 0.78, 0.13),
        (BASE + H - 0.48, W - 0.38, 0.52, 0.10),
    )
    stone = b.part('stone_facade')
    stone.textured_plane('casino/cas_stone.png', W - 0.35, H - 0.18, north_x + 0.165, 0, wall_z, rz=math.pi / 2)
    for dz, ww, hh, dep in tier_specs:
        walls.box('cas_dark', dep, ww, hh, north_x + 0.04, 0, dz)
        stone.textured_plane(
            'casino/cas_stone.png', ww - 0.06, hh - 0.05, north_x + dep + 0.04, 0, dz, rz=math.pi / 2
        )

    # 入口拱門「神之島」
    arch = b.part('entry_arch')
    arch_r = door_w / 2 + 0.11
    _semicircle_boxes(walls, 'cas_wall', 0, FRONT + T / 2, door_top, arch_r, 14, 0.22, T + 0.03, 0.14)
    walls.box('gold', door_w + 0.18, T + 0.04, 0.11, 0, FRONT, door_top + arch_r - 0.02)
    walls.box('cas_carpet', door_w + 0.22, 0.08, 0.07, 0, FRONT + 0.04, door_top - 0.14)
    arch.textured_plane(
        'casino/cas_arch_gate.png', door_w + 0.35, 0.72, 0, FRONT - 0.02, door_top + 0.28
    )
    for sx in (-door_w / 2 - 0.22, door_w / 2 + 0.22):
        arch.box('gold', 0.08, 0.10, 2.12, sx, FRONT - 0.06, BASE + 1.18)
        b.part('lanterns_glow').sphere('lantern_glow', 0.10, sx, FRONT - 0.08, BASE + 2.18, seg=6, rings=4)

    sign_z = BASE + 2.05
    _marquee_tower(b, north_x, BASE + 0.14, 3.62, 2.35)

    card_y = 1.62
    _textured_poker(b, -card_y, north_x, BASE + 1.55, 'cas_poker_ah.png')
    _textured_poker(b, card_y, north_x, BASE + 1.55, 'cas_poker_as.png')
    _seven_box(b, north_x, BASE + 0.92, 0)
    _glow_dice(b, north_x, BASE + 2.62, -1.12)
    _glow_dice(b, north_x, BASE + 2.62, 1.12)

    # 門與窗
    door = b.part('door')
    door.box('cas_dark', door_w, 0.09, 2.02, 0, FRONT + 0.02, BASE + 1.01)
    door.box('gold', door_w + 0.10, 0.10, 0.07, 0, FRONT, door_top + 0.02)
    g = b.part('windows_glow')
    g.box('windows_glow', 0.36, 0.032, 0.42, -0.20, FRONT - 0.03, BASE + 1.28)
    g.box('windows_glow', 0.36, 0.032, 0.42, 0.20, FRONT - 0.03, BASE + 1.28)
    win_z = BASE + 1.58
    for sx in (-1.18, 1.18):
        b.framed_window(sx, win_z, 0.58, 0.68, BACK - 0.02, normal='+y', frame='gold')
    b.framed_window(0, win_z + 0.06, 0.88, 0.82, FRONT - 0.04, normal='-y', frame='gold')

    roof_y = BASE + H + 0.12
    rf = b.part('roof')
    rf.box('cas_roof', W + 0.52, D + 0.52, 0.28, 0, 0, roof_y)
    rf.box('cas_roof', W - 0.42, D - 0.42, 0.20, 0, 0, roof_y + 0.30)
    rf.box('cas_roof', W - 0.95, D - 0.95, 0.16, 0, 0, roof_y + 0.50)
    rf.box('gold', W + 0.62, 0.11, 0.07, 0, -HY - 0.04, roof_y + 0.22)
    rf.box('gold', W + 0.62, 0.11, 0.07, 0, HY + 0.04, roof_y + 0.22)
    rf.box('gold', 0.11, D + 0.62, 0.07, -HX - 0.04, 0, roof_y + 0.22)
    rf.box('gold', 0.11, D + 0.62, 0.07, HX + 0.04, 0, roof_y + 0.22)
    # 屋頂北緣撲克／777 剪影（俯視可辨）
    roof_deco = b.part('roof_deco')
    for sx, sz in ((-0.95, 0.72), (0.95, 0.72)):
        roof_deco.textured_plane('casino/cas_poker_ah.png', 0.42, 0.58, sx, sz, roof_y + 0.58, rx=-math.pi / 2)
    roof_deco.textured_plane('casino/cas_777.png', 0.72, 0.38, 0, -1.02, roof_y + 0.56, rx=-math.pi / 2)

    wheel = b.part('roulette')
    wheel_r = 0.86
    wheel.cyl('cas_dark', wheel_r, wheel_r, 0.15, 0, 0, roof_y + 0.40, seg=16)
    wheel.cyl('gold', 0.22, 0.16, 0.07, 0, 0, roof_y + 0.55, seg=10)
    wheel.textured_plane('casino/cas_roulette.png', 1.68, 1.68, 0, 0, roof_y + 0.48, rx=-math.pi / 2)

    lp = b.part('lamp_posts')
    for sx in (-0.78, 0.78):
        lp.box('gold', 0.09, 0.09, 1.58, sx, FRONT - 0.72, 0.84)
        b.part('lanterns_glow').sphere('lantern_glow', 0.11, sx, FRONT - 0.72, 1.70, seg=6, rings=4)

    # 椰樹只在兩側，不擋門
    palms = b.part('palms')
    for sx in (-HX - 0.22, HX + 0.22):
        y = FRONT + 0.05
        palms.cyl('pot', 0.16, 0.12, 0.20, sx, y, 0.11, seg=7)
        palms.box('shop_wood', 0.24, 0.24, 0.04, sx, y, 0.034)
        palms.cyl('shop_wood', 0.045, 0.058, 0.82, sx, y, 0.58, seg=6)
        rxp, h, n = 1.12, 0.58, 5
        for i in range(n):
            a = (i / n) * math.pi * 2
            ox = math.sin(a) * math.sin(rxp) * (h / 2)
            oy = -math.cos(a) * math.sin(rxp) * (h / 2)
            oz = math.cos(rxp) * (h / 2)
            palms.cyl('palm_leaf', 0.10, 0.012, h, sx + ox, y + oy, 0.98 + oz, seg=5, rx=rxp, rz=a)

    # 門側少量霓虹（勿沿屋簷四面纏管）
    neo = b.part('neon')
    neo.box('neon_glow', door_w + 0.28, 0.038, 0.038, 0, FRONT - 0.06, door_top - 0.04)

    label_blender = (north_x + 0.22, 0.0, sign_z)

    return b.finish(
        out_path,
        door_local=[0.0, 0.0, HY],
        label_anchor=label_blender,
        extras={'building_id': 'casino'},
    )


if __name__ == '__main__':
    tex_dir = tex_path('casino')
    if not os.path.isdir(tex_dir):
        raise SystemExit(
            '缺貼圖目錄 ' + tex_dir + '；請先執行 python3 tools/blender/gen_casino_textures.py'
        )
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'casino.glb')
    build(argv_out(_default))