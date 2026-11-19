"""海空生物 GLB 共用：低多邊手繪風頂點色、骨骼、匯出。"""
from __future__ import annotations

import math
import os
import sys

import bmesh
import bpy
from mathutils import Euler, Vector

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

from kit import hex_rgba, prepare_scene, srgb_to_lin

_VERTEX_MAT = None


def creature_vertex_material():
    global _VERTEX_MAT
    if _VERTEX_MAT is not None:
        return _VERTEX_MAT
    m = bpy.data.materials.new('creature_vertex')
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    attr = nt.nodes.new('ShaderNodeVertexColor')
    attr.layer_name = 'COLOR_0'
    nt.links.new(attr.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.82
    _VERTEX_MAT = m
    return m


def rgba_hex(h):
    return hex_rgba(h)


def append_bmesh(dst, src):
    src.verts.ensure_lookup_table()
    src.faces.ensure_lookup_table()
    off = len(dst.verts)
    for v in src.verts:
        dst.verts.new(v.co)
    dst.verts.ensure_lookup_table()
    dst.verts.index_update()
    for face in src.faces:
        dst.faces.new([dst.verts[off + v.index] for v in face.verts])


def prim_sphere(bm, co, radius, seg=7, rings=5):
    sub = bmesh.new()
    bmesh.ops.create_uvsphere(sub, u_segments=seg, v_segments=rings, radius=radius)
    bmesh.ops.translate(sub, vec=co, verts=sub.verts)
    append_bmesh(bm, sub)
    sub.free()


def prim_cone(bm, co, radius, depth, seg=5, rot=None):
    sub = bmesh.new()
    bmesh.ops.create_cone(sub, cap_ends=True, cap_tris=False, segments=seg,
                          radius1=radius, radius2=0.0, depth=depth)
    bmesh.ops.translate(sub, vec=co, verts=sub.verts)
    if rot:
        bmesh.ops.rotate(sub, verts=sub.verts, cent=(0, 0, 0),
                         matrix=Euler(rot).to_matrix().to_4x4())
    append_bmesh(bm, sub)
    sub.free()


def prim_box(bm, size, co, rot=None):
    sx, sy, sz = size
    sub = bmesh.new()
    bmesh.ops.create_cube(sub, size=1.0)
    bmesh.ops.scale(sub, vec=(sx / 2, sy / 2, sz / 2), verts=sub.verts)
    if rot:
        bmesh.ops.rotate(sub, verts=sub.verts, cent=(0, 0, 0),
                         matrix=Euler(rot).to_matrix().to_4x4())
    bmesh.ops.translate(sub, vec=co, verts=sub.verts)
    append_bmesh(bm, sub)
    sub.free()


def prim_cylinder(bm, co, radius, depth, seg=6, rot=None):
    sub = bmesh.new()
    bmesh.ops.create_cone(sub, cap_ends=True, cap_tris=False, segments=seg,
                          radius1=radius, radius2=radius, depth=depth)
    bmesh.ops.translate(sub, vec=co, verts=sub.verts)
    if rot:
        bmesh.ops.rotate(sub, verts=sub.verts, cent=(0, 0, 0),
                         matrix=Euler(rot).to_matrix().to_4x4())
    append_bmesh(bm, sub)
    sub.free()


def build_colored_mesh(name, pieces):
    """pieces: list of (hex_color, builder_fn(bm))"""
    bm = bmesh.new()
    color_lay = bm.loops.layers.color.new('COLOR_0')
    for hx, build_fn in pieces:
        sub = bmesh.new()
        build_fn(sub)
        sub.faces.ensure_lookup_table()
        col = rgba_hex(hx)
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
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bm.normal_update()
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(creature_vertex_material())
    return bpy.data.objects.new(name, me)


def add_bone(arm, edit_bones, name, head, tail, parent=None):
    eb = edit_bones.new(name)
    eb.head = Vector(head)
    eb.tail = Vector(tail)
    if parent:
        eb.parent = edit_bones[parent]
    return eb


def make_armature(name, bone_defs):
    """bone_defs: list of (name, head, tail, parent_name|None)"""
    bpy.ops.object.armature_add(location=(0, 0, 0))
    rig = bpy.context.active_object
    rig.name = name
    rig.data.name = name + '_data'
    bpy.ops.object.mode_set(mode='EDIT')
    edit = rig.data.edit_bones
    edit.remove(edit[0])
    names = {}
    for bname, head, tail, parent in bone_defs:
        eb = add_bone(rig, edit, bname, head, tail, parent)
        names[bname] = eb
    bpy.ops.object.mode_set(mode='OBJECT')
    return rig


def bind_mesh(mesh_obj, rig):
    for mod in list(mesh_obj.modifiers):
        if mod.type == 'ARMATURE':
            mesh_obj.modifiers.remove(mod)
    mod = mesh_obj.modifiers.new('Armature', type='ARMATURE')
    mod.object = rig


def assign_region_weights(mesh_obj, rig, picker):
    """picker(co: Vector) -> bone name"""
    names = {b.name for b in rig.data.bones}
    vgs = mesh_obj.vertex_groups
    for bname in names:
        if bname not in vgs:
            vgs.new(name=bname)
    for v in mesh_obj.data.vertices:
        co = v.co
        bone = picker(co, names)
        if bone not in names:
            bone = 'root'
        vgs[bone].add([v.index], 1.0, 'REPLACE')


def key_rot(pb, frame, axis, angle):
    if not pb:
        return
    pb.rotation_mode = 'XYZ'
    pb.rotation_euler[axis] = angle
    pb.keyframe_insert(data_path='rotation_euler', frame=frame)


def bake_swim_cycle(rig, bone_names, frames=(0, 8, 16)):
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    if rig.animation_data is None:
        rig.animation_data_create()
    act = bpy.data.actions.new(rig.name + '_swim')
    rig.animation_data.action = act
    bpy.ops.object.mode_set(mode='POSE')
    pose = rig.pose.bones
    for fi, frame in enumerate(frames):
        ph = (fi / max(1, len(frames) - 1)) * math.pi * 2
        for i, bname in enumerate(bone_names):
            off = math.sin(ph + i * 0.6) * 0.22
            key_rot(pose.get(bname), frame, 1, off)
        root = pose.get('root')
        if root:
            root.location.z = abs(math.sin(ph)) * 0.04
            root.keyframe_insert(data_path='location', frame=frame)
    bpy.ops.object.mode_set(mode='OBJECT')
    rig.animation_data.action = None
    tr = rig.animation_data.nla_tracks.new()
    tr.strips.new('swim', 0, act)
    return act


def bake_flap_cycle(rig, wing_bones, frames=(0, 6, 12)):
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    if rig.animation_data is None:
        rig.animation_data_create()
    act = bpy.data.actions.new(rig.name + '_flap')
    rig.animation_data.action = act
    bpy.ops.object.mode_set(mode='POSE')
    pose = rig.pose.bones
    for fi, frame in enumerate(frames):
        ph = (fi / max(1, len(frames) - 1)) * math.pi * 2
        flap = math.sin(ph) * 0.45
        for bname in wing_bones:
            pb = pose.get(bname)
            if pb:
                sign = -1 if '.L' in bname else 1
                key_rot(pb, frame, 0, sign * flap)
        body = pose.get('body')
        if body:
            key_rot(body, frame, 2, math.sin(ph) * 0.06)
    bpy.ops.object.mode_set(mode='OBJECT')
    rig.animation_data.action = None
    tr = rig.animation_data.nla_tracks.new()
    tr.strips.new('flap', 0, act)
    return act


def export_glb(out_path, export_anim=True):
    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
    bpy.ops.export_scene.gltf(
        filepath=out_path,
        export_format='GLB',
        use_selection=False,
        export_yup=True,
        export_apply=False,
        export_texcoords=False,
        export_normals=True,
        export_materials='EXPORT',
        export_cameras=False,
        export_lights=False,
        export_animations=export_anim,
        export_nla_strips=export_anim,
    )
    size = os.path.getsize(out_path)
    print(f'EXPORTED {out_path} bytes={size}')


def clear_all():
    prepare_scene()
    for act in list(bpy.data.actions):
        bpy.data.actions.remove(act)
    for arm in list(bpy.data.armatures):
        bpy.data.armatures.remove(arm)