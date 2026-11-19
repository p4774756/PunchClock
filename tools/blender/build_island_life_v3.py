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
from mathutils.bvhtree import BVHTree

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
    'gull_white': 0xF7F9FB,
    'gull_wing': 0xB8C2CC,
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
    _decimate_objects(_mesh_objs(), max_tris)


def _decimate_objects(objs, max_tris):
    objs = [o for o in objs if o and o.type == 'MESH']
    if not objs:
        return
    total = sum(sum(len(p.vertices) - 2 for p in ob.data.polygons) for ob in objs)
    if total <= max_tris:
        return
    ratio = (max_tris / total) * 0.98
    for ob in objs:
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


def _curve_path_length(points):
    total = 0.0
    for i in range(len(points) - 1):
        total += (Vector(points[i + 1]) - Vector(points[i])).length
    return total


def _shell_gold_hex_lines(bm, shell_ob):
    """BVHTree 將六角格線投影到 xuan_shell 表面並以細管連接。"""
    bvh_bm = bmesh.new()
    bvh_bm.from_mesh(shell_ob.data)
    bvh_bm.transform(shell_ob.matrix_world)
    if not bvh_bm.faces:
        bvh_bm.free()
        return
    bvh = BVHTree.FromBMesh(bvh_bm)
    co_list = [v.co for v in bvh_bm.verts]
    max_z = max(v.z for v in co_list)
    min_x = min(v.x for v in co_list)
    max_x = max(v.x for v in co_list)
    min_y = min(v.y for v in co_list)
    max_y = max(v.y for v in co_list)
    span = max(max_x - min_x, max_y - min_y)
    cell = span / 5.5
    tube_r = max(0.008, min(0.012, cell * 0.07))
    span_lim = span * 0.52

    def _surf(x, y):
        hit = bvh.ray_cast(Vector((x, y, max_z + 1.0)), Vector((0.0, 0.0, -1.0)))
        if hit[0] is None:
            return None
        loc, nrm = hit[0], hit[1]
        return loc + nrm.normalized() * 0.006

    rings = 3
    for q in range(-rings, rings + 1):
        for r in range(-rings, rings + 1):
            if abs(q + r) > rings:
                continue
            cx = cell * 1.5 * q
            cy = cell * math.sqrt(3.0) * (r + q / 2.0)
            if cx * cx + cy * cy > span_lim * span_lim:
                continue
            for k in range(6):
                a0 = (k / 6.0) * math.pi * 2.0 + math.pi / 6.0
                a1 = ((k + 1) / 6.0) * math.pi * 2.0 + math.pi / 6.0
                x0, y0 = cx + cell * math.cos(a0) * 0.92, cy + cell * math.sin(a0) * 0.92
                x1, y1 = cx + cell * math.cos(a1) * 0.92, cy + cell * math.sin(a1) * 0.92
                poly = []
                steps = 6
                for s in range(steps + 1):
                    t = s / steps
                    pt = _surf(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
                    if pt is not None:
                        poly.append(pt)
                if len(poly) >= 2:
                    radii = [tube_r] * len(poly)
                    _continuous_tube(bm, [(p.x, p.y, p.z) for p in poly], radii, 6)
    bvh_bm.free()


def _snake_taper_radii(n, head_r=0.062, tail_r=0.008):
    radii = []
    for i in range(n):
        t = i / max(1, n - 1)
        radii.append(head_r * (1.0 - t ** 1.4) + tail_r * (t ** 1.4))
    return radii


def _fallback_snake_tubes(root, path_pts):
    radii = _snake_taper_radii(len(path_pts))

    def _body(bm):
        _continuous_tube(bm, path_pts, radii, 12)

    def _belly(bm):
        low = [(p[0], p[1], p[2] - 0.02) for p in path_pts]
        _continuous_tube(bm, low, [r * 0.62 for r in radii], 12)

    _solid_obj('snake', COL['snake'], _body, parent=root)
    _solid_obj('snake_belly', COL['snake_belly'], _belly, parent=root)


def _flatten_mesh_list(objs):
    """只對指定 mesh 清除父級並套用變換（不影響場景其他物件）。"""
    objs = [o for o in objs if o and o.type == 'MESH']
    if not objs:
        return None
    for ob in list(bpy.data.objects):
        if ob.type == 'ARMATURE':
            bpy.data.objects.remove(ob, do_unlink=True)
    _flatten_selected_meshes(objs)
    if len(objs) == 1:
        return objs[0]
    bpy.ops.object.select_all(action='DESELECT')
    for ob in objs:
        ob.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    joined = bpy.context.view_layer.objects.active
    joined.name = 'snake_import'
    joined.select_set(False)
    return joined


def _add_snake_quaternius(root, path_pts):
    """沿殼頂曲線彎曲 snake-quaternius；失敗時回傳 False 由呼叫端改 procedural tube。"""
    before = {ob.as_pointer() for ob in bpy.data.objects}
    try:
        _import_glb(os.path.join(SRC, 'snake-quaternius.glb'))
    except Exception:
        return False
    new_meshes = [ob for ob in bpy.data.objects if ob.type == 'MESH' and ob.as_pointer() not in before]
    if not new_meshes:
        return False
    src = _flatten_mesh_list(new_meshes)
    if src is None:
        return False
    mn, mx = _bounds_meshes([src])
    dims = mx - mn
    if dims.y < max(dims.x, dims.z) * 0.85:
        if dims.x >= dims.z:
            src.rotation_euler = Euler((0.0, 0.0, math.pi / 2.0))
        else:
            src.rotation_euler = Euler((math.pi / 2.0, 0.0, 0.0))
        bpy.context.view_layer.objects.active = src
        src.select_set(True)
        bpy.ops.object.transform_apply(rotation=True)
        src.select_set(False)
        mn, mx = _bounds_meshes([src])
    body_len = mx.y - mn.y
    path_len = _curve_path_length(path_pts)
    if body_len > 1e-5 and path_len > 1e-5:
        fac = path_len / body_len
        src.scale = (fac * 0.92, fac, fac * 0.92)
        bpy.context.view_layer.objects.active = src
        src.select_set(True)
        bpy.ops.object.transform_apply(scale=True)
        src.select_set(False)
        mn, mx = _bounds_meshes([src])
    for _ in range(2):
        bpy.context.view_layer.objects.active = src
        src.select_set(True)
        bpy.ops.object.mode_set(mode='EDIT')
        bpy.ops.mesh.select_all(action='SELECT')
        bpy.ops.mesh.subdivide(number_cuts=4)
        bpy.ops.object.mode_set(mode='OBJECT')
        src.select_set(False)
    cu = bpy.data.curves.new('SnakePath', type='CURVE')
    cu.dimensions = '3D'
    sp = cu.splines.new('POLY')
    sp.points.add(len(path_pts) - 1)
    for i, p in enumerate(path_pts):
        sp.points[i].co = (float(p[0]), float(p[1]), float(p[2]), 1.0)
    curve_ob = bpy.data.objects.new('snake_curve', cu)
    bpy.context.scene.collection.objects.link(curve_ob)
    curve_ob.parent = root
    mod = src.modifiers.new('Curve', 'CURVE')
    mod.object = curve_ob
    mod.deform_axis = 'POS_Y'
    bpy.context.view_layer.objects.active = src
    src.select_set(True)
    bpy.ops.object.select_all(action='DESELECT')
    src.select_set(True)
    bpy.context.view_layer.objects.active = src
    try:
        bpy.ops.object.modifier_apply(modifier=mod.name)
    except Exception:
        src.modifiers.remove(mod)
        bpy.data.objects.remove(curve_ob, do_unlink=True)
        src.select_set(False)
        for ob in list(bpy.data.objects):
            if ob.as_pointer() not in before and ob.type == 'MESH' and ob != src:
                bpy.data.objects.remove(ob, do_unlink=True)
        bpy.data.objects.remove(src, do_unlink=True)
        return False
    src.select_set(False)
    bpy.data.objects.remove(curve_ob, do_unlink=True)
    mn, mx = _bounds_meshes([src])
    head_y = mx.y
    body_len = max(mx.y - mn.y, 1e-6)
    src.parent = root

    def _is_eye(f):
        c = _face_center(f)
        return c.y > head_y - body_len * 0.14 and abs(c.x) < body_len * 0.12 and c.z > -0.05

    def _is_belly(f):
        return f.normal.z < -0.28

    _split_src_mesh(
        src,
        [
            ('snake_eye', COL['snake_eye'], 1.0, _is_eye),
            ('snake_belly', COL['snake_belly'], 1.0, _is_belly),
            ('snake', COL['snake'], 1.0, lambda f: True),
        ],
        root,
    )
    return True


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
             lambda f: f.normal.z > 0.42 and abs(_face_center(f).x) > 0.1),
            ('gull_body', COL['gull_white'], 1.0, lambda f: True),
        ],
        root,
    )
    for ob in [c for c in root.children if c.type == 'MESH']:
        em = 0x222222 if ob.name in ('gull_body', 'gull_wing') else None
        hx = COL['gull_beak'] if ob.name == 'gull_beak' else (
            COL['gull_tip'] if ob.name == 'gull_tip' else (
                COL['gull_wing'] if ob.name == 'gull_wing' else COL['gull_white']))
        ob.data.materials.clear()
        mat = creature_solid_material(hx, ob.name + '_mat', emissive=em)
        mat.use_backface_culling = False
        bsdf = mat.node_tree.nodes.get('Principled BSDF')
        if bsdf:
            bsdf.inputs['Roughness'].default_value = 0.8
            if 'Metallic' in bsdf.inputs:
                bsdf.inputs['Metallic'].default_value = 0.0
        ob.data.materials.append(mat)
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
            prim_sphere(bm, (-0.14, 0.04, 0.27), 0.045, 6, 4),
            prim_sphere(bm, (0.14, 0.04, 0.27), 0.045, 6, 4),
        ),
        parent=root,
    )
    _decimate_scene(950)
    _shade_smooth(_mesh_objs())
    export_life(os.path.join(OUT_DIR, 'octopus.glb'), root)


