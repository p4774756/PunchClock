#!/usr/bin/env node
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {gunzipSync} from 'node:zlib';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const glbPath = join(root, 'public/models/anime-assistant.glb');
const gzPath = join(root, 'public/models/anime-assistant.glb.gz');
const digestPath = join(root, 'app/anime-model-digest.ts');

const raw = readFileSync(glbPath);
const gz = readFileSync(gzPath);
const decoded = gunzipSync(gz);
assert.equal(decoded.length, raw.length, 'gunzip(.glb.gz) must match .glb byte length');
assert.equal(decoded.compare(raw), 0, 'gunzip(.glb.gz) must be byte-identical to .glb');

const digestSrc = readFileSync(digestPath, 'utf8');
const digestMatch = digestSrc.match(/ANIME_MODEL_DIGEST='([^']+)'/);
const bytesMatch = digestSrc.match(/ANIME_MODEL_BYTES=(\d+)/);
const gzipMatch = digestSrc.match(/ANIME_MODEL_GZIP_BYTES=(\d+)/);
assert(digestMatch && bytesMatch && gzipMatch, 'anime-model-digest.ts must export digest and byte constants');

const digest = createHash('sha256').update(raw).digest('hex').slice(0, 12);
assert.equal(digestMatch[1], digest, 'ANIME_MODEL_DIGEST must match sha256 of .glb');
assert.equal(Number(bytesMatch[1]), raw.length, 'ANIME_MODEL_BYTES must match .glb size');
assert.equal(Number(gzipMatch[1]), gz.length, 'ANIME_MODEL_GZIP_BYTES must match .glb.gz size');

const MAIN_GZIP_MAX = 2615803;
assert(gz.length <= MAIN_GZIP_MAX, `gzip ${gz.length} B must be <= main baseline ${MAIN_GZIP_MAX} B`);

console.log('anime model digest + gzip integrity passed.');