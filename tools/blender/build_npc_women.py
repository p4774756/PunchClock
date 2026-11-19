"""七種 Q 版女性行人 NPC v2（貼圖臉／分縷髮／服裝輪廓）→ npc-women.glb。

無頭重建：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_npc_women.py -- \\
    server/src/main/resources/public/models/npc-women.glb
"""
from __future__ import annotations

import json
import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import bpy
from mathutils import Vector

import kit
from kit import prepare_scene, argv_out

from npc_mesh_kit import (
    StyleMesh,
    make_face_texture,
    make_hair_texture,
    make_outfit_texture,
    tex_material,
    flat_material,
)

STYLES = (
    'summer', 'student', 'tech', 'idol', 'swimsuit', 'sport', 'fairy',
)

HAIR_HEX = {
    'summer': 0x7ecfc4,
    'student': 0x1f3355,
    'tech': 0xdde4ea,
    'idol': 0xf5d878,
    'swimsuit': 0x4db8a8,
    'sport': 0xf08030,
    'fairy': 0xc8a0e8,
}

M_SHARED = {}


def _prepare_textures():
    face = make_face_texture()
    M_SHARED['face'] = tex_material('npc_mat_face', face)
    M_SHARED['skin'] = flat_material('npc_mat_skin', 0xf3d0b0, rough=0.62)
    M_SHARED['skin_shadow'] = flat_material('npc_mat_skin_shadow', 0xe8b898, rough=0.65)
    M_SHARED['white'] = flat_material('npc_mat_white', 0xfffaf6)
    M_SHARED['black'] = flat_material('npc_mat_black', 0x181820, rough=0.35)
    M_SHARED['red'] = flat_material('npc_mat_red', 0xd83840)
    M_SHARED['navy'] = flat_material('npc_mat_navy', 0x1f3355)
    M_SHARED['brown'] = flat_material('npc_mat_brown', 0x6b4634)
    M_SHARED['gold'] = flat_material('npc_mat_gold', 0xd4a848, rough=0.4)
    M_SHARED['grey'] = flat_material('npc_mat_grey', 0x8890a0)
    M_SHARED['pink_hot'] = flat_material('npc_mat_pink_hot', 0xff78b0)
    M_SHARED['cyan_glow'] = flat_material('npc_mat_cyan', 0x30c8e8, rough=0.3, emit=0x30c8e8)
    for style in STYLES:
        ht = make_hair_texture(style, HAIR_HEX[style])
        M_SHARED[f'hair_{style}'] = tex_material(f'npc_hair_{style}', ht, rough=0.55)


def _outfit_mat(style, colors, pattern='solid', alpha=False):
    img = make_outfit_texture(style, colors, pattern)
    return tex_material(
        f'npc_outfit_{style}',
        img,
        rough=0.64,
        alpha_clip=alpha,
    )


def _hair_strands(m, hair, pairs):
    for sx, x, y, z, w, h, d in pairs:
        m.taper_box(hair, w, h, d, sx * x, y, z, ry=sx * 0.22, taper=0.82)


def _limbs(m, skin):
    for side in (-1, 1):
        m.box(skin, 0.09, 0.11, 0.22, side * 0.08, 0, 0.22)
        m.box(skin, 0.085, 0.1, 0.24, side * 0.205, 0.01, 0.48)
    for side in (-1, 1):
        m.box(skin, 0.072, 0.078, 0.2, side * 0.24, -0.01, 0.34)


def _head_base(m, hair, z=0.79):
    m.sphere(hair, 0.175, 0, 0.02, z, sx=1.02, sy=0.92, sz=0.95, seg=14, rings=10)
    m.sphere(M_SHARED['skin'], 0.155, 0, 0.04, z - 0.03, sx=1.0, sy=0.85, sz=0.9, seg=12, rings=8)
    m.face_plate(M_SHARED['face'], 0.22, 0.26, 0, 0.198, z - 0.02, seg_u=9, seg_v=10)


def _style_summer():
    hair = M_SHARED['hair_summer']
    outfit = _outfit_mat('summer', (0xfff8e8, 0xf6e878, 0xffffff), 'floral')
    m = StyleMesh('summer')
    _head_base(m, hair, 0.805)
    _hair_strands(m, hair, [
        (1, 0.155, -0.02, 0.72, 0.08, 0.14, 0.12),
        (1, 0.17, -0.03, 0.58, 0.06, 0.22, 0.14),
        (-1, 0.155, -0.02, 0.72, 0.08, 0.14, 0.12),
        (-1, 0.17, -0.03, 0.58, 0.06, 0.22, 0.14),
        (1, 0.1, 0.04, 0.82, 0.05, 0.06, 0.08),
        (-1, 0.1, 0.04, 0.82, 0.05, 0.06, 0.08),
    ])
    m.box(hair, 0.2, 0.05, 0.1, 0, 0.06, 0.88)
    m.taper_box(outfit, 0.34, 0.12, 0.2, 0, 0, 0.46, taper=1.15)
    m.taper_box(outfit, 0.4, 0.1, 0.15, 0, 0, 0.36, taper=1.2)
    for side in (-1, 1):
        m.box(outfit, 0.08, 0.08, 0.1, side * 0.2, 0.02, 0.5)
        m.box(outfit, 0.1, 0.06, 0.1, side * 0.08, 0.04, 0.06)
    _limbs(m, M_SHARED['skin'])
    return m


