"""海空生物 GLB 共用：低多邊手繪 UV 貼圖、骨骼、匯出。"""
from __future__ import annotations

import math
import os
import random
import sys

import bmesh
import bpy
from mathutils import Euler, Vector

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

from kit import hex_rgba, prepare_scene, srgb_to_lin

# 模型空間倍率（匯出前套用到 rig）
CREATURE_MESH_SCALE = 3.0
TEXTURE_SIZE = 384


def _hex_lin(h):
    r, g, b, a = hex_rgba(h)
    return Vector((r, g, b, a))


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


def _barycentric(px, py, x0, y0, x1, y1, x2, y2):
    d = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(d) < 1e-12:
        return None
    w0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / d
    w1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / d
    w2 = 1.0 - w0 - w1
    if w0 < -0.02 or w1 < -0.02 or w2 < -0.02:
        return None
    return w0, w1, w2


def _paint_triangle(pixels, w, h, uv0, uv1, uv2, col):
    """手繪風：三角區塊著色並加筆觸噪點。"""
    xs = [
        max(0, min(w - 1, int(uv0[0] * w))),
        max(0, min(w - 1, int(uv1[0] * w))),
        max(0, min(w - 1, int(uv2[0] * w))),
    ]
    ys = [
        max(0, min(h - 1, int((1.0 - uv0[1]) * h))),
        max(0, min(h - 1, int((1.0 - uv1[1]) * h))),
        max(0, min(h - 1, int((1.0 - uv2[1]) * h))),
    ]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    for y in range(min_y, max_y + 1):
        for x in range(min_x, max_x + 1):
            px = (x + 0.5) / w
            py = 1.0 - (y + 0.5) / h
            bc = _barycentric(px, py, uv0[0], uv0[1], uv1[0], uv1[1], uv2[0], uv2[1])
            if bc is None:
                continue
            j = (y * w + x) * 4
            n = 0.9 + random.random() * 0.18
            pixels[j] = min(1.0, col[0] * n)
            pixels[j + 1] = min(1.0, col[1] * n)
            pixels[j + 2] = min(1.0, col[2] * n)
            pixels[j + 3] = 1.0


def _rasterize_hand_painted(bm, color_lay, uv_lay, size):
    w = h = size
    pixels = [0.82, 0.88, 0.94, 1.0] * (w * h)
    # base paper tone
    for i in range(0, len(pixels), 4):
        n = 0.96 + random.random() * 0.08
        pixels[i] *= n
        pixels[i + 1] *= n
        pixels[i + 2] *= n
    for face in bm.faces:
        loops = list(face.loops)
        if len(loops) < 3:
            continue
        col = loops[0][color_lay]
        if len(loops) == 3:
            uvs = [tuple(loop[uv_lay].uv) for loop in loops]
            _paint_triangle(pixels, w, h, uvs[0], uvs[1], uvs[2], col)
        else:
            uv0 = tuple(loops[0][uv_lay].uv)
            for i in range(1, len(loops) - 1):
                _paint_triangle(
                    pixels, w, h, uv0,
                    tuple(loops[i][uv_lay].uv),
                    tuple(loops[i + 1][uv_lay].uv),
                    col,
                )
    img = bpy.data.images.new('creature_tex', w, h, alpha=True)
    img.pixels[:] = pixels
    img.pack()
    return img


def creature_image_material(tex_img, name='creature_tex_mat'):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nodes = nt.nodes
    links = nt.links
    for node in list(nodes):
        nodes.remove(node)
    out = nodes.new('ShaderNodeOutputMaterial')
    bsdf = nodes.new('ShaderNodeBsdfPrincipled')
    tex = nodes.new('ShaderNodeTexImage')
    tex.image = tex_img
    tex.interpolation = 'Closest'
    links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = 0.82
    bsdf.inputs['Specular IOR Level'].default_value = 0.15
    links.new(bsdf.outputs['BSDF'], out.inputs['Surface'])
    return m


