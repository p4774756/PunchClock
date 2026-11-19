"""行人 NPC v2 共用：貼圖、材質、幾何分件（build_npc_women 匯入）。"""
from __future__ import annotations

import math
import bpy
import bmesh
from mathutils import Matrix, Vector

from kit import hex_rgba, srgb_to_lin


def srgba(h, a=1.0):
    return _srgb_hex(h) + (a,)


def _px_set(pixels, w, x, y, rgba):
    i = (y * w + x) * 4
    pixels[i : i + 4] = rgba


def _fill_rect(pixels, w, x0, y0, x1, y1, rgba):
    for y in range(y0, y1):
        for x in range(x0, x1):
            _px_set(pixels, w, x, y, rgba)


def _ellipse(pixels, w, cx, cy, rx, ry, rgba):
    for y in range(max(0, cy - ry), min(cy + ry, w)):
        for x in range(max(0, cx - rx), min(cx + rx, w)):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                _px_set(pixels, w, x, y, rgba)


def _commit_image(name, w, h, pixels):
    if name in bpy.data.images:
        img = bpy.data.images[name]
        bpy.data.images.remove(img)
    img = bpy.data.images.new(name, w, h, alpha=True)
    img.pixels = pixels
    img.pack()
    return img


def _srgb_hex(h):
    return tuple(srgb_to_lin(((h >> s) & 255) / 255) for s in (16, 8, 0))


def make_face_texture():
    w = h = 256
    px = [0.0] * (w * h * 4)
    skin = _srgb_hex(0xf3d0b0)
    skin_d = _srgb_hex(0xe8b898)
    base = (*skin, 1.0)
    _fill_rect(px, w, 0, 0, w, h, base)
  # 腮紅
    blush = (*_srgb_hex(0xf0a8a8), 0.35)
    _ellipse(px, w, 70, 138, 22, 14, blush)
    _ellipse(px, w, 186, 138, 22, 14, blush)
    # 眉毛
    brow = (*_srgb_hex(0x5a4038), 1.0)
    _fill_rect(px, w, 68, 108, 98, 118, brow)
    _fill_rect(px, w, 158, 108, 188, 118, brow)
    # 眼白
    white = (1.0, 1.0, 1.0, 1.0)
    _ellipse(px, w, 82, 128, 24, 18, white)
    _ellipse(px, w, 174, 128, 24, 18, white)
    # 虹膜
    iris = (*_srgb_hex(0x6a8098), 1.0)
    _ellipse(px, w, 84, 130, 14, 16, iris)
    _ellipse(px, w, 176, 130, 14, 16, iris)
    pupil = (*_srgb_hex(0x1a1828), 1.0)
    _ellipse(px, w, 86, 132, 7, 9, pupil)
    _ellipse(px, w, 178, 132, 7, 9, pupil)
    # 高光
    hi = (1.0, 1.0, 1.0, 0.95)
    _ellipse(px, w, 90, 122, 5, 4, hi)
    _ellipse(px, w, 182, 122, 5, 4, hi)
    # 睫毛線
    lash = (*_srgb_hex(0x2a1828), 1.0)
    _fill_rect(px, w, 62, 104, 102, 110, lash)
    _fill_rect(px, w, 154, 104, 194, 110, lash)
    # 鼻子陰影
    nose = (*skin_d, 0.55)
    _ellipse(px, w, 128, 142, 5, 4, nose)
    # 嘴唇
    lip = (*_srgb_hex(0xe88898), 1.0)
    _ellipse(px, w, 128, 162, 16, 7, lip)
    lip_hi = (*_srgb_hex(0xf8b0b8), 0.8)
    _ellipse(px, w, 128, 160, 12, 3, lip_hi)
    return _commit_image('npc_face_shared', w, h, px)


