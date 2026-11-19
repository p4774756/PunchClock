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
TEX_SIZE = 1024
BRONZE_HEX = 0xc9a55c
BRONZE_DARK_HEX = 0xab8748
MERIDIAN_LINE_HEX = 0x5a3e1a
BRONZE_METAL = 0.97
BRONZE_ROUGH = 0.26
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


def _mesh_from_bm(bm, name, mat):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    if mat:
        ob.data.materials.append(mat)
    for p in me.polygons:
        p.use_smooth = True
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


def _smooth_breast_detail(body):
    """教學銅人胸部：抹平乳頭／乳暈突起，保留柔和輪廓。"""
    me = body.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.verts.ensure_lookup_table()
    chest = []
    for v in bm.verts:
        x, y, z = v.co.x, v.co.y, v.co.z
        if z < 1.05 or z > 1.34:
            continue
        if y > -0.015:
            continue
        ax = abs(x)
        if ax < 0.05 or ax > 0.17:
            continue
        chest.append(v)
    if chest:
        for _ in range(6):
            bmesh.ops.smooth_vert(
                bm,
                verts=chest,
                factor=0.9,
                use_axis_x=True,
                use_axis_y=True,
                use_axis_z=True,
            )
        for v in chest:
            if v.co.y < -0.06 and 0.075 < abs(v.co.x) < 0.13:
                blend = min(1.0, max(0.0, (-0.06 - v.co.y) * 8.0))
                v.co.y += blend * 0.018
                v.co.z -= blend * 0.004 * (1.0 if v.co.z > 1.18 else 0.35)
    bm.to_mesh(me)
    bm.free()
    me.update()
    print(f'[body] chest smoothed verts={len(chest)}')


