"""海空生物 v3（#226）：自 life-src 低多邊模型改色手繪貼圖，輸出 public/models/life/。

  nice -n 10 ~/.local/bin/blender -b --factory-startup -P tools/blender/build_island_life_v3.py
"""
from __future__ import annotations

import math
import os
import random
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, '..', '..'))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

import bmesh
import bpy
from mathutils import Euler, Matrix, Vector

import creature_common as creature_common_mod

from creature_common import (
    apply_creature_scale,
    bake_flap_cycle,
    bake_swim_cycle,
    clear_all,
    creature_image_material,
    export_glb,
    make_armature,
    prim_box,
    prim_cone,
    prim_cylinder,
    prim_ellipsoid,
    prim_sphere,
    rgba_hex,
    bind_mesh,
    build_colored_mesh,
    assign_region_weights,
    print_world_bounds,
    _rasterize_hand_painted,
)

SRC = os.path.join(_HERE, 'assets', 'life-src')
OUT_DIR = os.path.join(_REPO, 'server', 'src', 'main', 'resources', 'public', 'models', 'life')
TEX = 128

COL = {
    'fish_yellow': 0xf8d838,
    'fish_blue': 0x38b0f0,
    'gull_white': 0xf8f8ff,
    'gull_wing': 0xa0a8c0,
    'gull_beak': 0xf8b038,
    'whale_blue': 0x2a88e8,
    'whale_pale': 0xe8f4fc,
    'whale_dark': 0x1868c8,
    'spray': 0xc8f0ff,
    'octo': 0xf8a888,
    'octo_dark': 0xe87898,
    'wen_blue': 0x48b0f0,
    'wen_white': 0xf0f8ff,
    'wen_wing': 0x68a8e8,
    'xuan_green': 0x287838,
    'xuan_gold': 0xe8b838,
    'xuan_snake': 0x9ed848,
    'xuan_body': 0x5a8868,
    'kun_white': 0xf4f8ff,
    'kun_blue': 0xb8d8f8,
    'kun_cloud': 0xe8f4ff,
    'kun_fin': 0xd0e8ff,
    'black': 0x181818,
}


def _v3(c):
    r, g, b, a = rgba_hex(c)
    return (r, g, b, a)


def _select_hierarchy(obj):
    obj.select_set(True)
    for child in obj.children:
        _select_hierarchy(child)


def export_life(out_path, root, export_anim=True):
    """匯出 life GLB；JPEG 貼圖壓縮以符合 ≤300 KB。模型已正規化最長軸=1，世界尺寸由 JS LIFE_GLB_SCALE。"""
    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
    print_world_bounds(root, os.path.basename(out_path))
    bpy.ops.object.select_all(action='DESELECT')
    _select_hierarchy(root)
    bpy.context.view_layer.objects.active = root
    bpy.ops.export_scene.gltf(
        filepath=out_path,
        export_format='GLB',
        use_selection=True,
        export_yup=True,
        export_apply=True,
        export_texcoords=True,
        export_normals=True,
        export_materials='EXPORT',
        export_cameras=False,
        export_lights=False,
        export_animations=export_anim,
        export_nla_strips=export_anim,
        export_image_format='JPEG',
        export_jpeg_quality=78,
    )
    size = os.path.getsize(out_path)
    print(f'EXPORTED {out_path} bytes={size}')


def _import_glb(path):
    bpy.ops.import_scene.gltf(filepath=path)


def _drop_helpers():
    for ob in list(bpy.data.objects):
        if ob.type == 'MESH' and 'icosphere' in ob.name.lower():
            bpy.data.objects.remove(ob, do_unlink=True)


def _mesh_objs():
    return [ob for ob in bpy.data.objects if ob.type == 'MESH']


