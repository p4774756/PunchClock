// 手搖店佔位室內。幾何對齊 island-interior.js 的 shop 地板與家具，離開時由呼叫端 dispose。

import { interiorByBuilding } from './island-interior.js';

export function createInterior(THREE, opts) {
  const room = interiorByBuilding('shop');
  const ground = (opts && opts.groundY != null) ? opts.groundY : 0.2;
  const floor = room.floor;
  const width = floor.maxX - floor.minX;
  const depth = floor.maxZ - floor.minZ;
  const cx = (floor.minX + floor.maxX) / 2;
  const cz = (floor.minZ + floor.maxZ) / 2;
  const wallH = 2.15;
  const wallT = 0.12;
  const materials = [];
  const geometries = [];

  function mat(color, night, glow) {
    const material = new THREE.MeshLambertMaterial({
      color: color,
      flatShading: true,
      emissive: new THREE.Color(glow || 0x000000),
      emissiveIntensity: 0
    });
    material.userData.day = new THREE.Color(color);
    material.userData.night = new THREE.Color(night);
    material.userData.dayE = 0;
    material.userData.nightE = glow ? 0.85 : 0;
    materials.push(material);
    return material;
  }

  function box(w, h, d, color, night, x, y, z, glow) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow));
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }

  const group = new THREE.Group();
  const wallY = ground + wallH / 2;
  box(width + wallT * 2, 0.1, depth + wallT * 2, 0xc4844a, 0x6a4630, cx, ground + 0.05, cz);
  box(width, 0.04, depth, 0xe7c8a0, 0x8a6844, cx, ground + 0.1, cz);

  box(wallT, wallH, depth + wallT, 0xfff7ef, 0x8ea0bf, floor.minX - wallT / 2, wallY, cz);
  box(wallT, wallH, depth + wallT, 0xfff7ef, 0x8ea0bf, floor.maxX + wallT / 2, wallY, cz);
  box(width + wallT * 2, wallH, wallT, 0xfff7ef, 0x8ea0bf, cx, wallY, floor.maxZ + wallT / 2);

  const doorW = 0.92;
  const southZ = floor.minZ - wallT / 2;
  const leftW = (width - doorW) / 2;
  box(leftW, wallH, wallT, 0xfff7ef, 0x8ea0bf, floor.minX + leftW / 2, wallY, southZ);
  box(leftW, wallH, wallT, 0xfff7ef, 0x8ea0bf, floor.maxX - leftW / 2, wallY, southZ);
  box(doorW + 0.16, 0.16, wallT + 0.02, 0xf3e2cc, 0x8d735c, 0, ground + wallH - 0.08, southZ);
  box(doorW * 0.92, 1.35, 0.06, 0xf0d2b0, 0x8a6848, -0.18, ground + 0.72, southZ + 0.02);

  const counter = room.blocks[0];
  const cw = counter.maxX - counter.minX;
  const cd = counter.maxZ - counter.minZ;
  const ccx = (counter.minX + counter.maxX) / 2;
  const ccz = (counter.minZ + counter.maxZ) / 2;
  box(cw, 0.78, cd, 0xf3e2cc, 0x8d735c, ccx, ground + 0.49, ccz);
  box(cw + 0.06, 0.06, cd + 0.06, 0xfffaf6, 0xa89888, ccx, ground + 0.9, ccz);

  const table = room.blocks[1];
  const tw = table.maxX - table.minX;
  const td = table.maxZ - table.minZ;
  const tcx = (table.minX + table.maxX) / 2;
  const tcz = (table.minZ + table.maxZ) / 2;
  box(0.12, 0.62, 0.12, 0xc4844a, 0x6a4630, tcx - tw * 0.32, ground + 0.41, tcz - td * 0.28);
  box(0.12, 0.62, 0.12, 0xc4844a, 0x6a4630, tcx + tw * 0.32, ground + 0.41, tcz + td * 0.28);
  box(tw, 0.08, td, 0xfff7ef, 0x8ea0bf, tcx, ground + 0.74, tcz);

  box(0.7, 0.55, 0.06, 0xb7e0ef, 0xffd7a4, floor.maxX + 0.02, ground + 1.25, 0.2, 0xffb15a);
  box(0.08, 0.08, 0.08, 0xf06a2a, 0xf06a2a, -0.28, ground + 0.72, southZ + 0.06);

  const light = new THREE.PointLight(0xffb066, 0, 7.5, 2);
  light.position.set(cx, ground + wallH - 0.15, cz);
  group.add(light);

  return {
    group: group,
    materials: materials,
    lamp: { light: light, power: 7 },
    dispose() {
      geometries.forEach((geometry) => geometry.dispose());
      materials.forEach((material) => {
        if (material.map) material.map.dispose();
        material.dispose();
      });
    }
  };
}
