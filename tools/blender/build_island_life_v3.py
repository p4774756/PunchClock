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
    append_bmesh,
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
    'gull_white': 0xF4F6F8,
    'gull_wing': 0x9AA6B2,
    'gull_tip': 0x2A2A2A,
    'gull_beak': 0xF2B630,
    'whale_back': 0x3E6E9E,
    'whale_belly': 0xDCE6EC,
    'whale_fin': 0x3A6490,
    'spray': 0xF2FAFF,
    'octo': 0xF08A8A,
    'octo_sucker': 0xF8C8C8,
    'octo_eye_w': 0xF8F8FF,
    'wen_blue': 0x3A8FD8,
    'wen_blue_dk': 0x2A6EB8,
    'wen_white': 0xE8F0F8,
    'wen_wing': 0xBFE3FF,
    'xuan_shell': 0x3C7048,
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


def _orient_long_axis_x():
    """最長軸對齊 Blender X（export_yup 後為 glTF 左右翼展／體長）。"""
    mn, mx = _bounds_meshes()
    dims = mx - mn
    ax = sorted([('x', dims.x), ('y', dims.y), ('z', dims.z)], key=lambda t: t[1], reverse=True)
    long_axis = ax[0][0]
    if long_axis == 'y':
        _rotate_scene_euler((0.0, math.pi / 2.0, 0.0))
    elif long_axis == 'z':
        _rotate_scene_euler((0.0, 0.0, math.pi / 2.0))


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


def _tube_along_points(bm, points, radius, segs=7):
    """圓柱段串成管狀路徑（Blender 座標）。"""
    for i in range(len(points) - 1):
        p0 = Vector(points[i])
        p1 = Vector(points[i + 1])
        diff = p1 - p0
        length = diff.length
        if length < 1e-5:
            continue
        mid = (p0 + p1) * 0.5
        sub = bmesh.new()
        bmesh.ops.create_cone(
            sub, cap_ends=True, cap_tris=False, segments=segs,
            radius1=radius, radius2=radius * 0.97, depth=length,
        )
        axis = Vector((0.0, 0.0, 1.0))
        quat = axis.rotation_difference(diff.normalized())
        bmesh.ops.rotate(sub, verts=sub.verts, cent=(0.0, 0.0, 0.0),
                         matrix=quat.to_matrix().to_4x4())
        bmesh.ops.translate(sub, vec=mid, verts=sub.verts)
        append_bmesh(bm, sub)
        sub.free()


def _snake_ring_points(turns=1.55, n=28, rad=0.46, head_lift=0.72):
    pts = []
    for i in range(n + 1):
        t = (i / n) * turns * 2.0 * math.pi
        x = math.cos(t) * rad
        y = math.sin(t) * rad * 0.82
        z = 0.06 + head_lift * max(0.0, math.sin(t * 0.65 + 0.9)) ** 1.4
        pts.append((x, y, z))
    return pts


def _gold_hex_strips(bm, cz=0.26, rad=0.38):
    for ring in range(4):
        for i in range(6):
            ang = (i / 6.0) * math.pi * 2.0 + ring * 0.18
            r = rad * (0.42 + ring * 0.14)
            px = math.cos(ang) * r
            py = math.sin(ang) * r * 0.78
            prim_ellipsoid(bm, (px, py, cz + ring * 0.035), (0.08, 0.032, 0.018), 4, 3)


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
    _solid_obj(
        'gull_body',
        COL['gull_white'],
        lambda bm: (
            prim_ellipsoid(bm, (0.0, 0.0, 0.0), (0.1, 0.07, 0.22), 7, 5),
            prim_ellipsoid(bm, (-0.05, 0.0, -0.12), (0.07, 0.06, 0.08), 5, 4),
        ),
    )
    _solid_obj(
        'gull_wing',
        COL['gull_wing'],
        lambda bm: (
            prim_ellipsoid(bm, (0.46, 0.0, 0.02), (0.42, 0.025, 0.1), 6, 4),
            prim_ellipsoid(bm, (-0.46, 0.0, 0.02), (0.42, 0.025, 0.1), 6, 4),
        ),
    )
    _solid_obj(
        'gull_tip',
        COL['gull_tip'],
        lambda bm: (
            prim_ellipsoid(bm, (0.82, 0.0, 0.03), (0.06, 0.012, 0.05), 4, 3),
            prim_ellipsoid(bm, (-0.82, 0.0, 0.03), (0.06, 0.012, 0.05), 4, 3),
        ),
    )
    _solid_obj(
        'gull_beak',
        COL['gull_beak'],
        lambda bm: prim_cone(bm, (0.36, 0.0, 0.02), 0.018, 0.09, 4, rot=(0, 0, -math.pi / 2)),
    )
    _orient_long_axis_x()
    _center_at_origin()
    root = _make_export_root()
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
                ('whale_fin', COL['whale_fin'], 1.0,
                 lambda f: abs(_face_center(f).x) > 0.22 and abs(_face_center(f).y) < 0.14),
                ('whale_belly', COL['whale_belly'], 1.0, lambda f: f.normal.y < -0.15),
                ('whale_back', COL['whale_back'], 1.0, lambda f: True),
            ],
            root,
        )
    spray_parent = root
    _solid_obj(
        'whale_spray',
        COL['spray'],
        lambda bm: (
            prim_cylinder(bm, (0.42, 0.0, 0.18), 0.13, 0.65, 8),
            prim_cylinder(bm, (0.42, 0.0, 0.65), 0.09, 1.45, 8),
            prim_ellipsoid(bm, (0.42, 0.0, 1.35), (0.26, 0.24, 0.22), 8, 6),
            prim_sphere(bm, (0.32, 0.1, 1.45), 0.11, 6, 5),
            prim_sphere(bm, (0.52, -0.06, 1.4), 0.1, 6, 5),
            prim_sphere(bm, (0.42, 0.02, 1.52), 0.08, 5, 4),
        ),
        parent=spray_parent,
        alpha=0.58,
    )
    _normalize_longest(1.0)
    _decimate_scene(620)
    export_life(os.path.join(OUT_DIR, 'whale.glb'), root)


