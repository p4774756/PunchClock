import * as THREE from './vendor/three.module.min.js';
import {
  box,
  cyl,
  loadRepeatTex,
  COL,
  roundedRectShape,
  extrudedRoundedPlate
} from './private-team-scene-room.js';
import { privateTeamSeededRand } from './private-team-scene-window.js';
import { isLoungeTigerStatus, isWorkingTigerStatus, parseSecretaryOutfitId } from './private-team-scene-secretary.js';

export const SECRETARY_TUB_POSITION = { x: 0.02, y: 0, z: 0.56 };
export const SECRETARY_SOFA_POSITION = { x: 1.05, y: 0, z: -0.1 };
export const SECRETARY_SHADOW_TUB = { x: 0.06, y: 0.072, z: -0.42 };
export const SECRETARY_SHADOW_SOFA = { x: -0.04, y: 0.072, z: 0.18 };
export const SECRETARY_BUBBLE_TUB = { x: 0.56, z: 0.72 };
export const SECRETARY_BUBBLE_SOFA = { x: 1.18, z: 0.35 };

/**
 * 舞台服沙發立繪是去背坐姿（沒有畫上沙發），需縮小並相對
 * SECRETARY_SOFA_POSITION（與 3D 皮沙發同一錨點）偏移，讓坐線對上座墊。
 * 只用相對偏移，之後沙發錨點若移動，舞台服會跟著走。
 */
export const SECRETARY_STAGE_SOFA_SCALE = 0.82;
export const SECRETARY_STAGE_SOFA_OFFSET = { x: 0.02, y: -0.18, z: -0.03 };
export const SECRETARY_STAGE_SOFA_BUBBLE_OFFSET = { x: 0.12, z: 0.22 };
export const SECRETARY_STAGE_SOFA_BUBBLE_Y = 1.16;

export function secretarySofaPlacementForOutfit(outfitId) {
  const base = SECRETARY_SOFA_POSITION;
  if (parseSecretaryOutfitId(outfitId) !== 'stage') {
    return {
      position: { x: base.x, y: base.y, z: base.z },
      scale: 1,
      bubble: { x: SECRETARY_BUBBLE_SOFA.x, z: SECRETARY_BUBBLE_SOFA.z, y: null }
    };
  }
  return {
    position: {
      x: base.x + SECRETARY_STAGE_SOFA_OFFSET.x,
      y: base.y + SECRETARY_STAGE_SOFA_OFFSET.y,
      z: base.z + SECRETARY_STAGE_SOFA_OFFSET.z
    },
    scale: SECRETARY_STAGE_SOFA_SCALE,
    bubble: {
      x: base.x + SECRETARY_STAGE_SOFA_BUBBLE_OFFSET.x,
      z: base.z + SECRETARY_STAGE_SOFA_BUBBLE_OFFSET.z,
      y: SECRETARY_STAGE_SOFA_BUBBLE_Y
    }
  };
}

