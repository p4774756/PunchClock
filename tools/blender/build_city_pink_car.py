"""城市島粉紅復古小車（Fiat 500 風格圓潤掀背）；邏輯／碰撞仍用 island-vehicle.js。

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

    # 底盤底盤＋圓潤掀背（Fiat 500：短鼻、拱頂、外擴輪拱）
    body.box('city_pink_car', 0.98, 0.20, 0.56, 0, 0, 0.10)
    body.box('city_pink_car', 0.92, 0.36, 0.54, 0, 0, 0.30)
    body.box('city_pink_car', 0.86, 0.28, 0.50, 0, 0.04, 0.46)
    body.box('city_pink_car', 0.72, 0.22, 0.46, 0, 0.02, 0.62)
    cabin.box('city_pink_roof', 0.78, 0.20, 0.44, 0, 0, 0.72)
    cabin.box('city_pink_roof', 0.66, 0.14, 0.40, 0, -0.02, 0.78)
    body.box('city_pink_car', 0.42, 0.26, 0.46, 0, -0.34, 0.26, rx=-0.12)
    body.box('city_pink_car', 0.56, 0.14, 0.44, 0, -0.30, 0.20, rx=-0.22)
    body.box('city_pink_car', 0.64, 0.18, 0.42, 0, 0.36, 0.30, rx=0.28)

    for sx in (-1, 1):
        for sz in (-0.24, 0.24):
            trim.box('city_traffic_dark', 0.24, 0.14, 0.28, sx * 0.48, sz, 0.15)
            body.box('city_pink_car', 0.16, 0.06, 0.20, sx * 0.50, sz, 0.24)

    # 車窗（掀背＋側窗）
    glass.box('city_glass_blue', 0.44, 0.02, 0.34, 0, 0.06, 0.52)
    glass.box('city_glass_blue', 0.38, 0.16, 0.02, 0, 0.10, 0.54)
    for sx in (-0.25, 0.25):
        glass.box('city_glass_blue', 0.02, 0.15, 0.32, sx, 0.06, 0.50)
    glass.box('city_glass_blue', 0.34, 0.10, 0.02, 0, -0.16, 0.44)

    # 鍍鉻保險桿
    trim.box('city_trim_white', 0.78, 0.04, 0.06, 0, -0.35, 0.13)
    trim.box('city_trim_white', 0.68, 0.04, 0.06, 0, 0.35, 0.15)
    trim.box('city_trim_white', 0.78, 0.03, 0.05, 0, -0.35, 0.20)
    trim.box('city_trim_white', 0.70, 0.03, 0.05, 0, 0.35, 0.21)
    for sx in (-0.44, 0.44):
        trim.box('city_trim_white', 0.04, 0.09, 0.10, sx, -0.16, 0.30)

    # 圓形頭尾燈
    for sx in (-0.38, 0.38):
        trim.sphere('city_trim_white', 0.050, sx, -0.30, 0.22, seg=8, rings=5)
    for sx in (-0.08, 0.08):
        trim.sphere('city_traffic_red', 0.042, sx, 0.36, 0.22, seg=8, rings=5)

    wheel_offsets = [(-0.43, 0.25), (0.43, 0.25), (-0.43, -0.25), (0.43, -0.25)]
    for wx, wz in wheel_offsets:
        wheels.cyl('city_traffic_dark', 0.12, 0.12, 0.09, wx, wz, 0.10, seg=14, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.07, 0.07, 0.04, wx, wz, 0.10, seg=10, rx=math.pi / 2)

    return b.finish(out_path, extras={'model': 'city-pink-car'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))