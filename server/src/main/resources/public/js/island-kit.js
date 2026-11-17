// 共用場景工具：材質／幾何快取、招牌畫布、杯子與燈。快取 key 與材質參數維持原樣。
export const G = 0.2;
export const P = G + 0.055;
export const HEX = Math.PI / 6;

export const NIGHT = Object.freeze({
  0xa8d06e: 0x3d5d42,
  0x93c462: 0x355538,
  0xc5e098: 0x4d6a45,
  0x7eae58: 0x314e36,
  0x6fa84e: 0x2d4c34,
  0x8fbf62: 0x3a5a3c,
  0x4f8a45: 0x243e2c,
  0x5e9a48: 0x2a4632,
  0xf4e6cf: 0x6e6252,
  0xe4d2b2: 0x5c5346,
  0xfff7ef: 0xd4ccc2,
  0xf3e2cc: 0xc4b6a4,
  0xf06a2a: 0xe07a3a,
  0xd4531c: 0xc4622a,
  0x1f4d73: 0x17324e,
  0x45c4c6: 0x12325f,
  0xe7dcd0: 0xb7aea2,
  0xcfc3b2: 0x8e8578,
  0xd2a56c: 0x8a6844,
  0xb8884e: 0x6d4e32,
  0xc4844a: 0x8a5a32,
  0xa56b38: 0x6e4528,
  0xf0ddb4: 0x8d7b5c,
  0xfffaf6: 0xe6ddd4,
  0x5b3a8c: 0x2e1c4c,
  0x3d2468: 0x1c1234,
  0xd4a84b: 0x8a6a2e,
  0xf0d078: 0xb08a40,
  0x2a1848: 0x140c28,
  0x7a54b5: 0x3a2860
});

/** 對照表有就回傳夜間色，沒有回傳 null。不需要 THREE。 */
export function nightHex(hex) {
  if (Object.prototype.hasOwnProperty.call(NIGHT, hex)) return NIGHT[hex];
  return null;
}

