// 城市島場景：載入 city.glb／粉紅車 GLB（#205）。
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { islandById } from './island-layout.js';
import { G, nightHex } from './island-kit.js';
import { glowForMaterial } from './island-buildings-data.js';
import {
  CITY_ID,
  PINK_CAR_LOCAL
} from './island-city-data.js';

const glbCache = new Map();
let sharedLoader = null;

function getLoader() {
  if (!sharedLoader) sharedLoader = new GLTFLoader();
  return sharedLoader;
}

function loadGLB(url) {
  if (glbCache.has(url)) return glbCache.get(url);
  const promise = new Promise((resolve, reject) => {
    getLoader().load(url, resolve, undefined, reject);
  });
  glbCache.set(url, promise);
  return promise;
}

function citySignMaps(kit) {
  const font = '"PingFang TC", "Heiti TC", "Microsoft JhengHei", sans-serif';
  const { paint } = kit;

  function neonSign(text, w, h, fontSize, fill, stroke, bg) {
    return paint(w, h, (g, pw, ph) => {
      g.fillStyle = bg || '#1a1028';
      g.fillRect(0, 0, pw, ph);
      g.strokeStyle = stroke || '#ff6ad8';
      g.lineWidth = 6;
      g.strokeRect(8, 8, pw - 16, ph - 16);
      g.font = '700 ' + fontSize + 'px ' + font;
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.strokeStyle = stroke || '#ff4ec8';
      g.lineWidth = 4;
      g.strokeText(text, pw / 2, ph / 2);
      g.fillStyle = fill || '#ffe8ff';
      g.fillText(text, pw / 2, ph / 2);
    });
  }

  return {
    sign_face_arcade: neonSign('小東遊戲機', 768, 240, 62, '#fff2b8', '#ff9a3a', '#2a1848'),
    sign_face_711: paint(512, 256, (g, pw, ph) => {
      g.fillStyle = '#ff6b35';
      g.fillRect(0, 0, pw, ph);
      g.fillStyle = '#00913a';
      g.fillRect(pw * 0.58, 0, pw * 0.42, ph);
      g.fillStyle = '#ff2200';
      g.fillRect(0, ph * 0.55, pw * 0.58, ph * 0.45);
      g.fillStyle = '#ffffff';
      g.font = '900 120px ' + font;
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.fillText('7-ELEVEN', pw * 0.29, ph * 0.3);
      g.font = '900 100px ' + font;
      g.fillText('7-11', pw * 0.29, ph * 0.78);
    }),
    sign_face_claw: neonSign('夾娃娃', 560, 220, 66, '#ffd8f4', '#ff5ac8', '#281038'),
    sign_face_snack: neonSign('台灣小吃', 600, 220, 64, '#fff0d8', '#ff8a3a', '#3a2018'),
    sign_face_bubble: paint(600, 220, (g, pw, ph) => {
      g.fillStyle = '#e8fff8';
      g.fillRect(0, 0, pw, ph);
      g.fillStyle = '#7ec8ea';
      g.fillRect(0, 0, pw, 22);
      g.font = '700 68px ' + font;
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.fillStyle = '#1f4d73';
      g.fillText('Bubble Tea', pw / 2, ph / 2 + 4);
      g.font = '700 52px ' + font;
      g.fillText('波波茶飲', pw / 2, ph / 2 + 52);
    }),
    sign_face_game: neonSign('小東遊戲機', 640, 220, 58, '#fff2b8', '#ff9a3a', '#2a1848')
  };
}

function disposeLoadedMaterial(oldMat, keepMap) {
  if (!oldMat || typeof oldMat.dispose !== 'function') return;
  if (keepMap && oldMat.map === keepMap) {
    oldMat.map = null;
    oldMat.dispose();
    return;
  }
  oldMat.dispose();
}

function applyCityMaterials(model, kit, signMaps) {
  const { mat } = kit;
  model.traverse((obj) => {
    if (!obj.isMesh) return;
    const oldMat = obj.material;
    const matName = (oldMat && oldMat.name) || '';
    obj.userData.glbMaterialName = matName;
    let baseColor = 0xffffff;
    if (oldMat && oldMat.color) baseColor = oldMat.color.getHex();
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
      const emissive = obj.name.indexOf('711') >= 0;
      obj.material = mat(0xffffff, {
        map: signTex,
        night: 0xfff0dc,
        glow: emissive ? 0xffffff : 0xffb15a,
        glowAmount: emissive ? 0.95 : 1.08,
        side: THREE.DoubleSide
      });
      if (emissive) obj.material.emissiveMap = signTex;
    } else if (obj.name === 'traffic_red' || obj.name === 'traffic_yellow' || obj.name === 'traffic_green') {
      const init = obj.name === 'traffic_red' ? 0xff3b30
        : obj.name === 'traffic_yellow' ? 0xffcc00 : 0x34c759;
      obj.material = mat(init, { shadow: false });
    } else if (atlasMap) {
      obj.material = mat(0xffffff, Object.assign({ shadow: true, map: atlasMap }, extra));
    } else {
      obj.material = mat(baseColor, Object.assign({ shadow: true }, extra));
    }
    disposeLoadedMaterial(oldMat, obj.material && obj.material.map);
    obj.castShadow = true;
    obj.receiveShadow = true;
  });
}

