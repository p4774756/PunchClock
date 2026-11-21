import test from 'node:test';
import assert from 'node:assert/strict';
import { drawSecretaryAvatar } from '../../main/resources/public/js/private-team-avatar.js';

test('私人秘書頭像繪製不拋錯', () => {
  const canvas = { width: 64, height: 64, getContext: () => ({
    clearRect() {},
    fillRect() {},
    beginPath() {},
    ellipse() {},
    arc() {},
    fill() {},
    strokeRect() {},
    get fillStyle() { return ''; },
    set fillStyle(_v) {}
  }) };
  assert.doesNotThrow(() => drawSecretaryAvatar(canvas.getContext(), 64));
});