"""針灸銅人 GLB：MakeHuman 底模、青銅 PBR、經絡烘培紋理、穴位表面 JSON。

無頭重建：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_tcm_bronze_figure.py -- \\
    server/src/main/resources/public/models/tcm-bronze-figure.glb
"""
from __future__ import annotations

import json
import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_REPO = os.path.normpath(os.path.join(_HERE, '..', '..'))
sys.path.insert(0, _HERE)

import bpy
import bmesh
import numpy as np
from mathutils import Matrix, Vector

from kit import argv_out, hex_rgba, prepare_scene

DATA_BODY_HEIGHT = 1.72
MODEL_HEIGHT_TOTAL = 1.92
TEX_SIZE = 1024
BRONZE_HEX = 0xcda862
BRONZE_DARK_HEX = 0xab8748
MERIDIAN_DARK_HEX = 0x4a3214
BRONZE_METAL = 0.97
BRONZE_ROUGH = 0.26
MERIDIAN_METAL = 0.9
MERIDIAN_ROUGH = 0.45
MERIDIAN_BEVEL_R = 0.00175
MERIDIAN_SURFACE_OFFSET = 0.0015
HAIR_HEX = 0x54381C
MERIDIAN_PATH_STEP = 0.008
MERIDIAN_EDGE_GAP_MAX = 0.004
MERIDIAN_BREAK_GAP = 0.006
MERIDIAN_EDGE_LEN_MAX = 0.012
MERIDIAN_MAX_TRIS = 20000
PEDESTAL_BASE_H = 0.072
PEDESTAL_MID_H = 0.064
PEDESTAL_TOP_H = 0.056
PEDESTAL_TOP_Z = PEDESTAL_BASE_H + PEDESTAL_MID_H + PEDESTAL_TOP_H
BODY_FOOT_Z = PEDESTAL_TOP_Z
BASE_GLB = os.path.join(_HERE, 'assets', 'bronze-base', 'mh-female-base.glb')
POINTS_JSON = os.path.join(_REPO, 'server/src/main/resources/public/island/tcm-bronze-points.json')
SURFACE_JSON = os.path.join(_REPO, 'server/src/main/resources/public/island/tcm-bronze-surface.json')


def data_to_blender(px, py, pz):
    return Vector((px, -pz, py))


def blender_to_data(v):
    return (float(v.x), float(v.z), float(-v.y))


def blender_normal_to_data(n):
    n = n.normalized()
    return (float(n.x), float(n.z), float(-n.y))


