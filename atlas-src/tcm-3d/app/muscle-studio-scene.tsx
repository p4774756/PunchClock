import {useEffect, useRef, useCallback} from 'react';
import * as T from 'three';
import {OrbitControls} from 'three/examples/jsm/controls/OrbitControls.js';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import {DRACOLoader} from 'three/examples/jsm/loaders/DRACOLoader.js';
import {PointerTap} from './pointer-tap';
import {
 ANATOMY_MANIFEST,
 anatomyPartUrl,
 type AnatomyLayer,
 type AnatomyPart,
} from './anatomy-manifest';
import {muscleInfoForMesh, type MuscleInfo} from './muscle-catalog';
import {POINTS, type TeachingPoint} from './teaching';
import {CURRICULUM_SETS} from './teaching';
import {animeAvatar, avatarSupportedCodes} from './avatars';
import type {View} from './anatomy';
import type {AtlasLoadProgress} from './load-progress';

export type MuscleLayerStep = 'superficial' | 'deep' | 'bone';

export interface MusclePick {
 meshName: string;
 info: MuscleInfo | null;
}

interface Props {
 layer: MuscleLayerStep;
 showPoints: boolean;
 selectedPoint: string | null;
 view: View;
 rotate: boolean;
 resetToken: number;
 onMusclePick: (pick: MusclePick | null) => void;
 onPointPick: (code: string) => void;
 onProgress: (p: AtlasLoadProgress | number) => void;
 onError: (msg: string) => void;
}

const LAYER_OPACITY: Record<MuscleLayerStep, Record<AnatomyLayer, number>> = {
 superficial: {'muscle-superficial': 1, 'muscle-deep': 0, skeleton: 0},
 deep: {'muscle-superficial': 0.22, 'muscle-deep': 1, skeleton: 0},
 bone: {'muscle-superficial': 0.1, 'muscle-deep': 0.32, skeleton: 1},
};

const MUSCLE_COLOR = new T.Color('#9a4f4f');
const BONE_COLOR = new T.Color('#e8e2d4');

const anatomyCodes = () => {
 const avatar = animeAvatar('assistant');
 const base = avatarSupportedCodes(avatar);
 const codes = new Set<string>();
 for (const lesson of CURRICULUM_SETS) {
  if (lesson.id !== 'head-neck-21' && lesson.id !== 'trunk-30' && lesson.id !== 'common-10') continue;
  for (const c of lesson.codes) if (base.has(c) || lesson.id === 'common-10') codes.add(c);
 }
 return codes;
};

const pointList: TeachingPoint[] = (() => {
 const codes = anatomyCodes();
 return POINTS.filter(p => codes.has(p.code));
})();

