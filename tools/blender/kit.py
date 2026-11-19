"""PunchClock 島嶼建築共用工具：部件、格扇、屋頂、調色盤、匯出。

座標約定（匯出後 glTF / Three.js）：原點 = 佔地中心、y=0 為地面。
Blender 內 Z 朝上；本地 +Z（門／正面）在 Blender 裡是 -Y（glTF 匯出會把 -Y 轉成 +Z）。
佔地數字對齊 island-layout.js 的 BUILDINGS。
"""
from __future__ import annotations

import math
import os
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

# ---------- 調色盤（sRGB hex；匯出時轉 linear）----------
# 發光材質名稱必須是 windows_glow / lantern_glow / neon_glow，code 端依此換 mat()。
PALETTE = {
    'stone':          (0xc9c1b3, None),
    'stone_dark':     (0x9a9184, None),
    'paving':         (0xe4d6bd, None),
    'wall_red':       (0xb4483a, None),
    'pillar_red':     (0xa8302a, None),
    'wood':           (0x5e3424, None),
    'beam_teal':      (0x3f7d74, None),
    'gold':           (0xd4a84b, None),
    'roof_tile':      (0x617488, None),
    'roof_tile_dark': (0x56687c, None),
    'roof_ridge':     (0x3b4654, None),
    'windows_glow':   (0xf6e3bd, 0xffb15a),
    'lantern_glow':   (0xd8432f, 0xff7a3a),
    'neon_glow':      (0xf0d078, 0xffc56a),
    'sign':           (0x3a2620, None),
    'plant':          (0x5f9e5a, None),
    'pot':            (0x8a5a3c, None),
    'herb':           (0xb7a35a, None),
    # 手搖店
    'shop_wall':      (0xfff7ef, None),
    'shop_trim':      (0xf3e2cc, None),
    'shop_orange':    (0xf06a2a, None),
    'shop_navy':      (0x1f4d73, None),
    'shop_navy_dark': (0x17385a, None),
    'shop_wood':      (0xc4844a, None),
    'shop_counter':   (0xf3e2cc, None),
    'cup_pink':       (0xf4a0c0, None),
    'cup_yellow':     (0xf6d35a, None),
    'cup_green':      (0x9fd9b4, None),
    'cup_lid':        (0xfffaf6, None),
    'straw':          (0x3a312c, None),
    # 賭場
    'cas_wall':       (0x5b3a8c, None),
    'cas_dark':       (0x3d2468, None),
    'cas_roof':       (0x2a1848, None),
    'cas_purple':     (0x7a54b5, None),
    'cas_carpet':     (0x9c2b3a, None),
    'cas_gold_light': (0xf0d078, None),
    # 研發樓
    'lab_steel':      (0x5d7384, None),
    'lab_steel_dark': (0x24384c, None),
    'lab_frame':      (0x3a4a58, None),
    'lab_panel':      (0xd7e6ee, None),
    'lab_glass':      (0xb7e4ff, 0x7ec8ea),
    'lab_accent':     (0x7ec8ea, None),
    'lab_ground':     (0x8aa0b0, None),
    'lab_cable':      (0x3a312c, None),
    'lab_grey':       (0x6a7a88, None),
    # 中醫館銅像
    'bronze':         (0xb08a3c, None),
    'bronze_dark':    (0x6e4e22, None),
    # 棕櫚
    'palm_leaf':      (0x4f8a45, None),
    'umbrella_yellow':(0xf6d35a, None),
}


def srgb_to_lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def hex_rgba(h):
    return tuple(srgb_to_lin(((h >> s) & 255) / 255) for s in (16, 8, 0)) + (1.0,)


_MATS = {}
_PALETTE_OVERRIDES = {}


def set_palette_overrides(d=None):
    """單棟建築覆寫調色（每個 GLB 分開匯出，所以同名 windows_glow 可以有不同底色）。"""
    global _PALETTE_OVERRIDES
    _PALETTE_OVERRIDES = dict(d or {})
    for k in list(_MATS):
        _MATS.pop(k, None)


