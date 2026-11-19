// 城市島場景：載入 city.glb／粉紅車 GLB（#205）。
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { islandById } from './island-layout.js';
import { G } from './island-kit.js';
import {
  CITY_ID,
  PINK_CAR_LOCAL,
  PINK_CAR_ROOF_Y_FALLBACK
} from './island-city-data.js';
import { applyCityMaterials, applyCarMaterials } from './island-city-materials.js';

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
    sign_face_arcade: neonSign('小東遊戲機', 900, 280, 78, '#fff8d8', '#ff9a3a', '#1a0838'),
    sign_face_711: paint(640, 320, (g, pw, ph) => {
      g.fillStyle = '#ff6b35';
      g.fillRect(0, 0, pw, ph * 0.48);
      g.fillStyle = '#008c3a';
      g.fillRect(0, ph * 0.48, pw, ph * 0.28);
      g.fillStyle = '#e3181c';
      g.fillRect(0, ph * 0.76, pw, ph * 0.24);
      g.fillStyle = '#ffffff';
      g.font = '900 112px ' + font;
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.strokeStyle = '#1a1a1a';
      g.lineWidth = 5;
      g.strokeText('7-11', pw * 0.5, ph * 0.62);
      g.fillText('7-11', pw * 0.5, ph * 0.62);
    }),
    sign_face_claw: neonSign('夾娃娃', 680, 260, 76, '#ffe8f8', '#ff4ec8', '#180828'),
    sign_face_snack: neonSign('台灣小吃', 720, 260, 74, '#fff4d0', '#ff8a3a', '#281008'),
    sign_face_bubble: paint(720, 260, (g, pw, ph) => {
      g.fillStyle = '#f0fff8';
      g.fillRect(0, 0, pw, ph);
      g.fillStyle = '#7ec8ea';
      g.fillRect(0, 0, pw, 28);
      g.font = '700 58px ' + font;
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.fillStyle = '#143858';
      g.fillText('Bubble Tea', pw / 2, ph * 0.38);
      g.font = '900 82px ' + font;
      g.fillText('波波茶飲', pw / 2, ph * 0.72);
    }),
    sign_face_game: neonSign('小東遊戲機', 820, 280, 72, '#fff8d8', '#ff9a3a', '#180838')
  };
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
    const box = new THREE.Box3().setFromObject(model);
    const roofY = box.max.y;
    if (ctx.pinkCarMesh) {
      ctx.pinkCarMesh.roofY = roofY > 0.05 ? roofY : PINK_CAR_ROOF_Y_FALLBACK;
    }
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
    if (ctx.pinkCarMesh) ctx.pinkCarMesh.roofY = 0.77;
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