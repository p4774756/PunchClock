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
MERIDIAN_SURFACE_OFFSET = 0.0012
MERIDIAN_PATH_STEP = 0.024
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
    """教學銅人胸部：強抹乳頭／乳暈區（≈2.5 cm），外圈 4 cm 輕抹。"""
    me = body.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.verts.ensure_lookup_table()
    nipple_zones = []
    chest_ring = []
    hubs = (Vector((0.082, -0.068, 1.198)), Vector((-0.082, -0.068, 1.198)))
    for v in bm.verts:
        x, y, z = v.co.x, v.co.y, v.co.z
        if z < 1.04 or z > 1.36:
            continue
        if y > 0.01:
            continue
        co = v.co
        in_nip = False
        for h in hubs:
            if (co - h).length <= 0.025:
                nipple_zones.append(v)
                in_nip = True
                break
        if in_nip:
            continue
        ax = abs(x)
        if y > 0.0 or ax < 0.03 or ax > 0.20:
            continue
        for h in hubs:
            d = (co - h).length
            if 0.02 < d <= 0.045:
                chest_ring.append(v)
                break
    if not nipple_zones:
        for v in bm.verts:
            x, y, z = v.co.x, v.co.y, v.co.z
            if z < 1.08 or z > 1.32 or y > -0.02:
                continue
            ax = abs(x)
            if ax < 0.06 or ax > 0.14:
                continue
            if -0.12 < y < -0.04:
                nipple_zones.append(v)
    if nipple_zones:
        for _ in range(22):
            bmesh.ops.smooth_vert(
                bm,
                verts=nipple_zones,
                factor=1.0,
                use_axis_x=True,
                use_axis_y=True,
                use_axis_z=True,
            )
    if chest_ring:
        for _ in range(10):
            bmesh.ops.smooth_vert(
                bm,
                verts=chest_ring,
                factor=0.85,
                use_axis_x=True,
                use_axis_y=True,
                use_axis_z=True,
            )
    all_smooth = list({id(v): v for v in nipple_zones + chest_ring}.values())
    if all_smooth:
        bmesh.ops.smooth_vert(
            bm,
            verts=all_smooth,
            factor=0.55,
            use_axis_x=True,
            use_axis_y=True,
            use_axis_z=True,
        )
    bm.to_mesh(me)
    bm.free()
    me.update()
    print(f'[body] chest smooth nipple={len(nipple_zones)} ring={len(chest_ring)}')


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
    return _mesh_from_bm(bm, name, mat)


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

    plaque_w = 0.52 * 0.74
    plaque_h = PEDESTAL_BASE_H * 0.82
    plaque_y = -(0.46 * 0.5 + 0.011)
    plaque_z = base_cz
    bm2 = bmesh.new()
    bmesh.ops.create_cube(
        bm2,
        size=1.0,
        matrix=Matrix.Translation((0, plaque_y, plaque_z))
        @ Matrix.Diagonal((plaque_w, 0.012, plaque_h, 1.0)),
    )
    plaque = _mesh_from_bm(bm2, 'pedestal_plaque', None)
    _assign_plaque_uv(plaque, plaque_w, plaque_h)
    plaque_tex = _plaque_texture()
    p_mat = _mat_pbr('plaque', 0x5a3e1a, rough=0.45, metal=0.35, tex=plaque_tex)
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
    for f in bm.faces:
        for loop in f.loops:
            co = loop.vert.co
            if f.normal.y < -0.5:
                loop[layer].uv = (
                    (co.x + pw * 0.5) / pw,
                    max(0.0, min(1.0, (co.z + ph * 0.5) / ph)),
                )
            else:
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
    [(0, 1.70, 0.05), (0, 1.74, 0.04), (0, 1.72, 0.11), (0, 1.68, 0.14)],
    [(0.055, 1.62, 0.10), (0.050, 1.56, 0.09), (0.045, 1.48, 0.08)],
    [(-0.055, 1.62, 0.10), (-0.050, 1.56, 0.09), (-0.045, 1.48, 0.08)],
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


def _snap_data_path(body, waypoints):
    snapped = []
    for wp in waypoints:
        if isinstance(wp, dict):
            px = 0.0 if wp.get('midline') else wp['position'][0]
            py, pz = wp['position'][1], wp['position'][2]
        else:
            px, py, pz = wp
        wco = data_to_blender(px, py, pz)
        hit = _closest_on_body(body, wco)
        if not hit:
            continue
        wloc, wnorm, _ = hit
        snapped.append(wloc + wnorm * MERIDIAN_SURFACE_OFFSET)
    if len(snapped) < 2:
        return []
    dense = []
    for i in range(len(snapped) - 1):
        a, b = snapped[i], snapped[i + 1]
        dist = (a - b).length
        steps = max(1, int(dist / MERIDIAN_PATH_STEP))
        for j in range(steps):
            dense.append(a.lerp(b, j / steps))
    dense.append(snapped[-1])
    out = []
    for p in dense:
        hit = _closest_on_body(body, p)
        if hit:
            wloc, wnorm, _ = hit
            out.append(wloc + wnorm * MERIDIAN_SURFACE_OFFSET)
    return out


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
        path = _snap_data_path(body, ordered)
        if len(path) >= 2:
            paths.append(path)
            mer = key[0] if key else ''
            if mer and mer not in MIDLINE_MERIDIANS:
                paths.append(_mirror_path_x(path))
    for waypoints in EXTRA_MERIDIAN_WAYPOINTS:
        path = _snap_data_path(body, waypoints)
        if len(path) >= 2:
            paths.append(path)
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


def _build_meridian_mesh(body, records, mat_meridian):
    paths = _meridian_paths_from_records(body, records)
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
    print(f'[meridians] paths={len(paths)} tris={tris}')
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
        ('chest', Vector((0, -0.55, 1.22)), 42),
        ('pedestal', Vector((0, -1.28, PEDESTAL_BASE_H * 0.5)), 58),
    ):
        look_at = (
            Vector((0, -0.22, PEDESTAL_BASE_H * 0.5))
            if name == 'pedestal'
            else (Vector((0, -0.05, 1.20)) if name == 'chest' else center)
        )
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
    mat_meridian = _mat_pbr(
        'meridian_dark',
        MERIDIAN_DARK_HEX,
        rough=MERIDIAN_ROUGH,
        metal=MERIDIAN_METAL,
    )

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