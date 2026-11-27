import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '../../../..');
const assetDir = join(repoRoot, 'server/src/main/resources/public/assets/private-team');

const CLAP_FILES = ['secretary-clap-a.webp', 'secretary-clap-b.webp'];
const SIZE_MIN = 35_000;
const SIZE_MAX = 62_000;

test('拍手慶祝立繪存在且 WebP 體積符合同系列水準', () => {
  for (const name of CLAP_FILES) {
    const path = join(assetDir, name);
    assert.ok(existsSync(path), name);
    const st = statSync(path);
    assert.ok(st.size >= SIZE_MIN && st.size <= SIZE_MAX, `${name} size ${st.size}`);
    const head = readFileSync(path, { encoding: 'latin1', flag: 'r' }).slice(0, 12);
    assert.match(head, /^RIFF....WEBP/);
  }
});

test('拍手立繪 alpha 檢查（含 624×768 與白罩衫無破洞，髮隙透明除外）', () => {
  const py = process.env.PYTHON_BINARY?.trim() || 'python3';
  const script = join(repoRoot, 'scripts/fix_secretary_status_sprites.py');
  const r = spawnSync(py, [script, '--check'], {
    cwd: repoRoot,
    encoding: 'utf8',
    timeout: 120_000
  });
  const out = (r.stdout || '') + (r.stderr || '');
  assert.equal(r.status, 0, out);
  assert.match(out, /ok/);
});