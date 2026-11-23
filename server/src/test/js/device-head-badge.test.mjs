import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('device-head-badge.js');

test('離線仍顯示離線', () => {
  assert.equal(M.buildDeviceHeadBadgeText(false, 'HTTP', [{ status: 'SCHEDULED' }]), '離線');
  assert.equal(M.buildDeviceHeadChipClass(false, []), 'chip-offline');
});

test('連線時依打卡狀態顯示單一狀態詞', () => {
  const scheduled = [{ name: '每日排程', status: 'SCHEDULED' }];
  assert.equal(
    M.buildDeviceHeadBadgeText(true, 'HTTP', scheduled),
    '辦公室上班中 · 打卡已排程'
  );
  assert.equal(M.buildDeviceHeadChipClass(true, scheduled), 'chip-online');

  const checking = [{ status: 'CHECKING_IN' }];
  assert.equal(
    M.buildDeviceHeadBadgeText(true, 'HTTP', checking),
    '辦公室上班中 · 打卡中'
  );
  assert.equal(M.buildDeviceHeadChipClass(true, checking), 'chip-online');

  const both = [
    { status: 'SCHEDULED' },
    { status: 'CHECKING_IN' }
  ];
  assert.equal(
    M.buildDeviceHeadBadgeText(true, 'HTTP', both),
    '辦公室上班中 · 打卡中'
  );
});

test('未排打卡在線一律綠色徽章', () => {
  assert.equal(
    M.buildDeviceHeadBadgeText(true, 'HTTP', []),
    '辦公室上班中 · 未排打卡'
  );
  assert.equal(M.buildDeviceHeadChipClass(true, []), 'chip-online');
  assert.equal(
    M.buildDeviceHeadBadgeText(true, 'WebSocket', [{ status: 'SUCCESS' }]),
    '辦公室上班中 · 未排打卡'
  );
  assert.equal(
    M.buildDeviceHeadBadgeText(true, null, [{ status: 'PENDING' }]),
    '辦公室上班中 · 未排打卡'
  );
});