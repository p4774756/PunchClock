"""城市島粉紅復古小車（Fiat 500 圓潤掀背）；邏輯／碰撞仍用 island-vehicle.js。

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

    b = Builder('city-pink-car', 1.05, 0.78)
    body = b.part('car_body')
    cabin = b.part('car_cabin')
    trim = b.part('car_trim')
    wheels = b.part('car_wheels')
    glass = b.part('car_glass')

    # 短圓掀背：總長約 0.72、寬 0.64、高駕駛艙
    body.box('city_pink_car', 0.60, 0.14, 0.46, 0, 0, 0.10)
    body.box('city_pink_car', 0.64, 0.20, 0.50, 0, 0.0, 0.22)
    body.sphere('city_pink_car', 0.34, 0, 0.0, 0.38, seg=10, rings=8)
    body.sphere('city_pink_car', 0.28, 0, -0.04, 0.52, seg=9, rings=7)
    cabin.sphere('city_pink_roof', 0.30, 0, 0.0, 0.62, seg=10, rings=8)
    cabin.box('city_pink_roof', 0.50, 0.12, 0.36, 0, 0.02, 0.72)
    # 圓鼻（前緣 -Y）
    body.sphere('city_pink_car', 0.22, 0, -0.30, 0.26, seg=8, rings=6)
    body.box('city_pink_car', 0.46, 0.18, 0.42, 0, -0.26, 0.20, rx=-0.14)
    # 後掀背
    body.box('city_pink_car', 0.52, 0.16, 0.38, 0, 0.26, 0.34, rx=0.28)
    body.box('city_pink_car', 0.44, 0.12, 0.32, 0, 0.30, 0.46, rx=0.36)

    for sx in (-1, 1):
        body.box('city_pink_car', 0.10, 0.22, 0.40, sx * 0.28, 0.0, 0.28)
        trim.sphere('city_trim_white', 0.10, sx * 0.30, -0.28, 0.22, seg=8, rings=6)
        trim.sphere('windows_glow', 0.052, sx * 0.29, -0.27, 0.24, seg=6, rings=5)

    glass.box('city_glass_blue', 0.46, 0.02, 0.28, 0, 0.24, 0.52)
    glass.box('city_glass_blue', 0.38, 0.16, 0.02, 0, 0.22, 0.48)
    glass.box('city_glass_blue', 0.36, 0.02, 0.26, 0, 0.0, 0.46)
    glass.box('city_glass_blue', 0.22, 0.12, 0.02, 0, -0.16, 0.38)
    for sx in (-0.18, 0.18):
        glass.box('city_glass_blue', 0.02, 0.12, 0.24, sx, 0.02, 0.44)

    trim.box('city_trim_white', 0.58, 0.05, 0.06, 0, -0.30, 0.15)
    trim.box('city_trim_white', 0.52, 0.05, 0.06, 0, 0.30, 0.16)
    trim.box('city_trim_white', 0.46, 0.03, 0.03, 0, -0.30, 0.19)
    for sx in (-0.32, 0.32):
        trim.sphere('city_trim_white', 0.040, sx, -0.28, 0.20, seg=7, rings=4)
    for sx in (-0.08, 0.08):
        trim.sphere('city_traffic_red', 0.042, sx, 0.30, 0.18, seg=7, rings=4)

    wheel_offs = [(-0.38, 0.24), (0.38, 0.24), (-0.38, -0.24), (0.38, -0.24)]
    for wx, wz in wheel_offs:
        wheels.cyl('city_traffic_dark', 0.12, 0.12, 0.09, wx, wz, 0.10, seg=16, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.068, 0.068, 0.04, wx, wz, 0.10, seg=12, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.032, 0.032, 0.025, wx, wz, 0.10, seg=8, rx=math.pi / 2)

    return b.finish(out_path, extras={'model': 'city-pink-car'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))