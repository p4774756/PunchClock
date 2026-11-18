"""研發樓（computer-site）：佔地對齊 island-layout.js，門朝本地 +Z（#155）。

無頭重建：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_computer_site.py -- \\
    server/src/main/resources/public/models/computer-site.glb
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

# 佔地（中心後移，門口世界座標維持約 z≈16.17）
W, D = 3.2, 2.5
BASE = 0.10
DOOR_W = 0.62
STOREY_H = 0.78
STOREYS = 2
# 裙樓 + 退台二層（非高塔）
H = BASE + 0.38 + STOREY_H + 0.52 + 0.22


def _chamfer_corners(p, mat, hx, hy, z0, h, inset=0.14, t=0.11):
    """立面四角 45° 導角體塊。"""
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        p.box(mat, t, t, h, sx * (hx - inset * 0.35), sy * (hy - inset * 0.35), z0,
              rz=math.pi / 4 * sx * sy)


def _curtain_wall(b, x, y, z, w, h, cols, rows, normal='-y', frame='lab_frame', glow='windows_glow',
                  rack_floor=False, skip_bottom=None):
    """玻璃帷幕 + 窗櫺；normal 為 -y 正面、+y 背面、-x/+x 側面（沿 Y 展開）。"""
    g = b.part('windows_glow')
    m = b.part('mullions')
    sil = b.part('interior_sil')
    ft = 0.045
    t_glass = 0.016
    if normal in ('-y', '+y'):
        s = -1 if normal == '-y' else 1
        gy = y + s * 0.03
        g.box(glow, w, t_glass, h, x, gy, z)
        m.box(frame, w + ft * 2, 0.038, ft, x, gy, z + h / 2 + ft / 2)
        m.box(frame, w + ft * 2, 0.038, ft, x, gy, z - h / 2 - ft / 2)
        m.box(frame, ft, 0.038, h, x - w / 2 - ft / 2, gy, z)
        m.box(frame, ft, 0.038, h, x + w / 2 + ft / 2, gy, z)
        for i in range(1, cols):
            mx = x - w / 2 + w * i / cols
            m.box(frame, 0.032, 0.036, h, mx, gy, z)
        for j in range(1, rows):
            mz = z - h / 2 + h * j / rows
            m.box(frame, w, 0.036, 0.028, x, gy, mz)
        cy_off = -0.08 if normal == '-y' else 0.08
        if rack_floor:
            cw, ch = w / cols * 0.55, h / rows * 0.72
            for i in range(cols):
                for j in range(rows):
                    if skip_bottom and j == 0 and abs(x) < 0.01:
                        continue
                    cx = x - w / 2 + cw / 2 + w * i / cols
                    cz = z - h / 2 + ch / 2 + h * j / rows
                    sil.box('lab_steel_dark', cw, 0.10, ch, cx, y + cy_off, cz)
                    sil.box('lab_accent', cw * 0.85, 0.04, 0.025, cx, y + cy_off - 0.04, cz + ch * 0.3)
                    sil.box('neon_glow', cw * 0.7, 0.02, 0.02, cx, y + cy_off - 0.03, cz - ch * 0.25)
    else:
        s = -1 if normal == '-x' else 1
        gx = x + s * 0.03
        g.box(glow, t_glass, w, h, gx, y, z)
        m.box(frame, ft, w + ft * 2, ft, gx, y, z + h / 2 + ft / 2)
        m.box(frame, ft, w + ft * 2, ft, gx, y, z - h / 2 - ft / 2)
        m.box(frame, ft, ft, h, gx, y - w / 2 - ft / 2, z)
        m.box(frame, ft, ft, h, gx, y + w / 2 + ft / 2, z)
        for i in range(1, cols):
            my = y - w / 2 + w * i / cols
            m.box(frame, 0.032, 0.036, h, gx, my, z)
        for j in range(1, rows):
            mz = z - h / 2 + h * j / rows
            m.box(frame, ft, w, 0.028, gx, y, mz)


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()

    set_palette_overrides({
        'lab_panel': (0xf8fbff, None),
        'lab_grey': (0xe8eef4, None),
        'lab_steel': (0xd0dae4, None),
        'lab_steel_dark': (0xa8b8c8, None),
        'lab_frame': (0xc8d4dc, None),
        'windows_glow': (0xb8e8ff, 0x7ec8ea),
        'lantern_glow': (0xa8d8f0, 0x7ec8ea),
        'neon_glow': (0x3cf0ff, 0x3cf0ff),
        'lab_accent': (0x8ab8d0, None),
        'lab_ground': (0xc4d0d8, None),
    })

    b = Builder('computer_site', W, D)
    FRONT, BACK = b.front, b.hy
    HX, HY = b.hx, b.hy

    # ── 前庭（短淺，不往職涯走廊前凸）──
    p = b.part('plaza')
    p.box('lab_ground', 2.15, 0.95, 0.05, 0, FRONT - 0.48, 0.025)
    p.box('lab_panel', 1.95, 0.82, 0.04, 0, FRONT - 0.48, 0.055)
    for i, dz in enumerate((0.06, 0.10)):
        p.box('lab_grey', 1.25 - i * 0.12, 0.28 - i * 0.04, 0.05, 0, FRONT - 0.22 - i * 0.22, dz)

    st = b.part('base')
    st.box('lab_panel', W + 0.18, D + 0.14, 0.12, 0, 0.02, 0.06)
    st.box('lab_grey', W - 0.14, D - 0.12, 0.06, 0, 0.02, 0.13)

    z_ground = BASE + 0.38
    h1 = STOREY_H
    z2_base = z_ground + h1
    h2 = 0.50
    tw, td = W - 0.42, D - 0.36

    vol = b.part('massing')
    _chamfer_corners(vol, 'lab_steel', HX, HY, z_ground + 0.08, h1 + h2 - 0.05, inset=0.15)
    # 一層白牆＋裙樓
    vol.box('lab_panel', W - 0.06, 0.10, h1, 0, BACK - 0.04, z_ground + h1 / 2)
    vol.box('lab_grey', 0.10, D - 0.14, h1, -HX + 0.05, 0.02, z_ground + h1 / 2)
    vol.box('lab_grey', 0.10, D - 0.14, h1, HX - 0.05, 0.02, z_ground + h1 / 2)
    # 二層退台
    vol.box('lab_panel', tw, 0.09, h2, 0, BACK + 0.08, z2_base + h2 / 2)
    vol.box('lab_grey', 0.09, td - 0.12, h2, -(tw / 2 - 0.05), 0.06, z2_base + h2 / 2)
    vol.box('lab_grey', 0.09, td - 0.12, h2, tw / 2 - 0.05, 0.06, z2_base + h2 / 2)
    vol.box('lab_steel', W - 0.10, 0.06, 0.08, 0, FRONT - 0.02, z2_base + 0.04)

    neo = b.part('neon')
    fr = b.part('frame')
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        fr.box('lab_steel_dark', 0.12, 0.12, z2_base + h2 - 0.12, sx * (HX - 0.07), sy * (HY - 0.07),
               (z_ground + z2_base + h2) / 2)

    # 正面大玻璃 + 二層橫帶
    fw = W - 0.26
    _curtain_wall(b, 0, FRONT - 0.05, z_ground + h1 * 0.52, fw, h1 - 0.18, 6, 3,
                  normal='-y', rack_floor=True, skip_bottom=True)
    _curtain_wall(b, 0, FRONT - 0.04, z2_base + h2 * 0.52, fw - 0.35, h2 - 0.12, 4, 2,
                  normal='-y', rack_floor=True)

    # 側立面玻璃（正確法向）
    sw = D - 0.28
    side_h = h1 - 0.14
    for sx in (-1, 1):
        normal = '-x' if sx < 0 else '+x'
        _curtain_wall(b, sx * (HX + 0.02), 0, z_ground + side_h / 2 + 0.08, sw, side_h, 5, 2, normal=normal)
        _curtain_wall(b, sx * (HX + 0.01), 0, z2_base + h2 * 0.50, td - 0.20, h2 - 0.10, 3, 2, normal=normal)

    # 背面
    _curtain_wall(b, 0, BACK + 0.05, z_ground + h1 * 0.50, W - 0.36, h1 - 0.16, 5, 2, normal='+y')

    # 簷口／轉角青色燈條（非整片屋頂）
    eave_z = z2_base + h2 + 0.02
    neo.box('neon_glow', fw + 0.04, 0.022, 0.022, 0, FRONT - 0.07, eave_z)
    neo.box('neon_glow', sw + 0.04, 0.022, 0.022, -HX - 0.05, 0, z_ground + side_h + 0.06)
    neo.box('neon_glow', sw + 0.04, 0.022, 0.022, HX + 0.05, 0, z_ground + side_h + 0.06)
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        neo.box('neon_glow', 0.028, 0.028, 0.42, sx * (HX - 0.04), sy * (HY - 0.04), z_ground + 0.35)

    # 入口
    door = b.part('door')
    door_h = 1.22
    door_z = z_ground + door_h / 2 - 0.08
    door.box('lab_steel_dark', DOOR_W, 0.05, door_h, 0, FRONT + 0.02, door_z)
    b.part('windows_glow').box('windows_glow', DOOR_W - 0.08, 0.022, door_h - 0.12,
                               0, FRONT - 0.02, door_z)
    door.box('lab_accent', DOOR_W + 0.12, 0.07, 0.06, 0, FRONT, door_z + door_h / 2 - 0.02)
    door.box('lab_accent', 0.05, 0.07, door_h, -DOOR_W / 2, FRONT, door_z)
    door.box('lab_accent', 0.05, 0.07, door_h, DOOR_W / 2, FRONT, door_z)
    can = b.part('canopy')
    can.box('lab_steel', 1.05, 0.55, 0.04, 0, FRONT - 0.22, door_z + door_h / 2 + 0.12, rx=-0.10)
    can.box('lab_steel_dark', 1.08, 0.04, 0.04, 0, FRONT - 0.22, door_z + door_h / 2 + 0.06)
    neo.box('neon_glow', 1.0, 0.018, 0.018, 0, FRONT - 0.24, door_z + door_h / 2 + 0.05)

    # 門側機櫃（貼牆，不往前凸）
    racks = b.part('racks')
    for sx in (-1.0, 1.0):
        racks.box('lab_steel_dark', 0.36, 0.30, 0.88, sx, FRONT - 0.18, z_ground + 0.52)
        racks.box('lab_frame', 0.38, 0.32, 0.06, sx, FRONT - 0.18, z_ground + 1.02)
        for zz in (0.32, 0.50, 0.68):
            neo.box('neon_glow', 0.26, 0.016, 0.022, sx, FRONT - 0.32, z_ground + zz)
        b.part('lanterns_glow').sphere('lantern_glow', 0.040, sx, FRONT - 0.18, z_ground + 0.98, seg=6, rings=4)

    # 右側戶外螢幕（貼牆）
    scr = b.part('outdoor_screen')
    scr.box('lab_steel_dark', 0.72, 0.44, 0.05, 1.05, FRONT + 0.06, z_ground + 0.88, rz=-0.28)
    scr.box('windows_glow', 0.58, 0.30, 0.02, 1.05, FRONT + 0.02, z_ground + 0.88, rz=-0.28)
    scr.box('neon_glow', 0.50, 0.06, 0.02, 1.05, FRONT - 0.02, z_ground + 1.08, rz=-0.28)

    roof_y = z2_base + h2 + 0.08
    rf = b.part('roof')
    rf.box('lab_panel', W + 0.06, D + 0.04, 0.07, 0, 0, roof_y - 0.04)
    rf.box('lab_grey', W + 0.12, D + 0.10, 0.06, 0, 0, roof_y)
    rf.box('lab_steel', W + 0.22, D + 0.16, 0.04, 0, 0, roof_y + 0.05)
    # 女兒牆（淺灰；屋頂檐口細條青光）
    for sy in (-1, 1):
        rf.box('lab_grey', W + 0.10, 0.06, 0.14, 0, sy * (HY + 0.02), roof_y + 0.12)
    for sx in (-1, 1):
        rf.box('lab_grey', 0.06, D + 0.10, 0.14, sx * (HX + 0.02), 0, roof_y + 0.12)
    neo.box('neon_glow', W + 0.14, 0.018, 0.018, 0, FRONT - HY + 0.06, roof_y + 0.13)
    neo.box('neon_glow', W + 0.14, 0.018, 0.018, 0, BACK + HY - 0.06, roof_y + 0.13)
    neo.box('neon_glow', 0.018, D + 0.10, 0.018, -HX - 0.02, 0, roof_y + 0.13)
    neo.box('neon_glow', 0.018, D + 0.10, 0.018, HX + 0.02, 0, roof_y + 0.13)

    # 大面藍色太陽能板（高視角可辨）
    s_w, s_d = 1.15, 0.72
    rf.box('shop_navy', s_w, s_d, 0.05, 0.15, -0.08, roof_y + 0.10, rx=0.22)
    for i in range(5):
        for j in range(3):
            rf.box('shop_navy_dark', s_w / 5 - 0.02, s_d / 3 - 0.02, 0.012,
                   0.15 - s_w / 2 + s_w * (i + 0.5) / 5, -0.08 - s_d / 2 + s_d * (j + 0.5) / 3,
                   roof_y + 0.16, rx=0.22)
    rf.box('shop_navy', 0.55, 0.42, 0.04, -0.62, 0.12, roof_y + 0.10, rx=-0.18)
    rf.box('shop_navy_dark', 0.50, 0.38, 0.025, -0.62, 0.12, roof_y + 0.145, rx=-0.18)

    # 衛星碟（加大、朝鏡頭側）
    ant = b.part('antenna')
    ax, ay = 0.78, -0.10
    ant.cyl('lab_steel', 0.055, 0.055, 0.38, ax, ay, roof_y + 0.22, seg=8)
    ant.cyl('lab_panel', 0.42, 0.05, 0.06, ax + 0.08, ay, roof_y + 0.48, seg=12, rx=0.72, ry=0.15)
    ant.box('lab_steel_dark', 0.03, 0.03, 0.16, ax + 0.04, ay, roof_y + 0.48)
    b.part('lanterns_glow').sphere('lantern_glow', 0.085, ax + 0.08, ay, roof_y + 0.72, seg=8, rings=5)

    rf.box('lab_grey', 0.58, 0.46, 0.28, -0.62, 0.08, roof_y + 0.22)
    rf.box('lab_steel', 0.54, 0.08, 0.06, -0.62, 0.08, roof_y + 0.40)
    rf.cyl('lab_steel_dark', 0.045, 0.045, 0.12, -0.76, 0.20, roof_y + 0.32, seg=6)

    wx, wy = -HX - 0.32, FRONT - 0.42
    b.part('windmill_base').cyl('lab_steel', 0.035, 0.035, 0.44, wx, wy, z_ground + 0.26, seg=6)
    hub_z = z_ground + 0.52
    rotor = b.part('windmill')
    rotor.cyl('lab_accent', 0.062, 0.062, 0.04, wx, wy, hub_z, seg=6)
    for i in range(3):
        ang = (i / 3) * math.pi * 2
        rotor.box('lab_panel', 0.072, 0.020, 0.36, wx + math.sin(ang) * 0.16, wy + math.cos(ang) * 0.10,
                  hub_z + 0.02 + math.cos(ang) * 0.14, rz=ang)

    for sx in (-0.82, 0.82):
        b.part('lanterns_glow').sphere('lantern_glow', 0.050, sx, FRONT - 0.08, z_ground + 1.05, seg=6, rings=4)

    # 招牌：入口上方朝 +Z，預設高視角可讀「研發樓」（sign_face / labSignMap）
    sign_z = door_z + door_h / 2 + 0.28
    b.tilted_sign(1.48, 0.54, FRONT - 0.10, sign_z, rx=-0.38,
                  frame='lab_accent', board='lab_steel_dark')

    label_blender = (0.0, -0.12, sign_z + 0.42)
    return b.finish(
        out_path,
        door_local=[0.0, 0.0, HY],
        label_anchor=label_blender,
        extras={'building_id': 'computer-site'},
    )


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'computer-site.glb')
    build(argv_out(_default))