"""中醫推拿館（tcm-site）：w=2.3 d=1.8，門朝本地 +Z。"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
import kit
from kit import Builder, clear_scene, prepare_scene, argv_out

W, D = 2.3, 1.8
PLAT = 0.22
COL_TOP = 1.55
BEAM_TOP = 1.67


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()
    b = Builder('tcm_hall', W, D)
    FRONT = b.front
    HX, HY = b.hx, b.hy

    # 石基、台階
    p = b.part('base')
    p.box('stone_dark', W, D, 0.12, 0, 0, 0.06)
    p.box('stone', W - 0.12, D - 0.12, 0.10, 0, 0, 0.17)
    p.box('stone_dark', W - 0.08, D - 0.08, 0.025, 0, 0, PLAT - 0.012)

    p = b.part('steps')
    for dz, dy in ((0.147, 0.13), (0.074, 0.26)):
        p.box('stone', 0.86, dy, dz, 0, FRONT - dy / 2, dz / 2)
    for sx in (-1, 1):
        p.box('stone_dark', 0.08, 0.26, 0.17, sx * 0.47, FRONT - 0.13, 0.085)

    # 前庭鋪面（中央走道淨空，給門口互動區）
    p = b.part('courtyard')
    p.box('paving', 1.7, 1.15, 0.03, 0, FRONT - 0.26 - 0.575, 0.015)
    for i in range(5):
        p.box('stone_dark', 1.7, 0.012, 0.032, 0, FRONT - 0.36 - i * 0.22, 0.016)
    # 兩側低石欄（不擋中央走道）
    for sx in (-1, 1):
        p.box('stone', 0.08, 1.05, 0.16, sx * 0.82, FRONT - 0.78, 0.10)
        p.box('stone_dark', 0.10, 0.10, 0.20, sx * 0.82, FRONT - 0.30, 0.12)
        p.box('stone_dark', 0.10, 0.10, 0.20, sx * 0.82, FRONT - 1.25, 0.12)

    # 柱
    p = b.part('pillars')
    COLS = [(-0.98, -0.72), (-0.33, -0.72), (0.33, -0.72), (0.98, -0.72),
            (-0.98, 0.0), (0.98, 0.0), (-0.98, 0.72), (0.98, 0.72),
            (-0.33, 0.72), (0.33, 0.72)]
    for (x, y) in COLS:
        p.cyl('stone', 0.085, 0.075, 0.05, x, y, PLAT + 0.025)
        p.cyl('pillar_red', 0.055, 0.05, COL_TOP - PLAT, x, y, (PLAT + COL_TOP) / 2)
        p.box('gold', 0.13, 0.13, 0.05, x, y, COL_TOP + 0.02)

    # 額枋
    p = b.part('beams')
    bx, by = 1.04, 0.76
    for y in (-by, by):
        p.box('beam_teal', 2 * bx + 0.1, 0.09, 0.10, 0, y, COL_TOP + 0.03)
        p.box('gold', 2 * bx + 0.1, 0.095, 0.018, 0, y, COL_TOP - 0.02)
    for x in (-bx, bx):
        p.box('beam_teal', 0.09, 2 * by + 0.1, 0.10, x, 0, COL_TOP + 0.03)
        p.box('gold', 0.095, 2 * by + 0.1, 0.018, x, 0, COL_TOP - 0.02)
    p.box('wood', 2 * bx + 0.22, 2 * by + 0.22, 0.05, 0, 0, BEAM_TOP - 0.03)
    for y in (-by - 0.06, by + 0.06):
        for i in range(9):
            p.box('gold', 0.07, 0.08, 0.06, -0.96 + i * 0.24, y, COL_TOP + 0.1)
    for x in (-bx - 0.06, bx + 0.06):
        for i in range(6):
            p.box('gold', 0.08, 0.07, 0.06, x, -0.62 + i * 0.248, COL_TOP + 0.1)

    # 牆（前廊退縮）
    WX0, WX1, WY0, WY1 = -0.92, 0.92, -0.50, 0.70
    WALL_H = COL_TOP - PLAT
    p = b.part('walls')
    p.box('wall_red', WX1 - WX0, 0.06, WALL_H, 0, WY1, PLAT + WALL_H / 2)
    for x in (WX0, WX1):
        p.box('wall_red', 0.06, WY1 - WY0, WALL_H, x, (WY0 + WY1) / 2, PLAT + WALL_H / 2)
    p.box('wall_red', WX1 - WX0, 0.06, WALL_H, 0, WY0, PLAT + WALL_H / 2)
    p.box('stone_dark', WX1 - WX0 + 0.06, WY1 - WY0 + 0.06, 0.12, 0, (WY0 + WY1) / 2, PLAT + 0.06)

    # 門窗
    lat = b.part('lattice')
    DOOR_H = 1.05
    for sx in (-1, 1):
        b.lattice_panel(sx * 0.145, PLAT + 0.12 + DOOR_H / 2, 0.26, DOOR_H - 0.06, WY0, nx=2, nz=5)
    lat.box('wood', 0.66, 0.05, 0.06, 0, WY0 - 0.05, PLAT + 0.12 + DOOR_H + 0.03)
    lat.box('wood', 0.66, 0.06, 0.04, 0, WY0 - 0.05, PLAT + 0.14)
    for sx in (-1, 1):
        lat.cyl('gold', 0.022, 0.022, 0.012, sx * 0.04, WY0 - 0.075, PLAT + 0.62, seg=8, rx=math.pi / 2)
    for sx in (-1, 1):
        b.lattice_panel(sx * 0.62, PLAT + 0.74, 0.42, 0.52, WY0, nx=3, nz=3)
    for sx, n in ((-1, '-x'), (1, '+x')):
        b.lattice_panel(sx * 0.92, PLAT + 0.78, 0.5, 0.46, 0.12, nx=4, nz=3, normal=n)
    b.lattice_panel(0, PLAT + 0.8, 0.6, 0.38, WY1, nx=4, nz=2, normal='+y')

    # 簷下仍留一塊小裝飾匾（不是 sign_face，高視角會被擋住）
    dec = b.part('eave_plaque')
    dec.box('gold', 0.62, 0.03, 0.16, 0, -by - 0.075, COL_TOP - 0.04)
    dec.box('sign', 0.54, 0.016, 0.11, 0, -by - 0.09, COL_TOP - 0.04)

    # 燈籠
    p = b.part('lanterns')
    for sx in (-1, 1):
        x, y = sx * 0.66, -0.86
        L0 = COL_TOP - 0.25
        p.box('wood', 0.008, 0.008, 0.16, x, y, L0 + 0.175)
        p.cyl('gold', 0.035, 0.035, 0.025, x, y, L0 + 0.09)
        p.cyl('gold', 0.035, 0.035, 0.025, x, y, L0 - 0.09)
        p.box('gold', 0.01, 0.01, 0.06, x, y, L0 - 0.125)
    for sx in (-1, 1):
        b.part('lanterns_glow').sphere('lantern_glow', 0.075, sx * 0.66, -0.86, COL_TOP - 0.25, sz=1.25, seg=8, rings=6)

    # 重簷屋頂：下簷較寬較矮，上簷較陡，中間一段短牆
    Z0L, RISE_L = BEAM_TOP, 0.36
    EHXL, EHYL = HX + 0.38, HY + 0.36
    RHXL, RHYL = 0.88, 0.44
    rings_l, nxl, nyl, nrl = b.hip_roof(
        'roof_lower', Z0L, RISE_L, EHXL, EHYL, RHXL, RHYL,
        nx=12, ny=8, nr=5, lift=0.16, push=0.08,
    )
    zlow = Z0L + RISE_L
    rid_l = b.part('roof_ridge_lower')
    rid_l.box('roof_ridge', 2 * RHXL + 0.08, 2 * RHYL + 0.08, 0.06, 0, 0, zlow + 0.03)
    rid_l.box('gold', 2 * RHXL + 0.04, 2 * RHYL + 0.04, 0.02, 0, 0, zlow + 0.07)

    attic_h = 0.22
    attic = b.part('attic')
    aw, ad = 2 * RHXL - 0.16, 2 * RHYL - 0.16
    attic.box('wall_red', aw, 0.06, attic_h, 0, -(RHYL - 0.10), zlow + attic_h / 2)
    attic.box('wall_red', aw, 0.06, attic_h, 0, (RHYL - 0.10), zlow + attic_h / 2)
    attic.box('wall_red', 0.06, ad, attic_h, -(RHXL - 0.10), 0, zlow + attic_h / 2)
    attic.box('wall_red', 0.06, ad, attic_h, (RHXL - 0.10), 0, zlow + attic_h / 2)
    b.lattice_panel(0, zlow + attic_h / 2, 0.48, 0.14, -(RHYL - 0.10), nx=3, nz=1)

    Z0U = zlow + attic_h
    RISE_U = 0.52
    EHXU, EHYU = RHXL + 0.12, RHYL + 0.10
    RHXU, RHYU = 0.42, 0.03
    rings, nx, ny, nr = b.hip_roof(
        'roof', Z0U, RISE_U, EHXU, EHYU, RHXU, RHYU,
        nx=12, ny=8, nr=6, lift=0.18, push=0.10,
    )
    b.ridge_ornaments(rings, Z0U, RISE_U, RHXU, nx, ny, nr)

    # 招牌放在上簷前坡，高視角仍可讀「推拿館」
    sign_z = Z0U + RISE_U * 0.55 + 0.16
    sign_y = -0.28
    b.tilted_sign(1.05, 0.36, sign_y, sign_z, rx=-1.05)

    Z0, RISE = Z0U, RISE_U  # 給後面的 label 高度用

    # 前庭牌坊（兩側柱 + 橫樑；中央淨空）
    pg = b.part('paifang')
    gate_y = FRONT - 1.32
    for sx in (-1, 1):
        pg.cyl('stone', 0.07, 0.06, 0.06, sx * 0.62, gate_y, 0.06)
        pg.cyl('pillar_red', 0.045, 0.04, 1.38, sx * 0.62, gate_y, 0.06 + 0.69)
        pg.box('gold', 0.12, 0.12, 0.05, sx * 0.62, gate_y, 1.47)
    pg.box('beam_teal', 1.40, 0.10, 0.10, 0, gate_y, 1.55)
    pg.box('gold', 1.40, 0.11, 0.02, 0, gate_y, 1.49)
    # 牌坊上的小飾板（裝飾用，主招牌在屋頂）
    pg.box('gold', 0.70, 0.05, 0.22, 0, gate_y - 0.02, 1.69)
    pg.box('sign', 0.60, 0.02, 0.15, 0, gate_y - 0.04, 1.69)

    # 石燈籠（前庭兩外角，不擋走道）
    lan = b.part('stone_lanterns')
    glow = b.part('lanterns_glow')
    for sx in (-1, 1):
        x, y = sx * 0.72, FRONT - 1.18
        lan.box('stone_dark', 0.16, 0.16, 0.06, x, y, 0.04)
        lan.cyl('stone', 0.04, 0.04, 0.28, x, y, 0.22)
        lan.box('stone', 0.14, 0.14, 0.16, x, y, 0.44)
        glow.box('lantern_glow', 0.10, 0.10, 0.10, x, y, 0.44)
        lan.box('stone_dark', 0.20, 0.20, 0.04, x, y, 0.54)
        lan.cyl('stone', 0.03, 0.0, 0.08, x, y, 0.60, seg=5)

    # 前庭小物：近廳盆景；左側銅像、右側藥圃（中央走道淨空）
    pr = b.part('props')
    for sx in (-1, 1):
        x, y = sx * 0.66, FRONT - 0.50
        pr.cyl('pot', 0.09, 0.07, 0.11, x, y, 0.085)
        pr.sphere('plant', 0.11, x, y, 0.23, sz=0.8, seg=7, rings=4)
        pr.sphere('plant', 0.07, x + 0.05, y - 0.03, 0.32, seg=6, rings=4)

    # 銅像（左側，約 1.1 高，不擋門與牌坊中央）
    sx, sy = -0.58, FRONT - 0.95
    pr.box('stone_dark', 0.34, 0.34, 0.08, sx, sy, 0.05)
    pr.box('bronze_dark', 0.26, 0.26, 0.10, sx, sy, 0.14)
    pr.box('bronze', 0.07, 0.07, 0.32, sx - 0.06, sy, 0.36)
    pr.box('bronze', 0.07, 0.07, 0.32, sx + 0.06, sy, 0.36)
    pr.box('bronze', 0.22, 0.11, 0.40, sx, sy, 0.70)
    pr.box('bronze', 0.05, 0.05, 0.30, sx - 0.16, sy, 0.68)
    pr.box('bronze', 0.05, 0.05, 0.30, sx + 0.16, sy, 0.68)
    pr.sphere('bronze', 0.11, sx, sy, 0.98, seg=7, rings=5)
    pr.cyl('bronze_dark', 0.12, 0.08, 0.07, sx, sy, 1.08, seg=6)
    pr.box('gold', 0.04, 0.04, 0.08, sx, sy, 1.15)

    # 藥圃抬床（右側）
    hx, hy = 0.58, FRONT - 0.95
    pr.box('wood', 0.62, 0.42, 0.10, hx, hy, 0.06)
    pr.box('pot', 0.54, 0.34, 0.05, hx, hy, 0.12)
    for i, (dx, dy, col, h) in enumerate((
        (-0.16, 0.08, 'plant', 0.16),
        (-0.02, -0.06, 'herb', 0.14),
        (0.16, 0.06, 'plant', 0.18),
        (0.08, -0.10, 'plant', 0.12),
        (-0.14, -0.08, 'herb', 0.13),
    )):
        pr.cyl(col, 0.025, 0.015, h, hx + dx, hy + dy, 0.16 + h / 2, seg=5)
        pr.sphere(col, 0.06 if i % 2 == 0 else 0.045, hx + dx, hy + dy, 0.16 + h, sz=0.7, seg=5, rings=3)

    # 標籤錨點約在屋脊上方（對齊既有 roofY + 1.05）
    # 既有：roofY ≈ G+0.08+1.85+0.08，anchor 在本地 (0, roofY+1.05, 0.12)
    # GLB 以 y=0 為地面，遊戲會再把模型放在 y=G
    ztop = Z0 + RISE
    label = (0.0, ztop + 1.05, 0.12)  # Blender (x, y, z) = (x, -gltf_z, gltf_y) → wait
    # Blender location (x, y, z) exports to glTF (x, z, -y)
    # Want glTF label at (0, roof_height+1.05, 0.12) → Blender (0, -0.12, ztop+1.05)
    label_blender = (0.0, -0.12, ztop + 1.05)

    return b.finish(
        out_path,
        door_local=[0.0, 0.0, HY],
        label_anchor=label_blender,
        extras={'building_id': 'tcm-site'},
    )


if __name__ == '__main__':
    default = '/workspace/glb-proto/tcm-site.glb'
    build(argv_out(default))
