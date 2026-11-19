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
from mathutils import Matrix, Vector

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


def _horiz_tex(part, tex_subpath, w, h, x, y, z, ry=0.0, rz=0.0, flip_u=False, label='horiz'):
    """屋頂水平貼圖：rx=-π/2，法線朝 +Z（俯視可見）；旋轉順序同 kit.plane（rx→ry→rz）。"""
    rx = -math.pi / 2
    part.textured_plane(tex_subpath, w, h, x, y, z, rx=rx, ry=ry, rz=rz, flip_u=flip_u)
    rot = Matrix.Rotation(rz, 4, 'Z') @ Matrix.Rotation(ry, 4, 'Y') @ Matrix.Rotation(rx, 4, 'X')
    n = rot @ Vector((0.0, -1.0, 0.0))
    print(f'HORIZ_TEX {label}: normal≈({n.x:.3f}, {n.y:.3f}, {n.z:.3f}) expect z≈+1')


def _marquee_tower(b, north_x, z_base, height, width):
    """獨立高大 marquee 柱：燈泡框 + sign_face（神之島賭場）。"""
    fr = b.part('marquee_tower')
    face = b.part('sign_face')
    bulbs = b.part('marquee_lamps')
    neo = b.part('marquee_neon')
    pitch = -0.12
    x_back = north_x + 0.06
    x_face = north_x + 0.22
    x_bulb = north_x + 0.25
    fr.box('cas_dark', 0.22, width + 0.12, height + 0.14, x_back, 0, z_base + height / 2)
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
    face.plane(
        'sign', sign_w, sign_h, x_face, 0, z_base + height * 0.52,
        rz=math.pi / 2, rx=pitch, flip_u=False,
    )
    fr.box('cas_dark', 0.08, 0.55, 0.55, x_face + 0.04, -sign_w / 2 - 0.38, z_base + height * 0.78, rx=pitch)
    neo.box('neon_glow', 0.040, 0.38, 0.38, x_bulb, -sign_w / 2 - 0.38, z_base + height * 0.78, rx=pitch)


def _textured_poker(b, y, north_x, z_center, tex_name, flip=False):
    cards = b.part('poker_cards')
    neo = b.part('card_neon')
    lean = -0.24
    x_board, x_face = north_x + 0.05, north_x + 0.21
    cw, ch = 0.72, 1.02
    cards.box('cas_dark', 0.038, cw + 0.06, ch + 0.06, x_board, y, z_center)
    cards.textured_plane(
        f'casino/{tex_name}', cw, ch, x_face, y, z_center, rz=math.pi / 2, rx=lean
    )
    et = 0.012
    x_n = x_face + 0.018
    neo.box('neon_glow', et, cw + 0.02, 0.010, x_n, y, z_center + ch / 2)
    neo.box('neon_glow', et, cw + 0.02, 0.010, x_n, y, z_center - ch / 2)


def _seven_box(b, north_x, z_center, oy):
    box = b.part('seven_sign')
    neo = b.part('seven_neon')
    lean = -0.22
    x_back, x_face = north_x + 0.05, north_x + 0.21
    bw, bh = 2.05, 0.88
    box.box('gold', 0.075, bw + 0.10, bh + 0.10, x_back, oy, z_center)
    box.textured_plane('casino/cas_777.png', bw, bh, x_face, oy, z_center, rz=math.pi / 2, rx=lean)
    neo.box('neon_glow', 0.035, bw + 0.06, 0.035, x_face + 0.02, oy, z_center + bh / 2 + 0.02)
    neo.box('neon_glow', 0.035, bw + 0.06, 0.035, x_face + 0.02, oy, z_center - bh / 2 - 0.02)


