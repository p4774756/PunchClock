import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('device-head-badge.js');

test('離線仍顯示離線', () => {
  assert.equal(M.buildDeviceHeadBadgeText(false, 'HTTP', [{ status: 'SCHEDULED' }]), '離線');
  assert.equal(M.buildDeviceHeadChipClass(false, []), 'chip-offline');
});

test('連線時依打卡狀態顯示單一狀態詞', () => {
  const http = 'HTTP';
  const scheduled = [{ name: '每日排程', status: 'SCHEDULED' }];
  assert.equal(
    M.buildDeviceHeadBadgeText(true, http, scheduled),
    '辦公室上班中 · HTTP · 打卡已排程'
  );
  assert.equal(M.buildDeviceHeadChipClass(true, scheduled), 'chip-online');

  const checking = [{ status: 'CHECKING_IN' }];
  assert.equal(
    M.buildDeviceHeadBadgeText(true, http, checking),
    '辦公室上班中 · HTTP · 打卡中'
  );
  assert.equal(M.buildDeviceHeadChipClass(true, checking), 'chip-online');

  const both = [
    { status: 'SCHEDULED' },
    { status: 'CHECKING_IN' }
  ];
  assert.equal(
    M.buildDeviceHeadBadgeText(true, http, both),
    '辦公室上班中 · HTTP · 打卡中'
  );
});

test('未啟動與其他狀態顯示灰色徽章類別', () => {
  const http = 'HTTP';
  assert.equal(
    M.buildDeviceHeadBadgeText(true, http, []),
    '辦公室上班中 · HTTP · 打卡未啟動'
  );
  assert.equal(M.buildDeviceHeadChipClass(true, []), 'chip-idle');
  assert.equal(
    M.buildDeviceHeadBadgeText(true, http, [{ status: 'SUCCESS' }]),
    '辦公室上班中 · HTTP · 打卡未啟動'
  );
  assert.equal(
    M.buildDeviceHeadBadgeText(true, http, [{ status: 'PENDING' }]),
    '辦公室上班中 · HTTP · 打卡未啟動'
  );
});