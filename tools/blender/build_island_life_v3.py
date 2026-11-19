"""海空生物 v3（#226 r3）：烘焙節點節點變換、純色材質、無骨架／無貼圖、無 GLB 動畫。

  nice -n 10 ~/.local/bin/blender -b --factory-startup -P tools/blender/build_island_life_v3.py
"""
from __future__ import annotations

import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.abspath(os.path.join(_HERE, '..', '..'))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

import bmesh
import bpy
from mathutils import Euler, Vector

from creature_common import (
    clear_all,
    creature_solid_material,
    prim_box,
    prim_cone,
    prim_cylinder,
    prim_ellipsoid,
    prim_sphere,
    print_world_bounds,
    rgba_hex,
)

SRC = os.path.join(_HERE, 'assets', 'life-src')
OUT_DIR = os.path.join(_REPO, 'server', 'src', 'main', 'resources', 'public', 'models', 'life')

COL = {
    'fish_yellow': 0xF5C542,
    'fish_back': 0xD89A1E,
    'fish_eye': 0x1A2830,
    'gull_white': 0xF8F8FF,
    'gull_wing': 0xA0A8C0,
    'gull_beak': 0xF8B038,
    'whale_back': 0x3E6E9E,
    'whale_belly': 0xDCE6EC,
    'whale_fin': 0x3A6490,
    'spray': 0xF2FAFF,
    'octo': 0xF08A8A,
    'octo_sucker': 0xF8C8C8,
    'wen_blue': 0x48B0F0,
    'wen_white': 0xF0F8FF,
    'wen_wing': 0x68A8E8,
    'xuan_shell': 0x2F5D3A,
    'xuan_gold': 0xC9A23A,
    'xuan_body': 0x7C8F6A,
    'snake': 0x3FAF7A,
    'snake_belly': 0xF0E8B8,
    'kun_white': 0xEAF4FB,
    'kun_blue': 0xBFD9EE,
    'kun_cloud': 0xF8FCFF,
    'kun_fin': 0xC8DCEF,
    'black': 0x181818,
}


def _import_glb(path):
    bpy.ops.import_scene.gltf(filepath=path)


def _mesh_objs():
    return [ob for ob in bpy.data.objects if ob.type == 'MESH']


def _top_level_objects():
    return [ob for ob in bpy.data.objects if ob.parent is None]


def _bounds_meshes(objs=None):
    mn = Vector((1e9, 1e9, 1e9))
    mx = Vector((-1e9, -1e9, -1e9))
    for ob in objs or _mesh_objs():
        for v in ob.data.vertices:
            co = ob.matrix_world @ v.co
            mn.x = min(mn.x, co.x)
            mn.y = min(mn.y, co.y)
            mn.z = min(mn.z, co.z)
            mx.x = max(mx.x, co.x)
            mx.y = max(mx.y, co.y)
            mx.z = max(mx.z, co.z)
    return mn, mx


def _tri_count_scene():
    n = 0
    for ob in _mesh_objs():
        n += sum(len(p.vertices) - 2 for p in ob.data.polygons)
    return n


def _drop_helpers():
    for ob in list(bpy.data.objects):
        if ob.type == 'MESH' and 'icosphere' in ob.name.lower():
            bpy.data.objects.remove(ob, do_unlink=True)


def _flatten_bake_meshes():
    """parent clear keep transform → 刪骨架 → apply all transforms。"""
    meshes = _mesh_objs()
    if not meshes:
        return
    bpy.ops.object.select_all(action='DESELECT')
    for ob in meshes:
        ob.select_set(True)
    bpy.ops.object.parent_clear(type='CLEAR_KEEP_TRANSFORM')
    for ob in [o for o in bpy.data.objects if o.type == 'ARMATURE']:
        bpy.data.objects.remove(ob, do_unlink=True)
    bpy.ops.object.select_all(action='DESELECT')
    meshes = _mesh_objs()
    for ob in meshes:
        ob.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    bpy.ops.object.select_all(action='DESELECT')


def _rotate_scene_euler(euler):
    if not euler or euler == (0.0, 0.0, 0.0):
        return
    pivot = bpy.data.objects.new('_orient_pivot', None)
    bpy.context.scene.collection.objects.link(pivot)
    meshes = list(_mesh_objs())
    for ob in meshes:
        ob.parent = pivot
    pivot.rotation_euler = Euler(euler)
    bpy.context.view_layer.objects.active = pivot
    pivot.select_set(True)
    bpy.ops.object.transform_apply(rotation=True)
    for ob in meshes:
        ob.parent = None
    bpy.data.objects.remove(pivot, do_unlink=True)
    bpy.ops.object.select_all(action='DESELECT')


