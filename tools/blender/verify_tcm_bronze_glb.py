#!/usr/bin/env python3
"""Parse tcm-bronze-figure.glb: POSITION/UV bounds, sash→body, plaque flat normals, meridian edge sample."""
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


def accessor_indices(chunk, blob, acc_idx):
    acc = chunk['accessors'][acc_idx]
    bv = buffer_view(blob, chunk, acc['bufferView'])
    count = acc['count']
    ctype = acc['componentType']
    if ctype == 5123:
        fmt = '<H'
        stride = 2
    elif ctype == 5125:
        fmt = '<I'
        stride = 4
    else:
        return []
    out = []
    for i in range(count):
        off = i * stride
        out.append(struct.unpack(fmt, bv[off : off + stride])[0])
    return out


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


def mesh_primitive_by_node(chunk, blob, name):
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
        nrm = None
        if 'TEXCOORD_0' in attrs:
            uv = accessor_array(chunk, blob, attrs['TEXCOORD_0'])
        if 'NORMAL' in attrs:
            nrm = accessor_array(chunk, blob, attrs['NORMAL'])
        indices = None
        if 'indices' in prim:
            indices = accessor_indices(chunk, blob, prim['indices'])
        return pos, uv, nrm, indices
    return None


def mesh_by_node_name(chunk, blob, name):
    got = mesh_primitive_by_node(chunk, blob, name)
    if not got:
        return None
    pos, uv, _nrm, _idx = got
    return pos, uv


def body_gap_sample(body_pos, world_p, step=22):
    best = 1e9
    for j in range(0, len(body_pos), step):
        q = body_pos[j]
        d = math.sqrt(sum((world_p[k] - q[k]) ** 2 for k in range(3)))
        if d < best:
            best = d
    return best


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


