import fs from 'node:fs';
import {gunzipSync} from 'node:zlib';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const project = join(dirname(fileURLToPath(import.meta.url)), '..');

/** Prefer .bin.gz in the repo; fall back to uncompressed .bin for local tooling. */
export function loadChunkBuffer(chunk, root = project) {
  const gzipRel = chunk.gzip?.replace(/^\//, '');
  const urlRel = chunk.url.replace(/^\//, '');
  if (gzipRel) {
    const gzipPath = join(root, 'public', gzipRel);
    if (fs.existsSync(gzipPath)) {
      return gunzipSync(fs.readFileSync(gzipPath));
    }
  }
  const binPath = join(root, 'public', urlRel);
  if (!fs.existsSync(binPath)) {
    throw new Error(`缺少模型區塊：${gzipRel || urlRel}（可先跑 npm run inflate-models）`);
  }
  return fs.readFileSync(binPath);
}

export {project};
