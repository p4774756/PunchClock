import * as THREE from './vendor/three.module.min.js';

const MAX_POINTS = 48;
const HIDDEN_Y = -999;

/** 跳水水花、氣泡與水面漣漪：一個 Points（48 粒）加兩圈環，行動裝置也負擔得起。 */
export function createDiveFx(root) {
  const group = new THREE.Group();
  group.name = 'secretaryDiveFx';
  group.visible = false;
  const pos = new Float32Array(MAX_POINTS * 3);
  const vel = new Float32Array(MAX_POINTS * 3);
  const life = new Float32Array(MAX_POINTS);
  const kind = new Uint8Array(MAX_POINTS);
  for (let i = 0; i < MAX_POINTS; i++) pos[i * 3 + 1] = HIDDEN_Y;
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const mat = new THREE.PointsMaterial({
    color: 0xe4f8ff,
    size: 0.055,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0.88,
    depthWrite: false
  });
  const points = new THREE.Points(geo, mat);
  points.frustumCulled = false;
  group.add(points);

  const rings = [];
  for (let i = 0; i < 2; i++) {
    const m = new THREE.Mesh(
      new THREE.RingGeometry(0.09, 0.115, 28),
      new THREE.MeshBasicMaterial({
        color: 0xdff6ff,
        transparent: true,
        opacity: 0,
        depthWrite: false,
        side: THREE.DoubleSide
      })
    );
    m.rotation.x = -Math.PI / 2;
    m.visible = false;
    m.userData.age = 1;
    group.add(m);
    rings.push(m);
  }
  root.add(group);

  let cursor = 0;
  let active = 0;
  function spawn(k, x, y, z, vx, vy, vz, ttl) {
    const i = cursor;
    cursor = (cursor + 1) % MAX_POINTS;
    pos[i * 3] = x;
    pos[i * 3 + 1] = y;
    pos[i * 3 + 2] = z;
    vel[i * 3] = vx;
    vel[i * 3 + 1] = vy;
    vel[i * 3 + 2] = vz;
    life[i] = ttl;
    kind[i] = k;
    group.visible = true;
  }

  return {
    group,
    /** 水花：由水面往上、往四周噴出的小水珠。 */
    splash(x, waterY, z, count) {
      const n = Math.min(count || 16, MAX_POINTS);
      for (let i = 0; i < n; i++) {
        const a = (i / n) * Math.PI * 2 + Math.random() * 0.5;
        const sp = 0.25 + Math.random() * 0.35;
        spawn(0, x, waterY + 0.02, z, Math.cos(a) * sp, 0.9 + Math.random() * 0.8, Math.sin(a) * sp, 0.85);
      }
      this.ring(x, waterY, z);
    },
    bubble(x, y, z) {
      spawn(1, x + (Math.random() - 0.5) * 0.18, y, z + (Math.random() - 0.5) * 0.12,
        (Math.random() - 0.5) * 0.04, 0.38 + Math.random() * 0.25, 0, 2.2);
    },
    ring(x, waterY, z) {
      let r = rings[0];
      for (let i = 0; i < rings.length; i++) {
        if (rings[i].userData.age >= 1) {
          r = rings[i];
          break;
        }
      }
      r.position.set(x, waterY + 0.012, z);
      r.userData.age = 0;
      r.visible = true;
      group.visible = true;
    },
    tick(dtSec, waterY) {
      const dt = Math.min(Math.max(dtSec, 0), 0.1);
      active = 0;
      for (let i = 0; i < MAX_POINTS; i++) {
        if (life[i] <= 0) continue;
        life[i] -= dt;
        if (life[i] <= 0) {
          pos[i * 3 + 1] = HIDDEN_Y;
          continue;
        }
        active++;
        if (kind[i] === 0) {
          vel[i * 3 + 1] -= 3.4 * dt;
          pos[i * 3] += vel[i * 3] * dt;
          pos[i * 3 + 1] += vel[i * 3 + 1] * dt;
          pos[i * 3 + 2] += vel[i * 3 + 2] * dt;
          if (pos[i * 3 + 1] < waterY && vel[i * 3 + 1] < 0) {
            life[i] = 0;
            pos[i * 3 + 1] = HIDDEN_Y;
          }
        } else {
          pos[i * 3] += vel[i * 3] * dt + Math.sin(life[i] * 7 + i) * 0.003;
          pos[i * 3 + 1] += vel[i * 3 + 1] * dt;
          if (pos[i * 3 + 1] > waterY) {
            life[i] = 0;
            pos[i * 3 + 1] = HIDDEN_Y;
          }
        }
      }
      geo.attributes.position.needsUpdate = true;
      let ringsOn = 0;
      for (let i = 0; i < rings.length; i++) {
        const r = rings[i];
        if (r.userData.age >= 1) continue;
        r.userData.age += dt / 0.95;
        const a = Math.min(r.userData.age, 1);
        const s = 1 + a * 5.5;
        r.scale.set(s, s, 1);
        r.material.opacity = 0.7 * (1 - a);
        if (a >= 1) r.visible = false;
        else ringsOn++;
      }
      if (!active && !ringsOn) group.visible = false;
    },
    clear() {
      for (let i = 0; i < MAX_POINTS; i++) {
        life[i] = 0;
        pos[i * 3 + 1] = HIDDEN_Y;
      }
      geo.attributes.position.needsUpdate = true;
      for (let i = 0; i < rings.length; i++) {
        rings[i].userData.age = 1;
        rings[i].visible = false;
      }
      group.visible = false;
    },
    dispose() {
      root.remove(group);
      geo.dispose();
      mat.dispose();
      for (let i = 0; i < rings.length; i++) {
        rings[i].geometry.dispose();
        rings[i].material.dispose();
      }
    }
  };
}
