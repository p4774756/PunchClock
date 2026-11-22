"""七種 Q 版女性行人 NPC → 單一 npc-women.glb（含 walk／idle；傘桌可選 sit_idle）。

無頭重建：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_npc_women.py -- \\
    server/src/main/resources/public/models/npc-women.glb

腳趾 y=0（匯出後 glTF Y-up）；高度約 1.0，頭部約總高 1/3。
"""
from __future__ import annotations

import json
import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import bpy
import bmesh
from mathutils import Euler, Vector

import kit
from kit import hex_rgba, prepare_scene, argv_out, srgb_to_lin

# ---------- 七種造型（不含旗袍）----------
STYLES = (
    'summer', 'student', 'tech', 'idol', 'swimsuit', 'sport', 'fairy',
)

PALETTE = {
    'skin': 0xf3d0b0,
    'skin_shadow': 0xe8b898,
    'hair_mint': 0x7ecfc4,
    'hair_navy': 0x1f3355,
    'hair_silver': 0xdde4ea,
    'hair_blonde': 0xf5d878,
    'hair_teal': 0x4db8a8,
    'hair_orange': 0xf08030,
    'hair_purple': 0xc8a0e8,
    'eye_white': 0xf8f8ff,
    'eye_pupil': 0x2a2838,
    'lip': 0xe8a0a8,
    'white': 0xfffaf6,
    'navy': 0x1f3355,
    'red': 0xd83840,
    'brown': 0x6b4634,
    'yellow': 0xf6e878,
    'cream': 0xfff5e0,
    'pink': 0xf4a0c0,
    'pink_hot': 0xff78b0,
    'cyan': 0x48d8f0,
    'cyan_glow': 0x30c8e8,
    'black': 0x181820,
    'grey': 0x8890a0,
    'blue_swim': 0x2878c0,
    'gold': 0xd4a848,
    'straw': 0xe8c878,
    'purple_light': 0xd8b8f0,
    'purple_gown': 0xc0a0e0,
}


