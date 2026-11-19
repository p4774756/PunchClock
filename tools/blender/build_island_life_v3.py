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
    'kun_cloud': 0xFFFFFF,
    'kun_fin': 0xBFD9EE,
    'snake_eye': 0xD4A82A,
    'wen_fin_line': 0x8CC4F0,
    'black': 0x181818,
}

# 配合 island-life.js LIFE_GLB_SCALE
_WHALE_SCALE = 7.5
_KUN_SCALE = 9.0


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


def _normalize_objects(objs, target=1.0):
    """只對指定 mesh 正規化最長軸（雲氣／噴水可不參與）。"""
    if not objs:
        return
    mn, mx = _bounds_meshes(objs)
    dims = mx - mn
    longest = max(dims.x, dims.y, dims.z)
    if longest < 1e-6:
        return
    factor = target / longest
    for ob in objs:
        ob.scale *= factor
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.transform_apply(scale=True)
        ob.select_set(False)


def _shade_smooth(objs=None):
    for ob in objs or _mesh_objs():
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.shade_smooth()
        ob.select_set(False)


def _reparent_meshes_to_root(root):
    for ob in _mesh_objs():
        if ob.parent != root:
            ob.parent = root


def _flatten_selected_meshes(objs):
    if not objs:
        return
    bpy.ops.object.select_all(action='DESELECT')
    for ob in objs:
        ob.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.parent_clear(type='CLEAR_KEEP_TRANSFORM')
    for ob in objs:
        ob.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    bpy.ops.object.select_all(action='DESELECT')


def _merge_imported_meshes(name='creature_mesh'):
    """匯入 GLB 後合併為單一 mesh（保留全部部位）。"""
    meshes = _mesh_objs()
    if len(meshes) <= 1:
        return meshes[0] if meshes else None
    bpy.ops.object.select_all(action='DESELECT')
    for ob in meshes:
        ob.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.object.join()
    joined = bpy.context.view_layer.objects.active
    joined.name = name
    return joined


def _hex_gold_on_shell_test(face, cz_floor=0.08):
    c = _face_center(face)
    if face.normal.z < 0.08:
        return False
    ang = math.atan2(c.y, c.x)
    r = math.hypot(c.x, c.y)
    if r < 0.04 or r > 0.5:
        return False
    ring = int(r / 0.09) % 2
    sector = int(((ang + math.pi) / (math.pi / 3.0))) % 2
    return (ring + sector) % 2 == 0


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
    """實心圓柱段串成管狀路徑（Blender 座標）。"""
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


