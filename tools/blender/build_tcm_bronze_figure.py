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


def _mat_pbr(name, base_hex, rough=0.3, metal=0.0, tex=None, normal_tex=None):
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
        links.new(t_n.outputs['Color'], nm.inputs['Color'])
        links.new(nm.outputs['Normal'], bsdf.inputs['Normal'])
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


def _add_hair(body, mat_hair):
    me = body.data
    z_cut = 1.58
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.verts.ensure_lookup_table()
    bm.faces.ensure_lookup_table()
    has_head = any(all(v.co.z >= z_cut for v in f.verts) for f in bm.faces)
    bm.free()
    if not has_head:
        return None
    shell = bmesh.new()
    shell.from_mesh(me)
    shell.verts.ensure_lookup_table()
    shell.faces.ensure_lookup_table()
    del_faces = [f for f in shell.faces if not all(v.co.z >= z_cut for v in f.verts)]
    if del_faces:
        bmesh.ops.delete(shell, geom=del_faces, context='FACES_ONLY')
    ob_shell = _mesh_from_bm(shell, 'bronze_hair_shell', mat_hair)
    mod = ob_shell.modifiers.new('Solidify', 'SOLIDIFY')
    mod.thickness = 0.009
    mod.offset = 1.0
    bpy.context.view_layer.objects.active = ob_shell
    ob_shell.select_set(True)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    ob_shell.select_set(False)

    bm_bun = bmesh.new()
    matrix = Matrix.Translation((0.0, 0.055, 1.66)) @ Matrix.Diagonal((0.042, 0.038, 0.040, 1.0))
    bmesh.ops.create_uvsphere(bm_bun, u_segments=16, v_segments=12, radius=1.0, matrix=matrix)
    ob_bun = _mesh_from_bm(bm_bun, 'bronze_hair_bun', mat_hair)
    return [ob_shell, ob_bun]


def _add_sash(mat_cloth):
    bm = bmesh.new()
    z0, z1 = 0.82, 1.02
    segs = 28
    inner_r, outer_r = 0.128, 0.148
    rings = []
    for z in (z0, z1):
        inner, outer = [], []
        for i in range(segs):
            ang = 2 * math.pi * i / segs
            c, s = math.cos(ang), math.sin(ang)
            inner.append(bm.verts.new((inner_r * c, inner_r * s, z)))
            outer.append(bm.verts.new((outer_r * c, outer_r * s, z)))
        rings.append((inner, outer))
    for j in range(segs):
        k = (j + 1) % segs
        i0, o0 = rings[0]
        i1, o1 = rings[1]
        bm.faces.new([o0[j], o0[k], o1[k], o1[j]])
        bm.faces.new([i0[j], i1[j], i1[k], i0[k]])
        bm.faces.new([i0[j], o0[j], o0[k], i0[k]])
        bm.faces.new([i1[j], i1[k], o1[k], o1[j]])
    ob = _mesh_from_bm(bm, 'bronze_sash', None)
    stripe_path = _make_stripe_tex()
    mat = _mat_pbr('sash_cloth', 0xffffff, rough=0.6, metal=0.0)
    nt = mat.node_tree
    for n in list(nt.nodes):
        if n.type == 'BSDF_PRINCIPLED':
            bsdf = n
            break
    else:
        bsdf = nt.nodes['Principled BSDF']
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(stripe_path, check_existing=True)
    tex.image.colorspace_settings.name = 'sRGB'
    tex.location = (-300, 0)
    nt.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    ob.data.materials.append(mat)
    uv = ob.data.uv_layers.active or ob.data.uv_layers.new()
    for poly in ob.data.polygons:
        for loop_i in poly.loop_indices:
            uv.data[loop_i].uv = (loop_i * 0.07, ob.data.loops[loop_i].vertex_index * 0.01)
    return ob


def _plaque_texture():
    w, h = 512, 128
    arr = np.zeros((h, w, 4), dtype=np.float32)
    arr[:, :, :3] = (0.08, 0.06, 0.04)
    arr[:, :, 3] = 1.0
    curve = bpy.data.curves.new('_plaque_curve', type='FONT')
    curve.body = '人體針灸銅人'
    curve.size = 0.22
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
                            arr[y, x, :3] = (0.86, 0.70, 0.38)
    bpy.data.objects.remove(ob, do_unlink=True)
    img = bpy.data.images.new('plaque_text', w, h, alpha=True)
    img.pixels.foreach_set(arr.ravel())
    img.pack()
    return img


def _build_pedestal(mat_dark):
    bm = bmesh.new()

    def box(cx, cy, cz, sx, sy, sz):
        bmesh.ops.create_cube(
            bm,
            size=1.0,
            matrix=Matrix.Translation((cx, cy, cz)) @ Matrix.Diagonal((sx, sy, sz, 1.0)),
        )

    # 三層金座：頂面 z=0，向下延伸（腳底仍貼 y=0 地面）
    box(0, 0, -0.04, 0.52, 0.46, 0.080)
    box(0, 0, -0.11, 0.40, 0.34, 0.060)
    box(0, 0, -0.165, 0.30, 0.26, 0.050)
    ped = _mesh_from_bm(bm, 'pedestal', mat_dark)

    bm2 = bmesh.new()
    bmesh.ops.create_cube(
        bm2,
        size=1.0,
        matrix=Matrix.Translation((0, -0.155, -0.055)) @ Matrix.Diagonal((0.22, 0.012, 0.10, 1.0)),
    )
    plaque = _mesh_from_bm(bm2, 'pedestal_plaque', None)
    plaque_tex = _plaque_texture()
    p_mat = _mat_pbr('plaque', 0x8a6528, rough=0.35, metal=0.85, tex=plaque_tex)
    plaque.data.materials.append(p_mat)
    return [ped, plaque]


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