def _mat(name, hex_rgb, rough=0.72, emit=None):
    if name in bpy.data.materials:
        return bpy.data.materials[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = hex_rgba(hex_rgb)
    bsdf.inputs['Roughness'].default_value = rough
    if emit:
        bsdf.inputs['Emission Color'].default_value = hex_rgba(emit)
        bsdf.inputs['Emission Strength'].default_value = 0.85
    return m


def _ensure_mats():
    for k, h in PALETTE.items():
        emit = PALETTE.get(k + '_glow')
        if k.endswith('_glow'):
            continue
        em = PALETTE['cyan_glow'] if k == 'cyan' else None
        _mat('npc_' + k, h, emit=em if k == 'cyan' else None)


def _append_bmesh(dst, src):
    """把 sub-bmesh 併入 dst（避免在同一 bmesh 連續 create 時焊到舊頂點）。"""
    src.verts.ensure_lookup_table()
    src.faces.ensure_lookup_table()
    off = len(dst.verts)
    for v in src.verts:
        dst.verts.new(v.co)
    dst.verts.ensure_lookup_table()
    dst.verts.index_update()
    for face in src.faces:
        dst.faces.new([dst.verts[off + v.index] for v in face.verts])


def _uv_sphere(bm, co, radius, seg=8, rings=6):
    sub = bmesh.new()
    bmesh.ops.create_uvsphere(sub, u_segments=seg, v_segments=rings, radius=radius)
    bmesh.ops.translate(sub, vec=co, verts=sub.verts)
    _append_bmesh(bm, sub)
    sub.free()


def _uv_box(bm, size, co):
    sx, sy, sz = size
    sub = bmesh.new()
    bmesh.ops.create_cube(sub, size=1.0)
    bmesh.ops.scale(sub, vec=(sx / 2, sy / 2, sz / 2), verts=sub.verts)
    bmesh.ops.translate(sub, vec=co, verts=sub.verts)
    _append_bmesh(bm, sub)
    sub.free()


_NPC_VERTEX_MAT = None


def _npc_vertex_color_material():
    """單一材質；頂點色 COLOR_0（glTF）。"""
    global _NPC_VERTEX_MAT
    if _NPC_VERTEX_MAT is not None:
        return _NPC_VERTEX_MAT
    m = bpy.data.materials.new('npc_vertex_color')
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes.get('Principled BSDF')
    attr = nt.nodes.new('ShaderNodeVertexColor')
    attr.layer_name = 'COLOR_0'
    nt.links.new(attr.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.74
    _NPC_VERTEX_MAT = m
    return m


def _palette_color_rgba(mat_name):
    h = PALETTE.get(mat_name, PALETTE['skin'])
    return hex_rgba(h)


def _join_meshes(name, pieces):
    """pieces: list of (palette_key, bmesh_ops_fn) — 合併為單網格、頂點色分色。"""
    bm = bmesh.new()
    color_lay = bm.loops.layers.color.new('COLOR_0')

    for mat_name, build_fn in pieces:
        sub = bmesh.new()
        build_fn(sub)
        sub.faces.ensure_lookup_table()
        col = _palette_color_rgba(mat_name)
        offset = len(bm.verts)
        for v in sub.verts:
            bm.verts.new(v.co)
        bm.verts.ensure_lookup_table()
        bm.verts.index_update()
        for face in sub.faces:
            nf = bm.faces.new([bm.verts[offset + v.index] for v in face.verts])
            for loop in nf.loops:
                loop[color_lay] = col
        sub.free()
    bm.faces.ensure_lookup_table()
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    loose = [v for v in bm.verts if not v.link_faces]
    if loose:
        bmesh.ops.delete(bm, geom=loose, context='VERTS')
    bm.normal_update()
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(_npc_vertex_color_material())
    obj = bpy.data.objects.new(name, me)
    return obj


def _face_pieces(pieces):
    """頭部與五官分色，避免全膚色糊成一團。"""
    def head_skin(bm):
        _uv_sphere(bm, (0, 0, 0.78), 0.172, 9, 7)
        _uv_sphere(bm, (0, 0.052, 0.788), 0.02, 5, 4)

    pieces.append(('skin', head_skin))

    def eye_white(bm):
        for sx in (-0.055, 0.055):
            _uv_box(bm, (0.038, 0.014, 0.012), (sx, 0.188, 0.796))

    pieces.append(('eye_white', eye_white))

    def eye_pupil(bm):
        for sx in (-0.055, 0.055):
            _uv_box(bm, (0.02, 0.006, 0.006), (sx, 0.196, 0.798))

    pieces.append(('eye_pupil', eye_pupil))

    def lips(bm):
        _uv_box(bm, (0.05, 0.014, 0.01), (0, 0.158, 0.768))

    pieces.append(('lip', lips))


def _style_mesh(style):
    """回傳 mesh object（未綁骨）。"""
    skin = 'skin'
    pieces = []
    _face_pieces(pieces)

    def torso_base(bm):
        _uv_box(bm, (0.26, 0.14, 0.26), (0, 0, 0.52))

    if style == 'summer':
        def hair(bm):
            _uv_sphere(bm, (0, 0.02, 0.9), 0.2, 9, 7)
            for side in (-1, 1):
                _uv_sphere(bm, (side * 0.15, -0.02, 0.73), 0.09, 7, 5)
                _uv_sphere(bm, (side * 0.17, 0.04, 0.68), 0.06, 6, 4)
        pieces.append(('hair_mint', hair))

        def dress(bm):
            _uv_box(bm, (0.32, 0.13, 0.24), (0, 0, 0.47))
            _uv_box(bm, (0.36, 0.11, 0.16), (0, 0, 0.35))
            _uv_box(bm, (0.34, 0.05, 0.1), (0, 0.07, 0.42))
        pieces.append(('yellow', dress))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.22), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.11, 0.05, 0.09), (side * 0.08, 0.06, 0.07))
        pieces.append((skin, legs))

    elif style == 'student':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.87), 0.188, 9, 7)
            _uv_box(bm, (0.22, 0.05, 0.09), (0, 0.13, 0.925))
            for side in (-1, 1):
                _uv_box(bm, (0.08, 0.04, 0.06), (side * 0.1, 0.1, 0.84))
        pieces.append(('hair_navy', hair))

        def uniform(bm):
            _uv_box(bm, (0.3, 0.15, 0.25), (0, 0, 0.52))
            _uv_box(bm, (0.1, 0.06, 0.06), (0, -0.09, 0.57))
            _uv_box(bm, (0.35, 0.12, 0.2), (0, 0, 0.36))
        pieces.append(('white', uniform))
        pieces.append(('red', lambda b: _uv_box(b, (0.12, 0.05, 0.025), (0, -0.01, 0.595))))
        pieces.append(('navy', lambda b: _uv_box(b, (0.36, 0.11, 0.17), (0, 0, 0.34))))

        def bag(bm):
            _uv_box(bm, (0.16, 0.07, 0.2), (0.16, -0.05, 0.46))
            _uv_box(bm, (0.03, 0.02, 0.04), (0.1, -0.02, 0.55))
        pieces.append(('brown', bag))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.22), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.12, 0.06, 0.11), (side * 0.08, 0.045, 0.06))
        def arms(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.08, 0.08, 0.22), (side * 0.2, 0, 0.48))
        pieces.append((skin, arms))
        pieces.append((skin, legs))

    elif style == 'tech':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.885), 0.175, 9, 7)
            _uv_box(bm, (0.07, 0.07, 0.26), (0, -0.15, 0.93))
            _uv_box(bm, (0.05, 0.04, 0.08), (0, -0.1, 1.0))
        pieces.append(('hair_silver', hair))

        def suit(bm):
            _uv_box(bm, (0.26, 0.14, 0.3), (0, 0, 0.5))
            _uv_box(bm, (0.32, 0.12, 0.16), (0, 0, 0.35))
            for side in (-1, 1):
                _uv_box(bm, (0.1, 0.08, 0.22), (side * 0.2, 0.02, 0.48))
        pieces.append(('black', suit))
        pieces.append(('cyan', lambda b: _uv_box(b, (0.22, 0.01, 0.18), (0, 0.07, 0.52))))
        pieces.append(('white', lambda b: _uv_box(b, (0.1, 0.07, 0.11), (0, 0.05, 0.555))))

        def goggles(bm):
            _uv_box(bm, (0.24, 0.04, 0.05), (0, 0.19, 0.82))
            for side in (-1, 1):
                _uv_box(bm, (0.04, 0.02, 0.02), (side * 0.12, 0.19, 0.825))
        pieces.append(('cyan', goggles))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.1, 0.1, 0.24), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.13, 0.08, 0.13), (side * 0.08, 0.04, 0.07))
        pieces.append((skin, legs))

    elif style == 'idol':
        def hair(bm):
            _uv_sphere(bm, (0, 0.02, 0.888), 0.175, 9, 7)
            for side in (-1, 1):
                _uv_box(bm, (0.08, 0.08, 0.3), (side * 0.14, -0.08, 0.76))
                _uv_sphere(bm, (side * 0.13, 0.04, 0.98), 0.065, 7, 5)
                _uv_box(bm, (0.06, 0.03, 0.05), (side * 0.12, -0.04, 1.0))
        pieces.append(('hair_blonde', hair))

        def outfit(bm):
            _uv_box(bm, (0.28, 0.13, 0.22), (0, 0, 0.5))
            _uv_box(bm, (0.34, 0.11, 0.14), (0, 0, 0.35))
            for side in (-1, 1):
                _uv_box(bm, (0.08, 0.08, 0.14), (side * 0.19, 0.04, 0.46))
        pieces.append(('pink', outfit))
        pieces.append(('pink_hot', lambda b: _uv_box(b, (0.07, 0.05, 0.05), (-0.11, 0, 0.99))))
        pieces.append(('pink_hot', lambda b: _uv_box(b, (0.07, 0.05, 0.05), (0.11, 0, 0.99))))
        pieces.append(('white', lambda b: _uv_box(b, (0.15, 0.07, 0.09), (0, 0, 0.585))))

        def mic(bm):
            _uv_box(bm, (0.04, 0.04, 0.14), (0.26, 0.02, 0.56))
            _uv_sphere(bm, (0.26, 0.02, 0.64), 0.045, 6, 5)
        pieces.append(('grey', mic))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.22), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.12, 0.07, 0.11), (side * 0.08, 0.04, 0.06))
        pieces.append((skin, legs))

    elif style == 'swimsuit':
        def hair(bm):
            _uv_sphere(bm, (0, 0.02, 0.89), 0.185, 9, 7)
            for side in (-1, 1):
                _uv_sphere(bm, (side * 0.13, -0.02, 0.71), 0.08, 7, 5)
                _uv_sphere(bm, (side * 0.15, 0.02, 0.65), 0.055, 6, 4)
        pieces.append(('hair_teal', hair))

        def swim(bm):
            _uv_box(bm, (0.26, 0.11, 0.28), (0, 0, 0.48))
            _uv_box(bm, (0.14, 0.06, 0.12), (0, 0.04, 0.52))
        pieces.append(('blue_swim', swim))

        def sarong(bm):
            _uv_box(bm, (0.3, 0.05, 0.16), (0, 0.07, 0.33))
            _uv_box(bm, (0.18, 0.03, 0.08), (0.12, 0.05, 0.34))
        pieces.append(('white', sarong))

        def hat(bm):
            _uv_box(bm, (0.24, 0.05, 0.24), (0, 0, 1.02))
            _uv_box(bm, (0.28, 0.02, 0.1), (0, 0, 1.06))
        pieces.append(('straw', hat))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.24), (side * 0.08, 0, 0.22))
        pieces.append((skin, legs))

    elif style == 'sport':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.885), 0.175, 9, 7)
            _uv_box(bm, (0.08, 0.08, 0.24), (0, -0.13, 0.91))
            _uv_box(bm, (0.05, 0.03, 0.06), (0, -0.16, 0.98))
        pieces.append(('hair_orange', hair))
        pieces.append(('cyan', lambda b: _uv_box(b, (0.07, 0.025, 0.035), (0, -0.11, 0.99))))

        def jacket(bm):
            _uv_box(bm, (0.3, 0.13, 0.26), (0, 0, 0.52))
            for side in (-1, 1):
                _uv_box(bm, (0.12, 0.06, 0.18), (side * 0.22, 0.02, 0.47))
            _uv_box(bm, (0.08, 0.04, 0.08), (0, 0.09, 0.56))
        pieces.append(('navy', jacket))
        pieces.append(('white', lambda b: _uv_box(b, (0.28, 0.09, 0.09), (0, 0.075, 0.54))))

        def shorts(bm):
            _uv_box(bm, (0.32, 0.11, 0.15), (0, 0, 0.355))
        pieces.append(('navy', shorts))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.1, 0.1, 0.16), (side * 0.08, 0, 0.14))
                _uv_box(bm, (0.14, 0.08, 0.12), (side * 0.08, 0.04, 0.06))
        pieces.append((skin, legs))
        pieces.append(('red', lambda b: _uv_box(b, (0.05, 0.035, 0.065), (-0.09, 0.035, 0.05))))

    elif style == 'fairy':
        def hair(bm):
            _uv_sphere(bm, (0, 0.02, 0.905), 0.195, 9, 7)
            for side in (-1, 1):
                _uv_sphere(bm, (side * 0.15, -0.02, 0.66), 0.09, 7, 5)
                _uv_sphere(bm, (side * 0.17, 0.04, 0.6), 0.06, 6, 4)
            _uv_box(bm, (0.05, 0.05, 0.09), (0, 0.13, 0.97))
        pieces.append(('hair_purple', hair))
        pieces.append(('gold', lambda b: _uv_box(b, (0.07, 0.012, 0.05), (0, 0.15, 0.99))))

        def shoulders(bm):
            for side in (-1, 1):
                _uv_sphere(bm, (side * 0.16, 0, 0.56), 0.06, 6, 5)
            _uv_box(bm, (0.12, 0.04, 0.1), (0, 0, 0.58))
        pieces.append((skin, shoulders))

        def torso_gauze(bm):
            _uv_box(bm, (0.14, 0.06, 0.12), (0, 0, 0.52))
            _uv_box(bm, (0.16, 0.04, 0.08), (0, 0, 0.46))
        pieces.append(('purple_light', torso_gauze))

        def gown(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.1, 0.03, 0.28), (side * 0.24, 0.02, 0.42))
                _uv_box(bm, (0.06, 0.025, 0.18), (side * 0.2, 0.03, 0.52))
            _uv_box(bm, (0.3, 0.04, 0.2), (0, 0.04, 0.32))
            _uv_box(bm, (0.08, 0.02, 0.14), (0, 0, 0.36))
        pieces.append(('purple_gown', gown))

        def sash(bm):
            for side in (-1, 1):
                _uv_sphere(bm, (side * 0.2, 0.05, 0.54), 0.03, 5, 4)
        pieces.append(('gold', sash))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.26), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.11, 0.05, 0.09), (side * 0.08, 0.05, 0.08))
        pieces.append((skin, legs))

    else:
        pieces.append((skin, torso_base))

    return _join_meshes('mesh_' + style, pieces)


