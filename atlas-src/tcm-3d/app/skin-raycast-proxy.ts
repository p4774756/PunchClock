import * as T from 'three';

const _v = new T.Vector3();
const SLICE_BUDGET_MS = 12;
const yieldSlice = (): Promise<void> =>
  new Promise(resolve => {
    requestAnimationFrame(() => setTimeout(() => resolve(), 0));
  });

const proxyFromSkinned = (obj: T.SkinnedMesh): T.Mesh => {
  const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
  obj.skeleton.update();
  const geometry = obj.geometry.clone();
  geometry.morphAttributes = {};
  const position = geometry.attributes.position as T.BufferAttribute;
  for (let i = 0; i < position.count; i++) {
    obj.getVertexPosition(i, _v);
    position.setXYZ(i, _v.x, _v.y, _v.z);
  }
  position.needsUpdate = true;
  geometry.computeBoundingSphere();
  geometry.computeBoundingBox();
  const proxyMaterials = mats.map(
    m =>
      new T.MeshBasicMaterial({
        side: (m as T.Material & {side?: number}).side ?? T.FrontSide,
      }),
  );
  const proxy = new T.Mesh(
    geometry,
    proxyMaterials.length === 1 ? proxyMaterials[0] : proxyMaterials,
  );
  proxy.name = `${obj.name || 'skin'}#raycast`;
  proxy.matrixAutoUpdate = false;
  proxy.matrix.copy(obj.matrix);
  proxy.matrixWorld.copy(obj.matrixWorld);
  return proxy;
};

const listSkinMeshes = (root: T.Object3D, namePattern: RegExp) => {
  const skins: T.SkinnedMesh[] = [];
  root.traverse(obj => {
    if (!(obj instanceof T.SkinnedMesh)) return;
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    const label = `${mats.map(m => m?.name || '').join(' ')} ${obj.name || ''}`;
    if (!namePattern.test(label)) return;
    skins.push(obj);
  });
  return skins;
};

/** Bake SkinnedMesh poses into static BufferGeometry for fast raycasts (once per load). */
export function buildSkinRaycastProxies(root: T.Object3D, namePattern: RegExp): T.Mesh[] {
  root.updateMatrixWorld(true);
  return listSkinMeshes(root, namePattern).map(proxyFromSkinned);
}

/** Slice skin proxy bake so anchor raycast work stays off the post-load critical path. */
export const buildSkinRaycastProxiesIncremental = async (
  root: T.Object3D,
  namePattern: RegExp,
  disposed: () => boolean,
): Promise<T.Mesh[]> => {
  root.updateMatrixWorld(true);
  const skins = listSkinMeshes(root, namePattern);
  const proxies: T.Mesh[] = [];
  let sliceStart = performance.now();
  for (let i = 0; i < skins.length; i++) {
    if (disposed()) break;
    proxies.push(proxyFromSkinned(skins[i]));
    if (performance.now() - sliceStart >= SLICE_BUDGET_MS && i < skins.length - 1) {
      await yieldSlice();
      sliceStart = performance.now();
    }
  }
  return proxies;
};