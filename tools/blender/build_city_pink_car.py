"""城市島粉紅復古小車（圓潤掀背剪影）；邏輯／碰撞仍用 island-vehicle.js。

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
    glass = b.part('car_glass')

    # 底盤＋圓潤掀背側影（少球體、明確稜線）
    body.box('city_pink_car', 1.02, 0.22, 0.58, 0, 0, 0.11)
    body.box('city_pink_car', 0.94, 0.38, 0.52, 0, 0, 0.32)
    body.box('city_pink_car', 0.82, 0.30, 0.48, 0, 0.05, 0.48)
    body.box('city_pink_roof', 0.72, 0.16, 0.46, 0, -0.02, 0.68)
    body.box('city_pink_car', 0.88, 0.12, 0.42, 0, -0.28, 0.28, rx=-0.32)
    body.box('city_pink_car', 0.20, 0.14, 0.40, 0, 0.38, 0.26, rx=0.15)

    # 輪拱（掏空感）
    for sx in (-1, 1):
        for sz in (-0.24, 0.24):
            trim.box('city_traffic_dark', 0.22, 0.16, 0.26, sx * 0.50, sz, 0.13)
            body.box('city_pink_car', 0.18, 0.08, 0.22, sx * 0.52, sz, 0.22)

    # 車窗
    glass.box('city_glass_blue', 0.46, 0.02, 0.36, 0, 0.04, 0.52)
    glass.box('city_glass_blue', 0.42, 0.18, 0.02, 0, 0.10, 0.52)
    for sx in (-0.26, 0.26):
        glass.box('city_glass_blue', 0.02, 0.16, 0.34, sx, 0.06, 0.50)
    glass.box('city_glass_blue', 0.36, 0.12, 0.02, 0, -0.18, 0.46)

    # 鍍鉻／保險桿
    trim.box('city_trim_white', 0.78, 0.04, 0.06, 0, -0.36, 0.16)
    trim.box('city_trim_white', 0.06, 0.14, 0.44, 0, 0.34, 0.34)
    for sx in (-0.46, 0.46):
        trim.box('city_trim_white', 0.05, 0.10, 0.12, sx, -0.18, 0.32)

    # 頭尾燈
    trim.sphere('city_trim_white', 0.055, -0.48, -0.20, 0.24, seg=8, rings=5)
    trim.sphere('city_trim_white', 0.055, 0.48, -0.20, 0.24, seg=8, rings=5)
    trim.box('city_traffic_red', 0.06, 0.10, 0.08, 0, 0.34, 0.22)
    trim.box('city_traffic_red', 0.06, 0.10, 0.08, 0, -0.34, 0.22)

    wheel_offsets = [(-0.44, 0.26), (0.44, 0.26), (-0.44, -0.26), (0.44, -0.26)]
    for wx, wz in wheel_offsets:
        wheels.cyl('city_traffic_dark', 0.12, 0.12, 0.09, wx, wz, 0.10, seg=14, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.07, 0.07, 0.04, wx, wz, 0.10, seg=10, rx=math.pi / 2)

    return b.finish(out_path, extras={'model': 'city-pink-car'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))