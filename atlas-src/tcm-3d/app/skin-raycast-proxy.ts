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
    const position = geometry.attributes.position as T.BufferAttribute;
    for (let i = 0; i < position.count; i++) {
      obj.applyBoneTransform(i, _v);
      position.setXYZ(i, _v.x, _v.y, _v.z);
    }
    position.needsUpdate = true;
    geometry.computeBoundingSphere();
    geometry.computeBoundingBox();
    const proxy = new T.Mesh(
      geometry,
      new T.MeshBasicMaterial({visible: false, side: T.DoubleSide}),
    );
    proxy.name = `${obj.name || 'skin'}#raycast`;
    proxy.matrixAutoUpdate = false;
    proxy.matrix.copy(obj.matrix);
    proxy.matrixWorld.copy(obj.matrixWorld);
    proxies.push(proxy);
  });
  return proxies;
}