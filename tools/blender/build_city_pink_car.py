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

    b = Builder('city-pink-car', 1.72, 0.92)
    body = b.part('car_body')
    cabin = b.part('car_cabin')
    trim = b.part('car_trim')
    wheels = b.part('car_wheels')
    glass = b.part('car_glass')

    # 短鼻圓頂掀背（側／後方可辨識輪拱與尾窗）
    body.box('city_pink_car', 0.88, 0.17, 0.52, 0, -0.28, 0.10)
    body.box('city_pink_car', 0.94, 0.22, 0.58, 0, 0.02, 0.11)
    body.box('city_pink_car', 0.90, 0.34, 0.56, 0, 0.02, 0.28)
    body.box('city_pink_car', 0.84, 0.40, 0.54, 0, 0.04, 0.44)
    body.box('city_pink_car', 0.76, 0.36, 0.50, 0, 0.06, 0.58)
    body.box('city_pink_car', 0.62, 0.28, 0.46, 0, 0.04, 0.70)
    cabin.box('city_pink_roof', 0.70, 0.16, 0.42, 0, 0.02, 0.78)
    cabin.box('city_pink_roof', 0.54, 0.10, 0.36, 0, 0.0, 0.84)
    # 前鼻圓滑
    body.box('city_pink_car', 0.48, 0.20, 0.48, 0, -0.40, 0.20, rx=-0.18)
    body.box('city_pink_car', 0.36, 0.24, 0.44, 0, -0.46, 0.30, rx=-0.28)
    # 後掀背斜面
    body.box('city_pink_car', 0.58, 0.22, 0.44, 0, 0.40, 0.34, rx=0.32)
    body.box('city_pink_car', 0.50, 0.18, 0.40, 0, 0.44, 0.48, rx=0.38)

    for sx in (-1, 1):
        trim.box('city_traffic_dark', 0.22, 0.12, 0.30, sx * 0.46, 0.02, 0.20)
        body.box('city_pink_car', 0.18, 0.08, 0.22, sx * 0.50, 0.0, 0.26)
        body.sphere('city_pink_car', 0.14, sx * 0.48, 0.08, 0.24, seg=8, rings=5)

    # 尾窗（後視可見深色玻璃塊）
    glass.box('city_glass_blue', 0.46, 0.02, 0.30, 0, 0.36, 0.54)
    glass.box('city_glass_blue', 0.38, 0.14, 0.02, 0, 0.34, 0.50)
    # 側窗
    glass.box('city_glass_blue', 0.40, 0.02, 0.32, 0, 0.08, 0.50)
    for sx in (-0.24, 0.24):
        glass.box('city_glass_blue', 0.02, 0.14, 0.30, sx, 0.06, 0.48)
    glass.box('city_glass_blue', 0.28, 0.10, 0.02, 0, -0.18, 0.40)

    trim.box('city_trim_white', 0.74, 0.04, 0.06, 0, -0.44, 0.15)
    trim.box('city_trim_white', 0.62, 0.04, 0.06, 0, 0.43, 0.17)
    trim.box('city_trim_white', 0.72, 0.03, 0.04, 0, -0.44, 0.21)
    for sx in (-0.40, 0.40):
        trim.sphere('city_trim_white', 0.048, sx, -0.38, 0.20, seg=8, rings=5)
    for sx in (-0.10, 0.10):
        trim.sphere('city_traffic_red', 0.046, sx, 0.42, 0.20, seg=8, rings=5)

    wheel_offs = [(-0.44, 0.27), (0.44, 0.27), (-0.44, -0.27), (0.44, -0.27)]
    for wx, wz in wheel_offs:
        wheels.cyl('city_traffic_dark', 0.13, 0.13, 0.10, wx, wz, 0.10, seg=16, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.074, 0.074, 0.05, wx, wz, 0.10, seg=12, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.038, 0.038, 0.03, wx, wz, 0.10, seg=8, rx=math.pi / 2)

    return b.finish(out_path, extras={'model': 'city-pink-car'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))