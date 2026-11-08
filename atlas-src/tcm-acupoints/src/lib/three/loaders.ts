import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { DRACOLoader } from "three/examples/jsm/loaders/DRACOLoader.js";
import { createBodyFigure } from "./body";

/** Target body space: height ≈ 1.72, origin near pelvis (y≈0), facing +Z. */
export const BODY_HEIGHT = 1.72;
const publicBase = import.meta.env.BASE_URL;
export const BODY_MODEL_URL = `${publicBase}models/body.glb`;

export type LoadedBody = {
  root: THREE.Group;
  meshes: THREE.Mesh[];
  source: "glb" | "procedural";
};

let loaderSingleton: GLTFLoader | null = null;

function getLoader() {
  if (loaderSingleton) return loaderSingleton;
  const draco = new DRACOLoader();
  draco.setDecoderPath(`${publicBase}draco/`);
  draco.preload();
  const loader = new GLTFLoader();
  loader.setDRACOLoader(draco);
  loaderSingleton = loader;
  return loader;
}

/**
 * Fit an arbitrary humanoid mesh into the acupoint coordinate frame used by
 * `acupoint-data.ts`: Y-up, pelvis near origin, crown ≈ +0.86, soles ≈ −0.86.
 */
export function fitBodyToAcupointSpace(model: THREE.Object3D): THREE.Group {
  const wrapper = new THREE.Group();
  wrapper.name = "body-fitted";
  wrapper.add(model);

  model.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(model);
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  model.position.sub(center);

  const tallest = Math.max(size.y, size.x, size.z) || 1;
  const scale = BODY_HEIGHT / tallest;
  model.scale.setScalar(scale);

  model.updateMatrixWorld(true);
  const box2 = new THREE.Box3().setFromObject(model);
  const center2 = box2.getCenter(new THREE.Vector3());
  const size2 = box2.getSize(new THREE.Vector3());
  model.position.y += -center2.y;
  model.position.y += size2.y * 0.02;

  return wrapper;
}

/** Unified jade-skin look so meridians read through the silhouette. */
export function styleBodyMaterials(root: THREE.Object3D) {
  const skin = new THREE.MeshPhysicalMaterial({
    color: 0xc9b5a0,
    roughness: 0.58,
    metalness: 0.04,
    transmission: 0.12,
    thickness: 0.4,
    transparent: true,
    opacity: 0.88,
    side: THREE.DoubleSide,
    depthWrite: true,
  });

  root.traverse((obj) => {
    if (!(obj instanceof THREE.Mesh)) return;
    obj.castShadow = false;
    obj.receiveShadow = false;
    const prev = obj.material;
    const tint = new THREE.Color(0xc9b5a0);
    if (prev && !Array.isArray(prev) && "color" in prev && prev.color instanceof THREE.Color) {
      tint.copy(prev.color).lerp(new THREE.Color(0xc9b5a0), 0.65);
    }
    const mat = skin.clone();
    mat.color.copy(tint);
    obj.material = mat;
    obj.frustumCulled = true;
  });
}

export function collectMeshes(root: THREE.Object3D): THREE.Mesh[] {
  const meshes: THREE.Mesh[] = [];
  root.traverse((obj) => {
    if (obj instanceof THREE.Mesh && obj.geometry?.getAttribute("position")) {
      meshes.push(obj);
    }
  });
  return meshes;
}

export async function loadBodyModel(
  onProgress?: (ratio: number) => void,
): Promise<LoadedBody> {
  try {
    const gltf = await new Promise<{ scene: THREE.Group }>((resolve, reject) => {
      getLoader().load(
        BODY_MODEL_URL,
        (result) => resolve(result),
        (event) => {
          if (event.total > 0) onProgress?.(event.loaded / event.total);
        },
        reject,
      );
    });

    const fitted = fitBodyToAcupointSpace(gltf.scene);
    styleBodyMaterials(fitted);
    const meshes = collectMeshes(fitted);
    onProgress?.(1);
    return { root: fitted, meshes, source: "glb" };
  } catch (error) {
    console.warn("[body] GLB load failed, falling back to procedural figure", error);
    const root = createBodyFigure();
    return { root, meshes: collectMeshes(root), source: "procedural" };
  }
}
