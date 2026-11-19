"""Cycles 斜俯 45° 預覽 → ~/PunchClock-oki/life3-agent-prev/（不 commit）"""
from __future__ import annotations

import math
import os

import bpy
from mathutils import Euler, Vector

_REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
LIFE = os.path.join(_REPO, 'server', 'src', 'main', 'resources', 'public', 'models', 'life')
OUT = os.path.expanduser('~/PunchClock-oki/life3-agent-prev')
FILES = (
    'fish.glb',
    'seagull.glb',
    'whale.glb',
    'octopus.glb',
    'wenyao.glb',
    'xuanwu.glb',
    'kunpeng.glb',
)


def _bounds():
    mn = Vector((1e9, 1e9, 1e9))
    mx = Vector((-1e9, -1e9, -1e9))
    for ob in bpy.data.objects:
        if ob.type != 'MESH':
            continue
        for v in ob.data.vertices:
            co = ob.matrix_world @ v.co
            for i in range(3):
                mn[i] = min(mn[i], co[i])
                mx[i] = max(mx[i], co[i])
    return mn, mx


def render_one(path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 48
    sc.render.resolution_x = 640
    sc.render.resolution_y = 480
    sc.render.film_transparent = True
    if sc.world is None:
        sc.world = bpy.data.worlds.new('prev_world')
    sc.world.use_nodes = True
    sc.world.node_tree.nodes['Background'].inputs[0].default_value = (0.78, 0.88, 0.96, 1)
    bpy.ops.import_scene.gltf(filepath=path)
    mn, mx = _bounds()
    center = (mn + mx) * 0.5
    span = max(mx.x - mn.x, mx.y - mn.y, mx.z - mn.z, 0.3)
    cam = bpy.data.cameras.new('cam')
    cam_ob = bpy.data.objects.new('cam', cam)
    sc.collection.objects.link(cam_ob)
    dist = span * 2.1
    cam_ob.location = center + Vector((dist * 0.65, -dist * 0.65, dist * 0.85))
    cam_ob.rotation_euler = Euler((math.radians(55), 0, math.radians(45)))
    cam.lens = 44
    bpy.ops.object.light_add(type='SUN', location=center + Vector((6, -6, 14)))
    light = bpy.context.object
    light.data.energy = 2.4
    sc.camera = cam_ob
    base = os.path.splitext(os.path.basename(path))[0]
    sc.render.filepath = os.path.join(OUT, base + '-45.png')
    bpy.ops.render.render(write_still=True)
    print('RENDER', sc.render.filepath)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in FILES:
        fp = os.path.join(LIFE, name)
        if os.path.isfile(fp):
            render_one(fp)


if __name__ == '__main__':
    main()