def build_octopus():
    clear_all()
    _solid_obj(
        'octo_body',
        COL['octo'],
        lambda bm: prim_ellipsoid(bm, (0.0, 0.22, 0.0), (0.24, 0.22, 0.21), 8, 6),
    )
    arms_bm = bmesh.new()

    def _arm(ang):
        cx = math.cos(ang) * 0.06
        cz = math.sin(ang) * 0.06
        px = cx + math.cos(ang) * 0.42
        pz = cz + math.sin(ang) * 0.42
        _tube_along_points(
            arms_bm,
            [(cx, 0.06, cz), (cx + math.cos(ang) * 0.18, -0.02, cz + math.sin(ang) * 0.18),
             (px, 0.02, pz), (px + math.cos(ang) * 0.08, 0.12, pz + math.sin(ang) * 0.08)],
            0.042,
            6,
        )

    for i in range(8):
        _arm(i * math.pi / 4.0)
    me = bpy.data.meshes.new('octo_arms')
    arms_bm.to_mesh(me)
    arms_bm.free()
    arms_ob = bpy.data.objects.new('octo_arms', me)
    arms_ob.data.materials.append(creature_solid_material(COL['octo'], 'octo_arms_mat'))
    bpy.context.scene.collection.objects.link(arms_ob)

    suckers_bm = bmesh.new()
    for i in range(8):
        ang = i * math.pi / 4.0
        for j in range(3):
            r = 0.12 + j * 0.11
            px = math.cos(ang) * r
            pz = math.sin(ang) * r
            prim_sphere(suckers_bm, (px, -0.04, pz), 0.018, 4, 3)
    me2 = bpy.data.meshes.new('octo_sucker')
    suckers_bm.to_mesh(me2)
    suckers_bm.free()
    suck_ob = bpy.data.objects.new('octo_sucker', me2)
    suck_ob.data.materials.append(creature_solid_material(COL['octo_sucker'], 'octo_sucker_mat'))
    bpy.context.scene.collection.objects.link(suck_ob)

    for side, tag in ((-1, 'l'), (1, 'r')):
        _solid_obj(
            f'octo_eye_{tag}',
            COL['octo_eye_w'],
            lambda bm, side=side: prim_ellipsoid(bm, (0.1 * side, 0.28, 0.18), (0.075, 0.08, 0.075), 6, 5),
        )
        _solid_obj(
            f'octo_pupil_{tag}',
            COL['black'],
            lambda bm, side=side: prim_sphere(bm, (0.1 * side, 0.28, 0.24), 0.035, 5, 4),
        )

    _center_at_origin()
    root = _make_export_root()
    _normalize_longest(1.0)
    _decimate_scene(680)
    export_life(os.path.join(OUT_DIR, 'octopus.glb'), root)