def meridian_edge_stats(body_pos, mer_pos, mer_idx):
    if not mer_idx:
        return None
    edges = set()
    for i in range(0, len(mer_idx) - 2, 3):
        a, b, c = mer_idx[i], mer_idx[i + 1], mer_idx[i + 2]
        for e in (a, b), (b, c), (c, a):
            edges.add(tuple(sorted(e)))
    max_gap = 0.0
    max_len = 0.0
    edge_list = list(edges)
    if len(edge_list) > 4000:
        edge_list = edge_list[:: max(1, len(edge_list) // 4000)]
    for a, b in edge_list:
        pa, pb = mer_pos[a], mer_pos[b]
        elen = math.sqrt(sum((pa[k] - pb[k]) ** 2 for k in range(3)))
        max_len = max(max_len, elen)
        for t in (0.25, 0.5, 0.75):
            wp = tuple(pa[k] + t * (pb[k] - pa[k]) for k in range(3))
            max_gap = max(max_gap, body_gap_sample(body_pos, wp))
    return max_gap, max_len


def linear_to_srgb8(c):
    c = max(0.0, min(1.0, c))
    if c <= 0.0031308:
        return int(round(c * 12.92 * 255))
    return int(round((1.055 * (c ** (1.0 / 2.4)) - 0.055) * 255))


def plaque_front_check(pos, uv, nrm):
    """匾正面四點：uv 左上(0,0)…、法線朝 +Z（glTF）。"""
    if not (uv and nrm and pos):
        return 'missing attrs'
    face_pts = []
    for i, p in enumerate(pos):
        nm = nrm[i]
        if abs(nm[2] - 1.0) < 0.02 and abs(nm[0]) < 0.02 and abs(nm[1]) < 0.02:
            face_pts.append((i, p, uv[i], nrm[i]))
    if len(face_pts) != 4:
        return f'front vert count={len(face_pts)} (expect 4)'
    face_pts.sort(key=lambda t: (-t[1][1], t[1][0]))
    corners = {'ul': None, 'ur': None, 'll': None, 'lr': None}
    for _i, p, uvi, nm in face_pts:
        u, v = uvi
        if p[0] < 0 and v < 0.5:
            corners['ul'] = (p, uvi, nm)
        elif p[0] >= 0 and v < 0.5:
            corners['ur'] = (p, uvi, nm)
        elif p[0] < 0:
            corners['ll'] = (p, uvi, nm)
        else:
            corners['lr'] = (p, uvi, nm)
    if not all(corners.values()):
        return 'could not classify plaque corners'
    lines = ['plaque front corners (glTF y-up):']
    ok_uv = True
    ok_n = True
    expect = {
        'ul': ((0.0, 0.0), (0, 0, 1)),
        'ur': ((1.0, 0.0), (0, 0, 1)),
        'll': ((0.0, 1.0), (0, 0, 1)),
        'lr': ((1.0, 1.0), (0, 0, 1)),
    }
    for key in ('ul', 'ur', 'll', 'lr'):
        p, uvi, nm = corners[key]
        eu, en = expect[key]
        lines.append(
            f'  {key} pos=({p[0]:.4f},{p[1]:.4f},{p[2]:.4f}) '
            f'uv=({uvi[0]:.3f},{uvi[1]:.3f}) n=({nm[0]:.2f},{nm[1]:.2f},{nm[2]:.2f})'
        )
        if abs(uvi[0] - eu[0]) > 0.02 or abs(uvi[1] - eu[1]) > 0.02:
            ok_uv = False
        if abs(nm[2] - 1.0) > 0.15 or math.hypot(nm[0], nm[1]) > 0.25:
            ok_n = False
    lines.append(f'  plaque UV ok={ok_uv} normal ok={ok_n}')
    return '\n'.join(lines)


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

    for tier in ('pedestal_base', 'pedestal_mid', 'pedestal_top'):
        got = mesh_primitive_by_node(chunk, blob, tier)
        if got:
            pos = got[0]
            print(f'  {tier} verts={len(pos)} (expect 24 flat)')

    plaque = mesh_primitive_by_node(chunk, blob, 'pedestal_plaque')
    if plaque:
        pos, uv, nrm, _ = plaque
        print(f'  pedestal_plaque verts={len(pos)} (expect ≥20 split)')
        print(plaque_front_check(pos, uv, nrm))

    shell = mesh_primitive_by_node(chunk, blob, 'bronze_hair_shell')
    body_mesh = mesh_primitive_by_node(chunk, blob, 'bronze_body')
    if shell and body_mesh:
        spos = shell[0]
        bpos = body_mesh[0]
        gaps = []
        for i in range(0, len(spos), 48):
            gaps.append(body_gap_sample(bpos, spos[i], step=48))
        print(
            f'  hair_shell→body mm mean={1000*sum(gaps)/len(gaps):.2f} '
            f'max={1000*max(gaps):.2f} (expect 4-7)'
        )

    bun = mesh_primitive_by_node(chunk, blob, 'bronze_hair_bun')
    if bun:
        bpos = bun[0]
        cx = 0.5 * (min(p[0] for p in bpos) + max(p[0] for p in bpos))
        ys = [p[1] for p in bpos]
        zs = [p[2] for p in bpos]
        print(
            f'  bun center_x={cx:.4f} y=[{min(ys):.3f},{max(ys):.3f}] '
            f'z=[{min(zs):.3f},{max(zs):.3f}] (expect |x|<0.003)'
        )
        if shell:
            spos = shell[0]
            min_gap = 1e9
            for i in range(0, len(bpos), 4):
                p = bpos[i]
                for j in range(0, len(spos), 6):
                    q = spos[j]
                    d = math.sqrt(sum((p[k] - q[k]) ** 2 for k in range(3)))
                    min_gap = min(min_gap, d)
            print(f'  bun→hair_shell min dist m={min_gap:.4f} (expect ≤0 embed)')

    mer = mesh_primitive_by_node(chunk, blob, 'bronze_meridians')
    if mer and body_mesh:
        mpos, _, _, midx = mer
        bpos = body_mesh[0]
        stats = meridian_edge_stats(bpos, mpos, midx)
        if stats:
            gmax, lmax = stats
            print(
                f'  meridians edge sample gap max mm={gmax*1000:.2f} '
                f'edge len max mm={lmax*1000:.2f} (expect ≤6 / ≤12)'
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
            base = pbr.get('baseColorFactor')
            if base and mat.get('name') == 'bronze_hair':
                mean = tuple(linear_to_srgb8(c) for c in base[:3])
                print(f'  bronze_hair base sRGB={mean} (from linear factor)')
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