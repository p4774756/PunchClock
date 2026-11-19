import * as T from 'three';

const _v = new T.Vector3();

/** Bake SkinnedMesh poses into static BufferGeometry for fast raycasts (once per load). */
export function buildSkinRaycastProxies(root: T.Object3D, namePattern: RegExp): T.Mesh[] {
  const proxies: T.Mesh[] = [];
  root.updateMatrixWorld(true);
  root.traverse(obj => {
    if (!(obj instanceof T.SkinnedMesh)) return;
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    const label = `${mats.map(m => m?.name || '').join(' ')} ${obj.name || ''}`;
    if (!namePattern.test(label)) return;
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
    proxies.push(proxy);
  });
  return proxies;
}