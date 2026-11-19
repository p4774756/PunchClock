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


def _subdiv(ob, levels=2):
    mod = ob.modifiers.new('Subsurf', 'SUBSURF')
    mod.levels = levels
    mod.render_levels = levels
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.shade_smooth()
    ob.select_set(False)


def _build_pedestal(mat_top, mat_dark):
    bm = bmesh.new()
    # glTF y-up：Blender Z = 高度
    def box(cx, cy, cz, sx, sy, sz):
        bmesh.ops.create_cube(bm, size=1.0, matrix=(
            __import__('mathutils').Matrix.Translation((cx, cy, cz))
            @ __import__('mathutils').Matrix.Diagonal((sx, sy, sz, 1.0))
        ))

    # 石座三層
    box(0, 0, 0.025, 0.44, 0.36, 0.05)
    box(0, 0, 0.068, 0.34, 0.28, 0.04)
    box(0, 0, 0.098, 0.28, 0.22, 0.024)
    top = _mesh_from_bmesh(bm, 'pedestal', mat_top)
    _subdiv(top, 1)
    bm2 = bmesh.new()
    bmesh.ops.create_cube(bm2, size=1.0, matrix=(
        __import__('mathutils').Matrix.Translation((0, 0, 0.085))
        @ __import__('mathutils').Matrix.Diagonal((0.36, 0.30, 0.015, 1.0))
    ))
    trim = _mesh_from_bmesh(bm2, 'pedestal_trim', mat_dark)
    return [top, trim]


def _add_ellipsoid(bm, cx, cy, cz, rx, ry, rz):
    bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=10, radius=1.0)
    for v in bm.verts:
        v.co.x = v.co.x * rx + cx
        v.co.y = v.co.y * ry + cy
        v.co.z = v.co.z * rz + cz


def _build_body(mat_main, mat_dark):
    bm = bmesh.new()
    z0 = 0.11  # 腳底略高於石座頂

    # 頭、頸、軀幹、四肢 — 橢球拼接後 subsurf
    parts = [
        (0, 0, z0 + 0.09, 0.11, 0.085, 0.055),      # 腳
        (0.07, 0.01, z0 + 0.20, 0.075, 0.075, 0.10),
        (-0.07, 0.01, z0 + 0.20, 0.075, 0.075, 0.10),
        (0.075, 0.01, z0 + 0.50, 0.065, 0.065, 0.14),  # 小腿
        (-0.075, 0.01, z0 + 0.50, 0.065, 0.065, 0.14),
        (0, 0.02, z0 + 0.72, 0.14, 0.10, 0.14),    # 骨盆
        (0, 0.02, z0 + 0.98, 0.17, 0.12, 0.24),    # 胸腹
        (0, 0.02, z0 + 1.22, 0.19, 0.11, 0.18),    # 胸上
        (0, 0.03, z0 + 1.42, 0.10, 0.09, 0.09),    # 頸
        (0, 0.04, z0 + 1.56, 0.11, 0.095, 0.13),   # 頭
        (0, 0.04, z0 + 1.67, 0.055, 0.055, 0.04),  # 髻
    ]
    for p in parts:
        _add_ellipsoid(bm, *p)

    # 手臂（微張，掌心朝前 -Y）
    def arm(side):
        sx = side * 0.22
        _add_ellipsoid(bm, sx, -0.02, z0 + 1.28, 0.055, 0.07, 0.10)   # 肩
        _add_ellipsoid(bm, sx * 1.35, -0.06, z0 + 1.12, 0.048, 0.06, 0.12)
        _add_ellipsoid(bm, sx * 1.55, -0.10, z0 + 0.95, 0.042, 0.055, 0.11)
        _add_ellipsoid(bm, sx * 1.68, -0.12, z0 + 0.84, 0.038, 0.05, 0.06)

    arm(1)
    arm(-1)

    # 簡潔中衣褶（深色青銅飾帶，不做裸露）
    bm_dark = bmesh.new()
    _add_ellipsoid(bm_dark, 0, 0.05, z0 + 0.66, 0.15, 0.11, 0.04)

    body = _mesh_from_bmesh(bm, 'bronze_body', mat_main)
    sash = _mesh_from_bmesh(bm_dark, 'bronze_sash', mat_dark)
    _subdiv(body, 2)
    _subdiv(sash, 1)
    return [body, sash]


def build(out_path):
    prepare_scene()
    mat_bronze = _mat_bronze('bronze', 0xc8a850, rough=0.32, metal=0.9, emit=0xffd080, emit_strength=0.03)
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
    tris = sum(len(p.vertices) - 2 for o in bpy.data.objects if o.type == 'MESH' for p in o.data.polygons)
    size = os.path.getsize(out_path)
    print(f'EXPORTED {out_path} tris≈{tris} bytes={size}')
    return tris, size


if __name__ == '__main__':
    out = argv_out('server/src/main/resources/public/models/tcm-bronze-figure.glb')
    build(out)