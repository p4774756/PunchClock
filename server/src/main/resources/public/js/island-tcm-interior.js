// 神之島推拿館 3D 室內：暖色中式醫館（低多邊形、無 PointLight）。
// 進門先進入此廳，再從館內互動前往既有經絡研習室。

import { interiorByBuilding } from './island-interior.js';
import {
  buildCardTextModel,
  pickTodayCode,
  taipeiDateLabel
} from './island-tcm-figure-logic.js';

export const BRONZE_POINTS_URL = '/island/tcm-bronze-points.json';
export const STUDY_LABEL = '進入經絡研習室';
export const HALL_NAME = '神之島推拿館';

const WALL_H = 4.4;
const WALL_T = 0.16;

export function todayExhibitModel(records, nowMs) {
  const list = Array.isArray(records) ? records : [];
  const code = pickTodayCode(list, nowMs);
  const dateLine = taipeiDateLabel(nowMs) || '';
  if (!code) {
    return Object.freeze({
      title: '今日一穴',
      subtitle: '請見庭院銅人',
      dateLine: dateLine,
      location: '',
      indications: ''
    });
  }
  const record = list.find((row) => row && row.code === code);
  if (!record) {
    return Object.freeze({
      title: '今日一穴',
      subtitle: '請見庭院銅人',
      dateLine: dateLine,
      location: '',
      indications: ''
    });
  }
  return buildCardTextModel(record, { today: true, nowMs: nowMs });
}