def _style_student():
    hair = M_SHARED['hair_student']
    uniform = _outfit_mat('student', (0x1f3355, 0xfffaf6, 0x6b4634), 'pleats')
    m = StyleMesh('student')
    _head_base(m, hair, 0.8)
    m.box(hair, 0.22, 0.05, 0.1, 0, 0.14, 0.86)
    for side in (-1, 1):
        m.box(hair, 0.07, 0.05, 0.07, side * 0.11, 0.1, 0.82)
    m.box(uniform, 0.3, 0.14, 0.22, 0, 0, 0.54)
    for side in (-1, 1):
        m.box(uniform, 0.09, 0.12, 0.08, side * 0.19, 0.02, 0.56)
        m.box(uniform, 0.072, 0.1, 0.09, side * 0.21, -0.01, 0.48)
    m.box(uniform, 0.16, 0.05, 0.03, 0, -0.01, 0.58)
    m.taper_box(uniform, 0.38, 0.12, 0.2, 0, 0, 0.35, taper=1.18)
    m.box(uniform, 0.15, 0.07, 0.2, 0.16, -0.04, 0.44)
    m.box(uniform, 0.04, 0.02, 0.05, 0.1, -0.02, 0.52)
    _limbs(m, M_SHARED['skin'])
    for side in (-1, 1):
        m.box(uniform, 0.11, 0.06, 0.13, side * 0.08, 0.04, 0.05)
    return m


def _style_tech():
    hair = M_SHARED['hair_tech']
    suit = _outfit_mat('tech', (0x181820, 0x2a3040), 'tech')
    m = StyleMesh('tech')
    _head_base(m, hair, 0.795)
    m.box(hair, 0.08, 0.08, 0.28, 0, -0.15, 0.94)
    m.box(hair, 0.06, 0.05, 0.1, 0, -0.09, 1.03)
    m.box(suit, 0.26, 0.14, 0.28, 0, 0, 0.5)
    m.taper_box(suit, 0.32, 0.12, 0.16, 0, 0, 0.35)
    for side in (-1, 1):
        m.box(suit, 0.1, 0.08, 0.2, side * 0.2, 0.02, 0.47)
        m.box(suit, 0.045, 0.02, 0.025, side * 0.11, 0.2, 0.83)
    m.box(suit, 0.24, 0.04, 0.05, 0, 0.2, 0.83)
    _limbs(m, M_SHARED['skin'])
    for side in (-1, 1):
        m.box(suit, 0.12, 0.09, 0.14, side * 0.08, 0.04, 0.06)
    return m


def _style_idol():
    hair = M_SHARED['hair_idol']
    outfit = _outfit_mat('idol', (0xff78b0, 0xfff0f8), 'frills')
    m = StyleMesh('idol')
    _head_base(m, hair, 0.8)
    for side in (-1, 1):
        m.box(hair, 0.08, 0.09, 0.32, side * 0.14, -0.06, 0.76)
        m.sphere(hair, 0.07, side * 0.13, 0.02, 0.99, seg=8, rings=6)
        m.box(outfit, 0.06, 0.03, 0.05, side * 0.11, -0.02, 1.01)
    m.box(outfit, 0.28, 0.13, 0.2, 0, 0, 0.51)
    m.taper_box(outfit, 0.36, 0.11, 0.14, 0, 0, 0.36, taper=1.22)
    for side in (-1, 1):
        m.box(outfit, 0.085, 0.08, 0.14, side * 0.19, 0.04, 0.46)
        m.box(outfit, 0.11, 0.07, 0.11, side * 0.08, 0.04, 0.06)
    m.box(outfit, 0.035, 0.035, 0.13, 0.26, 0.02, 0.55)
    m.sphere(outfit, 0.04, 0.26, 0.02, 0.62, seg=7, rings=5)
    _limbs(m, M_SHARED['skin'])
    return m


