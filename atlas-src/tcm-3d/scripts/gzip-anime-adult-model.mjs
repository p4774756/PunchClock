#!/usr/bin/env node
/** Pre-compress anime-adult.glb for AtlasRoutes Content-Encoding: gzip. */
import {createHash} from 'node:crypto';
import {gzipSync, gunzipSync} from 'node:zlib';
import {spawnSync} from 'node:child_process';
import {existsSync, readFileSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const glbPath = join(root, 'public/models/anime-adult.glb');
const gzPath = join(root, 'public/models/anime-adult.glb.gz');
const digestPath = join(root, 'app/anime-adult-model-digest.ts');

const raw = readFileSync(glbPath);
const digest = createHash('sha256').update(raw).digest('hex').slice(0, 12);
const digestSrc = (gzipBytes) =>
  `/** Content digest for anime-adult.glb (cache bust). */\nexport const ANIME_ADULT_MODEL_DIGEST='${digest}';\nexport const ANIME_ADULT_MODEL_BYTES=${raw.length};\nexport const ANIME_ADULT_MODEL_GZIP_BYTES=${gzipBytes};\n`;

const digestMatches = (gzipBytes) => {
  if (!existsSync(digestPath)) return false;
  const src = readFileSync(digestPath, 'utf8');
  return (
    src.includes(`ANIME_ADULT_MODEL_DIGEST='${digest}'`) &&
    src.includes(`ANIME_ADULT_MODEL_BYTES=${raw.length}`) &&
    src.includes(`ANIME_ADULT_MODEL_GZIP_BYTES=${gzipBytes}`)
  );
};

if (existsSync(gzPath)) {
  const existing = readFileSync(gzPath);
  try {
    const inflated = gunzipSync(existing);
    if (inflated.equals(raw)) {
      if (digestMatches(existing.length)) {
        console.log(`anime-adult.glb.gz verified ${existing.length} B (digest ${digest}, skip rewrite)`);
        process.exit(0);
      }
      writeFileSync(digestPath, digestSrc(existing.length));
      console.log(`anime-adult.glb.gz verified ${existing.length} B (digest ${digest}, digest file refreshed)`);
      process.exit(0);
    }
  } catch {
    /* fall through */
  }
}

const fromGzip = spawnSync('gzip', ['-9', '-n'], {input: raw, maxBuffer: raw.length + 2_000_000});
const gz =
  fromGzip.status === 0 && fromGzip.stdout?.length
    ? Buffer.from(fromGzip.stdout)
    : gzipSync(raw, {level: 9, memLevel: 9});
writeFileSync(gzPath, gz);
writeFileSync(digestPath, digestSrc(gz.length));
console.log(`anime-adult.glb ${raw.length} B → gzip ${gz.length} B (digest ${digest})`);