def _wen_fan_wing(bm, side, body_len, fin_bm=None):
    """大扇形胸鰭：根部弦長約 35% 體長、展長約 0.9 體長、後掠 ~30°（水平展開）。"""
    L = body_len
    sweep = math.radians(30.0)
    chord = L * 0.35
    span = L * 1.08
    root_x = 0.08 * side * L
    root_z = 0.02
    tip_x = root_x + side * span * math.cos(sweep)
    tip_z = root_z - span * math.sin(sweep) * 0.35
    leading = [
        (root_x, 0.0, root_z),
        (root_x + side * chord * 0.45, -L * 0.04, root_z + L * 0.02),
        (tip_x * 0.72, -L * 0.14, tip_z * 0.55 + L * 0.03),
        (tip_x, -L * 0.18, tip_z),
    ]
    trailing = [
        (root_x - side * chord * 0.12, -L * 0.06, root_z - L * 0.02),
        (root_x + side * chord * 0.35, -L * 0.12, root_z - L * 0.01),
        (tip_x * 0.68, -L * 0.22, tip_z * 0.5),
        (tip_x * 0.94, -L * 0.2, tip_z * 0.85),
    ]
    v_top = []
    v_bot = []
    for i in range(4):
        v_top.append(bm.verts.new(Vector(leading[i]) + Vector((0.0, L * 0.012, 0.0))))
        v_bot.append(bm.verts.new(Vector(trailing[i]) + Vector((0.0, -L * 0.008, 0.0))))
    bm.verts.ensure_lookup_table()
    for i in range(3):
        try:
            bm.faces.new((v_top[i], v_top[i + 1], v_bot[i + 1], v_bot[i]))
        except ValueError:
            pass
    try:
        bm.faces.new((v_top[3], v_bot[3], v_bot[2], v_top[2]))
    except ValueError:
        pass
    if fin_bm is not None:
        for i, t in enumerate((0.28, 0.52, 0.76)):
            fx = leading[1][0] + (tip_x - leading[1][0]) * t
            fz = leading[1][2] + (tip_z - leading[1][2]) * t
            prim_box(fin_bm, (0.006, L * 0.55, 0.004),
                     (fx, -L * (0.08 + i * 0.04), fz),
                     rot=(0.0, 0.08 * side, 0.0))


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
    body_parts = [c for c in root.children if c.type == 'MESH' and not c.name.startswith('wen_wing')]
    _normalize_objects(body_parts, 1.2)
    body_ob = bpy.data.objects.get('wen_body') or body_parts[0]
    bb = body_ob.data.vertices
    if bb:
        mn = Vector((min(v.co.x for v in bb), min(v.co.y for v in bb), min(v.co.z for v in bb)))
        mx = Vector((max(v.co.x for v in bb), max(v.co.y for v in bb), max(v.co.z for v in bb)))
        body_len = max(mx.y - mn.y, mx.x - mn.x, mx.z - mn.z)
    else:
        body_len = 1.2

    def _wing_l(bm):
        ribs = bmesh.new()
        _wen_fan_wing(bm, 1.0, body_len, ribs)
        append_bmesh(bm, ribs)
        ribs.free()

    def _wing_r(bm):
        ribs = bmesh.new()
        _wen_fan_wing(bm, -1.0, body_len, ribs)
        append_bmesh(bm, ribs)
        ribs.free()

    for builder, nm in ((_wing_l, 'wen_wing_l'), (_wing_r, 'wen_wing_r')):
        wob = _solid_obj(nm, COL['wen_wing'], builder, parent=root, alpha=0.85)
        wob.data.materials[0].use_backface_culling = False
    _decimate_objects([c for c in root.children if c.type == 'MESH'], 720)
    _normalize_longest(1.0)
    _shade_smooth(_mesh_objs())
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
    shell_ob = bpy.data.objects.get('xuan_shell')
    if shell_ob:
        def _gold_build(bm):
            _shell_gold_hex_lines(bm, shell_ob)
        _solid_obj('xuan_gold', COL['xuan_gold'], _gold_build, parent=root)
    snake_pts = _snake_shell_path(turns=1.4, rad=0.43)
    if not _add_snake_quaternius(root, snake_pts):
        _fallback_snake_tubes(root, snake_pts)
    else:
        snake_obs = [c for c in root.children if c.name in ('snake', 'snake_belly', 'snake_eye')]
        smn, smx = _bounds_meshes(snake_obs)
        turtle_obs = [c for c in root.children if c.name.startswith('xuan_')]
        tmn, tmx = _bounds_meshes(turtle_obs)
        if (smx - smn).length > (tmx - tmn).length * 1.35:
            for ob in snake_obs:
                bpy.data.objects.remove(ob, do_unlink=True)
            _fallback_snake_tubes(root, snake_pts)
    _normalize_longest(1.0)
    turtle_meshes = [c for c in root.children if c.type == 'MESH' and c.name in ('xuan_shell', 'xuan_body')]
    _decimate_objects(turtle_meshes, 650)
    snake_meshes = [
        c for c in root.children
        if c.type == 'MESH' and c.name in ('snake', 'snake_belly', 'snake_eye')
    ]
    _decimate_objects(snake_meshes, 480)
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