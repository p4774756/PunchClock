#!/usr/bin/env python3
"""Parse tcm-bronze-figure.glb: mesh Y bounds, texture means, export plaque PNG."""
from __future__ import annotations

import json
import struct
import sys
from io import BytesIO
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    Image = None

ROOT = Path(__file__).resolve().parents[2]
DEFAULT = ROOT / 'server/src/main/resources/public/models/tcm-bronze-figure.glb'


def read_glb(path: Path):
    data = path.read_bytes()
    assert data[:4] == b'glTF'
    jlen = struct.unpack('<I', data[12:16])[0]
    chunk0 = json.loads(data[20 : 20 + jlen])
    bin_start = 20 + jlen
    blen = struct.unpack('<I', data[bin_start + 4 : bin_start + 8])[0]
    blob = data[bin_start + 8 : bin_start + 8 + blen]
    return chunk0, blob


def buffer_view(blob, chunk, idx):
    bv = chunk['bufferViews'][idx]
    off = bv.get('byteOffset', 0)
    ln = bv['byteLength']
    return blob[off : off + ln]


def accessor_array(chunk, blob, acc_idx):
    acc = chunk['accessors'][acc_idx]
    bv = buffer_view(blob, chunk, acc['bufferView'])
    atype = acc['type']
    comp = acc['componentType']
    count = acc['count']
    items = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[atype]
    stride = 4 * items
    fmt = '<' + 'f' * items
    rows = []
    for i in range(count):
        off = i * stride
        rows.append(struct.unpack(fmt, bv[off : off + stride]))
    return rows


def image_bytes(chunk, blob, img_idx):
    img = chunk['images'][img_idx]
    if 'bufferView' in img:
        return bytes(buffer_view(blob, chunk, img['bufferView']))
    if 'uri' in img and img['uri'].startswith('data:'):
        import base64

        b64 = img['uri'].split(',', 1)[1]
        return base64.b64decode(b64)
    return None


def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT
    chunk, blob = read_glb(path)
    print(f'GLB {path.name} bytes={path.stat().st_size}')
    nodes = chunk.get('nodes', [])
    meshes = chunk.get('meshes', [])
    for ni, node in enumerate(nodes):
        mi = node.get('mesh')
        if mi is None:
            continue
        name = node.get('name') or meshes[mi].get('name') or f'mesh{mi}'
        prim = meshes[mi]['primitives'][0]
        pos = accessor_array(chunk, blob, prim['attributes']['POSITION'])
        ys = [p[1] for p in pos]
        print(
            f'  mesh {name!r} POSITION y=[{min(ys):.4f},{max(ys):.4f}] '
            f'verts={len(pos)}'
        )
    if Image is None:
        print('PIL missing; skip textures')
        return
    mats = chunk.get('materials', [])
    tex_idx = {t['name']: i for i, t in enumerate(chunk.get('textures', [])) if 'name' in t}
    for mat in mats:
        pbr = mat.get('pbrMetallicRoughness') or {}
        bct = pbr.get('baseColorTexture')
        if not bct:
            continue
        tix = bct['index']
        tex = chunk['textures'][tix]
        src = tex.get('source')
        if src is None:
            continue
        raw = image_bytes(chunk, blob, src)
        if not raw:
            continue
        im = Image.open(BytesIO(raw)).convert('RGB')
        px = list(im.getdata())
        r = sum(p[0] for p in px) / len(px)
        g = sum(p[1] for p in px) / len(px)
        b = sum(p[2] for p in px) / len(px)
        mean = (int(round(r)), int(round(g)), int(round(b)))
        label = mat.get('name', f'tex{tix}')
        print(f'  texture {label} mean sRGB={mean} #{mean[0]:02X}{mean[1]:02X}{mean[2]:02X}')
        if label == 'plaque' or 'plaque' in label:
            out = Path(__file__).parent / 'previews' / 'tcm_bronze' / 'plaque_extracted.png'
            out.parent.mkdir(parents=True, exist_ok=True)
            im.save(out)
            print(f'  WROTE {out}')


if __name__ == '__main__':
    main()