def _make_armature(name):
    arm_data = bpy.data.armatures.new(name + '_data')
    rig = bpy.data.objects.new(name + '_rig', arm_data)
    bpy.context.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    bones = [
        ('root', (0, 0, 0), (0, 0, 0.12)),
        ('spine', (0, 0, 0.12), (0, 0, 0.55)),
        ('head', (0, 0, 0.55), (0, 0, 0.85)),
        ('thigh.L', (0.08, 0, 0.42), (0.08, 0, 0.18)),
        ('shin.L', (0.08, 0, 0.18), (0.08, 0, 0.02)),
        ('thigh.R', (-0.08, 0, 0.42), (-0.08, 0, 0.18)),
        ('shin.R', (-0.08, 0, 0.18), (-0.08, 0, 0.02)),
        ('upper_arm.L', (0.18, 0, 0.52), (0.22, 0, 0.34)),
        ('forearm.L', (0.22, 0, 0.34), (0.24, 0, 0.22)),
        ('upper_arm.R', (-0.18, 0, 0.52), (-0.22, 0, 0.34)),
        ('forearm.R', (-0.22, 0, 0.34), (-0.24, 0, 0.22)),
    ]
    edit = arm_data.edit_bones
    created = {}
    for bname, head, tail in bones:
        eb = edit.new(bname)
        eb.head = Vector(head)
        eb.tail = Vector(tail)
        created[bname] = eb
    created['spine'].parent = created['root']
    created['head'].parent = created['spine']
    created['thigh.L'].parent = created['spine']
    created['thigh.R'].parent = created['spine']
    created['shin.L'].parent = created['thigh.L']
    created['shin.R'].parent = created['thigh.R']
    created['upper_arm.L'].parent = created['spine']
    created['upper_arm.R'].parent = created['spine']
    created['forearm.L'].parent = created['upper_arm.L']
    created['forearm.R'].parent = created['upper_arm.R']
    bpy.ops.object.mode_set(mode='OBJECT')
    return rig


