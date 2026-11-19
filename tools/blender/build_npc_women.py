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


def _uv_sphere(bm, co, radius, seg=8, rings=6):
    bmesh.ops.create_uvsphere(bm, u_segments=seg, v_segments=rings, radius=radius)
    bmesh.ops.translate(bm, vec=co, verts=bm.verts)


def _uv_box(bm, size, co):
    sx, sy, sz = size
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=(sx / 2, sy / 2, sz / 2), verts=bm.verts)
    bmesh.ops.translate(bm, vec=co, verts=bm.verts)


def _join_meshes(name, pieces):
    """pieces: list of (material_name, bmesh_ops_fn returning bm with geo)"""
    bm = bmesh.new()
    for mat_name, build_fn in pieces:
        sub = bmesh.new()
        build_fn(sub)
        sub.faces.ensure_lookup_table()
        offset = len(bm.verts)
        for v in sub.verts:
            bm.verts.new(v.co)
        bm.verts.ensure_lookup_table()
        bm.verts.index_update()
        for face in sub.faces:
            bm.faces.new([bm.verts[offset + v.index] for v in face.verts])
        sub.free()
    bm.faces.ensure_lookup_table()
    bm.normal_update()
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    mats = []
    for mat_name, _ in pieces:
        m = bpy.data.materials.get('npc_' + mat_name) or bpy.data.materials.get(mat_name)
        if m and m not in mats:
            mats.append(m)
    me.materials.append(mats[0] if mats else _mat('npc_skin', PALETTE['skin']))
    if len(mats) > 1:
        me.materials.append(mats[1])
    return obj


