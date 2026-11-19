// 神之島手搖店 3D 室內：暖色吧台、座位與菜單燈箱（低多邊形、無 PointLight）。

import { interiorByBuilding } from './island-interior.js';

export const HALL_NAME = '神之島';

const WALL_H = 4.0;
const WALL_T = 0.14;

export function createInterior(THREE, opts) {
  const room = interiorByBuilding('shop');
  const ground = (opts && opts.groundY != null) ? opts.groundY : 0.2;
  const reduced = !!(opts && opts.reducedMotion);
  const floor = room.floor;
  const width = floor.maxX - floor.minX;
  const depth = floor.maxZ - floor.minZ;
  const cx = (floor.minX + floor.maxX) / 2;
  const cz = (floor.minZ + floor.maxZ) / 2;
  const materials = [];
  const geometries = [];
  const textures = [];

  function mat(color, night, glow, emissiveI, nightE, extra) {
    const material = new THREE.MeshLambertMaterial({
      color: color,
      flatShading: true,
      emissive: new THREE.Color(glow || 0x000000),
      emissiveIntensity: emissiveI || 0,
      ...(extra || {})
    });
    material.userData.day = new THREE.Color(color);
    material.userData.night = new THREE.Color(night);
    material.userData.dayE = emissiveI || 0;
    material.userData.nightE = glow
      ? (nightE != null ? nightE : Math.max(emissiveI || 0, 0.8))
      : 0;
    materials.push(material);
    return material;
  }

  const group = new THREE.Group();

  function box(w, h, d, color, night, x, y, z, glow, emissiveI, nightE) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow, emissiveI, nightE));
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }

  function cyl(rTop, rBot, h, segs, color, night, x, y, z, glow, emissiveI, nightE) {
    const geometry = new THREE.CylinderGeometry(rTop, rBot, h, segs);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow, emissiveI, nightE));
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }

  function sphere(r, segs, color, night, x, y, z, glow, emissiveI, nightE) {
    const geometry = new THREE.SphereGeometry(r, segs, segs);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow, emissiveI, nightE));
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }

  const wallY = ground + WALL_H / 2;
  const southZ = floor.minZ - WALL_T / 2;
  const northZ = floor.maxZ + WALL_T / 2;

  // 淺木地板與中央走道
  box(width + WALL_T * 2, 0.14, depth + WALL_T * 2, 0xd8b08a, 0x8a6848, cx, ground + 0.04, cz);
  box(width - 0.4, 0.04, depth - 0.4, 0xf3e2cc, 0xa88868, cx, ground + 0.12, cz);
  box(2.3, 0.03, depth - 1.3, 0xfff6ea, 0xc8b49a, 0, ground + 0.145, cz + 0.15, 0xffe6c0, 0.04, 0.08);

  const wallMat = mat(0xfff7ef, 0xc8b8a8, 0xffe8d0, 0.04, 0.1);
  function wallBox(w, h, d, x, y, z) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, wallMat);
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }
  wallBox(WALL_T, WALL_H, depth + WALL_T, floor.minX - WALL_T / 2, wallY, cz);
  wallBox(WALL_T, WALL_H, depth + WALL_T, floor.maxX + WALL_T / 2, wallY, cz);
  wallBox(width + WALL_T * 2, WALL_H, WALL_T, cx, wallY, northZ);

  const doorW = 1.55;
  const leftW = (width - doorW) / 2;
  wallBox(leftW, WALL_H, WALL_T, floor.minX + leftW / 2, wallY, southZ);
  wallBox(leftW, WALL_H, WALL_T, floor.maxX - leftW / 2, wallY, southZ);
  box(doorW + 0.3, 0.14, 0.18, 0x3a7ad9, 0x2a5aa8, 0, ground + 2.18, southZ + 0.03, 0x8cb8ff, 0.2, 0.35);

  // 天花與暖色燈帶
  box(width + WALL_T * 2, 0.12, depth + WALL_T * 2, 0xfff7ef, 0x8a7a68, cx, ground + WALL_H + 0.04, cz, 0xffe6c0, 0.08, 0.18);
  box(width - 0.8, 0.05, 0.16, 0xffe2b0, 0xffe2b0, 0, ground + WALL_H - 0.2, floor.minZ + 0.45, 0xffd9a0, 0.4, 0.6);
  box(width - 0.8, 0.05, 0.16, 0xffe2b0, 0xffe2b0, 0, ground + WALL_H - 0.2, floor.maxZ - 0.45, 0xffd9a0, 0.4, 0.6);
  box(width - 0.8, 0.05, 0.16, 0xffe2b0, 0xffe2b0, 0, ground + WALL_H - 0.2, cz, 0xffd9a0, 0.35, 0.55);

  function lantern(x, z) {
    cyl(0.03, 0.03, 0.35, 6, 0x5a3a24, 0x3a2418, x, ground + 3.35, z);
    sphere(0.2, 8, 0xfff0d0, 0xffd9a0, x, ground + 3.08, z, 0xffe2a8, reduced ? 0.3 : 0.5, 0.7);
  }
  lantern(-1.2, 3.2);
  lantern(1.2, 3.2);
  lantern(-1.2, 6.4);
  lantern(1.2, 6.4);

  // 北牆吧台
  box(9.2, 0.9, 1.15, 0xf3e2cc, 0x8d735c, 0, ground + 0.55, 8.85);
  box(9.3, 0.08, 1.25, 0xfffaf6, 0xc8b8a8, 0, ground + 1.02, 8.85);
  box(9.2, 0.12, 0.12, 0x3a7ad9, 0x2a5aa8, 0, ground + 0.16, 8.3, 0x8cb8ff, 0.15, 0.28);
  // 吧台後牆櫃與杯架
  box(9.0, 1.6, 0.28, 0xffffff, 0xc8d0dc, 0, ground + 2.15, 9.45);
  const cupColors = [
    [0xff9ab8, 0xe8789a],
    [0x8fd9a4, 0x5aaa72],
    [0xffd27a, 0xe0b050],
    [0xf3e6d2, 0xd2c4aa],
    [0x8cb8ff, 0x5a90e0]
  ];
  for (let i = 0; i < 9; i++) {
    const x = -3.6 + i * 0.9;
    const c = cupColors[i % cupColors.length];
    cyl(0.09, 0.08, 0.28, 8, c[0], c[1], x, ground + 1.22, 8.55, c[0], 0.12, 0.2);
    box(0.16, 0.05, 0.16, 0xffffff, 0xd0d0d0, x, ground + 1.38, 8.55);
  }
  // 菜單燈箱
  function paintCanvas(w, h, paint) {
    if (typeof document === 'undefined' || !document.createElement) return null;
    try {
      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d');
      if (!ctx) return null;
      paint(ctx, canvas);
      const texture = new THREE.CanvasTexture(canvas);
      textures.push(texture);
      if ('colorSpace' in texture) texture.colorSpace = THREE.SRGBColorSpace;
      else texture.encoding = THREE.sRGBEncoding;
      texture.needsUpdate = true;
      return texture;
    } catch (_) {
      return null;
    }
  }

  function applyTex(mesh, texture) {
    if (!texture || !mesh.material) return;
    mesh.material.map = texture;
    mesh.material.emissiveMap = texture;
    mesh.material.emissive = new THREE.Color(0xffffff);
    mesh.material.userData.day = new THREE.Color(0xffffff);
    mesh.material.userData.night = new THREE.Color(0xfff4e8);
    mesh.material.needsUpdate = true;
  }

  const menuTex = paintCanvas(768, 1024, (ctx, canvas) => {
    ctx.fillStyle = '#1e4d9a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'center';
    ctx.font = 'bold 86px "Noto Serif TC","Songti TC",serif';
    ctx.fillText('神之島', canvas.width / 2, 130);
    ctx.font = '36px "Noto Sans TC","PingFang TC",sans-serif';
    ctx.fillText('今日飲品', canvas.width / 2, 200);
    const drinks = ['熟成紅茶', '桂花綠茶', '芋頭鮮奶', '粉紅氣泡', '四季春'];
    ctx.textAlign = 'left';
    ctx.font = '40px "Noto Sans TC","PingFang TC",sans-serif';
    drinks.forEach((name, i) => {
      const y = 320 + i * 110;
      ctx.fillStyle = '#ffffff';
      ctx.fillText(name, 90, y);
      ctx.fillStyle = '#ffd27a';
      ctx.fillText('－', 520, y);
    });
  });
  const menu = box(1.7, 2.2, 0.08, 0x1e4d9a, 0x1e4d9a, -2.4, ground + 2.35, 9.58, 0x8cb8ff, 0.25, 0.4);
  applyTex(menu, menuTex);
  box(1.82, 2.32, 0.05, 0xffffff, 0xc8d0dc, -2.4, ground + 2.35, 9.64);

  const nameTex = paintCanvas(1024, 256, (ctx, canvas) => {
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.strokeStyle = '#3a7ad9';
    ctx.lineWidth = 16;
    ctx.beginPath();
    ctx.arc(canvas.width / 2, canvas.height / 2, 90, 0, Math.PI * 2);
    ctx.stroke();
    ctx.fillStyle = '#3a7ad9';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = 'bold 92px "Noto Serif TC","Songti TC",serif';
    ctx.fillText('神', canvas.width / 2, canvas.height / 2 + 4);
    ctx.font = 'bold 48px "Noto Sans TC","PingFang TC",sans-serif';
    ctx.fillText('神之島', canvas.width / 2 + 220, canvas.height / 2);
    ctx.fillText('神之島', canvas.width / 2 - 220, canvas.height / 2);
  });
  const nameBoard = box(3.2, 0.8, 0.08, 0xffffff, 0xffffff, 1.6, ground + 3.15, 9.62, 0x8cb8ff, 0.15, 0.28);
  applyTex(nameBoard, nameTex);

  function seat(x, z) {
    box(1.15, 0.08, 1.15, 0xfff7ef, 0xc8b8a8, x, ground + 0.62, z);
    cyl(0.07, 0.07, 0.6, 8, 0xc4844a, 0x6a4630, x - 0.42, ground + 0.32, z - 0.42);
    cyl(0.07, 0.07, 0.6, 8, 0xc4844a, 0x6a4630, x + 0.42, ground + 0.32, z - 0.42);
    cyl(0.07, 0.07, 0.6, 8, 0xc4844a, 0x6a4630, x - 0.42, ground + 0.32, z + 0.42);
    cyl(0.07, 0.07, 0.6, 8, 0xc4844a, 0x6a4630, x + 0.42, ground + 0.32, z + 0.42);
    // chairs
    [[-0.95, 0], [0.95, 0], [0, -0.95], [0, 0.95]].forEach((o, i) => {
      box(0.4, 0.08, 0.4, 0x3a7ad9, 0x2a5aa8, x + o[0], ground + 0.42, z + o[1], 0x8cb8ff, 0.05, 0.1);
      box(0.08, 0.42, 0.4, 0x3a7ad9, 0x2a5aa8, x + o[0] + (i < 2 ? (o[0] < 0 ? -0.16 : 0.16) : 0), ground + 0.66, z + o[1] + (i >= 2 ? (o[1] < 0 ? -0.16 : 0.16) : 0));
    });
    const drink = cupColors[(Math.abs(x) + z) % cupColors.length | 0];
    cyl(0.06, 0.055, 0.16, 8, drink[0], drink[1], x + 0.12, ground + 0.76, z, drink[0], 0.1, 0.18);
  }
  seat(-5.3, 3.0);
  seat(-5.3, 5.8);
  seat(5.3, 3.0);
  seat(5.3, 5.8);

  // 東側飲品展示櫃
  box(0.7, 1.8, 2.2, 0xffffff, 0xc8d0dc, 6.85, ground + 1.0, 8.2, 0x8cb8ff, 0.06, 0.12);
  box(0.62, 1.5, 2.0, 0xb7e0ef, 0x88b8d0, 6.82, ground + 1.05, 8.2, 0x8cb8ff, 0.12, 0.2);
  for (let i = 0; i < 4; i++) {
    const c = cupColors[i];
    cyl(0.07, 0.07, 0.22, 8, c[0], c[1], 6.7, ground + 0.55 + i * 0.32, 7.6, c[0], 0.12, 0.2);
    cyl(0.07, 0.07, 0.22, 8, c[0], c[1], 6.7, ground + 0.55 + i * 0.32, 8.8, c[0], 0.12, 0.2);
  }

  return {
    group: group,
    materials: materials,
    dispose() {
      textures.forEach((texture) => texture.dispose && texture.dispose());
      geometries.forEach((geometry) => geometry.dispose && geometry.dispose());
      materials.forEach((material) => {
        if (material.map && material.map.dispose) material.map.dispose();
        if (material.emissiveMap && material.emissiveMap.dispose && material.emissiveMap !== material.map) {
          material.emissiveMap.dispose();
        }
        if (material.dispose) material.dispose();
      });
    }
  };
}