function wireTrafficFromModel(model, trafficMeshes) {
  if (!trafficMeshes) return;
  const lights = {};
  let pole = null;
  let housing = null;
  model.traverse((obj) => {
    if (!obj.isMesh) return;
    if (obj.name === 'traffic_red') lights.red = obj;
    if (obj.name === 'traffic_yellow') lights.yellow = obj;
    if (obj.name === 'traffic_green') lights.green = obj;
    if (obj.name === 'traffic_pole') pole = obj;
    if (obj.name === 'traffic_housing') housing = obj;
  });
  if (lights.red && lights.yellow && lights.green) {
    trafficMeshes.lights = lights;
    trafficMeshes.pole = pole;
    trafficMeshes.housing = housing;
    trafficMeshes.group = model;
  }
}

function applyCarMaterials(carModel, kit) {
  const { mat } = kit;
  carModel.traverse((obj) => {
    if (!obj.isMesh) return;
    const oldMat = obj.material;
    let baseColor = 0xffffff;
    if (oldMat && oldMat.color) baseColor = oldMat.color.getHex();
    const atlasMap = oldMat && oldMat.map ? oldMat.map : null;
    if (atlasMap) {
      obj.material = mat(0xffffff, { shadow: true, map: atlasMap });
    } else {
      obj.material = mat(baseColor, { shadow: true });
    }
    disposeLoadedMaterial(oldMat, obj.material && obj.material.map);
    obj.castShadow = true;
    obj.receiveShadow = true;
  });
}

export function buildIslandCity(ctx) {
  const { scene, kit, walkers, trafficMeshes, dayNight } = ctx;
  const island = islandById(CITY_ID);
  if (!island) return;
  const { addBox, geo, mat } = kit;
  const cx = island.x;
  const cz = island.z;
  const root = new THREE.Group();
  root.position.set(cx, 0, cz);
  scene.add(root);

  const signMaps = citySignMaps(kit);
  const car = new THREE.Group();
  car.position.set(PINK_CAR_LOCAL.x, G, PINK_CAR_LOCAL.z);
  car.rotation.y = PINK_CAR_LOCAL.yaw;
  root.add(car);
  if (ctx.pinkCarMesh) ctx.pinkCarMesh.group = car;

  function finishRefresh() {
    if (dayNight && typeof dayNight.refresh === 'function') dayNight.refresh();
  }

  const cityUrl = '/models/city.glb';
  const carUrl = '/models/city-pink-car.glb';

  loadGLB(cityUrl).then((gltf) => {
    const model = gltf.scene;
    model.position.set(0, G, 0);
    applyCityMaterials(model, kit, signMaps);
    root.add(model);
    wireTrafficFromModel(model, trafficMeshes);
    finishRefresh();
  }).catch((err) => {
    console.warn('[Island] city.glb 載入失敗：', err);
  });

  loadGLB(carUrl).then((gltf) => {
    const model = gltf.scene;
    applyCarMaterials(model, kit);
    car.add(model);
    finishRefresh();
  }).catch((err) => {
    console.warn('[Island] city-pink-car.glb 載入失敗，保留占位：', err);
    addBox(car, 1.45, 0.42, 0.75, 0xff8ac8, 0, 0.21, 0);
    addBox(car, 0.95, 0.38, 0.68, 0xffb8dd, 0, 0.58, -0.05);
    [[-0.55, 0.35], [0.55, 0.35], [-0.55, -0.35], [0.55, -0.35]].forEach(([wx, wz]) => {
      const wheelMesh = new THREE.Mesh(geo('cyl', [0.14, 0.14, 0.12, 6, 0]), mat(0x2a2a2a, { shadow: false }));
      wheelMesh.position.set(wx, 0.12, wz);
      wheelMesh.rotation.z = Math.PI / 2;
      car.add(wheelMesh);
    });
  });

  const peopleApi = ctx.people || {};
  if (walkers) {
    const spawnCity = () => {
      if (typeof peopleApi.spawnCityWalkers === 'function') {
        peopleApi.spawnCityWalkers();
      }
      if (typeof peopleApi.spawnCityScooterWalkers === 'function') {
        peopleApi.spawnCityScooterWalkers();
      }
    };
    if (peopleApi.whenReady && typeof peopleApi.whenReady.then === 'function') {
      peopleApi.whenReady.then(spawnCity).catch(spawnCity);
    } else {
      spawnCity();
    }
  }
}