export function createSceneKit(THREE, opts) {
  const scene = opts && opts.scene;
  const bobs = (opts && opts.bobs) || [];
  const cache = new Map();
  const geos = new Map();
  const materials = [];
  const lamps = [];

  function nightOf(hex) {
    const mapped = nightHex(hex);
    if (mapped !== null) return new THREE.Color(mapped);
    const c = new THREE.Color(hex);
    const hsl = { h: 0, s: 0, l: 0 };
    c.getHSL(hsl);
    const out = new THREE.Color();
    out.setHSL(hsl.h, Math.min(1, hsl.s * 0.78), 0.12 + hsl.l * 0.42);
    return out;
  }

  function mat(color, extra) {
    extra = extra || {};
    const key = extra.map ? '' : [
      color,
      extra.night != null ? extra.night : '',
      extra.glow || 0,
      extra.glowAmount == null ? '' : extra.glowAmount,
      extra.opacity == null ? 1 : extra.opacity,
      extra.side || 0
    ].join('|');
    if (key && cache.has(key)) return cache.get(key);
    const material = new THREE.MeshLambertMaterial({
      color: color,
      flatShading: true,
      map: extra.map || null,
      transparent: extra.opacity != null && extra.opacity < 1,
      opacity: extra.opacity == null ? 1 : extra.opacity,
      side: extra.side || THREE.FrontSide,
      emissive: new THREE.Color(extra.glow || 0x000000),
      emissiveIntensity: 0
    });
    material.userData.day = new THREE.Color(color);
    material.userData.night = new THREE.Color(extra.night != null ? extra.night : nightOf(color));
    material.userData.dayE = 0;
    material.userData.nightE = extra.glow ? (extra.glowAmount == null ? 0.85 : extra.glowAmount) : 0;
    materials.push(material);
    if (key) cache.set(key, material);
    return material;
  }

  function geo(kind, args) {
    const key = kind + ':' + args.join(',');
    let geometry = geos.get(key);
    if (!geometry) {
      if (kind === 'box') geometry = new THREE.BoxGeometry(args[0], args[1], args[2]);
      else if (kind === 'cyl') geometry = new THREE.CylinderGeometry(args[0], args[1], args[2], args[3], 1, false, args[4] || 0);
      else if (kind === 'sph') geometry = new THREE.SphereGeometry(args[0], args[1], args[2]);
      else if (kind === 'cone') geometry = new THREE.ConeGeometry(args[0], args[1], args[2]);
      geos.set(key, geometry);
    }
    return geometry;
  }

  function addMesh(parent, geometry, color, x, y, z, extra) {
    const mesh = new THREE.Mesh(geometry, mat(color, extra));
    mesh.position.set(x, y, z);
    mesh.castShadow = !extra || extra.shadow !== false;
    mesh.receiveShadow = !extra || extra.receive !== false;
    if (extra && extra.scale) mesh.scale.set(extra.scale[0], extra.scale[1], extra.scale[2]);
    if (extra && extra.rot) mesh.rotation.set(extra.rot[0], extra.rot[1], extra.rot[2]);
    parent.add(mesh);
    return mesh;
  }

  function addBox(parent, w, h, d, color, x, y, z, extra) {
    return addMesh(parent, geo('box', [w, h, d]), color, x, y, z, extra);
  }

  function paint(w, h, draw) {
    const board = document.createElement('canvas');
    board.width = w;
    board.height = h;
    draw(board.getContext('2d'), w, h);
    const texture = new THREE.CanvasTexture(board);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 8;
    return texture;
  }

  function picture(parent, w, h, texture, x, y, z, rotY, glowAmount, tiltX) {
    const group = new THREE.Group();
    group.position.set(x, y, z);
    if (rotY) group.rotation.y = rotY;
    if (tiltX) group.rotation.x = tiltX;
    addBox(group, w, h, 0.08, 0xf06a2a, 0, 0, -0.02);
    const plane = new THREE.Mesh(
      new THREE.PlaneGeometry(w * 0.9, h * 0.82),
      mat(0xffffff, {
        map: texture,
        night: 0xfff0dc,
        glow: 0xffb15a,
        glowAmount: glowAmount == null ? 0.28 : glowAmount,
        side: THREE.DoubleSide
      })
    );
    plane.position.z = 0.04;
    plane.castShadow = false;
    plane.receiveShadow = false;
    group.add(plane);
    parent.add(group);
    return group;
  }

  const cupBody = geo('cyl', [0.13, 0.105, 0.36, 7, 0]);
  const cupLid = geo('cyl', [0.138, 0.138, 0.045, 7, 0]);
  const cupStraw = geo('cyl', [0.016, 0.016, 0.24, 4, 0]);

  function addCup(parent, color, x, y, z, phase) {
    const group = new THREE.Group();
    addMesh(group, cupBody, color, 0, 0.18, 0);
    addMesh(group, cupLid, 0xfffaf6, 0, 0.38, 0);
    const straw = addMesh(group, cupStraw, 0x3a312c, 0.03, 0.52, 0);
    straw.rotation.z = 0.22;
    group.position.set(x, y, z);
    parent.add(group);
    if (phase != null) bobs.push({ obj: group, base: y, phase: phase, amp: 0.03 });
    return group;
  }

  function lampLight(x, y, z, power, parent) {
    const holder = parent || scene;
    const bulb = addMesh(holder, geo('sph', [0.09, 6, 5]), 0xfff6d4, x, y, z, {
      night: 0xffe2b0,
      glow: 0xffb066,
      glowAmount: Math.min(1.25, 0.05 + power * 0.06),
      shadow: false,
      receive: false
    });
    bulb.scale.setScalar(0.001);
    const item = { mesh: bulb, power: power, light: null };
    lamps.push(item);
    return item;
  }

  function addMaterial(material) {
    materials.push(material);
  }

  function removeMaterial(material) {
    const at = materials.indexOf(material);
    if (at >= 0) materials.splice(at, 1);
  }

  function addLamp(item) {
    lamps.push(item);
  }

  function removeLamp(item) {
    const at = lamps.indexOf(item);
    if (at >= 0) lamps.splice(at, 1);
  }

  function forEachMaterial(fn) {
    for (let i = 0; i < materials.length; i++) fn(materials[i]);
  }

  function forEachLamp(fn) {
    for (let i = 0; i < lamps.length; i++) fn(lamps[i]);
  }

  return {
    mat: mat,
    geo: geo,
    addMesh: addMesh,
    addBox: addBox,
    paint: paint,
    picture: picture,
    addCup: addCup,
    lampLight: lampLight,
    addMaterial: addMaterial,
    removeMaterial: removeMaterial,
    addLamp: addLamp,
    removeLamp: removeLamp,
    forEachMaterial: forEachMaterial,
    forEachLamp: forEachLamp
  };
}
