import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();
const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const {
  PRIVATE_TEAM_WINDOW_TEXTURE_URLS,
  PRIVATE_TEAM_WINDOW_SKY_Z,
  privateTeamWindowPeriodFromTaipeiDate,
  privateTeamWindowCyclePreviewMode,
  privateTeamWindowPreviewToastLabel,
  privateTeamWindowAmbientMotionEnabled,
  privateTeamWindowTowerBeaconOpacity,
  privateTeamSceneLighting
} = await loadPublicModule('private-team-scene.js');

test('窗景改用台北 101 三時段 WebP，懶載當前時段並依切換換 map', () => {
  const src = sceneSrc;
  assert.match(src, /PRIVATE_TEAM_WINDOW_TEXTURE_URLS/);
  assert.match(src, /taipei-window-day\.webp/);
  assert.match(src, /taipei-window-sunset\.webp/);
  assert.match(src, /taipei-window-night\.webp/);
  assert.match(src, /ensureWindowVistaTexture/);
  assert.match(src, /ensureWindowVistaTexture\(texLoader, vistaTex, period\)/);
  assert.doesNotMatch(src, /name = 'skylineFar'/);
  assert.doesNotMatch(src, /windowRainBack/);
  assert.doesNotMatch(src, /windowAirplane/);
  assert.ok(PRIVATE_TEAM_WINDOW_SKY_Z > 0);
});

test('三張窗景靜態資源存在於 public', () => {
  for (const url of Object.values(PRIVATE_TEAM_WINDOW_TEXTURE_URLS)) {
    const rel = url.replace(/^\//, '');
    assert.ok(existsSync(join(publicRoot, rel)), url);
  }
});

test('Asia/Taipei 時段判定', () => {
  assert.equal(
    privateTeamWindowPeriodFromTaipeiDate(new Date('2026-03-15T10:00:00+08:00')),
    'day'
  );
  assert.equal(
    privateTeamWindowPeriodFromTaipeiDate(new Date('2026-03-15T17:15:00+08:00')),
    'sunset'
  );
  assert.equal(
    privateTeamWindowPeriodFromTaipeiDate(new Date('2026-03-15T21:00:00+08:00')),
    'night'
  );
  assert.equal(
    privateTeamWindowPeriodFromTaipeiDate(new Date('2026-03-15T05:30:00+08:00')),
    'night'
  );
});

test('預覽輪播順序與 toast 文案', () => {
  assert.equal(privateTeamWindowCyclePreviewMode('auto'), 'day');
  assert.equal(privateTeamWindowCyclePreviewMode('day'), 'sunset');
  assert.equal(privateTeamWindowCyclePreviewMode('sunset'), 'night');
  assert.equal(privateTeamWindowCyclePreviewMode('night'), 'auto');
  assert.equal(privateTeamWindowPreviewToastLabel('auto'), '窗景：自動');
  assert.match(sceneSrc, /pickRole = 'windowVista'/);
  assert.match(sceneSrc, /privateTeamWindowCyclePreviewMode/);
  assert.match(sceneSrc, /pt-window-vista-toast/);
});

test('燈光依時段變化，日夜切換與三扇窗框保留', () => {
  const day = privateTeamSceneLighting('day');
  const night = privateTeamSceneLighting('night');
  assert.ok(day.amb > night.amb);
  assert.ok(day.point < night.point);
  assert.ok(day.floorWash < night.floorWash);
  assert.match(sceneSrc, /applyWindowScenePeriod/);
  assert.match(sceneSrc, /windowGlass/);
  assert.match(sceneSrc, /frameBar/);
  assert.doesNotMatch(sceneSrc, /const lighting = privateTeamSceneLighting\(\)/);
});

test('窗景細微動態與 reducedMotion', () => {
  const src = sceneSrc;
  assert.match(src, /windowVistaBeacon/);
  assert.match(src, /windowStreetTraffic/);
  assert.match(src, /windowVistaRiver/);
  assert.match(src, /privateTeamWindowTowerBeaconOpacity/);
  assert.match(src, /privateTeamWindowAmbientMotionEnabled\(reducedMotion\)/);
  assert.equal(privateTeamWindowAmbientMotionEnabled(true), false);
  assert.equal(privateTeamWindowAmbientMotionEnabled(false), true);
  assert.ok(privateTeamWindowTowerBeaconOpacity(0, true) >= 0.35);
});

test('窗台物件與窗外洒光貼圖保留', () => {
  const src = sceneSrc;
  assert.match(src, /windowSill/);
  assert.match(src, /windowMoonlight/);
  assert.match(src, /makeWindowMoonlightFloorTexture/);
});