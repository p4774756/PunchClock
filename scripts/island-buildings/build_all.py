"""一次建出四棟建築的 GLB。

用法：
  blender -b --factory-startup --python build_all.py -- /path/to/outdir
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import build_casino
import build_lab
import build_shop
import build_tcm
from kit import argv_out

BUILDINGS = [
    ('tcm-site.glb', build_tcm.build),
    ('shop.glb', build_shop.build),
    ('casino.glb', build_casino.build),
    ('computer-site.glb', build_lab.build),
]


def main(outdir):
    os.makedirs(outdir, exist_ok=True)
    summary = []
    for name, fn in BUILDINGS:
        path = os.path.join(outdir, name)
        tris, size = fn(path)
        summary.append((name, tris, size))
    print('--- SUMMARY ---')
    for name, tris, size in summary:
        print(f'{name:22s}  tris≈{tris:5d}  {size:7d} bytes')


if __name__ == '__main__':
    main(argv_out('/workspace/glb-proto'))
