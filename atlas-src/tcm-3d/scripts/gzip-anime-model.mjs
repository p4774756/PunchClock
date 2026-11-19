#!/usr/bin/env node
/** Pre-compress anime-assistant.glb for AtlasRoutes Content-Encoding: gzip. */
import {createHash} from 'node:crypto';
import {gzipSync} from 'node:zlib';
import {readFileSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const glbPath = join(root, 'public/models/anime-assistant.glb');
const gzPath = join(root, 'public/models/anime-assistant.glb.gz');
const digestPath = join(root, 'app/anime-model-digest.ts');

const raw = readFileSync(glbPath);
const gz = gzipSync(raw, {level: 9});
writeFileSync(gzPath, gz);

const digest = createHash('sha256').update(raw).digest('hex').slice(0, 12);
const digestSrc = `/** Content digest for anime-assistant.glb (cache bust). */\nexport const ANIME_MODEL_DIGEST='${digest}';\nexport const ANIME_MODEL_BYTES=${raw.length};\nexport const ANIME_MODEL_GZIP_BYTES=${gz.length};\n`;
writeFileSync(digestPath, digestSrc);
console.log(`anime-assistant.glb ${raw.length} B → gzip ${gz.length} B (digest ${digest})`);