def _bone_for_vertex(co, names):
    x, z = co.x, co.z
    if z >= 0.72:
        return 'head' if 'head' in names else 'spine'
    if z >= 0.48:
        if abs(x) >= 0.15 and 'upper_arm.L' in names:
            return 'upper_arm.L' if x > 0 else 'upper_arm.R'
        return 'spine'
    if z >= 0.28:
        if abs(x) >= 0.17 and 'forearm.L' in names:
            return 'forearm.L' if x > 0 else 'forearm.R'
        if abs(x) >= 0.13 and 'upper_arm.L' in names:
            return 'upper_arm.L' if x > 0 else 'upper_arm.R'
        return 'spine'
    if z >= 0.11:
        return 'thigh.L' if x >= 0 else 'thigh.R'
    return 'shin.L' if x >= 0 else 'shin.R'


def _assign_simple_weights(mesh_obj, rig):
    """依高度／左右分骨（含四肢），確保 glTF skin 正確。"""
    names = {b.name for b in rig.data.bones}
    vgs = mesh_obj.vertex_groups
    for bname in names:
        if bname not in vgs.keys():
            vgs.new(name=bname)
    for v in mesh_obj.data.vertices:
        bone = _bone_for_vertex(v.co, names)
        if bone not in names:
            bone = 'root'
        vgs[bone].add([v.index], 1.0, 'REPLACE')


