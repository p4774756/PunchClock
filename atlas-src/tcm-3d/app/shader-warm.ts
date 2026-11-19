import * as T from 'three';
import {yieldSlice} from './load-progress';

const stashSceneChrome = (scene: T.Scene, keep: T.Object3D) => {
  const saved: {obj: T.Object3D; visible: boolean}[] = [];
  const isAncestorOfKeep = (obj: T.Object3D) => {
    let p: T.Object3D | null = keep;
    while (p) {
      if (p === obj) return true;
      p = p.parent;
    }
    return false;
  };
  for (const child of scene.children) {
    if (child === keep || isAncestorOfKeep(child)) continue;
    saved.push({obj: child, visible: child.visible});
    child.visible = false;
  }
  return () => saved.forEach(({obj, visible}) => {
    obj.visible = visible;
  });
};

export const collectRenderableMeshes = (root: T.Object3D): (T.Mesh | T.SkinnedMesh)[] => {
  const meshes: (T.Mesh | T.SkinnedMesh)[] = [];
  root.traverse(obj => {
    if ((obj instanceof T.Mesh || obj instanceof T.SkinnedMesh) && obj.geometry) meshes.push(obj);
  });
  return meshes;
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

export const warmShadersIncremental = async (opts: {
  renderer: T.WebGLRenderer;
  scene: T.Scene;
  camera: T.Camera;
  root: T.Object3D;
  disposed: () => boolean;
  onSlice?: (index: number, total: number) => void;
}) => {
  const {renderer, scene, camera, root, disposed, onSlice} = opts;
  const meshes = collectRenderableMeshes(root).filter(
    m => !/Outline/i.test(m.name) && !/Outline/i.test((m.material as T.Material)?.name ?? ''),
  );
  if (!meshes.length) return;
  const saved = meshes.map(m => m.visible);
  const hadRootVisible = root.visible;
  root.visible = true;
  for (let i = 0; i < meshes.length; i++) {
    if (disposed()) break;
    for (let j = 0; j < meshes.length; j++) meshes[j].visible = saved[j] && j === i;
    root.updateMatrixWorld(true);
    await yieldSlice();
    const restoreChrome = stashSceneChrome(scene, root);
    try {
      const scissorOn = renderer.getScissorTest();
      renderer.setScissorTest(true);
      renderer.setScissor(0, 0, 1, 1);
      renderer.render(scene, camera);
      renderer.setScissorTest(scissorOn);
    } catch {
      /* software WebGL */
    } finally {
      restoreChrome();
    }
    onSlice?.(i + 1, meshes.length);
    await yieldSlice();
  }
  meshes.forEach((m, idx) => {
    m.visible = saved[idx];
  });
  root.visible = hadRootVisible;
};