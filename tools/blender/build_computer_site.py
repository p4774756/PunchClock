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

# 佔地（可較舊版放大；中心後移，門口世界座標維持約 z≈16.17）
W, D = 3.2, 2.5
BASE = 0.10
DOOR_W = 0.62
STOREY_H = 1.05
STOREYS = 3
H = BASE + STOREYS * STOREY_H + 0.35


def _chamfer_corners(p, mat, hx, hy, z0, h, inset=0.14, t=0.11):
    """立面四角 45° 導角體塊。"""
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        p.box(mat, t, t, h, sx * (hx - inset * 0.35), sy * (hy - inset * 0.35), z0,
              rz=math.pi / 4 * sx * sy)


def _curtain_wall(b, x, y, z, w, h, cols, rows, frame='lab_frame', glow='windows_glow',
                  rack_floor=False, skip_bottom=None):
    """玻璃帷幕 + 窗外側窗櫺；可選機櫃剪影。"""
    g = b.part('windows_glow')
    m = b.part('mullions')
    sil = b.part('interior_sil')
    gy = y - 0.03
    g.box(glow, w, 0.016, h, x, y, z)
    ft = 0.045
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
    if rack_floor:
        cw, ch = w / cols * 0.55, h / rows * 0.72
        for i in range(cols):
            for j in range(rows):
                if skip_bottom and j == 0 and abs(x) < 0.01:
                    continue
                cx = x - w / 2 + cw / 2 + w * i / cols
                cz = z - h / 2 + ch / 2 + h * j / rows
                sil.box('lab_steel_dark', cw, 0.10, ch, cx, y - 0.08, cz)
                sil.box('lab_accent', cw * 0.85, 0.04, 0.025, cx, y - 0.12, cz + ch * 0.3)
                sil.box('neon_glow', cw * 0.7, 0.02, 0.02, cx, y - 0.11, cz - ch * 0.25)


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

    # ── 前庭台阶与导角台基 ──
    p = b.part('plaza')
    p.box('lab_ground', 2.55, 1.85, 0.05, 0, FRONT - 0.95, 0.025)
    p.box('lab_panel', 2.35, 1.65, 0.04, 0, FRONT - 0.95, 0.055)
    for i, dz in enumerate((0.06, 0.11, 0.16)):
        p.box('lab_grey', 1.45 - i * 0.08, 0.42 - i * 0.05, 0.05, 0, FRONT - 0.32 - i * 0.40, dz)

    st = b.part('base')
    st.box('lab_panel', W + 0.22, D + 0.18, 0.14, 0, 0.02, 0.07)
    st.box('lab_grey', W - 0.12, D - 0.10, 0.08, 0, 0.02, 0.15)
    _chamfer_corners(st, 'lab_steel', HX, HY, BASE + 0.55, 1.05, inset=0.16)

    vol = b.part('massing')
    # 一層：略外探的裙樓 + 側翼後退
    z1 = BASE + 0.55
    h1 = STOREY_H - 0.08
    vol.box('lab_panel', W, 0.11, h1, 0, BACK - 0.05, z1 + h1 / 2)
    vol.box('lab_grey', 0.11, D - 0.18, h1, -HX + 0.055, 0.04, z1 + h1 / 2)
    vol.box('lab_grey', 0.11, D - 0.18, h1, HX - 0.055, 0.04, z1 + h1 / 2)
    # 二層：中央塔樓收進，形成層次
    z2 = BASE + STOREY_H + 0.42
    h2 = STOREY_H
    tw, td = W - 0.38, D - 0.28
    vol.box('lab_panel', tw, 0.10, h2, 0, BACK + 0.06, z2 + h2 / 2)
    vol.box('lab_grey', 0.10, td - 0.14, h2, -(tw / 2 - 0.05), 0.08, z2 + h2 / 2)
    vol.box('lab_grey', 0.10, td - 0.14, h2, tw / 2 - 0.05, 0.08, z2 + h2 / 2)
    # 三層：更小的屋頂機房 + 女兒牆
    z3 = BASE + STOREY_H * 2 + 0.48
    h3 = STOREY_H * 0.88
    pw, pd = W - 0.55, D - 0.42
    vol.box('lab_steel', pw, 0.09, h3, 0, BACK + 0.12, z3 + h3 / 2)
    vol.box('lab_grey', 0.09, pd, h3, 0, 0, z3 + h3 / 2)

    neo = b.part('neon')
    fr = b.part('frame')
    # 四角主結構柱（貫穿）
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        fr.box('lab_steel_dark', 0.14, 0.14, H - 0.35, sx * (HX - 0.08), sy * (HY - 0.08), H / 2)

    # 玻璃帷幕：三層正面 + 側翼後退窗
    fw = W - 0.28
    for i in range(STOREYS):
        z0 = BASE + 0.32 + i * STOREY_H
        hh = STOREY_H - 0.22 if i < STOREYS - 1 else STOREY_H - 0.10
        _curtain_wall(b, 0, FRONT - 0.04, z0 + hh / 2, fw, hh, 6, 3,
                      rack_floor=(i >= 1), skip_bottom=(i == 0))
        neo.box('neon_glow', fw + 0.08, 0.03, 0.03, 0, FRONT - 0.08, z0 + hh + 0.05)
    neo.box('neon_glow', 0.035, fw + 0.12, 0.035, -HX + 0.02, FRONT - 0.06,
            BASE + STOREY_H * 1.5)
    neo.box('neon_glow', 0.035, fw + 0.12, 0.035, HX - 0.02, FRONT - 0.06,
            BASE + STOREY_H * 1.5)

    for i in range(STOREYS):
        zband = BASE + (i + 1) * STOREY_H + 0.15
        neo.box('neon_glow', W + 0.06, D + 0.08, 0.035, 0, 0, zband)
        for sx in (-1, 1):
            neo.box('neon_glow', 0.035, D + 0.04, 0.50, sx * (HX + 0.04), 0, zband)

    # 側面窗帶
    for i in range(STOREYS):
        z0 = BASE + 0.38 + i * STOREY_H
        hh = STOREY_H - 0.28
        sw = D - 0.35
        for sx in (-1, 1):
            _curtain_wall(b, sx * (HX + 0.03), 0, z0 + hh / 2, sw, hh, 4, 2, rack_floor=False)

    # 背面
    for i in range(STOREYS):
        z0 = BASE + 0.35 + i * STOREY_H
        hh = STOREY_H - 0.24
        _curtain_wall(b, 0, BACK + 0.04, z0 + hh / 2, W - 0.40, hh, 5, 2)

    # 入口玻璃門
    door = b.part('door')
    door_h = 1.34
    door_z = BASE + 0.32 + door_h / 2
    door.box('lab_steel_dark', DOOR_W, 0.05, door_h, 0, FRONT + 0.025, door_z)
    b.part('windows_glow').box('windows_glow', DOOR_W - 0.08, 0.022, door_h - 0.12,
                               0, FRONT - 0.02, door_z)
    door.box('lab_accent', DOOR_W + 0.14, 0.08, 0.06, 0, FRONT, door_z + door_h / 2 - 0.02)
    door.box('lab_accent', 0.05, 0.08, door_h, -DOOR_W / 2, FRONT, door_z)
    door.box('lab_accent', 0.05, 0.08, door_h, DOOR_W / 2, FRONT, door_z)
    # 入口雨棚
    can = b.part('canopy')
    can.box('lab_steel', 1.35, 0.72, 0.05, 0, FRONT - 0.38, door_z + door_h / 2 + 0.18, rx=-0.12)
    can.box('lab_steel_dark', 1.38, 0.04, 0.05, 0, FRONT - 0.38, door_z + door_h / 2 + 0.08)
    neo.box('neon_glow', 1.32, 0.025, 0.025, 0, FRONT - 0.40, door_z + door_h / 2 + 0.06)

    # 門側戶外機櫃
    racks = b.part('racks')
    for sx in (-1.15, 1.15):
        racks.box('lab_steel_dark', 0.38, 0.34, 0.92, sx, FRONT - 0.48, BASE + 0.58)
        racks.box('lab_frame', 0.40, 0.36, 0.06, sx, FRONT - 0.48, BASE + 1.08)
        for k, zz in enumerate((0.34, 0.52, 0.72)):
            neo.box('neon_glow', 0.28, 0.018, 0.025, sx, FRONT - 0.62, BASE + zz)
        b.part('lanterns_glow').sphere('lantern_glow', 0.042, sx, FRONT - 0.48, BASE + 1.02, seg=6, rings=4)

    # 右側戶外螢幕（靜態配色塊；無動態貼圖）
    scr = b.part('outdoor_screen')
    sx, sy, sz = 1.18, FRONT + 0.12, BASE + 1.22
    scr.box('lab_steel_dark', 0.85, 0.52, 0.06, sx, sy, sz, rz=-0.38)
    scr.box('lab_steel', 0.78, 0.46, 0.03, sx, sy - 0.04, sz, rz=-0.38)
    scr.box('neon_glow', 0.62, 0.08, 0.02, sx, sy - 0.08, sz + 0.18, rz=-0.38)
    scr.box('windows_glow', 0.70, 0.32, 0.02, sx, sy - 0.05, sz, rz=-0.38)

    roof_y = BASE + STOREYS * STOREY_H + 0.52
    rf = b.part('roof')
    rf.box('lab_panel', W + 0.10, D + 0.08, 0.08, 0, 0, roof_y - 0.06)
    rf.box('lab_steel_dark', W + 0.28, D + 0.22, 0.12, 0, 0, roof_y)
    # 女兒牆
    for sy in (-1, 1):
        rf.box('lab_grey', W + 0.24, 0.07, 0.22, 0, sy * (HY + 0.04), roof_y + 0.18)
    for sx in (-1, 1):
        rf.box('lab_grey', 0.07, D + 0.18, 0.22, sx * (HX + 0.04), 0, roof_y + 0.18)
    neo.box('neon_glow', W + 0.38, D + 0.30, 0.04, 0, 0, roof_y + 0.26)

    # 太陽能板（傾斜）
    for ox, oy, rw, rd, tilt in (
        (0.55, -0.22, 0.92, 0.52, 0.32),
        (-0.48, 0.08, 0.58, 0.48, -0.28),
        (0.10, 0.28, 0.48, 0.38, 0.18),
    ):
        rf.box('lab_steel_dark', rw, rd, 0.04, ox, oy, roof_y + 0.14, rx=tilt)
        rf.box('lab_steel', rw - 0.04, rd - 0.04, 0.025, ox, oy, roof_y + 0.175, rx=tilt)

    # 衛星碟
    ant = b.part('antenna')
    ant.cyl('lab_steel', 0.05, 0.05, 0.52, 0.62, -0.12, roof_y + 0.42, seg=6)
    ant.cyl('lab_panel', 0.30, 0.06, 0.05, 0.72, -0.12, roof_y + 0.62, seg=8, rx=0.82)
    ant.box('lab_steel_dark', 0.025, 0.025, 0.14, 0.68, -0.12, roof_y + 0.62)
    b.part('lanterns_glow').sphere('lantern_glow', 0.065, 0.62, -0.12, roof_y + 0.82, seg=6, rings=4)

    # 屋頂機房盒
    rf.box('lab_grey', 0.62, 0.48, 0.32, -0.58, 0.10, roof_y + 0.30)
    rf.box('lab_steel', 0.58, 0.08, 0.06, -0.58, 0.10, roof_y + 0.48)
    rf.cyl('lab_steel_dark', 0.05, 0.05, 0.14, -0.72, 0.22, roof_y + 0.38, seg=6)

    # 地面風車（左前；windmill 僅轉子與葉片）
    wx, wy = -HX - 0.38, FRONT - 0.72
    b.part('windmill_base').cyl('lab_steel', 0.035, 0.035, 0.48, wx, wy, BASE + 0.28, seg=6)
    hub_z = BASE + 0.56
    rotor = b.part('windmill')
    rotor.cyl('lab_accent', 0.065, 0.065, 0.045, wx, wy, hub_z, seg=6)
    for i in range(3):
        ang = (i / 3) * math.pi * 2
        rotor.box('lab_panel', 0.075, 0.022, 0.40, wx + math.sin(ang) * 0.18, wy + math.cos(ang) * 0.12,
                  hub_z + 0.02 + math.cos(ang) * 0.16, rz=ang)

    # 簷下燈
    for sx in (-0.95, 0.95):
        b.part('lanterns_glow').sphere('lantern_glow', 0.055, sx, FRONT - 0.12, BASE + 1.28, seg=6, rings=4)

    # 招牌
    b.tilted_sign(1.55, 0.48, FRONT - 0.14, roof_y + 0.38, rx=-1.08,
                  frame='lab_accent', board='lab_steel_dark')

    label_blender = (0.0, -0.12, roof_y + 1.08)
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