"""針灸銅人底模準備：MakeHuman CC0 base mesh → 成年女性、修長、雙臂微張掌心向前 → GLB。

素材來源（皆 CC0 1.0，見 tools/blender/assets/bronze-base/SOURCE.txt）：
  https://github.com/makehumancommunity/makehuman  makehuman/data/
    3dobjs/base.obj, rigs/default.mhskel, rigs/default_weights.mhw,
    targets/macrodetails/*.target（female-young、minweight、height、proportions）

無頭執行：
  ~/.local/bin/blender -b --factory-startup -P tools/blender/prep_bronze_base_mesh.py -- <mh_dir> <out.glb> [preview_dir]
輸出：Blender Z-up、腳底 z=0、正面 -Y（glTF 匯出後為 Three.js Y-up、正面 +Z），身高 DATA_BODY_HEIGHT=1.72。
"""
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
MH = argv[0] if argv else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'assets', 'bronze-base', 'mh-src')
OUT = argv[1] if len(argv) > 1 else os.path.join(MH, 'bronze-base-female.glb')
PREVIEW = argv[2] if len(argv) > 2 else None

TARGET_HEIGHT = 1.72
ARM_FROM_VERTICAL_DEG = float(os.environ.get('ARM_DEG', '13'))
PALM_TWIST_DEG = float(os.environ.get('TWIST_DEG', '-60'))
KEEP_GROUPS = {'body', 'helper-l-eye', 'helper-r-eye'}

TARGETS = [
    ('caucasian-female-young.target', 0.5),
    ('asian-female-young.target', 0.5),
    ('universal-female-young-averagemuscle-minweight.target', 0.45),
    ('female-young-averagemuscle-averageweight-maxheight.target', 0.35),
    ('female-young-averagemuscle-averageweight-idealproportions.target', 1.0),
]


def parse_obj(path):
    vs, vts, faces = [], [], []
    grp = None
    with open(path) as f:
        for line in f:
            if line.startswith('v '):
                vs.append([float(x) for x in line.split()[1:4]])
            elif line.startswith('vt '):
                vts.append([float(x) for x in line.split()[1:3]])
            elif line.startswith('g '):
                grp = line.split(None, 1)[1].strip()
            elif line.startswith('f '):
                vi, ti = [], []
                for tok in line.split()[1:]:
                    p = tok.split('/')
                    vi.append(int(p[0]) - 1)
                    ti.append(int(p[1]) - 1 if len(p) > 1 and p[1] else -1)
                faces.append((grp, vi, ti))
    return np.array(vs, dtype=np.float64), np.array(vts, dtype=np.float64), faces


def apply_target(V, path, w):
    with open(path) as f:
        for line in f:
            if not line.strip() or line.startswith('#'):
                continue
            p = line.split()
            i = int(p[0])
            V[i] += w * np.array([float(p[1]), float(p[2]), float(p[3])])


def rot(axis, deg):
    axis = np.asarray(axis, dtype=np.float64)
    axis = axis / np.linalg.norm(axis)
    return np.array(Matrix.Rotation(math.radians(deg), 3, Vector(axis.tolist())))


