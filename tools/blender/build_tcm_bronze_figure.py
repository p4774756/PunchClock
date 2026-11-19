"""古典針灸銅人（庭院／館門共用 GLB）：平滑人體、青銅 PBR、石座。

座標匯出後 Three.js Y-up：y=0 地面，正面 +Z。人體資料空間高約 DATA_BODY_HEIGHT=1.72。

無頭重建：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_tcm_bronze_figure.py -- \\
    server/src/main/resources/public/models/tcm-bronze-figure.glb
"""
from __future__ import annotations

import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import bpy
import bmesh
from mathutils import Matrix, Vector

from kit import argv_out, hex_rgba, prepare_scene


def _mat_bronze(name, base_hex, rough=0.38, metal=0.92, emit=None, emit_strength=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = hex_rgba(base_hex)
    bsdf.inputs['Roughness'].default_value = rough
    bsdf.inputs['Metallic'].default_value = metal
    bsdf.inputs['Specular IOR Level'].default_value = 0.72
    if emit is not None:
        bsdf.inputs['Emission Color'].default_value = hex_rgba(emit)
        bsdf.inputs['Emission Strength'].default_value = emit_strength
    return m


def _mat_stone(name, base_hex, rough=0.88):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = hex_rgba(base_hex)
    bsdf.inputs['Roughness'].default_value = rough
    bsdf.inputs['Metallic'].default_value = 0.05
    bsdf.inputs['Specular IOR Level'].default_value = 0.42
    return m


def _mesh_from_bmesh(bm, name, mat):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    ob.data.materials.append(mat)
    return ob


def _subdiv_apply(ob, levels=2):
    mod = ob.modifiers.new('Subsurf', 'SUBSURF')
    mod.levels = levels
    mod.render_levels = levels
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.shade_smooth()
    bpy.ops.object.modifier_apply(modifier=mod.name)
    ob.select_set(False)


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


def _v(x, y_up, z_front):
    """資料／glTF (x, y 高, z 前) → Blender (x, -z前, z高)."""
    return Vector((x, -z_front, y_up))


def _ellipse_ring(bm, center, z, rx, ry, segs=22):
    cx, cy = center
    verts = []
    for i in range(segs):
        t = 2.0 * math.pi * i / segs
        verts.append(bm.verts.new((cx + rx * math.cos(t), cy + ry * math.sin(t), z)))
    return verts


def _loft_sections(bm, sections):
    """sections: (y, cx, cz_front, rx, ry) in data space; cz_front maps to blender -Y."""
    rings = []
    for y, cx, cz, rx, ry in sections:
        rings.append(_ellipse_ring(bm, (cx, -cz), y, rx, ry))
    nseg = len(rings[0])
    for i in range(len(rings) - 1):
        a, b = rings[i], rings[i + 1]
        for j in range(nseg):
            bm.faces.new([a[j], a[(j + 1) % nseg], b[(j + 1) % nseg], b[j]])


def _cap_ring(bm, ring, tip):
    """封住管端：tip 為中心頂點座標。"""
    tip_v = bm.verts.new(tip)
    n = len(ring)
    for j in range(n):
        bm.faces.new([ring[j], ring[(j + 1) % n], tip_v])


def _tube(bm, sections, cap_start=False, cap_end=False):
    _loft_sections(bm, sections)
    if cap_start:
        y0, cx, cz, rx, ry = sections[0]
        ring = _ellipse_ring(bm, (cx, -cz), y0, rx * 0.35, ry * 0.35, segs=22)
        _cap_ring(bm, ring, _v(cx, y0 - 0.018, cz))
    if cap_end:
        y1, cx, cz, rx, ry = sections[-1]
        ring = _ellipse_ring(bm, (cx, -cz), y1, rx * 0.42, ry * 0.42, segs=22)
        _cap_ring(bm, ring, _v(cx, y1 + 0.012, cz))


def _add_uv_ellipsoid(bm, center, radii, u=16, v=12):
    m = Matrix.Translation(center) @ Matrix.Diagonal((radii[0], radii[1], radii[2], 1.0))
    bmesh.ops.create_uvsphere(bm, u_segments=u, v_segments=v, radius=1.0, matrix=m)


def _append_bmesh(dst, src):
    src.verts.ensure_lookup_table()
    src.faces.ensure_lookup_table()
    off = len(dst.verts)
    for v in src.verts:
        dst.verts.new(v.co)
    dst.verts.ensure_lookup_table()
    dst.verts.index_update()
    for face in src.faces:
        dst.faces.new([dst.verts[off + v.index] for v in face.verts])


def _build_pedestal(mat_top, mat_dark, mat_band):
    bm = bmesh.new()

    def box(cx, cy, cz, sx, sy, sz):
        bmesh.ops.create_cube(
            bm,
            size=1.0,
            matrix=Matrix.Translation((cx, cy, cz)) @ Matrix.Diagonal((sx, sy, sz, 1.0)),
        )

    # 三層淺色石座：台階分明、略高
    box(0, 0, 0.040, 0.54, 0.46, 0.080)
    box(0, 0, 0.102, 0.42, 0.36, 0.050)
    box(0, 0, 0.142, 0.32, 0.28, 0.038)
    top = _mesh_from_bmesh(bm, 'pedestal', mat_top)
    _subdiv_apply(top, 1)

    bm2 = bmesh.new()

    def box2(cx, cy, cz, sx, sy, sz):
        bmesh.ops.create_cube(
            bm2,
            size=1.0,
            matrix=Matrix.Translation((cx, cy, cz)) @ Matrix.Diagonal((sx, sy, sz, 1.0)),
        )

    # 層間淺槽與頂緣帶
    box2(0, 0, 0.082, 0.44, 0.38, 0.008)
    box2(0, 0, 0.128, 0.34, 0.30, 0.006)
    band = _mesh_from_bmesh(bm2, 'pedestal_band', mat_band)

    bm3 = bmesh.new()
    bmesh.ops.create_cube(
        bm3,
        size=1.0,
        matrix=Matrix.Translation((0, 0, 0.118)) @ Matrix.Diagonal((0.34, 0.30, 0.012, 1.0)),
    )
    for i in range(4):
        ang = math.pi * 0.5 * i + math.pi * 0.25
        bmesh.ops.create_cube(
            bm3,
            size=1.0,
            matrix=Matrix.Translation((0.36 * math.cos(ang), 0.36 * math.sin(ang), 0.052))
            @ Matrix.Diagonal((0.055, 0.048, 0.082, 1.0)),
        )
    trim = _mesh_from_bmesh(bm3, 'pedestal_trim', mat_dark)
    return [top, band, trim]


def _build_classical_head(bm, front):
    """頂髻、眼鼻口凹凸，非光滑圓球。"""
    head_y = 1.562
    head_z = front + 0.014

    _add_uv_ellipsoid(bm, _v(0.0, head_y, head_z), (0.090, 0.102, 0.118), u=24, v=16)
    _add_uv_ellipsoid(bm, _v(0.0, head_y - 0.020, head_z + 0.024), (0.078, 0.086, 0.094), u=20, v=14)

    # 頂髻（雙層、略扁且橫向拉開，配合 crown/topknot 吸附）
    _add_uv_ellipsoid(bm, _v(0.0, 1.676, head_z - 0.058), (0.062, 0.034, 0.054), u=14, v=12)
    _add_uv_ellipsoid(bm, _v(0.0, 1.698, head_z - 0.064), (0.048, 0.044, 0.048), u=12, v=10)

    brow_y = head_y + 0.022
    for side in (1, -1):
        eye = bmesh.new()
        _add_uv_ellipsoid(
            eye,
            _v(side * 0.034, head_y + 0.008, head_z + 0.086),
            (0.019, 0.013, 0.011),
            u=8,
            v=6,
        )
        _append_bmesh(bm, eye)
        eye.free()
        brow = bmesh.new()
        _add_uv_ellipsoid(
            brow,
            _v(side * 0.034, brow_y, head_z + 0.078),
            (0.022, 0.006, 0.008),
            u=6,
            v=4,
        )
        _append_bmesh(bm, brow)
        brow.free()
        ear = bmesh.new()
        _add_uv_ellipsoid(
            ear,
            _v(side * 0.084, head_y - 0.008, head_z - 0.012),
            (0.015, 0.024, 0.013),
            u=8,
            v=6,
        )
        _append_bmesh(bm, ear)
        ear.free()

    nose = bmesh.new()
    _add_uv_ellipsoid(nose, _v(0.0, head_y - 0.006, head_z + 0.096), (0.015, 0.028, 0.030), u=10, v=8)
    _append_bmesh(bm, nose)
    nose.free()

    mouth = bmesh.new()
    _add_uv_ellipsoid(mouth, _v(0.0, head_y - 0.042, head_z + 0.078), (0.028, 0.008, 0.012), u=8, v=6)
    _append_bmesh(bm, mouth)
    mouth.free()


def _build_body(mat_main, mat_dark, mat_patina):
    """站姿銅人：管狀軀幹四肢＋古典頭面，非光滑圓球。"""
    bm = bmesh.new()
    front = 0.038

    # —— 軀幹（胸腹背一体）——
    torso = [
        (1.34, 0.0, front, 0.075, 0.070),   # 頸根
        (1.24, 0.0, front - 0.004, 0.118, 0.092),   # 肩線
        (1.16, 0.0, front - 0.006, 0.152, 0.100),   # 肩膊
        (1.06, 0.0, front - 0.01, 0.132, 0.098),  # 胸
        (0.94, 0.0, front, 0.118, 0.090),   # 腰
        (0.82, 0.0, front - 0.006, 0.132, 0.104),  # 腹
        (0.74, 0.0, front, 0.142, 0.108),  # 胯
    ]
    _loft_sections(bm, torso)

    # —— 頭頸 ——
    neck = [
        (1.38, 0.0, front, 0.048, 0.050),
        (1.34, 0.0, front, 0.058, 0.056),
    ]
    _loft_sections(bm, neck)

    _build_classical_head(bm, front)

    # —— 腿（分左右，能辨識大腿／小腿／腳）——
    def leg(side):
        ox = side * 0.085
        sections = [
            (0.76, ox, front, 0.092, 0.088),
            (0.54, ox * 0.95, front, 0.072, 0.076),
            (0.34, ox * 0.88, front + 0.01, 0.058, 0.062),
            (0.18, ox * 0.82, front + 0.02, 0.062, 0.068),
            (0.075, ox * 0.75, front + 0.03, 0.082, 0.095),
            (0.028, ox * 0.70, front + 0.035, 0.095, 0.11),
        ]
        _tube(bm, sections, cap_end=True)

    leg(1)
    leg(-1)

    # —— 手臂（肩—肘—腕—手，略離身）——
    def arm(side):
        s = side
        upper = [
            (1.28, s * 0.155, front - 0.008, 0.058, 0.055),
            (1.14, s * 0.182, front - 0.015, 0.050, 0.048),
            (1.02, s * 0.198, front - 0.022, 0.044, 0.044),
        ]
        _loft_sections(bm, upper)
        fore = [
            (1.02, s * 0.198, front - 0.022, 0.042, 0.040),
            (0.90, s * 0.228, front - 0.028, 0.038, 0.038),
            (0.82, s * 0.248, front - 0.032, 0.036, 0.036),
        ]
        _loft_sections(bm, fore)
        hand = bmesh.new()
        _add_uv_ellipsoid(hand, _v(s * 0.258, 0.795, front - 0.034), (0.034, 0.030, 0.042), u=10, v=8)
        _append_bmesh(bm, hand)
        hand.free()

    arm(1)
    arm(-1)

    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.008)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)

    # —— 中衣／飾帶／袍擺（獨立網格）——
    bm_robe = bmesh.new()
    _loft_sections(
        bm_robe,
        [
            (0.96, 0.0, front + 0.018, 0.122, 0.068),
            (0.92, 0.0, front + 0.022, 0.128, 0.072),
            (0.88, 0.0, front + 0.018, 0.122, 0.068),
        ],
    )
    _loft_sections(
        bm_robe,
        [
            (1.08, 0.0, front + 0.012, 0.138, 0.078),
            (1.02, 0.0, front + 0.014, 0.142, 0.080),
            (0.98, 0.0, front + 0.012, 0.136, 0.076),
        ],
    )
    _loft_sections(
        bm_robe,
        [
            (0.62, 0.0, front + 0.024, 0.148, 0.082),
            (0.48, 0.0, front + 0.030, 0.158, 0.088),
            (0.34, 0.0, front + 0.028, 0.142, 0.082),
            (0.22, 0.0, front + 0.022, 0.122, 0.072),
        ],
    )

    body = _mesh_from_bmesh(bm, 'bronze_body', mat_main)
    sash = _mesh_from_bmesh(bm_robe, 'bronze_sash', mat_dark)
    pat = bmesh.new()
    _loft_sections(
        pat,
        [
            (0.52, 0.0, front - 0.105, 0.10, 0.014),
            (0.48, 0.0, front - 0.108, 0.11, 0.015),
            (0.44, 0.0, front - 0.105, 0.10, 0.014),
        ],
    )
    patina = _mesh_from_bmesh(pat, 'bronze_patina', mat_patina)
    _subdiv_apply(body, 2)
    _subdiv_apply(sash, 1)
    _subdiv_apply(patina, 1)
    return [body, sash, patina]