def _shrinkwrap_to_body(ob, body, offset=0.01):
    mod = ob.modifiers.new('Shrinkwrap', 'SHRINKWRAP')
    mod.target = body
    mod.wrap_method = 'NEAREST_SURFACEPOINT'
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
    img = bpy.data.images.new('sash_stripe', 256, 256)
    arr = np.ones((256, 256, 4), dtype=np.float32)
    colors = [
        (0.85, 0.12, 0.15, 1.0),
        (0.95, 0.95, 0.94, 1.0),
        (0.12, 0.22, 0.65, 1.0),
    ]
    for row in range(256):
        band = colors[(row // 18) % 3]
        arr[row, :, :] = band
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


def _hair_face_ok(face, z_hairline=1.618):
    """髮際線在額上緣；不蓋眉眼，只留頂部與耳上後方薄殼。"""
    verts = list(face.verts)
    max_z = max(v.co.z for v in verts)
    avg_y = sum(v.co.y for v in verts) / len(verts)
    if max_z < z_hairline - 0.028:
        return False
    if avg_y < -0.05 and max_z < z_hairline + 0.006:
        return False
    return True


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
    mod.thickness = 0.003
    mod.offset = -0.2
    bpy.context.view_layer.objects.active = ob_shell
    ob_shell.select_set(True)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    ob_shell.select_set(False)

    bm_bun = bmesh.new()
    matrix = (
        Matrix.Translation((0.028, 0.082, 1.638))
        @ Matrix.Rotation(math.radians(26), 4, 'X')
        @ Matrix.Diagonal((0.020, 0.016, 0.018, 1.0))
    )
    bmesh.ops.create_uvsphere(bm_bun, u_segments=12, v_segments=8, radius=1.0, matrix=matrix)
    ob_bun = _mesh_from_bm(bm_bun, 'bronze_hair_bun', mat_hair)
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


def _stripe_uvs(ob, band_half_h, repeat_u=14.0, repeat_v=22.0):
    uv = ob.data.uv_layers.active or ob.data.uv_layers.new(name='UVMap')
    for poly in ob.data.polygons:
        for li in poly.loop_indices:
            vid = ob.data.loops[li].vertex_index
            co = ob.data.vertices[vid].co
            ang = math.atan2(co.y, co.x)
            u = (ang / (2 * math.pi) + 0.5) * repeat_u
            v = ((co.z + band_half_h) / max(band_half_h * 2, 1e-6)) * repeat_v
            uv.data[li].uv = (u, v)


def _add_sash(body, _mat_cloth):
    """紅白藍條紋帶：環繞髖一整圈（前低後高），正面垂短布遮下身。"""
    mat = _sash_stripe_mat()
    hip_mid = 0.905
    band_half_h = 0.035
    mean_r = 0.138
    segs = 72
    bm = bmesh.new()
    ring_lo, ring_hi = [], []
    for i in range(segs + 1):
        ang = 2 * math.pi * i / segs
        ca, sa = math.cos(ang), math.sin(ang)
        z_tilt = hip_mid - 0.028 * max(0.0, -sa) + 0.022 * max(0.0, sa)
        ring_lo.append(bm.verts.new(((mean_r - 0.006) * ca, (mean_r - 0.006) * sa, z_tilt - band_half_h)))
        ring_hi.append(bm.verts.new(((mean_r + 0.006) * ca, (mean_r + 0.006) * sa, z_tilt + band_half_h)))
    for i in range(segs):
        bm.faces.new([ring_lo[i], ring_lo[i + 1], ring_hi[i + 1], ring_hi[i]])
    ob_band = _mesh_from_bm(bm, 'bronze_sash_band', mat)
    _shrinkwrap_to_body(ob_band, body, offset=0.009)
    _stripe_uvs(ob_band, band_half_h)

    bm2 = bmesh.new()
    flap_w, flap_h = 0.12, 0.15
    y_front = -0.138
    z_top = hip_mid - band_half_h - 0.032
    pts = [
        (-flap_w * 0.42, y_front, z_top),
        (flap_w * 0.42, y_front, z_top),
        (flap_w * 0.38, y_front, z_top - flap_h),
        (-flap_w * 0.38, y_front, z_top - flap_h),
    ]
    vs = [bm2.verts.new(p) for p in pts]
    bm2.faces.new(vs)
    ob_flap = _mesh_from_bm(bm2, 'bronze_sash_flap', mat)
    _shrinkwrap_to_body(ob_flap, body, offset=0.011)
    _stripe_uvs(ob_flap, flap_h * 0.5, repeat_u=1.0, repeat_v=16.0)

    root = bpy.data.objects.new('bronze_sash', None)
    bpy.context.scene.collection.objects.link(root)
    ob_band.parent = root
    ob_flap.parent = root
    return root


def _plaque_texture():
    w, h = 640, 160
    arr = np.zeros((h, w, 4), dtype=np.float32)
    arr[:, :, :3] = (0.14, 0.11, 0.07)
    arr[:, :, 3] = 1.0
    curve = bpy.data.curves.new('_plaque_curve', type='FONT')
    curve.body = '人體針灸銅人'
    curve.size = 0.36
    curve.align_x = 'CENTER'
    curve.extrude = 0.012
    ob = bpy.data.objects.new('_plaque_glyph', curve)
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.convert(target='MESH')
    ob.select_set(False)
    xs, zs = [], []
    for v in ob.data.vertices:
        xs.append(v.co.x)
        zs.append(v.co.z)
    if xs:
        mn_x, mx_x = min(xs), max(xs)
        mn_z, mx_z = min(zs), max(zs)
        pad = 0.02
        for v in ob.data.vertices:
            u = (v.co.x - mn_x + pad) / (mx_x - mn_x + 2 * pad)
            vpix = (v.co.z - mn_z + pad) / (mx_z - mn_z + 2 * pad)
            px = int(u * (w - 1))
            py = int(vpix * (h - 1))
            if 0 <= px < w and 0 <= py < h:
                for dy in range(-1, 2):
                    for dx in range(-1, 2):
                        x, y = px + dx, py + dy
                        if 0 <= x < w and 0 <= y < h:
                            arr[y, x, :3] = (0.94, 0.80, 0.44)
    bpy.data.objects.remove(ob, do_unlink=True)
    img = bpy.data.images.new('plaque_text', w, h, alpha=True)
    img.pixels.foreach_set(arr.ravel())
    img.pack()
    return img


def _pedestal_tier(name, mat, cx, cy, cz, sx, sy, sz):
    bm = bmesh.new()
    bmesh.ops.create_cube(
        bm,
        size=1.0,
        matrix=Matrix.Translation((cx, cy, cz)) @ Matrix.Diagonal((sx, sy, sz, 1.0)),
    )
    return _mesh_from_bm(bm, name, mat)


def _build_pedestal(mat_dark):
    # 三階金座：由下往上逐階縮小，頂面 z=0（腳掌貼座面）；各階獨立封閉方塊避免內面空洞
    tiers = [
        _pedestal_tier('pedestal_base', mat_dark, 0, 0, -0.142, 0.52, 0.46, 0.056),
        _pedestal_tier('pedestal_mid', mat_dark, 0, 0, -0.086, 0.42, 0.38, 0.048),
        _pedestal_tier('pedestal_top', mat_dark, 0, 0, -0.032, 0.32, 0.28, 0.040),
    ]
    ped_root = bpy.data.objects.new('pedestal', None)
    bpy.context.scene.collection.objects.link(ped_root)
    for t in tiers:
        t.parent = ped_root

    bm2 = bmesh.new()
    bmesh.ops.create_cube(
        bm2,
        size=1.0,
        matrix=Matrix.Translation((0, -0.198, -0.078))
        @ Matrix.Diagonal((0.30, 0.012, 0.132, 1.0)),
    )
    plaque = _mesh_from_bm(bm2, 'pedestal_plaque', None)
    plaque_tex = _plaque_texture()
    p_mat = _mat_pbr('plaque', 0x5a3e1a, rough=0.32, metal=0.9, tex=plaque_tex)
    plaque.data.materials.append(p_mat)
    plaque.parent = ped_root
    return [ped_root, plaque]


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


def _draw_disk(buf, cx, cy, r, color):
    h, w = buf.shape[:2]
    y0 = max(0, int(cy - r))
    y1 = min(h, int(cy + r) + 1)
    x0 = max(0, int(cx - r))
    x1 = min(w, int(cx + r) + 1)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                buf[y, x, :3] = color


def _draw_line(buf, x0, y0, x1, y1, color, width=2):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1) * 2
    for i in range(steps + 1):
        t = i / steps
        x = int(x0 + (x1 - x0) * t)
        y = int(y0 + (y1 - y0) * t)
        _draw_disk(buf, x, y, width, color)


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


def _uv_span_ok(a, b, limit=110):
    return (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 <= limit * limit


def _bake_meridian_textures(body, records):
    base = np.array(hex_rgba(BRONZE_HEX)[:3], dtype=np.float32)
    dark = np.array(hex_rgba(MERIDIAN_LINE_HEX)[:3], dtype=np.float32)
    col_buf = np.ones((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32)
    col_buf[:, :, :3] = base
    col_buf[:, :, 3] = 1.0
    nrm = np.zeros((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32)
    nrm[:, :, :] = [0.5, 0.5, 1.0, 1.0]
    rough = np.ones((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32) * BRONZE_ROUGH

    groups = {}
    for rec in records:
        mer = rec.get('meridian') or ''
        if rec.get('midline'):
            key = (mer, 'm')
        else:
            key = (mer, 'pair')
        groups.setdefault(key, []).append(rec)

    def uv_for_rec(rec, side_mul=1.0):
        px = 0.0 if rec.get('midline') else rec['position'][0] * side_mul
        py, pz = rec['position'][1], rec['position'][2]
        wco = data_to_blender(px, py, pz)
        hit = _closest_on_body(body, wco)
        if hit is None:
            return None
        _, _, uv = hit
        return int(uv.x * (TEX_SIZE - 1)), int((1.0 - uv.y) * (TEX_SIZE - 1))

    line_w = 5
    dot_r = 4
    for _key, pts in groups.items():
        ordered = _sort_meridian_records(pts)
        uvs = []
        for rec in ordered:
            u = uv_for_rec(rec, 1.0)
            if u:
                uvs.append(u)
                _draw_disk(col_buf, u[0], u[1], dot_r, dark)
                nrm[u[1], u[0], 0] = 0.44
                nrm[u[1], u[0], 1] = 0.52
                rough[u[1], u[0], 0] = 0.42
        for a, b in zip(uvs, uvs[1:]):
            if not _uv_span_ok(a, b):
                continue
            _draw_line(col_buf, a[0], a[1], b[0], b[1], dark, line_w)
            _draw_line(nrm, a[0], a[1], b[0], b[1], [0.44, 0.44, 1.0], line_w)
            _draw_line(rough, a[0], a[1], b[0], b[1], [0.44, 0.44, 0.44], line_w)
        uvs_m = []
        for rec in ordered:
            if rec.get('midline'):
                continue
            u = uv_for_rec(rec, -1.0)
            if u:
                uvs_m.append(u)
                _draw_disk(col_buf, u[0], u[1], dot_r, dark)
        for a, b in zip(uvs_m, uvs_m[1:]):
            if not _uv_span_ok(a, b):
                continue
            _draw_line(col_buf, a[0], a[1], b[0], b[1], dark, line_w)

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
    bands = [
        ('feet', 0.0, 0.18),
        ('legs', 0.14, 0.74),
        ('torso', 0.68, 1.36),
        ('arms', 0.72, 1.32),
        ('head', 1.32, y1),
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


def _project_surface(body, records):
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
                pos = [px, py, pz]
                nrm = rec.get('normal', [0, 0, 1])
            else:
                wloc, wnorm, _ = hit
                pos = list(blender_to_data(wloc))
                nrm = blender_normal_to_data(wnorm)
            key = f'{code}{suffix}'
            instances[key] = {
                'position': [round(v, 5) for v in pos],
                'normal': [round(v, 5) for v in nrm],
            }
    return instances


def _write_surface_json(body, records):
    payload = {
        'version': 1,
        'dataBodyHeight': DATA_BODY_HEIGHT,
        'partBoxes': _compute_part_boxes(body),
        'instances': _project_surface(body, records),
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
        ('pedestal', Vector((0, -1.28, -0.055)), 58),
    ):
        look_at = Vector((0, -0.02, -0.075)) if name == 'pedestal' else center
        cam.location = look_at + off if name == 'pedestal' else center + off
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
    mat_hair = _mat_pbr('bronze_hair', BRONZE_HEX, rough=BRONZE_ROUGH, metal=BRONZE_METAL)
    mat_cloth = _mat_pbr('sash_cloth', 0xffffff, rough=0.62, metal=0.0)

    img_col, img_n, img_r = _bake_meridian_textures(body, records)
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
    objs.extend(_build_pedestal(mat_dark))

    root = bpy.data.objects.new('tcm_bronze_figure', None)
    bpy.context.scene.collection.objects.link(root)
    for ob in objs:
        if ob and ob.name in bpy.data.objects:
            ob.parent = root
    root['ground_y'] = 0.0
    root['data_body_height'] = DATA_BODY_HEIGHT

    _write_surface_json(body, records)
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