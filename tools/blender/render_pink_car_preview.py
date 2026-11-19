"""無頭預覽：日間 3/4 前／後側。輸出到 tools/blender/previews/（不進 git）。

  nice -n 10 ~/.local/bin/blender -b --factory-startup -P tools/blender/render_pink_car_preview.py
"""
from __future__ import annotations

import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import bpy
from mathutils import Vector

from kit import prepare_scene

_ROOT = os.path.abspath(os.path.join(_HERE, '..', '..'))
CAR_GLB = os.path.join(_ROOT, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
OUT_DIR = os.path.join(_HERE, 'previews')


def _bounds():
    mn = Vector((1e9, 1e9, 1e9))
    mx = Vector((-1e9, -1e9, -1e9))
    for ob in bpy.data.objects:
        if ob.type != 'MESH':
            continue
        for c in ob.bound_box:
            co = ob.matrix_world @ Vector(c)
            mn.x = min(mn.x, co.x)
            mx.x = max(mx.x, co.x)
            mn.y = min(mn.y, co.y)
            mx.y = max(mx.y, co.y)
            mn.z = min(mn.z, co.z)
            mx.z = max(mx.z, co.z)
    return mn, mx


def _setup_world():
    sc = bpy.context.scene
    sc.render.engine = 'BLENDER_WORKBENCH'
    sc.display.shading.light = 'STUDIO'
    sc.display.shading.color_type = 'MATERIAL'
    sc.render.resolution_x = 960
    sc.render.resolution_y = 540
    sc.render.film_transparent = False
    if sc.world is None:
        sc.world = bpy.data.worlds.new('World')
    sc.world.use_nodes = True
    bg = sc.world.node_tree.nodes.get('Background')
    if bg:
        bg.inputs['Color'].default_value = (0.72, 0.84, 0.96, 1.0)
        bg.inputs['Strength'].default_value = 1.05
    sun = bpy.data.lights.new('Sun', 'SUN')
    sun.energy = 2.6
    sun_obj = bpy.data.objects.new('Sun', sun)
    bpy.context.scene.collection.objects.link(sun_obj)
    sun_obj.rotation_euler = (math.radians(52), math.radians(8), math.radians(24))


def _render_pose(name, cam_y, cam_x=0.0, cam_z=None):
    mn, mx = _bounds()
    center = Vector(((mn.x + mx.x) * 0.5, (mn.y + mx.y) * 0.5, (mn.z + mx.z) * 0.5))
    if cam_z is None:
        cam_z = center.z + 0.22
    cam = bpy.data.cameras.new('Cam')
    cam_obj = bpy.data.objects.new('Cam', cam)
    bpy.context.scene.collection.objects.link(cam_obj)
    cam_obj.location = (cam_x, cam_y, cam_z)
    direction = center - cam_obj.location
    cam_obj.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()
    bpy.context.scene.camera = cam_obj
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, name + '.png')
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print('PREVIEW', path)
    bpy.data.objects.remove(cam_obj, do_unlink=True)


def main():
    prepare_scene()
    _setup_world()
    if not os.path.isfile(CAR_GLB):
        raise FileNotFoundError('請先建置 ' + CAR_GLB)
    bpy.ops.import_scene.gltf(filepath=CAR_GLB)
    mn, mx = _bounds()
    rear_y = mx.y + 1.55
    front_y = mn.y - 1.55
    _render_pose('pink-car-3q-rear-day', rear_y, cam_x=0.55)
    _render_pose('pink-car-3q-front-day', front_y, cam_x=-0.55)


if __name__ == '__main__':
    main()