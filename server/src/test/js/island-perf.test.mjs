// ?perf=1 效能面板：node --test server/src/test/js/island-perf.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const publicDir = join(here, '..', '..', 'main', 'resources', 'public');
const islandCss = readFileSync(join(publicDir, 'css', 'island.css'), 'utf8');
const islandJs = readFileSync(join(publicDir, 'js', 'island.js'), 'utf8');

test('island.css：.island-perf 固定於 body 層、不攔截點擊、z-index 低於 HUD', () => {
  const block = islandCss.match(/\.island-perf\s*\{[^}]+\}/s);
  assert.ok(block, '應有 .island-perf 規則');
  const css = block[0];
  assert.match(css, /position:\s*fixed/);
  assert.match(css, /pointer-events:\s*none/);
  assert.match(css, /z-index:\s*5/);
  assert.match(css, /user-select:\s*none/);
  assert.match(css, /font-variant-numeric:\s*tabular-nums/);
});

test('island.js：perf 面板掛在 body、使用 island-perf class', () => {
  assert.match(islandJs, /perfEl\.className\s*=\s*['"]island-perf['"]/);
  assert.match(islandJs, /document\.body\.appendChild\(perfEl\)/);
  assert.doesNotMatch(islandJs, /stage\.appendChild\(perfEl\)/);
});