def build(out_path):
    prepare_scene()
    mat_bronze = _mat_bronze(
        'bronze',
        0xe8c468,
        rough=0.13,
        metal=0.94,
        emit=0xfff0c8,
        emit_strength=0.038,
    )
    mat_bronze_dark = _mat_bronze('bronze_dark', 0x9e7c4c, rough=0.22, metal=0.90)
    mat_patina = _mat_bronze('bronze_patina', 0x7a8260, rough=0.36, metal=0.82)
    mat_stone = _mat_stone('stone', 0xf6f0e8, rough=0.68)
    mat_stone_band = _mat_stone('stone_band', 0xeae2d8, rough=0.72)
    mat_stone_dark = _mat_stone('stone_dark', 0xd8cec2, rough=0.76)

    objs = []
    objs.extend(_build_pedestal(mat_stone, mat_stone_dark, mat_stone_band))
    objs.extend(_build_body(mat_bronze, mat_bronze_dark, mat_patina))

    root = bpy.data.objects.new('tcm_bronze_figure', None)
    bpy.context.scene.collection.objects.link(root)
    for ob in objs:
        ob.parent = root

    root['ground_y'] = 0.0
    root['data_body_height'] = 1.72

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
    )
    tris = sum(
        len(p.vertices) - 2
        for o in bpy.data.objects if o.type == 'MESH'
        for p in o.data.polygons
    )
    size = os.path.getsize(out_path)
    print(f'EXPORTED {out_path} tris≈{tris} bytes={size}')
    return tris, size


if __name__ == '__main__':
    out = argv_out('server/src/main/resources/public/models/tcm-bronze-figure.glb')
    build(out)