def _bounds_meshes():
    mn = Vector((1e9, 1e9, 1e9))
    mx = Vector((-1e9, -1e9, -1e9))
    for ob in _mesh_objs():
        for v in ob.data.vertices:
            co = ob.matrix_world @ v.co
            mn.x = min(mn.x, co.x)
            mn.y = min(mn.y, co.y)
            mn.z = min(mn.z, co.z)
            mx.x = max(mx.x, co.x)
            mx.y = max(mx.y, co.y)
            mx.z = max(mx.z, co.z)
    return mn, mx


def _top_level_objects():
    return [ob for ob in bpy.data.objects if ob.parent is None]


def _normalize_all(target_max=1.0):
    mn, mx = _bounds_meshes()
    dims = mx - mn
    longest = max(dims.x, dims.y, dims.z)
    if longest < 1e-6:
        return
    factor = target_max / longest
    tops = _top_level_objects()
    for ob in tops:
        ob.scale *= factor
    bpy.ops.object.select_all(action='DESELECT')
    for ob in tops:
        ob.select_set(True)
    if tops:
        bpy.context.view_layer.objects.active = tops[0]
        bpy.ops.object.transform_apply(scale=True)
    bpy.ops.object.select_all(action='DESELECT')


def _tri_count(ob):
    return sum(len(p.vertices) - 2 for p in ob.data.polygons)


def _decimate_obj(ob, max_tris):
    tris = _tri_count(ob)
    if tris <= max_tris:
        return
    ratio = max_tris / tris
    mod = ob.modifiers.new('Decimate', 'DECIMATE')
    mod.ratio = min(0.98, ratio * 0.98)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    ob.select_set(False)


def _retexture_mesh(ob, paint_fn, mat_name=None):
    """依 paint_fn(co, normal) → Vector RGBA 產生手繪貼圖材質。"""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bm.faces.ensure_lookup_table()
    bm.normal_update()
    color_lay = bm.loops.layers.color.new('COLOR_0')
    for face in bm.faces:
        for loop in face.loops:
            co = loop.vert.co
            n = loop.vert.normal
            loop[color_lay] = paint_fn(co, n)
    uv_lay = bm.loops.layers.uv.new('UVMap')
    xs = [v.co.x for v in bm.verts]
    zs = [v.co.z for v in bm.verts]
    min_x, max_x = min(xs), max(xs)
    min_z, max_z = min(zs), max(zs)
    dx = max(max_x - min_x, 1e-5)
    dz = max(max_z - min_z, 1e-5)
    for face in bm.faces:
        for loop in face.loops:
            v = loop.vert
            loop[uv_lay].uv = ((v.co.x - min_x) / dx, (v.co.z - min_z) / dz)
    random.seed(int(abs(ob.name.__hash__())) % 9973)
    tex_img = _rasterize_hand_painted(bm, color_lay, uv_lay, TEX)
    mat = creature_image_material(tex_img, mat_name or (ob.name + '_mat'))
    me = bpy.data.meshes.new(ob.data.name + '_retex')
    bm.to_mesh(me)
    bm.free()
    old = ob.data
    ob.data = me
    ob.data.materials.clear()
    ob.data.materials.append(mat)
    if old.users == 0:
        bpy.data.meshes.remove(old)


def _find_armature():
    for ob in bpy.data.objects:
        if ob.type == 'ARMATURE':
            return ob
    return None


def _root_for_export():
    arm = _find_armature()
    if arm:
        return arm
    meshes = _mesh_objs()
    if len(meshes) == 1:
        return meshes[0]
    empty = bpy.data.objects.new('life_root', None)
    bpy.context.scene.collection.objects.link(empty)
    for ob in list(_mesh_objs()):
        ob.parent = empty
    return empty


def _tex_budget():
    creature_common_mod.TEXTURE_SIZE = TEX


def _lay_horizon_y(root, euler=(math.pi / 2, 0, math.pi / 2)):
    """Quaternius 鯨／魚沿 Z 躺平：頭朝 +X、背朝 +Y（export Y-up）。"""
    root.rotation_euler = Euler(euler)
    bpy.context.view_layer.objects.active = root
    root.select_set(True)
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=False)
    root.select_set(False)


