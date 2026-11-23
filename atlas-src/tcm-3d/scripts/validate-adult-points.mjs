#!/usr/bin/env node
import assert from 'node:assert/strict';
import {existsSync,readFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const root=join(dirname(fileURLToPath(import.meta.url)),'..');
const adultPath=join(root,'app/adult-points.json');
const bakePath=join(root,'scripts/bake-anime-points.mjs');
const glbPath=join(root,'public/models/anime-adult.glb');

assert(existsSync(adultPath),'adult-points.json must exist');
assert(existsSync(glbPath),'anime-adult.glb must be in repo for bake validation');

const adult=JSON.parse(readFileSync(adultPath,'utf8'));
const anime=JSON.parse(readFileSync(join(root,'app/anime-points.json'),'utf8'));

assert.equal(adult.model,'anime-adult.glb');
assert.equal(adult.faceAxis,'-Z');
assert.ok(adult.points.length>=83,`adult expects full baked set, got ${adult.points.length}`);
assert.equal(adult.points.length,anime.points.length,'adult and assistant should bake the same catalog codes');

const headNeck=adult.points.filter(p=>p.set==='head-neck-21');
const trunk=adult.points.filter(p=>p.set==='trunk-30');
assert.ok(headNeck.length>=21,`head-neck-21 on adult: ${headNeck.length}`);
assert.ok(trunk.length>=20,`trunk-30 on adult: ${trunk.length}`);

const charMode=readFileSync(join(root,'app/character-mode.ts'),'utf8');
const avatarsSrc=readFileSync(join(root,'app/avatars.ts'),'utf8');
assert.match(charMode,/ADULT_POINTS/, 'character-mode exports ADULT_POINTS');
assert.match(avatarsSrc,/points:ADULT_POINTS/, 'adult avatar wires ADULT_POINTS');

if(!process.env.CI&&!process.env.SKIP_ADULT_BAKE_DRY){
 const dry=spawnSync(process.execPath,[bakePath,'--dry-run','--model','public/models/anime-adult.glb','--output','app/adult-points.json','--codes','GV20'],{
  cwd:root,encoding:'utf8',timeout:120_000,
 });
 if(dry.status===0){
  const after=JSON.parse(readFileSync(adultPath,'utf8'));
  assert.equal(after.points.length,adult.points.length,'dry-run adult bake must not shrink JSON');
 }else if(dry.error?.code==='ENOENT'||/Cannot find module/.test(dry.stderr??'')){
  console.log('adult bake dry-run skipped (three unavailable)');
 }else{
  assert.equal(dry.status,0,dry.stderr||dry.stdout);
 }
}

console.log(`adult-points checks passed (${adult.points.length} codes).`);