def material(name):
    if name in _MATS and _MATS[name].users >= 0 and _MATS[name].name in bpy.data.materials:
        return _MATS[name]
    if name not in PALETTE and name not in _PALETTE_OVERRIDES:
        raise KeyError(f'未知材質 {name}，請先加進 PALETTE')
    base, emis = _PALETTE_OVERRIDES.get(name, PALETTE[name])
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = hex_rgba(base)
    bsdf.inputs['Roughness'].default_value = 0.85
    bsdf.inputs['Metallic'].default_value = 0.15 if name in ('gold', 'lab_steel', 'lab_steel_dark', 'lab_frame', 'lab_accent') else 0.0
    if emis is not None:
        bsdf.inputs['Emission Color'].default_value = hex_rgba(emis)
        bsdf.inputs['Emission Strength'].default_value = 1.0
    _MATS[name] = m
    return m


def clear_scene():
    """清空物件、網格、材質，重設材質快取。給同一行程連續建多棟用。"""
    global _MATS
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for block in (bpy.data.meshes, bpy.data.materials, bpy.data.cameras, bpy.data.lights):
        for item in list(block):
            block.remove(item)
    _MATS = {}
    _PALETTE_OVERRIDES = {}


def prepare_scene():
    """factory-startup 後刪掉預設 Cube／Camera／Light。"""
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


class Part:
    def __init__(self, name):
        self.name = name
        self.bm = bmesh.new()
        self.mats = []
        self._uv = None

    def mi(self, mat):
        if mat not in self.mats:
            self.mats.append(mat)
        return self.mats.index(mat)

    def _tag(self, faces, mat):
        idx = self.mi(mat)
        for f in faces:
            f.material_index = idx

    def _faces_of(self, verts):
        return {f for v in verts for f in v.link_faces}

    def box(self, mat, sx, sy, sz, x, y, z, rz=0.0, rx=0.0, ry=0.0):
        m = (Matrix.Translation((x, y, z))
             @ Matrix.Rotation(rz, 4, 'Z')
             @ Matrix.Rotation(ry, 4, 'Y')
             @ Matrix.Rotation(rx, 4, 'X')
             @ Matrix.Diagonal((sx, sy, sz, 1)))
        r = bmesh.ops.create_cube(self.bm, size=1.0, matrix=m)
        self._tag(self._faces_of(r['verts']), mat)

    def cyl(self, mat, r1, r2, h, x, y, z, seg=8, rx=0.0, ry=0.0, rz=0.0):
        m = (Matrix.Translation((x, y, z))
             @ Matrix.Rotation(rz, 4, 'Z')
             @ Matrix.Rotation(ry, 4, 'Y')
             @ Matrix.Rotation(rx, 4, 'X'))
        r = bmesh.ops.create_cone(self.bm, cap_ends=True, segments=seg,
                                  radius1=r1, radius2=r2, depth=h, matrix=m)
        self._tag(self._faces_of(r['verts']), mat)

    def sphere(self, mat, r, x, y, z, sz=1.0, seg=8, rings=5):
        m = Matrix.Translation((x, y, z)) @ Matrix.Diagonal((1, 1, sz, 1))
        res = bmesh.ops.create_uvsphere(self.bm, u_segments=seg, v_segments=rings,
                                        radius=r, matrix=m)
        self._tag(self._faces_of(res['verts']), mat)

    def cone(self, mat, r, h, x, y, z, seg=6, rx=0.0, ry=0.0, rz=0.0):
        self.cyl(mat, r, 0.0, h, x, y, z, seg=seg, rx=rx, ry=ry, rz=rz)

    def uv_layer(self):
        if self._uv is None:
            self._uv = self.bm.loops.layers.uv.new('UVMap')
        return self._uv

    def plane(self, mat, w, h, x, y, z, rx=0.0, ry=0.0, rz=0.0):
        """XZ 平面上寬 w、高 h、法線朝 -Y（正面）的單一面，帶 0..1 UV。"""
        dx, dz = w / 2, h / 2
        local = [(-dx, 0, -dz), (dx, 0, -dz), (dx, 0, dz), (-dx, 0, dz)]
        # glTF / Three.js 的 V=0 在圖上緣；這裡先把 V 對調，匯出後文字才是正的。
        uvs = [(0.0, 1.0), (1.0, 1.0), (1.0, 0.0), (0.0, 0.0)]
        m = (Matrix.Translation((x, y, z))
             @ Matrix.Rotation(rz, 4, 'Z')
             @ Matrix.Rotation(ry, 4, 'Y')
             @ Matrix.Rotation(rx, 4, 'X'))
        verts = [self.bm.verts.new(m @ Vector(c)) for c in local]
        face = self.bm.faces.new(verts)
        self._tag([face], mat)
        uv = self.uv_layer()
        for loop, uvco in zip(face.loops, uvs):
            loop[uv].uv = uvco
        return face

    def finish(self, parent):
        me = bpy.data.meshes.new(self.name)
        self.bm.to_mesh(me)
        self.bm.free()
        self.bm = None
        for m in self.mats:
            me.materials.append(material(m))
        for p in me.polygons:
            p.use_smooth = False
        ob = bpy.data.objects.new(self.name, me)
        bpy.context.scene.collection.objects.link(ob)
        ob.parent = parent
        return ob


