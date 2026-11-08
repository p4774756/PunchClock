import * as THREE from 'three';

export type InstrumentTool = 'needle' | 'moxa' | 'stone' | 'finger' | 'scraper' | 'cup';

interface InstrumentState {
  tool: InstrumentTool;
  motion: THREE.Group;
  ember?: THREE.MeshStandardMaterial;
  suctionRing?: THREE.Mesh;
}

const STATE_KEY = '__tcmInstrument';

/** Metres. The root stays at the skin contact point; local +Z points out of the skin. */
export function createInstrument(tool: InstrumentTool): THREE.Group {
  const root = new THREE.Group();
  root.name = `instrument-${tool}`;
  const motion = new THREE.Group();
  motion.name = 'instrument-motion';
  root.add(motion);
  const state: InstrumentState = { tool, motion };
  root.userData[STATE_KEY] = state;
  root.userData.units = 'metres';
  root.userData.contactAxis = '+Z';
  root.userData.purpose = 'Visual teaching illustration; no clinical dose calculation.';

  const material = (color: THREE.ColorRepresentation, roughness = 0.5, metalness = 0) =>
    new THREE.MeshStandardMaterial({ color, roughness, metalness });

  const mesh = (geometry: THREE.BufferGeometry, surface: THREE.Material, name: string) => {
    const part = new THREE.Mesh(geometry, surface);
    part.name = name;
    part.castShadow = true;
    part.receiveShadow = true;
    motion.add(part);
    return part;
  };

  // CylinderGeometry's Y axis becomes the contact frame's Z axis.
  const cylinder = (radius: number, length: number, z: number, surface: THREE.Material, name: string) => {
    const part = mesh(new THREE.CylinderGeometry(radius, radius, length, 24), surface, name);
    part.rotation.x = Math.PI / 2;
    part.position.z = z;
    return part;
  };

  const ellipsoid = (x: number, y: number, z: number, surface: THREE.Material, name: string) => {
    const part = mesh(new THREE.SphereGeometry(1, 32, 20), surface, name);
    part.scale.set(x, y, z);
    return part;
  };

  switch (tool) {
    case 'needle': {
      const steel = material(0xdde5eb, 0.23, 0.94);
      const handle = material(0xc3a77a, 0.36, 0.8);
      const tip = mesh(new THREE.ConeGeometry(0.00065, 0.006, 16), steel, 'needle-tip');
      tip.rotation.x = -Math.PI / 2;
      tip.position.z = 0.003;
      cylinder(0.00065, 0.071, 0.0415, steel, 'needle-shaft');
      cylinder(0.0019, 0.05, 0.102, handle, 'needle-handle');
      const wire = new THREE.TorusGeometry(0.00196, 0.00025, 6, 20);
      for (let i = 0; i < 24; i += 1) {
        const ring = mesh(wire, handle, `handle-wire-${i}`);
        ring.position.z = 0.079 + i * 0.00194;
      }
      cylinder(0.00215, 0.003, 0.1285, handle, 'handle-cap');
      break;
    }
    case 'moxa': {
      const paper = material(0x9b7551, 0.93);
      const fibre = material(0x66543e, 1);
      const ash = material(0xb8ada0, 1);
      state.ember = new THREE.MeshStandardMaterial({
        color: 0xce6530, roughness: 1, emissive: 0xe5671b, emissiveIntensity: 0.4,
      });
      // The warm end always hovers at least 29 mm above the contact plane.
      cylinder(0.0078, 0.081, 0.0735, paper, 'moxa-paper');
      cylinder(0.0075, 0.004, 0.031, ash, 'moxa-ash');
      cylinder(0.0069, 0.0012, 0.0296, state.ember, 'moxa-glowing-end');
      cylinder(0.0075, 0.001, 0.1145, fibre, 'moxa-back');
      const seam = mesh(new THREE.BoxGeometry(0.0007, 0.0004, 0.071), fibre, 'paper-seam');
      seam.position.set(0, 0.0078, 0.073);
      // No flame is drawn on or near the skin.
      break;
    }
    case 'finger': {
      const skin = new THREE.MeshPhysicalMaterial({
        color: 0xd6a082, roughness: 0.6, metalness: 0, clearcoat: 0.06,
      });
      const nail = new THREE.MeshPhysicalMaterial({
        color: 0xf1c9b9, roughness: 0.28, metalness: 0, clearcoat: 0.35,
      });
      // Continuous tapered fingers in a familiar pointer-hand pose.
      const digit = (points: number[][], radii: number[], name: string) => {
        const curve = new THREE.CatmullRomCurve3(points.map(p => new THREE.Vector3(...p)));
        const geometry = new THREE.TubeGeometry(curve, 56, 1, 24, false);
        const positions = geometry.attributes.position;
        for (let i = 0; i <= 56; i++) {
          const t = i / 56, center = curve.getPointAt(t);
          const u = t * (radii.length - 1), j = Math.min(radii.length - 2, Math.floor(u));
          const radius = THREE.MathUtils.lerp(radii[j], radii[j + 1], u - j);
          for (let k = 0; k <= 24; k++) {
            const index = i * 25 + k;
            const v = new THREE.Vector3().fromBufferAttribute(positions, index).sub(center).multiplyScalar(radius).add(center);
            positions.setXYZ(index, v.x, v.y, v.z);
          }
        }
        geometry.computeVertexNormals();
        return mesh(geometry, skin, name);
      };
      const palm = ellipsoid(0.036, 0.017, 0.046, skin, 'hand-palm');
      palm.position.set(0.022, -0.001, 0.112);
      const wrist = ellipsoid(0.023, 0.015, 0.028, skin, 'hand-wrist');
      wrist.position.set(0.023, -0.001, 0.153);
      const indexPad = ellipsoid(0.008, 0.008, 0.008, skin, 'index-pad');
      indexPad.position.z = 0.008;
      digit([[0,0,0.008],[0,0,0.012],[0,0,0.033],[0.001,0,0.055],[0.003,-0.001,0.088]],
        [0.008,0.0085,0.009,0.0095,0.011], 'extended-index');
      for (let i = 0; i < 3; i++) {
        const x = 0.022 + i * 0.016, z = 0.079 + i * 0.007;
        digit([[x,0,z+0.025],[x,0.006,z],[x,-0.003,z-0.007],[x,-0.018,z+0.004],[x,-0.02,z+0.026]],
          [0.011-i*0.001,0.01-i*0.001,0.009-i*0.001,0.0075-i*0.0005,0.0001], `folded-finger-${i}`);
      }
      const thenar = ellipsoid(0.015, 0.016, 0.025, skin, 'thumb-thenar');
      thenar.position.set(-0.002,-0.006,0.113);
      digit([[-0.001,-0.003,0.121],[-0.018,-0.005,0.103],[-0.02,-0.01,0.084],[-0.008,-0.015,0.079]],
        [0.014,0.012,0.01,0.0001], 'folded-thumb');
      const plate = ellipsoid(0.0064, 0.0011, 0.009, nail, 'index-nail');
      plate.position.set(0,0.008,0.016);
      break;
    }
    case 'stone': {
      const stone = new THREE.MeshPhysicalMaterial({
        color: 0x353c3c, roughness: 0.27, metalness: 0.08,
        clearcoat: 0.58, clearcoatRoughness: 0.19,
      });
      const body = ellipsoid(0.023, 0.014, 0.046, stone, 'polished-stone');
      body.position.z = 0.046;
      break;
    }
    case 'scraper': {
      const jade = new THREE.MeshPhysicalMaterial({
        color: 0x6fbaa8, roughness: 0.28, metalness: 0,
        clearcoat: 0.78, clearcoatRoughness: 0.16,
        transparent: true, opacity: 0.94,
      });
      const outline = new THREE.Shape();
      outline.moveTo(-0.029, 0.014);
      outline.bezierCurveTo(-0.027, 0.004, -0.013, 0.001, -0.007, 0.001);
      outline.bezierCurveTo(0, 0.001, 0.014, 0.004, 0.027, 0.012);
      outline.bezierCurveTo(0.039, 0.02, 0.034, 0.042, 0.028, 0.058);
      outline.bezierCurveTo(0.021, 0.079, 0.008, 0.09, -0.006, 0.084);
      outline.bezierCurveTo(-0.017, 0.079, -0.009, 0.062, -0.015, 0.054);
      outline.bezierCurveTo(-0.025, 0.049, -0.04, 0.034, -0.029, 0.014);
      outline.closePath();
      const board = mesh(new THREE.ExtrudeGeometry(outline, {
        depth: 0.004, steps: 1, bevelEnabled: true,
        bevelThickness: 0.001, bevelSize: 0.001, bevelSegments: 3, curveSegments: 18,
      }), jade, 'jade-scraper');
      board.rotation.x = Math.PI / 4;
      board.position.y = 0.002;
      break;
    }
    case 'cup': {
      const glass = new THREE.MeshPhysicalMaterial({
        color: 0xdceff1, roughness: 0.09, metalness: 0,
        transparent: true, opacity: 0.18, depthWrite: false,
        side: THREE.DoubleSide, clearcoat: 1, clearcoatRoughness: 0.08,
        // Alpha transparency also works when no transmission render target is available.
      });
      const profile = [
        [0.027, 0], [0.03, 0.0015], [0.033, 0.013], [0.037, 0.035],
        [0.032, 0.06], [0.022, 0.076], [0, 0.08],
        [0, 0.077], [0.02, 0.073], [0.029, 0.058], [0.034, 0.035],
        [0.03, 0.013], [0.027, 0],
      ].map(([radius, height]) => new THREE.Vector2(radius, height));
      const cup = mesh(new THREE.LatheGeometry(profile, 56), glass, 'glass-cup-open-mouth');
      cup.rotation.x = Math.PI / 2;
      cup.castShadow = false;
      cup.renderOrder = 2;
      const rimMaterial = new THREE.MeshPhysicalMaterial({
        color: 0xe9f7f5, roughness: 0.12, transparent: true, opacity: 0.5,
        depthWrite: false, clearcoat: 1,
      });
      const rim = mesh(new THREE.TorusGeometry(0.0285, 0.0015, 10, 56), rimMaterial, 'cup-rim');
      rim.position.z = 0.0015;
      rim.castShadow = false;
      rim.renderOrder = 3;
      const ringMaterial = new THREE.MeshStandardMaterial({
        color: 0xc59087, roughness: 1, transparent: true, opacity: 0,
        depthWrite: false, side: THREE.DoubleSide,
      });
      state.suctionRing = mesh(new THREE.RingGeometry(0.0258, 0.0295, 56), ringMaterial, 'contact-indicator');
      state.suctionRing.position.z = 0.00035;
      state.suctionRing.castShadow = false;
      // Root-owned contact indicator stays on the skin when the cup gently settles.
      motion.remove(state.suctionRing);
      root.add(state.suctionRing);
      break;
    }
  }

  updateInstrument(root, 0, 0);
  return root;
}