def build_fish():
    _tex_budget()
    clear_all()
    _import_glb(os.path.join(SRC, 'fish-quaternius.glb'))
    _drop_helpers()
    _normalize_all(1.0)
    for ob in _mesh_objs():
        if 'fish' in ob.name.lower():
            _decimate_obj(ob, 620)

            def paint(co, _n):
                return _v3(COL['fish_yellow'])

            _retexture_mesh(ob, paint, 'fish_mat')
    root = _find_armature() or _root_for_export()
    if root:
        _lay_horizon_y(root, (0, 0, math.pi / 2))
    export_life(os.path.join(OUT_DIR, 'fish.glb'), root)


def build_seagull():
    _tex_budget()
    clear_all()
    _import_glb(os.path.join(SRC, 'seagull-flying-polygoogle.glb'))
    _drop_helpers()
    _normalize_all(1.0)
    for ob in _mesh_objs():
        _decimate_obj(ob, 420)

        def paint(co, n):
            if co.z > 0.08 and abs(co.x) > 0.05:
                return _v3(COL['gull_wing'])
            if co.x > 0.14 and co.z > -0.05:
                return _v3(COL['gull_beak'])
            return _v3(COL['gull_white'])

        _retexture_mesh(ob, paint, 'gull_mat')
    root = _root_for_export()
    export_life(os.path.join(OUT_DIR, 'seagull.glb'), root, export_anim=False)


def build_whale():
    _tex_budget()
    clear_all()
    _import_glb(os.path.join(SRC, 'whale-quaternius.glb'))
    _drop_helpers()
    _normalize_all(1.0)
    for ob in _mesh_objs():
        if 'whale' in ob.name.lower():
            _decimate_obj(ob, 520)

            def paint(co, _n):
                if co.z < -0.02 or _n.z < -0.2:
                    return _v3(COL['whale_pale'])
                if co.z > 0.12:
                    return _v3(COL['whale_dark'])
                return _v3(COL['whale_blue'])

            _retexture_mesh(ob, paint, 'whale_mat')
    root = _find_armature() or _root_for_export()
    if root:
        mn, mx = _bounds_meshes()
        body_long = max(mx.x - mn.x, mx.y - mn.y, mx.z - mn.z)
        if body_long > 1.02:
            _normalize_all(1.0)
        for ob in _mesh_objs():
            if 'whale' in ob.name.lower():
                ob.rotation_euler = Euler((math.pi / 2, 0, 0))
        bpy.ops.object.select_all(action='SELECT')
        bpy.ops.object.transform_apply(rotation=True, location=False, scale=False)
        bpy.ops.object.select_all(action='DESELECT')
    spray = build_colored_mesh('whale_spray', [
        (COL['spray'], lambda bm: (
            prim_cylinder(bm, (0.38, 0, 0.18), 0.09, 0.55, 6),
            prim_cylinder(bm, (0.38, 0, 0.52), 0.05, 1.1, 5),
            prim_ellipsoid(bm, (0.38, 0, 1.05), (0.18, 0.18, 0.14), 6, 5),
            prim_sphere(bm, (0.32, 0.08, 1.12), 0.07, 5, 4),
            prim_sphere(bm, (0.44, -0.06, 1.1), 0.06, 5, 4),
        )),
    ])
    spray.parent = root
    bpy.context.scene.collection.objects.link(spray)
    export_life(os.path.join(OUT_DIR, 'whale.glb'), root)