def main():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    V, VT, faces = parse_obj(os.path.join(MH, 'base.obj'))
    for name, w in TARGETS:
        apply_target(V, os.path.join(MH, name), w)
    skel = json.load(open(os.path.join(MH, 'default.mhskel')))
    weights = json.load(open(os.path.join(MH, 'default_weights.mhw')))['weights']

    def joint(name):
        return V[skel['joints'][name]].mean(axis=0)

    def bone_w(names):
        W = np.zeros(len(V))
        for n in names:
            for vi, wt in weights.get(n, []):
                W[vi] += wt
        return np.clip(W, 0, 1)

    for side, sgn in (('L', 1.0), ('R', -1.0)):
        hand = ['wrist.' + side] + [f'metacarpal{i}.{side}' for i in range(1, 5)] + \
            [f'finger{a}-{b}.{side}' for a in range(1, 6) for b in range(1, 4)]
        arm_w = bone_w([f'upperarm01.{side}', f'upperarm02.{side}', f'lowerarm01.{side}', f'lowerarm02.{side}'] + hand)
        twist_w = np.clip(bone_w([f'lowerarm01.{side}']) * 0.2 + bone_w([f'lowerarm02.{side}']) * 0.7 + bone_w(hand), 0, 1)
        sh = joint(f'upperarm01.{side}____head')
        el = joint(f'lowerarm01.{side}____head')
        wr = joint(f'wrist.{side}____head')
        # 0) 伸直手肘（MakeHuman 預設前臂前屈），保留約 4°
        fore_w = np.clip(bone_w([f'lowerarm01.{side}']) * 0.85 + bone_w([f'lowerarm02.{side}']) + bone_w(hand), 0, 1)
        u = (el - sh) / np.linalg.norm(el - sh)
        f = (wr - el) / np.linalg.norm(wr - el)
        ax = np.cross(f, u)
        if np.linalg.norm(ax) > 1e-6:
            ang = math.degrees(math.acos(float(np.clip(np.dot(f, u), -1, 1)))) - 4.0
            if ang > 0:
                Re = rot(ax, ang)
                V = V + fore_w[:, None] * (((V - el) @ Re.T + el) - V)
                print(f'[prep] elbow {side}: straightened {ang:.1f}°')
        wr = joint(f'wrist.{side}____head')
        # 1) 前臂旋外：掌心朝前
        Rt = rot(wr - el, PALM_TWIST_DEG * sgn)
        V = V + twist_w[:, None] * (((V - el) @ Rt.T + el) - V)
        # 2) 整臂放下到距垂直 ARM_FROM_VERTICAL_DEG
        d = wr - sh
        cur = math.degrees(math.atan2(abs(d[0]), -d[1]))  # 距垂直角度
        delta = cur - ARM_FROM_VERTICAL_DEG
        Ra = rot([0, 0, 1], -delta * sgn)
        V = V + arm_w[:, None] * (((V - sh) @ Ra.T + sh) - V)
        print(f'[prep] arm {side}: was {cur:.1f}° from vertical, lowered by {delta:.1f}°')

    keep = [(vi, ti) for g, vi, ti in faces if g in KEEP_GROUPS]
    used = sorted({i for vi, _ in keep for i in vi})
    remap = {o: n for n, o in enumerate(used)}
    Vk = V[used]
    ymin, ymax = Vk[:, 1].min(), Vk[:, 1].max()
    s = TARGET_HEIGHT / (ymax - ymin)
    # MH: Y-up, 正面 +Z → Blender: (x, -z, y)
    co = np.stack([Vk[:, 0] * s, -Vk[:, 2] * s, (Vk[:, 1] - ymin) * s], axis=1)
    co[:, 0] -= (co[:, 0].max() + co[:, 0].min()) / 2
    me = bpy.data.meshes.new('BronzeBaseFemale')
    me.from_pydata(co.tolist(), [], [[remap[i] for i in vi] for vi, _ in keep])
    uv = me.uv_layers.new(name='UVMap')
    li = 0
    for vi, ti in keep:
        for t in ti:
            if t >= 0:
                uv.data[li].uv = VT[t].tolist()
            li += 1
    me.update()
    ob = bpy.data.objects.new('BronzeBaseFemale', me)
    bpy.context.scene.collection.objects.link(ob)
    for p in me.polygons:
        p.use_smooth = True
    print(f'[prep] verts={len(me.vertices)} faces={len(me.polygons)} height={co[:,2].max():.3f} width={co[:,0].max()-co[:,0].min():.3f}')
    os.makedirs(os.path.dirname(os.path.abspath(OUT)), exist_ok=True)
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True)
    bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', use_selection=True, export_apply=True)
    print('[prep] wrote', OUT)
    if PREVIEW:
        render_previews(ob, PREVIEW)


def render_previews(ob, outdir):
    os.makedirs(outdir, exist_ok=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.samples = 24
    sc.cycles.device = 'CPU'
    sc.render.resolution_x = 640
    sc.render.resolution_y = 900
    mat = bpy.data.materials.new('prevBronze')
    mat.use_nodes = True
    b = mat.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (0.75, 0.52, 0.25, 1)
    b.inputs['Metallic'].default_value = 0.35
    b.inputs['Roughness'].default_value = 0.45
    ob.data.materials.append(mat)
    world = bpy.data.worlds.new('w')
    sc.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes['Background']
    bg.inputs[0].default_value = (0.06, 0.10, 0.25, 1)
    bg.inputs[1].default_value = 1.0
    sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
    sun.data.energy = 3
    sun.rotation_euler = (math.radians(50), 0, math.radians(30))
    sc.collection.objects.link(sun)
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    sc.collection.objects.link(cam)
    sc.camera = cam
    for name, pos, look, lens in [
        ('front', (0, -4.2, 0.95), (0, 0, 0.88), 50),
        ('side', (4.2, 0, 0.95), (0, 0, 0.88), 50),
        ('face', (0, -0.9, 1.6), (0, 0, 1.58), 70),
        ('hand', (0.5, -1.4, 0.85), (0.25, 0, 0.85), 60),
    ]:
        cam.location = pos
        cam.data.lens = lens
        dvec = Vector(look) - Vector(pos)
        cam.rotation_euler = dvec.to_track_quat('-Z', 'Y').to_euler()
        sc.render.filepath = os.path.join(outdir, name + '.png')
        bpy.ops.render.render(write_still=True)


main()
