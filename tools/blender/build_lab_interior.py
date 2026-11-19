"""研發樓室內大廳桁架（選用 GLB；執行時仍以 island-lab-interior.js 程序化為主）。

  ~/.local/bin/blender -b --factory-startup -P tools/blender/build_lab_interior.py -- \\
    server/src/main/resources/public/models/lab-interior-truss.glb
"""
from __future__ import annotations

import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import bpy
from kit import Builder, clear_scene, prepare_scene, argv_out

W, D = 14.0, 12.0


def build(out_path):
    if bpy.data.objects:
        clear_scene()
    else:
        prepare_scene()

    b = Builder('lab-truss', W, D)
    p = b.part('truss')
    for i, x in enumerate([-4.5, -1.5, 1.5, 4.5]):
        p.box('lab_steel', 0.12, 0.12, 3.8, x, 0, 2.2 + i * 0.08)
        p.box('lab_steel_dark', 0.08, 0.08, 3.6, x, 0, 2.15 + i * 0.08)
    p.box('lab_glass', 2.2, 0.04, 0.06, 0, 0, 3.6)
    return b.finish(out_path, extras={'building_id': 'lab-interior-truss'})


if __name__ == '__main__':
    _root = os.path.abspath(os.path.join(_HERE, '..', '..'))
    _default = os.path.join(_root, 'server', 'src', 'main', 'resources', 'public', 'models', 'lab-interior-truss.glb')
    build(argv_out(_default))