def _continuous_tube(bm, points, radii, segs=12):
    """相鄰截面共用頂點的連續管（smooth）。"""
    pts = [Vector(p) for p in points]
    if len(pts) < 2:
        return
    rings = []
    for i, p in enumerate(pts):
        r = radii[i] if i < len(radii) else radii[-1]
        if i == 0:
            tangent = (pts[1] - pts[0]).normalized()
        elif i == len(pts) - 1:
            tangent = (pts[-1] - pts[-2]).normalized()
        else:
            tangent = (pts[i + 1] - pts[i - 1]).normalized()
        up = Vector((0.0, 0.0, 1.0))
        if abs(tangent.dot(up)) > 0.92:
            up = Vector((0.0, 1.0, 0.0))
        side = tangent.cross(up).normalized()
        up2 = side.cross(tangent).normalized()
        ring = []
        for s in range(segs):
            ang = (s / segs) * math.pi * 2.0
            off = side * math.cos(ang) * r + up2 * math.sin(ang) * r
            ring.append(bm.verts.new(p + off))
        rings.append(ring)
    bm.verts.ensure_lookup_table()
    for i in range(len(rings) - 1):
        r0, r1 = rings[i], rings[i + 1]
        for s in range(segs):
            s1 = (s + 1) % segs
            try:
                bm.faces.new((r0[s], r0[s1], r1[s1], r1[s]))
            except ValueError:
                pass
    bm.verts.ensure_lookup_table()


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
    _import_glb(os.path.join(SRC, 'seagull-flying-polygoogle.glb'))
    _flatten_bake_meshes()
    src = _merge_imported_meshes('gull_src')
    bm_g = bmesh.new()
    bm_g.from_mesh(src.data)
    bmesh.ops.remove_doubles(bm_g, verts=bm_g.verts, dist=0.0005)
    bm_g.to_mesh(src.data)
    bm_g.free()
    src.data.update()
    _orient_long_axis_x()
    _center_at_origin()
    root = _make_export_root()
    if src.parent != root:
        src.parent = root
    _split_src_mesh(
        src,
        [
            ('gull_beak', COL['gull_beak'], 1.0,
             lambda f: _face_center(f).x > 0.24 and f.normal.x > 0.35),
            ('gull_tip', COL['gull_tip'], 1.0,
             lambda f: abs(_face_center(f).x) > 0.32 and abs(_face_center(f).z) < 0.07),
            ('gull_wing', COL['gull_wing'], 1.0,
             lambda f: f.normal.z < -0.42 and abs(_face_center(f).x) > 0.1),
            ('gull_body', COL['gull_white'], 1.0, lambda f: True),
        ],
        root,
    )
    meshes = [c for c in root.children if c.type == 'MESH']
    _normalize_objects(meshes, 1.0)
    _decimate_scene(520)
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
    body_meshes = []
    for src in [c for c in root.children if c.type == 'MESH']:
        body_meshes.extend(_split_src_mesh(
            src,
            [
                ('whale_fin', COL['whale_fin'], 1.0,
                 lambda f: abs(_face_center(f).x) > 0.22 and abs(_face_center(f).y) < 0.14),
                ('whale_belly', COL['whale_belly'], 1.0, lambda f: f.normal.y < -0.15),
                ('whale_back', COL['whale_back'], 1.0, lambda f: True),
            ],
            root,
        ))
    body_meshes = [c for c in root.children if c.type == 'MESH']
    _normalize_objects(body_meshes, 1.0)
    _decimate_scene(580)
    sy = 3.2 / _WHALE_SCALE
    sx_base = 0.42 / _WHALE_SCALE
    spray_rot = (math.pi / 2.0, 0.0, 0.0)

    def _spray(bm):
        prim_cylinder(bm, (0.46, 0.04, 0.0), sx_base * 0.85, sy * 0.35, 10, rot=spray_rot)
        prim_cylinder(bm, (0.46, 0.04 + sy * 0.4, 0.0), sx_base * 0.62, sy * 0.48, 10, rot=spray_rot)
        prim_ellipsoid(bm, (0.46, 0.04 + sy * 0.86, 0.0), (sx_base * 0.85, sy * 0.55, sx_base * 0.95), 10, 8)
        prim_sphere(bm, (0.38, 0.04 + sy * 0.95, 0.11), sx_base * 0.42, 8, 6)
        prim_sphere(bm, (0.54, 0.04 + sy * 0.92, -0.08), sx_base * 0.38, 8, 6)
        prim_sphere(bm, (0.46, 0.04 + sy * 1.02, 0.04), sx_base * 0.32, 7, 5)

    _solid_obj('whale_spray', COL['spray'], _spray, parent=root, alpha=0.82)
    _shade_smooth([c for c in root.children if c.name == 'whale_spray'])
    export_life(os.path.join(OUT_DIR, 'whale.glb'), root)


def build_octopus():
    clear_all()
    _import_glb(os.path.join(SRC, 'octopus-polygoogle.glb'))
    _flatten_bake_meshes()
    src = _merge_imported_meshes('octo_src')
    _rotate_scene_euler((math.pi / 2.0, 0.0, 0.0))
    _center_at_origin()
    mn, mx = _bounds_meshes([src])
    mid_z = mn.z + (mx.z - mn.z) * 0.38
    root = _make_export_root()
    src.parent = root

    def _is_arm_face(f):
        c = _face_center(f)
        if abs(c.y) > 0.13:
            return False
        return math.hypot(c.x, c.z) > 0.12 or (c.z < -0.05 and math.hypot(c.x, c.z) > 0.05)

    _split_src_mesh(
        src,
        [
            ('octo_sucker', COL['octo_sucker'], 1.0,
             lambda f: _is_arm_face(f) and f.normal.z < -0.35),
            ('octo_arms', COL['octo'], 1.0, _is_arm_face),
            ('octo_body', COL['octo'], 1.0, lambda f: True),
        ],
        root,
    )
    body = [c for c in root.children if c.type == 'MESH']
    _normalize_objects(body, 1.0)
    _solid_obj(
        'octo_eyes',
        COL['octo_eye_w'],
        lambda bm: (
            prim_sphere(bm, (-0.14, 0.04, 0.2), 0.1, 8, 6),
            prim_sphere(bm, (0.14, 0.04, 0.2), 0.1, 8, 6),
            prim_sphere(bm, (-0.14, 0.04, 0.24), 0.035, 6, 4),
            prim_sphere(bm, (0.14, 0.04, 0.24), 0.035, 6, 4),
        ),
        parent=root,
    )
    _decimate_scene(950)
    _shade_smooth(_mesh_objs())
    export_life(os.path.join(OUT_DIR, 'octopus.glb'), root)