def _mat_pbr(name, base_hex, rough=0.3, metal=0.0, tex=None, normal_tex=None, rough_tex=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nodes, links = nt.nodes, nt.links
    for n in list(nodes):
        nodes.remove(n)
    out = nodes.new('ShaderNodeOutputMaterial')
    bsdf = nodes.new('ShaderNodeBsdfPrincipled')
    bsdf.inputs['Roughness'].default_value = rough
    bsdf.inputs['Metallic'].default_value = metal
    bsdf.inputs['Base Color'].default_value = hex_rgba(base_hex)
    if tex is not None:
        t = nodes.new('ShaderNodeTexImage')
        t.image = tex
        t.image.colorspace_settings.name = 'sRGB'
        links.new(t.outputs['Color'], bsdf.inputs['Base Color'])
    if normal_tex is not None:
        t_n = nodes.new('ShaderNodeTexImage')
        t_n.image = normal_tex
        t_n.image.colorspace_settings.name = 'Non-Color'
        nm = nodes.new('ShaderNodeNormalMap')
        nm.inputs['Strength'].default_value = 0.85
        links.new(t_n.outputs['Color'], nm.inputs['Color'])
        links.new(nm.outputs['Normal'], bsdf.inputs['Normal'])
    if rough_tex is not None:
        t_r = nodes.new('ShaderNodeTexImage')
        t_r.image = rough_tex
        t_r.image.colorspace_settings.name = 'Non-Color'
        links.new(t_r.outputs['Color'], bsdf.inputs['Roughness'])
    links.new(bsdf.outputs['BSDF'], out.inputs['Surface'])
    out.location = (400, 0)
    bsdf.location = (100, 0)
    return m


def _mesh_from_bm(bm, name, mat, flat=False):
    if flat:
        for f in bm.faces:
            f.smooth = False
        bmesh.ops.split_edges(bm, edges=list(bm.edges), use_verts=False)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    if mat:
        ob.data.materials.append(mat)
    for p in me.polygons:
        p.use_smooth = not flat
    me.update()
    return ob


def _print_world_bounds(label):
    mn = Vector((1e9, 1e9, 1e9))
    mx = Vector((-1e9, -1e9, -1e9))
    for ob in bpy.data.objects:
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
    print(
        f'BOUNDS_BLENDER {label} '
        f'x=[{mn.x:.3f},{mx.x:.3f}] y=[{mn.y:.3f},{mx.y:.3f}] z=[{mn.z:.3f},{mx.z:.3f}]'
    )
    y_up = (mn.z, mx.z)
    x_lr = (mn.x, mx.x)
    z_fb = (-mx.y, -mn.y)
    print(
        f'BOUNDS_GLTF_YUP {label} '
        f'y(height)=[{y_up[0]:.3f},{y_up[1]:.3f}] x=[{x_lr[0]:.3f},{x_lr[1]:.3f}] '
        f'z(front)=[{z_fb[0]:.3f},{z_fb[1]:.3f}]'
    )
    return mn, mx


def _import_body():
    if not os.path.isfile(BASE_GLB):
        raise FileNotFoundError(BASE_GLB)
    bpy.ops.import_scene.gltf(filepath=BASE_GLB)
    body = None
    for ob in list(bpy.data.objects):
        if ob.type != 'MESH':
            continue
        if ob.name == 'BronzeBaseFemale':
            body = ob
        elif body is None and len(ob.data.vertices) > 1000:
            body = ob
        else:
            bpy.data.objects.remove(ob, do_unlink=True)
    if body is None:
        raise RuntimeError('no body mesh in base GLB')
    body.name = 'bronze_body'
    return body


def _tri_count(ob):
    return sum(len(p.vertices) - 2 for p in ob.data.polygons)


def _nipple_hubs(body):
    """左右胸部最前凸點（教學模型抹平用）。"""
    hubs = []
    for side in (1, -1):
        best = None
        best_y = 1e9
        for v in body.data.vertices:
            co = v.co
            if co.z < 1.02 or co.z > 1.38:
                continue
            if side > 0 and co.x < 0.045:
                continue
            if side < 0 and co.x > -0.045:
                continue
            if co.y > -0.02:
                continue
            if co.y < best_y:
                best_y = co.y
                best = co.copy()
        if best is not None:
            hubs.append(best)
    if len(hubs) < 2:
        hubs = [
            Vector((0.082, -0.068, 1.198)),
            Vector((-0.082, -0.068, 1.198)),
        ]
    return tuple(hubs)


def _smoothstep(edge0, edge1, x):
    t = max(0.0, min(1.0, (x - edge0) / max(edge1 - edge0, 1e-9)))
    return t * t * (3.0 - 2.0 * t)


def _fit_quad_height(samples):
    """samples: list of (u, v, h) → coeffs for h = au²+bv²+cuv+du+ev+f."""
    if len(samples) < 10:
        return None
    rows = []
    targets = []
    for u, v, h in samples:
        rows.append([u * u, v * v, u * v, u, v, 1.0])
        targets.append(h)
    a = np.array(rows, dtype=np.float64)
    b = np.array(targets, dtype=np.float64)
    try:
        coeffs, *_ = np.linalg.lstsq(a, b, rcond=None)
    except np.linalg.LinAlgError:
        return None
    return coeffs


def _chest_second_diff_max(bm, hubs, r_lim=0.06):
    """乳房區沿 Z 剖面之二階差分最大值（與平滑區對照用）。"""
    max_chest = 0.0
    max_other = 0.0
    for hub in hubs:
        ring_z = sorted(
            {round(v.co.z, 3) for v in bm.verts if (v.co - hub).length < r_lim and v.co.z > 1.0}
        )
        for z0 in ring_z[:: max(1, len(ring_z) // 6)]:
            line = sorted(
                [v.co.y for v in bm.verts if abs(v.co.z - z0) < 0.003 and (v.co - hub).length < r_lim],
            )
            if len(line) < 5:
                continue
            d2 = [abs(line[i + 1] - 2 * line[i] + line[i - 1]) for i in range(1, len(line) - 1)]
            if d2:
                max_chest = max(max_chest, max(d2))
    ref_z = 0.92
    ref_line = sorted([v.co.y for v in bm.verts if abs(v.co.z - ref_z) < 0.008 and abs(v.co.x) < 0.22])
    if len(ref_line) >= 5:
        d2 = [abs(ref_line[i + 1] - 2 * ref_line[i] + ref_line[i - 1]) for i in range(1, len(ref_line) - 1)]
        if d2:
            max_other = max(d2)
    return max_chest, max_other


def _smooth_breast_detail(body):
    """胸部：外圈二次曲面擬合 + 權重 lerp + 局部 Laplacian（無球面投影）。"""
    R_RING_LO = 0.045
    R_RING_HI = 0.068
    R_BLEND_FULL = 0.030
    R_BLEND_FADE = 0.060
    me = body.data
    orig = [v.co.copy() for v in me.vertices]
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.verts.ensure_lookup_table()
    hubs = _nipple_hubs(body)
    weight = {v.index: 0.0 for v in bm.verts}
    touched = set()

    for hub in hubs:
        ring = []
        for v in bm.verts:
            if v.co.z < 1.0 or v.co.z > 1.42 or v.co.y > 0.02:
                continue
            d = (v.co - hub).length
            if R_RING_LO <= d <= R_RING_HI:
                ring.append(v)
        if len(ring) < 14:
            continue
        normal = Vector((0.0, 0.0, 0.0))
        for v in ring:
            for p in v.link_faces:
                normal += p.normal
        if normal.length < 1e-6:
            normal = Vector((0.0, -1.0, 0.2))
        normal.normalize()
        tangent_u = Vector((1.0, 0.0, 0.0))
        if abs(normal.dot(tangent_u)) > 0.92:
            tangent_u = Vector((0.0, 0.0, 1.0))
        tangent_v = normal.cross(tangent_u).normalized()
        tangent_u = tangent_v.cross(normal).normalized()
        samples = []
        for v in ring:
            off = v.co - hub
            u = off.dot(tangent_u)
            vv = off.dot(tangent_v)
            h = off.dot(normal)
            samples.append((u, vv, h))
        coeffs = _fit_quad_height(samples)
        if coeffs is None:
            continue

        def h_quad(u, vv):
            a, b, c, d, e, f = coeffs
            return a * u * u + b * vv * vv + c * u * vv + d * u + e * vv + f

        for v in bm.verts:
            if v.co.z < 0.98 or v.co.z > 1.44:
                continue
            off = v.co - hub
            d = off.length
            if d > R_RING_HI + 0.008:
                continue
            u = off.dot(tangent_u)
            vv = off.dot(tangent_v)
            h_orig = (orig[v.index] - hub).dot(normal)
            h_new = h_quad(u, vv)
            w = 1.0 if d <= R_BLEND_FULL else (0.0 if d >= R_BLEND_FADE else 1.0 - _smoothstep(R_BLEND_FULL, R_BLEND_FADE, d))
            if w <= 0.0:
                continue
            h_blend = h_orig * (1.0 - w) + h_new * w
            v.co = hub + tangent_u * u + tangent_v * vv + normal * h_blend
            weight[v.index] = max(weight[v.index], w)
            touched.add(v.index)

    smooth_verts = [v for v in bm.verts if weight.get(v.index, 0.0) > 0.05]
    for _ in range(11):
        bmesh.ops.smooth_vert(
            bm,
            verts=smooth_verts,
            factor=0.35,
            use_axis_x=True,
            use_axis_y=True,
            use_axis_z=True,
        )
        for v in smooth_verts:
            w = weight.get(v.index, 0.0)
            if w >= 0.999:
                continue
            o = orig[v.index]
            v.co = o.lerp(v.co, w)
    bm.to_mesh(me)
    bm.free()
    me.update()
    bm2 = bmesh.new()
    bm2.from_mesh(me)
    d2_chest, d2_ref = _chest_second_diff_max(bm2, hubs)
    bm2.free()
    residual_bump = 0.0
    for hub in hubs:
        for i, o in enumerate(orig):
            if abs(o.z - hub.z) > 0.14:
                continue
            if (o - hub).length > 0.035:
                continue
            wco = me.vertices[i].co
            off = wco - hub
            if off.length < 1e-5:
                continue
            bump = max(0.0, off.dot(Vector((0.0, -1.0, 0.0)).normalized()) - (o - hub).dot(Vector((0.0, -1.0, 0.0)).normalized()))
            residual_bump = max(residual_bump, bump)
    print(
        f'[body] chest hubs=({hubs[0].x:.3f},{hubs[0].y:.3f},{hubs[0].z:.3f}) '
        f'verts_touched={len(touched)} d2_chest={d2_chest*1000:.3f}mm d2_ref={d2_ref*1000:.3f}mm '
        f'residual_bump_mm={residual_bump * 1000:.2f}'
    )
    body['chest_max_bump_m'] = residual_bump
    return residual_bump


def _shrinkwrap_to_body(ob, body, offset=0.007):
    mod = ob.modifiers.new('Shrinkwrap', 'SHRINKWRAP')
    mod.target = body
    mod.wrap_method = 'NEAREST_SURFACEPOINT'
    try:
        mod.wrap_mode = 'OUTSIDE'
    except (TypeError, AttributeError):
        mod.wrap_mode = 'ON_SURFACE'
    mod.offset = offset
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    ob.select_set(False)


def _optional_decimate(body, limit=30000):
    tris = _tri_count(body)
    if tris <= limit:
        print(f'[body] tris={tris} (no decimate)')
        return tris
    ratio = limit / tris
    mod = body.modifiers.new('Decimate', 'DECIMATE')
    mod.ratio = min(0.95, ratio * 1.02)
    bpy.context.view_layer.objects.active = body
    body.select_set(True)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    body.select_set(False)
    tris = _tri_count(body)
    print(f'[body] decimated to tris≈{tris}')
    return tris


def _stripe_image():
    """沿帶長（影像寬向）五寬條紋：紅、白、藍、白、紅。"""
    w, h = 512, 64
    img = bpy.data.images.new('sash_stripe', w, h)
    arr = np.ones((h, w, 4), dtype=np.float32)
    bands = [
        (200 / 255.0, 39 / 255.0, 46 / 255.0, 1.0),
        (242 / 255.0, 238 / 255.0, 230 / 255.0, 1.0),
        (35 / 255.0, 65 / 255.0, 154 / 255.0, 1.0),
        (242 / 255.0, 238 / 255.0, 230 / 255.0, 1.0),
        (200 / 255.0, 39 / 255.0, 46 / 255.0, 1.0),
    ]
    edges = [int(w * i / 5) for i in range(6)]
    for bi in range(5):
        x0, x1 = edges[bi], edges[bi + 1]
        arr[:, x0:x1, :] = bands[bi]
    img.pixels.foreach_set(arr.ravel())
    img.pack()
    return img


def _make_stripe_tex():
    img = _stripe_image()
    path = os.path.join(bpy.app.tempdir, 'sash_stripe.png')
    img.filepath_raw = path
    img.file_format = 'PNG'
    img.save()
    return path


def _hair_face_ok(face, z_hairline=1.638):
    """盤髻用薄殼：額頭全露、鬢不蓋耳，只留頂與後腦。"""
    verts = list(face.verts)
    max_z = max(v.co.z for v in verts)
    min_z = min(v.co.z for v in verts)
    avg_y = sum(v.co.y for v in verts) / len(verts)
    avg_x = sum(abs(v.co.x) for v in verts) / len(verts)
    if max_z < z_hairline - 0.020:
        return False
    if any(v.co.y < -0.045 and v.co.z < z_hairline + 0.032 for v in verts):
        return False
    if avg_y < -0.010 and min_z < z_hairline + 0.012:
        return False
    if avg_y < -0.028 and max_z < z_hairline + 0.038:
        return False
    if avg_x > 0.095 and avg_y < 0.0 and max_z < z_hairline + 0.024:
        return False
    return True


def _mesh_merge_centerline(ob, threshold=0.00012):
    """僅合併背面 x≈0 接縫，避免誤刪整片髮殼。"""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bm.verts.ensure_lookup_table()
    seam = [v for v in bm.verts if abs(v.co.x) <= threshold * 1.5 and v.co.y > 0.01]
    if len(seam) >= 2:
        bmesh.ops.remove_doubles(bm, verts=seam, dist=threshold)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()


def _hair_streak_normal_image():
    w, h = 256, 256
    arr = np.zeros((h, w, 4), dtype=np.float32)
    arr[:, :, 0] = 0.5
    arr[:, :, 1] = 0.5
    arr[:, :, 2] = 1.0
    arr[:, :, 3] = 1.0
    for y in range(h):
        phase = y * 0.09
        for x in range(w):
            arr[y, x, 0] = 0.5 + 0.06 * math.sin(x * 0.22 + phase)
            arr[y, x, 1] = 0.5 + 0.04 * math.cos(x * 0.17 + phase * 1.1)
    img = bpy.data.images.new('bronze_hair_normal', w, h, alpha=False)
    img.colorspace_settings.name = 'Non-Color'
    img.pixels.foreach_set(arr.ravel())
    img.pack()
    return img


def _mat_hair_gold():
    nrm = _hair_streak_normal_image()
    return _mat_pbr(
        'bronze_hair',
        HAIR_HEX,
        rough=0.32,
        metal=0.88,
        normal_tex=nrm,
    )


def _add_hair(body, mat_hair):
    me = body.data
    shell = bmesh.new()
    shell.from_mesh(me)
    shell.verts.ensure_lookup_table()
    shell.faces.ensure_lookup_table()
    del_faces = [f for f in shell.faces if not _hair_face_ok(f)]
    if del_faces:
        bmesh.ops.delete(shell, geom=del_faces, context='FACES_ONLY')
    if not shell.faces:
        shell.free()
        return None
    ob_shell = _mesh_from_bm(shell, 'bronze_hair_shell', mat_hair)
    mod = ob_shell.modifiers.new('Solidify', 'SOLIDIFY')
    mod.thickness = 0.0115
    mod.offset = -0.62
    bpy.context.view_layer.objects.active = ob_shell
    ob_shell.select_set(True)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    ob_shell.select_set(False)
    _mesh_merge_centerline(ob_shell)
    bpy.context.view_layer.update()
    mw_s = ob_shell.matrix_world
    trim_bm = bmesh.new()
    trim_bm.from_mesh(ob_shell.data)
    trim_bm.verts.ensure_lookup_table()
    inv_s = mw_s.inverted()
    drop = [
        v
        for v in trim_bm.verts
        if (w := mw_s @ v.co).y < -0.048 and w.z < 1.652
    ]
    if drop:
        bmesh.ops.delete(trim_bm, geom=drop, context='VERTS')
    trim_bm.to_mesh(ob_shell.data)
    trim_bm.free()
    ob_shell.data.update()
    mw_s = ob_shell.matrix_world
    inv_s = mw_s.inverted()
    for v in ob_shell.data.vertices:
        w = mw_s @ v.co
        if w.z < 1.58:
            continue
        flow = math.atan2(w.y + 0.035, max(w.z - 1.60, 0.015))
        strike = 0.0012 * math.sin(flow * 13.5 - w.x * 140.0)
        along = Vector((0.05 * math.sin(flow * 2.5), 1.0, 0.10)).normalized()
        v.co = inv_s @ (w + along * strike)
    ob_shell.data.update()
    shell_gaps = []
    mw_s = ob_shell.matrix_world
    for i in range(0, len(ob_shell.data.vertices), 4):
        w = mw_s @ ob_shell.data.vertices[i].co
        hit = _closest_on_body(body, w)
        if hit:
            wloc, wnorm, _ = hit
            gap = (w - wloc).dot(wnorm)
            if gap > 0.0025:
                shell_gaps.append(gap)
    if shell_gaps:
        print(
            f'[hair] shell gap mm mean={1000*np.mean(shell_gaps):.2f} '
            f'max={1000*np.max(shell_gaps):.2f} (expect 4-7)'
        )
    front_z = max(-(mw_s @ v.co).y for v in ob_shell.data.vertices)
    print(f'[hair] shell front_z_gltf_max={front_z:.4f} (forehead band; lower=less bang)')

    bun_r = 0.028
    bun_center = Vector((0.0, 0.068, 1.662))
    matrix = (
        Matrix.Translation(bun_center)
        @ Matrix.Diagonal((bun_r, bun_r * 0.85, bun_r * 1.0, 1.0))
    )
    bm_bun = bmesh.new()
    bmesh.ops.create_uvsphere(bm_bun, u_segments=32, v_segments=16, radius=1.0, matrix=matrix)
    for v in bm_bun.verts:
        w = matrix @ v.co
        groove = 0.0012 * math.sin(math.atan2(w.x, w.y - bun_center.y) * 9.0)
        inward = (w - bun_center).normalized()
        v.co = matrix.inverted() @ (w - inward * groove)
    ob_bun = _mesh_from_bm(bm_bun, 'bronze_hair_bun', mat_hair)
    bpy.context.view_layer.update()
    head_ref = Vector((0.0, -0.055, 1.648))
    mw_b = ob_bun.matrix_world
    to_head = (head_ref - bun_center).normalized()
    for v in ob_bun.data.vertices:
        w = mw_b @ v.co
        outward = w - bun_center
        if outward.length < 1e-8:
            continue
        if outward.normalized().dot(to_head) < 0.32:
            continue
        hit = _closest_on_body(ob_shell, w)
        if not hit:
            hit = _closest_on_body(body, w)
        if not hit:
            continue
        wloc, wnorm, _ = hit
        gap = (w - wloc).dot(wnorm)
        if gap > 0.0008:
            pull = min(gap, 0.003)
            v.co = mw_b.inverted() @ (w - wnorm * pull)
    ob_bun.data.update()
    gap_max = 0.0
    gap_min = 0.0
    mw_b = ob_bun.matrix_world
    verts = ob_bun.data.vertices
    for i in range(0, len(verts), 2):
        v = verts[i]
        w = mw_b @ v.co
        hit = _closest_on_body(body, w)
        if hit:
            wloc, wnorm, _ = hit
            g = (w - wloc).dot(wnorm)
            gap_max = max(gap_max, g)
            gap_min = min(gap_min, g)
    print(
        f'[hair] bun center_x={bun_center.x:.4f} '
        f'gap_mm=[{gap_min*1000:.1f},{gap_max*1000:.1f}] (expect max≤0 embed)'
    )
    return [ob_shell, ob_bun]


def _sash_stripe_mat():
    stripe_path = _make_stripe_tex()
    mat = _mat_pbr('sash_cloth', 0xffffff, rough=0.62, metal=0.0)
    nt = mat.node_tree
    bsdf = None
    for n in nt.nodes:
        if n.type == 'BSDF_PRINCIPLED':
            bsdf = n
            break
    if bsdf is None:
        bsdf = nt.nodes['Principled BSDF']
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(stripe_path, check_existing=True)
    tex.image.colorspace_settings.name = 'sRGB'
    tex.location = (-300, 0)
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    return mat


def _hip_outer_radius(body, z_lo=0.86, z_hi=0.96):
    mw = body.matrix_world
    radii = []
    for v in body.data.vertices:
        co = mw @ v.co
        if z_lo <= co.z <= z_hi and co.y < 0.02:
            radii.append(math.hypot(co.x, co.y))
    if not radii:
        return 0.172
    radii.sort()
    return radii[int(len(radii) * 0.88)]


def _body_ray_hit(body, axis_z, angle, max_dist=0.22):
    """自中軸水平 ray_cast 僅打到 bronze_body（限制長度避開手臂）。"""
    ca, sa = math.cos(angle), math.sin(angle)
    origin = Vector((0.0, 0.0, axis_z))
    direction = Vector((ca, sa, 0.0))
    inv = body.matrix_world.inverted()
    lo = inv @ origin
    ld = (inv.to_3x3() @ direction).normalized()
    rc = body.ray_cast(lo, ld, distance=max_dist)
    hit = rc[0]
    if not hit:
        return None
    loc, norm = rc[1], rc[2]
    wloc = body.matrix_world @ loc
    wnorm = (body.matrix_world.to_3x3() @ norm).normalized()
    imw = body.matrix_world.inverted()
    return imw @ wloc, wnorm, imw.to_3x3() @ wnorm


def _torso_surface(body, axis_z, angle, offset=0.006):
    """由外側探點吸附髖部曲面（避開手臂）。"""
    ca, sa = math.cos(angle), math.sin(angle)
    probe = Vector((ca * 0.28, sa * 0.28, axis_z))
    cpm = _closest_on_body(body, probe)
    if not cpm:
        return None
    wloc, wnorm, _ = cpm
    if abs(wloc.x) > 0.27 or wloc.z < 0.86 or wloc.z > 1.18:
        return None
    inv = body.matrix_world.inverted()
    loc = inv @ wloc
    ln = (inv.to_3x3() @ wnorm).normalized()
    return loc + ln * offset


def _band_ring_z(angle):
    """前低後高斜繞（資料高度前≈1.02、後≈1.10，對應本地 Z）。"""
    sa = math.sin(angle)
    return 1.02 - 0.040 * max(0.0, -sa) + 0.080 * max(0.0, sa)


def _assign_bm_uvs(bm, uv_map):
    """uv_map: id(BMVert) -> (u, v)"""
    layer = bm.loops.layers.uv.new('UVMap')
    for f in bm.faces:
        for loop in f.loops:
            u, v = uv_map.get(id(loop.vert), (0.0, 0.0))
            loop[layer].uv = (u, v)


def _validate_sash_to_body(band_ob, body, offset_nom=0.006):
    from mathutils.bvhtree import BVHTree

    body_bm = bmesh.new()
    body_bm.from_mesh(body.data)
    body_bm.transform(body.matrix_world)
    bvh = BVHTree.FromBMesh(body_bm)
    dists = []
    max_x = 0.0
    for v in band_ob.data.vertices:
        wco = band_ob.matrix_world @ v.co
        max_x = max(max_x, abs(wco.x))
        nearest, _norm, _idx, _d = bvh.find_nearest(wco)
        if nearest is not None:
            dists.append((wco - nearest).length)
    body_bm.free()
    if not dists:
        print('[sash] validate: no distances')
        return
    dmin, dmax = min(dists), max(dists)
    print(
        f'[sash] body_dist mm=[{dmin * 1000:.1f},{dmax * 1000:.1f}] '
        f'|x|_max={max_x:.3f} (expect 0.17-0.19, dist 3-12mm)'
    )


def _add_sash(body, _mat_cloth):
    """紅白藍條紋帶：ray_cast 貼體環繞，正面垂片。"""
    mat = _sash_stripe_mat()
    segs = 90
    band_half_h = 0.035
    offset = 0.006
    deg_step = 4
    steps = 360 // deg_step
    if steps != segs:
        segs = steps

    bm = bmesh.new()
    bm.verts.ensure_lookup_table()
    uv_by_vert = {}
    grid = []
    for i in range(segs):
        ang = 2 * math.pi * i / segs
        axis_z = _band_ring_z(ang)
        row_verts = []
        for row, dz in enumerate((-band_half_h, 0.0, band_half_h)):
            hit = _body_ray_hit(body, axis_z + dz, ang)
            if hit is None:
                alt = _torso_surface(body, axis_z + dz, ang, offset)
                if alt is None:
                    break
                co = alt
            else:
                loc, _wn, ln = hit
                co = loc + ln * offset
            v = bm.verts.new(co)
            bm.verts.ensure_lookup_table()
            u = (i / float(segs)) * 4.0
            v_uv = (dz + band_half_h) / (2.0 * band_half_h)
            uv_by_vert[id(v)] = (u, v_uv)
            row_verts.append(v)
        if len(row_verts) == 3:
            grid.append(row_verts)
    bm.verts.index_update()

    for i in range(len(grid)):
        j = (i + 1) % len(grid)
        a0, a1, a2 = grid[i]
        b0, b1, b2 = grid[j]
        bm.faces.new([a0, b0, b1, a1])
        bm.faces.new([a1, b1, b2, a2])

    _assign_bm_uvs(bm, uv_by_vert)
    print(f'[sash] ring_segments={len(grid)} uv_pairs={len(uv_by_vert)}')
    me = bpy.data.meshes.new('bronze_sash_band')
    bm.to_mesh(me)
    bm.free()
    ob_band = bpy.data.objects.new('bronze_sash_band', me)
    bpy.context.scene.collection.objects.link(ob_band)
    ob_band.data.materials.append(mat)
    for p in me.polygons:
        p.use_smooth = True

    _validate_sash_to_body(ob_band, body)

    bm2 = bmesh.new()
    flap_w, flap_h = 0.12, 0.15
    front_ang = -math.pi * 0.5
    flap_z_top = _band_ring_z(front_ang) - band_half_h * 0.35
    flap_surf = _torso_surface(body, flap_z_top + flap_h * 0.35, front_ang, 0.008)
    if flap_surf:
        base = flap_surf
        hw = flap_w * 0.5
        pts = [
            base + Vector((-hw * 0.88, 0.0, 0.0)),
            base + Vector((hw * 0.88, 0.0, 0.0)),
            base + Vector((hw * 0.80, 0.0, -flap_h)),
            base + Vector((-hw * 0.80, 0.0, -flap_h)),
        ]
    else:
        y_front = -0.17
        pts = [
            (-flap_w * 0.42, y_front, flap_z_top),
            (flap_w * 0.42, y_front, flap_z_top),
            (flap_w * 0.38, y_front, flap_z_top - flap_h),
            (-flap_w * 0.38, y_front, flap_z_top - flap_h),
        ]
    vs = [bm2.verts.new(p) for p in pts]
    f = bm2.faces.new(vs)
    flap_uv = {
        id(vs[0]): (0.0, 1.0),
        id(vs[1]): (1.0, 1.0),
        id(vs[2]): (1.0, 0.0),
        id(vs[3]): (0.0, 0.0),
    }
    _assign_bm_uvs(bm2, flap_uv)
    me_flap = bpy.data.meshes.new('bronze_sash_flap')
    bm2.to_mesh(me_flap)
    bm2.free()
    ob_flap = bpy.data.objects.new('bronze_sash_flap', me_flap)
    bpy.context.scene.collection.objects.link(ob_flap)
    ob_flap.data.materials.append(mat)
    for p in me_flap.polygons:
        p.use_smooth = True

    root = bpy.data.objects.new('bronze_sash', None)
    bpy.context.scene.collection.objects.link(root)
    ob_band.parent = root
    ob_flap.parent = root
    return root


def _plaque_texture():
    """macOS 中文字型烘焙匾額字（系統 python3 + PIL）。"""
    import subprocess

    tmp = bpy.app.tempdir or '/tmp'
    png_path = os.path.join(tmp, 'plaque_text.png')
    gen = os.path.join(_HERE, 'gen_plaque_texture.py')
    subprocess.check_call(['python3', gen, png_path])
    img = bpy.data.images.load(png_path, check_existing=True)
    img.name = 'plaque_text'
    img.colorspace_settings.name = 'sRGB'
    img.pack()
    return img


def _pedestal_tier(name, mat, cx, cy, cz, sx, sy, sz):
    bm = bmesh.new()
    bmesh.ops.create_cube(
        bm,
        size=1.0,
        matrix=Matrix.Translation((cx, cy, cz)) @ Matrix.Diagonal((sx, sy, sz, 1.0)),
    )
    return _mesh_from_bm(bm, name, mat, flat=True)


def _build_pedestal(mat_dark):
    """三階金座：底面 z=0、頂面 BODY_FOOT_Z；匾於正面且寬度≥底階 70%。"""
    base_cz = PEDESTAL_BASE_H * 0.5
    mid_cz = PEDESTAL_BASE_H + PEDESTAL_MID_H * 0.5
    top_cz = PEDESTAL_BASE_H + PEDESTAL_MID_H + PEDESTAL_TOP_H * 0.5
    tiers = [
        _pedestal_tier('pedestal_base', mat_dark, 0, 0, base_cz, 0.52, 0.46, PEDESTAL_BASE_H),
        _pedestal_tier('pedestal_mid', mat_dark, 0, 0, mid_cz, 0.42, 0.38, PEDESTAL_MID_H),
        _pedestal_tier('pedestal_top', mat_dark, 0, 0, top_cz, 0.32, 0.28, PEDESTAL_TOP_H),
    ]
    ped_root = bpy.data.objects.new('pedestal', None)
    bpy.context.scene.collection.objects.link(ped_root)
    for t in tiers:
        t.parent = ped_root

    plaque_tex_aspect = 1024.0 / 140.0
    plaque_h = min(PEDESTAL_BASE_H * 0.9, 0.064)
    plaque_w = plaque_h * plaque_tex_aspect
    plaque_y = -(0.46 * 0.5 + 0.008)
    plaque_z = base_cz
    bm2 = bmesh.new()
    bmesh.ops.create_cube(
        bm2,
        size=1.0,
        matrix=Matrix.Translation((0, plaque_y, plaque_z))
        @ Matrix.Diagonal((plaque_w, 0.012, plaque_h, 1.0)),
    )
    plaque = _mesh_from_bm(bm2, 'pedestal_plaque', None, flat=True)
    _assign_plaque_uv(plaque, plaque_w, plaque_h)
    plaque_tex = _plaque_texture()
    p_mat = _mat_pbr('plaque', 0x4A3520, rough=0.5, metal=0.18, tex=plaque_tex)
    plaque.data.materials.append(p_mat)
    plaque.parent = ped_root
    return [ped_root, plaque]


def _assign_plaque_uv(plaque, pw, ph):
    me = plaque.data
    if not me.uv_layers:
        me.uv_layers.new(name='UVMap')
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.faces.ensure_lookup_table()
    layer = bm.loops.layers.uv.active
    front = None
    front_dot = -1.0
    for f in bm.faces:
        dot = (-f.normal).y
        if dot > front_dot:
            front_dot = dot
            front = f
    if front and front_dot > 0.85:
        xs = [lp.vert.co.x for lp in front.loops]
        zs = [lp.vert.co.z for lp in front.loops]
        xmin, xmax = min(xs), max(xs)
        zmin, zmax = min(zs), max(zs)
        dx = max(xmax - xmin, 1e-6)
        dz = max(zmax - zmin, 1e-6)
        for loop in front.loops:
            co = loop.vert.co
            u = (co.x - xmin) / dx
            v = (co.z - zmin) / dz
            loop[layer].uv = (max(0.0, min(1.0, u)), max(0.0, min(1.0, v)))
    for f in bm.faces:
        if f is front:
            continue
        for loop in f.loops:
            loop[layer].uv = (0.0, 0.0)
    bm.to_mesh(me)
    bm.free()
    me.update()


def _face_uv_at_point(mesh, face_idx, world_co, mw):
    imw = mw.inverted()
    local = imw @ world_co
    poly = mesh.polygons[face_idx]
    loop_idx = poly.loop_indices
    uv_l = mesh.uv_layers.active
    if uv_l is None:
        return Vector((0.5, 0.5))
    me_loops = mesh.loops
    me_verts = mesh.vertices
    vcos = []
    uvs = []
    for li in loop_idx:
        vcos.append(me_verts[me_loops[li].vertex_index].co)
        uvs.append(uv_l.data[li].uv.copy())
    if len(vcos) == 4:
        tri_a = (0, 1, 2)
        tri_b = (0, 2, 3)
        w_a = _barycentric(local, vcos[0], vcos[1], vcos[2])
        w_b = _barycentric(local, vcos[0], vcos[2], vcos[3])
        if w_a and sum(w_a) >= sum(w_b or (0,)):
            u = w_a[0] * uvs[0] + w_a[1] * uvs[1] + w_a[2] * uvs[2]
        else:
            u = w_b[0] * uvs[0] + w_b[1] * uvs[2] + w_b[2] * uvs[3]
        return u
    w = _barycentric(local, vcos[0], vcos[1], vcos[2])
    if not w:
        return uvs[0]
    return w[0] * uvs[0] + w[1] * uvs[1] + w[2] * uvs[2]


def _barycentric(p, a, b, c):
    v0 = b - a
    v1 = c - a
    v2 = p - a
    d00 = v0.dot(v0)
    d01 = v0.dot(v1)
    d11 = v1.dot(v1)
    d20 = v2.dot(v0)
    d21 = v2.dot(v1)
    denom = d00 * d11 - d01 * d01
    if abs(denom) < 1e-12:
        return None
    v = (d11 * d20 - d01 * d21) / denom
    w = (d00 * d21 - d01 * d20) / denom
    u = 1.0 - v - w
    if min(u, v, w) < -0.02:
        return None
    return (u, v, w)


def _closest_on_body(body, world_co):
    mw = body.matrix_world
    ok, loc, norm, face_idx = body.closest_point_on_mesh(mw.inverted() @ world_co)
    if not ok or face_idx < 0:
        return None
    wloc = mw @ loc
    wnorm = (mw.to_3x3() @ norm).normalized()
    uv = _face_uv_at_point(body.data, face_idx, wloc, mw)
    return wloc, wnorm, uv


def _sort_meridian_records(recs):
    """沿經脈縱向（頭→足）排序，避免 JSON 順序造成斜線網格。"""
    return sorted(
        recs,
        key=lambda r: (
            -r['position'][1],
            r['position'][2],
            abs(r['position'][0]),
        ),
    )


MIDLINE_MERIDIANS = frozenset({'任脈', '督脈'})


def _mirror_path_x(points):
    return [Vector((-p.x, p.y, p.z)) for p in points]


EXTRA_MERIDIAN_WAYPOINTS = [
    [(0.24, 1.22, 0.04), (0.26, 1.05, 0.06), (0.24, 0.88, 0.02)],
    [(-0.24, 1.22, 0.04), (-0.26, 1.05, 0.06), (-0.24, 0.88, 0.02)],
    [(0.08, 1.20, -0.02), (0.06, 1.02, -0.04), (0.05, 0.86, -0.02)],
    [(-0.08, 1.20, -0.02), (-0.06, 1.02, -0.04), (-0.05, 0.86, -0.02)],
    [(0.06, 0.62, 0.09), (0.06, 0.38, 0.08), (0.06, 0.12, 0.06)],
    [(-0.06, 0.62, 0.09), (-0.06, 0.38, 0.08), (-0.06, 0.12, 0.06)],
    [(0.12, 0.60, -0.02), (0.12, 0.35, -0.03), (0.10, 0.10, -0.02)],
    [(-0.12, 0.60, -0.02), (-0.12, 0.35, -0.03), (-0.10, 0.10, -0.02)],
    [(0.14, 0.58, 0.02), (0.14, 0.32, 0.0), (0.12, 0.08, 0.01)],
    [(-0.14, 0.58, 0.02), (-0.14, 0.32, 0.0), (-0.12, 0.08, 0.01)],
]


def _snap_point_on_body(body, world_co):
    hit = _closest_on_body(body, world_co)
    if not hit:
        return None
    wloc, wnorm, _ = hit
    return wloc + wnorm * MERIDIAN_SURFACE_OFFSET, hit


def _point_body_gap(body, world_co):
    hit = _closest_on_body(body, world_co)
    if not hit:
        return 1e9
    wloc, _, _ = hit
    return (world_co - wloc).length


TORSO_MERIDIAN_NAMES = frozenset({'任脈', '督脈', '足陽明胃經', '足少陰腎經', '足太陰脾經'})


def _torso_half_width_blender(body, axis_z):
    mw = body.matrix_world
    half = 0.12
    for v in body.data.vertices:
        w = mw @ v.co
        if abs(w.z - axis_z) > 0.016:
            continue
        if w.y > 0.07:
            continue
        half = max(half, abs(w.x))
    return half


def _point_in_scalp_hair_zone(wco):
    """髮殼覆蓋區（留額與臉頸經絡）。"""
    if wco.z < 1.625:
        return False
    if wco.y < -0.05 and wco.z < 1.648:
        return False
    if wco.y < -0.02 and wco.z < 1.638:
        return False
    if wco.z >= 1.628 and wco.y >= -0.07:
        return True
    if wco.z >= 1.622 and abs(wco.x) >= 0.085:
        return True
    return False


def _filter_hair_zone_points(points, meridian=''):
    if meridian == '督脈':
        return points
    return [p for p in points if not _point_in_scalp_hair_zone(p)]


def _path_polyline_length(points):
    if len(points) < 2:
        return 0.0
    total = 0.0
    for i in range(1, len(points)):
        total += (points[i] - points[i - 1]).length
    return total


def _edge_should_break(body, a, b):
    gap = _edge_body_gap_max(body, a, b)
    if gap <= MERIDIAN_BREAK_GAP:
        return False
    if (b - a).length > MERIDIAN_EDGE_LEN_MAX and gap <= MERIDIAN_EDGE_GAP_MAX:
        return False
    z_mid = a.lerp(b, 0.5).z
    torso_w = _torso_half_width_blender(body, z_mid)
    on_torso = abs(a.x) <= torso_w + 0.028 and abs(b.x) <= torso_w + 0.028
    chest_band = 1.02 <= min(a.z, b.z) and max(a.z, b.z) <= 1.42
    mid = a.lerp(b, 0.5)
    mid_gap = _point_body_gap(body, mid)
    if on_torso and chest_band:
        if abs(mid.x) < torso_w * 0.70 and mid_gap <= MERIDIAN_BREAK_GAP:
            return False
        if mid_gap <= MERIDIAN_EDGE_GAP_MAX:
            return False
    if abs(mid.x) > torso_w + 0.05 and mid_gap > MERIDIAN_BREAK_GAP:
        return True
    fine = _subdivide_segment_snap(body, a, b, max_iters=8)
    if len(fine) > 2:
        return False
    if (b - a).length > MERIDIAN_EDGE_LEN_MAX:
        return True
    return gap > MERIDIAN_BREAK_GAP


def _meridian_draw_ratio_report(body, records):
    groups = {}
    for rec in records:
        mer = rec.get('meridian') or ''
        if rec.get('midline') or mer in ('任脈', '督脈'):
            groups.setdefault((mer, 0), []).append(rec)
        else:
            side = -1 if rec['position'][0] < 0 else 1
            groups.setdefault((mer, side), []).append(rec)
    for key in sorted(groups.keys(), key=lambda k: k[0]):
        mer = key[0]
        ordered = _sort_meridian_records(groups[key])
        raw = []
        for rec in ordered:
            px = 0.0 if rec.get('midline') else rec['position'][0]
            py, pz = rec['position'][1], rec['position'][2]
            raw.append(data_to_blender(px, py, pz))
        raw_len = _path_polyline_length(raw)
        drawn = 0.0
        for path in _snap_data_path_segments(body, ordered):
            path = _filter_hair_zone_points(path, mer)
            drawn += _path_polyline_length(path)
        if mer not in MIDLINE_MERIDIANS and key[1] > 0:
            drawn *= 2.0
        pct = 100.0 * drawn / raw_len if raw_len > 1e-6 else 100.0
        mark = 'OK' if mer not in TORSO_MERIDIAN_NAMES or pct >= 90.0 else 'LOW'
        print(
            f'[meridians] {mer} drawn/path={pct:.1f}% '
            f'({drawn:.3f}/{raw_len:.3f}m) {mark}'
        )


def _edge_body_gap_max(body, a, b):
  mx = 0.0
  for t in (0.25, 0.5, 0.75):
    mx = max(mx, _point_body_gap(body, a.lerp(b, t)))
  return mx


def _subdivide_segment_snap(body, a, b, max_iters=5):
    """反覆細分→吸附，直到弦中點貼體或達迭代上限。"""
    pts = [a, b]
    for _ in range(max_iters):
        new_pts = [pts[0]]
        changed = False
        for i in range(len(pts) - 1):
            p0, p1 = pts[i], pts[i + 1]
            if (p1 - p0).length <= MERIDIAN_PATH_STEP and _edge_body_gap_max(body, p0, p1) <= MERIDIAN_EDGE_GAP_MAX:
                new_pts.append(p1)
                continue
            mid = p0.lerp(p1, 0.5)
            snapped = _snap_point_on_body(body, mid)
            if snapped and _point_body_gap(body, snapped[0]) <= MERIDIAN_BREAK_GAP:
                new_pts.append(snapped[0])
                new_pts.append(p1)
                changed = True
            else:
                new_pts.append(p1)
        pts = new_pts
        if not changed:
            break
    return pts


def _resample_path_uniform(points, step=MERIDIAN_PATH_STEP):
    if len(points) < 2:
        return points
    out = [points[0]]
    for i in range(1, len(points)):
        a, b = points[i - 1], points[i]
        dist = (b - a).length
        if dist <= step + 1e-9:
            if (b - out[-1]).length > 1e-6:
                out.append(b)
            continue
        n = max(1, int(math.ceil(dist / step)))
        for k in range(1, n + 1):
            p = a.lerp(b, k / n)
            if (p - out[-1]).length > 1e-6:
                out.append(p)
    return out


def _densify_and_snap(body, points):
    """≤8 mm 間距吸附；弦面貼體不足時斷開跨越腋窩的空段。"""
    if len(points) < 2:
        return []
    dense = [points[0]]
    for i in range(len(points) - 1):
        a, b = points[i], points[i + 1]
        seg = _subdivide_segment_snap(body, a, b, max_iters=8)
        if len(seg) < 2:
            continue
        if (
            _edge_body_gap_max(body, a, b) > MERIDIAN_BREAK_GAP
            and len(seg) <= 2
            and (b - a).length > 0.022
        ):
            continue
        for p in seg[1:]:
            if _point_body_gap(body, p) <= MERIDIAN_BREAK_GAP:
                if dense and (p - dense[-1]).length > 1e-5:
                    dense.append(p)
    out = []
    for p in dense:
        if _point_body_gap(body, p) <= MERIDIAN_BREAK_GAP:
            out.append(p)
    if len(out) < 2:
        return []
    return out


def _split_path_keep_surface(body, points):
    if len(points) < 2:
        return []
    segments = [[points[0]]]
    for i in range(1, len(points)):
        a, b = points[i - 1], points[i]
        if _edge_should_break(body, a, b):
            if len(segments[-1]) >= 2:
                segments.append([b])
            elif segments[-1]:
                segments[-1] = [segments[-1][-1], b] if (b - segments[-1][-1]).length < MERIDIAN_EDGE_LEN_MAX else [b]
            else:
                segments[-1] = [b]
        else:
            segments[-1].append(b)
    return [s for s in segments if len(s) >= 2]


def _snap_data_path(body, waypoints):
    snapped = []
    for wp in waypoints:
        if isinstance(wp, dict):
            px = 0.0 if wp.get('midline') else wp['position'][0]
            py, pz = wp['position'][1], wp['position'][2]
        else:
            px, py, pz = wp
        wco = data_to_blender(px, py, pz)
        pt = _snap_point_on_body(body, wco)
        if pt:
            snapped.append(pt[0])
    if len(snapped) < 2:
        return []
    dense = _densify_and_snap(body, snapped)
    dense = _resample_path_uniform(dense)
    parts = _split_path_keep_surface(body, dense)
    if not parts:
        return []
    return max(parts, key=len)


def _snap_data_path_segments(body, waypoints):
    """若資料點間弦離體，拆成多段各自細分吸附。"""
    raw = []
    for wp in waypoints:
        if isinstance(wp, dict):
            px = 0.0 if wp.get('midline') else wp['position'][0]
            py, pz = wp['position'][1], wp['position'][2]
        else:
            px, py, pz = wp
        wco = data_to_blender(px, py, pz)
        pt = _snap_point_on_body(body, wco)
        if pt:
            raw.append(pt[0])
    if len(raw) < 2:
        return []
    chunks = [[raw[0]]]
    for i in range(1, len(raw)):
        a, b = raw[i - 1], raw[i]
        if _edge_should_break(body, a, b):
            if len(chunks[-1]) >= 2:
                chunks.append([b])
            else:
                chunks[-1] = [b]
        else:
            chunks[-1].append(b)
    out = []
    for ch in chunks:
        dense = _densify_and_snap(body, ch)
        dense = _resample_path_uniform(dense)
        snapped_line = []
        for p in dense:
            sp = _snap_point_on_body(body, p)
            if not sp:
                if len(snapped_line) >= 2:
                    out.extend(_split_path_keep_surface(body, snapped_line))
                snapped_line = []
                continue
            snapped_line.append(sp[0])
        if len(snapped_line) >= 2:
            out.extend(_split_path_keep_surface(body, snapped_line))
    return out


def _meridian_vertex_body_gap_max(body, meridian_ob, step=3):
    if meridian_ob is None:
        return 0.0
    mw_m = meridian_ob.matrix_world
    gaps = []
    for v in meridian_ob.data.vertices:
        wco = mw_m @ v.co
        gaps.append(_point_body_gap(body, wco))
    gaps.sort(reverse=True)
    sample = gaps[:: max(1, step)]
    return max(sample) if sample else 0.0


def _meridian_edge_gap_and_len_max(body, meridian_ob):
    """每條 mesh 邊在 25/50/75% 取樣離體距離與邊長上限。"""
    if meridian_ob is None:
        return 0.0, 0.0
    mw_m = meridian_ob.matrix_world
    me = meridian_ob.data
    edge_lens = []
    edge_gaps = []
    vcos = [mw_m @ v.co for v in me.vertices]
    for e in me.edges:
        a, b = e.vertices
        pa, pb = vcos[a], vcos[b]
        elen = (pb - pa).length
        edge_lens.append(elen)
        for t in (0.25, 0.5, 0.75):
            wco = pa.lerp(pb, t)
            edge_gaps.append(_point_body_gap(body, wco))
    return (max(edge_gaps) if edge_gaps else 0.0, max(edge_lens) if edge_lens else 0.0)


def _finalize_meridian_paths(body, points):
    """≤8 mm 重採樣、再吸附；過長弦自動拆段。"""
    points = _resample_path_uniform(points)
    chunks = []
    chunk = []
    for p in points:
        sp = _snap_point_on_body(body, p)
        if not sp:
            if len(chunk) >= 2:
                chunks.append(chunk)
            chunk = []
            continue
        pt = sp[0]
        if chunk and (pt - chunk[-1]).length > MERIDIAN_EDGE_LEN_MAX + 0.001:
            if len(chunk) >= 2:
                chunks.append(chunk)
            chunk = [pt]
        else:
            chunk.append(pt)
    if len(chunk) >= 2:
        chunks.append(chunk)
    return chunks


def _meridian_paths_from_records(body, records):
    groups = {}
    for rec in records:
        mer = rec.get('meridian') or ''
        if rec.get('midline') or mer in ('任脈', '督脈'):
            groups.setdefault((mer, 0), []).append(rec)
        else:
            side = -1 if rec['position'][0] < 0 else 1
            groups.setdefault((mer, side), []).append(rec)
    paths = []
    for key, pts in groups.items():
        ordered = _sort_meridian_records(pts)
        mer = key[0] if key else ''
        for path in _snap_data_path_segments(body, ordered):
            path = _filter_hair_zone_points(path, mer)
            for seg in _finalize_meridian_paths(body, path):
                paths.append(seg)
                if mer and mer not in MIDLINE_MERIDIANS:
                    mir = _filter_hair_zone_points(_mirror_path_x(seg), mer)
                    if len(mir) >= 2:
                        paths.append(mir)
    for waypoints in EXTRA_MERIDIAN_WAYPOINTS:
        for path in _snap_data_path_segments(body, waypoints):
            for seg in _finalize_meridian_paths(body, path):
                paths.append(seg)
    return paths


def _curve_tube_from_points(points, name, mat):
    if len(points) < 2:
        return None
    cu = bpy.data.curves.new(name, type='CURVE')
    cu.dimensions = '3D'
    cu.bevel_depth = MERIDIAN_BEVEL_R
    cu.bevel_resolution = 2
    cu.fill_mode = 'FULL'
    spline = cu.splines.new('POLY')
    spline.points.add(len(points) - 1)
    for i, p in enumerate(points):
        spline.points[i].co = (p.x, p.y, p.z, 1)
    ob = bpy.data.objects.new(name, cu)
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.convert(target='MESH')
    ob.select_set(False)
    if mat:
        if ob.data.materials:
            ob.data.materials[0] = mat
        else:
            ob.data.materials.append(mat)
    return ob


def _subdivide_long_meridian_edges(ob, max_len=MERIDIAN_EDGE_LEN_MAX, max_passes=12):
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    for _ in range(max_passes):
        bm.edges.ensure_lookup_table()
        long_edges = [e for e in bm.edges if e.calc_length() > max_len]
        if not long_edges:
            break
        bmesh.ops.subdivide_edges(bm, edges=long_edges, cuts=1)
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()


def _build_meridian_mesh(body, records, mat_meridian):
    paths = _meridian_paths_from_records(body, records)
    seg_max = 0.0
    for path in paths:
        for i in range(1, len(path)):
            seg_max = max(seg_max, (path[i] - path[i - 1]).length)
    print(f'[meridians] path_seg_max_mm={seg_max * 1000:.2f} (expect ≤12)')
    tubes = []
    for i, path in enumerate(paths):
        ob = _curve_tube_from_points(path, f'meridian_seg_{i}', mat_meridian)
        if ob:
            tubes.append(ob)
    if not tubes:
        return None
    bpy.ops.object.select_all(action='DESELECT')
    for ob in tubes:
        ob.select_set(True)
    bpy.context.view_layer.objects.active = tubes[0]
    bpy.ops.object.join()
    merged = bpy.context.active_object
    merged.name = 'bronze_meridians'
    _subdivide_long_meridian_edges(merged)
    mw_m = merged.matrix_world
    vcos = [mw_m @ v.co for v in merged.data.vertices]
    pre_max_edge = 0.0
    for e in merged.data.edges:
        va, vb = e.vertices
        pre_max_edge = max(pre_max_edge, (vcos[va] - vcos[vb]).length)
    print(f'[meridians] mesh_edge_max_mm={pre_max_edge * 1000:.2f} after subdivide')
    if merged.data:
        merged.data.name = 'bronze_meridians'
    tris = _tri_count(merged)
    if tris > MERIDIAN_MAX_TRIS:
        mod = merged.modifiers.new('Decimate', 'DECIMATE')
        mod.ratio = MERIDIAN_MAX_TRIS / tris
        bpy.context.view_layer.objects.active = merged
        merged.select_set(True)
        bpy.ops.object.modifier_apply(modifier=mod.name)
        merged.select_set(False)
        tris = _tri_count(merged)
    gap_max = _meridian_vertex_body_gap_max(body, merged)
    edge_gap_max, _ = _meridian_edge_gap_and_len_max(body, merged)
    edge_len_max = pre_max_edge
    print(
        f'[meridians] paths={len(paths)} tris={tris} vtx_gap_max_mm={gap_max * 1000:.2f} '
        f'edge_gap_max_mm={edge_gap_max * 1000:.2f} edge_len_max_mm={edge_len_max * 1000:.2f}'
    )
    if edge_gap_max > 0.006 or edge_len_max > MERIDIAN_EDGE_LEN_MAX:
        print('[meridians] WARN edge sample or edge length exceeds spec')
    return merged


def _translate_object_tree(ob, dz):
    if ob is None:
        return
    if ob.type == 'MESH':
        for v in ob.data.vertices:
            v.co.z += dz
        ob.data.update()
    for ch in ob.children:
        _translate_object_tree(ch, dz)


def _texture_mean_srgb(arr_rgb):
    m = arr_rgb.mean(axis=(0, 1))
    return tuple(int(round(c * 255)) for c in m[:3])


def _srgb_hex_f32(h):
    return np.array([((h >> s) & 255) / 255.0 for s in (16, 8, 0)], dtype=np.float32)


def _bake_body_textures():
    base = _srgb_hex_f32(BRONZE_HEX)
    col_buf = np.ones((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32)
    col_buf[:, :, :3] = base
    col_buf[:, :, 3] = 1.0
    mean = _texture_mean_srgb(col_buf)
    print(f'[body_tex] bronze_body_color mean sRGB={mean} hex=#{mean[0]:02X}{mean[1]:02X}{mean[2]:02X}')
    nrm = np.zeros((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32)
    nrm[:, :, 0] = 0.5
    nrm[:, :, 1] = 0.5
    nrm[:, :, 2] = 1.0
    nrm[:, :, 3] = 1.0
    rough = np.ones((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32)
    rough[:, :, 0] = BRONZE_ROUGH
    rough[:, :, 1] = BRONZE_ROUGH
    rough[:, :, 2] = BRONZE_ROUGH
    rough[:, :, 3] = 1.0
    img_col = bpy.data.images.new('bronze_body_color', TEX_SIZE, TEX_SIZE, alpha=False)
    img_col.colorspace_settings.name = 'sRGB'
    img_col.pixels.foreach_set(col_buf.ravel())
    img_col.pack()
    img_n = bpy.data.images.new('bronze_body_normal', TEX_SIZE, TEX_SIZE, alpha=False)
    img_n.colorspace_settings.name = 'Non-Color'
    img_n.pixels.foreach_set(nrm.ravel())
    img_n.pack()
    img_r = bpy.data.images.new('bronze_body_rough', TEX_SIZE, TEX_SIZE, alpha=False)
    img_r.colorspace_settings.name = 'Non-Color'
    img_r.pixels.foreach_set(rough.ravel())
    img_r.pack()
    return img_col, img_n, img_r


def _compute_part_boxes(body):
    mw = body.matrix_world
    data_pts = [blender_to_data(mw @ v.co) for v in body.data.vertices]
    ys = [p[1] for p in data_pts]
    y0, y1 = min(ys), max(ys)
    foot = round(BODY_FOOT_Z, 4)
    bands = [
        ('feet', foot, foot + 0.18),
        ('legs', foot + 0.14, foot + 0.74),
        ('torso', foot + 0.68, foot + 1.36),
        ('arms', foot + 0.72, foot + 1.32),
        ('head', foot + 1.32, y1),
    ]
    boxes = []
    for _name, lo, hi in bands:
        subset = [p for p in data_pts if lo <= p[1] <= hi]
        if not subset:
            continue
        ax = [abs(p[0]) for p in subset]
        zs = [p[2] for p in subset]
        boxes.append({
            'minX': round(min(ax), 4),
            'maxX': round(max(ax), 4),
            'minY': round(lo, 4),
            'maxY': round(hi, 4),
            'minZ': round(min(zs), 4),
            'maxZ': round(max(zs), 4),
        })
    return boxes


def _closest_on_mesh(ob, world_co):
    if ob is None:
        return None
    mw = ob.matrix_world
    ok, loc, norm, _idx = ob.closest_point_on_mesh(mw.inverted() @ world_co)
    if not ok:
        return None
    wloc = mw @ loc
    wnorm = (mw.to_3x3() @ norm).normalized()
    return wloc, wnorm


def _project_surface(body, meridian_ob, records):
    instances = {}
    for rec in records:
        code = rec['code']
        if rec.get('midline'):
            sides = [(1, ':m')]
        else:
            sides = [(-1, ':-1'), (1, ':1')]
        for side, suffix in sides:
            px = 0.0 if rec.get('midline') else rec['position'][0] * side
            py, pz = rec['position'][1], rec['position'][2]
            wco = data_to_blender(px, py, pz)
            hit = _closest_on_body(body, wco)
            if hit is None:
                pos = [px, py + BODY_FOOT_Z, pz]
                nrm = rec.get('normal', [0, 0, 1])
            else:
                wloc, wnorm, _ = hit
                mer_hit = _closest_on_mesh(meridian_ob, wloc)
                if mer_hit and (mer_hit[0] - wloc).length < 0.12:
                    wloc, wnorm = mer_hit
                pos = list(blender_to_data(wloc))
                nrm = blender_normal_to_data(wnorm)
            key = f'{code}{suffix}'
            instances[key] = {
                'position': [round(v, 5) for v in pos],
                'normal': [round(v, 5) for v in nrm],
            }
    return instances


def _write_surface_json(body, meridian_ob, records, model_height):
    payload = {
        'version': 1,
        'dataBodyHeight': model_height,
        'bodyFootY': round(BODY_FOOT_Z, 5),
        'partBoxes': _compute_part_boxes(body),
        'instances': _project_surface(body, meridian_ob, records),
    }
    os.makedirs(os.path.dirname(SURFACE_JSON), exist_ok=True)
    with open(SURFACE_JSON, 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
    print(f'WROTE {SURFACE_JSON} instances={len(payload["instances"])}')


def render_previews(root, out_dir=None):
    out_dir = out_dir or os.path.join(_HERE, 'previews', 'tcm_bronze')
    os.makedirs(out_dir, exist_ok=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.samples = 64
    sc.cycles.device = 'CPU'
    sc.render.resolution_x = 768
    sc.render.resolution_y = 1080
    world = bpy.data.worlds.new('preview_world')
    sc.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes['Background']
    bg.inputs[0].default_value = (0.10, 0.12, 0.16, 1)
    bg.inputs[1].default_value = 0.85
    sun = bpy.data.objects.new('preview_sun', bpy.data.lights.new('preview_sun', 'SUN'))
    sun.data.energy = 4.2
    sun.rotation_euler = (math.radians(52), 0, math.radians(28))
    sc.collection.objects.link(sun)
    fill = bpy.data.objects.new('preview_fill', bpy.data.lights.new('preview_fill', 'AREA'))
    fill.data.energy = 280
    fill.data.size = 2.8
    fill.location = (1.2, -1.5, 1.55)
    fill.rotation_euler = (math.radians(65), math.radians(12), math.radians(8))
    sc.collection.objects.link(fill)
    rim = bpy.data.objects.new('preview_rim', bpy.data.lights.new('preview_rim', 'AREA'))
    rim.data.energy = 160
    rim.data.size = 2.0
    rim.location = (-1.6, 1.4, 1.2)
    rim.rotation_euler = (math.radians(50), math.radians(-30), 0)
    sc.collection.objects.link(rim)
    cam = bpy.data.objects.new('preview_cam', bpy.data.cameras.new('preview_cam'))
    sc.collection.objects.link(cam)
    sc.camera = cam
    bb_min, bb_max = _print_world_bounds('preview')
    center = (bb_min + bb_max) * 0.5
    for name, off, lens in (
        ('front', Vector((0, -2.8, center.z)), 52),
        ('side', Vector((2.8, 0, center.z)), 52),
        ('back', Vector((0, 2.65, center.z)), 52),
        ('face', Vector((0, -1.0, bb_max.z - 0.08)), 75),
        ('head_side', Vector((0.82, 0.08, bb_max.z - 0.12)), 64),
        ('head_back', Vector((0.0, 1.05, bb_max.z - 0.14)), 68),
        ('head_3q', Vector((0.72, -0.42, bb_max.z - 0.12)), 62),
        ('chest', Vector((0.35, -0.22, 1.20)), 48),
        ('pedestal', Vector((0, -1.28, PEDESTAL_BASE_H * 0.5)), 58),
    ):
        if name == 'pedestal':
            look_at = Vector((0, -0.22, PEDESTAL_BASE_H * 0.5))
        elif name == 'head_side':
            look_at = Vector((0, -0.02, bb_max.z - 0.14))
        elif name == 'head_back':
            look_at = Vector((0, 0.04, bb_max.z - 0.16))
        elif name == 'head_3q':
            look_at = Vector((0.02, -0.03, bb_max.z - 0.14))
        elif name == 'chest':
            look_at = Vector((0, -0.04, 1.20))
        else:
            look_at = center
        if name == 'chest':
            sun.data.energy = 8.5
            sun.rotation_euler = (math.radians(12), 0, math.radians(62))
            fill.data.energy = 40
        else:
            sun.data.energy = 4.2
            sun.rotation_euler = (math.radians(52), 0, math.radians(28))
            fill.data.energy = 280
        cam.location = (
            look_at + off
            if name in ('pedestal', 'head_side', 'head_back', 'head_3q', 'chest', 'face')
            else center + off
        )
        cam.data.lens = lens
        sc.render.filepath = os.path.join(out_dir, name + '.png')
        bpy.ops.object.select_all(action='DESELECT')
        cam.select_set(True)
        bpy.context.view_layer.objects.active = root
        bpy.ops.view3d.camera_to_view_selected() if False else None
        direction = look_at - cam.location
        cam.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()
        bpy.ops.render.render(write_still=True)
        print(f'PREVIEW {sc.render.filepath}')


def build(out_path):
    prepare_scene()
    with open(POINTS_JSON, encoding='utf-8') as f:
        records = json.load(f)

    body = _import_body()
    _optional_decimate(body)

    _smooth_breast_detail(body)

    mat_bronze = _mat_pbr('bronze', BRONZE_HEX, rough=BRONZE_ROUGH, metal=BRONZE_METAL)
    mat_dark = _mat_pbr('bronze_dark', BRONZE_DARK_HEX, rough=0.28, metal=0.96)
    mat_hair = _mat_hair_gold()
    mat_cloth = _mat_pbr('sash_cloth', 0xffffff, rough=0.62, metal=0.0)
    mat_meridian = _mat_pbr(
        'meridian_dark',
        MERIDIAN_DARK_HEX,
        rough=MERIDIAN_ROUGH,
        metal=MERIDIAN_METAL,
    )

    _meridian_draw_ratio_report(body, records)
    meridian_ob = _build_meridian_mesh(body, records, mat_meridian)

    img_col, img_n, img_r = _bake_body_textures()
    mat_body = _mat_pbr(
        'bronze',
        BRONZE_HEX,
        rough=BRONZE_ROUGH,
        metal=BRONZE_METAL,
        tex=img_col,
        normal_tex=img_n,
        rough_tex=img_r,
    )
    body.data.materials.clear()
    body.data.materials.append(mat_body)

    objs = [body]
    hair = _add_hair(body, mat_hair)
    if hair:
        objs.extend(hair)
    objs.append(_add_sash(body, mat_cloth))
    if meridian_ob:
        objs.append(meridian_ob)
    objs.extend(_build_pedestal(mat_dark))

    for ob in objs:
        if ob is None:
            continue
        if ob.name.startswith('pedestal') or ob.name == 'pedestal':
            continue
        if ob.type == 'EMPTY' and ob.name == 'pedestal':
            continue
        _translate_object_tree(ob, BODY_FOOT_Z)

    mw = body.matrix_world
    data_ys = [blender_to_data(mw @ v.co)[1] for v in body.data.vertices]
    model_height = round(max(data_ys), 4)
    print(f'[model] bodyFootY={BODY_FOOT_Z:.3f} modelHeight={model_height:.3f}')

    root = bpy.data.objects.new('tcm_bronze_figure', None)
    bpy.context.scene.collection.objects.link(root)
    for ob in objs:
        if ob and ob.name in bpy.data.objects:
            ob.parent = root
    root['ground_y'] = 0.0
    root['data_body_height'] = model_height
    root['body_foot_y'] = BODY_FOOT_Z

    _write_surface_json(body, meridian_ob, records, model_height)
    _print_world_bounds('pre_export')

    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=out_path,
        export_format='GLB',
        use_selection=False,
        export_yup=True,
        export_apply=True,
        export_extras=True,
        export_texcoords=True,
        export_normals=True,
        export_materials='EXPORT',
        export_cameras=False,
        export_lights=False,
        export_animations=False,
        export_image_format='JPEG',
        export_jpeg_quality=72,
    )
    tris = sum(
        len(p.vertices) - 2
        for o in bpy.data.objects if o.type == 'MESH'
        for p in o.data.polygons
    )
    size = os.path.getsize(out_path)
    print(f'EXPORTED {out_path} tris≈{tris} bytes={size}')
    if os.environ.get('RENDER_PREV') == '1':
        render_previews(root)
    return tris, size


if __name__ == '__main__':
    out = argv_out('server/src/main/resources/public/models/tcm-bronze-figure.glb')
    build(out)