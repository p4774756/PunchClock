"""城市島粉紅復古小車（圓潤殼體）；邏輯／碰撞仍用 island-vehicle.js。

  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_city_pink_car.py -- \\
    server/src/main/resources/public/models/city-pink-car.glb
"""
from __future__ import annotations

import math
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import bpy
import kit
from kit import Builder, clear_scene, prepare_scene, argv_out, set_palette_overrides


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()

    set_palette_overrides({})

    b = Builder('city-pink-car', 1.6, 0.9)
    body = b.part('car_body')
    cabin = b.part('car_cabin')
    trim = b.part('car_trim')
    wheels = b.part('car_wheels')

    # 底盤略圓
    body.box('city_pink_car', 1.42, 0.38, 0.72, 0, 0, 0.19)
    body.sphere('city_pink_car', 0.18, 0.62, 0, 0.19, sz=0.55, seg=6, rings=4)
    body.sphere('city_pink_car', 0.18, -0.62, 0, 0.19, sz=0.55, seg=6, rings=4)

    cabin.box('city_pink_roof', 0.92, 0.36, 0.62, 0, 0, 0.55)
    cabin.sphere('city_pink_roof', 0.22, 0, 0, 0.72, sz=0.85, seg=7, rings=5)

    for sx in (-0.32, 0.32):
        trim.box('city_glass_blue', 0.26, 0.20, 0.48, sx, 0.08, 0.52)
        trim.box('city_trim_white', 0.30, 0.04, 0.52, sx, 0.04, 0.52)

    # 頭燈
    trim.sphere('city_trim_white', 0.06, 0.68, -0.22, 0.32, seg=5, rings=4)
    trim.sphere('city_trim_white', 0.06, -0.68, -0.22, 0.32, seg=5, rings=4)

    wheel_offsets = [(-0.52, 0.30), (0.52, 0.30), (-0.52, -0.30), (0.52, -0.30)]
    for wx, wz in wheel_offsets:
        wheels.cyl('city_traffic_dark', 0.14, 0.14, 0.11, wx, wz, 0.12, seg=8,
                   rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.08, 0.08, 0.06, wx, wz, 0.12, seg=6,
                   rx=math.pi / 2)

    return b.finish(out_path, extras={'model': 'city-pink-car'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))