def _normalize_longest(target=1.0):
    mn, mx = _bounds_meshes()
    dims = mx - mn
    longest = max(dims.x, dims.y, dims.z)
    if longest < 1e-6:
        return
    factor = target / longest
    root = bpy.data.objects.get('Root')
    if root:
        root.scale = Vector((factor, factor, factor))
        bpy.context.view_layer.objects.active = root
        root.select_set(True)
        bpy.ops.object.transform_apply(scale=True)
        root.select_set(False)
    else:
        for ob in _mesh_objs():
            ob.scale *= factor
        bpy.ops.object.select_all(action='SELECT')
        bpy.ops.object.transform_apply(scale=True)
        bpy.ops.object.select_all(action='DESELECT')


def _orient_long_axis_z():
    """最長軸對齊 Blender Y（export_yup 後為 glTF 水平 ±Z）。"""
    mn, mx = _bounds_meshes()
    dims = mx - mn
    ax = sorted([('x', dims.x), ('y', dims.y), ('z', dims.z)], key=lambda t: t[1], reverse=True)
    long_axis = ax[0][0]
    if long_axis == 'x':
        _rotate_scene_euler((0.0, math.pi / 2.0, 0.0))
    elif long_axis == 'z':
        _rotate_scene_euler((math.pi / 2.0, 0.0, 0.0))


def _scale_meshes(objs, factor):
    for ob in objs:
        ob.scale *= factor
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.transform_apply(scale=True)
        ob.select_set(False)


def _center_at_origin():
    mn, mx = _bounds_meshes()
    center = (mn + mx) * 0.5
    for ob in _mesh_objs():
        ob.location -= center
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
    bpy.ops.object.select_all(action='DESELECT')


def _make_export_root():
    meshes = _mesh_objs()
    root = bpy.data.objects.new('Root', None)
    bpy.context.scene.collection.objects.link(root)
    for ob in list(meshes):
        ob.parent = root
    return root


def _decimate_scene(max_tris):
    total = _tri_count_scene()
    if total <= max_tris:
        return
    ratio = (max_tris / total) * 0.98
    for ob in _mesh_objs():
        mod = ob.modifiers.new('Decimate', 'DECIMATE')
        mod.ratio = min(0.99, ratio)
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.modifier_apply(modifier=mod.name)
        ob.select_set(False)


def _face_center(face):
    acc = Vector((0.0, 0.0, 0.0))
    for v in face.verts:
        acc += v.co
    return acc / len(face.verts)


def _split_src_mesh(src, specs, parent):
    """specs: (name, hex, alpha, face_test)；依序互斥分割，最後一項可吃剩餘面。"""
    bm = bmesh.new()
    bm.from_mesh(src.data)
    bm.faces.ensure_lookup_table()
    bm.verts.ensure_lookup_table()
    bm.normal_update()
    out = []
    for idx, (name, hx, alpha, test) in enumerate(specs):
        is_last = idx == len(specs) - 1
        keep = [f for f in bm.faces if (test(f) if not is_last else True)]
        if not keep:
            continue
        sub = bmesh.new()
        for f in keep:
            verts = [sub.verts.new(v.co) for v in f.verts]
            sub.verts.ensure_lookup_table()
            try:
                sub.faces.new(verts)
            except ValueError:
                pass
        bmesh.ops.delete(bm, geom=keep, context='FACES')
        if not sub.faces:
            sub.free()
            continue
        me = bpy.data.meshes.new(name)
        sub.to_mesh(me)
        sub.free()
        ob = bpy.data.objects.new(name, me)
        mat = creature_solid_material(hx, name + '_mat', alpha=alpha)
        ob.data.materials.append(mat)
        bpy.context.scene.collection.objects.link(ob)
        ob.parent = parent
        ob.matrix_world = src.matrix_world.copy()
        out.append(ob)
    bm.free()
    bpy.data.objects.remove(src, do_unlink=True)
    return out


def _solid_obj(name, hx, builder, parent=None, alpha=1.0):
    bm = bmesh.new()
    builder(bm)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bm.normal_update()
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    mat = creature_solid_material(hx, name + '_mat', alpha=alpha)
    ob.data.materials.append(mat)
    bpy.context.scene.collection.objects.link(ob)
    if parent:
        ob.parent = parent
    return ob