def _bind_mesh(mesh_obj, rig):
    """僅 Armature modifier + 權重；mesh 不當 rig 子物件，避免 skin＋父子雙重變換。"""
    for mod in list(mesh_obj.modifiers):
        if mod.type == 'ARMATURE':
            mesh_obj.modifiers.remove(mod)
    mod = mesh_obj.modifiers.new(name='Armature', type='ARMATURE')
    mod.object = rig
    _assign_simple_weights(mesh_obj, rig)


def _clear_rest_pose(rig):
    """動畫完成後、匯出前將骨架清回 bind／rest pose。"""
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    prev_mode = rig.mode
    if prev_mode != 'POSE':
        bpy.ops.object.mode_set(mode='POSE')
    bpy.ops.pose.transforms_clear()
    bpy.ops.object.mode_set(mode='OBJECT')
    ad = rig.animation_data
    if ad:
        ad.action = None


def _rest_pose_sane(mesh_obj, rig):
    """無頭自查：靜態網格是否為緊湊人形（非 translate 累加爆炸）。"""
    me = mesh_obj.data
    if not me.vertices:
        return False
    xs = [v.co.x for v in me.vertices]
    ys = [v.co.y for v in me.vertices]
    zs = [v.co.z for v in me.vertices]
    x_ext = max(xs) - min(xs)
    y_ext = max(ys) - min(ys)
    z_ext = max(zs) - min(zs)
    if x_ext > 0.62 or y_ext > 0.55 or z_ext > 1.22:
        return False
    return max(zs) <= 1.22 and min(zs) >= -0.08


