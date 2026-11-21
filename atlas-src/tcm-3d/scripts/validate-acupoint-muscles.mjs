#!/usr/bin/env node
import {readFileSync} from 'node:fs';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const table = JSON.parse(readFileSync(join(root, 'data/acupoint-muscles.json'), 'utf8'));
const anime = JSON.parse(readFileSync(join(root, 'app/anime-points.json'), 'utf8'));
const required = new Set(anime.points.map(p => p.code));
required.add('ST36');

let missing = 0;
for (const code of required) {
 if (!table[code]) {
  missing += 1;
  console.warn('missing acupoint muscle note:', code);
 }
}
if (!table.ST36?.nearby?.includes('脛骨前肌')) {
 throw new Error('ST36 must reference tibialis anterior region');
}
if (missing > 10) throw new Error(`too many missing acupoint-muscles entries: ${missing}`);
console.log(`acupoint-muscles OK (${Object.keys(table).length} entries, ${missing} gaps in baked set)`);