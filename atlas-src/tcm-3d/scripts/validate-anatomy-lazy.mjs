#!/usr/bin/env node
/** Ensure anatomy GLB chunks are listed only in muscle-mode code paths (lazy bundle). */
import {readFileSync} from 'node:fs';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const page = readFileSync(join(root, 'app/page.tsx'), 'utf8');
const scene = readFileSync(join(root, 'app/character-scene.tsx'), 'utf8');
const manifest = readFileSync(join(root, 'data/anatomy-manifest.json'), 'utf8');

if (scene.includes('models/anatomy')) {
 throw new Error('character-scene must not reference anatomy GLBs');
}
if (page.includes('models/anatomy') || page.includes('anatomy-manifest')) {
 throw new Error('page.tsx should lazy-load muscle-studio only, not anatomy URLs');
}
if (!page.includes("lazy(()=>import('./muscle-studio-shell'))")) {
 throw new Error('muscle-studio-shell must stay lazy-loaded from page.tsx');
}
if (!page.includes("studioView==='muscle'")) {
 throw new Error('muscle mode gate missing in page.tsx');
}
const parsed = JSON.parse(manifest);
if (!Array.isArray(parsed.parts)) throw new Error('anatomy-manifest.json needs parts[]');
for (const p of parsed.parts) {
 if (!p.url?.includes('models/anatomy/')) throw new Error('bad anatomy part url');
}
console.log(`Anatomy lazy-load checks passed (${parsed.parts.length} GLB parts in manifest).`);