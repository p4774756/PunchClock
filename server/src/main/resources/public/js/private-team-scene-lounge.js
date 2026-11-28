import * as THREE from './vendor/three.module.min.js';
import { box, cyl, loadRepeatTex, COL } from './private-team-scene-room.js';
import { privateTeamSeededRand } from './private-team-scene-window.js';
import { isLoungeTigerStatus, isWorkingTigerStatus } from './private-team-scene-secretary.js';

export const SECRETARY_TUB_POSITION = { x: 0.02, y: 0, z: 0.56 };
export const SECRETARY_SOFA_POSITION = { x: 1.05, y: 0, z: -0.1 };
export const SECRETARY_SHADOW_TUB = { x: 0.06, y: 0.072, z: -0.42 };
export const SECRETARY_SHADOW_SOFA = { x: -0.04, y: 0.072, z: 0.18 };
export const SECRETARY_BUBBLE_TUB = { x: 0.56, z: 0.72 };
export const SECRETARY_BUBBLE_SOFA = { x: 1.18, z: 0.35 };

export function secretaryAtLoungePose(status, sofaSpriteOk) {
  return !!sofaSpriteOk && isLoungeTigerStatus(status);
}

export function vinylShouldSpin(status, reducedMotion) {
  if (reducedMotion) return false;
  return isLoungeTigerStatus(status) && !isWorkingTigerStatus(status);
}

export { isLoungeTigerStatus, isWorkingTigerStatus };

export function makeMarbleFloorTexture(repeatX, repeatY) {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#1c1e22';
  ctx.fillRect(0, 0, 256, 256);
  const rand = privateTeamSeededRand(7541);
  for (let i = 0; i < 18; i++) {
    const gold = i % 4 === 0;
    ctx.strokeStyle = gold ? 'rgba(168, 148, 98, 0.22)' : 'rgba(175, 188, 205, 0.16)';
    ctx.lineWidth = 0.8 + rand() * 2.4;
    ctx.beginPath();
    let x = rand() * 256;
    let y = rand() * 256;
    ctx.moveTo(x, y);
    for (let j = 0; j < 7; j++) {
      x += (rand() - 0.5) * 72;
      y += (rand() - 0.5) * 72;
      ctx.lineTo(x, y);
    }
    ctx.stroke();
  }
  ctx.strokeStyle = 'rgba(32, 34, 38, 0.9)';
  ctx.lineWidth = 2.5;
  const tile = 128;
  for (let i = 1; i < 256 / tile; i++) {
    ctx.beginPath();
    ctx.moveTo(i * tile, 0);
    ctx.lineTo(i * tile, 256);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(0, i * tile);
    ctx.lineTo(256, i * tile);
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(repeatX, repeatY);
  tex.anisotropy = 4;
  return tex;
}

function addMonstera(root, x, z) {
  const pot = cyl(0.1, 0.082, 0.12, 10, 0x5a4a38);
  pot.position.set(x, 0.06, z);
  pot.name = 'monsteraPot';
  root.add(pot);
  const leafMat = new THREE.MeshLambertMaterial({ color: 0x2d6b3c });
  const holes = [0, 1.2, 2.4, 3.6, 4.8];
  for (let i = 0; i < holes.length; i++) {
    const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.09, 6, 5), leafMat);
    const a = holes[i];
    leaf.position.set(x + Math.cos(a) * 0.05, 0.2 + (i % 2) * 0.12, z + Math.sin(a) * 0.05);
    leaf.scale.set(1.2, 0.65, 1.05);
    leaf.name = 'monsteraLeaf';
    root.add(leaf);
  }
}

/**
 * 右側休憩區、後窗胡桃木矮櫃與黑膠；回傳唱盤轉動件與音符（供 tick 更新）。
 */