def _bake_meshes_identity(root):
    """套用世界矩陣到頂點，節點 TRS 歸一（無 Armature／無縮放）。"""
    meshes = []
    stack = [root]
    while stack:
        o = stack.pop()
        if o.type == 'MESH':
            meshes.append(o)
        stack.extend(list(o.children))
    bpy.ops.object.select_all(action='DESELECT')
    for ob in meshes:
        mw = ob.matrix_world.copy()
        ob.parent = None
        ob.matrix_world = mw
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        ob.select_set(False)
        ob.parent = root
    root.location = (0.0, 0.0, 0.0)
    root.rotation_euler = (0.0, 0.0, 0.0)
    root.scale = (1.0, 1.0, 1.0)


def export_life(out_path, root):
    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
    _bake_meshes_identity(root)
    print_world_bounds(root, os.path.basename(out_path))
    bpy.ops.object.select_all(action='DESELECT')

    def _sel_hierarchy(obj):
        obj.select_set(True)
        for ch in obj.children:
            _sel_hierarchy(ch)

    _sel_hierarchy(root)
    bpy.context.view_layer.objects.active = root
    kw = dict(
        filepath=out_path,
        export_format='GLB',
        use_selection=True,
        export_yup=True,
        export_apply=True,
        export_texcoords=False,
        export_normals=True,
        export_materials='EXPORT',
        export_cameras=False,
        export_lights=False,
        export_animations=False,
        export_nla_strips=False,
    )
    try:
        bpy.ops.export_scene.gltf(**kw, export_image_format='NONE')
    except TypeError:
        bpy.ops.export_scene.gltf(**kw)
    print(f'EXPORTED {out_path} bytes={os.path.getsize(out_path)} tris={_tri_count_scene()}')


def build_fish():
    clear_all()
    _import_glb(os.path.join(SRC, 'fish-quaternius.glb'))
    _drop_helpers()
    _flatten_bake_meshes()
    _orient_long_axis_z()
    _center_at_origin()
    root = _make_export_root()
    for ob in list(_mesh_objs()):
        if ob.parent != root and ob.type == 'MESH':
            ob.parent = root
    meshes = [ob for ob in root.children if ob.type == 'MESH']
    for src in list(meshes):
        _split_src_mesh(
            src,
            [
                ('fish_eye', COL['fish_eye'], 1.0, lambda f: _face_center(f).x > 0.28 and _face_center(f).y > -0.02),
                ('fish_back', COL['fish_back'], 1.0, lambda f: f.normal.y > 0.35 or _face_center(f).y > 0.06),
                ('fish_body', COL['fish_yellow'], 1.0, lambda f: True),
            ],
            root,
        )
    _normalize_longest(1.0)
    _decimate_scene(520)
    export_life(os.path.join(OUT_DIR, 'fish.glb'), root)


def build_seagull():
    clear_all()
    _import_glb(os.path.join(SRC, 'seagull-flying-polygoogle.glb'))
    _drop_helpers()
    _flatten_bake_meshes()
    _center_at_origin()
    root = _make_export_root()
    for ob in list(_mesh_objs()):
        if ob.parent != root:
            ob.parent = root
    for src in [c for c in root.children if c.type == 'MESH']:
        _split_src_mesh(
            src,
            [
                ('gull_beak', COL['gull_beak'], 1.0, lambda f: _face_center(f).x > 0.2 and _face_center(f).z > -0.04),
                ('gull_wing', COL['gull_wing'], 1.0, lambda f: abs(_face_center(f).z) > 0.08 and abs(_face_center(f).x) < 0.18),
                ('gull_body', COL['gull_white'], 1.0, lambda f: True),
            ],
            root,
        )
    _normalize_longest(1.0)
    _decimate_scene(400)
    mn, mx = _bounds_meshes()
    span = max(mx.x - mn.x, mx.y - mn.y, mx.z - mn.z)
    if span > 1.05 or span < 0.95:
        _normalize_longest(1.0)
    export_life(os.path.join(OUT_DIR, 'seagull.glb'), root)


