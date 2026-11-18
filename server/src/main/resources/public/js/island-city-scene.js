// 城市島場景 mesh（瀏覽器／Three.js）。
import * as THREE from 'three';
import { islandById } from './island-layout.js';
import { G } from './island-kit.js';
import { DECOR_STONE } from './island-decor.js';
import {
  layInstancedPaving,
  attachParkBench,
  makeTradSignTexture
} from './island-decor-scene.js';
import {
  CITY_ID,
  CITY_CENTER,
  CITY_COLUMN_LOCAL,
  CITY_PAVING_SPECS,
  PINK_CAR_LOCAL,
  cityWalkerPaths,
  cityMotoPaths
} from './island-city-data.js';

export function buildIslandCity(ctx) {
  const { scene, kit, walkers, trafficMeshes } = ctx;
  const island = islandById(CITY_ID);
  if (!island) return;
  const { addMesh, addBox, geo, mat, lampLight } = kit;
  const cx = island.x;
  const cz = island.z;
  const root = new THREE.Group();
  root.position.set(cx, 0, cz);
  scene.add(root);

  layInstancedPaving(root, kit, CITY_PAVING_SPECS);
  attachParkBench(root, kit, -2.4, 4.2, 0.4);
  attachParkBench(root, kit, 2.5, 4.0, -0.5);

  function shopBlock(lx, lz, w, d, h, wallColor, signText, signColor) {
    addBox(root, w, h, d, wallColor, lx, G + h / 2, lz);
    addBox(root, w + 0.35, 0.12, d + 0.2, DECOR_STONE.edge, lx, G + h + 0.06, lz);
    const tex = makeTradSignTexture(kit, signText, 280, 128);
    const sign = new THREE.Mesh(
      new THREE.PlaneGeometry(1.35, 0.55),
      mat(0xffffff, {
        map: tex,
        side: THREE.DoubleSide,
        shadow: false,
        night: signColor || 0xfff0dc,
        glow: 0xffb15a,
        glowAmount: 0.45
      })
    );
    sign.position.set(lx, G + h + 0.45, lz + d / 2 + 0.08);
    sign.rotation.x = -0.06;
    root.add(sign);
    for (let i = -1; i <= 1; i += 2) {
      addBox(root, 0.18, h + 0.5, 0.18, 0xe8e0d4, lx + i * (w / 2 + 0.55), G + (h + 0.5) / 2, lz + d / 2 + 0.55);
    }
  }

  shopBlock(-3.8, 0.5, 3.4, 4.6, 2.05, 0xfff7ef, '便利商店', 0xfff0e0);
  shopBlock(3.8, 0.5, 3.4, 4.6, 2.05, 0xf4f0ff, '手搖飲', 0xfff6ff);
  shopBlock(0, -4.2, 3.6, 2.8, 1.85, 0xfff0f5, '夾娃娃店', 0xffe8f4);

  addBox(root, 2.2, 3.2, 2.2, 0xe0e4ea, -4.2, G + 1.6, 3.8);
  addBox(root, 2.2, 3.2, 2.2, 0xe0e4ea, 4.2, G + 1.6, 3.8);

  const colGeo = geo('box', [0.22, 2.4, 0.22]);
  const colMat = mat(0xe8e0d4, { shadow: false });
  const cols = new THREE.InstancedMesh(colGeo, colMat, CITY_COLUMN_LOCAL.length);
  const dummy = new THREE.Object3D();
  CITY_COLUMN_LOCAL.forEach((col, i) => {
    dummy.position.set(col.x, G + 1.2, col.z);
    dummy.updateMatrix();
    cols.setMatrixAt(i, dummy.matrix);
  });
  cols.instanceMatrix.needsUpdate = true;
  root.add(cols);

  function streetLamp(lx, lz) {
    addMesh(root, geo('cyl', [0.04, 0.05, 1.35, 5, 0]), 0x1f4d73, lx, G + 0.68, lz);
    addBox(root, 0.26, 0.08, 0.26, 0xf3e2cc, lx, G + 1.38, lz);
    addMesh(root, geo('sph', [0.09, 6, 5]), 0xfff6d4, lx, G + 1.28, lz, {
      night: 0xffe2b0,
      glow: 0xffc56a,
      glowAmount: 1.1,
      shadow: false
    });
    lampLight(lx + cx, G + 1.28, lz + cz, 8);
  }
  streetLamp(-3.0, 4.5);
  streetLamp(3.0, 4.5);
  streetLamp(-3.0, -3.5);
  streetLamp(3.0, -3.5);

  const trafficGroup = new THREE.Group();
  trafficGroup.position.set(0, G, 0);
  const pole = addBox(trafficGroup, 0.12, 2.6, 0.12, 0x2e2e2e, 0, 1.3, 0);
  const housing = addBox(trafficGroup, 0.38, 0.95, 0.22, 0x1a1a1a, 0, 2.55, 0.08);
  const lights = {
    red: addMesh(trafficGroup, geo('sph', [0.08, 8, 6]), 0xff3b30, 0, 2.78, 0.08, { shadow: false }),
    yellow: addMesh(trafficGroup, geo('sph', [0.08, 8, 6]), 0x3a3520, 0, 2.55, 0.08, { shadow: false }),
    green: addMesh(trafficGroup, geo('sph', [0.08, 8, 6]), 0x1e3324, 0, 2.32, 0.08, { shadow: false })
  };
  root.add(trafficGroup);
  if (trafficMeshes) {
    trafficMeshes.pole = pole;
    trafficMeshes.housing = housing;
    trafficMeshes.lights = lights;
    trafficMeshes.group = trafficGroup;
  }

  addBox(root, 0.9, 0.08, 5.2, 0xc9b896, 0, G + 0.04, 6.2);
  addBox(root, 0.12, 0.55, 5.2, 0xa89272, 0.42, G + 0.28, 6.2);
  addBox(root, 0.12, 0.55, 5.2, 0xa89272, -0.42, G + 0.28, 6.2);

  const car = new THREE.Group();
  car.position.set(PINK_CAR_LOCAL.x, G, PINK_CAR_LOCAL.z);
  car.rotation.y = PINK_CAR_LOCAL.yaw;
  addBox(car, 1.45, 0.42, 0.75, 0xff8ac8, 0, 0.21, 0);
  addBox(car, 0.95, 0.38, 0.68, 0xffb8dd, 0, 0.58, -0.05);
  addBox(car, 0.28, 0.22, 0.55, 0x88ccff, -0.32, 0.52, 0.12, { shadow: false });
  addBox(car, 0.28, 0.22, 0.55, 0x88ccff, 0.32, 0.52, 0.12, { shadow: false });
  [[-0.55, 0.35], [0.55, 0.35], [-0.55, -0.35], [0.55, -0.35]].forEach(([wx, wz]) => {
    addMesh(car, geo('cyl', [0.14, 0.14, 0.12, 6, 0]), 0x2a2a2a, wx, 0.12, wz, {
      rot: [0, 0, Math.PI / 2],
      shadow: false
    });
  });
  root.add(car);
  if (ctx.pinkCarMesh) ctx.pinkCarMesh.group = car;

  const { makePerson } = ctx.people || {};
  if (makePerson && walkers) {
    const paths = cityWalkerPaths();
    const shirts = [0xf08a3a, 0x1f4d73, 0x9fd9b4, 0xf4a0c0, 0x7ec8ea, 0xfff7ef];
    for (let i = 0; i < 6; i++) {
      const person = makePerson({ shirt: shirts[i % shirts.length] });
      scene.add(person);
      walkers.push({
        obj: person,
        path: paths[i % paths.length],
        speed: 0.038 + (i % 3) * 0.006,
        pingpong: i % 2 === 1,
        offset: i * 0.17,
        cityOnly: true
      });
    }
    const motoPaths = cityMotoPaths();
    for (let m = 0; m < 2; m++) {
      const moto = new THREE.Group();
      addBox(moto, 0.55, 0.28, 0.22, 0x3a4a5a, 0, 0.14, 0);
      addMesh(moto, geo('cyl', [0.12, 0.12, 0.1, 6, 0]), 0x222222, -0.22, 0.12, 0, { rot: [0, 0, Math.PI / 2] });
      addMesh(moto, geo('cyl', [0.12, 0.12, 0.1, 6, 0]), 0x222222, 0.22, 0.12, 0, { rot: [0, 0, Math.PI / 2] });
      addMesh(moto, geo('box', [0.2, 0.28, 0.14]), 0x5a7ec8, 0, 0.38, -0.02);
      addMesh(moto, geo('sph', [0.1, 6, 5]), 0xf3d0b0, 0, 0.55, 0);
      scene.add(moto);
      walkers.push({
        obj: moto,
        path: motoPaths[m],
        speed: 0.08,
        pingpong: true,
        offset: m * 0.3,
        cityOnly: true
      });
    }
  }
}