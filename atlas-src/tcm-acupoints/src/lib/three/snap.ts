import * as THREE from "three";

const SURFACE_LIFT = 0.012;
/** Cones from tightest to loosest — keep dots on the intended body side. */
const DIRECTION_CONES = [0.94, 0.82, 0.6, 0.2, -1.1];

type Candidate = {
  distance: number;
  mesh: THREE.Mesh;
  index: number;
  point: THREE.Vector3;
};

/**
 * Snap authored positions onto the mesh shell (anatomy-atelier technique).
 * Direction-cone filter prevents piercing through to the far side.
 */
export function snapPointsToSurface(
  targets: THREE.Vector3[],
  pivot: THREE.Object3D,
  meshes: THREE.Mesh[],
  lift = SURFACE_LIFT,
): THREE.Vector3[] {
  if (!targets.length) return [];
  if (!meshes.length) return targets.map((t) => t.clone());

  const directions = targets.map((target) => {
    const d = target.clone();
    // Midline points: prefer anterior (+Z) or posterior (−Z) from authored z.
    if (Math.abs(d.x) < 0.02 && Math.abs(d.z) < 0.02) {
      return new THREE.Vector3(0, 0, target.z >= 0 ? 1 : -1);
    }
    // Prefer lateral direction in XZ, keep some Y so limbs resolve correctly.
    d.y *= 0.35;
    return d.lengthSq() > 1e-8 ? d.normalize() : new THREE.Vector3(0, 0, 1);
  });

  const tiers: (Candidate | null)[][] = targets.map(() => DIRECTION_CONES.map(() => null));

  pivot.updateWorldMatrix(true, true);
  const toPivot = new THREE.Matrix4().copy(pivot.matrixWorld).invert();
  const local = new THREE.Matrix4();
  const vertex = new THREE.Vector3();

  // Stride vertices on dense meshes for interactive load times.
  const totalVerts = meshes.reduce((n, m) => n + (m.geometry.getAttribute("position")?.count ?? 0), 0);
  const stride = totalVerts > 180_000 ? 3 : totalVerts > 80_000 ? 2 : 1;

  for (const mesh of meshes) {
    const position = mesh.geometry.getAttribute("position");
    if (!position) continue;
    local.multiplyMatrices(toPivot, mesh.matrixWorld);

    for (let i = 0; i < position.count; i += stride) {
      vertex.fromBufferAttribute(position, i).applyMatrix4(local);
      const radius = vertex.length();
      for (let h = 0; h < targets.length; h += 1) {
        const distance = vertex.distanceToSquared(targets[h]);
        // Soft height gate: reject vertices more than ~22cm away in Y.
        const dy = Math.abs(vertex.y - targets[h].y);
        if (dy > 0.22) continue;
        const cosine = radius > 1e-5 ? vertex.dot(directions[h]) / (radius * directions[h].length() || 1) : 1;
        for (let t = 0; t < DIRECTION_CONES.length; t += 1) {
          if (cosine < DIRECTION_CONES[t]) continue;
          const best = tiers[h][t];
          if (best && best.distance <= distance) continue;
          if (best) {
            best.distance = distance;
            best.mesh = mesh;
            best.index = i;
            best.point.copy(vertex);
          } else {
            tiers[h][t] = { distance, mesh, index: i, point: vertex.clone() };
          }
        }
      }
    }
  }

  const normal = new THREE.Vector3();
  const normalMatrix = new THREE.Matrix3();

  return targets.map((target, h) => {
    const chosen = tiers[h].find(Boolean);
    if (!chosen) {
      // Fallback: push outward from torso axis.
      const fallback = target.clone();
      const lateral = new THREE.Vector3(fallback.x, 0, fallback.z);
      if (lateral.lengthSq() < 1e-6) lateral.set(0, 0, fallback.z >= 0 ? 1 : -1);
      else lateral.normalize();
      return fallback.addScaledVector(lateral, lift * 2);
    }

    const normals = chosen.mesh.geometry.getAttribute("normal");
    if (normals) {
      local.multiplyMatrices(toPivot, chosen.mesh.matrixWorld);
      normalMatrix.getNormalMatrix(local);
      normal.fromBufferAttribute(normals, chosen.index).applyMatrix3(normalMatrix).normalize();
    } else {
      normal.copy(chosen.point).normalize();
    }
    if (normal.dot(chosen.point) < 0) normal.negate();
    // Prefer outward relative to authored direction when normal is ambiguous.
    if (normal.dot(directions[h]) < 0) normal.negate();
    return chosen.point.clone().addScaledVector(normal, lift);
  });
}

/** Snap a polyline (meridian path) onto the surface, point by point. */
export function snapPathToSurface(
  path: [number, number, number][],
  pivot: THREE.Object3D,
  meshes: THREE.Mesh[],
): [number, number, number][] {
  const targets = path.map((p) => new THREE.Vector3(...p));
  return snapPointsToSurface(targets, pivot, meshes, 0.008).map(
    (v) => [v.x, v.y, v.z] as [number, number, number],
  );
}
