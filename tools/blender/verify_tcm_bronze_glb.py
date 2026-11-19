#!/usr/bin/env python3
"""Parse tcm-bronze-figure.glb: POSITION/UV bounds, sash→body distance, plaque TEXCOORD."""
from __future__ import annotations

import json
import math
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
    count = acc['count']
    items = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[atype]
    stride = 4 * items
    fmt = '<' + 'f' * items
    rows = []
    for i in range(count):
        off = i * stride
        rows.append(struct.unpack(fmt, bv[off : off + stride]))
    return rows


def axis_minmax(rows, axis):
    vals = [r[axis] for r in rows]
    return min(vals), max(vals)


def image_bytes(chunk, blob, img_idx):
    img = chunk['images'][img_idx]
    if 'bufferView' in img:
        return bytes(buffer_view(blob, chunk, img['bufferView']))
    if 'uri' in img and img['uri'].startswith('data:'):
        import base64

        b64 = img['uri'].split(',', 1)[1]
        return base64.b64decode(b64)
    return None


def mesh_by_node_name(chunk, blob, name):
    meshes = chunk.get('meshes', [])
    for node in chunk.get('nodes', []):
        if node.get('name') != name:
            continue
        mi = node.get('mesh')
        if mi is None:
            return None
        prim = meshes[mi]['primitives'][0]
        attrs = prim['attributes']
        pos = accessor_array(chunk, blob, attrs['POSITION'])
        uv = None
        if 'TEXCOORD_0' in attrs:
            uv = accessor_array(chunk, blob, attrs['TEXCOORD_0'])
        return pos, uv
    return None


def sash_body_distances(chunk, blob, step=4):
    body = mesh_by_node_name(chunk, blob, 'bronze_body')
    band = mesh_by_node_name(chunk, blob, 'bronze_sash_band')
    if not body or not band:
        return None
    bpos, _ = body
    spos, _ = band
    dists = []
    for i in range(0, len(spos), step):
        p = spos[i]
        best = 1e9
        for j in range(0, len(bpos), step):
            q = bpos[j]
            d = math.sqrt(sum((p[k] - q[k]) ** 2 for k in range(3)))
            if d < best:
                best = d
        dists.append(best)
    return min(dists), max(dists)


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
        xs = [p[0] for p in pos]
        ys = [p[1] for p in pos]
        zs = [p[2] for p in pos]
        line = (
            f'  mesh {name!r} POSITION '
            f'x=[{min(xs):.4f},{max(xs):.4f}] '
            f'y=[{min(ys):.4f},{max(ys):.4f}] '
            f'z=[{min(zs):.4f},{max(zs):.4f}] verts={len(pos)}'
        )
        if 'TEXCOORD_0' in prim['attributes']:
            uv = accessor_array(chunk, blob, prim['attributes']['TEXCOORD_0'])
            us = [u[0] for u in uv]
            vs = [u[1] for u in uv]
            line += f' UV u=[{min(us):.4f},{max(us):.4f}] v=[{min(vs):.4f},{max(vs):.4f}]'
        else:
            line += ' UV=MISSING'
        print(line)

    plaque = mesh_by_node_name(chunk, blob, 'pedestal_plaque')
    if plaque:
        _, uv = plaque
        ok = uv is not None and len(uv) > 0
        print(f'  plaque TEXCOORD_0={"yes" if ok else "NO"}')

    band = mesh_by_node_name(chunk, blob, 'bronze_sash_band')
    if band:
        bpos, buv = band
        bx = axis_minmax(bpos, 0)
        print(f'  sash_band |x| range ≈ [{abs(bx[0]):.4f},{abs(bx[1]):.4f}] (expect ~0.17-0.19)')
        if buv:
            print(
                f'  sash_band UV u=[{axis_minmax(buv,0)[0]:.4f},{axis_minmax(buv,0)[1]:.4f}] '
                f'v=[{axis_minmax(buv,1)[0]:.4f},{axis_minmax(buv,1)[1]:.4f}]'
            )

    dist = sash_body_distances(chunk, blob)
    if dist:
        print(
            f'  sash→body vertex distance (sampled) mm=[{dist[0]*1000:.1f},{dist[1]*1000:.1f}] '
            '(expect 3-12)'
        )

    if Image is None:
        print('PIL missing; skip textures')
        return
    mats = chunk.get('materials', [])
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


if __name__ == '__main__':
    main()