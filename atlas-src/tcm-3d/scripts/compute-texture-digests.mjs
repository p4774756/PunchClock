#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFileSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const woodcut = join(root, 'public/textures/woodcut-landscape.webp');
const digest = createHash('sha256').update(readFileSync(woodcut)).digest('hex').slice(0, 12);
const out = join(root, 'app/texture-digests.ts');
writeFileSync(
  out,
  `/** Content digest for static textures (cache bust). Regenerate when bytes change. */\nexport const WOODCUT_LANDSCAPE_DIGEST='${digest}';\n`,
);
console.log('texture-digests.ts updated', digest);