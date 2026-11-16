import assert from 'node:assert/strict';
import {readFile, writeFile, copyFile, mkdir, rm} from 'node:fs/promises';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

/**
 * Ensures bake-anime-points.mjs merges into anime-points.json and never shrinks
 * the existing head-neck-21 set when re-run without wiping.
 */
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const pointsPath = join(root, 'app/anime-points.json');
const backupDir = join(root, '.validation');
await mkdir(backupDir, {recursive: true});
const backup = join(backupDir, 'anime-points.backup.json');
await copyFile(pointsPath, backup);

try {
  const before = JSON.parse(await readFile(pointsPath, 'utf8'));
  assert.ok(before.points.length >= 21, 'anime-points.json should keep at least head-neck-21');
  const beforeCodes = before.points.map(p => p.code);

  const dry = spawnSync(process.execPath, [join(root, 'scripts/bake-anime-points.mjs'), '--dry-run', '--codes', 'GV20'], {
    cwd: root, encoding: 'utf8',
  });
  assert.equal(dry.status, 0, dry.stderr || dry.stdout);
  assert.match(dry.stdout, /dry-run|merge|GV20/i);

  const afterDry = JSON.parse(await readFile(pointsPath, 'utf8'));
  assert.equal(afterDry.points.length, before.points.length, 'dry-run must not shrink anime-points.json');

  const merge = spawnSync(process.execPath, [join(root, 'scripts/bake-anime-points.mjs'), '--codes', 'GV20,ST9'], {
    cwd: root, encoding: 'utf8',
  });
  assert.equal(merge.status, 0, merge.stderr || merge.stdout);
  const after = JSON.parse(await readFile(pointsPath, 'utf8'));
  assert.ok(after.points.length >= before.points.length, 'merge must not drop existing points');
  const codes = new Set(after.points.map(p => p.code));
  for (const code of beforeCodes) assert.ok(codes.has(code), `lost ${code}`);

  const fake = spawnSync(process.execPath, [join(root, 'scripts/bake-anime-points.mjs'), '--codes', 'FAKE99', '--dry-run'], {
    cwd: root, encoding: 'utf8',
  });
  assert.notEqual(fake.status, 0, 'unknown code without seed should fail');
} finally {
  await copyFile(backup, pointsPath);
  await rm(backup, {force: true});
}

console.log('bake-anime-points merge checks passed.');
