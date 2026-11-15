import * as THREE from 'three';
import { G, P } from './island-kit.js';

export function buildIslandPeople(ctx) {
  const { scene, kit, idles, walkers } = ctx;
  const { addMesh, addBox, geo, addCup } = kit;
  const headGeo = geo('sph', [0.15, 7, 6]);
  const hairGeo = geo('sph', [0.155, 6, 5]);
  const legGeo = geo('box', [0.11, 0.28, 0.12]);
  const shoeGeo = geo('box', [0.12, 0.06, 0.16]);
  const torsoGeo = geo('box', [0.34, 0.36, 0.18]);
  const armGeo = geo('box', [0.09, 0.28, 0.09]);

  function makePerson(opts) {
    const group = new THREE.Group();
    const shirt = opts.shirt;
    const pants = opts.pants || 0x3e4a5a;
    const hair = opts.hair || 0x3a312c;
    if (opts.seated) {
      addBox(group, 0.12, 0.1, 0.28, pants, -0.08, 0.42, 0.14);
      addBox(group, 0.12, 0.1, 0.28, pants, 0.08, 0.42, 0.14);
      addMesh(group, legGeo, pants, -0.08, 0.18, 0.28);
      addMesh(group, legGeo, pants, 0.08, 0.18, 0.28);
      addMesh(group, shoeGeo, 0x2c2826, -0.08, 0.04, 0.32);
      addMesh(group, shoeGeo, 0x2c2826, 0.08, 0.04, 0.32);
      addMesh(group, torsoGeo, shirt, 0, 0.66, 0.02);
      addMesh(group, armGeo, shirt, -0.22, 0.6, 0.08);
      addMesh(group, armGeo, shirt, 0.22, 0.58, 0.12);
      addMesh(group, headGeo, 0xf3d0b0, 0, 0.98, 0.04);
      const hairMesh = addMesh(group, hairGeo, hair, 0, 1.05, 0);
      hairMesh.scale.set(1.02, 0.58, 1.05);
    } else {
      addMesh(group, legGeo, pants, -0.08, 0.2, 0);
      addMesh(group, legGeo, pants, 0.08, 0.2, 0);
      addMesh(group, shoeGeo, 0x2c2826, -0.08, 0.045, 0.03);
      addMesh(group, shoeGeo, 0x2c2826, 0.08, 0.045, 0.03);
      addMesh(group, torsoGeo, shirt, 0, 0.52, 0);
      addMesh(group, armGeo, shirt, -0.22, 0.5, 0);
      addMesh(group, armGeo, shirt, 0.22, 0.5, 0.02);
      addMesh(group, headGeo, 0xf3d0b0, 0, 0.86, 0);
      const hairMesh = addMesh(group, hairGeo, hair, 0, 0.94, -0.01);
      hairMesh.scale.set(1.02, 0.55, 1.05);
      if (opts.apron) addBox(group, 0.26, 0.28, 0.04, 0xf06a2a, 0, 0.46, 0.11);
      if (opts.hat) addMesh(group, geo('cyl', [0.17, 0.17, 0.08, 6, 0]), opts.hat, 0, 1.04, 0);
      if (opts.cup) addCup(group, opts.cup, 0.2, 0.46, 0.18, null);
    }
    group.scale.setScalar(opts.scale || 1.12);
    return group;
  }

  function placePerson(opts, x, z, rotY, idle) {
    const person = makePerson(opts);
    person.position.set(x, opts.seated ? G : P, z);
    person.rotation.y = rotY || 0;
    scene.add(person);
    if (idle) idles.push({ obj: person, base: person.position.y, phase: idle });
    return person;
  }

  placePerson({ shirt: 0xfff7ef, apron: true, hair: 0x3a312c }, 0.15, 0.42, 0, 0.2);
  placePerson({ shirt: 0xf4a0c0, hair: 0x6b4634 }, 0.05, 2.25, Math.PI, 0.8);
  placePerson({ shirt: 0x7ec8ea, hat: 0xf6d35a }, -0.05, 2.9, Math.PI, 1.4);
  placePerson({ shirt: 0x9fd9b4, hair: 0x3a312c }, 0.12, 3.55, Math.PI, 2.1);
  placePerson({ shirt: 0xf6d35a, seated: true, hair: 0x6b4634 }, -4.85, 4.15, Math.PI, 0.5);
  placePerson({ shirt: 0xf4a0c0, seated: true }, -4.85, 2.95, 0, 1.6);
  placePerson({ shirt: 0x7ec8ea, seated: true, hair: 0x3a312c }, 4.55, 4.08, Math.PI, 2.4);
  placePerson({ shirt: 0xf6d35a, hair: 0x6b4634, cup: 0xf4a0c0 }, -0.15, 8.45, 0.15, 0.9);

  function addWalker(shirt, path, speed, pingpong) {
    const person = makePerson({ shirt: shirt, hair: speed > 0 ? 0x3a312c : 0x6b4634 });
    scene.add(person);
    walkers.push({
      obj: person,
      path: path,
      speed: speed,
      pingpong: !!pingpong,
      offset: Math.random()
    });
  }
  addWalker(0xf08a3a, [
    [-5.6, 4.7], [-1.8, 6.35], [2.1, 6.4], [5.6, 4.3],
    [6.05, 1.0], [5.3, -3.3], [1.1, -5.15], [-3.5, -5.05],
    [-6.15, -1.6], [-6.3, 2.1]
  ], 0.045, false);
  addWalker(0x1f4d73, [[5.6, 1.15], [7.4, 1.6], [9.1, 1.95]], 0.05, true);
  return { makePerson: makePerson };
}