def _style_mesh(style):
    """回傳 mesh object（未綁骨）。"""
    z0 = 0.0
    skin = 'skin'
    pieces = []

    def head_block(bm):
        _uv_sphere(bm, (0, 0, 0.78), 0.17, 7, 5)
        _uv_sphere(bm, (0, 0.055, 0.785), 0.028, 5, 4)  # nose hint
        _uv_sphere(bm, (-0.055, 0.12, 0.8), 0.032, 5, 4)  # L eye white
        _uv_sphere(bm, (0.055, 0.12, 0.8), 0.032, 5, 4)
        _uv_sphere(bm, (-0.055, 0.125, 0.805), 0.018, 4, 3)
        _uv_sphere(bm, (0.055, 0.125, 0.805), 0.018, 4, 3)

    pieces.append((skin, head_block))

    def torso_base(bm):
        _uv_box(bm, (0.26, 0.14, 0.26), (0, 0, 0.52))

    if style == 'summer':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.88), 0.19, 7, 5)
            for side in (-1, 1):
                _uv_sphere(bm, (side * 0.14, 0, 0.74), 0.08, 5, 4)
        pieces.append(('hair_mint', hair))

        def dress(bm):
            _uv_box(bm, (0.3, 0.12, 0.22), (0, 0, 0.46))
            _uv_box(bm, (0.34, 0.1, 0.14), (0, 0, 0.36))
        pieces.append(('yellow', dress))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.2), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.1, 0.05, 0.08), (side * 0.08, 0.05, 0.08))
        pieces.append((skin, legs))
        pieces.append((skin, torso_base))

    elif style == 'student':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.87), 0.185, 7, 5)
            _uv_box(bm, (0.2, 0.04, 0.08), (0, 0.12, 0.92))
        pieces.append(('hair_navy', hair))

        def uniform(bm):
            _uv_box(bm, (0.28, 0.14, 0.24), (0, 0, 0.52))
            _uv_box(bm, (0.08, 0.05, 0.06), (0, -0.08, 0.56))
            _uv_box(bm, (0.32, 0.11, 0.18), (0, 0, 0.38))
        pieces.append(('white', uniform))
        pieces.append(('red', lambda b: _uv_box(b, (0.1, 0.04, 0.02), (0, -0.02, 0.58))))
        pieces.append(('navy', lambda b: _uv_box(b, (0.34, 0.1, 0.16), (0, 0, 0.36))))

        def bag(bm):
            _uv_box(bm, (0.14, 0.06, 0.18), (0.14, -0.04, 0.48))
        pieces.append(('brown', bag))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.2), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.11, 0.06, 0.1), (side * 0.08, 0.04, 0.06))
        pieces.append((skin, legs))

    elif style == 'tech':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.88), 0.17, 7, 5)
            _uv_box(bm, (0.06, 0.06, 0.22), (0, -0.14, 0.92))
        pieces.append(('hair_silver', hair))

        def suit(bm):
            _uv_box(bm, (0.24, 0.13, 0.28), (0, 0, 0.5))
            for x in (-0.06, 0.06):
                _uv_box(bm, (0.02, 0.01, 0.26), (x, 0.07, 0.5))
        pieces.append(('black', suit))
        pieces.append(('white', lambda b: _uv_box(b, (0.12, 0.08, 0.12), (0, 0, 0.54))))

        def goggles(bm):
            _uv_box(bm, (0.24, 0.04, 0.05), (0, 0.14, 0.8))
        pieces.append(('cyan', goggles))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.1, 0.1, 0.22), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.12, 0.07, 0.12), (side * 0.08, 0.04, 0.08))
        pieces.append((skin, legs))

    elif style == 'idol':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.88), 0.17, 7, 5)
            for side in (-1, 1):
                _uv_box(bm, (0.08, 0.08, 0.28), (side * 0.12, -0.06, 0.8))
                _uv_box(bm, (0.06, 0.03, 0.04), (side * 0.1, 0, 0.98))
        pieces.append(('hair_blonde', hair))
        pieces.append(('pink_hot', lambda b: _uv_box(b, (0.06, 0.04, 0.04), (-0.1, 0, 0.98))))
        pieces.append(('pink_hot', lambda b: _uv_box(b, (0.06, 0.04, 0.04), (0.1, 0, 0.98))))

        def outfit(bm):
            _uv_box(bm, (0.26, 0.12, 0.2), (0, 0, 0.5))
            _uv_box(bm, (0.32, 0.1, 0.12), (0, 0, 0.36))
        pieces.append(('pink', outfit))
        pieces.append(('white', lambda b: _uv_box(b, (0.14, 0.06, 0.08), (0, 0, 0.58))))

        def mic(bm):
            _uv_box(bm, (0.04, 0.04, 0.14), (0.22, 0, 0.55))
            _uv_sphere(bm, (0.22, 0, 0.64), 0.045, 5, 4)
        pieces.append(('grey', mic))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.2), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.11, 0.07, 0.1), (side * 0.08, 0.04, 0.06))
        pieces.append((skin, legs))

    elif style == 'swimsuit':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.88), 0.18, 7, 5)
            for side in (-1, 1):
                _uv_sphere(bm, (side * 0.12, 0, 0.72), 0.07, 5, 4)
        pieces.append(('hair_teal', hair))

        def swim(bm):
            _uv_box(bm, (0.24, 0.1, 0.26), (0, 0, 0.48))
        pieces.append(('blue_swim', swim))

        def sarong(bm):
            _uv_box(bm, (0.28, 0.04, 0.14), (0, 0.06, 0.34))
        pieces.append(('white', sarong))

        def hat(bm):
            _uv_box(bm, (0.22, 0.04, 0.22), (0, 0, 1.0))
        pieces.append(('straw', hat))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.22), (side * 0.08, 0, 0.22))
        pieces.append((skin, legs))

    elif style == 'sport':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.88), 0.17, 7, 5)
            _uv_box(bm, (0.07, 0.07, 0.2), (0, -0.12, 0.9))
        pieces.append(('hair_orange', hair))
        pieces.append(('cyan', lambda b: _uv_box(b, (0.06, 0.02, 0.03), (0, -0.1, 0.98))))

        def jacket(bm):
            _uv_box(bm, (0.28, 0.12, 0.24), (0, 0, 0.52))
            _uv_box(bm, (0.14, 0.05, 0.1), (-0.2, 0, 0.52))
            _uv_box(bm, (0.14, 0.05, 0.1), (0.2, 0, 0.52))
        pieces.append(('navy', jacket))
        pieces.append(('white', lambda b: _uv_box(b, (0.26, 0.08, 0.08), (0, 0.07, 0.54))))

        def shorts(bm):
            _uv_box(bm, (0.3, 0.1, 0.14), (0, 0, 0.36))
        pieces.append(('navy', shorts))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.1, 0.1, 0.22), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.12, 0.07, 0.1), (side * 0.08, 0.04, 0.06))
        pieces.append((skin, legs))
        pieces.append(('red', lambda b: _uv_box(b, (0.04, 0.03, 0.06), (-0.08, 0.03, 0.05))))

    elif style == 'fairy':
        def hair(bm):
            _uv_sphere(bm, (0, 0, 0.9), 0.19, 7, 5)
            for side in (-1, 1):
                _uv_sphere(bm, (side * 0.14, 0, 0.68), 0.08, 5, 4)
            _uv_box(bm, (0.04, 0.04, 0.08), (0, 0.12, 0.96))
        pieces.append(('hair_purple', hair))
        pieces.append(('gold', lambda b: _uv_box(b, (0.06, 0.01, 0.04), (0, 0.14, 0.98))))

        def gown(bm):
            _uv_box(bm, (0.22, 0.08, 0.12), (0, 0, 0.58))
            _uv_box(bm, (0.18, 0.06, 0.1), (0, 0, 0.52))
            for side in (-1, 1):
                _uv_box(bm, (0.12, 0.04, 0.22), (side * 0.22, 0.02, 0.46))
            _uv_box(bm, (0.28, 0.05, 0.18), (0, 0.04, 0.34))
        pieces.append(('purple_gown', gown))
        pieces.append(('purple_light', lambda b: _uv_box(b, (0.16, 0.05, 0.08), (0, 0, 0.54))))
        pieces.append(('gold', lambda b: _uv_sphere(b, (0, -0.04, 0.54), 0.03, 4, 3)))

        def legs(bm):
            for side in (-1, 1):
                _uv_box(bm, (0.09, 0.1, 0.24), (side * 0.08, 0, 0.22))
                _uv_box(bm, (0.1, 0.05, 0.08), (side * 0.08, 0.05, 0.08))
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


