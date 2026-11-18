"""已遷移至 tools/blender/build_casino.py；此模組保留給 build_all.py 相容匯入。"""
from __future__ import annotations

import os
import sys

_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
sys.path.insert(0, os.path.join(_ROOT, 'tools', 'blender'))

from build_casino import build  # noqa: E402,F401

if __name__ == '__main__':
    from kit import argv_out

    _default = os.path.join(_ROOT, 'server', 'src', 'main', 'resources', 'public', 'models', 'casino.glb')
    build(argv_out(_default))