def build_whale():
    clear_all()
    _import_glb(os.path.join(SRC, 'whale-quaternius.glb'))
    _drop_helpers()
    _flatten_bake_meshes()
    _orient_long_axis_z()
    _center_at_origin()
    root = _make_export_root()
    for ob in list(_mesh_objs()):
        if ob.parent != root:
            ob.parent = root
    for src in [c for c in root.children if c.type == 'MESH']:
        _split_src_mesh(
            src,
            [
                ('whale_belly', COL['whale_belly'], 1.0, lambda f: f.normal.y < -0.15 or _face_center(f).y < -0.04),
                ('whale_fin', COL['whale_fin'], 1.0, lambda f: abs(_face_center(f).x) > 0.25 and abs(_face_center(f).y) < 0.08),
                ('whale_back', COL['whale_back'], 1.0, lambda f: True),
            ],
            root,
        )
    spray_parent = root
    _solid_obj(
        'whale_spray',
        COL['spray'],
        lambda bm: (
            prim_cylinder(bm, (0.42, 0.0, 0.22), 0.11, 0.55, 8),
            prim_cylinder(bm, (0.42, 0.0, 0.58), 0.07, 1.2, 7),
            prim_ellipsoid(bm, (0.42, 0.0, 1.15), (0.22, 0.2, 0.18), 7, 5),
            prim_sphere(bm, (0.34, 0.08, 1.22), 0.08, 5, 4),
            prim_sphere(bm, (0.5, -0.05, 1.18), 0.07, 5, 4),
        ),
        parent=spray_parent,
        alpha=0.55,
    )
    _normalize_longest(1.0)
    _decimate_scene(620)
    export_life(os.path.join(OUT_DIR, 'whale.glb'), root)


def build_octopus():
    clear_all()
    _import_glb(os.path.join(SRC, 'octopus-polygoogle.glb'))
    _flatten_bake_meshes()
    _center_at_origin()
    root = _make_export_root()
    for ob in list(_mesh_objs()):
        if ob.parent != root:
            ob.parent = root
    for src in [c for c in root.children if c.type == 'MESH']:
        _split_src_mesh(
            src,
            [
                ('octo_sucker', COL['octo_sucker'], 1.0, lambda f: _face_center(f).z < -0.1 and (_face_center(f).x ** 2 + _face_center(f).y ** 2) > 0.04),
                ('octo_body', COL['octo'], 1.0, lambda f: True),
            ],
            root,
        )
    _solid_obj(
        'octo_eye_l',
        COL['black'],
        lambda bm: prim_ellipsoid(bm, (-0.09, 0.08, 0.2), (0.05, 0.06, 0.05), 5, 4),
        parent=root,
    )
    _solid_obj(
        'octo_eye_r',
        COL['black'],
        lambda bm: prim_ellipsoid(bm, (0.09, 0.08, 0.2), (0.05, 0.06, 0.05), 5, 4),
        parent=root,
    )
    _normalize_longest(1.0)
    _decimate_scene(720)
    export_life(os.path.join(OUT_DIR, 'octopus.glb'), root)


def build_wenyao():
    clear_all()
    _solid_obj(
        'wen_body',
        COL['wen_blue'],
        lambda bm: (
            prim_ellipsoid(bm, (0.02, 0.0, 0.0), (0.22, 0.09, 0.08), 6, 5),
            prim_cone(bm, (-0.26, 0.0, 0.0), 0.05, 0.11, 4, rot=(0, 0, math.pi)),
        ),
    )
    _solid_obj(
        'wen_wing_l',
        COL['wen_wing'],
        lambda bm: prim_ellipsoid(bm, (0.04, 0.16, 0.02), (0.28, 0.07, 0.05), 5, 4),
    )
    _solid_obj(
        'wen_wing_r',
        COL['wen_wing'],
        lambda bm: prim_ellipsoid(bm, (0.04, -0.16, 0.02), (0.28, 0.07, 0.05), 5, 4),
    )
    _solid_obj(
        'wen_tail',
        COL['wen_white'],
        lambda bm: prim_box(bm, (0.09, 0.02, 0.03), (0.02, 0.08, 0.0)),
    )
    _orient_long_axis_z()
    _center_at_origin()
    root = _make_export_root()
    _normalize_longest(1.0)
    export_life(os.path.join(OUT_DIR, 'wenyao.glb'), root)


def _gold_hex_bumps(bm, cx, cy, cz, rad, count=14):
    for i in range(count):
        ang = (i / count) * math.pi * 2.0
        px = cx + math.cos(ang) * rad * 0.55
        py = cy + math.sin(ang) * rad * 0.45
        prim_ellipsoid(bm, (px, py, cz), (0.04, 0.035, 0.022), 4, 3)


