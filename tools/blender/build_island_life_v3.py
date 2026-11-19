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
from mathutils import Euler, Matrix, Vector
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
    'gull_wing': 0xC9D1D9,
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
    'snake': 0x2F6B3A,
    'snake_belly': 0xB8C98A,
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


def _solid_obj(name, hx, builder, parent=None, alpha=1.0, emissive=None):
    bm = bmesh.new()
    builder(bm)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bm.normal_update()
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    mat = creature_solid_material(hx, name + '_mat', alpha=alpha, emissive=emissive)
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


def _shell_bvh_data(shell_ob):
    bvh_bm = bmesh.new()
    bvh_bm.from_mesh(shell_ob.data)
    bvh_bm.transform(shell_ob.matrix_world)
    bvh_bm.normal_update()
    if not bvh_bm.faces:
        bvh_bm.free()
        return None, None, None
    bvh = BVHTree.FromBMesh(bvh_bm)
    verts = [v.co.copy() for v in bvh_bm.verts]
    xs = [v.x for v in verts]
    ys = [v.y for v in verts]
    zs = [v.z for v in verts]
    meta = dict(
        min_x=min(xs), max_x=max(xs), min_y=min(ys), max_y=max(ys),
        max_z=max(zs), cx=(min(xs) + max(xs)) * 0.5, cy=(min(ys) + max(ys)) * 0.5,
    )
    return bvh, bvh_bm, meta


def _shell_top_hit(bvh, meta, x, y, lift=0.003):
    hit = bvh.ray_cast(Vector((x, y, meta['max_z'] + 1.0)), Vector((0.0, 0.0, -1.0)))
    if hit[0] is None:
        return None
    loc, nrm = hit[0], hit[1]
    n = nrm.normalized()
    if n.z < 0.5:
        return None
    return loc + n * lift, n


def _band_quad(bm, a0, n0, a1, n1, half_w):
    t = (a1 - a0).normalized()
    s0 = n0.cross(t)
    if s0.length < 1e-6:
        s0 = Vector((1.0, 0.0, 0.0))
    s0.normalize()
    s1 = n1.cross(t)
    if s1.length < 1e-6:
        s1 = s0
    else:
        s1.normalize()
    v = [
        bm.verts.new(a0 + s0 * half_w), bm.verts.new(a0 - s0 * half_w),
        bm.verts.new(a1 - s1 * half_w), bm.verts.new(a1 + s1 * half_w),
    ]
    bm.verts.ensure_lookup_table()
    try:
        bm.faces.new(v)
    except ValueError:
        pass


def _shell_gold_hex_lines(bm, shell_ob):
    """殼頂上表面：BVH 六角細帶（非圓管）＋殼緣內側金邊。"""
    pack = _shell_bvh_data(shell_ob)
    if pack[0] is None:
        return
    bvh, bvh_bm, meta = pack
    span = max(meta['max_x'] - meta['min_x'], meta['max_y'] - meta['min_y'])
    cell = span / 6.0
    half_w = max(0.010, min(0.014, cell * 0.09))
    span_lim = span * 0.45

    def _edge_samples(x0, y0, x1, y1, steps=11):
        pts = []
        for s in range(steps + 1):
            t = s / steps
            hit = _shell_top_hit(bvh, meta, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
            if hit is None:
                return None
            pts.append(hit)
        for i in range(len(pts) - 1):
            if abs(pts[i][0].z - pts[i + 1][0].z) > 0.02:
                return None
        return pts

    rings = 2
    for q in range(-rings, rings + 1):
        for r in range(-rings, rings + 1):
            if abs(q + r) > rings:
                continue
            cx = meta['cx'] + cell * 1.5 * q
            cy = meta['cy'] + cell * math.sqrt(3.0) * (r + q / 2.0)
            if (cx - meta['cx']) ** 2 + (cy - meta['cy']) ** 2 > span_lim * span_lim:
                continue
            for k in range(6):
                a0 = (k / 6.0) * math.pi * 2.0 + math.pi / 6.0
                a1 = ((k + 1) / 6.0) * math.pi * 2.0 + math.pi / 6.0
                x0, y0 = cx + cell * math.cos(a0) * 0.88, cy + cell * math.sin(a0) * 0.88
                x1, y1 = cx + cell * math.cos(a1) * 0.88, cy + cell * math.sin(a1) * 0.88
                pts = _edge_samples(x0, y0, x1, y1)
                if not pts:
                    continue
                for i in range(len(pts) - 1):
                    _band_quad(bm, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], half_w * 0.5)

    rim_n = 40
    rim_pts = []
    rx = (meta['max_x'] - meta['min_x']) * 0.42
    ry = (meta['max_y'] - meta['min_y']) * 0.40
    for i in range(rim_n):
        ang = (i / rim_n) * math.pi * 2.0
        hit = _shell_top_hit(
            bvh, meta,
            meta['cx'] + math.cos(ang) * rx,
            meta['cy'] + math.sin(ang) * ry * 0.88,
            lift=0.0025,
        )
        if hit:
            rim_pts.append(hit)
    for i in range(len(rim_pts)):
        j = (i + 1) % len(rim_pts)
        _band_quad(bm, rim_pts[i][0], rim_pts[i][1], rim_pts[j][0], rim_pts[j][1], half_w * 0.35)
    bvh_bm.free()


