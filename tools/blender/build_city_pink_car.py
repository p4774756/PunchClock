"""城市島粉紅復古小車（圓潤殼（圓潤殼體）；邏輯／碰撞仍用 island-vehicle.js。

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


def _half_arc(part, mat, cx, cz, radius, nseg, sx, sy, sz, z0, rz=0.0):
  for i in range(nseg):
    a0 = math.pi * i / nseg
    a1 = math.pi * (i + 1) / nseg
    mx = cx + radius * math.cos((a0 + a1) / 2)
    mz = cz + radius * math.sin((a0 + a1) / 2)
    part.box(mat, sx, sy, sz, mx, 0, mz + z0, rz=rz)


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

    # 底盤（平順、少稜角）
    body.box('city_pink_car', 1.18, 0.28, 0.64, 0, 0, 0.14)
  # 側腹略外鼓
    body.sphere('city_pink_car', 0.18, 0, 0.58, 0.22, sz=0.52, seg=9, rings=5)
    body.sphere('city_pink_car', 0.18, 0, -0.58, 0.22, sz=0.52, seg=9, rings=5)
    _half_arc(body, 'city_pink_car', 0, -0.38, 0.38, 10, 0.30, 0.36, 0.64, 0.14)
    _half_arc(body, 'city_pink_car', 0, 0.38, 0.38, 10, 0.30, 0.36, 0.64, 0.14)

    # 圓頂車室
    cabin.sphere('city_pink_roof', 0.44, 0, 0, 0.58, sz=0.68, seg=12, rings=7)
    cabin.box('city_pink_roof', 0.78, 0.24, 0.54, 0, 0, 0.52)
    _half_arc(cabin, 'city_pink_roof', 0, 0.24, 0.26, 6, 0.22, 0.26, 0.52, 0.58)

    # 輪拱（弧線而非瘤塊）
    for sx in (-1, 1):
        for sz in (-0.27, 0.27):
            trim.sphere('city_pink_car', 0.15, sx * 0.54, sz, 0.17, sz=0.48, seg=7, rings=4)

    # 車窗與鍍鉻
    trim.box('city_glass_blue', 0.54, 0.20, 0.42, 0, 0.06, 0.50)
    for sx in (-0.28, 0.28):
        trim.box('city_glass_blue', 0.22, 0.18, 0.40, sx, 0.04, 0.48)
        trim.box('city_trim_white', 0.26, 0.04, 0.44, sx, 0.01, 0.48)
    trim.box('city_trim_white', 0.76, 0.04, 0.05, 0, -0.35, 0.30)
    trim.box('city_trim_white', 0.06, 0.16, 0.48, 0, 0.35, 0.42)

    # 頭尾燈
    for sx in (-0.64, 0.64):
        trim.sphere('city_trim_white', 0.06, sx, -0.22, 0.28, seg=7, rings=5)
        trim.sphere('city_traffic_red', 0.05, sx, 0.22, 0.28, seg=6, rings=4)

    wheel_offsets = [(-0.48, 0.28), (0.48, 0.28), (-0.48, -0.28), (0.48, -0.28)]
    for wx, wz in wheel_offsets:
        wheels.cyl('city_traffic_dark', 0.13, 0.13, 0.11, wx, wz, 0.11, seg=12, rx=math.pi / 2)
        wheels.cyl('city_trim_white', 0.08, 0.08, 0.05, wx, wz, 0.11, seg=10, rx=math.pi / 2)

    return b.finish(out_path, extras={'model': 'city-pink-car'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'city-pink-car.glb')
    build(argv_out(_default))