export function addLoungeFurnishings(root, texLoader, layout) {
  const backZ = layout.backZ;
  const winBottom = layout.winBottom;

  const rugTex = loadRepeatTex(texLoader, 'rug-weave.webp', 1.1, 1.35);
  const rugMat = new THREE.MeshLambertMaterial({ map: rugTex, color: 0xf2e0c8 });
  const rug = new THREE.Mesh(new THREE.PlaneGeometry(1.05, 0.88), rugMat);
  rug.rotation.x = -Math.PI / 2;
  rug.position.set(1.02, 0.038, -0.08);
  rug.name = '編織地毯';
  rug.renderOrder = 1;
  root.add(rug);

  const sofaBody = box(0.72, 0.28, 0.44, 0x8b5a32);
  sofaBody.position.set(1.05, 0.16, -0.1);
  sofaBody.name = '焦糖棕皮沙發';
  root.add(sofaBody);
  const sofaBack = box(0.72, 0.42, 0.12, 0x7a4a28);
  sofaBack.position.set(1.05, 0.36, -0.32);
  root.add(sofaBack);
  const sofaArmL = box(0.12, 0.32, 0.44, 0x7a4a28);
  sofaArmL.position.set(0.72, 0.22, -0.1);
  root.add(sofaArmL);
  const sofaArmR = box(0.12, 0.32, 0.44, 0x7a4a28);
  sofaArmR.position.set(1.38, 0.22, -0.1);
  root.add(sofaArmR);

  addMonstera(root, 0.78, -0.42);

  const sb = new THREE.Group();
  sb.name = 'walnutSideboard';
  sb.position.set(0.92, 0, backZ + 0.38);
  const body = box(0.62, 0.28, 0.32, 0x3d2a1e);
  body.position.set(0, 0.14, 0);
  sb.add(body);
  const top = box(0.64, 0.02, 0.34, 0x4a3528);
  top.position.set(0, 0.285, 0);
  sb.add(top);
  const legMat = new THREE.MeshLambertMaterial({ color: 0x2a1e14 });
  for (const lx of [-0.26, 0.26]) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.022, 0.08, 6), legMat);
    leg.position.set(lx, 0.04, 0.12);
    sb.add(leg);
    const leg2 = leg.clone();
    leg2.position.set(lx, 0.04, -0.12);
    sb.add(leg2);
  }
  root.add(sb);

  const turntable = new THREE.Group();
  turntable.name = 'vinylTurntable';
  turntable.position.set(0.92, 0.3, backZ + 0.38);
  const ttBase = box(0.28, 0.04, 0.22, 0x2a2420);
  ttBase.position.set(0, 0.02, 0);
  turntable.add(ttBase);
  const platter = new THREE.Mesh(
    new THREE.CylinderGeometry(0.11, 0.11, 0.012, 24),
    new THREE.MeshLambertMaterial({ color: 0x1a1a1a })
  );
  platter.position.set(0, 0.05, 0);
  platter.name = 'vinylDisc';
  turntable.add(platter);
  const label = new THREE.Mesh(
    new THREE.CylinderGeometry(0.028, 0.028, 0.014, 16),
    new THREE.MeshLambertMaterial({ color: 0xc43c3c })
  );
  label.position.set(0, 0.056, 0);
  platter.add(label);
  const arm = box(0.04, 0.02, 0.14, 0x6a6058);
  arm.position.set(0.12, 0.07, -0.02);
  arm.rotation.y = -0.35;
  turntable.add(arm);
  root.add(turntable);

  const lampGrp = new THREE.Group();
  lampGrp.name = 'loungeLamp';
  lampGrp.position.set(1.18, 0.3, backZ + 0.38);
  const lampBase = cyl(0.045, 0.05, 0.04, 8, COL.lampBase);
  lampBase.position.set(0, 0.02, 0);
  lampGrp.add(lampBase);
  const stem = cyl(0.012, 0.012, 0.14, 6, COL.lampMetal);
  stem.position.set(0, 0.1, 0);
  lampGrp.add(stem);
  const shade = cyl(0.09, 0.11, 0.08, 12, COL.lampShade);
  shade.position.set(0, 0.2, 0);
  lampGrp.add(shade);
  const bulb = new THREE.PointLight(0xffd8a8, 0.35, 0.9, 2);
  bulb.position.set(0, 0.16, 0);
  bulb.name = 'loungeLampBulb';
  lampGrp.add(bulb);
  root.add(lampGrp);

  const noteMat = new THREE.MeshBasicMaterial({
    color: 0xe8d8b0,
    transparent: true,
    opacity: 0.42,
    depthWrite: false
  });
  const notes = [];
  for (let ni = 0; ni < 3; ni++) {
    const note = new THREE.Mesh(new THREE.PlaneGeometry(0.035, 0.05), noteMat.clone());
    note.position.set(1.02 + ni * 0.06, 0.42 + ni * 0.05, backZ + 0.42);
    note.name = 'vinylNote';
    note.visible = false;
    root.add(note);
    notes.push(note);
  }

  return {
    vinylDisc: platter,
    vinylNotes: notes,
    sideboardMaxX: 0.92 + 0.32,
    handheldClearX: 0.51
  };
}

export function tickVinylSpin(platter, notes, status, reducedMotion, elapsed, dt) {
  const spin = vinylShouldSpin(status, reducedMotion);
  if (platter) {
    platter.rotation.y += spin ? dt * 1.15 : 0;
  }
  const showNotes = spin && !reducedMotion;
  if (notes) {
    for (let i = 0; i < notes.length; i++) {
      const n = notes[i];
      n.visible = showNotes;
      if (showNotes) {
        n.position.y = 0.4 + i * 0.05 + Math.sin(elapsed * 1.6 + i) * 0.025;
        n.material.opacity = 0.28 + ((Math.sin(elapsed * 2.2 + i) + 1) / 2) * 0.18;
      }
    }
  }
}