def _glow_dice(b, north_x, z_center, oy):
    d = b.part('dice')
    neo = b.part('dice_neon')
    lean = -0.20
    x_back, x_face = north_x + 0.05, north_x + 0.21
    s = 0.58
    d.box('cas_dark', 0.055, s + 0.06, s + 0.06, x_back, oy, z_center)
    d.textured_plane('casino/cas_dice.png', s, s, x_face, oy, z_center, rz=math.pi / 2, rx=lean)
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
    span = door_w + 0.44
    arch_h = 0.92
    arch_y = FRONT - 0.12
    for sx in (-span / 2, span / 2):
        arch.box('gold', 0.11, 0.15, arch_h, sx, arch_y, door_top + arch_h / 2 - 0.08)
    arch.box('gold', span + 0.14, 0.13, 0.11, 0, arch_y, door_top + arch_h - 0.02)
    _semicircle_boxes(arch, 'gold', 0, arch_y, door_top + arch_h - 0.10, span / 2, 11, 0.11, 0.11, 0.07)
    arch.textured_plane(
        'casino/cas_arch_gate.png', span + 0.08, 0.74, 0, arch_y - 0.06, door_top + arch_h * 0.52, rx=-0.10
    )
    _horiz_tex(
        arch, 'casino/cas_arch_roof.png', span + 0.05, 0.38,
        0, arch_y - 0.04, door_top + arch_h + 0.08, label='arch_gate_roof',
    )
    for sx in (-door_w / 2 - 0.22, door_w / 2 + 0.22):
        arch.box('gold', 0.08, 0.10, 2.12, sx, FRONT - 0.06, BASE + 1.18)
        b.part('lanterns_glow').sphere('lantern_glow', 0.10, sx, FRONT - 0.08, BASE + 2.18, seg=6, rings=4)

    sign_z = BASE + 1.92
    _marquee_tower(b, north_x, BASE + 0.18, 2.55, 2.35)

    roof_y = BASE + H + 0.12
    roof_top = roof_y + 0.58

    card_y = 2.32
    _textured_poker(b, -card_y, north_x, BASE + 1.58, 'cas_poker_ah.png')
    _textured_poker(b, card_y, north_x, BASE + 1.58, 'cas_poker_as.png')
    _seven_box(b, north_x, BASE + 0.68, 0)
    _glow_dice(b, north_x, BASE + 2.58, -2.05)
    _glow_dice(b, north_x, BASE + 2.58, 2.05)

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

    rf = b.part('roof')
    rf.box('cas_roof', W + 0.52, D + 0.52, 0.28, 0, 0, roof_y)
    rf.box('cas_roof', W - 0.42, D - 0.42, 0.20, 0, 0, roof_y + 0.30)
    rf.box('cas_roof', W - 0.95, D - 0.95, 0.16, 0, 0, roof_y + 0.50)
    # 女兒牆與退台輪廓（俯視分層）
    for sy in (-1, 1):
        rf.box('gold', W + 0.54, 0.07, 0.20, 0, sy * (HY + 0.03), roof_y + 0.36)
        rf.box('cas_dark', W + 0.38, 0.05, 0.12, 0, sy * (HY + 0.02), roof_y + 0.52)
    for sx in (-1, 1):
        rf.box('gold', 0.07, D + 0.54, 0.20, sx * (HX + 0.03), 0, roof_y + 0.36)
        rf.box('cas_dark', 0.05, D + 0.38, 0.12, sx * (HX + 0.02), 0, roof_y + 0.52)
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        rf.box('gold', 0.12, 0.12, 0.28, sx * (W / 2 + 0.08), sy * (D / 2 + 0.08), roof_y + 0.62)

    # 屋頂水平招牌＋輪盤（預設俯視辨識；北簷橫幅 + 南側輪盤不重疊）
    roof_deco = b.part('roof_deco')
    sign_cx, sign_cy = 1.55, -0.35
    sign_w, sign_h = 2.85, 1.08
    wheel = b.part('roulette')
    wheel_z = roof_top + 0.02
    wheel_cx, wheel_cy = -0.12, 0.0
    wheel_d = 2.10
    wheel_r = wheel_d / 2
    sign_rz = -math.pi / 2
    _horiz_tex(
        roof_deco, 'casino/cas_roof_sign.png', sign_w, sign_h,
        sign_cx, sign_cy, roof_top + 0.04, rz=sign_rz, label='roof_sign',
    )
    wheel.cyl('cas_dark', wheel_r, wheel_r, 0.06, wheel_cx, wheel_cy, wheel_z, seg=20)
    _horiz_tex(
        wheel, 'casino/cas_roulette.png', wheel_d, wheel_d,
        wheel_cx, wheel_cy, wheel_z + 0.035, label='roulette',
    )
    wheel.cyl('gold', 0.14, 0.12, 0.04, wheel_cx, wheel_cy, wheel_z + 0.05, seg=8)
    quarter = abs(abs(sign_rz) - math.pi / 2) < 0.02
    sign_rx, sign_ry = sign_w, sign_h
    if quarter:
        sign_rx, sign_ry = sign_h, sign_w
    sign_x0, sign_x1 = sign_cx - sign_rx / 2, sign_cx + sign_rx / 2
    sign_y0, sign_y1 = sign_cy - sign_ry / 2, sign_cy + sign_ry / 2
    north_gap = sign_x0 - (wheel_cx + wheel_r)
    print(f'ROOF_BOUNDS sign X=[{sign_x0:.3f},{sign_x1:.3f}] Y=[{sign_y0:.3f},{sign_y1:.3f}]')
    print(
        f'ROOF_BOUNDS wheel X=[{wheel_cx - wheel_r:.3f},{wheel_cx + wheel_r:.3f}] '
        f'Y=[{wheel_cy - wheel_r:.3f},{wheel_cy + wheel_r:.3f}] north_gap={north_gap:.3f}'
    )
      # 西簷朝上橫幅（門前俯視可讀「神之島」；U 沿南北長邊）
    west_w, west_h = 2.85, 0.95
    west_x, west_y = -HX + 0.58, -0.28
    _horiz_tex(
        roof_deco, 'casino/cas_arch_roof.png', west_w, west_h,
        west_x, west_y, roof_top + 0.035, rz=-math.pi / 2, label='west_eave_sign',
    )

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

    label_blender = (north_x + 0.22, 0.0, sign_z + 0.08)

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