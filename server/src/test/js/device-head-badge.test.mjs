import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('device-head-badge.js');

test('離線：灰色連線標籤、不顯示排程點', () => {
  assert.equal(M.buildDeviceHeadConnectionText(false), '離線');
  assert.equal(M.buildDeviceHeadConnectionChipClass(false), 'chip-idle');
  assert.equal(M.buildDeviceHeadCheckInText(false, [{ status: 'SCHEDULED' }]), null);
  assert.equal(M.buildDeviceHeadCheckInChipClass(false, []), null);
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(false, [{ status: 'SCHEDULED' }]), null);
  assert.equal(M.buildDeviceHeadBadgeText(false, 'HTTP', [{ status: 'SCHEDULED' }]), '離線');
  assert.equal(M.buildDeviceHeadChipClass(false, []), 'chip-idle');
});

test('連線：辦公室上班中與排程綠點 aria', () => {
  const scheduled = [{ name: '每日排程', status: 'SCHEDULED' }];
  assert.equal(M.buildDeviceHeadConnectionText(true), '辦公室上班中');
  assert.equal(M.buildDeviceHeadConnectionChipClass(true), 'chip-online');
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, scheduled), '已排程');
  assert.equal(M.buildDeviceHeadScheduleDotChipClass(true, scheduled), 'chip-online');
  assert.equal(M.buildDeviceHeadCheckInText(true, scheduled), null);

  const checking = [{ status: 'CHECKING_IN' }];
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, checking), '執行中');
  assert.equal(M.buildDeviceHeadScheduleDotChipClass(true, checking), 'chip-online');

  const both = [{ status: 'SCHEDULED' }, { status: 'CHECKING_IN' }];
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, both), '執行中');
  assert.doesNotMatch(String(M.buildDeviceHeadScheduleAriaLabel(true, both)), /打卡/);
});

test('未排程：連線綠色、排程灰點', () => {
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, []), '未排程');
  assert.equal(M.buildDeviceHeadScheduleDotChipClass(true, []), 'chip-idle');
  assert.equal(M.buildDeviceHeadCheckInText(true, []), null);
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, [{ status: 'SUCCESS' }]), '未排程');
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, [{ status: 'PENDING' }]), '未排程');
});