export function secretaryAtLoungePose(status, sofaSpriteOk) {
  void sofaSpriteOk;
  return isLoungeTigerStatus(status);
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
  ctx.fillStyle = '#3a3f48';
  ctx.fillRect(0, 0, 256, 256);
  const rand = privateTeamSeededRand(7541);
  for (let i = 0; i < 18; i++) {
    const gold = i % 4 === 0;
    ctx.strokeStyle = gold ? 'rgba(198, 178, 128, 0.32)' : 'rgba(205, 218, 235, 0.24)';
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

function makeLeatherGrainTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#8b5a32';
  ctx.fillRect(0, 0, 128, 128);
  const rand = privateTeamSeededRand(8841);
  for (let i = 0; i < 42; i++) {
    ctx.strokeStyle = 'rgba(42, 28, 16, ' + (0.08 + rand() * 0.12) + ')';
    ctx.lineWidth = 0.4 + rand() * 0.8;
    ctx.beginPath();
    let x = rand() * 128;
    let y = rand() * 128;
    ctx.moveTo(x, y);
    for (let j = 0; j < 4; j++) {
      x += (rand() - 0.5) * 28;
      y += (rand() - 0.5) * 10;
      ctx.lineTo(x, y);
    }
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(2.4, 2.4);
  tex.anisotropy = 4;
  return tex;
}

function makeWalnutWoodTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#3d2a1e';
  ctx.fillRect(0, 0, 128, 128);
  const rand = privateTeamSeededRand(4417);
  for (let row = 0; row < 14; row++) {
    const y = row * 9 + rand() * 3;
    const shade = 48 + (row % 3) * 6 + rand() * 8;
    ctx.strokeStyle = 'rgb(' + (shade + 18) + ',' + shade + ',' + (shade - 8) + ')';
    ctx.lineWidth = 1.2 + rand() * 2.2;
    ctx.beginPath();
    ctx.moveTo(0, y);
    for (let x = 0; x < 128; x += 8) {
      ctx.lineTo(x, y + Math.sin(x * 0.09 + row) * 2.5 + (rand() - 0.5) * 2);
    }
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(1.6, 1.2);
  tex.anisotropy = 4;
  return tex;
}

function makeVinylGrooveTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#141414';
  ctx.fillRect(0, 0, 128, 128);
  const cx = 64;
  const cy = 64;
  for (let r = 58; r > 14; r -= 2) {
    ctx.strokeStyle = r % 4 === 0 ? '#0a0a0a' : '#1c1c1c';
    ctx.lineWidth = 0.6;
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

function leatherMaterial(baseColor, grainTex) {
  return new THREE.MeshStandardMaterial({
    color: baseColor,
    map: grainTex,
    metalness: 0.06,
    roughness: 0.48
  });
}

function walnutMaterial(grainTex, color = 0xffffff) {
  return new THREE.MeshStandardMaterial({
    color,
    map: grainTex,
    metalness: 0.04,
    roughness: 0.62
  });
}

function extrudedPanel(hw, hh, depth, radius, mat) {
  const shape = roundedRectShape(hw, hh, radius);
  const geo = new THREE.ExtrudeGeometry(shape, {
    depth: depth,
    bevelEnabled: true,
    bevelThickness: 0.008,
    bevelSize: 0.008,
    bevelSegments: 2,
    curveSegments: 8
  });
  geo.center();
  return new THREE.Mesh(geo, mat);
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

  const leatherTex = makeLeatherGrainTexture();
  const leatherLight = leatherMaterial(0x8b5a32, leatherTex);
  const leatherDark = leatherMaterial(0x7a4a28, leatherTex);
  const seamMat = new THREE.MeshStandardMaterial({
    color: 0x5a3820,
    metalness: 0.02,
    roughness: 0.72
  });

  const sofaGrp = new THREE.Group();
  sofaGrp.name = '焦糖棕皮沙發';
  sofaGrp.position.set(1.05, 0, -0.1);
  const sofaBase = extrudedRoundedPlate(0.36, 0.22, 0.26, 0.055, leatherLight);
  sofaBase.position.set(0, 0.15, 0);
  sofaGrp.add(sofaBase);
  for (let si = -1; si <= 1; si++) {
    const cushion = extrudedRoundedPlate(0.115, 0.2, 0.045, 0.032, leatherLight);
    cushion.position.set(si * 0.235, 0.29, 0.02);
    sofaGrp.add(cushion);
    const seam = box(0.012, 0.22, 0.44, 0x5a3820);
    seam.material = seamMat;
    seam.position.set(si * 0.118, 0.2, 0);
    if (si < 1) sofaGrp.add(seam);
  }
  const sofaBack = extrudedPanel(0.36, 0.21, 0.1, 0.04, leatherDark);
  sofaBack.rotation.x = -Math.PI / 2;
  sofaBack.rotation.z = Math.PI;
  sofaBack.position.set(0, 0.36, -0.22);
  sofaGrp.add(sofaBack);
  const armGeoR = extrudedPanel(0.06, 0.16, 0.42, 0.035, leatherDark);
  armGeoR.rotation.y = Math.PI / 2;
  const sofaArmL = armGeoR.clone();
  sofaArmL.position.set(-0.33, 0.22, 0);
  sofaGrp.add(sofaArmL);
  const sofaArmR = armGeoR.clone();
  sofaArmR.position.set(0.33, 0.22, 0);
  sofaGrp.add(sofaArmR);
  root.add(sofaGrp);

  const sofaLamp = new THREE.Group();
  sofaLamp.name = 'sofaFloorLamp';
  sofaLamp.position.set(1.36, 0, -0.36);
  const sfBase = cyl(0.055, 0.062, 0.025, 10, COL.lampBase);
  sfBase.position.set(0, 0.012, 0);
  sofaLamp.add(sfBase);
  const sfPole = cyl(0.014, 0.014, 0.62, 8, COL.lampMetal);
  sfPole.position.set(0, 0.34, 0);
  sofaLamp.add(sfPole);
  const sfShade = cyl(0.14, 0.17, 0.11, 14, COL.lampShade);
  sfShade.position.set(0, 0.66, 0);
  sofaLamp.add(sfShade);
  const sfBulb = new THREE.PointLight(0xffc888, 0.62, 2.4, 2);
  sfBulb.position.set(0, 0.58, 0);
  sfBulb.name = 'sofaFloorLampBulb';
  sofaLamp.add(sfBulb);
  root.add(sofaLamp);

  addMonstera(root, 0.78, -0.42);

  const walnutTex = makeWalnutWoodTexture();
  const walnutBodyMat = walnutMaterial(walnutTex, 0xdddddd);
  const walnutTopMat = walnutMaterial(walnutTex, 0xeeeeee);

  const sb = new THREE.Group();
  sb.name = 'walnutSideboard';
  sb.position.set(0.92, 0, backZ + 0.38);
  const body = extrudedPanel(0.31, 0.14, 0.3, 0.04, walnutBodyMat);
  body.rotation.x = -Math.PI / 2;
  body.position.set(0, 0.14, 0);
  sb.add(body);
  const top = extrudedRoundedPlate(0.32, 0.17, 0.022, 0.028, walnutTopMat);
  top.position.set(0, 0.285, 0);
  sb.add(top);
  const doorLineMat = new THREE.MeshStandardMaterial({ color: 0x1e140e, metalness: 0.02, roughness: 0.8 });
  for (const dz of [-0.1, 0.1]) {
    const doorLine = box(0.002, 0.24, 0.26, 0x1e140e);
    doorLine.material = doorLineMat;
    doorLine.position.set(0, 0.14, dz);
    sb.add(doorLine);
  }
  const pullMat = new THREE.MeshStandardMaterial({ color: 0xc8b898, metalness: 0.35, roughness: 0.45 });
  for (const dx of [-0.22, 0.22]) {
    const pull = new THREE.Mesh(new THREE.CylinderGeometry(0.006, 0.006, 0.045, 10), pullMat);
    pull.rotation.z = Math.PI / 2;
    pull.position.set(dx, 0.14, 0.14);
    sb.add(pull);
  }
  const legMat = new THREE.MeshStandardMaterial({ color: 0x2a1e14, metalness: 0.05, roughness: 0.7 });
  for (const lx of [-0.26, 0.26]) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.02, 0.08, 12), legMat);
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
  const baseMat = new THREE.MeshStandardMaterial({ color: 0x2a2420, metalness: 0.12, roughness: 0.55 });
  const ttBase = extrudedRoundedPlate(0.14, 0.11, 0.038, 0.028, baseMat);
  ttBase.position.set(0, 0.019, 0);
  turntable.add(ttBase);
  const vinylGrooveTex = makeVinylGrooveTexture();
  const platter = new THREE.Mesh(
    new THREE.CylinderGeometry(0.11, 0.11, 0.012, 48),
    new THREE.MeshStandardMaterial({
      color: 0x1a1a1a,
      map: vinylGrooveTex,
      metalness: 0.08,
      roughness: 0.45
    })
  );
  platter.position.set(0, 0.05, 0);
  platter.name = 'vinylDisc';
  turntable.add(platter);
  const spindle = new THREE.Mesh(
    new THREE.CylinderGeometry(0.008, 0.01, 0.018, 12),
    new THREE.MeshStandardMaterial({ color: 0x888078, metalness: 0.55, roughness: 0.38 })
  );
  spindle.position.set(0, 0.064, 0);
  platter.add(spindle);
  const label = new THREE.Mesh(
    new THREE.CylinderGeometry(0.028, 0.028, 0.014, 24),
    new THREE.MeshStandardMaterial({ color: 0xc43c3c, roughness: 0.55, metalness: 0.02 })
  );
  label.position.set(0, 0.008, 0);
  platter.add(label);
  const armPivot = new THREE.Mesh(
    new THREE.CylinderGeometry(0.014, 0.014, 0.022, 12),
    new THREE.MeshStandardMaterial({ color: 0x5a544c, metalness: 0.4, roughness: 0.42 })
  );
  armPivot.position.set(0.1, 0.055, -0.06);
  turntable.add(armPivot);
  const armTube = new THREE.Mesh(
    new THREE.CylinderGeometry(0.006, 0.007, 0.12, 10),
    new THREE.MeshStandardMaterial({ color: 0x6a6058, metalness: 0.35, roughness: 0.48 })
  );
  armTube.rotation.x = 0.35;
  armTube.rotation.z = -0.55;
  armTube.position.set(0.105, 0.078, -0.02);
  turntable.add(armTube);
  const headshell = box(0.022, 0.014, 0.032, 0x4a443c);
  headshell.material = new THREE.MeshStandardMaterial({
    color: 0x4a443c,
    metalness: 0.25,
    roughness: 0.5
  });
  headshell.position.set(0.13, 0.072, 0.04);
  turntable.add(headshell);
  const knobMat = new THREE.MeshStandardMaterial({ color: 0x8a8078, metalness: 0.2, roughness: 0.52 });
  for (const kx of [-0.1, 0.08]) {
    const knob = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.008, 14), knobMat);
    knob.position.set(kx, 0.042, 0.08);
    turntable.add(knob);
  }
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