def build_octopus():
    _tex_budget()
    clear_all()
    _import_glb(os.path.join(SRC, 'octopus-polygoogle.glb'))
    _normalize_all(1.0)
    for ob in _mesh_objs():
        _decimate_obj(ob, 760)

        def paint(co, _n):
            if co.z < -0.12 and abs(co.x) + abs(co.y) > 0.2:
                return _v3(COL['octo_dark'])
            return _v3(COL['octo'])

        _retexture_mesh(ob, paint, 'octo_mat')
    eyes = build_colored_mesh('octo_eyes', [
        (COL['black'], lambda bm: (
            prim_ellipsoid(bm, (-0.1, 0.1, 0.22), (0.05, 0.06, 0.05), 5, 4),
            prim_ellipsoid(bm, (0.1, 0.1, 0.22), (0.05, 0.06, 0.05), 5, 4),
        )),
        (0xf0f0f8, lambda bm: (
            prim_ellipsoid(bm, (-0.1, 0.11, 0.26), (0.018, 0.022, 0.01), 4, 3),
            prim_ellipsoid(bm, (0.1, 0.11, 0.26), (0.018, 0.022, 0.01), 4, 3),
        )),
    ])
    root = _root_for_export()
    bpy.context.scene.collection.objects.link(eyes)
    eyes.parent = root
    export_life(os.path.join(OUT_DIR, 'octopus.glb'), root)


def _stripe_fish(co):
    band = int(abs(co.y) * 14 + co.x * 5) % 3
    return _v3(COL['wen_white']) if band == 0 else _v3(COL['wen_blue'])


def build_wenyao():
    _tex_budget()
    clear_all()
    body = build_colored_mesh('wenyao_body', [
        (COL['wen_blue'], lambda bm: (
            prim_ellipsoid(bm, (0.02, 0, 0), (0.22, 0.09, 0.08), 6, 5),
            prim_cone(bm, (-0.26, 0, 0), 0.055, 0.12, 4, rot=(0, 0, math.pi)),
        )),
        (COL['wen_white'], lambda bm: prim_box(bm, (0.08, 0.02, 0.03), (0.02, 0.08, 0))),
    ])
    bpy.context.scene.collection.objects.link(body)
    _import_glb(os.path.join(SRC, 'gull-flying-polygoogle.glb'))
    wing_meshes = [ob for ob in _mesh_objs() if ob is not body]
    _normalize_selection(wing_meshes, 0.42)
    wing_parts = []
    for ob in wing_meshes:
        _decimate_obj(ob, 280)

        def paint(co, _n):
            if abs(co.y) > 0.06 and co.x > -0.05:
                return _v3(COL['wen_wing'])
            return _stripe_fish(co)

        _retexture_mesh(ob, paint, 'wen_wing_mat')
        wing_parts.append(ob)
    for i, ob in enumerate(wing_parts):
        side = 1 if i % 2 == 0 else -1
        ob.location = Vector((0.05, side * 0.14, 0.02))
        ob.rotation_euler = Euler((0.15, side * 0.25, side * 0.35))
        ob.scale = Vector((0.85, side * 0.9, 0.75))
    rig = make_armature('wenyao_rig', [
        ('root', (0, 0, 0), (0.2, 0, 0), None),
        ('body', (0, 0, 0.02), (0.12, 0, 0.05), 'root'),
        ('wing.L', (0.02, 0.12, 0.02), (0.24, 0.28, 0.04), 'body'),
        ('wing.R', (0.02, -0.12, 0.02), (0.24, -0.28, 0.04), 'body'),
        ('tail', (-0.16, 0, 0), (-0.32, 0, 0), 'root'),
    ])
    bpy.context.scene.collection.objects.link(rig)
    body.parent = rig
    bind_mesh(body, rig)
    assign_region_weights(body, rig, lambda co, _n:
        'wing.L' if co.y > 0.05 and co.x > 0 else ('wing.R' if co.y < -0.05 and co.x > 0 else ('tail' if co.x < -0.08 else 'body')))
    for ob in wing_parts:
        ob.parent = rig
    _normalize_all(1.0)
    bake_flap_cycle(rig, ['wing.L', 'wing.R', 'tail'])
    export_life(os.path.join(OUT_DIR, 'wenyao.glb'), rig)


def _bounds_of(objs):
    mn = Vector((1e9, 1e9, 1e9))
    mx = Vector((-1e9, -1e9, -1e9))
    for ob in objs:
        if ob.type != 'MESH':
            continue
        for v in ob.data.vertices:
            co = ob.matrix_world @ v.co
            mn.x = min(mn.x, co.x)
            mn.y = min(mn.y, co.y)
            mn.z = min(mn.z, co.z)
            mx.x = max(mx.x, co.x)
            mx.y = max(mx.y, co.y)
            mx.z = max(mx.z, co.z)
    return mn, mx


