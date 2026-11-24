#!/usr/bin/env node
/** Avatar switch cache: teaching overlay, models, and study decor stay on the active scene. */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import * as T from 'three';

const {
  detachCharacterModels,
  reparentCharacterModel,
  restoreTeachingOverlay,
  stashTeachingOverlay,
} = await import('../app/avatar-scene-restore.ts');
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const sceneSrc = readFileSync(join(root, 'app/character-scene.tsx'), 'utf8');
assert.match(sceneSrc, /stashTeachingOverlay/, 'stash must detach teaching overlay');
assert.match(sceneSrc, /restoreTeachingOverlay/, 'restore must reattach teaching overlay');
assert.match(sceneSrc, /reparentCharacterModel/, 'restore must reparent cached models');
assert.match(sceneSrc, /syncResolvedPoints/, 'adult overlay must follow bone bindings');
assert.match(sceneSrc, /buildAdultPointBindings\(vrm,avatar,characterRoot\)/, 'adult bindings after model on characterRoot');
assert.match(sceneSrc, /studyDecor\.group/, 'each scene mount must add study decor');

const host = {
  children: [],
  contains(node) {
    return this.children.includes(node);
  },
  appendChild(node) {
    if (!this.children.includes(node)) this.children.push(node);
    node.parentElement = this;
    return node;
  },
};

const makeLabel = () => ({
  className: 'contact-label tool-drag-handle',
  hidden: false,
  parentElement: null,
  remove() {
    if (this.parentElement?.children) {
      const idx = this.parentElement.children.indexOf(this);
      if (idx >= 0) this.parentElement.children.splice(idx, 1);
    }
    this.parentElement = null;
  },
});

const makeTeaching = (scene) => {
  const root = new T.Group();
  scene.add(root);
  const dot = new T.Mesh(new T.SphereGeometry(0.01, 8, 6), new T.MeshBasicMaterial());
  dot.visible = true;
  root.add(dot);
  return {
    root,
    markers: [dot],
    label: makeLabel(),
    resetAnchorState: () => {},
  };
};

const makeRig = (avatarId) => {
  const scene = new T.Scene();
  const decor = {group: new T.Group()};
  decor.group.name = 'studyDecor';
  scene.add(decor.group);
  const characterRoot = new T.Group();
  scene.add(characterRoot);
  const model = new T.Mesh(new T.BoxGeometry(0.2, 1.6, 0.2), new T.MeshBasicMaterial());
  model.name = `model-${avatarId}`;
  characterRoot.add(model);
  const teaching = makeTeaching(scene);
  return {scene, decor, characterRoot, model, teaching, avatarId};
};

const switchAvatar = (from, to) => {
  stashTeachingOverlay(from.teaching);
  detachCharacterModels(from.characterRoot);
  restoreTeachingOverlay(to.teaching, to.scene, host);
  reparentCharacterModel(to.model, to.characterRoot);
  return to;
};

const assertRigVisible = (rig, label) => {
  assert.equal(rig.model.parent, rig.characterRoot, `${label}: model on characterRoot`);
  assert.equal(rig.teaching.root.parent, rig.scene, `${label}: teaching.root on scene`);
  assert.ok(rig.teaching.root.visible, `${label}: teaching root visible`);
  assert.ok(
    rig.scene.children.some((o) => o === rig.decor.group),
    `${label}: study decor on scene`,
  );
  assert.ok(rig.teaching.markers.some((m) => m.visible !== false), `${label}: acupoint markers present`);
  assert.equal(host.children.filter((c) => c.className?.includes('contact-label')).length, 1, `${label}: one contact label`);
};

const assistant = makeRig('assistant');
const adult = makeRig('adult');
restoreTeachingOverlay(assistant.teaching, assistant.scene, host);

assertRigVisible(assistant, 'assistant initial');
let active = switchAvatar(assistant, adult);
assertRigVisible(active, 'after switch to adult');
active = switchAvatar(adult, assistant);
assertRigVisible(active, 'after switch back to assistant');
active = switchAvatar(assistant, adult);
assertRigVisible(active, 'after second switch to adult');
active = switchAvatar(adult, assistant);
assertRigVisible(active, 'after second switch back');

console.log('avatar scene restore checks passed.');