def _set_action_fps(frames=24):
    bpy.context.scene.render.fps = frames


def _key_rot_bone(pose_bones, name, frame, axis, angle):
    pb = pose_bones.get(name)
    if not pb:
        return
    pb.rotation_mode = 'XYZ'
    pb.rotation_euler[axis] = angle
    pb.keyframe_insert(data_path='rotation_euler', frame=frame)


def _bake_action(rig, pose, action):
    if rig.animation_data is None:
        rig.animation_data_create()
    rig.animation_data.action = action


def _nla_push(rig, action, clip_name):
    ad = rig.animation_data
    if ad is None:
        rig.animation_data_create()
        ad = rig.animation_data
    tr = ad.nla_tracks.new()
    tr.strips.new(clip_name, 0, action)


def _animate_walk_idle(rig, style):
    _set_action_fps()
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='POSE')
    pose = rig.pose.bones

    walk = bpy.data.actions.new(style + '_walk')
    _bake_action(rig, pose, walk)
    for frame, ph in enumerate([0, 6, 12, 18]):
        t = ph / 18.0 * math.pi * 2
        leg = math.sin(t) * 0.16
        arm = math.sin(t) * 0.12
        _key_rot_bone(pose, 'thigh.L', frame, 0, leg)
        _key_rot_bone(pose, 'thigh.R', frame, 0, -leg)
        _key_rot_bone(pose, 'shin.L', frame, 0, max(0, leg) * 0.5)
        _key_rot_bone(pose, 'shin.R', frame, 0, max(0, -leg) * 0.5)
        _key_rot_bone(pose, 'upper_arm.L', frame, 0, -arm)
        _key_rot_bone(pose, 'upper_arm.R', frame, 0, arm)
        rig.pose.bones['root'].location.z = abs(math.sin(t)) * 0.006
        rig.pose.bones['root'].keyframe_insert(data_path='location', frame=frame)

    idle = bpy.data.actions.new(style + '_idle')
    _bake_action(rig, pose, idle)
    for frame in (0, 12, 24):
        sway = frame / 24.0
        ang = math.sin(sway * math.pi * 2) * 0.04
        _key_rot_bone(pose, 'spine', frame, 2, ang)
        _key_rot_bone(pose, 'head', frame, 2, -ang * 0.3)
        rig.pose.bones['spine'].scale = Vector((1, 1, 1 + sway * 0.008))
        rig.pose.bones['spine'].keyframe_insert(data_path='scale', frame=frame)

    sit = bpy.data.actions.new(style + '_sit_idle')
    _bake_action(rig, pose, sit)
    for frame in (0, 24):
        _key_rot_bone(pose, 'spine', frame, 0, -0.12)
        _key_rot_bone(pose, 'thigh.L', frame, 0, 1.05)
        _key_rot_bone(pose, 'thigh.R', frame, 0, 1.05)
        _key_rot_bone(pose, 'shin.L', frame, 0, 0.65)
        _key_rot_bone(pose, 'shin.R', frame, 0, 0.65)

    bpy.ops.object.mode_set(mode='OBJECT')
    ad = rig.animation_data
    ad.action = None
    for act, label in (
        (walk, style + '_walk'),
        (idle, style + '_idle'),
        (sit, style + '_sit_idle'),
    ):
        _nla_push(rig, act, label)

    return [walk, idle, sit]


