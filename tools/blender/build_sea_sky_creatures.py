"""海空生物 v2：低多邊手繪頂點色 + 游泳／拍翅骨骼，導出至 public/models/life/。

  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_sea_sky_creatures.py
"""
from __future__ import annotations

import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, '..', '..'))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

import bpy
from mathutils import Vector

from creature_common import (
    bake_flap_cycle,
    bake_swim_cycle,
    bind_mesh,
    build_colored_mesh,
    clear_all,
    export_glb,
    make_armature,
    prim_box,
    prim_cone,
    prim_cylinder,
    prim_sphere,
    assign_region_weights,
)

OUT_DIR = os.path.join(_REPO, 'server', 'src', 'main', 'resources', 'public', 'models', 'life')

COL = {
    'whale_blue': 0x3a6a9a,
    'whale_pale': 0xc8dce8,
    'fish_yellow': 0xf0c848,
    'fish_blue': 0x48a8d8,
    'fish_orange': 0xf07838,
    'octo': 0xf0a898,
    'octo_dark': 0xd87878,
    'wen_blue': 0x58a8e0,
    'wen_white': 0xe8f0f8,
    'wen_wing': 0xb8d8f0,
    'chiru_red': 0xe84848,
    'chiru_gold': 0xf0c858,
    'xuan_green': 0x3a5848,
    'xuan_gold': 0xd4a848,
    'xuan_snake': 0x2a4838,
    'serpent': 0x58c8c8,
    'serpent_fin': 0x38a8a8,
    'gull_white': 0xf0f0f8,
    'gull_wing': 0xb8c0d0,
    'gull_beak': 0xf0a848,
    'qing_blue': 0x48b8f0,
    'qing_tail': 0x88d8ff,
    'phoenix': 0xff8838,
    'phoenix_gold': 0xffcc68,
    'wyvern_green': 0x58a868,
    'wyvern_wing': 0xf0d8a8,
    'kun_cloud': 0xe8f0ff,
    'kun_shade': 0x7a98c8,
    'kun_fin': 0xc8e0ff,
    'spray': 0xd8f0ff,
    'xuan_moss': 0x5a7858,
    'fish_red': 0xf05858,
    'fish_green': 0x58c878,
}


def _link_root(rig, mesh):
    coll = bpy.context.scene.collection
    coll.objects.link(rig)
    coll.objects.link(mesh)
    mesh.parent = rig
    bind_mesh(mesh, rig)


