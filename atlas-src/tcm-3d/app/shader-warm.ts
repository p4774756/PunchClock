import * as T from 'three';
import {
  createSliceBudget,
  resetSliceBudget,
  sliceBudgetElapsed,
  SLICE_BUDGET_MS,
  yieldForSliceBudget,
  yieldOnce,
  yieldToMain,
  type SliceBudget,
} from './load-progress';

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

const isOutlineMesh = (mesh: T.Mesh | T.SkinnedMesh) =>
  /Outline/i.test(mesh.name) || /Outline/i.test((mesh.material as T.Material)?.name ?? '');

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

export const textureCount = (renderer: T.WebGLRenderer): number =>
  renderer.info?.memory?.textures ?? 0;

export const programCacheKeys = (renderer: T.WebGLRenderer): string[] =>
  renderer.info?.programs?.map(p => String((p as {cacheKey?: string}).cacheKey ?? p.name ?? '')) ?? [];

const supportsParallelCompile = (renderer: T.WebGLRenderer): boolean => {
  try {
    return renderer.getContext().getExtension('KHR_parallel_shader_compile') != null;
  } catch {
    return false;
  }
};

export const isTextureOnGpu = (renderer: T.WebGLRenderer, texture: T.Texture): boolean => {
  const props = renderer.properties.get(texture) as {__webglTexture?: WebGLTexture} | undefined;
  return props?.__webglTexture !== undefined;
};

type ImageLike = CanvasImageSource & {width: number; height: number};

const textureImage = (tex: T.Texture): ImageLike | null => {
  const img = tex.image as ImageLike | undefined;
  if (!img?.width || !img?.height) return null;
  return img;
};

const textureMetaLabel = (tex: T.Texture): string => tex.name || `tex#${tex.id}`;