class Builder:
    def __init__(self, name, w, d):
        self.name = name
        self.w = float(w)
        self.d = float(d)
        self.hx = self.w / 2
        self.hy = self.d / 2
        self.front = -self.hy  # Blender 前方（-Y）= glTF +Z
        self.parts = {}

    def part(self, name):
        if name not in self.parts:
            self.parts[name] = Part(name)
        return self.parts[name]

    def lattice_panel(self, x, z, w, h, y, nx=3, nz=4, normal='-y',
                      glow_part='windows_glow', lat_part='lattice'):
        """在 y（或 x）平面放一片發光紙面 + 木格。normal: '-y' 前、'+y' 後、'-x'/ '+x' 側。"""
        glow = self.part(glow_part)
        lat = self.part(lat_part)
        t = 0.022
        if normal in ('-y', '+y'):
            s = -1 if normal == '-y' else 1
            glow.box('windows_glow', w, 0.012, h, x, y + s * 0.035, z)
            lat.box('wood', w + 0.06, 0.03, 0.035, x, y + s * 0.05, z + h / 2 + 0.017)
            lat.box('wood', w + 0.06, 0.03, 0.035, x, y + s * 0.05, z - h / 2 - 0.017)
            lat.box('wood', 0.035, 0.03, h, x - w / 2 - 0.017, y + s * 0.05, z)
            lat.box('wood', 0.035, 0.03, h, x + w / 2 + 0.017, y + s * 0.05, z)
            for i in range(1, nx):
                lat.box('wood', t, 0.02, h, x - w / 2 + w * i / nx, y + s * 0.045, z)
            for j in range(1, nz):
                lat.box('wood', w, 0.02, t, x, y + s * 0.045, z - h / 2 + h * j / nz)
        else:
            s = -1 if normal == '-x' else 1
            glow.box('windows_glow', 0.012, w, h, x + s * 0.035, y, z)
            lat.box('wood', 0.03, w + 0.06, 0.035, x + s * 0.05, y, z + h / 2 + 0.017)
            lat.box('wood', 0.03, w + 0.06, 0.035, x + s * 0.05, y, z - h / 2 - 0.017)
            lat.box('wood', 0.03, 0.035, h, x + s * 0.05, y - w / 2 - 0.017, z)
            lat.box('wood', 0.03, 0.035, h, x + s * 0.05, y + w / 2 + 0.017, z)
            for i in range(1, nx):
                lat.box('wood', 0.02, t, h, x + s * 0.045, y - w / 2 + w * i / nx, z)
            for j in range(1, nz):
                lat.box('wood', 0.02, w, t, x + s * 0.045, y, z - h / 2 + h * j / nz)

    def framed_window(self, x, z, w, h, y, normal='-y', frame='shop_trim', glow='windows_glow',
                      glow_part='windows_glow', frame_part='frames', bar=True):
        """現代／店鋪用的簡單窗框 + 發光玻璃。"""
        g = self.part(glow_part)
        f = self.part(frame_part)
        ft = 0.045
        if normal in ('-y', '+y'):
            s = -1 if normal == '-y' else 1
            g.box(glow, w, 0.012, h, x, y + s * 0.03, z)
            f.box(frame, w + ft * 2, 0.04, ft, x, y + s * 0.05, z + h / 2 + ft / 2)
            f.box(frame, w + ft * 2, 0.04, ft, x, y + s * 0.05, z - h / 2 - ft / 2)
            f.box(frame, ft, 0.04, h, x - w / 2 - ft / 2, y + s * 0.05, z)
            f.box(frame, ft, 0.04, h, x + w / 2 + ft / 2, y + s * 0.05, z)
            if bar:
                f.box(frame, ft * 0.6, 0.03, h, x, y + s * 0.048, z)
        else:
            s = -1 if normal == '-x' else 1
            g.box(glow, 0.012, w, h, x + s * 0.03, y, z)
            f.box(frame, 0.04, w + ft * 2, ft, x + s * 0.05, y, z + h / 2 + ft / 2)
            f.box(frame, 0.04, w + ft * 2, ft, x + s * 0.05, y, z - h / 2 - ft / 2)
            f.box(frame, 0.04, ft, h, x + s * 0.05, y - w / 2 - ft / 2, z)
            f.box(frame, 0.04, ft, h, x + s * 0.05, y + w / 2 + ft / 2, z)
            if bar:
                f.box(frame, 0.03, ft * 0.6, h, x + s * 0.048, y, z)

    def hip_roof(self, part_name, z0, rise, ehx, ehy, rhx, rhy,
                 nx=14, ny=10, nr=7, lift=0.24, push=0.12,
                 tile='roof_tile', tile_dark='roof_tile_dark',
                 ridge_mat='roof_ridge', under='wood'):
        """歇山／廡殿曲面屋頂，回傳 rings（給屋脊裝飾用）。"""
        roof = self.part(part_name)

        def ring_points(hx, hy):
            pts = []
            for i in range(nx):
                pts.append((-hx + 2 * hx * i / nx, -hy))
            for i in range(ny):
                pts.append((hx, -hy + 2 * hy * i / ny))
            for i in range(nx):
                pts.append((hx - 2 * hx * i / nx, hy))
            for i in range(ny):
                pts.append((-hx, hy - 2 * hy * i / ny))
            return pts

        rings = []
        for r in range(nr + 1):
            t = r / nr
            hx = ehx + (rhx - ehx) * t
            hy = ehy + (rhy - ehy) * t
            z = z0 + rise * (t ** 1.75)
            ring = []
            for (px, py) in ring_points(hx, hy):
                c = (abs(px) / hx) ** 3 * (abs(py) / hy) ** 3 if hy > 1e-6 else 0
                c = c if t < 0.999 else 0
                k = (1 - t)
                lf = lift * c * k ** 1.6
                ps = push * c * k
                ln = math.hypot(px, py) or 1
                ring.append(roof.bm.verts.new((px + px / ln * ps, py + py / ln * ps, z + lf)))
            rings.append(ring)
        n = len(rings[0])
        tile_a, tile_b = roof.mi(tile), roof.mi(tile_dark)
        for r in range(nr):
            for j in range(n):
                a, b = rings[r][j], rings[r][(j + 1) % n]
                c, d = rings[r + 1][(j + 1) % n], rings[r + 1][j]
                f = roof.bm.faces.new((a, b, c, d))
                f.material_index = tile_a if j % 2 == 0 else tile_b
        top = roof.bm.faces.new(rings[-1])
        top.material_index = tile_a
        under_vs = [roof.bm.verts.new((v.co.x, v.co.y, v.co.z - 0.07)) for v in rings[0]]
        ridge_mi = roof.mi(ridge_mat)
        for j in range(n):
            f = roof.bm.faces.new((under_vs[j], under_vs[(j + 1) % n], rings[0][(j + 1) % n], rings[0][j]))
            f.material_index = ridge_mi
        bot = roof.bm.faces.new(list(reversed(under_vs)))
        bot.material_index = roof.mi(under)
        bmesh.ops.recalc_face_normals(roof.bm, faces=roof.bm.faces[:])
        return rings, nx, ny, nr

    def ridge_ornaments(self, rings, z0, rise, rhx, nx, ny, nr,
                        part_name='roof_ridge', ridge_mat='roof_ridge', accent='gold'):
        rid = self.part(part_name)
        ztop = z0 + rise
        rid.box(ridge_mat, 2 * rhx + 0.1, 0.09, 0.09, 0, 0, ztop + 0.04)
        rid.box(accent, 0.14, 0.1, 0.1, 0, 0, ztop + 0.1)
        rid.sphere(accent, 0.05, 0, 0, ztop + 0.19, seg=8, rings=5)
        for sx in (-1, 1):
            x = sx * (rhx + 0.05)
            rid.box(ridge_mat, 0.09, 0.1, 0.22, x, 0, ztop + 0.12, ry=sx * -0.18)
            rid.box(ridge_mat, 0.12, 0.08, 0.06, x - sx * 0.05, 0, ztop + 0.24)
        corner_idx = [0, nx, nx + ny, 2 * nx + ny]
        for ci in corner_idx:
            pts = [rings[r][ci].co.copy() for r in range(nr, -1, -1)]
            for a, b in zip(pts[:-1], pts[1:]):
                a = a + Vector((0, 0, 0.03))
                b = b + Vector((0, 0, 0.03))
                mid = (a + b) / 2
                vec = b - a
                L = vec.length
                yaw = math.atan2(vec.y, vec.x)
                pitch = math.atan2(vec.z, math.hypot(vec.x, vec.y))
                rid.box(ridge_mat, L + 0.02, 0.06, 0.06, mid.x, mid.y, mid.z, rz=yaw, ry=-pitch)
            tip = pts[-1]
            rid.cyl(accent, 0.03, 0.0, 0.12, tip.x, tip.y, tip.z + 0.08, seg=5)

    def tilted_sign(self, width, height, y, z, rx=None, frame='gold', board='sign', facing='front'):
        """屋頂招牌。facing=front：法線朝 -Y 略仰（南簷）；back：朝 +Y 面北鏡頭（EXPLORE 下緣）。sign_face 帶 0..1 UV。"""
        import math as _math
        if rx is None:
            rx = 2.18 if facing == 'back' else -1.05
        fr = self.part('sign')
        face = self.part('sign_face')
        # (0,-1,0) 經 Rx(rx) → (0, -cos(rx), sin(rx))；front 沿用舊 -sin 以不動其它 GLB。
        if facing == 'back':
            nx, ny, nz = 0.0, -_math.cos(rx), _math.sin(rx)
        else:
            nx, ny, nz = 0.0, -_math.cos(rx), -_math.sin(rx)
        def along(x0, y0, z0, dist):
            return x0 + nx * dist, y0 + ny * dist, z0 + nz * dist
        fx, fy, fz = along(0, y, z, 0.012)
        bx, by, bz = along(0, y, z, 0.028)
        px, py, pz = along(0, y, z, 0.048)
        fr.box(frame, width + 0.10, 0.05, height + 0.10, fx, fy, fz, rx=rx)
        fr.box(board, width + 0.02, 0.02, height + 0.02, bx, by, bz, rx=rx)
        face.plane('sign', width, height, px, py, pz, rx=rx)
        return (0.0, z, -y)

    def roof_readable_sign(self, width, height, x, y, z, rx=None, ry=0.10, frame='lab_accent', board='sign'):
        """屋頂近水平招牌（字朝上），高俯視角可讀；併入既有 sign_face UV。"""
        import math as _math
        if rx is None:
            rx = _math.pi / 2 - 0.18
        fr = self.part('sign')
        face = self.part('sign_face')
        nx, ny, nz = 0.0, -_math.cos(rx), _math.sin(rx)
        def along(x0, y0, z0, dist):
            return x0 + nx * dist, y0 + ny * dist, z0 + nz * dist
        fx, fy, fz = along(x, y, z, 0.010)
        bx, by, bz = along(x, y, z, 0.024)
        px, py, pz = along(x, y, z, 0.040)
        fr.box(frame, width + 0.08, 0.04, height + 0.08, fx, fy, fz, rx=rx, ry=ry)
        fr.box(board, width + 0.02, 0.02, height + 0.02, bx, by, bz, rx=rx, ry=ry)
        face.plane('sign', width, height, px, py, pz, rx=rx, ry=ry)

    def finish(self, out_path, extras=None, label_anchor=None, door_local=None):
        root = bpy.data.objects.new(self.name, None)
        bpy.context.scene.collection.objects.link(root)
        for name, pt in self.parts.items():
            pt.finish(root)

        if 'sign_face' in bpy.data.objects:
            # plane() 已寫 UV；若沒有 UV 層就用 bounding box 補一個
            ob = bpy.data.objects['sign_face']
            me = ob.data
            if not me.uv_layers:
                uv = me.uv_layers.new(name='UVMap')
                xs = [v.co.x for v in me.vertices]
                zs = [v.co.z for v in me.vertices]
                dx = max(xs) - min(xs) or 1
                dz = max(zs) - min(zs) or 1
                for poly in me.polygons:
                    for li in poly.loop_indices:
                        v = me.vertices[me.loops[li].vertex_index].co
                        uv.data[li].uv = ((v.x - min(xs)) / dx, (v.z - min(zs)) / dz)

        if label_anchor is not None:
            empty = bpy.data.objects.new('label_anchor', None)
            empty.location = label_anchor  # Blender 座標
            empty.empty_display_size = 0.15
            empty.parent = root
            bpy.context.scene.collection.objects.link(empty)

        root['footprint_w'] = self.w
        root['footprint_d'] = self.d
        root['ground_y'] = 0.0
        if door_local is None:
            door_local = [0.0, 0.0, self.hy]
        root['door_local'] = list(door_local)
        if extras:
            for k, v in extras.items():
                root[k] = v

        os.makedirs(os.path.dirname(os.path.abspath(out_path)) or '.', exist_ok=True)
        bpy.ops.export_scene.gltf(
            filepath=out_path, export_format='GLB', use_selection=False,
            export_yup=True, export_apply=True, export_extras=True,
            export_texcoords=True, export_normals=True, export_materials='EXPORT',
            export_cameras=False, export_lights=False, export_animations=False,
        )
        tris = sum(
            len(p.vertices) - 2
            for o in bpy.data.objects if o.type == 'MESH'
            for p in o.data.polygons
        )
        size = os.path.getsize(out_path)
        print(f'EXPORTED {out_path} tris≈{tris} bytes={size}')
        return tris, size


def argv_out(default):
    if '--' in sys.argv:
        args = sys.argv[sys.argv.index('--') + 1:]
        if args:
            return args[0]
    return default