def _noise_band(px, w, h, y0, y1, c1, c2, stripes=0):
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0)
        r = c1[0] * (1 - t) + c2[0] * t
        g = c1[1] * (1 - t) + c2[1] * t
        b = c1[2] * (1 - t) + c2[2] * t
        for x in range(w):
            n = 0.02 * math.sin(x * 0.4 + y * 0.2)
            if stripes and (x // stripes) % 2:
                n += 0.04
            _px_set(px, w, x, y, (min(1, r + n), min(1, g + n), min(1, b + n), 1.0))


def make_hair_texture(style, hex_rgb):
    w = h = 128
    px = [0.0] * (w * h * 4)
    c1 = _srgb_hex(hex_rgb)
    c2 = tuple(max(0, c - 0.08) for c in c1)
    _noise_band(px, w, h, 0, h, c1, c2, stripes=8 if style in ('student', 'tech') else 0)
    hi = tuple(min(1, c + 0.12) for c in c1)
    _fill_rect(px, w, 0, h - 18, w, h, (*hi, 1.0))
    return _commit_image(f'npc_hair_{style}', w, h, px)


def make_outfit_texture(style, colors, pattern='solid'):
    w = h = 128
    px = [0.0] * (w * h * 4)
    c1 = _srgb_hex(colors[0])
    c2 = _srgb_hex(colors[1] if len(colors) > 1 else colors[0])
    _noise_band(px, w, h, 0, h, c1, c2)
    if pattern == 'floral':
        dot = srgba(colors[2] if len(colors) > 2 else 0xffffff, 0.9)
        for i in range(18):
            _ellipse(px, w, 12 + (i * 17) % 116, 10 + (i * 23) % 108, 4, 4, dot)
    elif pattern == 'pleats':
        for x in range(0, w, 6):
            shade = 0.92 if (x // 6) % 2 else 1.04
            for y in range(h):
                i = (y * w + x) * 4
                px[i] = min(1, px[i] * shade)
                px[i + 1] = min(1, px[i + 1] * shade)
                px[i + 2] = min(1, px[i + 2] * shade)
    elif pattern == 'tech':
        glow = _srgb_hex(0x30c8e8)
        for y in range(h):
            for x in range(w):
                line = 0.2 if (x + y) % 14 < 2 else 0.0
                i = (y * w + x) * 4
                px[i] = min(1, px[i] + glow[0] * line)
                px[i + 1] = min(1, px[i + 1] + glow[1] * line)
                px[i + 2] = min(1, px[i + 2] + glow[2] * line)
    elif pattern == 'gauze':
        for y in range(h):
            for x in range(w):
                i = (y * w + x) * 4
                px[i + 3] = 0.62
    elif pattern == 'frills':
        for y in range(0, h, 10):
            _fill_rect(px, w, 0, y, w, min(h, y + 3), srgba(0xfff0f8))
    return _commit_image(f'npc_outfit_{style}', w, h, px)


def tex_material(name, image, rough=0.68, emit=None, alpha_clip=False):
    if name in bpy.data.materials:
        return bpy.data.materials[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    tx = nt.nodes.new('ShaderNodeTexImage')
    tx.image = image
    tx.interpolation = 'Smart'
    nt.links.new(tx.outputs['Color'], bsdf.inputs['Base Color'])
    if alpha_clip:
        m.blend_method = 'CLIP'
        bsdf.inputs['Alpha'].default_value = 1.0
        nt.links.new(tx.outputs['Alpha'], bsdf.inputs['Alpha'])
    bsdf.inputs['Roughness'].default_value = rough
    if emit:
        bsdf.inputs['Emission Color'].default_value = hex_rgba(emit)
        bsdf.inputs['Emission Strength'].default_value = 0.35
    return m


def flat_material(name, hex_rgb, rough=0.7, emit=None):
    if name in bpy.data.materials:
        return bpy.data.materials[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = hex_rgba(hex_rgb)
    bsdf.inputs['Roughness'].default_value = rough
    if emit:
        bsdf.inputs['Emission Color'].default_value = hex_rgba(emit)
        bsdf.inputs['Emission Strength'].default_value = 0.8
    return m


class StyleMesh:
    """單一 mesh_{style}：多分件材質（≤6 primitive）。"""

    def __init__(self, style):
        self.style = style
        self.name = 'mesh_' + style
        self.bm = bmesh.new()
        self._uv = None
        self.materials = []

    def uv(self):
        if self._uv is None:
            self._uv = self.bm.loops.layers.uv.new('UVMap')
        return self._uv

    def mi(self, mat):
        if mat not in self.materials:
            self.materials.append(mat)
        return self.materials.index(mat)

    def tag(self, verts, mat):
        faces = {f for v in verts for f in v.link_faces}
        idx = self.mi(mat)
        for f in faces:
            f.material_index = idx

    def box(self, mat, sx, sy, sz, x, y, z, rx=0, ry=0, rz=0):
        m = (
            Matrix.Translation((x, y, z))
            @ Matrix.Rotation(rz, 4, 'Z')
            @ Matrix.Rotation(ry, 4, 'Y')
            @ Matrix.Rotation(rx, 4, 'X')
            @ Matrix.Diagonal((sx, sy, sz, 1.0))
        )
        r = bmesh.ops.create_cube(self.bm, size=1.0, matrix=m)
        self.tag(r['verts'], mat)

    def sphere(self, mat, r, x, y, z, sx=1.0, sy=1.0, sz=1.0, seg=10, rings=8):
        m = Matrix.Translation((x, y, z)) @ Matrix.Diagonal((sx, sy, sz, 1.0))
        res = bmesh.ops.create_uvsphere(
            self.bm, u_segments=seg, v_segments=rings, radius=r, matrix=m
        )
        self.tag(res['verts'], mat)

    def face_plate(self, mat, w, h, x, y, z, seg_u=6, seg_v=8):
        """朝 +Y 的法線臉板（glTF 正面）。"""
        uv = self.uv()
        dx, dz = w / 2, h / 2
        verts = []
        for j in range(seg_v + 1):
            for i in range(seg_u + 1):
                u = i / seg_u
                v = j / seg_v
                vx = -dx + w * u
                vz = -dz + h * v
                verts.append(self.bm.verts.new((x + vx, y, z + vz)))
        self.bm.verts.ensure_lookup_table()
        for j in range(seg_v):
            for i in range(seg_u):
                a = j * (seg_u + 1) + i
                f = self.bm.faces.new(
                    (verts[a], verts[a + 1], verts[a + seg_u + 2], verts[a + seg_u + 1])
                )
                f.material_index = self.mi(mat)
                for loop, (uu, vv) in zip(
                    f.loops,
                    ((i / seg_u, 1 - j / seg_v), ((i + 1) / seg_u, 1 - j / seg_v),
                     ((i + 1) / seg_u, 1 - (j + 1) / seg_v), (i / seg_u, 1 - (j + 1) / seg_v)),
                ):
                    loop[uv].uv = uu, vv
        return verts

    def taper_box(self, mat, w, h, d, x, y, z, rx=0, ry=0, rz=0, taper=0.7):
        self.box(mat, w, h, d * taper, x, y, z - d * 0.25, rx, ry, rz)

    def finish(self):
        bmesh.ops.remove_doubles(self.bm, verts=self.bm.verts, dist=1e-5)
        bmesh.ops.triangulate(self.bm, faces=self.bm.faces)
        self.bm.normal_update()
        me = bpy.data.meshes.new(self.name)
        self.bm.to_mesh(me)
        self.bm.free()
        self.bm = None
        for mat in self.materials:
            me.materials.append(mat)
        for p in me.polygons:
            p.use_smooth = True
        ob = bpy.data.objects.new(self.name, me)
        return ob