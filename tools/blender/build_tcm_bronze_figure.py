"""古典針灸銅人（庭院／館門共用 GLB）：平滑人體、青銅 PBR、石座。

座標匯出後 Three.js Y-up：y=0 地面，正面 +Z。人體資料空間高約 DATA_BODY_HEIGHT=1.72。

無頭重建：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_tcm_bronze_figure.py -- \\
    server/src/main/resources/public/models/tcm-bronze-figure.glb
"""
from __future__ import annotations

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
    bsdf.inputs['Specular IOR Level'].default_value = 0.55
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
    # kit：Blender Z=高、-Y=正面 → glTF Y=Z、+Z=-Y
    y_up = (mn.z, mx.z)
    x_lr = (mn.x, mx.x)
    z_fb = (-mx.y, -mn.y)
    print(
        f'BOUNDS_GLTF_YUP {label} '
        f'y(height)=[{y_up[0]:.3f},{y_up[1]:.3f}] x=[{x_lr[0]:.3f},{x_lr[1]:.3f}] '
        f'z(front)=[{z_fb[0]:.3f},{z_fb[1]:.3f}]'
    )
    return mn, mx


def _build_pedestal(mat_top, mat_dark):
    bm = bmesh.new()

    def box(cx, cy, cz, sx, sy, sz):
        bmesh.ops.create_cube(bm, size=1.0, matrix=(
            Matrix.Translation((cx, cy, cz))
            @ Matrix.Diagonal((sx, sy, sz, 1.0))
        ))

    box(0, 0, 0.025, 0.44, 0.36, 0.05)
    box(0, 0, 0.068, 0.34, 0.28, 0.04)
    box(0, 0, 0.098, 0.28, 0.22, 0.024)
    top = _mesh_from_bmesh(bm, 'pedestal', mat_top)
    _subdiv_apply(top, 1)
    bm2 = bmesh.new()
    bmesh.ops.create_cube(bm2, size=1.0, matrix=(
        Matrix.Translation((0, 0, 0.085))
        @ Matrix.Diagonal((0.36, 0.30, 0.015, 1.0))
    ))
    trim = _mesh_from_bmesh(bm2, 'pedestal_trim', mat_dark)
    return [top, trim]


def _add_ellipsoid(bm, cx, cy, cz, rx, ry, rz, u=14, v=11):
    m = Matrix.Translation((cx, cy, cz)) @ Matrix.Diagonal((rx, ry, rz, 1.0))
    bmesh.ops.create_uvsphere(bm, u_segments=u, v_segments=v, radius=1.0, matrix=m)


def _build_body(mat_main, mat_dark):
    bm = bmesh.new()
    z0 = 0.11

    parts = [
        (0, 0.01, z0 + 0.06, 0.10, 0.08, 0.04),
        (0, 0.01, z0 + 0.11, 0.11, 0.095, 0.05),
        (0.07, 0.02, z0 + 0.20, 0.078, 0.08, 0.10),
        (-0.07, 0.02, z0 + 0.20, 0.078, 0.08, 0.10),
        (0.078, 0.02, z0 + 0.50, 0.068, 0.068, 0.14),
        (-0.078, 0.02, z0 + 0.50, 0.068, 0.068, 0.14),
        (0, 0.03, z0 + 0.72, 0.15, 0.11, 0.14),
        (0, 0.04, z0 + 0.98, 0.19, 0.13, 0.26),
        (0, 0.04, z0 + 1.20, 0.20, 0.12, 0.17),
        (0, 0.04, z0 + 1.38, 0.11, 0.095, 0.11),
        (0, 0.05, z0 + 1.52, 0.115, 0.10, 0.135),
        (0, 0.05, z0 + 1.64, 0.058, 0.058, 0.045),
    ]
    for p in parts:
        _add_ellipsoid(bm, *p)

    def arm(side):
        sx = side * 0.21
        _add_ellipsoid(bm, sx, -0.03, z0 + 1.28, 0.058, 0.075, 0.10)
        _add_ellipsoid(bm, sx * 1.32, -0.07, z0 + 1.12, 0.050, 0.065, 0.12)
        _add_ellipsoid(bm, sx * 1.52, -0.11, z0 + 0.96, 0.044, 0.058, 0.11)
        _add_ellipsoid(bm, sx * 1.65, -0.13, z0 + 0.84, 0.040, 0.052, 0.065)

    arm(1)
    arm(-1)

    bm_dark = bmesh.new()
    _add_ellipsoid(bm_dark, 0, 0.06, z0 + 0.68, 0.155, 0.115, 0.045)
    _add_ellipsoid(bm_dark, 0, 0.07, z0 + 0.96, 0.165, 0.125, 0.055)

    body = _mesh_from_bmesh(bm, 'bronze_body', mat_main)
    sash = _mesh_from_bmesh(bm_dark, 'bronze_sash', mat_dark)
    _subdiv_apply(body, 2)
    _subdiv_apply(sash, 1)
    return [body, sash]


def build(out_path):
    prepare_scene()
    mat_bronze = _mat_bronze(
        'bronze', 0xc8a850, rough=0.32, metal=0.9, emit=0xffd080, emit_strength=0.03
    )
    mat_bronze_dark = _mat_bronze('bronze_dark', 0x7a5520, rough=0.42, metal=0.85)
    mat_stone = _mat_stone('stone', 0xc4b8a8)
    mat_stone_dark = _mat_stone('stone_dark', 0x8a8070, rough=0.92)

    objs = []
    objs.extend(_build_pedestal(mat_stone, mat_stone_dark))
    objs.extend(_build_body(mat_bronze, mat_bronze_dark))

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