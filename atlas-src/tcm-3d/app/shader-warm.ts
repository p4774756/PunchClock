import * as T from 'three';
import {yieldOnce, yieldSlice} from './load-progress';

/** Hide panorama / ground / platform / ring only — never lights or fog. */
export const hideSceneChrome = (scene: T.Scene) => {
  const saved: {obj: T.Object3D; visible: boolean}[] = [];
  scene.traverse(obj => {
    if (!obj.userData?.atlasSceneChrome) return;
    saved.push({obj, visible: obj.visible});
    obj.visible = false;
  });
  return () => saved.forEach(({obj, visible}) => {
    obj.visible = visible;
  });
};

export const materialProgramKey = (material: T.Material): string => {
  const custom =
    typeof material.customProgramCacheKey === 'function' ? material.customProgramCacheKey() : '';
  const defines = (material as {defines?: Record<string, unknown>}).defines;
  const defineKey = defines ? JSON.stringify(defines, Object.keys(defines).sort()) : '';
  return `${material.type}|${custom}|${defineKey}`;
};

export const collectRenderableMeshes = (root: T.Object3D): (T.Mesh | T.SkinnedMesh)[] => {
  const meshes: (T.Mesh | T.SkinnedMesh)[] = [];
  root.traverse(obj => {
    if ((obj instanceof T.Mesh || obj instanceof T.SkinnedMesh) && obj.geometry) meshes.push(obj);
  });
  return meshes;
};

/** One representative mesh per unique material program (type / defines / custom key). */
export const groupMeshesByProgram = (
  meshes: (T.Mesh | T.SkinnedMesh)[],
): {key: string; mesh: T.Mesh | T.SkinnedMesh}[] => {
  const groups = new Map<string, T.Mesh | T.SkinnedMesh>();
  for (const mesh of meshes) {
    const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
    for (const mat of mats) {
      if (!mat?.visible) continue;
      const key = materialProgramKey(mat);
      if (!groups.has(key)) groups.set(key, mesh);
    }
  }
  return [...groups.entries()].map(([key, mesh]) => ({key, mesh}));
};

export const programCount = (renderer: T.WebGLRenderer): number =>
  renderer.info?.programs?.length ?? 0;

const supportsParallelCompile = (renderer: T.WebGLRenderer): boolean => {
  try {
    return renderer.getContext().getExtension('KHR_parallel_shader_compile') != null;
  } catch {
    return false;
  }
};

const warmScissorRender = (renderer: T.WebGLRenderer, scene: T.Scene, camera: T.Camera) => {
  const scissorOn = renderer.getScissorTest();
  renderer.setScissorTest(true);
  renderer.setScissor(0, 0, 1, 1);
  renderer.render(scene, camera);
  renderer.setScissorTest(scissorOn);
};

const setRootMeshVisibility = (
  meshes: (T.Mesh | T.SkinnedMesh)[],
  visible: Set<T.Mesh | T.SkinnedMesh>,
) => {
  const saved = meshes.map(m => m.visible);
  for (const m of meshes) m.visible = visible.has(m);
  return () => meshes.forEach((m, i) => {
    m.visible = saved[i];
  });
};

export const uploadTexturesIncremental = async (opts: {
  renderer: T.WebGLRenderer;
  textures: T.Texture[];
  disposed: () => boolean;
  onSlice?: (index: number, total: number) => void;
}) => {
  const {renderer, textures, disposed, onSlice} = opts;
  for (let i = 0; i < textures.length; i++) {
    if (disposed()) break;
    try {
      renderer.initTexture(textures[i]);
    } catch {
      /* software WebGL */
    }
    onSlice?.(i + 1, textures.length);
    await yieldSlice();
  }
};

export type ShaderWarmResult = {programsBefore: number; programsAfter: number};

const filterWarmMeshes = (root: T.Object3D) =>
  collectRenderableMeshes(root).filter(
    m => !/Outline/i.test(m.name) && !/Outline/i.test((m.material as T.Material)?.name ?? ''),
  );

export const warmShadersIncremental = async (opts: {
  renderer: T.WebGLRenderer;
  scene: T.Scene;
  camera: T.Camera;
  root: T.Object3D;
  roots?: T.Object3D[];
  disposed: () => boolean;
  onSlice?: (index: number, total: number) => void;
  /** Use rAF between slices when updating a visible progress bar. */
  progressSlices?: boolean;
}): Promise<ShaderWarmResult> => {
  const {renderer, scene, camera, root, roots, disposed, onSlice, progressSlices} = opts;
  const programsBefore = programCount(renderer);
  const warmRoots = roots?.length ? roots : [root];
  const meshes = warmRoots.flatMap(r => filterWarmMeshes(r));
  if (!meshes.length) return {programsBefore, programsAfter: programsBefore};

  const groups = groupMeshesByProgram(meshes);
  const parallel = supportsParallelCompile(renderer);
  const yieldBetween = progressSlices ? yieldSlice : yieldOnce;
  const visSaved = warmRoots.map(r => ({r, visible: r.visible}));
  for (const r of warmRoots) {
    r.visible = true;
    r.updateMatrixWorld(true);
  }

  if (parallel && typeof renderer.compileAsync === 'function') {
    const reps = new Set(groups.map(g => g.mesh));
    const restoreMeshes = setRootMeshVisibility(meshes, reps);
    const restoreChrome = hideSceneChrome(scene);
    try {
      await renderer.compileAsync(scene, camera);
    } catch {
      warmScissorRender(renderer, scene, camera);
    } finally {
      restoreChrome();
      restoreMeshes();
    }
    onSlice?.(groups.length, groups.length);
  } else {
    const reps = new Set(groups.map(g => g.mesh));
    const restoreMeshes = setRootMeshVisibility(meshes, reps);
    root.updateMatrixWorld(true);
    const restoreChrome = hideSceneChrome(scene);
    try {
      warmScissorRender(renderer, scene, camera);
    } catch {
      /* software WebGL */
    } finally {
      restoreChrome();
      restoreMeshes();
    }
    onSlice?.(groups.length, groups.length);
    await yieldBetween();
  }

  visSaved.forEach(({r, visible}) => {
    r.visible = visible;
  });
  const programsAfter = programCount(renderer);
  if (import.meta.env.DEV) {
    console.debug('atlas:shader-warm programs', programsBefore, '→', programsAfter);
  }
  return {programsBefore, programsAfter};
};