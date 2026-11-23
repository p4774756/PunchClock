import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('device-head-badge.js');

test('離線：灰色連線標籤、不顯示打卡文案', () => {
  assert.equal(M.buildDeviceHeadConnectionText(false), '離線');
  assert.equal(M.buildDeviceHeadConnectionChipClass(false), 'chip-idle');
  assert.equal(M.buildDeviceHeadCheckInText(false, [{ status: 'SCHEDULED' }]), null);
  assert.equal(M.buildDeviceHeadCheckInChipClass(false, []), null);
  assert.equal(M.buildDeviceHeadBadgeText(false, 'HTTP', [{ status: 'SCHEDULED' }]), '離線');
  assert.equal(M.buildDeviceHeadChipClass(false, []), 'chip-idle');
});

test('連線：綠色辦公室上班中 + 打卡狀態標籤', () => {
  const scheduled = [{ name: '每日排程', status: 'SCHEDULED' }];
  assert.equal(M.buildDeviceHeadConnectionText(true), '辦公室上班中');
  assert.equal(M.buildDeviceHeadConnectionChipClass(true), 'chip-online');
  assert.equal(M.buildDeviceHeadCheckInText(true, scheduled), '打卡已排程');
  assert.equal(M.buildDeviceHeadCheckInChipClass(true, scheduled), 'chip-online');

  const checking = [{ status: 'CHECKING_IN' }];
  assert.equal(M.buildDeviceHeadCheckInText(true, checking), '打卡中');
  assert.equal(M.buildDeviceHeadCheckInChipClass(true, checking), 'chip-online');

  const both = [{ status: 'SCHEDULED' }, { status: 'CHECKING_IN' }];
  assert.equal(M.buildDeviceHeadCheckInText(true, both), '打卡中');
  assert.equal(M.buildDeviceHeadCheckInChipClass(true, both), 'chip-online');
});

test('未排打卡：連線綠色、打卡標籤灰色', () => {
  assert.equal(M.buildDeviceHeadCheckInText(true, []), '未排打卡');
  assert.equal(M.buildDeviceHeadCheckInChipClass(true, []), 'chip-idle');
  assert.equal(
    M.buildDeviceHeadCheckInText(true, [{ status: 'SUCCESS' }]),
    '未排打卡'
  );
  assert.equal(
    M.buildDeviceHeadCheckInText(true, [{ status: 'PENDING' }]),
    '未排打卡'
  );
});