def _catmull_rom(p0, p1, p2, p3, t):
    t2 = t * t
    t3 = t2 * t
    return 0.5 * (
        (2.0 * p1)
        + (-p0 + p2) * t
        + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
        + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
    )


def _resample_catmull_rom(control, count=128, closed=True):
    n = len(control)
    if n < 4:
        return [Vector(p) for p in control]
    dense = []
    segs = max(12, count // max(1, n - (0 if closed else 1)))
    loops = n if closed else n - 1
    for i in range(loops):
        i0 = (i - 1) % n
        i1 = i
        i2 = (i + 1) % n
        i3 = (i + 2) % n
        p0, p1, p2, p3 = control[i0], control[i1], control[i2], control[i3]
        for s in range(segs):
            t = s / segs
            dense.append(_catmull_rom(p0, p1, p2, p3, t))
    if not closed:
        dense.append(Vector(control[-1]))
    acc = [0.0]
    total = 0.0
    for i in range(1, len(dense)):
        total += (dense[i] - dense[i - 1]).length
        acc.append(total)
    if total < 1e-6:
        return dense
    out = []
    for k in range(count):
        target = (k / max(1, count - 1)) * total
        j = 0
        while j < len(acc) - 1 and acc[j + 1] < target:
            j += 1
        if j >= len(dense) - 1:
            out.append(dense[-1].copy())
            continue
        seg = acc[j + 1] - acc[j]
        t = 0.0 if seg < 1e-8 else (target - acc[j]) / seg
        out.append(dense[j].lerp(dense[j + 1], t))
    return out


def _parallel_transport_frames(path):
    if len(path) < 2:
        return []
    tangents = []
    for i in range(len(path)):
        if i == 0:
            tangents.append((path[1] - path[0]).normalized())
        elif i == len(path) - 1:
            tangents.append((path[-1] - path[-2]).normalized())
        else:
            tangents.append((path[i + 1] - path[i - 1]).normalized())
    up_hint = Vector((0.0, 0.0, 1.0))
    side = tangents[0].cross(up_hint)
    if side.length < 1e-5:
        side = Vector((1.0, 0.0, 0.0))
    else:
        side.normalize()
    up = side.cross(tangents[0]).normalized()
    frames = [(path[0].copy(), tangents[0], side, up)]
    for i in range(1, len(path)):
        t_prev, t_curr = frames[-1][1], tangents[i]
        side_prev, up_prev = frames[-1][2], frames[-1][3]
        axis = t_prev.cross(t_curr)
        if axis.length < 1e-6:
            side_n, up_n = side_prev, up_prev
        else:
            ang = t_prev.angle(t_curr)
            rot = Matrix.Rotation(ang, 3, axis.normalized())
            side_n = rot @ side_prev
            up_n = rot @ up_prev
        side_n = side_n - t_curr * t_curr.dot(side_n)
        if side_n.length > 1e-6:
            side_n.normalize()
        else:
            side_n = side_prev
        up_n = t_curr.cross(side_n).normalized()
        frames.append((path[i].copy(), t_curr, side_n, up_n))
    return frames


def _interp_frame(frames, s):
    s = max(0.0, min(1.0, s))
    f = s * (len(frames) - 1)
    i = int(f)
    t = f - i
    i = min(i, len(frames) - 2)
    p0, t0, s0, u0 = frames[i]
    p1, t1, s1, u1 = frames[i + 1]
    pos = p0.lerp(p1, t)
    tang = (t0 * (1.0 - t) + t1 * t).normalized()
    side = (s0 * (1.0 - t) + s1 * t)
    side -= tang * tang.dot(side)
    if side.length > 1e-6:
        side.normalize()
    else:
        side = s0
    up = tang.cross(side).normalized()
    return pos, tang, side, up


def _get_image_texture(ob):
    for slot in ob.material_slots:
        mat = slot.material
        if not mat or not mat.use_nodes:
            continue
        for node in mat.node_tree.nodes:
            if node.type == 'TEX_IMAGE' and node.image:
                return node.image
    return None


def _sample_tex_rgb(img, u, v):
    w, h = img.size[0], img.size[1]
    if w < 1 or h < 1:
        return (0.5, 0.5, 0.5)
    x = max(0, min(w - 1, int(u * (w - 1) + 0.5)))
    y = max(0, min(h - 1, int((1.0 - v) * (h - 1) + 0.5)))
    idx = (y * w + x) * 4
    px = img.pixels
    return (px[idx], px[idx + 1], px[idx + 2])


def _tex_bucket(r, g, b):
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    if lum < 0.36 and max(r, g, b) < 0.42:
        return 'black'
    if (r > 0.40 and g > 0.28 and b < 0.58 and r >= g * 0.88) or (
        r > g * 1.02 and r > b * 1.05 and 0.34 < lum < 0.88
    ):
        return 'orange'
    if lum > 0.78:
        return 'white'
    if g > r * 1.05 and g > b * 1.05:
        return 'green_dark' if lum < 0.50 else 'green_light'
    if lum > 0.42:
        return 'gray'
    return 'green_dark'


def _face_tex_bucket(face, uv_layer, img):
    if img is None or uv_layer is None:
        return 'white'
    uvs = [loop[uv_layer].uv for loop in face.loops]
    u = sum(p.x for p in uvs) / len(uvs)
    v = sum(p.y for p in uvs) / len(uvs)
    r, g, b = _sample_tex_rgb(img, u, v)
    return _tex_bucket(r, g, b)


def _snake_path_from_shell(shell_ob, turns=1.02):
    pack = _shell_bvh_data(shell_ob)
    if pack[0] is None:
        raise RuntimeError('xuan shell BVH missing for snake path')
    bvh, bvh_bm, meta = pack
    rx = (meta['max_x'] - meta['min_x']) * 0.46
    ry = (meta['max_y'] - meta['min_y']) * 0.43

    def _snap(x, y, lift=0.0):
        hit2 = bvh.ray_cast(Vector((x, y, meta['max_z'] + 1.0)), Vector((0.0, 0.0, -1.0)))
        if hit2[0] is None:
            return None
        return hit2[0] + Vector((0.0, 0.0, lift))

    span = max(meta['max_x'] - meta['min_x'], meta['max_y'] - meta['min_y'])
    rim_h = meta['max_z'] - span * 0.07
    ctrl_n = 18
    control = []
    for i in range(ctrl_n):
        ang = (i / ctrl_n) * turns * math.pi * 2.0 - math.pi * 0.5
        x = meta['cx'] + math.cos(ang) * rx
        y = meta['cy'] + math.sin(ang) * ry
        lift = 0.24 if i == int(ctrl_n * 0.25) else 0.004
        p = _snap(x, y, lift)
        if p is None:
            p = Vector((x, y, rim_h + lift))
        control.append(p)
    tail_ang = (0.70 / ctrl_n) * turns * math.pi * 2.0 - math.pi * 0.5
    p_tail = _snap(
        meta['cx'] + math.cos(tail_ang) * rx * 0.55,
        meta['cy'] + math.sin(tail_ang) * ry * 0.55,
        0.002,
    )
    control.append(p_tail if p_tail is not None else Vector((
        meta['cx'] + math.cos(tail_ang) * rx * 0.55,
        meta['cy'] + math.sin(tail_ang) * ry * 0.55,
        rim_h,
    )))
    bvh_bm.free()
    if len(control) < 8:
        raise RuntimeError('snake shell path too sparse')
    return _resample_catmull_rom(control, count=130, closed=True)


def _orient_snake_y_plus_head(src):
    mn, mx = _bounds_meshes([src])
    dims = mx - mn
    longest = sorted([('x', dims.x), ('y', dims.y), ('z', dims.z)], key=lambda t: t[1])[-1][0]
    if longest != 'y':
        if longest == 'x':
            src.rotation_euler = Euler((0.0, 0.0, math.pi / 2.0))
        else:
            src.rotation_euler = Euler((math.pi / 2.0, 0.0, 0.0))
        bpy.context.view_layer.objects.active = src
        src.select_set(True)
        bpy.ops.object.transform_apply(rotation=True)
        src.select_set(False)
        mn, mx = _bounds_meshes([src])
    cy = sum(v.co.y for v in src.data.vertices) / len(src.data.vertices)
    if cy < (mn.y + mx.y) * 0.5:
        src.rotation_euler = Euler((math.pi, 0.0, 0.0))
        bpy.context.view_layer.objects.active = src
        src.select_set(True)
        bpy.ops.object.transform_apply(rotation=True)
        src.select_set(False)
        mn, mx = _bounds_meshes([src])
    return mn, mx


def _snap_snake_to_turtle(ob, root, clearance=0.0015):
    turtle = [c for c in root.children if c.name in ('xuan_shell', 'xuan_body') and c.type == 'MESH']
    if not turtle:
        return
    bvh_bm = bmesh.new()
    for tob in turtle:
        sub = bmesh.new()
        sub.from_mesh(tob.data)
        sub.transform(tob.matrix_world)
        append_bmesh(bvh_bm, sub)
        sub.free()
    bvh = BVHTree.FromBMesh(bvh_bm)
    mw = ob.matrix_world
    imw = mw.inverted()
    me = ob.data
    mn, mx = _bounds_meshes([ob])
    span = mx.y - mn.y
    for v in me.vertices:
        ly = (v.co.y - mn.y) / max(span, 1e-6)
        if ly > 0.94:
            continue
        wp = mw @ v.co
        loc, nrm, _idx, dist = bvh.find_nearest(wp)
        if loc is None or dist is None or dist > 0.34:
            continue
        nn = nrm.normalized() if nrm.length > 1e-6 else Vector((0.0, 0.0, 1.0))
        target = loc + nn * clearance
        v.co = imw @ target
    me.update()
    bvh_bm.free()


def _deform_along_path(ob, frames, path_len):
    mn, mx = _bounds_meshes([ob])
    body_len = mx.y - mn.y
    if body_len < 1e-6:
        raise RuntimeError('snake body length zero')
    me = ob.data
    for v in me.vertices:
        s = (v.co.y - mn.y) / body_len
        pos, tang, side, up = _interp_frame(frames, s)
        v.co[:] = pos + side * v.co.x + up * v.co.z
    me.update()


def _scale_snake_cross_section(ob, factor):
    for v in ob.data.vertices:
        v.co.x *= factor
        v.co.z *= factor
    ob.data.update()


def _snake_contact_stats(root):
    turtle = [c for c in root.children if c.name in ('xuan_shell', 'xuan_body') and c.type == 'MESH']
    snakes = [c for c in root.children if c.name in ('snake', 'snake_belly', 'snake_eye') and c.type == 'MESH']
    if not turtle or not snakes:
        return 0.0, 0
    bvh_bm = bmesh.new()
    for ob in turtle:
        sub = bmesh.new()
        sub.from_mesh(ob.data)
        sub.transform(ob.matrix_world)
        append_bmesh(bvh_bm, sub)
        sub.free()
    bvh = BVHTree.FromBMesh(bvh_bm)
    near = 0
    total = 0
    for ob in snakes:
        mw = ob.matrix_world
        verts = ob.data.vertices
        for i in range(0, len(verts), 2):
            p = mw @ verts[i].co
            total += 1
            loc, _n, _i, _d = bvh.find_nearest(p)
            if loc is not None and (p - loc).length <= 0.03:
                near += 1
    bvh_bm.free()
    ratio = near / max(1, total)
    return ratio, total


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


def _add_snake_long_polygoogle(root, shell_ob, path_pts):
    before = {ob.as_pointer() for ob in bpy.data.objects}
    path = [Vector(p) if not isinstance(p, Vector) else p.copy() for p in path_pts]
    path_len = sum((path[i + 1] - path[i]).length for i in range(len(path) - 1))
    if path_len < 1e-4:
        raise RuntimeError('snake path length invalid')
    _import_glb(os.path.join(SRC, 'snake-long-polygoogle.glb'))
    new_meshes = [ob for ob in bpy.data.objects if ob.type == 'MESH' and ob.as_pointer() not in before]
    if not new_meshes:
        raise RuntimeError('snake-long import produced no meshes')
    src = _flatten_mesh_list(new_meshes)
    if src is None:
        raise RuntimeError('snake-long flatten failed')
    img = _get_image_texture(src)
    mn, mx = _orient_snake_y_plus_head(src)
    body_len = mx.y - mn.y
    fac = (path_len / body_len) * 0.96 if body_len > 1e-6 else 1.0
    src.scale = (fac * 0.82, fac, fac * 0.82)
    bpy.context.view_layer.objects.active = src
    src.select_set(True)
    bpy.ops.object.transform_apply(scale=True)
    src.select_set(False)
    bpy.context.view_layer.objects.active = src
    src.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.subdivide(number_cuts=2)
    bpy.ops.object.mode_set(mode='OBJECT')
    src.select_set(False)
    turtle_obs = [c for c in root.children if c.name.startswith('xuan_')]
    tmn, tmx = _bounds_meshes(turtle_obs)
    t_w = max(tmx.x - tmn.x, tmx.y - tmn.y)
    smn, smx = _bounds_meshes([src])
    s_w = max(smx.x - smn.x, smx.z - smn.z)
    if s_w > t_w * 0.22:
        _scale_snake_cross_section(src, (t_w * 0.20) / max(s_w, 1e-6))
        mn, mx = _bounds_meshes([src])
    frames = _parallel_transport_frames(path)
    _deform_along_path(src, frames, path_len)
    src.parent = root
    _snap_snake_to_turtle(src, root)
    _snap_snake_to_turtle(src, root)
    bm = bmesh.new()
    bm.from_mesh(src.data)
    bm.faces.ensure_lookup_table()
    bm.normal_update()
    uv_layer = bm.loops.layers.uv.active
    buckets = {f.index: _face_tex_bucket(f, uv_layer, img) for f in bm.faces}

    def _is_eye(f):
        return buckets.get(f.index) == 'black'

    def _is_belly(f):
        b = buckets.get(f.index, 'green_dark')
        return b in ('white', 'gray', 'green_light') or f.normal.dot(Vector((0, 0, -1))) > 0.35

    me_backup = src.data
    bm.to_mesh(me_backup)
    bm.free()
    _split_src_mesh(
        src,
        [
            ('snake_eye', COL['black'], 1.0, _is_eye),
            ('snake_belly', COL['snake_belly'], 1.0, _is_belly),
            ('snake', COL['snake'], 1.0, lambda f: True),
        ],
        root,
    )
    for ob in list(bpy.data.objects):
        if ob.as_pointer() not in before and ob.type == 'MESH' and ob.parent is None:
            bpy.data.objects.remove(ob, do_unlink=True)


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
    img = _get_image_texture(src)
    _orient_long_axis_x()
    _center_at_origin()
    root = _make_export_root()
    if src.parent != root:
        src.parent = root
    mn, mx = _bounds_meshes([src])
    bm0 = bmesh.new()
    bm0.from_mesh(src.data)
    bm0.faces.ensure_lookup_table()
    uv_layer = bm0.loops.layers.uv.active
    buckets = {f.index: _face_tex_bucket(f, uv_layer, img) for f in bm0.faces}
    orange_x = [_face_center(f).x for f in bm0.faces if buckets.get(f.index) == 'orange']
    max_orange_x = max(orange_x) if orange_x else mx.x
    bm0.free()
    span = mx.x - mn.x
    y_half = (mx.y - mn.y) * 0.5
    mid_y = (mn.y + mx.y) * 0.5
    beak_x_cut = max_orange_x - span * 0.035

    def _beak(f):
        c = _face_center(f)
        return (
            buckets.get(f.index) == 'orange'
            and c.x >= beak_x_cut
            and abs(c.z) < span * 0.05
            and abs(c.y - mid_y) < y_half * 0.12
        )

    def _tip(f):
        c = _face_center(f)
        wing_span = (mx.x - mn.x) * 0.5
        b = buckets.get(f.index)
        outer = abs(c.x - (mn.x + mx.x) * 0.5) > wing_span * 0.52
        return outer and b in ('black', 'gray', 'green_dark')

    def _wing(f):
        c = _face_center(f)
        b = buckets.get(f.index)
        if b in ('orange', 'black'):
            return False
        return abs(c.x - (mn.x + mx.x) * 0.5) > span * 0.11 and (
            b == 'gray' or (b == 'white' and (f.normal.z > 0.15 or abs(c.x) > span * 0.22))
        )

    _split_src_mesh(
        src,
        [
            ('gull_beak', COL['gull_beak'], 1.0, _beak),
            ('gull_tip', COL['gull_tip'], 1.0, _tip),
            ('gull_wing', COL['gull_wing'], 1.0, _wing),
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
        gold_ob = _solid_obj('xuan_gold', COL['xuan_gold'], _gold_build, parent=root, emissive=0x3A2A08)
        gmat = gold_ob.data.materials[0]
        bsdf = gmat.node_tree.nodes.get('Principled BSDF')
        if bsdf:
            try:
                bsdf.inputs['Emission Strength'].default_value = 0.42
            except KeyError:
                bsdf.inputs['Emission'].default_value = 0.42
        gmat.use_backface_culling = False
    snake_pts = _snake_path_from_shell(shell_ob)
    _add_snake_long_polygoogle(root, shell_ob, snake_pts)
    touch, sv = _snake_contact_stats(root)
    print(f'XUANWU snake_touch_ratio={touch:.3f} snake_verts={sv}')
    if touch < 0.60:
        raise RuntimeError(f'snake shell contact {touch:.3f} < 0.60')
    snake_obs = [c for c in root.children if c.name in ('snake', 'snake_belly', 'snake_eye')]
    smn, smx = _bounds_meshes(snake_obs)
    turtle_obs = [c for c in root.children if c.name.startswith('xuan_')]
    tmn, tmx = _bounds_meshes(turtle_obs)
    t_w = max(tmx.x - tmn.x, tmx.y - tmn.y)
    s_w = max(smx.x - smn.x, smx.y - smn.y)
    if s_w > t_w * 1.15:
        raise RuntimeError(f'snake too wide {s_w:.3f} vs turtle {t_w:.3f}')
    _normalize_longest(1.0)
    turtle_meshes = [c for c in root.children if c.type == 'MESH' and c.name in ('xuan_shell', 'xuan_body')]
    _decimate_objects(turtle_meshes, 650)
    snake_meshes = [
        c for c in root.children
        if c.type == 'MESH' and c.name in ('snake', 'snake_belly', 'snake_eye')
    ]
    _decimate_objects(snake_meshes, 480)
    for ob in snake_meshes:
        for _ in range(3):
            _snap_snake_to_turtle(ob, root)
    touch_final, _ = _snake_contact_stats(root)
    print(f'XUANWU snake_touch_final={touch_final:.3f}')
    if touch_final < 0.60:
        raise RuntimeError(f'snake post-decimate contact {touch_final:.3f} < 0.60')
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