def build_colored_mesh(name, pieces):
    """pieces: list of (hex_color, builder_fn(bm)) → UV 手繪貼圖 mesh。"""
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
    uv_lay = bm.loops.layers.uv.new('UVMap')
    xs = [v.co.x for v in bm.verts]
    zs = [v.co.z for v in bm.verts]
    min_x, max_x = min(xs), max(xs)
    min_z, max_z = min(zs), max(zs)
    dx = max(max_x - min_x, 1e-5)
    dz = max(max_z - min_z, 1e-5)
    for face in bm.faces:
        for loop in face.loops:
            v = loop.vert
            loop[uv_lay].uv = ((v.co.x - min_x) / dx, (v.co.z - min_z) / dz)
    random.seed(sum(pieces[i][0] for i in range(len(pieces))) % 9973)
    tex_img = _rasterize_hand_painted(bm, color_lay, uv_lay, TEXTURE_SIZE)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    mat = creature_image_material(tex_img, name + '_mat')
    me.materials.append(mat)
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
    existing = {obj.name for obj in bpy.data.objects}
    bpy.ops.object.armature_add(location=(0, 0, 0))
    for obj in list(bpy.data.objects):
        if obj.name not in existing and obj.type != 'ARMATURE':
            bpy.data.objects.remove(obj, do_unlink=True)
    rig = bpy.context.active_object
    rig.name = name
    rig.data.name = name + '_data'
    bpy.ops.object.mode_set(mode='EDIT')
    edit = rig.data.edit_bones
    edit.remove(edit[0])
    for bname, head, tail, parent in bone_defs:
        add_bone(rig, edit, bname, head, tail, parent)
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


def apply_creature_scale(rig, scale=CREATURE_MESH_SCALE):
    rig.scale = (scale, scale, scale)
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)


def world_bounds_for(obj):
    xs, ys, zs = [], [], []
    stack = [obj]
    while stack:
        o = stack.pop()
        if o.type == 'MESH':
            for v in o.data.vertices:
                co = o.matrix_world @ v.co
                xs.append(co.x)
                ys.append(co.y)
                zs.append(co.z)
        stack.extend(o.children)
    if not xs:
        return None
    return (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs))


def print_world_bounds(root, label):
    b = world_bounds_for(root)
    if not b:
        print(f'BOUNDS {label}: (empty)')
        return
    sx = b[1] - b[0]
    sy = b[3] - b[2]
    sz = b[5] - b[4]
    print(
        f'BOUNDS {label}: x[{b[0]:.2f},{b[1]:.2f}] y[{b[2]:.2f},{b[3]:.2f}] '
        f'z[{b[4]:.2f},{b[5]:.2f}] size {sx:.2f}×{sy:.2f}×{sz:.2f}'
    )
    if 1.88 <= sx <= 2.08 and 1.88 <= sy <= 2.08 and 1.88 <= sz <= 2.08:
        print(f'  WARN {label}: suspicious ~2m helper block in export')


def _select_hierarchy(obj):
    obj.select_set(True)
    for child in obj.children:
        _select_hierarchy(child)


def export_glb(out_path, root_rig, export_anim=True):
    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
    apply_creature_scale(root_rig)
    print_world_bounds(root_rig, os.path.basename(out_path))
    bpy.ops.object.select_all(action='DESELECT')
    _select_hierarchy(root_rig)
    bpy.context.view_layer.objects.active = root_rig
    bpy.ops.export_scene.gltf(
        filepath=out_path,
        export_format='GLB',
        use_selection=True,
        export_yup=True,
        export_apply=True,
        export_texcoords=True,
        export_normals=True,
        export_materials='EXPORT',
        export_cameras=False,
        export_lights=False,
        export_animations=export_anim,
        export_nla_strips=export_anim,
        export_image_format='AUTO',
    )
    size = os.path.getsize(out_path)
    print(f'EXPORTED {out_path} bytes={size}')


def purge_orphan_blender_data():
    for mesh in list(bpy.data.meshes):
        if mesh.users == 0:
            bpy.data.meshes.remove(mesh)
    for mat in list(bpy.data.materials):
        if mat.users == 0:
            bpy.data.materials.remove(mat)
    for img in list(bpy.data.images):
        if img.users == 0:
            bpy.data.images.remove(img)


def clear_all():
    prepare_scene()
    purge_orphan_blender_data()
    for act in list(bpy.data.actions):
        bpy.data.actions.remove(act)
    for arm in list(bpy.data.armatures):
        bpy.data.armatures.remove(arm)