def _normalize_selection(objs, target_max):
    sel = [ob for ob in objs if ob.type == 'MESH']
    if not sel:
        return
    mn, mx = _bounds_of(sel)
    longest = max(mx.x - mn.x, mx.y - mn.y, mx.z - mn.z)
    if longest < 1e-6:
        return
    factor = target_max / longest
    roots = set()
    for ob in sel:
        root = ob
        while root.parent:
            root = root.parent
        roots.add(root)
    for root in roots:
        root.scale *= factor
    bpy.ops.object.select_all(action='DESELECT')
    for root in roots:
        root.select_set(True)
    bpy.context.view_layer.objects.active = next(iter(roots))
    bpy.ops.object.transform_apply(scale=True)
    bpy.ops.object.select_all(action='DESELECT')


def _hex_gold(co, scale):
    u = co.x * scale
    v = (co.y * 0.866 + co.x * 0.5) * scale
    qi = int(round(u))
    ri = int(round(v - u / 2))
    ci = int((u - qi) * 3 + (v - ri) * 3)
    return ci % 5 == 0 or (qi + ri) % 4 == 0


def build_xuanwu():
    _tex_budget()
    clear_all()
    _import_glb(os.path.join(SRC, 'turtle-sea-polygoogle.glb'))
    turtle_meshes = _mesh_objs()
    _normalize_selection(turtle_meshes, 1.0)
    for ob in turtle_meshes:
        _decimate_obj(ob, 1100)

        def paint_turtle(co, n):
            if n.z > 0.45:
                if _hex_gold(co, 5.8):
                    return _v3(COL['xuan_gold'])
                return _v3(COL['xuan_green'])
            return _v3(COL['xuan_body'])

        _retexture_mesh(ob, paint_turtle, 'turtle_mat')
    _import_glb(os.path.join(SRC, 'snake-long-polygoogle.glb'))
    snake_meshes = [ob for ob in _mesh_objs() if ob not in turtle_meshes]
    _normalize_selection(snake_meshes, 0.95)
    for ob in snake_meshes:
        ob.location = Vector((0.05, 0, 0.22))
        ob.rotation_euler = Euler((0.55, 0.2, 1.05))
        _decimate_obj(ob, 300)

        def paint_snake(_co, n):
            return _v3(COL['xuan_snake'])

        _retexture_mesh(ob, paint_snake, 'snake_mat')
    mn, mx = _bounds_meshes()
    longest = max(mx.x - mn.x, mx.y - mn.y, mx.z - mn.z)
    if longest > 1e-6 and abs(longest - 1.0) > 0.02:
        _normalize_all(1.0)
    empty = bpy.data.objects.new('xuanwu_root', None)
    bpy.context.scene.collection.objects.link(empty)
    for ob in _mesh_objs():
        ob.parent = empty
    export_life(os.path.join(OUT_DIR, 'xuanwu.glb'), empty)


