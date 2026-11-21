"""
Export Z-Anatomy Startup.blend → regional GLBs (skeleton + superficial / deep muscles).
Run: blender -b work/z-anatomy/Z-Anatomy/Startup.blend -P scripts/export-z-anatomy-blender.py
"""
import bpy
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "public", "models", "anatomy", "raw")

SKELETON_ROOT = "1: Skeletal system"
MUSCLE_ROOT = "4: Muscular system"

EXCLUDE_NAME = re.compile(
    r"(kidney|renal|nephro|cochlea|vestibule|malleus|incus|stapes|labyrinth|"
    r"inner ear|organ of corti|tympanic|semicircular|utricle|saccule)",
    re.I,
)

REGIONS = ("head-neck", "trunk", "arm", "leg")
LAYERS = ("skeleton", "muscle-superficial", "muscle-deep")


def coll_objects(coll, acc=None):
    acc = acc or set()
    if not coll:
        return acc
    for obj in coll.objects:
        if obj.type == "MESH":
            acc.add(obj)
    for child in coll.children:
        coll_objects(child, acc)
    return acc


def object_collections(obj):
    return [c.name for c in bpy.data.collections if obj.name in c.objects]


def mesh_center(obj):
    world = obj.matrix_world
    coords = [world @ v.co for v in obj.data.vertices]
    if not coords:
        return (0.0, 0.0, 0.0)
    n = len(coords)
    return (sum(v.x for v in coords) / n, sum(v.y for v in coords) / n, sum(v.z for v in coords) / n)


# Upper limb is classified by TA name. A center-only split put the humerus and
# deltoid into head-neck (high Z) and the radius, ulna, and hand into trunk.
ARM_BONE_RE = re.compile(
    r"^(Humerus|Radius|Ulna|Clavicle|Scapula)\.[lr]$"
    r"|metacarpal bone\.[lr]$"
    r"|phalanx of (?:first|second|third|fourth|fifth) finger of hand\.[lr]$"
    r"|^(?:Capitate|Hamate|Lunate|Pisiform|Scaphoid|Trapezium|Trapezoid|Triquetrum) bone\.[lr]$",
    re.I,
)
ARM_SOFT_RE = re.compile(
    r"deltoid|biceps brachii|triceps brachii|brachialis|coracobrachialis|brachioradialis|anconeus|"
    r"antebrachial fascia|brachial fascia|intermuscular septum of arm|"
    r"of hand|metacarpal ligament|palmar aponeurosis|retinaculum of wrist|"
    r"flexor carpi|extensor carpi|flexor digitorum|extensor digitorum|flexor digiti|extensor digiti|"
    r"flexor pollicis|extensor pollicis|abductor pollicis|adductor pollicis|opponens|"
    r"palmaris longus|pronator|supinator|interossei|lumbrical|"
    r"bicipitoradial|subdeltoid|coracobrachial bursa|subacromial bursa|subcutaneous acromial|"
    r"infraspinatus|subscapularis|supraspinatus|teres major|teres minor|intertubercular tendon|"
    r"tendon sheath",
    re.I,
)
ARM_DENY_RE = re.compile(
    r"femoris|gastrocnemius|gluteus|sartorius|pectoralis|trapezius|latissimus|"
    r"levator scapulae|subclavius|of foot|digitorum longus|digitorum brevis|"
    r"hallucis|fibularis|tibialis|plantar",
    re.I,
)


def region_for_center(x, y, z):
    # Z-Anatomy world: Z is superior–inferior (probe ≈ −0.07…1.71).
    if z > 1.38:
        return "head-neck"
    if z > 1.02 and abs(x) < 0.22:
        return "head-neck"
    if z < 0.42:
        return "leg"
    if abs(x) > 0.34 and 0.38 < z < 1.42:
        return "arm"
    if abs(y) > 0.14 and abs(x) > 0.28 and 0.5 < z < 1.35:
        return "arm"
    return "trunk"


def region_for_object(obj):
    name = obj.name or ""
    if not ARM_DENY_RE.search(name) and (ARM_BONE_RE.search(name) or ARM_SOFT_RE.search(name)):
        return "arm"
    return region_for_center(*mesh_center(obj))


def muscle_depth(obj, colls):
    path = " ".join(colls).lower()
    if "superficial" in path:
        return "muscle-superficial"
    if "deep" in path:
        return "muscle-deep"
    n = obj.name.lower()
    if any(k in n for k in ("trapezius", "deltoid", "pectoralis major", "gastrocnemius", "sartorius", "biceps brachii", "tibialis anterior")):
        return "muscle-superficial"
    return "muscle-deep"


def export_objects(objects, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    selected = []
    for obj in objects:
        if obj.name not in bpy.context.view_layer.objects:
            continue
        obj.hide_set(False)
        obj.hide_viewport = False
        obj.select_set(True)
        selected.append(obj)
    if not selected:
        return False
    bpy.ops.export_scene.gltf(
        filepath=path,
        export_format="GLB",
        use_selection=True,
        export_yup=True,
        export_apply=True,
        export_materials="NONE",
        export_draco_mesh_compression_enable=True,
        export_draco_mesh_compression_level=10,
        export_draco_position_quantization=12,
        export_draco_normal_quantization=8,
    )
    for obj in selected:
        obj.select_set(False)
    return True


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    skel = coll_objects(bpy.data.collections.get(SKELETON_ROOT))
    musc = coll_objects(bpy.data.collections.get(MUSCLE_ROOT))
    layer_names = set(bpy.context.view_layer.objects.keys())
    allowed = [
        o for o in skel | musc
        if not EXCLUDE_NAME.search(o.name) and o.name in layer_names
    ]

    buckets = {r: {layer: [] for layer in LAYERS} for r in REGIONS}
    for obj in allowed:
        layer = "skeleton" if obj in skel else muscle_depth(obj, object_collections(obj))
        reg = region_for_object(obj)
        buckets[reg][layer].append(obj)

    manifest_parts = []

    for reg in REGIONS:
        for layer in LAYERS:
            objs = buckets[reg][layer]
            if not objs:
                continue
            fname = f"{reg}-{layer}.glb"
            fpath = os.path.join(OUT_DIR, fname)
            if not export_objects(objs, fpath):
                print(f"skip empty {fname}")
                continue
            manifest_parts.append({
                "id": f"{reg}-{layer}",
                "rawFile": fname,
                "region": reg,
                "layer": layer,
                "meshCount": len(objs),
                "bytes": os.path.getsize(fpath),
            })
            print(f"Wrote {fname} {manifest_parts[-1]['bytes']} B meshes={len(objs)}")

    manifest_path = os.path.join(ROOT, "data", "anatomy-export-manifest.json")
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump({"parts": manifest_parts}, f, indent=2)
    print("done", len(manifest_parts), "files")


if __name__ == "__main__":
    main()