/** time is seconds; activity is 0 (still) to 1 (moving). Allocates no geometry/materials. */
export function updateInstrument(group: THREE.Group, time: number, activity: number): void {
  const state = group.userData[STATE_KEY] as InstrumentState | undefined;
  if (!state) return;
  const t = Number.isFinite(time) ? time : 0;
  const a = Number.isFinite(activity) ? THREE.MathUtils.clamp(activity, 0, 1) : 0;
  const motion = state.motion;
  motion.position.set(0, 0, 0);
  motion.rotation.set(0, 0, 0);
  motion.scale.set(1, 1, 1);

  switch (state.tool) {
    case 'needle':
      // Stylized approach and shallow insertion, without a clinical depth scale.
      motion.position.z = 0.012 - 0.019 * Math.min(1, Math.max(0, t) / 1.4) * a;
      motion.rotation.z = Math.sin(t * 2.1) * 0.12 * a;
      break;
    case 'moxa':
      motion.position.x = Math.sin(t * 1.2) * 0.004 * a;
      motion.position.y = Math.cos(t * 1.2) * 0.003 * a;
      motion.position.z = (0.5 + 0.5 * Math.sin(t * 1.7)) * 0.003 * a;
      if (state.ember) state.ember.emissiveIntensity = 0.4 + (0.05 + Math.sin(t * 3) * 0.05) * a;
      break;
    case 'finger': {
      const press = (0.5 + 0.5 * Math.sin(t * 2.5)) * a;
      // Compress about the contact origin instead of penetrating the skin plane.
      motion.scale.set(1 + press * 0.018, 1 + press * 0.018, 1 - press * 0.025);
      break;
    }
    case 'stone':
      motion.position.x = Math.sin(t * 1.6) * 0.003 * a;
      motion.position.y = Math.sin(t * 3.2) * 0.0008 * a;
      motion.position.z = (0.5 + Math.sin(t * 2) * 0.5) * 0.0007 * a;
      break;
    case 'scraper':
      motion.position.x = Math.sin(t * 1.8) * 0.009 * a;
      motion.position.y = Math.sin(t * 1.8) * 0.0015 * a;
      motion.rotation.z = Math.sin(t * 1.8) * 0.03 * a;
      break;
    case 'cup':
      // Seated cup: no fire, clinical suction simulation, or repeated skin penetration.
      motion.scale.z = 1 - (0.004 + Math.sin(t * 1.5) * 0.002) * a;
      if (state.suctionRing) {
        (state.suctionRing.material as THREE.MeshStandardMaterial).opacity = 0.12 * a;
        state.suctionRing.visible = a > 0;
      }
      break;
  }
}