def build_kunpeng():
    _tex_budget()
    clear_all()
    _import_glb(os.path.join(SRC, 'whale-quaternius.glb'))
    _drop_helpers()
    _normalize_all(1.0)
    for ob in _mesh_objs():
        if 'whale' not in ob.name.lower():
            continue
        _decimate_obj(ob, 480)

        def paint(co, _n):
            t = (co.z + 0.15) / 0.35
            t = max(0, min(1, t))
            w = _v3(COL['kun_white'])
            b = _v3(COL['kun_blue'])
            return Vector((w[0] * (1 - t) + b[0] * t, w[1] * (1 - t) + b[1] * t, w[2] * (1 - t) + b[2] * t, 1))

        _retexture_mesh(ob, paint, 'kun_body')
    clouds = build_colored_mesh('kun_clouds', [
        (COL['kun_cloud'], lambda bm: (
            prim_ellipsoid(bm, (0.08, 0.06, 0.12), (0.14, 0.1, 0.11), 5, 4),
            prim_sphere(bm, (-0.18, 0.1, 0.14), 0.11, 4, 3),
            prim_sphere(bm, (0.22, -0.12, 0.16), 0.1, 4, 3),
            prim_sphere(bm, (-0.42, 0.04, 0.1), 0.12, 4, 3),
            prim_sphere(bm, (-0.62, -0.06, 0.08), 0.11, 4, 3),
            prim_sphere(bm, (-0.85, 0.08, 0.12), 0.13, 4, 3),
            prim_sphere(bm, (-1.02, -0.1, 0.1), 0.12, 4, 3),
            prim_sphere(bm, (-1.18, 0.02, 0.09), 0.11, 4, 3),
        )),
        (COL['kun_fin'], lambda bm: (
            prim_box(bm, (0.2, 0.06, 0.26), (0.18, 0.32, -0.02), rot=(0.42, 0.15, 0.08)),
            prim_box(bm, (0.2, 0.06, 0.26), (0.18, -0.32, -0.02), rot=(-0.42, -0.15, -0.08)),
            prim_box(bm, (0.38, 0.05, 0.2), (-0.75, 0, 0.05)),
            prim_box(bm, (0.12, 0.04, 0.32), (-0.92, 0.04, 0.02), rot=(0.2, 0.1, 0.15)),
        )),
    ])
    root = _find_armature()
    if root is None:
        root = make_armature('kun_rig', [
            ('root', (0, 0, 0), (0.4, 0, 0), None),
            ('body', (0, 0, 0.2), (0, 0, 0.45), 'root'),
            ('tail', (-0.55, 0, 0.18), (-0.85, 0, 0.16), 'root'),
        ])
        bpy.context.scene.collection.objects.link(root)
    clouds.parent = root
    bpy.context.scene.collection.objects.link(clouds)
    for ob in _mesh_objs():
        if 'whale' in ob.name.lower() and ob.parent is None:
            ob.parent = root
    if root.type == 'ARMATURE':
        names = [b.name for b in root.data.bones]
        swim = []
        for want in ('Tail', 'Spine2', 'Spine1', 'Root'):
            if want in names:
                swim.append(want)
        if len(swim) < 2:
            swim = names[: min(2, len(names))]
        if swim:
            bake_swim_cycle(root, swim)
        root.rotation_euler = Euler((math.pi / 2, 0, 0))
        bpy.context.view_layer.objects.active = root
        root.select_set(True)
        bpy.ops.object.transform_apply(rotation=True, location=False, scale=False)
        root.select_set(False)
    tops = _top_level_objects()
    if tops:
        bpy.ops.object.select_all(action='DESELECT')
        for ob in tops:
            ob.select_set(True)
        bpy.context.view_layer.objects.active = tops[0]
        bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    _normalize_all(1.0)
    export_life(os.path.join(OUT_DIR, 'kunpeng.glb'), root)


REMOVE_OUTPUTS = (
    'chiru.glb',
    'sea-serpent.glb',
    'qingniao.glb',
    'phoenix.glb',
    'wyvern.glb',
)

BUILDERS = (
    build_fish,
    build_seagull,
    build_whale,
    build_octopus,
    build_wenyao,
    build_xuanwu,
    build_kunpeng,
)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for fn in BUILDERS:
        try:
            fn()
        except Exception as ex:
            print('FAIL', fn.__name__, ex)
            raise
    for name in REMOVE_OUTPUTS:
        path = os.path.join(OUT_DIR, name)
        if os.path.isfile(path):
            os.remove(path)
            print('REMOVED', path)
    total = 0
    for f in os.listdir(OUT_DIR):
        if f.endswith('.glb'):
            total += os.path.getsize(os.path.join(OUT_DIR, f))
    print('TOTAL life GLB bytes', total)
    print('DONE island-life v3 →', OUT_DIR)


if __name__ == '__main__':
    main()