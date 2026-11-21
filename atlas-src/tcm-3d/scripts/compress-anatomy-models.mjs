#!/usr/bin/env node
/**
 * Gzip anatomy GLBs, merge skeleton chunks, write data/anatomy-manifest.json + app/anatomy-model-digest.ts.
 * Input: public/models/anatomy/raw/*.glb (from export-z-anatomy-blender.py)
 * Output: public/models/anatomy/*.glb.gz
 */
import {createHash} from 'node:crypto';
import {gzipSync, gunzipSync} from 'node:zlib';
import {existsSync, readFileSync, readdirSync, writeFileSync, mkdirSync, unlinkSync} from 'node:fs';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const rawDir = join(root, 'public/models/anatomy/raw');
const outDir = join(root, 'public/models/anatomy');
const manifestPath = join(root, 'data/anatomy-manifest.json');
const digestPath = join(root, 'app/anatomy-model-digest.ts');

const FIRST_BOOT_MAX = 3 * 1024 * 1024;
const TOTAL_MAX = 12 * 1024 * 1024;

if (!existsSync(rawDir)) {
 console.log('skip compress-anatomy: no raw GLBs (run Blender export first)');
 process.exit(0);
}

const rawFiles = readdirSync(rawDir).filter(f => f.endsWith('.glb'));
if (!rawFiles.length) {
 console.error('no .glb in', rawDir);
 process.exit(1);
}

mkdirSync(outDir, {recursive: true});

const parts = [];
let totalGzip = 0;

for (const file of rawFiles.sort()) {
 const raw = readFileSync(join(rawDir, file));
 const gz = gzipSync(raw, {level: 9});
 const outName = file.replace(/\.glb$/, '.glb.gz');
 const outPath = join(outDir, outName);
 writeFileSync(outPath, gz);
 totalGzip += gz.length;
  const base = file.replace('.glb', '');
  const layer = base.endsWith('muscle-deep')
    ? 'muscle-deep'
    : base.endsWith('muscle-superficial')
      ? 'muscle-superficial'
      : base.endsWith('skeleton')
        ? 'skeleton'
        : 'unknown';
  const region = base.replace(/-(muscle-deep|muscle-superficial|skeleton)$/, '');
 parts.push({
  id: file.replace('.glb', ''),
  url: `models/anatomy/${outName}`,
  region,
  layer,
  bytes: gz.length,
  rawBytes: raw.length,
  digest: createHash('sha256').update(raw).digest('hex').slice(0, 12),
 });
 console.log(`${file} → ${outName} gzip ${gz.length} B`);
}

const skeletonParts = parts.filter(p => p.layer === 'skeleton');
const initialParts = parts.filter(
 p => p.layer === 'skeleton' || (p.layer === 'muscle-superficial' && p.region === 'trunk'),
);
const initialBytes = initialParts.reduce((n, p) => n + p.bytes, 0);

const manifest = {
 source: 'Z-Anatomy (CC BY-SA 4.0) via Blender export; BodyParts3D upstream CC BY 4.0',
 initialLoad: initialParts.map(p => p.id),
 lazyRegions: ['head-neck', 'trunk', 'arm', 'leg'],
 parts,
 totalGzipBytes: totalGzip,
 initialGzipBytes: initialBytes,
 limits: {firstBootMax: FIRST_BOOT_MAX, totalMax: TOTAL_MAX},
};

writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));

const digestSrc = `/** Anatomy GLB digests (regenerate with scripts/compress-anatomy-models.mjs). */\n` +
 `export const ANATOMY_TOTAL_GZIP_BYTES=${totalGzip};\n` +
 `export const ANATOMY_INITIAL_GZIP_BYTES=${initialBytes};\n` +
 `export const ANATOMY_PART_COUNT=${parts.length};\n`;

writeFileSync(digestPath, digestSrc);

if (totalGzip > TOTAL_MAX) {
 console.error(`total gzip ${totalGzip} exceeds ${TOTAL_MAX}; document raised limit in PR`);
 process.exit(1);
}
if (initialBytes > FIRST_BOOT_MAX) {
 console.warn(`initial gzip ${initialBytes} exceeds ${FIRST_BOOT_MAX}; may need merge or stronger decimation`);
}

console.log(`manifest ${manifestPath} total=${totalGzip} initial=${initialBytes}`);