#!/usr/bin/env node
/** 從 .bin.gz 還原未壓縮 .bin，供 optimize／compress 等離線腳本使用。預設不進 git。 */
import fs from 'node:fs';
import {gunzipSync} from 'node:zlib';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const base = join(root, 'public', 'models');
const atlas = JSON.parse(fs.readFileSync(join(base, 'atlas.json'), 'utf8'));
let bytes = 0;
for (const chunk of atlas.chunks) {
  const gzipRel = chunk.gzip?.replace(/^\//, '');
  const urlRel = chunk.url.replace(/^\//, '');
  if (!gzipRel) continue;
  const gzipPath = join(root, 'public', gzipRel);
  const binPath = join(root, 'public', urlRel);
  const raw = gunzipSync(fs.readFileSync(gzipPath));
  if (raw.length !== chunk.bytes) {
    throw new Error(`${gzipRel} 解壓後 ${raw.length} bytes，預期 ${chunk.bytes}`);
  }
  fs.writeFileSync(binPath, raw);
  bytes += raw.length;
  console.log('wrote', urlRel, raw.length);
}
console.log('inflated', atlas.chunks.length, 'chunks;', bytes, 'bytes');