def build_wenyao():
    clear_all()
    _solid_obj(
        'wen_body',
        COL['wen_blue_dk'],
        lambda bm: prim_ellipsoid(bm, (0.0, 0.0, 0.0), (0.09, 0.36, 0.038), 7, 5),
    )
    _solid_obj(
        'wen_belly',
        COL['wen_white'],
        lambda bm: prim_ellipsoid(bm, (0.02, 0.05, -0.015), (0.08, 0.26, 0.04), 6, 4),
    )
    _solid_obj(
        'wen_wing_l',
        COL['wen_wing'],
        lambda bm: prim_ellipsoid(bm, (0.45, 0.02, 0.0), (0.34, 0.08, 0.022), 6, 4),
        alpha=0.85,
    )
    _solid_obj(
        'wen_wing_r',
        COL['wen_wing'],
        lambda bm: prim_ellipsoid(bm, (-0.45, 0.02, 0.0), (0.34, 0.08, 0.022), 6, 4),
        alpha=0.85,
    )
    _solid_obj(
        'wen_tail',
        COL['wen_white'],
        lambda bm: (
            prim_ellipsoid(bm, (-0.36, 0.07, 0.0), (0.05, 0.09, 0.04), 4, 3),
            prim_ellipsoid(bm, (-0.36, -0.07, 0.0), (0.05, 0.09, 0.04), 4, 3),
        ),
    )
    _orient_long_axis_x()
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
                ('xuan_shell', COL['xuan_shell'], 1.0, lambda f: f.normal.z > 0.12),
                ('xuan_body', COL['xuan_body'], 1.0, lambda f: True),
            ],
            root,
        )
    mn, mx = _bounds_meshes()
    span = max(mx.x - mn.x, mx.y - mn.y, mx.z - mn.z)
    if span > 1e-4:
        factor = 0.92 / span
        for ob in [c for c in root.children if c.type == 'MESH']:
            ob.scale *= factor
            bpy.context.view_layer.objects.active = ob
            ob.select_set(True)
            bpy.ops.object.transform_apply(scale=True)
            ob.select_set(False)
    _center_at_origin()
    _normalize_longest(1.0)
    _decimate_scene(950)
    _solid_obj(
        'xuan_gold',
        COL['xuan_gold'],
        lambda bm: _gold_hex_strips(bm, cz=0.24, rad=0.38),
        parent=root,
    )
    snake_pts = _snake_ring_points(rad=0.41)
    _solid_obj(
        'snake',
        COL['snake'],
        lambda bm: _tube_along_points(bm, snake_pts, 0.068, 8),
        parent=root,
    )
    belly_pts = [(p[0], p[1], p[2] - 0.038) for p in snake_pts]
    _solid_obj(
        'snake_belly',
        COL['snake_belly'],
        lambda bm: _tube_along_points(bm, belly_pts, 0.038, 6),
        parent=root,
    )
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
                ('kun_belly', COL['kun_white'], 1.0, lambda f: f.normal.y < -0.12),
                ('kun_back', COL['kun_blue'], 1.0, lambda f: True),
            ],
            root,
        )
    _solid_obj(
        'kun_fin_l',
        COL['kun_fin'],
        lambda bm: prim_ellipsoid(bm, (0.14, 0.22, 0.08), (0.2, 0.08, 0.22), 6, 5),
        parent=root,
    )
    _solid_obj(
        'kun_fin_r',
        COL['kun_fin'],
        lambda bm: prim_ellipsoid(bm, (0.14, -0.22, 0.08), (0.2, 0.08, 0.22), 6, 5),
        parent=root,
    )
    _solid_obj(
        'kun_tail',
        COL['kun_blue'],
        lambda bm: prim_ellipsoid(bm, (-0.44, 0.0, 0.0), (0.16, 0.28, 0.1), 6, 5),
        parent=root,
    )
    _solid_obj(
        'kun_clouds',
        COL['kun_cloud'],
        lambda bm: (
            prim_sphere(bm, (-0.38, 0.18, -0.08), 0.2, 6, 5),
            prim_sphere(bm, (-0.62, 0.08, -0.06), 0.22, 6, 5),
            prim_sphere(bm, (-0.88, 0.14, -0.04), 0.24, 6, 5),
            prim_sphere(bm, (-1.14, 0.02, -0.02), 0.21, 6, 5),
            prim_sphere(bm, (-1.38, 0.1, 0.0), 0.2, 6, 5),
            prim_sphere(bm, (0.12, 0.06, -0.12), 0.18, 5, 4),
            prim_sphere(bm, (-1.55, -0.06, 0.02), 0.17, 5, 4),
        ),
        parent=root,
        alpha=0.62,
    )
    _normalize_longest(1.0)
    for name, sc in (
        ('kun_fin_l', (2.8, 2.2, 2.6)),
        ('kun_fin_r', (2.8, 2.2, 2.6)),
        ('kun_tail', (2.4, 2.2, 2.5)),
    ):
        ob = bpy.data.objects.get(name)
        if not ob:
            continue
        ob.scale = sc
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.transform_apply(scale=True)
        ob.select_set(False)
    clouds = bpy.data.objects.get('kun_clouds')
    if clouds:
        clouds.scale = (1.95, 1.85, 1.75)
        bpy.context.view_layer.objects.active = clouds
        clouds.select_set(True)
        bpy.ops.object.transform_apply(scale=True)
        clouds.select_set(False)
    _center_at_origin()
    _normalize_longest(1.0)
    _decimate_scene(780)
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