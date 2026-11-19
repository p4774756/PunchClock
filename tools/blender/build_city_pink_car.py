"""城市島粉紅復古小車（Fiat 500）；底模 Montero CC-BY，見 assets/pink-fiat/SOURCE.txt。

  nice -n 10 ~/.local/bin/blender -b --factory-startup -P tools/blender/build_city_pink_car.py -- \\
    server/src/main/resources/public/models/city-pink-car.glb
"""
from __future__ import annotations

import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import bpy
from mathutils import Vector

from kit import argv_out, clear_scene, hex_rgba, material, prepare_scene, set_palette_overrides

SOURCE_GLB = os.path.join(_HERE, 'assets', 'pink-fiat', 'montero-1956-fiat-500.glb')
TARGET_LENGTH = 0.96
FOOTPRINT_W = 1.18
FOOTPRINT_D = 0.88
ROOF_OBJECTS = frozenset({'Object_4'})


def _mat_chrome():
    m = material('city_trim_white')
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Metallic'].default_value = 0.88
    bsdf.inputs['Roughness'].default_value = 0.24
    bsdf.inputs['Specular IOR Level'].default_value = 0.65
    return m


def _mat_glass():
    m = material('city_glass_blue')
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Roughness'].default_value = 0.12
    bsdf.inputs['Metallic'].default_value = 0.0
    bsdf.inputs['Alpha'].default_value = 0.82
    m.blend_method = 'BLEND'
    return m


def _mesh_bounds():
    mn = Vector((1e9, 1e9, 1e9))
    mx = Vector((-1e9, -1e9, -1e9))
    for ob in bpy.data.objects:
        if ob.type != 'MESH':
            continue
        for corner in ob.bound_box:
            co = ob.matrix_world @ Vector(corner)
            mn.x = min(mn.x, co.x)
            mx.x = max(mx.x, co.x)
            mn.y = min(mn.y, co.y)
            mx.y = max(mx.y, co.y)
            mn.z = min(mn.z, co.z)
            mx.z = max(mx.z, co.z)
    return mn, mx


def _assign_materials():
    chrome = _mat_chrome()
    body = material('city_pink_car')
    roof = material('city_pink_roof')
    glass = _mat_glass()
    tire = material('city_traffic_dark')
    lamp_front = material('city_trim_white')
    lamp_rear = material('city_traffic_red')
    trim_dark = material('city_traffic_dark')
    for ob in bpy.data.objects:
        if ob.type != 'MESH':
            continue
        src_mats = [s.name for s in ob.data.materials]
        src = src_mats[0] if src_mats else ''
        if ob.name in ROOF_OBJECTS:
            new = roof
        elif 'windows' in src:
            new = glass
        elif 'headlight' in src:
            new = lamp_front
        elif 'backlight' in src:
            new = lamp_rear
        elif 'rrubber' in src:
            new = tire
        elif 'chrome' in src:
            new = chrome
        elif 'black' in src:
            new = trim_dark
        else:
            new = body
        ob.data.materials.clear()
        ob.data.materials.append(new)
        new.name = new.name.split('.')[0]


def _join_meshes():
    meshes = [o for o in bpy.data.objects if o.type == 'MESH']
    if not meshes:
        raise RuntimeError('empty import')
    if len(meshes) == 1:
        return meshes[0]
    bpy.ops.object.select_all(action='DESELECT')
    for ob in meshes:
        ob.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.object.join()
    return bpy.context.view_layer.objects.active


def _fit_and_place(car_ob):
    """直接在 mesh 頂點上縮放與貼地（無頭模式較不依賴 transform_apply）。"""
    car_ob.location = (0.0, 0.0, 0.0)
    car_ob.scale = (1.0, 1.0, 1.0)
    car_ob.rotation_euler = (0.0, 0.0, 0.0)
    mn, mx = _mesh_bounds()
    length = mx.y - mn.y
    if length < 1e-6:
        raise RuntimeError('empty import')
    scale = TARGET_LENGTH / length
    cx = (mn.x + mx.x) * 0.5
    cy = (mn.y + mx.y) * 0.5
    z0 = mn.z
    mesh = car_ob.data
    for v in mesh.vertices:
        w = car_ob.matrix_world @ v.co
        w.x = (w.x - cx) * scale
        w.y = (w.y - cy) * scale
        w.z = (w.z - z0) * scale
        v.co = car_ob.matrix_world.inverted() @ w
    mesh.update()
    bpy.context.view_layer.update()
    mn2, mx2 = _mesh_bounds()
    print(f'FIT scale={scale:.4f} before_len={(mx.y - mn.y):.3f} after_len={(mx2.y - mn2.y):.3f} z0={mn2.z:.3f}')


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()
    set_palette_overrides({})
    if not os.path.isfile(SOURCE_GLB):
        raise FileNotFoundError(SOURCE_GLB)
    bpy.ops.import_scene.gltf(filepath=SOURCE_GLB)
    # 保留分件材質對應後再合併，方便上粉／白頂／鍍鉻
    _assign_materials()
    car_ob = _join_meshes()
    car_ob.name = 'city_pink_car_body'
    _fit_and_place(car_ob)

    car_ob.name = 'city-pink-car'
    car_ob['footprint_w'] = FOOTPRINT_W
    car_ob['footprint_d'] = FOOTPRINT_D
    car_ob['ground_y'] = 0.0
    car_ob['model'] = 'city-pink-car'

    os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
    mn_pre, mx_pre = _mesh_bounds()
    print(f'PRE_EXPORT len={(mx_pre.y - mn_pre.y):.3f} h={(mx_pre.z - mn_pre.z):.3f} z0={mn_pre.z:.3f}')
    bpy.ops.object.select_all(action='DESELECT')
    bpy.ops.export_scene.gltf(
        filepath=out_path, export_format='GLB', use_selection=False,
        export_yup=True, export_apply=True, export_extras=True,
        export_texcoords=True, export_normals=True, export_materials='EXPORT',
        export_cameras=False, export_lights=False, export_animations=False,
    )
    mn, mx = _mesh_bounds()
    tris = sum(len(p.vertices) - 2 for o in bpy.data.objects if o.type == 'MESH' for p in o.data.polygons)
    size = os.path.getsize(out_path)
    print(
        f'EXPORTED {out_path} tris≈{tris} bytes={size} '
        f'gltf_len≈{mx.y - mn.y:.3f} w≈{mx.x - mn.x:.3f} h≈{mx.z - mn.z:.3f}'
    )
    return tris, size


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))