import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const H = await loadPublicModule('console-nav-history.js');

test('任務分頁 hash 與施工進度網址', () => {
  assert.equal(H.consoleHashForTab('devices'), '#devices');
  assert.equal(H.buildConsoleTasksUrl('/console', 'devices'), '/console#devices');
  assert.equal(H.buildConsoleRoadmapUrl('/console'), '/console?open=roadmap');
});

test('剝除 open=roadmap query', () => {
  assert.equal(H.stripRoadmapOpenSearch('?open=roadmap'), '');
  assert.equal(H.stripRoadmapOpenSearch('?open=roadmap&x=1'), '?x=1');
  assert.equal(H.stripRoadmapOpenSearch(''), '');
});

test('popstate 判斷施工進度是否開著', () => {
  assert.equal(H.roadmapOpenFromPopstateHref('http://x/console?open=roadmap'), true);
  assert.equal(H.roadmapOpenFromPopstateHref('http://x/console#devices'), false);
});