export function createInterior(THREE, opts) {
  const room = interiorByBuilding('tcm-site');
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

  function box(w, h, d, color, night, x, y, z, glow, emissiveI, nightE, extra) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow, emissiveI, nightE, extra));
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

  // 木地板與中央暖色走道
  box(width + WALL_T * 2, 0.14, depth + WALL_T * 2, 0x5a3a24, 0x3a2418, cx, ground + 0.04, cz);
  box(width - 0.5, 0.04, depth - 0.5, 0x7a5234, 0x4a3220, cx, ground + 0.12, cz);
  box(2.4, 0.03, depth - 1.2, 0x8a5a38, 0x5a3a24, 0, ground + 0.145, cz + 0.1, 0xc48a52, 0.05, 0.1);

  const wallCream = mat(0xf3e2c8, 0xc8b08a, 0xffe6c0, 0.04, 0.1);
  function wallBox(w, h, d, x, y, z) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, wallCream);
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }
  wallBox(WALL_T, WALL_H, depth + WALL_T, floor.minX - WALL_T / 2, wallY, cz);
  wallBox(WALL_T, WALL_H, depth + WALL_T, floor.maxX + WALL_T / 2, wallY, cz);

  const doorW = 1.6;
  const studyW = 1.8;
  const southLeft = (width - doorW) / 2;
  const northLeft = (width - studyW) / 2;
  wallBox(southLeft, WALL_H, WALL_T, floor.minX + southLeft / 2, wallY, southZ);
  wallBox(southLeft, WALL_H, WALL_T, floor.maxX - southLeft / 2, wallY, southZ);
  // 北牆實心（經絡研習室是另頁，門口不要挖穿看到戶外天空）
  wallBox(width + WALL_T * 2, WALL_H, WALL_T, cx, wallY, northZ);
  // 南門樑、北門（經絡研習室）樑與門框
  box(doorW + 0.28, 0.16, 0.2, 0x6b3a2a, 0x4a2418, 0, ground + 2.22, southZ + 0.03, 0xc47a48, 0.12, 0.22);
  box(studyW + 0.36, 0.18, 0.22, 0x8a2018, 0x5a1010, 0, ground + 2.35, northZ - 0.03, 0xd45a3a, 0.16, 0.28);
  box(0.12, 2.35, 0.14, 0x6b3a2a, 0x4a2418, -studyW / 2, ground + 1.2, northZ - 0.02, 0xc47a48, 0.1, 0.2);
  box(0.12, 2.35, 0.14, 0x6b3a2a, 0x4a2418, studyW / 2, ground + 1.2, northZ - 0.02, 0xc47a48, 0.1, 0.2);
  // 門簾（北）
  box(studyW - 0.2, 1.7, 0.04, 0xa42828, 0x6a1818, 0, ground + 1.05, northZ - 0.08, 0xd44840, 0.08, 0.16);

  // 天花與樑，避免看見戶外天空
  box(width + WALL_T * 2, 0.12, depth + WALL_T * 2, 0xe8d2aa, 0x8a7050, cx, ground + WALL_H + 0.04, cz, 0xffe6c0, 0.08, 0.18);
  for (let z = floor.minZ + 1.2; z <= floor.maxZ - 0.4; z += 2.15) {
    box(width - 0.2, 0.16, 0.22, 0x5a3a24, 0x3a2418, 0, ground + WALL_H - 0.12, z, 0xc48a52, 0.04, 0.1);
  }
  // 暖色槽光
  box(width - 0.6, 0.05, 0.12, 0xe8c48a, 0xe8c48a, 0, ground + WALL_H - 0.22, floor.minZ + 0.35, 0xffd9a0, 0.35, 0.55);
  box(width - 0.6, 0.05, 0.12, 0xe8c48a, 0xe8c48a, 0, ground + WALL_H - 0.22, floor.maxZ - 0.35, 0xffd9a0, 0.35, 0.55);

  // 朱紅柱列
  const colXs = [-2.35, 2.35];
  const colZs = [1.6, 4.6, 7.6, 10.4];
  colXs.forEach((x) => {
    colZs.forEach((z) => {
      cyl(0.2, 0.22, 3.7, 8, 0x8a2018, 0x5a1010, x, ground + 1.85, z, 0xd44840, 0.06, 0.14);
      box(0.5, 0.1, 0.5, 0x5a3a24, 0x3a2418, x, ground + 0.18, z);
      box(0.46, 0.1, 0.46, 0xd4a84b, 0xb8922e, x, ground + 3.72, z, 0xffd27a, 0.2, 0.35);
    });
  });

  function lantern(x, z) {
    cyl(0.03, 0.03, 0.45, 6, 0x5a3a24, 0x3a2418, x, ground + 3.55, z);
    sphere(0.22, 8, 0xffd9a0, 0xffc878, x, ground + 3.22, z, 0xffe2a8, reduced ? 0.35 : 0.55, 0.75);
    cyl(0.16, 0.16, 0.05, 8, 0x8a2018, 0x5a1010, x, ground + 3.42, z, 0xd44840, 0.1, 0.2);
  }
  lantern(-1.15, 3.2);
  lantern(1.15, 5.8);
  lantern(-1.15, 8.6);
  lantern(-4.6, 3.2);
  lantern(4.8, 6.8);

  function screen(x, z, yaw) {
    const frame = box(1.7, 1.55, 0.08, 0x5a3a24, 0x3a2418, x, ground + 0.95, z);
    box(1.5, 1.35, 0.03, 0xf7ead2, 0xd2c09a, x, ground + 0.95, z + 0.02, 0xffe6c0, 0.05, 0.1);
    if (yaw) frame.rotation.y = yaw;
  }
  screen(-3.55, 2.15, 0);
  screen(-3.55, 5.15, 0);
  screen(3.7, 2.2, 0);

  function bed(x, z) {
    box(1.15, 0.22, 2.15, 0x5a3a24, 0x3a2418, x, ground + 0.28, z);
    box(1.05, 0.16, 2.05, 0xf3e6d2, 0xd2c4aa, x, ground + 0.46, z, 0xffe6c0, 0.04, 0.08);
    box(0.42, 0.14, 0.32, 0xe8c8a0, 0xc8a880, x, ground + 0.6, z - 0.78);
    // 紗帳支架
    box(0.05, 1.7, 0.05, 0x5a3a24, 0x3a2418, x - 0.5, ground + 1.15, z - 1.0);
    box(0.05, 1.7, 0.05, 0x5a3a24, 0x3a2418, x + 0.5, ground + 1.15, z - 1.0);
    box(0.05, 1.7, 0.05, 0x5a3a24, 0x3a2418, x - 0.5, ground + 1.15, z + 1.0);
    box(0.05, 1.7, 0.05, 0x5a3a24, 0x3a2418, x + 0.5, ground + 1.15, z + 1.0);
    box(1.15, 0.04, 2.15, 0xf7ead2, 0xd2c09a, x, ground + 2.02, z, 0xffe6c0, 0.06, 0.12);
  }
  bed(-5.35, 3.3);
  bed(-5.35, 6.4);
  bed(5.35, 3.3);

  // 百子櫃（東牆）
  box(0.55, 2.35, 3.6, 0x6b3a2a, 0x4a2418, 6.85, ground + 1.25, 7.6);
  const drawerGeo = new THREE.BoxGeometry(0.08, 0.16, 0.28);
  geometries.push(drawerGeo);
  const drawerMat = mat(0xc4a06a, 0x8a6a42, 0xffd9a0, 0.04, 0.08);
  const dummy = new THREE.Object3D();
  const drawers = [];
  for (let row = 0; row < 8; row++) {
    for (let col = 0; col < 8; col++) {
      drawers.push([
        6.58,
        ground + 0.28 + row * 0.26,
        7.6 - 1.5 + col * 0.38
      ]);
    }
  }
  const drawerMesh = new THREE.InstancedMesh(drawerGeo, drawerMat, drawers.length);
  drawers.forEach((p, i) => {
    dummy.position.set(p[0], p[1], p[2]);
    dummy.updateMatrix();
    drawerMesh.setMatrixAt(i, dummy.matrix);
  });
  drawerMesh.instanceMatrix.needsUpdate = true;
  drawerMesh.castShadow = true;
  group.add(drawerMesh);
  box(0.62, 0.08, 3.7, 0xd4a84b, 0xb8922e, 6.85, ground + 2.48, 7.6, 0xffd27a, 0.18, 0.3);

  // 拔罐罐與托盤（東側床旁）
  box(0.85, 0.08, 0.55, 0x5a3a24, 0x3a2418, 4.85, ground + 0.62, 6.55);
  box(0.9, 0.55, 0.55, 0x6b3a2a, 0x4a2418, 4.85, ground + 0.32, 6.55);
  const jarXs = [4.58, 4.85, 5.12];
  jarXs.forEach((x, i) => {
    cyl(0.07, 0.09, 0.16, 8, 0x3a1c18, 0x2a1010, x, ground + 0.76, 6.48, 0x8a3028, 0.08, 0.14);
    cyl(0.07, 0.09, 0.16, 8, 0x5a1414, 0x3a0c0c, x, ground + 0.76, 6.66, 0xa83830, 0.1, 0.16);
  });

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

  function plaque(w, h, x, y, z, texture, woodNight) {
    const board = box(w, h, 0.06, 0xf7ead2, 0xd2c09a, x, y, z, 0xffe6c0, 0.06, 0.12);
    if (texture) {
      board.material.map = texture;
      board.material.emissiveMap = texture;
      board.material.emissive = new THREE.Color(0xffffff);
      board.material.userData.day = new THREE.Color(0xffffff);
      board.material.userData.night = new THREE.Color(0xfff0dc);
      board.material.needsUpdate = true;
    }
    box(w + 0.12, h + 0.12, 0.05, 0x6b3a2a, woodNight || 0x4a2418, x, y, z + 0.01);
    return board;
  }

  const chartTex = paintCanvas(512, 768, (ctx, canvas) => {
    ctx.fillStyle = '#f4e6cc';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.strokeStyle = '#6b3a2a';
    ctx.lineWidth = 10;
    ctx.strokeRect(16, 16, canvas.width - 32, canvas.height - 32);
    ctx.fillStyle = '#6b3a2a';
    ctx.textAlign = 'center';
    ctx.font = 'bold 48px "Noto Serif TC","Songti TC",serif';
    ctx.fillText('十四經脈圖', canvas.width / 2, 90);
    ctx.font = '28px "Noto Sans TC","PingFang TC",sans-serif';
    const meridians = ['手太陰肺經', '手陽明大腸經', '足陽明胃經', '足太陰脾經', '手少陰心經', '足太陽膀胱經'];
    meridians.forEach((name, i) => {
      const y = 170 + i * 85;
      ctx.strokeStyle = '#a42828';
      ctx.lineWidth = 4;
      ctx.beginPath();
      ctx.moveTo(80, y - 20);
      ctx.bezierCurveTo(160, y + 30, 280, y - 40, 420, y + 10);
      ctx.stroke();
      ctx.fillStyle = '#4a2418';
      ctx.fillText(name, canvas.width / 2, y + 36);
    });
  });
  plaque(1.35, 1.9, floor.minX + 0.22, ground + 2.15, 8.4, chartTex);
  plaque(1.35, 1.9, floor.minX + 0.22, ground + 2.15, 5.5, chartTex);

  const todayBoard = plaque(1.5, 1.15, 4.9, ground + 1.85, 9.85, null);
  todayBoard.rotation.y = -0.35;
  box(1.7, 0.08, 0.4, 0x5a3a24, 0x3a2418, 4.9, ground + 1.2, 9.95);

  function paintToday(model) {
    const texture = paintCanvas(768, 512, (ctx, canvas) => {
      ctx.fillStyle = '#f7ead2';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.strokeStyle = '#8a2018';
      ctx.lineWidth = 12;
      ctx.strokeRect(18, 18, canvas.width - 36, canvas.height - 36);
      ctx.fillStyle = '#8a2018';
      ctx.textAlign = 'center';
      ctx.font = 'bold 54px "Noto Serif TC","Songti TC",serif';
      ctx.fillText(model.title || '今日一穴', canvas.width / 2, 110);
      ctx.fillStyle = '#4a2418';
      ctx.font = '36px "Noto Sans TC","PingFang TC",sans-serif';
      if (model.subtitle) ctx.fillText(model.subtitle, canvas.width / 2, 180);
      ctx.font = '28px "Noto Sans TC","PingFang TC",sans-serif';
      if (model.dateLine) ctx.fillText(model.dateLine, canvas.width / 2, 230);
      ctx.font = '26px "Noto Sans TC","PingFang TC",sans-serif';
      const body = model.location || '';
      if (body) {
        const line = body.length > 22 ? body.slice(0, 22) + '…' : body;
        ctx.fillText(line, canvas.width / 2, 300);
      }
      ctx.fillStyle = '#7a6e64';
      ctx.font = '22px "Noto Sans TC","PingFang TC",sans-serif';
      ctx.fillText('教學參考，非診療建議', canvas.width / 2, 430);
    });
    if (!texture || !todayBoard.material) return;
    todayBoard.material.map = texture;
    todayBoard.material.emissiveMap = texture;
    todayBoard.material.emissive = new THREE.Color(0xffffff);
    todayBoard.material.userData.day = new THREE.Color(0xffffff);
    todayBoard.material.userData.night = new THREE.Color(0xfff0dc);
    todayBoard.material.needsUpdate = true;
  }

  const presetRecords = opts && opts.todayRecords;
  if (presetRecords) {
    paintToday(todayExhibitModel(presetRecords, (opts && opts.nowMs) || Date.now()));
  } else if (typeof fetch === 'function') {
    fetch(BRONZE_POINTS_URL).then(async (res) => {
      if (!res.ok) return;
      const records = await res.json();
      paintToday(todayExhibitModel(records, Date.now()));
    }).catch(() => {
      paintToday(todayExhibitModel([], Date.now()));
    });
  } else {
    paintToday(todayExhibitModel([], Date.now()));
  }

  const nameTex = paintCanvas(1024, 256, (ctx, canvas) => {
    ctx.fillStyle = '#8a2018';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#f7ead2';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = 'bold 92px "Noto Serif TC","Songti TC",serif';
    ctx.fillText(HALL_NAME, canvas.width / 2, canvas.height / 2 - 16);
    ctx.font = '36px "Noto Sans TC","PingFang TC",sans-serif';
    ctx.fillText('經絡研習室由此進入', canvas.width / 2, canvas.height / 2 + 70);
  });
  plaque(2.6, 0.7, 0, ground + 3.35, floor.maxZ - 0.18, nameTex);

  const studyTex = paintCanvas(768, 192, (ctx, canvas) => {
    ctx.fillStyle = '#6b3a2a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#f7ead2';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = 'bold 72px "Noto Serif TC","Songti TC",serif';
    ctx.fillText('經絡研習室', canvas.width / 2, canvas.height / 2);
  });
  plaque(1.7, 0.42, 0, ground + 2.72, northZ - 0.12, studyTex);

  // 北門前楓之谷風金／藍脈衝地圈（emissive，無 PointLight）
  const glowGold = mat(0xffe8a8, 0xb8922e, 0xffd050, 0.42, 0.7);
  const glowBlue = mat(0xa8d0f2, 0x204e96, 0x3cf0ff, 0.38, 0.65);
  const studyGlowZ = 9.85;
  const glowMats = [glowGold, glowBlue];
  cyl(1.12, 1.12, 0.022, 36, 0xffe8a8, 0xb8922e, 0, ground + 0.155, studyGlowZ, 0xffd050, 0.42, 0.7).material = glowGold;
  cyl(0.78, 0.78, 0.028, 36, 0xa8d0f2, 0x204e96, 0, ground + 0.162, studyGlowZ, 0x3cf0ff, 0.38, 0.65).material = glowBlue;

  const guideTex = paintCanvas(1024, 200, (ctx, canvas) => {
    ctx.fillStyle = '#163a70';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.strokeStyle = '#ecc440';
    ctx.lineWidth = 10;
    ctx.strokeRect(12, 12, canvas.width - 24, canvas.height - 24);
    ctx.fillStyle = '#f8fbff';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = 'bold 52px "Noto Sans TC","PingFang TC",sans-serif';
    ctx.fillText('↓ 經絡研習室（銅人在這）', canvas.width / 2, canvas.height / 2);
  });
  const guideY = ground + 3.05;
  const studyGuide = plaque(2.35, 0.48, 0, guideY, northZ - 0.22, guideTex);
  studyGuide.rotation.x = -0.08;

  let glowPhase = 0;

  function tick(dt) {
    if (reduced) return null;
    glowPhase += dt;
    const pulse = 0.55 + 0.4 * Math.sin(glowPhase * 2.35);
    for (let i = 0; i < glowMats.length; i++) {
      const base = glowMats[i].userData.dayE || 0.4;
      glowMats[i].emissiveIntensity = base * pulse;
    }
    studyGuide.position.y = guideY + 0.1 * Math.sin(glowPhase * 1.65);
    return null;
  }

  return {
    group: group,
    materials: materials,
    tick: tick,
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