def _style_swimsuit():
    hair = M_SHARED['hair_swimsuit']
    swim = _outfit_mat('swimsuit', (0x2878c0, 0x1a5898))
    m = StyleMesh('swimsuit')
    _head_base(m, hair, 0.8)
    for side in (-1, 1):
        m.taper_box(hair, 0.07, 0.24, 0.12, side * 0.15, -0.02, 0.62, taper=0.9)
    m.box(swim, 0.26, 0.11, 0.27, 0, 0, 0.47)
    m.box(swim, 0.14, 0.07, 0.1, 0, 0.04, 0.53)
    m.taper_box(swim, 0.3, 0.05, 0.16, 0, 0.07, 0.33, taper=1.1)
    m.box(swim, 0.24, 0.05, 0.24, 0, 0, 1.02)
    m.box(swim, 0.26, 0.02, 0.08, 0, 0, 1.06)
    _limbs(m, M_SHARED['skin'])
    return m


def _style_sport():
    hair = M_SHARED['hair_sport']
    jacket = _outfit_mat('sport', (0x1f3355, 0xffffff))
    m = StyleMesh('sport')
    _head_base(m, hair, 0.795)
    m.box(hair, 0.09, 0.09, 0.26, 0, -0.14, 0.92)
    m.box(jacket, 0.07, 0.025, 0.04, 0, -0.1, 0.99)
    m.box(jacket, 0.32, 0.13, 0.24, 0, 0, 0.52)
    for side in (-1, 1):
        m.box(jacket, 0.12, 0.06, 0.17, side * 0.22, 0.02, 0.46)
    m.taper_box(jacket, 0.34, 0.1, 0.14, 0, 0, 0.355, taper=1.1)
    _limbs(m, M_SHARED['skin'])
    for side in (-1, 1):
        m.box(jacket, 0.14, 0.08, 0.12, side * 0.08, 0.04, 0.06)
    return m


def _style_fairy():
    hair = M_SHARED['hair_fairy']
    gown = _outfit_mat('fairy', (0xc0a0e0, 0xe8d8f8), 'gauze', alpha=True)
    m = StyleMesh('fairy')
    _head_base(m, hair, 0.815)
    for side in (-1, 1):
        m.taper_box(hair, 0.08, 0.28, 0.1, side * 0.17, -0.02, 0.58, taper=0.88)
    m.box(gown, 0.06, 0.012, 0.06, 0, 0.15, 0.99)
    for side in (-1, 1):
        m.sphere(M_SHARED['skin'], 0.065, side * 0.16, 0, 0.57, seg=8, rings=6)
    m.box(M_SHARED['skin'], 0.12, 0.04, 0.09, 0, 0, 0.58)
    m.box(gown, 0.16, 0.05, 0.12, 0, 0.01, 0.51)
    for side in (-1, 1):
        m.taper_box(gown, 0.11, 0.04, 0.26, side * 0.22, 0.02, 0.42, taper=0.8)
    m.taper_box(gown, 0.08, 0.04, 0.18, 0, 0.02, 0.52, taper=0.7)
    m.box(gown, 0.28, 0.04, 0.12, 0, 0.05, 0.33)
    m.box(gown, 0.1, 0.06, 0.2, 0, 0.02, 0.32)
    for side in (-1, 1):
        m.box(gown, 0.1, 0.05, 0.09, side * 0.08, 0.05, 0.08)
    _limbs(m, M_SHARED['skin'])
    return m


_BUILDERS = {
    'summer': _style_summer,
    'student': _style_student,
    'tech': _style_tech,
    'idol': _style_idol,
    'swimsuit': _style_swimsuit,
    'sport': _style_sport,
    'fairy': _style_fairy,
}


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
        if abs(x) >= 0.15:
            return 'upper_arm.L' if x > 0 else 'upper_arm.R'
        return 'spine'
    if z >= 0.28:
        if abs(x) >= 0.17:
            return 'forearm.L' if x > 0 else 'forearm.R'
        if abs(x) >= 0.13:
            return 'upper_arm.L' if x > 0 else 'upper_arm.R'
        return 'spine'
    if z >= 0.11:
        return 'thigh.L' if x >= 0 else 'thigh.R'
    return 'shin.L' if x >= 0 else 'shin.R'


def _assign_simple_weights(mesh_obj, rig):
    names = {b.name for b in rig.data.bones}
    vgs = mesh_obj.vertex_groups
    for bname in names:
        if bname not in vgs.keys():
            vgs.new(name=bname)
    for v in mesh_obj.data.vertices:
        bone = _bone_for_vertex(v.co, names)
        vgs[bone].add([v.index], 1.0, 'REPLACE')


def _bind_mesh(mesh_obj, rig):
    mod = mesh_obj.modifiers.new(name='Armature', type='ARMATURE')
    mod.object = rig
    _assign_simple_weights(mesh_obj, rig)


def _clear_rest_pose(rig):
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='POSE')
    bpy.ops.pose.transforms_clear()
    bpy.ops.object.mode_set(mode='OBJECT')
    ad = rig.animation_data
    if ad:
        ad.action = None


