"""城市島粉紅復古小車（Fiat 500 掀背側影）；邏輯／碰撞仍用 island-vehicle.js。

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

    b = Builder('city-pink-car', 1.18, 0.88)
    body = b.part('car_body')
    cabin = b.part('car_cabin')
    trim = b.part('car_trim')
    wheels = b.part('car_wheels')
    glass = b.part('car_glass')

    # Fiat 500：短鼻、單一圓頂泡泡、後段掀背下收，全長約 0.96
    body.box('city_pink_car', 0.90, 0.11, 0.40, 0, 0.08, 0.36)
    body.box('city_pink_car', 0.82, 0.18, 0.44, 0, 0.10, 0.22)
    body.sphere('city_pink_car', 0.22, 0, 0.06, -0.10, seg=12, rings=8)
    body.box('city_pink_car', 0.52, 0.16, 0.40, 0, 0.12, -0.22, rx=-0.22)
    body.box('city_pink_car', 0.38, 0.14, 0.36, 0, 0.10, -0.36, rx=-0.38)
    cabin.sphere('city_pink_roof', 0.30, 0, 0.20, 0.30, sz=0.52, seg=14, rings=9)
    cabin.sphere('city_pink_roof', 0.20, 0, 0.14, 0.52, sz=0.44, seg=10, rings=7)

    trim.box('city_trim_white', 0.68, 0.05, 0.07, 0, -0.44, -0.06)
    trim.box('city_trim_white', 0.58, 0.04, 0.06, 0, 0.43, 0.08)
    trim.box('city_trim_white', 0.62, 0.03, 0.04, 0, -0.44, 0.02)
    trim.box('city_trim_white', 0.18, 0.03, 0.05, 0, 0.40, -0.28)

    for sx in (-1, 1):
        trim.cyl('windows_glow', 0.078, 0.078, 0.02, sx * 0.34, -0.40, -0.08,
                 seg=16, rx=math.pi / 2)
        trim.sphere('city_trim_white', 0.065, sx * 0.34, -0.40, -0.08, seg=12, rings=8)
        body.box('city_pink_car', 0.08, 0.04, 0.10, sx * 0.36, -0.02, -0.10)

    glass.box('city_glass_blue', 0.38, 0.02, 0.22, 0, 0.28, 0.28)
    glass.box('city_glass_blue', 0.34, 0.14, 0.02, 0, 0.26, 0.22)
    glass.box('city_glass_blue', 0.30, 0.02, 0.24, 0, 0.04, 0.18)
    for sx in (-0.18, 0.18):
        glass.box('city_glass_blue', 0.02, 0.11, 0.24, sx, 0.0, 0.16)
    glass.box('city_glass_blue', 0.20, 0.09, 0.02, 0, -0.14, -0.08)
    glass.box('city_glass_blue', 0.16, 0.08, 0.02, 0, 0.12, -0.30)

    for sx in (-0.10, 0.10):
        trim.sphere('city_traffic_red', 0.040, sx, 0.41, 0.02, seg=10, rings=6)
    trim.sphere('city_traffic_red', 0.032, 0, 0.38, -0.38, seg=8, rings=6)

    wheel_offs = [(-0.40, 0.20), (0.40, 0.20), (-0.40, -0.20), (0.40, -0.20)]
    for wx, wz in wheel_offs:
        wheels.cyl('city_traffic_dark', 0.12, 0.12, 0.11, wx, wz, 0.09, seg=16, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.070, 0.070, 0.06, wx, wz, 0.09, seg=12, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.036, 0.036, 0.04, wx, wz, 0.09, seg=8, rx=math.pi / 2)

    return b.finish(out_path, extras={'model': 'city-pink-car'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))