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

    # 短掀背：全長約 0.95，圓頂、露輪、前圓燈獨立碟片
    body.box('city_pink_car', 0.72, 0.14, 0.48, 0, 0.02, 0.09)
    body.box('city_pink_car', 0.76, 0.18, 0.50, 0, 0.0, 0.18)
    body.box('city_pink_car', 0.74, 0.26, 0.48, 0, 0.0, 0.30)
    body.box('city_pink_car', 0.70, 0.30, 0.46, 0, 0.01, 0.42)
    body.box('city_pink_car', 0.64, 0.28, 0.44, 0, 0.02, 0.54)
    body.box('city_pink_car', 0.52, 0.22, 0.42, 0, 0.02, 0.64)
    cabin.box('city_pink_roof', 0.58, 0.14, 0.38, 0, 0.0, 0.72)
    cabin.box('city_pink_roof', 0.46, 0.10, 0.32, 0, -0.02, 0.78)
    body.box('city_pink_car', 0.40, 0.18, 0.40, 0, -0.36, 0.18, rx=-0.14)
    body.box('city_pink_car', 0.32, 0.16, 0.38, 0, -0.42, 0.26, rx=-0.18)
    body.box('city_pink_car', 0.44, 0.18, 0.38, 0, 0.32, 0.30, rx=0.28)
    body.box('city_pink_car', 0.38, 0.16, 0.36, 0, 0.38, 0.42, rx=0.34)
    body.box('city_pink_car', 0.28, 0.12, 0.34, 0, 0.42, 0.52, rx=0.40)

    trim.box('city_trim_white', 0.64, 0.04, 0.06, 0, -0.44, 0.14)
    trim.box('city_trim_white', 0.54, 0.04, 0.06, 0, 0.43, 0.15)
    trim.box('city_trim_white', 0.58, 0.03, 0.04, 0, -0.44, 0.18)

    for sx in (-1, 1):
        trim.cyl('windows_glow', 0.078, 0.078, 0.02, sx * 0.34, -0.40, 0.20,
                 seg=16, rx=math.pi / 2)
        trim.sphere('city_trim_white', 0.065, sx * 0.34, -0.40, 0.20, seg=12, rings=8)
        body.box('city_pink_car', 0.10, 0.05, 0.14, sx * 0.38, -0.02, 0.12)

    glass.box('city_glass_blue', 0.36, 0.02, 0.26, 0, 0.30, 0.52)
    glass.box('city_glass_blue', 0.30, 0.12, 0.02, 0, 0.28, 0.48)
    glass.box('city_glass_blue', 0.32, 0.02, 0.28, 0, 0.02, 0.46)
    for sx in (-0.20, 0.20):
        glass.box('city_glass_blue', 0.02, 0.12, 0.26, sx, 0.0, 0.44)
    glass.box('city_glass_blue', 0.22, 0.10, 0.02, 0, -0.16, 0.36)

    for sx in (-0.12, 0.12):
        trim.sphere('city_traffic_red', 0.042, sx, 0.41, 0.18, seg=10, rings=6)

    wheel_offs = [(-0.40, 0.22), (0.40, 0.22), (-0.40, -0.22), (0.40, -0.22)]
    for wx, wz in wheel_offs:
        wheels.cyl('city_traffic_dark', 0.12, 0.12, 0.11, wx, wz, 0.09, seg=16, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.070, 0.070, 0.06, wx, wz, 0.09, seg=12, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.036, 0.036, 0.04, wx, wz, 0.09, seg=8, rx=math.pi / 2)
        body.box('city_pink_car', 0.16, 0.08, 0.12, wx, wz, 0.14)

    return b.finish(out_path, extras={'model': 'city-pink-car'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))