def _set_action_fps(frames=24):
    bpy.context.scene.render.fps = frames


def _key_rot_bone(pose_bones, name, frame, axis, angle):
    pb = pose_bones.get(name)
    if not pb:
        return
    pb.rotation_mode = 'XYZ'
    pb.rotation_euler[axis] = angle
    pb.keyframe_insert(data_path='rotation_euler', frame=frame)


def _bake_action(rig, action):
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
    _bake_action(rig, walk)
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
        pose['root'].location.z = abs(math.sin(t)) * 0.006
        pose['root'].keyframe_insert(data_path='location', frame=frame)

    idle = bpy.data.actions.new(style + '_idle')
    _bake_action(rig, idle)
    for frame in (0, 12, 24):
        sway = frame / 24.0
        ang = math.sin(sway * math.pi * 2) * 0.04
        _key_rot_bone(pose, 'spine', frame, 2, ang)
        _key_rot_bone(pose, 'head', frame, 2, -ang * 0.3)
        pose['spine'].scale = Vector((1, 1, 1 + sway * 0.008))
        pose['spine'].keyframe_insert(data_path='scale', frame=frame)

    sit = bpy.data.actions.new(style + '_sit_idle')
    _bake_action(rig, sit)
    for frame in (0, 24):
        _key_rot_bone(pose, 'spine', frame, 0, -0.12)
        _key_rot_bone(pose, 'thigh.L', frame, 0, 1.05)
        _key_rot_bone(pose, 'thigh.R', frame, 0, 1.05)
        _key_rot_bone(pose, 'shin.L', frame, 0, 0.65)
        _key_rot_bone(pose, 'shin.R', frame, 0, 0.65)

    bpy.ops.object.mode_set(mode='OBJECT')
    rig.animation_data.action = None
    for act, label in (
        (walk, style + '_walk'),
        (idle, style + '_idle'),
        (sit, style + '_sit_idle'),
    ):
        _nla_push(rig, act, label)
    return [walk, idle, sit]


def _count_tris(obj):
    return sum(len(p.vertices) - 2 for p in obj.data.polygons)


def _prim_count(mesh_obj):
    slots = set()
    for p in mesh_obj.data.polygons:
        slots.add(p.material_index)
    return max(1, len(slots))


def build_character(style, root_collection):
    root = bpy.data.objects.new('npc_' + style, None)
    root.empty_display_size = 0.01
    root_collection.objects.link(root)
    sm = _BUILDERS[style]()
    mesh = sm.finish()
    rig = _make_armature('npc_' + style)
    root_collection.objects.link(rig)
    root_collection.objects.link(mesh)
    rig.parent = root
    mesh.parent = rig
    _bind_mesh(mesh, rig)
    _animate_walk_idle(rig, style)
    _clear_rest_pose(rig)
    tris = _count_tris(mesh)
    prims = _prim_count(mesh)
    if tris > 8000:
        print(f'WARN {style} tris={tris} exceeds 8000')
    if prims > 6:
        print(f'WARN {style} primitives={prims} exceeds 6')
    return root, tris, prims


def build(out_path):
    prepare_scene()
    _prepare_textures()
    coll = bpy.data.collections.new('npc_women')
    bpy.context.scene.collection.children.link(coll)
    stats = {}
    prims_per = {}
    for style in STYLES:
        root, tris, prims = build_character(style, coll)
        stats[style] = tris
        prims_per[style] = prims
    prim_max = max(prims_per.values())
    draw_est = 24 * prim_max
    for obj in coll.all_objects:
        if obj.type == 'ARMATURE':
            _clear_rest_pose(obj)
    bpy.context.scene.frame_start = 0
    bpy.context.scene.frame_end = 24
    bpy.context.scene.frame_set(0)
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
        export_image_format='AUTO',
    )
    total = sum(stats.values())
    size = os.path.getsize(out_path)
    tex_count = len([i for i in bpy.data.images if i.name.startswith('npc_')])
    meta = {
        'styles': list(STYLES),
        'tris_per_style': stats,
        'tris_total_unique': total,
        'bytes': size,
        'draw_calls_est': draw_est,
        'primitives_per_style': prims_per,
        'texture_count': tex_count,
        'material_count': len(bpy.data.materials),
    }
    print('NPC_EXPORT ' + json.dumps(meta, ensure_ascii=False))
    print(f'EXPORTED {out_path} tris≈{total} bytes={size}')
    if size > int(2.5 * 1024 * 1024):
        print('WARN GLB exceeds 2.5MB budget')
    if total > 50000:
        print('WARN unique tris exceed 50000')
    return meta


if __name__ == '__main__':
    out = argv_out('server/src/main/resources/public/models/npc-women.glb')
    build(out)