def _wen_fan_wing(bm, side, root_y=0.1):
    """扇形胸鰭（水平展開；魚體沿 Blender Y）。"""
    sx = side
    tip_y = root_y - 0.52
    pts = [
        (0.06 * sx, root_y, 0.0),
        (0.22 * sx, root_y - 0.08, 0.03 * sx),
        (0.38 * sx, root_y - 0.22, 0.06 * sx),
        (0.48 * sx, tip_y, 0.08 * sx),
    ]
    radii = [0.034, 0.028, 0.022, 0.012]
    _continuous_tube(bm, pts, radii, 10)
    for i, off in enumerate((0.14, 0.26, 0.38)):
        prim_box(bm, (0.012, 0.55 - i * 0.12, 0.008),
                 (pts[i + 1][0] * 0.92, pts[i + 1][1], pts[i + 1][2] * 0.5),
                 rot=(0.0, 0.52 * sx, -0.12))


def build_wenyao():
    clear_all()
    _import_glb(os.path.join(SRC, 'fish-quaternius.glb'))
    _drop_helpers()
    _flatten_bake_meshes()
    _orient_long_axis_z()
    _center_at_origin()
    root = _make_export_root()
    for ob in list(_mesh_objs()):
        if ob.parent != root:
            ob.parent = root
    for src in [c for c in list(root.children) if c.type == 'MESH']:
        _split_src_mesh(
            src,
            [
                ('wen_tail', COL['wen_white'], 1.0,
                 lambda f: _face_center(f).y < -0.32 and abs(_face_center(f).x) < 0.09),
                ('wen_belly', COL['wen_white'], 1.0, lambda f: f.normal.z < -0.2),
                ('wen_body', COL['wen_blue_dk'], 1.0,
                 lambda f: f.normal.z > 0.15 or f.normal.y > 0.3),
                ('wen_side', COL['wen_blue'], 1.0, lambda f: True),
            ],
            root,
        )
    def _wing_l(bm):
        _wen_fan_wing(bm, 1.0)

    def _wing_r(bm):
        _wen_fan_wing(bm, -1.0)

    _solid_obj('wen_wing_l', COL['wen_wing'], _wing_l, parent=root, alpha=0.85)
    _solid_obj('wen_wing_r', COL['wen_wing'], _wing_r, parent=root, alpha=0.85)
    for ob in [c for c in root.children if 'wen_wing' in c.name]:
        ob.scale = (3.4, 1.0, 1.6)
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        bpy.ops.object.transform_apply(scale=True)
        ob.select_set(False)
    all_meshes = [c for c in root.children if c.type == 'MESH']
    _normalize_objects(all_meshes, 1.0)
    _decimate_scene(720)
    export_life(os.path.join(OUT_DIR, 'wenyao.glb'), root)


def _gold_hex_bumps(bm, cx, cy, cz, rad, count=14):
    for i in range(count):
        ang = (i / count) * math.pi * 2.0
        px = cx + math.cos(ang) * rad * 0.55
        py = cy + math.sin(ang) * rad * 0.45
        prim_ellipsoid(bm, (px, py, cz), (0.04, 0.035, 0.022), 4, 3)


def _snake_shell_path(turns=1.35, n=32, rad=0.44):
    pts = []
    for i in range(n + 1):
        t = (i / n) * turns * 2.0 * math.pi
        x = math.cos(t) * rad
        y = math.sin(t) * rad * 0.84
        z = 0.02 + 0.62 * max(0.0, math.sin(t * 0.55 + 1.1)) ** 1.25
        pts.append((x, y, z))
    return pts


