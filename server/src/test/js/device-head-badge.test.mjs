import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('device-head-badge.js');

test('離線：已離線、無任務標籤、層級 offline', () => {
  assert.equal(M.buildDeviceHeadConnectionText(false), '已離線');
  assert.equal(M.buildDeviceHeadConnectionChipClass(false), 'chip-idle');
  assert.equal(M.buildDeviceHeadTaskLabelText(false, [{ status: 'SCHEDULED' }]), null);
  assert.equal(M.buildDeviceHeadTaskChipClass(false, [{ status: 'CHECKING_IN' }]), null);
  assert.equal(M.buildDeviceHeadCheckInText(false, [{ status: 'SCHEDULED' }]), null);
  assert.equal(M.buildDeviceHeadCheckInChipClass(false, []), null);
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(false, [{ status: 'SCHEDULED' }]), null);
  assert.equal(M.buildDeviceHeadBadgeText(false, 'HTTP', [{ status: 'SCHEDULED' }]), '已離線');
  assert.equal(M.buildDeviceHeadChipClass(false, []), 'chip-idle');
  assert.equal(M.resolveDeviceCardTier(false, [{ status: 'CHECKING_IN' }]), 'offline');
  assert.equal(M.buildDeviceCardTierClass(false, []), 'device-tier-offline');
});

test('連線任務啟動中：任務執行中標籤與 active 層級', () => {
  const scheduled = [{ name: '每日排程', status: 'SCHEDULED' }];
  assert.equal(M.buildDeviceHeadConnectionText(true), '辦公室上班中');
  assert.equal(M.buildDeviceHeadConnectionChipClass(true), 'chip-online');
  assert.equal(M.buildDeviceHeadTaskLabelText(true, scheduled), '任務執行中');
  assert.equal(M.buildDeviceHeadTaskChipClass(true, scheduled), 'chip-task-active');
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, scheduled), '任務執行中');
  assert.equal(M.buildDeviceHeadScheduleDotChipClass(true, scheduled), 'chip-task-active');
  assert.equal(M.resolveDeviceCardTier(true, scheduled), 'active');
  assert.equal(M.buildDeviceCardTierClass(true, scheduled), 'device-tier-active');

  const checking = [{ status: 'CHECKING_IN' }];
  assert.equal(M.buildDeviceHeadTaskLabelText(true, checking), '任務執行中');
  assert.equal(M.buildDeviceHeadTaskChipClass(true, checking), 'chip-task-active');

  const both = [{ status: 'SCHEDULED' }, { status: 'CHECKING_IN' }];
  assert.equal(M.buildDeviceHeadTaskLabelText(true, both), '任務執行中');
  assert.doesNotMatch(String(M.buildDeviceHeadTaskLabelText(true, both)), /打卡/);
});

test('在線未啟動：未啟動標籤與 idle 層級', () => {
  assert.equal(M.buildDeviceHeadTaskLabelText(true, []), '未啟動');
  assert.equal(M.buildDeviceHeadTaskChipClass(true, []), 'chip-task-idle');
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, []), '未啟動');
  assert.equal(M.buildDeviceHeadScheduleDotChipClass(true, []), 'chip-task-idle');
  assert.equal(M.buildDeviceHeadCheckInText(true, []), null);
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, [{ status: 'SUCCESS' }]), '未啟動');
  assert.equal(M.buildDeviceHeadScheduleAriaLabel(true, [{ status: 'PENDING' }]), '未啟動');
  assert.equal(M.resolveDeviceCardTier(true, []), 'idle');
  assert.equal(M.buildDeviceCardTierClass(true, [{ status: 'SUCCESS' }]), 'device-tier-idle');
});