def _parent_auto(mesh_obj, rig):
    bpy.ops.object.select_all(action='DESELECT')
    mesh_obj.select_set(True)
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.parent_set(type='ARMATURE_AUTO')


def _set_action_fps(frames=24):
    bpy.context.scene.render.fps = frames


def _key_rot_bone(pose_bones, name, frame, axis, angle):
    pb = pose_bones.get(name)
    if not pb:
        return
    pb.rotation_mode = 'XYZ'
    pb.rotation_euler[axis] = angle
    pb.keyframe_insert(data_path='rotation_euler', frame=frame)


def _animate_walk_idle(rig, style):
    _set_action_fps()
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='POSE')
    pose = rig.pose.bones
    walk = bpy.data.actions.new(style + '_walk')
    rig.animation_data_create()
    rig.animation_data.action = walk
    for frame, ph in enumerate([0, 6, 12, 18]):
        t = ph / 18.0 * math.pi * 2
        leg = math.sin(t) * 0.42
        arm = math.sin(t) * 0.32
        _key_rot_bone(pose, 'thigh.L', frame, 0, leg)
        _key_rot_bone(pose, 'thigh.R', frame, 0, -leg)
        _key_rot_bone(pose, 'shin.L', frame, 0, max(0, leg) * 0.5)
        _key_rot_bone(pose, 'shin.R', frame, 0, max(0, -leg) * 0.5)
        _key_rot_bone(pose, 'upper_arm.L', frame, 0, -arm)
        _key_rot_bone(pose, 'upper_arm.R', frame, 0, arm)
        rig.pose.bones['root'].location.z = abs(math.sin(t)) * 0.02
        rig.pose.bones['root'].keyframe_insert(data_path='location', frame=frame)

    idle = bpy.data.actions.new(style + '_idle')
    rig.animation_data.action = idle
    for frame, sway in enumerate([0, 12, 24]):
        ang = math.sin(frame / 24 * math.pi * 2) * 0.04
        _key_rot_bone(pose, 'spine', frame, 2, ang)
        _key_rot_bone(pose, 'head', frame, 2, -ang * 0.3)
        rig.pose.bones['spine'].keyframe_insert(data_path='scale', frame=frame)
        rig.pose.bones['spine'].scale = Vector((1, 1, 1 + sway * 0.008))
    bpy.ops.object.mode_set(mode='OBJECT')

    return [walk, idle]


def _count_tris(obj):
    if obj.type != 'MESH':
        return 0
    return sum(len(p.vertices) - 2 for p in obj.data.polygons)


def build_character(style, root_collection):
    mesh = _style_mesh(style)
    rig = _make_armature('npc_' + style)
    root_collection.objects.link(mesh)
    root_collection.objects.link(rig)
    _parent_auto(mesh, rig)
    root = bpy.data.objects.new('npc_' + style, None)
    root.empty_display_size = 0.2
    root_collection.objects.link(root)
    rig.parent = root
    mesh.parent = root
    anims = _animate_walk_idle(rig, style)
    tris = _count_tris(mesh)
    return root, tris, anims


def build(out_path):
    prepare_scene()
    _ensure_mats()
    coll = bpy.data.collections.new('npc_women')
    bpy.context.scene.collection.children.link(coll)
    stats = {}
    all_actions = []
    for style in STYLES:
        root, tris, actions = build_character(style, coll)
        stats[style] = tris
        all_actions.extend(actions)
        if tris > 2500:
            print(f'WARN {style} tris={tris} exceeds 2500')

    bpy.context.scene.frame_start = 0
    bpy.context.scene.frame_end = 24
    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=out_path,
        export_format='GLB',
        use_selection=False,
        export_yup=True,
        export_apply=True,
        export_texcoords=True,
        export_normals=True,
        export_materials='EXPORT',
        export_cameras=False,
        export_lights=False,
        export_animations=True,
        export_nla_strips=False,
    )
    total = sum(stats.values())
    size = os.path.getsize(out_path)
    meta = {
        'styles': list(STYLES),
        'tris_per_style': stats,
        'tris_total_unique': total,
        'bytes': size,
        'draw_calls_est': len(STYLES) * 2,
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