def build_xuanwu():
    clear_all()
    _import_glb(os.path.join(SRC, 'turtle-sea-polygoogle.glb'))
    _flatten_bake_meshes()
    _center_at_origin()
    root = _make_export_root()
    for ob in list(_mesh_objs()):
        if ob.parent != root:
            ob.parent = root
    for src in [c for c in list(root.children) if c.type == 'MESH']:
        _split_src_mesh(
            src,
            [
                ('xuan_shell', COL['xuan_shell'], 1.0, lambda f: f.normal.z > 0.25),
                ('xuan_body', COL['xuan_body'], 1.0, lambda f: True),
            ],
            root,
        )
    _solid_obj(
        'xuan_gold',
        COL['xuan_gold'],
        lambda bm: _gold_hex_bumps(bm, 0.0, 0.0, 0.22, 0.42),
        parent=root,
    )
    before = {ob.name for ob in _mesh_objs()}
    _import_glb(os.path.join(SRC, 'snake-long-polygoogle.glb'))
    snake_meshes = [ob for ob in _mesh_objs() if ob.name not in before]
    bpy.ops.object.select_all(action='DESELECT')
    for ob in snake_meshes:
        ob.select_set(True)
    bpy.ops.object.parent_clear(type='CLEAR_KEEP_TRANSFORM')
    for ob in list(bpy.data.objects):
        if ob.type == 'ARMATURE' and ob.name not in before:
            bpy.data.objects.remove(ob, do_unlink=True)
    snake_meshes = [ob for ob in _mesh_objs() if ob.name not in before]
    for ob in snake_meshes:
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        ob.select_set(False)
    sn_mn, sn_mx = _bounds_meshes(snake_meshes)
    sn_span = max(sn_mx.x - sn_mn.x, sn_mx.y - sn_mn.y, sn_mx.z - sn_mn.z)
    if sn_span > 1e-5:
        _scale_meshes(snake_meshes, 0.82 / sn_span)
    bpy.ops.object.select_all(action='DESELECT')
    for ob in snake_meshes:
        ob.location += Vector((0.02, 0.0, 0.1))
        ob.rotation_euler = Euler((0.35, 1.15, 0.95))
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        ob.select_set(False)
        ob.parent = root
        _split_src_mesh(
            ob,
            [
                ('snake_belly', COL['snake_belly'], 1.0, lambda f: f.normal.y < -0.25),
                ('snake', COL['snake'], 1.0, lambda f: True),
            ],
            root,
        )
    _normalize_longest(1.0)
    _decimate_scene(1150)
    export_life(os.path.join(OUT_DIR, 'xuanwu.glb'), root)


def build_kunpeng():
    clear_all()
    _import_glb(os.path.join(SRC, 'whale-quaternius.glb'))
    _drop_helpers()
    _flatten_bake_meshes()
    _orient_long_axis_z()
    _center_at_origin()
    root = _make_export_root()
    for ob in list(_mesh_objs()):
        if ob.parent != root:
            ob.parent = root
    for src in [c for c in root.children if c.type == 'MESH']:
        _split_src_mesh(
            src,
            [
                ('kun_belly', COL['kun_white'], 1.0, lambda f: f.normal.y < -0.1 or _face_center(f).y < 0.0),
                ('kun_back', COL['kun_blue'], 1.0, lambda f: True),
            ],
            root,
        )
    _solid_obj(
        'kun_fin_l',
        COL['kun_fin'],
        lambda bm: prim_ellipsoid(bm, (0.18, 0.16, 0.1), (0.16, 0.05, 0.12), 5, 4),
        parent=root,
    )
    _solid_obj(
        'kun_fin_r',
        COL['kun_fin'],
        lambda bm: prim_ellipsoid(bm, (0.18, -0.16, 0.1), (0.16, 0.05, 0.12), 5, 4),
        parent=root,
    )
    _solid_obj(
        'kun_tail',
        COL['kun_blue'],
        lambda bm: prim_ellipsoid(bm, (-0.42, 0.0, 0.02), (0.14, 0.18, 0.06), 5, 4),
        parent=root,
    )
    _solid_obj(
        'kun_clouds',
        COL['kun_cloud'],
        lambda bm: (
            prim_ellipsoid(bm, (-0.55, 0.12, 0.16), (0.28, 0.2, 0.22), 6, 5),
            prim_sphere(bm, (-0.82, 0.04, 0.12), 0.18, 5, 4),
            prim_sphere(bm, (-1.05, 0.1, 0.14), 0.2, 5, 4),
            prim_sphere(bm, (-1.28, -0.02, 0.11), 0.17, 5, 4),
            prim_sphere(bm, (0.15, 0.08, 0.18), 0.16, 5, 4),
            prim_sphere(bm, (0.35, -0.1, 0.15), 0.14, 5, 4),
        ),
        parent=root,
        alpha=0.62,
    )
    _normalize_longest(1.0)
    _decimate_scene(680)
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