def _bake_meridian_textures(body, records):
    base = np.array(hex_rgba(0xc89a4a)[:3], dtype=np.float32)
    dark = np.array([0.45, 0.32, 0.15], dtype=np.float32)
    col_buf = np.ones((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32)
    col_buf[:, :, :3] = base
    col_buf[:, :, 3] = 1.0
    nrm = np.zeros((TEX_SIZE, TEX_SIZE, 4), dtype=np.float32)
    nrm[:, :, :] = [0.5, 0.5, 1.0, 1.0]

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

    for key, pts in groups.items():
        uvs = []
        for rec in pts:
            if key[1] == 'm':
                u = uv_for_rec(rec, 1.0)
            else:
                u = uv_for_rec(rec, 1.0)
            if u:
                uvs.append(u)
                _draw_disk(col_buf, u[0], u[1], 4, dark)
                nrm[u[1], u[0], 0] = 0.45
                nrm[u[1], u[0], 1] = 0.55
        for a, b in zip(uvs, uvs[1:]):
            _draw_line(col_buf, a[0], a[1], b[0], b[1], dark, 2)
            _draw_line(nrm, a[0], a[1], b[0], b[1], [0.45, 0.45, 1.0], 2)
        if key[1] == 'pair':
            uvs_m = []
            for rec in pts:
                u = uv_for_rec(rec, -1.0)
                if u:
                    uvs_m.append(u)
                    _draw_disk(col_buf, u[0], u[1], 4, dark)
            for a, b in zip(uvs_m, uvs_m[1:]):
                _draw_line(col_buf, a[0], a[1], b[0], b[1], dark, 2)

    img_col = bpy.data.images.new('bronze_body_color', TEX_SIZE, TEX_SIZE, alpha=False)
    img_col.colorspace_settings.name = 'sRGB'
    img_col.pixels.foreach_set(col_buf.ravel())
    img_col.pack()

    img_n = bpy.data.images.new('bronze_body_normal', TEX_SIZE, TEX_SIZE, alpha=False)
    img_n.colorspace_settings.name = 'Non-Color'
    img_n.pixels.foreach_set(nrm.ravel())
    img_n.pack()
    return img_col, img_n


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
    sc.cycles.samples = 48
    sc.cycles.device = 'CPU'
    sc.render.resolution_x = 768
    sc.render.resolution_y = 1080
    world = bpy.data.worlds.new('preview_world')
    sc.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes['Background']
    bg.inputs[0].default_value = (0.08, 0.10, 0.14, 1)
    bg.inputs[1].default_value = 1.0
    sun = bpy.data.objects.new('preview_sun', bpy.data.lights.new('preview_sun', 'SUN'))
    sun.data.energy = 2.8
    sun.rotation_euler = (math.radians(55), 0, math.radians(25))
    sc.collection.objects.link(sun)
    cam = bpy.data.objects.new('preview_cam', bpy.data.cameras.new('preview_cam'))
    sc.collection.objects.link(cam)
    sc.camera = cam
    bb_min, bb_max = _print_world_bounds('preview')
    center = (bb_min + bb_max) * 0.5
    for name, off, lens in (
        ('front', Vector((0, -2.8, center.z)), 52),
        ('side', Vector((2.8, 0, center.z)), 52),
        ('face', Vector((0, -1.0, bb_max.z - 0.08)), 75),
    ):
        cam.location = center + off
        cam.data.lens = lens
        sc.render.filepath = os.path.join(out_dir, name + '.png')
        bpy.ops.object.select_all(action='DESELECT')
        cam.select_set(True)
        bpy.context.view_layer.objects.active = root
        bpy.ops.view3d.camera_to_view_selected() if False else None
        direction = center - cam.location
        cam.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()
        bpy.ops.render.render(write_still=True)
        print(f'PREVIEW {sc.render.filepath}')


def build(out_path):
    prepare_scene()
    with open(POINTS_JSON, encoding='utf-8') as f:
        records = json.load(f)

    body = _import_body()
    _optional_decimate(body)

    mat_bronze = _mat_pbr('bronze', 0xc89a4a, rough=0.3, metal=0.9)
    mat_dark = _mat_pbr('bronze_dark', 0x8a6528, rough=0.32, metal=0.88)
    mat_hair = _mat_pbr('bronze_hair', 0xc89a4a, rough=0.32, metal=0.9)
    mat_cloth = _mat_pbr('sash_cloth', 0xffffff, rough=0.6, metal=0.0)

    img_col, img_n = _bake_meridian_textures(body, records)
    mat_body = _mat_pbr('bronze', 0xc89a4a, rough=0.3, metal=0.9, tex=img_col, normal_tex=img_n)
    body.data.materials.clear()
    body.data.materials.append(mat_body)

    objs = [body]
    hair = _add_hair(body, mat_hair)
    if hair:
        objs.extend(hair)
    objs.append(_add_sash(mat_cloth))
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