def _count_tris(obj):
    if obj.type != 'MESH':
        return 0
    return sum(len(p.vertices) - 2 for p in obj.data.polygons)


def _primitives_per_mesh(mesh_obj):
    return 1 if mesh_obj.data and len(mesh_obj.data.polygons) else 0


def build_character(style, root_collection):
    root = bpy.data.objects.new('npc_' + style, None)
    root.empty_display_size = 0.01
    root_collection.objects.link(root)
    mesh = _style_mesh(style)
    rig = _make_armature('npc_' + style)
    root_collection.objects.link(rig)
    root_collection.objects.link(mesh)
    rig.parent = root
    mesh.parent = rig
    _bind_mesh(mesh, rig)
    anims = _animate_walk_idle(rig, style)
    _clear_rest_pose(rig)
    if not _rest_pose_sane(mesh, rig):
        print(f'WARN {style} rest-pose skin check failed')
    tris = _count_tris(mesh)
    slots = _primitives_per_mesh(mesh)
    return root, tris, anims, slots


def build(out_path):
    prepare_scene()
    coll = bpy.data.collections.new('npc_women')
    bpy.context.scene.collection.children.link(coll)
    stats = {}
    slots_per = {}
    all_actions = []
    for style in STYLES:
        root, tris, actions, slots = build_character(style, coll)
        stats[style] = tris
        slots_per[style] = slots
        all_actions.extend(actions)
        if tris > 2500:
            print(f'WARN {style} tris={tris} exceeds 2500')
    prim_per = max(slots_per.values()) if slots_per else 1
    max_instances = 24
    draw_est = max_instances * prim_per

    bpy.context.scene.frame_start = 0
    bpy.context.scene.frame_end = 24
    bpy.context.scene.frame_set(0)
    for obj in coll.all_objects:
        if obj.type == 'ARMATURE':
            _clear_rest_pose(obj)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=out_path,
        export_format='GLB',
        use_selection=False,
        export_yup=True,
        export_apply=False,
        export_texcoords=True,
        export_normals=True,
        export_materials='EXPORT',
        export_cameras=False,
        export_lights=False,
        export_animations=True,
        export_nla_strips=True,
    )
    total = sum(stats.values())
    size = os.path.getsize(out_path)
    meta = {
        'styles': list(STYLES),
        'tris_per_style': stats,
        'tris_total_unique': total,
        'bytes': size,
        'draw_calls_est': draw_est,
        'slots_per_style': slots_per,
    }
    print('NPC_EXPORT ' + json.dumps(meta, ensure_ascii=False))
    print(f'EXPORTED {out_path} tris≈{total} bytes={size}')
    if size > 800 * 1024:
        print('WARN GLB exceeds 800KB budget')
    if total > 17500:
        print('WARN unique tris exceed 17500')
    return meta


if __name__ == '__main__':
    out = argv_out('server/src/main/resources/public/models/npc-women.glb')
    build(out)