const warmScissorRender = (renderer: T.WebGLRenderer, scene: T.Scene, camera: T.Camera) => {
  const scissorOn = renderer.getScissorTest();
  renderer.setScissorTest(true);
  renderer.setScissor(0, 0, 1, 1);
  try {
    renderer.render(scene, camera);
  } catch {
    /* software WebGL */
  } finally {
    renderer.setScissorTest(scissorOn);
  }
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

export type IncrementalWarmStats = {
  sliceCount: number;
  itemCount: number;
  skippedCount: number;
};

export const uploadTexturesIncremental = async (opts: {
  renderer: T.WebGLRenderer;
  textures: T.Texture[];
  disposed: () => boolean;
  onSlice?: (index: number, total: number) => void;
  /** Throttled rAF yields so the load bar can repaint. */
  progressSlices?: boolean;
}): Promise<IncrementalWarmStats> => {
  const {renderer, textures, disposed, onSlice, progressSlices} = opts;
  const pending = textures.filter(tex => !isTextureOnGpu(renderer, tex));
  const skippedCount = textures.length - pending.length;
  if (!pending.length) {
    return {sliceCount: 0, itemCount: 0, skippedCount};
  }

  const budget = createSliceBudget();
  let sliceCount = 0;
  let itemCount = 0;
  let i = 0;
  while (i < pending.length) {
    if (disposed()) break;
    resetSliceBudget(budget);
    while (i < pending.length) {
      const tex = pending[i];
      const t0 = performance.now();
      try {
        renderer.initTexture(tex);
      } catch {
        /* software WebGL */
      }
      const ms = performance.now() - t0;
      const img = textureImage(tex);
      if (import.meta.env.DEV || ms > SLICE_BUDGET_MS) {
        console.debug(
          'atlas:texture-upload',
          textureMetaLabel(tex),
          img?.width ?? 0,
          img?.height ?? 0,
          tex.format,
          tex.generateMipmaps,
          Math.round(ms),
        );
      }
      i++;
      itemCount++;
      if (sliceBudgetElapsed(budget) >= SLICE_BUDGET_MS || ms > SLICE_BUDGET_MS * 4) break;
    }
    sliceCount++;
    onSlice?.(sliceCount, pending.length);
    if (i < pending.length) {
      if (progressSlices) await yieldToMain();
      await yieldForSliceBudget(budget, !!progressSlices);
    }
  }
  return {sliceCount, itemCount, skippedCount};
};

export type ShaderWarmResult = {
  programsBefore: number;
  programsAfter: number;
  groups: number;
  groupsSkipped: number;
  sliceCount: number;
};

type MeshWarmMode = 'character' | 'outline';

const filterWarmMeshes = (root: T.Object3D, mode: MeshWarmMode) =>
  collectRenderableMeshes(root).filter(m => {
    const outline = isOutlineMesh(m);
    if (mode === 'outline') return outline;
    return !outline;
  });

export const warmShadersIncremental = async (opts: {
  renderer: T.WebGLRenderer;
  scene: T.Scene;
  camera: T.Camera;
  root: T.Object3D;
  roots?: T.Object3D[];
  disposed: () => boolean;
  onSlice?: (index: number, total: number) => void;
  /** Throttled rAF yields for visible progress bar updates. */
  progressSlices?: boolean;
  /** When false (default), panorama/ground chrome is hidden during character warm. */
  keepSceneChrome?: boolean;
  /** Skip program groups already warmed this session (school + practice share programs). */
  warmedKeys?: Set<string>;
  /** Outline pass only — fills MToon outline shaders before the first full frame. */
  outlineOnly?: boolean;
}): Promise<ShaderWarmResult> => {
  const {
    renderer,
    scene,
    camera,
    root,
    roots,
    disposed,
    onSlice,
    progressSlices,
    keepSceneChrome,
    warmedKeys,
    outlineOnly,
  } = opts;
  const programsBefore = programCount(renderer);
  const warmRoots = roots?.length ? roots : [root];
  const mode: MeshWarmMode = outlineOnly ? 'outline' : 'character';
  const meshes = warmRoots.flatMap(r => filterWarmMeshes(r, mode));
  if (!meshes.length) {
    return {programsBefore, programsAfter: programsBefore, groups: 0, groupsSkipped: 0, sliceCount: 0};
  }

  let groups = groupMeshesByProgram(meshes);
  const groupsSkipped = warmedKeys ? groups.filter(g => warmedKeys.has(g.key)).length : 0;
  if (warmedKeys) groups = groups.filter(g => !warmedKeys.has(g.key));
  if (!groups.length) {
    return {
      programsBefore,
      programsAfter: programCount(renderer),
      groups: 0,
      groupsSkipped,
      sliceCount: 0,
    };
  }

  const parallel = supportsParallelCompile(renderer);
  const visSaved = warmRoots.map(r => ({r, visible: r.visible}));
  for (const r of warmRoots) {
    r.visible = true;
    r.updateMatrixWorld(true);
  }

  const budget = createSliceBudget();
  let sliceCount = 0;

  if (parallel && typeof renderer.compileAsync === 'function' && !outlineOnly) {
    const reps = new Set(groups.map(g => g.mesh));
    const restoreMeshes = setRootMeshVisibility(meshes, reps);
    const restoreChrome = keepSceneChrome ? () => {} : hideSceneChrome(scene);
    try {
      await renderer.compileAsync(scene, camera);
    } catch {
      warmScissorRender(renderer, scene, camera);
    } finally {
      restoreChrome();
      restoreMeshes();
    }
    for (const g of groups) warmedKeys?.add(g.key);
    sliceCount = 1;
    onSlice?.(groups.length, groups.length);
  } else {
    root.updateMatrixWorld(true);
    for (let i = 0; i < groups.length; i++) {
      if (disposed()) break;
      const rep = groups[i].mesh;
      const restoreMeshes = setRootMeshVisibility(meshes, new Set([rep]));
      const restoreChrome = keepSceneChrome ? () => {} : hideSceneChrome(scene);
      try {
        warmScissorRender(renderer, scene, camera);
      } catch {
        /* software WebGL */
      } finally {
        restoreChrome();
        restoreMeshes();
      }
      warmedKeys?.add(groups[i].key);
      sliceCount++;
      onSlice?.(i + 1, groups.length);
      if (i < groups.length - 1) {
        if (progressSlices) await yieldToMain();
        await (progressSlices ? yieldForSliceBudget(budget, true) : yieldOnce());
      }
    }
  }

  visSaved.forEach(({r, visible}) => {
    r.visible = visible;
  });
  const programsAfter = programCount(renderer);
  if (import.meta.env.DEV) {
    console.debug('atlas:shader-warm programs', programsBefore, '→', programsAfter);
  }
  return {programsBefore, programsAfter, groups: groups.length, groupsSkipped, sliceCount};
};

/** Load card: one compile pass for the visible school rig (no outline-only pass, no readPixels). */
export const warmSchoolDuringCard = async (
  renderer: T.WebGLRenderer,
  scene: T.Scene,
  camera: T.Camera,
  characterRoot: T.Object3D,
  whileCompiling: () => () => void,
): Promise<void> => {
  const t0 = performance.now();
  const charWasVisible = characterRoot.visible;
  characterRoot.visible = true;
  characterRoot.updateMatrixWorld(true);
  const restoreExtras = whileCompiling();
  const restoreChrome = hideSceneChrome(scene);
  try {
    if (typeof renderer.compileAsync === 'function') {
      await renderer.compileAsync(scene, camera);
    } else {
      warmScissorRender(renderer, scene, camera);
    }
  } catch {
    warmScissorRender(renderer, scene, camera);
  } finally {
    restoreChrome();
    restoreExtras();
    characterRoot.visible = charWasVisible;
  }
  console.debug('atlas:phase', 'school-compile', Math.round(performance.now() - t0));
};

export type ShaderWarmPlan = {
  groups: {key: string; mesh: T.Mesh | T.SkinnedMesh}[];
  meshes: (T.Mesh | T.SkinnedMesh)[];
  warmRoots: T.Object3D[];
};

/** Post-card slices: one program group per idle callback. */
export const planShaderWarm = (
  roots: T.Object3D[],
  outlineOnly: boolean,
  warmedKeys?: Set<string>,
): ShaderWarmPlan => {
  const mode: MeshWarmMode = outlineOnly ? 'outline' : 'character';
  const warmRoots = roots.filter(Boolean);
  const meshes = warmRoots.flatMap(r => filterWarmMeshes(r, mode));
  let groups = groupMeshesByProgram(meshes);
  if (warmedKeys) groups = groups.filter(g => !warmedKeys.has(g.key));
  return {groups, meshes, warmRoots};
};

export const warmShaderPlanGroup = async (
  renderer: T.WebGLRenderer,
  scene: T.Scene,
  camera: T.Camera,
  plan: ShaderWarmPlan,
  groupIndex: number,
  keepSceneChrome: boolean,
): Promise<number> => {
  const rep = plan.groups[groupIndex]?.mesh;
  if (!rep) return 0;
  const visSaved = plan.warmRoots.map(r => ({r, visible: r.visible}));
  for (const r of plan.warmRoots) {
    r.visible = true;
    r.updateMatrixWorld(true);
  }
  const restoreMeshes = setRootMeshVisibility(plan.meshes, new Set([rep]));
  const restoreChrome = keepSceneChrome ? () => {} : hideSceneChrome(scene);
  const t0 = performance.now();
  try {
    warmScissorRender(renderer, scene, camera);
  } catch {
    /* software WebGL */
  } finally {
    restoreChrome();
    restoreMeshes();
    visSaved.forEach(({r, visible}) => {
      r.visible = visible;
    });
  }
  const ms = performance.now() - t0;
  const keys = programCacheKeys(renderer);
  const name = keys.at(-1) ?? plan.groups[groupIndex].key;
  if (ms > 600) console.debug('atlas:post-warm-slow-program', name, Math.round(ms));
  return ms;
};

export const uploadOneTexture = (renderer: T.WebGLRenderer, texture: T.Texture): number => {
  const t0 = performance.now();
  try {
    renderer.initTexture(texture);
  } catch {
    /* software WebGL */
  }
  return performance.now() - t0;
};