def build_xuanwu():
    clear_all()
    _import_glb(os.path.join(SRC, 'turtle-sea-polygoogle.glb'))
    _flatten_bake_meshes()
    t_meshes = list(_mesh_objs())
    for ob in t_meshes:
        bm = bmesh.new()
        bm.from_mesh(ob.data)
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.0008)
        bm.to_mesh(ob.data)
        bm.free()
        ob.data.update()
    _center_at_origin()
    root = _make_export_root()
    for ob in t_meshes:
        ob.parent = root
    for src in [c for c in list(root.children) if c.type == 'MESH']:
        _split_src_mesh(
            src,
            [
                ('xuan_shell', COL['xuan_shell'], 1.0, lambda f: f.normal.z > 0.05),
                ('xuan_body', COL['xuan_body'], 1.0, lambda f: True),
            ],
            root,
        )
    for ob in [c for c in root.children if c.type == 'MESH']:
        bm = bmesh.new()
        bm.from_mesh(ob.data)
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.001)
        bm.to_mesh(ob.data)
        bm.free()
        ob.data.update()
    _normalize_objects([c for c in root.children if c.type == 'MESH' and c.name.startswith('xuan')], 1.0)
    snake_pts = _snake_shell_path(turns=1.4, rad=0.43)
    hx, hy, hz = snake_pts[0]

    def _procedural_snake(bm):
        _tube_along_points(bm, snake_pts, 0.055, 12)
        prim_ellipsoid(bm, (hx + 0.05, hy, hz + 0.05), (0.065, 0.075, 0.06), 6, 5)

    def _procedural_belly(bm):
        pts = [(p[0], p[1], p[2] - 0.022) for p in snake_pts]
        _tube_along_points(bm, pts, 0.034, 10)

    _solid_obj('snake', COL['snake'], _procedural_snake, parent=root)
    _solid_obj('snake_belly', COL['snake_belly'], _procedural_belly, parent=root)
    _normalize_longest(1.0)
    turtle_meshes = [c for c in root.children if c.type == 'MESH' and c.name.startswith('xuan')]
    t_tris = sum(sum(len(p.vertices) - 2 for p in ob.data.polygons) for ob in turtle_meshes)
    if t_tris > 650:
        ratio = (650 / t_tris) * 0.98
        for ob in turtle_meshes:
            mod = ob.modifiers.new('Decimate', 'DECIMATE')
            mod.ratio = min(0.99, ratio)
            bpy.context.view_layer.objects.active = ob
            ob.select_set(True)
            bpy.ops.object.modifier_apply(modifier=mod.name)
            ob.select_set(False)
    _decimate_scene(2900)
    _shade_smooth(_mesh_objs())
    export_life(os.path.join(OUT_DIR, 'xuanwu.glb'), root)


def _kun_cloud_builder(bm):
    """雲氣直徑以世界單位設定後除以 LIFE_GLB_SCALE；不拉長整體 AABB。"""
    specs = [
        (-0.18, 0.16, -0.06, 1.4),
        (-0.30, -0.14, -0.05, 1.6),
        (-0.42, 0.12, -0.05, 1.8),
        (-0.28, -0.22, -0.04, 1.3),
        (-0.40, 0.22, -0.04, 1.5),
        (0.02, 0.0, -0.08, 1.2),
        (-0.48, -0.08, -0.03, 1.25),
    ]
    for cx, cy, cz, dia in specs:
        r = (dia / _KUN_SCALE) * 0.5
        prim_sphere(bm, (cx, cy, cz), r, 12, 8)


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
    body_parts = []
    for src in [c for c in list(root.children) if c.type == 'MESH']:
        body_parts.extend(_split_src_mesh(
            src,
            [
                ('kun_fin', COL['kun_fin'], 1.0,
                 lambda f: abs(_face_center(f).x) > 0.2 and abs(_face_center(f).y) < 0.15),
                ('kun_belly', COL['kun_white'], 1.0, lambda f: f.normal.y < -0.12),
                ('kun_back', COL['kun_blue'], 1.0, lambda f: True),
            ],
            root,
        ))
    body_parts = [c for c in root.children if c.type == 'MESH']
    for fin_name in ('kun_fin',):
        fin = bpy.data.objects.get(fin_name)
        if fin:
            fin.scale = (1.65, 1.65, 1.65)
            fin.location.x *= 0.92
            bpy.context.view_layer.objects.active = fin
            fin.select_set(True)
            bpy.ops.object.transform_apply(scale=True, location=True)
            fin.select_set(False)
    _normalize_objects(body_parts, 1.0)
    _decimate_scene(600)
    cloud_bm = bmesh.new()
    _kun_cloud_builder(cloud_bm)
    bmesh.ops.remove_doubles(cloud_bm, verts=cloud_bm.verts, dist=1e-5)
    cloud_bm.normal_update()
    me = bpy.data.meshes.new('kun_clouds')
    cloud_bm.to_mesh(me)
    cloud_bm.free()
    clouds = bpy.data.objects.new('kun_clouds', me)
    mat = creature_solid_material(COL['kun_cloud'], 'kun_clouds_mat', alpha=0.75, emissive=0xFFFFFF)
    clouds.data.materials.append(mat)
    bpy.context.scene.collection.objects.link(clouds)
    clouds.parent = root
    _shade_smooth([clouds] + body_parts)
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