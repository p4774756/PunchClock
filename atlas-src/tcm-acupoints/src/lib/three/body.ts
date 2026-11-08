import * as THREE from "three";

/**
 * Smooth medical-mannequin body (procedural fallback).
 * Height ≈ 1.72, origin at pelvis, facing +Z.
 */
export function createBodyFigure(): THREE.Group {
  const root = new THREE.Group();
  root.name = "body-figure";

  const skin = new THREE.MeshPhysicalMaterial({
    color: 0xe2c6b0,
    roughness: 0.48,
    metalness: 0.02,
    clearcoat: 0.18,
    clearcoatRoughness: 0.55,
    sheen: 0.3,
    sheenColor: new THREE.Color(0xf0d2c0),
    flatShading: false,
  });

  const add = (
    geo: THREE.BufferGeometry,
    x: number,
    y: number,
    z: number,
    sx = 1,
    sy = 1,
    sz = 1,
    rx = 0,
    ry = 0,
    rz = 0,
  ) => {
    const mesh = new THREE.Mesh(geo, skin);
    mesh.position.set(x, y, z);
    mesh.scale.set(sx, sy, sz);
    mesh.rotation.set(rx, ry, rz);
    root.add(mesh);
    return mesh;
  };

  // Head / neck
  add(new THREE.SphereGeometry(0.092, 48, 36), 0, 0.73, 0.01, 1, 1.18, 1.05);
  add(new THREE.CylinderGeometry(0.036, 0.042, 0.09, 32), 0, 0.6, 0.01);

  // Torso — smooth ellipsoids
  add(new THREE.SphereGeometry(0.155, 40, 28), 0, 0.3, 0, 1.12, 1.55, 0.78);
  add(new THREE.SphereGeometry(0.135, 36, 26), 0, 0.04, 0, 1.2, 1.15, 0.82);
  add(new THREE.SphereGeometry(0.118, 32, 24), 0, -0.12, 0, 1.28, 0.88, 0.92);

  // Arms (A-pose-ish, slightly out)
  const upper = new THREE.CapsuleGeometry(0.034, 0.26, 8, 16);
  const fore = new THREE.CapsuleGeometry(0.028, 0.24, 8, 16);
  add(upper, 0.26, 0.3, 0, 1, 1, 1, 0, 0, -0.55);
  add(upper, -0.26, 0.3, 0, 1, 1, 1, 0, 0, 0.55);
  add(fore, 0.4, 0.05, 0.02, 1, 1, 1, 0, 0, -0.25);
  add(fore, -0.4, 0.05, 0.02, 1, 1, 1, 0, 0, 0.25);
  add(new THREE.SphereGeometry(0.032, 20, 16), 0.5, -0.16, 0.04, 1.15, 0.7, 0.55);
  add(new THREE.SphereGeometry(0.032, 20, 16), -0.5, -0.16, 0.04, 1.15, 0.7, 0.55);

  // Legs
  const thigh = new THREE.CapsuleGeometry(0.052, 0.28, 8, 18);
  const shin = new THREE.CapsuleGeometry(0.038, 0.28, 8, 16);
  add(thigh, 0.08, -0.36, 0);
  add(thigh, -0.08, -0.36, 0);
  add(shin, 0.08, -0.68, 0);
  add(shin, -0.08, -0.68, 0);
  add(new THREE.SphereGeometry(0.038, 20, 14), 0.08, -0.86, 0.045, 1.15, 0.55, 1.85);
  add(new THREE.SphereGeometry(0.038, 20, 14), -0.08, -0.86, 0.045, 1.15, 0.55, 1.85);

  return root;
}

export function createMeridianLine(
  path: [number, number, number][],
  color: string,
  bilateral = true,
): THREE.Group {
  const group = new THREE.Group();
  group.name = "meridian-line";

  const makeLine = (pts: [number, number, number][]) => {
    if (pts.length < 2) return;
    const curve = new THREE.CatmullRomCurve3(pts.map((p) => new THREE.Vector3(...p)));
    const tubular = new THREE.TubeGeometry(curve, 64, 0.0045, 8, false);
    const mat = new THREE.MeshBasicMaterial({
      color: new THREE.Color(color),
      transparent: true,
      opacity: 0.78,
      depthWrite: false,
    });
    const mesh = new THREE.Mesh(tubular, mat);
    mesh.renderOrder = 5;
    group.add(mesh);

    const glowGeo = new THREE.TubeGeometry(curve, 48, 0.012, 8, false);
    const glowMat = new THREE.MeshBasicMaterial({
      color: new THREE.Color(color),
      transparent: true,
      opacity: 0.2,
      depthWrite: false,
    });
    const glow = new THREE.Mesh(glowGeo, glowMat);
    glow.renderOrder = 4;
    group.add(glow);
  };

  makeLine(path);
  if (bilateral && path.some(([x]) => Math.abs(x) > 0.01)) {
    makeLine(path.map(([x, y, z]) => [-x, y, z] as [number, number, number]));
  }

  return group;
}