export default function MuscleStudioScene({
 layer,
 showPoints,
 selectedPoint,
 view,
 rotate,
 resetToken,
 onMusclePick,
 onPointPick,
 onProgress,
 onError,
}: Props) {
 const mountRef = useRef<HTMLDivElement>(null);
 const stateRef = useRef<{
  renderer: T.WebGLRenderer;
  scene: T.Scene;
  camera: T.PerspectiveCamera;
  controls: OrbitControls;
  root: T.Group;
  parts: Map<string, T.Object3D>;
  markers: T.Mesh[];
  loaded: Set<string>;
  raf: number;
 } | null>(null);

 const applyLayer = useCallback((step: MuscleLayerStep) => {
  const st = stateRef.current;
  if (!st) return;
  const opacity = LAYER_OPACITY[step];
  for (const [id, grp] of st.parts) {
   const layerKey = id.includes('skeleton')
    ? 'skeleton'
    : id.includes('muscle-deep')
     ? 'muscle-deep'
     : 'muscle-superficial';
   const v = opacity[layerKey as AnatomyLayer];
   grp.visible = v > 0;
   grp.traverse(obj => {
    if (!(obj instanceof T.Mesh)) return;
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    for (const mat of mats) {
     if (!mat) continue;
     const m = mat as T.MeshStandardMaterial;
     m.transparent = v < 1;
     m.opacity = v;
     m.depthWrite = v > 0.45;
     if (layerKey === 'skeleton') m.color.copy(BONE_COLOR);
     else if (layerKey.startsWith('muscle')) m.color.copy(MUSCLE_COLOR);
    }
   });
  }
 }, []);

 useEffect(() => {
  applyLayer(layer);
 }, [layer, applyLayer]);

 const setCameraView = useCallback((v: View, controls: OrbitControls, cam: T.PerspectiveCamera) => {
  const dist = 2.1;
  const target = new T.Vector3(0, 1.05, 0);
  if (v === 'front') cam.position.set(0, 1.05, dist);
  else if (v === 'back') cam.position.set(0, 1.05, -dist);
  else if (v === 'side') cam.position.set(dist, 1.05, 0);
  else cam.position.set(dist * 0.75, 1.15, dist * 0.75);
  cam.lookAt(target);
  controls.target.copy(target);
  controls.update();
 }, []);

 useEffect(() => {
  const el = mountRef.current;
  if (!el) return;
  const renderer = new T.WebGLRenderer({antialias: true, alpha: true});
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(el.clientWidth, el.clientHeight);
  renderer.outputColorSpace = T.SRGBColorSpace;
  el.appendChild(renderer.domElement);

  const scene = new T.Scene();
  scene.background = new T.Color('#eef1ef');
  const camera = new T.PerspectiveCamera(42, el.clientWidth / el.clientHeight, 0.05, 40);
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.minDistance = 0.55;
  controls.maxDistance = 4.5;
  controls.target.set(0, 1.05, 0);
  setCameraView(view, controls, camera);

  const hemi = new T.HemisphereLight('#f5f8f5', '#7a8f86', 0.55);
  const key = new T.DirectionalLight('#ffffff', 0.72);
  key.position.set(1.2, 2.4, 1.6);
  const fill = new T.DirectionalLight('#dce8e4', 0.35);
  fill.position.set(-1.6, 0.6, -1.2);
  scene.add(hemi, key, fill);

  const root = new T.Group();
  scene.add(root);

  const draco = new DRACOLoader();
  draco.setDecoderPath('https://www.gstatic.com/draco/versioned/decoders/1.5.6/');
  const loader = new GLTFLoader();
  loader.setDRACOLoader(draco);

  const parts = new Map<string, T.Object3D>();
  const markers: T.Mesh[] = [];
  const loaded = new Set<string>();
  const sphere = new T.SphereGeometry(0.008, 12, 10);
  const dotMat = new T.MeshStandardMaterial({color: '#e9ae58', emissive: '#9a6a28', emissiveIntensity: 0.35});
  const activeMat = new T.MeshStandardMaterial({color: '#f0c56b', emissive: '#c18a32', emissiveIntensity: 0.5});

  for (const pt of pointList) {
   const side = pt.midline ? 1 : 1;
   const dot = new T.Mesh(sphere, dotMat.clone());
   dot.userData = {point: pt.code, side};
   dot.position.set(pt.position[0] * side, pt.position[1], pt.position[2]);
   dot.visible = showPoints;
   root.add(dot);
   markers.push(dot);
  }

  const loadPart = async (part: AnatomyPart) => {
   const url = anatomyPartUrl(part);
   const res = await fetch(url);
   if (!res.ok) throw new Error(`fetch ${part.id}`);
   let glbBuf: ArrayBuffer;
   if (part.url.endsWith('.gz')) {
    const ds = new DecompressionStream('gzip');
    glbBuf = await new Response(res.body!.pipeThrough(ds)).arrayBuffer();
   } else {
    glbBuf = await res.arrayBuffer();
   }
   const gltf = await new Promise<T.Group>((resolve, reject) => {
    loader.parse(glbBuf, '', g => resolve(g.scene), reject);
   });
   gltf.name = part.id;
   gltf.traverse(obj => {
    if (obj instanceof T.Mesh) {
     obj.material = new T.MeshStandardMaterial({
      color: part.layer === 'skeleton' ? BONE_COLOR : MUSCLE_COLOR,
      roughness: 0.72,
      metalness: 0.02,
     });
     obj.userData.muscleMesh = true;
    }
   });
   root.add(gltf);
   parts.set(part.id, gltf);
   loaded.add(part.id);
  };

  const total = ANATOMY_MANIFEST.parts.length;
  let done = 0;
  const tick = () => {
   const pct = total ? Math.min(100, Math.round((done / total) * 100)) : 100;
   onProgress({percent: pct, step: 'download', stepLabel: '載入解剖分層模型…'});
  };

  (async () => {
   try {
    const order = [
     ...ANATOMY_MANIFEST.parts.filter(p => p.layer === 'skeleton'),
     ...ANATOMY_MANIFEST.parts.filter(p => p.layer === 'muscle-superficial' && p.region === 'trunk'),
     ...ANATOMY_MANIFEST.parts.filter(
      p => !(p.layer === 'skeleton') && !(p.layer === 'muscle-superficial' && p.region === 'trunk'),
     ),
    ];
    for (const part of order.length ? order : ANATOMY_MANIFEST.parts) {
     await loadPart(part);
     done += 1;
     tick();
    }
    applyLayer(layer);
    onProgress(100);
   } catch (e) {
    onError('肌肉分層模型載入失敗，請稍後重試。');
   }
  })();

  const raycaster = new T.Raycaster();
  const pointer = new T.Vector2();
  const pick = (clientX: number, clientY: number) => {
   const rect = renderer.domElement.getBoundingClientRect();
   pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
   pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
   raycaster.setFromCamera(pointer, camera);
   const muscleHits = raycaster.intersectObjects([...parts.values()], true);
   const muscle = muscleHits.find(h => h.object.userData.muscleMesh);
   if (muscle) {
    const name = muscle.object.name || muscle.object.parent?.name || 'mesh';
    onMusclePick({meshName: name, info: muscleInfoForMesh(name)});
    return;
   }
   const pointHits = raycaster.intersectObjects(markers, false);
   if (pointHits[0]) {
    const code = pointHits[0].object.userData.point as string;
    onPointPick(code);
    onMusclePick(null);
    return;
   }
   onMusclePick(null);
  };

  const tap = new PointerTap();
  const onPointerDown = (e: PointerEvent) => {
   if (e.button !== 0) return;
   tap.down(e.pointerId, e.clientX, e.clientY, 14);
  };
  const onPointerMove = (e: PointerEvent) => tap.move(e.pointerId, e.clientX, e.clientY);
  const onPointerUp = (e: PointerEvent) => {
   if (tap.up(e.pointerId, e.clientX, e.clientY)) pick(e.clientX, e.clientY);
  };
  const onPointerCancel = (e: PointerEvent) => tap.cancel(e.pointerId);
  renderer.domElement.addEventListener('pointerdown', onPointerDown);
  renderer.domElement.addEventListener('pointermove', onPointerMove);
  renderer.domElement.addEventListener('pointerup', onPointerUp);
  renderer.domElement.addEventListener('pointercancel', onPointerCancel);

  stateRef.current = {renderer, scene, camera, controls, root, parts, markers, loaded, raf: 0};
  let auto = 0;
  const animate = () => {
   stateRef.current!.raf = requestAnimationFrame(animate);
   if (rotate) {
    auto += 0.004;
    root.rotation.y = auto;
   }
   controls.update();
   for (const m of markers) {
    const sel = m.userData.point === selectedPoint;
    (m.material as T.MeshStandardMaterial).color.set(sel ? '#f0c56b' : '#e9ae58');
   }
   renderer.render(scene, camera);
  };
  animate();

  const onResize = () => {
   const w = el.clientWidth;
   const h = el.clientHeight;
   camera.aspect = w / h;
   camera.updateProjectionMatrix();
   renderer.setSize(w, h);
  };
  window.addEventListener('resize', onResize);

  return () => {
   window.removeEventListener('resize', onResize);
   cancelAnimationFrame(stateRef.current?.raf ?? 0);
   renderer.domElement.removeEventListener('pointerdown', onPointerDown);
   renderer.domElement.removeEventListener('pointermove', onPointerMove);
   renderer.domElement.removeEventListener('pointerup', onPointerUp);
   renderer.domElement.removeEventListener('pointercancel', onPointerCancel);
   draco.dispose();
   renderer.dispose();
   el.removeChild(renderer.domElement);
   stateRef.current = null;
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
 }, []);

 useEffect(() => {
  const st = stateRef.current;
  if (!st) return;
  setCameraView(view, st.controls, st.camera);
 }, [view, setCameraView]);

 useEffect(() => {
  const st = stateRef.current;
  if (!st) return;
  st.root.rotation.y = 0;
  setCameraView(view, st.controls, st.camera);
 }, [resetToken, view, setCameraView]);

 useEffect(() => {
  const st = stateRef.current;
  if (!st) return;
  for (const m of st.markers) m.visible = showPoints;
 }, [showPoints]);

 return <div ref={mountRef} className="muscle-canvas-host" aria-label="肌肉分層 3D 視窗" />;
}