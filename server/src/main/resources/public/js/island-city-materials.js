// 城市 GLB 材質替換（#205）；與 island-city-scene 分離供 node:test 驗證共用材質。
import { nightHex } from './island-kit.js';
import { glowForMaterial } from './island-buildings-data.js';

const MESH_SIDE_DOUBLE = 2;

function collectForDispose(mat, set) {
  if (!mat) return;
  if (Array.isArray(mat)) {
    for (let i = 0; i < mat.length; i++) collectForDispose(mat[i], set);
    return;
  }
  if (typeof mat.dispose === 'function') set.add(mat);
}

function replaceOneMaterial(obj, oldMat, kit, signMaps) {
  const { mat } = kit;
  const matName = (oldMat && oldMat.name) || '';
  let baseColor = 0xffffff;
  if (oldMat && oldMat.color && typeof oldMat.color.getHex === 'function') {
    baseColor = oldMat.color.getHex();
  }
  const atlasMap = oldMat && oldMat.map ? oldMat.map : null;
  const glowInfo = glowForMaterial('city', matName);
  let extra = {};
  if (glowInfo) {
    const nightC = nightHex(baseColor);
    extra = {
      night: nightC != null ? nightC : baseColor,
      glow: glowInfo.color,
      glowAmount: glowInfo.amount
    };
  }
  const signTex = signMaps[obj.name];
  if (signTex) {
    const is711 = obj.name.indexOf('711') >= 0;
    const created = mat(0xffffff, {
      map: signTex,
      night: 0xfff0dc,
      glow: is711 ? 0xffffff : 0xffb15a,
      glowAmount: is711 ? 0.98 : 1.18,
      side: MESH_SIDE_DOUBLE
    });
    created.emissiveMap = signTex;
    return created;
  }
  if (obj.name === 'traffic_red' || obj.name === 'traffic_yellow' || obj.name === 'traffic_green') {
    const init = obj.name === 'traffic_red' ? 0xff3b30
      : obj.name === 'traffic_yellow' ? 0xffcc00 : 0x34c759;
    return mat(init, { shadow: false });
  }
  if (atlasMap) {
    return mat(0xffffff, Object.assign({ shadow: true, map: atlasMap }, extra));
  }
  return mat(baseColor, Object.assign({ shadow: true }, extra));
}

export function applyCityMaterials(model, kit, signMaps) {
  const toDispose = new Set();
  model.traverse((obj) => {
    if (!obj.isMesh) return;
    const oldMat = obj.material;
    if (Array.isArray(oldMat)) {
      obj.userData.glbMaterialName = oldMat.map((m) => (m && m.name) || '').join('|');
      const next = [];
      for (let i = 0; i < oldMat.length; i++) {
        collectForDispose(oldMat[i], toDispose);
        next.push(replaceOneMaterial(obj, oldMat[i], kit, signMaps));
      }
      obj.material = next;
    } else {
      obj.userData.glbMaterialName = (oldMat && oldMat.name) || '';
      collectForDispose(oldMat, toDispose);
      obj.material = replaceOneMaterial(obj, oldMat, kit, signMaps);
    }
    obj.castShadow = true;
    obj.receiveShadow = true;
  });
  for (const m of toDispose) m.dispose();
}

export function applyCarMaterials(carModel, kit) {
  const toDispose = new Set();
  const { mat } = kit;
  carModel.traverse((obj) => {
    if (!obj.isMesh) return;
    const oldMat = obj.material;
    const mats = Array.isArray(oldMat) ? oldMat : [oldMat];
    const next = [];
    for (let i = 0; i < mats.length; i++) {
      const oldOne = mats[i];
      collectForDispose(oldOne, toDispose);
      let baseColor = 0xffffff;
      if (oldOne && oldOne.color && typeof oldOne.color.getHex === 'function') {
        baseColor = oldOne.color.getHex();
      }
      const atlasMap = oldOne && oldOne.map ? oldOne.map : null;
      if (atlasMap) {
        next.push(mat(0xffffff, { shadow: true, map: atlasMap }));
      } else {
        next.push(mat(baseColor, { shadow: true }));
      }
    }
    obj.material = Array.isArray(oldMat) ? next : next[0];
    obj.castShadow = true;
    obj.receiveShadow = true;
  });
  for (const m of toDispose) m.dispose();
}