def build_fish():
    def body(bm):
        prim_sphere(bm, (0, 0, 0.06), 0.16, 6, 5)
        prim_cone(bm, (-0.22, 0, 0.06), 0.07, 0.14, 4, rot=(0, 0, math.pi))
        prim_box(bm, (0.05, 0.02, 0.03), (0, 0.1, 0.04))
    mesh = build_colored_mesh('fish_body', [
        (COL['fish_yellow'], lambda bm: (prim_sphere(bm, (0, 0, 0.06), 0.16, 6, 5), prim_cone(bm, (-0.22, 0, 0.06), 0.07, 0.14, 4, rot=(0, 0, math.pi)))),
        (COL['fish_orange'], lambda bm: prim_box(bm, (0.06, 0.02, 0.03), (0, 0.1, 0.04))),
        (COL['fish_blue'], lambda bm: prim_box(bm, (0.04, 0.02, 0.05), (0, -0.1, 0.03))),
    ])
    rig = make_armature('fish_rig', [
        ('root', (0, 0, 0), (0.15, 0, 0), None),
        ('tail', (-0.12, 0, 0.06), (-0.28, 0, 0.06), 'root'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n: 'tail' if co.x < -0.05 else 'root')
    _link_root(rig, mesh)
    bake_swim_cycle(rig, ['root', 'tail'])
    export_glb(os.path.join(OUT_DIR, 'fish.glb'), rig)


def build_seagull():
    mesh = build_colored_mesh('gull_body', [
        (COL['gull_white'], lambda bm: (prim_sphere(bm, (0, 0, 0.12), 0.13, 6, 5), prim_cylinder(bm, (0.05, 0, 0.1), 0.05, 0.1, 5))),
        (COL['gull_beak'], lambda bm: prim_cone(bm, (0.12, 0, 0.12), 0.05, 0.16, 4, rot=(0, math.pi * 0.5, 0))),
        (COL['gull_wing'], lambda bm: (
            prim_box(bm, (0.34, 0.14, 0.05), (0, 0, 0.12)),
            prim_box(bm, (0.34, 0.14, 0.05), (0, 0, -0.12)),
        )),
    ])
    rig = make_armature('gull_rig', [
        ('root', (0, 0, 0), (0, 0, 0.15), None),
        ('body', (0, 0, 0.05), (0, 0, 0.18), 'root'),
        ('wing.L', (0, 0.08, 0.1), (0.2, 0.22, 0.1), 'body'),
        ('wing.R', (0, -0.08, 0.1), (0.2, -0.22, 0.1), 'body'),
    ])
    assign_region_weights(mesh, rig, lambda co, names:
        'wing.L' if co.z > 0 and co.x > 0 else ('wing.R' if co.z < 0 and co.x > 0 else 'body'))
    _link_root(rig, mesh)
    bake_flap_cycle(rig, ['wing.L', 'wing.R'])
    export_glb(os.path.join(OUT_DIR, 'seagull.glb'), rig)


def build_whale():
    import math
    mesh = build_colored_mesh('whale_body', [
        (COL['whale_blue'], lambda bm: (
            prim_sphere(bm, (0, 0, 0.35), 0.55, 8, 6),
            prim_sphere(bm, (0.65, 0, 0.2), 0.28, 6, 5),
            prim_cone(bm, (-1.0, 0, 0.42), 0.12, 0.35, 5, rot=(0, 0, math.pi * 0.5)),
        )),
        (COL['whale_pale'], lambda bm: prim_sphere(bm, (0, 0, -0.08), 0.42, 7, 5)),
        (COL['spray'], lambda bm: (
            prim_cylinder(bm, (0, 0, 1.05), 0.08, 0.55, 6),
            prim_sphere(bm, (0, 0, 1.32), 0.12, 5, 4),
        )),
        (COL['whale_pale'], lambda bm: prim_box(bm, (0.18, 0.05, 0.12), (0.2, 0, 0.55))),
    ])
    rig = make_armature('whale_rig', [
        ('root', (0, 0, 0), (0.5, 0, 0), None),
        ('body', (0, 0, 0.2), (0, 0, 0.55), 'root'),
        ('tail', (-0.85, 0, 0.35), (-1.2, 0, 0.35), 'root'),
        ('spray', (0, 0, 0.7), (0, 0, 1.05), 'body'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n:
        'spray' if co.z > 0.85 else ('tail' if co.x < -0.5 else 'body'))
    _link_root(rig, mesh)
    bake_swim_cycle(rig, ['body', 'tail'])
    export_glb(os.path.join(OUT_DIR, 'whale.glb'), rig)


def build_octopus():
    def head(bm):
        prim_sphere(bm, (0, 0, 0.22), 0.22, 7, 6)
    def tentacles(bm):
        import math
        for i in range(8):
            a = (i / 8.0) * math.pi * 2
            x, y = math.cos(a) * 0.14, math.sin(a) * 0.14
            prim_cylinder(bm, (x, y, 0.05), 0.04, 0.42, 4, rot=(0.5, a, 0))
    mesh = build_colored_mesh('octo_body', [
        (COL['octo'], head),
        (COL['octo_dark'], tentacles),
    ])
    rig = make_armature('octo_rig', [
        ('root', (0, 0, 0), (0, 0, 0.2), None),
        ('head', (0, 0, 0.18), (0, 0, 0.35), 'root'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n: 'head' if co.z > 0.12 else 'root')
    _link_root(rig, mesh)
    bake_swim_cycle(rig, ['root', 'head'])
    export_glb(os.path.join(OUT_DIR, 'octopus.glb'), rig)


def build_wenyao():
    mesh = build_colored_mesh('wenyao_body', [
        (COL['wen_white'], lambda bm: prim_sphere(bm, (0, 0, 0.08), 0.18, 6, 5)),
        (COL['wen_blue'], lambda bm: (
            prim_sphere(bm, (0, 0, 0.08), 0.16, 6, 5),
            prim_cone(bm, (0.2, 0, 0.08), 0.06, 0.12, 4, rot=(0, 0, -math.pi * 0.5)),
        )),
        (COL['wen_wing'], lambda bm: (
            prim_box(bm, (0.32, 0.06, 0.22), (0, 0.12, 0.08)),
            prim_box(bm, (0.32, 0.06, 0.22), (0, -0.12, 0.08)),
        )),
    ])
    rig = make_armature('wenyao_rig', [
        ('root', (0, 0, 0), (0.2, 0, 0), None),
        ('body', (0, 0, 0.05), (0, 0, 0.15), 'root'),
        ('wing.L', (0, 0.1, 0.08), (0.28, 0.22, 0.08), 'body'),
        ('wing.R', (0, -0.1, 0.08), (0.28, -0.22, 0.08), 'body'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n:
        'wing.L' if co.y > 0 and co.x > 0.05 else ('wing.R' if co.y < 0 and co.x > 0.05 else 'body'))
    _link_root(rig, mesh)
    bake_flap_cycle(rig, ['wing.L', 'wing.R'])
    export_glb(os.path.join(OUT_DIR, 'wenyao.glb'), rig)


def build_chiru():
    mesh = build_colored_mesh('chiru_body', [
        (COL['chiru_red'], lambda bm: prim_sphere(bm, (0, 0, 0.1), 0.2, 7, 6)),
        (COL['chiru_gold'], lambda bm: (
            prim_sphere(bm, (0.08, 0.1, 0.12), 0.05, 4, 3),
            prim_sphere(bm, (-0.08, 0.1, 0.12), 0.05, 4, 3),
            prim_sphere(bm, (0, -0.1, 0.12), 0.05, 4, 3),
        )),
    ])
    rig = make_armature('chiru_rig', [
        ('root', (0, 0, 0), (0.15, 0, 0), None),
        ('body', (0, 0, 0.08), (0, 0, 0.2), 'root'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n: 'body')
    _link_root(rig, mesh)
    bake_swim_cycle(rig, ['root', 'body'])
    export_glb(os.path.join(OUT_DIR, 'chiru.glb'), rig)


def build_xuanwu():
    mesh = build_colored_mesh('xuanwu_body', [
        (COL['xuan_green'], lambda bm: (
            prim_sphere(bm, (0, 0, 0.18), 0.52, 8, 6),
            prim_sphere(bm, (0, 0, 0.52), 0.35, 7, 5),
        )),
        (COL['xuan_gold'], lambda bm: prim_box(bm, (0.48, 0.06, 0.48), (0, 0, 0.52))),
        (COL['xuan_moss'], lambda bm: (
            prim_sphere(bm, (0.12, 0.08, 0.42), 0.1, 4, 3),
            prim_sphere(bm, (-0.14, 0.12, 0.38), 0.08, 4, 3),
            prim_sphere(bm, (0, -0.1, 0.45), 0.09, 4, 3),
        )),
        (COL['xuan_snake'], lambda bm: (
            prim_sphere(bm, (0.22, 0.18, 0.72), 0.1, 5, 4),
            prim_cylinder(bm, (0.38, 0.28, 0.82), 0.06, 0.62, 5),
            prim_sphere(bm, (0.52, 0.35, 0.92), 0.07, 4, 3),
        )),
    ])
    rig = make_armature('xuanwu_rig', [
        ('root', (0, 0, 0), (0.3, 0, 0), None),
        ('shell', (0, 0, 0.2), (0, 0, 0.55), 'root'),
        ('snake', (0.2, 0.15, 0.55), (0.45, 0.25, 0.85), 'shell'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n:
        'snake' if co.z > 0.58 and co.x > 0.1 else 'shell')
    _link_root(rig, mesh)
    bake_swim_cycle(rig, ['shell', 'snake'])
    export_glb(os.path.join(OUT_DIR, 'xuanwu.glb'), rig)


def build_sea_serpent():
    import math
    def segments(bm):
        for i in range(6):
            x = i * 0.22 - 0.55
            prim_sphere(bm, (x, math.sin(i * 0.8) * 0.08, 0.08 + i * 0.02), 0.12 - i * 0.01, 6, 5)
        prim_cone(bm, (0.55, 0, 0.2), 0.1, 0.22, 5, rot=(0, 0, -math.pi * 0.5))
    mesh = build_colored_mesh('serpent_body', [
        (COL['serpent'], segments),
        (COL['serpent_fin'], lambda bm: prim_box(bm, (0.12, 0.04, 0.18), (0, 0, 0.2))),
    ])
    seg_bones = [('root', (0, 0, 0), (-0.4, 0, 0), None)]
    for i in range(6):
        parent = 'root' if i == 0 else f'seg{i - 1}'
        seg_bones.append((
            f'seg{i}',
            (i * 0.22 - 0.55, 0, 0.1),
            (i * 0.22 - 0.33, 0, 0.1),
            parent,
        ))
    rig = make_armature('serpent_rig', seg_bones)
    assign_region_weights(mesh, rig, lambda co, names: 'root')
    _link_root(rig, mesh)
    segs = ['seg{}'.format(i) for i in range(6)]
    bake_swim_cycle(rig, segs)
    export_glb(os.path.join(OUT_DIR, 'sea-serpent.glb'), rig)


def build_qingniao():
    mesh = build_colored_mesh('qingniao_body', [
        (COL['qing_blue'], lambda bm: (
            prim_sphere(bm, (0, 0, 0.12), 0.1, 6, 5),
            prim_cone(bm, (0.08, 0, 0.12), 0.035, 0.12, 4, rot=(0, math.pi * 0.5, 0)),
        )),
        (COL['qing_tail'], lambda bm: (
            prim_cylinder(bm, (-0.15, 0, 0.08), 0.02, 0.35, 4, rot=(0, 0, 0.3)),
            prim_sphere(bm, (-0.32, 0.05, 0.02), 0.035, 4, 3),
            prim_sphere(bm, (-0.38, 0.12, -0.02), 0.03, 4, 3),
        )),
    ])
    rig = make_armature('qingniao_rig', [
        ('root', (0, 0, 0), (0, 0, 0.12), None),
        ('body', (0, 0, 0.08), (0, 0, 0.18), 'root'),
        ('wing.L', (0, 0.06, 0.1), (0.12, 0.18, 0.1), 'body'),
        ('wing.R', (0, -0.06, 0.1), (0.12, -0.18, 0.1), 'body'),
        ('tail', (-0.08, 0, 0.08), (-0.35, 0.08, 0), 'body'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n:
        'tail' if co.x < -0.05 else ('wing.L' if co.y > 0 and co.x > 0 else (
            'wing.R' if co.y < 0 and co.x > 0 else 'body')))
    _link_root(rig, mesh)
    bake_flap_cycle(rig, ['wing.L', 'wing.R', 'tail'])
    export_glb(os.path.join(OUT_DIR, 'qingniao.glb'), rig)


def build_phoenix():
    mesh = build_colored_mesh('phoenix_body', [
        (COL['phoenix'], lambda bm: prim_sphere(bm, (0, 0, 0.12), 0.12, 6, 5)),
        (COL['phoenix_gold'], lambda bm: (
            prim_cone(bm, (-0.35, 0.05, 0.06), 0.06, 0.45, 4, rot=(0.2, 0, math.pi)),
            prim_cone(bm, (-0.35, -0.05, 0.06), 0.06, 0.45, 4, rot=(-0.2, 0, math.pi)),
            prim_box(bm, (0.28, 0.14, 0.04), (0, 0.1, 0.1)),
            prim_box(bm, (0.28, 0.14, 0.04), (0, -0.1, 0.1)),
        )),
    ])
    rig = make_armature('phoenix_rig', [
        ('root', (0, 0, 0), (0, 0, 0.15), None),
        ('body', (0, 0, 0.08), (0, 0, 0.2), 'root'),
        ('wing.L', (0, 0.08, 0.1), (0.22, 0.2, 0.1), 'body'),
        ('wing.R', (0, -0.08, 0.1), (0.22, -0.2, 0.1), 'body'),
        ('tail', (-0.1, 0, 0.08), (-0.4, 0, 0.05), 'body'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n:
        'tail' if co.x < -0.08 else ('wing.L' if co.y > 0 and co.x >= 0 else (
            'wing.R' if co.y < 0 else 'body')))
    _link_root(rig, mesh)
    bake_flap_cycle(rig, ['wing.L', 'wing.R', 'tail'])
    export_glb(os.path.join(OUT_DIR, 'phoenix.glb'), rig)


def build_wyvern():
    import math
    mesh = build_colored_mesh('wyvern_body', [
        (COL['wyvern_green'], lambda bm: (
            prim_sphere(bm, (0, 0, 0.14), 0.14, 6, 5),
            prim_cone(bm, (0.18, 0, 0.14), 0.08, 0.2, 5, rot=(0, 0, -math.pi * 0.5)),
            prim_cylinder(bm, (0, 0, 0.04), 0.06, 0.18, 5),
        )),
        (COL['wyvern_wing'], lambda bm: (
            prim_box(bm, (0.42, 0.22, 0.05), (0.05, 0.14, 0.12)),
            prim_box(bm, (0.42, 0.22, 0.05), (0.05, -0.14, 0.12)),
        )),
    ])
    rig = make_armature('wyvern_rig', [
        ('root', (0, 0, 0), (0.2, 0, 0), None),
        ('body', (0, 0, 0.08), (0, 0, 0.22), 'root'),
        ('wing.L', (0, 0.1, 0.14), (0.35, 0.28, 0.14), 'body'),
        ('wing.R', (0, -0.1, 0.14), (0.35, -0.28, 0.14), 'body'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n:
        'wing.L' if co.y > 0 and abs(co.x) < 0.35 else ('wing.R' if co.y < 0 else 'body'))
    _link_root(rig, mesh)
    bake_flap_cycle(rig, ['wing.L', 'wing.R'])
    export_glb(os.path.join(OUT_DIR, 'wyvern.glb'), rig)


def build_kunpeng():
    mesh = build_colored_mesh('kunpeng_body', [
        (COL['kun_shade'], lambda bm: (
            prim_sphere(bm, (0, 0, 0.2), 0.62, 8, 6),
            prim_sphere(bm, (0.62, 0.05, 0.18), 0.38, 6, 5),
            prim_cone(bm, (-1.0, 0, 0.22), 0.18, 0.42, 5, rot=(0, 0, math.pi * 0.5)),
        )),
        (COL['kun_cloud'], lambda bm: (
            prim_sphere(bm, (-0.35, 0.18, 0.35), 0.22, 5, 4),
            prim_sphere(bm, (0.15, -0.22, 0.42), 0.2, 5, 4),
            prim_sphere(bm, (0.42, 0.25, 0.32), 0.18, 5, 4),
            prim_sphere(bm, (-0.15, -0.15, 0.55), 0.16, 5, 4),
        )),
        (COL['kun_fin'], lambda bm: (
            prim_box(bm, (0.55, 0.08, 0.3), (0, 0.32, 0.12)),
            prim_box(bm, (0.55, 0.08, 0.3), (0, -0.32, 0.12)),
        )),
    ])
    rig = make_armature('kunpeng_rig', [
        ('root', (0, 0, 0), (0.4, 0, 0), None),
        ('body', (0, 0, 0.2), (0, 0, 0.45), 'root'),
        ('tail', (-0.55, 0, 0.2), (-0.85, 0, 0.18), 'root'),
    ])
    assign_region_weights(mesh, rig, lambda co, _n: 'tail' if co.x < -0.4 else 'body')
    _link_root(rig, mesh)
    bake_swim_cycle(rig, ['body', 'tail'])
    export_glb(os.path.join(OUT_DIR, 'kunpeng.glb'), rig)


BUILDERS = (
    build_fish,
    build_seagull,
    build_whale,
    build_octopus,
    build_wenyao,
    build_chiru,
    build_xuanwu,
    build_sea_serpent,
    build_qingniao,
    build_phoenix,
    build_wyvern,
    build_kunpeng,
)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for fn in BUILDERS:
        clear_all()
        fn()
    print('DONE sea-sky creatures →', OUT_DIR)


if __name__ == '__main__':
    main()