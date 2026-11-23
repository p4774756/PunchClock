#!/usr/bin/env node
/**
 * Seed AvatarSample_A VRM → public/models/anime-adult.glb (pure-black hair, compressed textures).
 * Default seed: ../../seed-571/avatar-sample-a.vrm (sibling of wt-571 under PunchClock-oki).
 */
import {copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import sharp from 'sharp';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const defaultSeed = resolve(root, '../../seed-571/avatar-sample-a.vrm');
const seedPath = process.env.SEED_VRM ? resolve(process.env.SEED_VRM) : defaultSeed;
const staged = join(root, 'public/models/.anime-adult-staged.glb');
const output = join(root, 'public/models/anime-adult.glb');

const GLB_MAGIC = 0x46546c67;
const GLB_JSON = 0x4e4f534a;
const GLB_BIN = 0x004e4942;

function readGlb(path) {
  const file = readFileSync(path);
  if (file.readUInt32LE(0) !== GLB_MAGIC) throw new Error('不是 GLB');
  const jsonLen = file.readUInt32LE(12);
  const json = JSON.parse(file.slice(20, 20 + jsonLen).toString('utf8'));
  const binChunk = 20 + jsonLen;
  const binLen = file.readUInt32LE(binChunk);
  const bin = file.slice(binChunk + 8, binChunk + 8 + binLen);
  return {json, bin, file};
}

function writeGlb(path, json, bin) {
  json.buffers[0].byteLength = bin.length;
  const jsonText = JSON.stringify(json);
  const jsonBuf = Buffer.from(jsonText, 'utf8');
  const jsonPad = (4 - (jsonBuf.length % 4)) % 4;
  const binPad = (4 - (bin.length % 4)) % 4;
  const total = 12 + 8 + jsonBuf.length + jsonPad + 8 + bin.length + binPad;
  const out = Buffer.alloc(total);
  let o = 0;
  out.writeUInt32LE(GLB_MAGIC, o); o += 4;
  out.writeUInt32LE(2, o); o += 4;
  out.writeUInt32LE(total, o); o += 4;
  out.writeUInt32LE(jsonBuf.length + jsonPad, o); o += 4;
  out.writeUInt32LE(GLB_JSON, o); o += 4;
  jsonBuf.copy(out, o); o += jsonBuf.length;
  if (jsonPad) out.fill(0x20, o, o + jsonPad), o += jsonPad;
  out.writeUInt32LE(bin.length + binPad, o); o += 4;
  out.writeUInt32LE(GLB_BIN, o); o += 4;
  bin.copy(out, o); o += bin.length;
  if (binPad) out.fill(0, o, o + binPad);
  mkdirSync(dirname(path), {recursive: true});
  writeFileSync(path, out);
}

function shouldBlackenHairImage(name) {
  if (!name || !/Hair/i.test(name)) return false;
  if (/_nml|_spe/i.test(name)) return false;
  if (/Shader_|Thumbnail/i.test(name)) return false;
  return true;
}

async function blackenHairTexture(png) {
  return sharp(png).ensureAlpha().modulate({brightness: 0.07, saturation: 0}).png().toBuffer();
}

async function patchHair(bin, json) {
  const imageByBv = new Map(json.images.map((img, idx) => [img.bufferView, {img, idx}]));
  const sorted = [...json.bufferViews.keys()].sort(
    (a, b) => (json.bufferViews[a].byteOffset ?? 0) - (json.bufferViews[b].byteOffset ?? 0),
  );
  const pieces = [];
  let offset = 0;
  for (const bvIndex of sorted) {
    const bv = json.bufferViews[bvIndex];
    const start = bv.byteOffset ?? 0;
    let slice = bin.subarray(start, start + bv.byteLength);
    const hit = imageByBv.get(bvIndex);
    if (hit && shouldBlackenHairImage(hit.img.name || '')) {
      slice = await blackenHairTexture(slice);
      console.log(`髮色：${hit.img.name}`);
    }
    bv.byteOffset = offset;
    bv.byteLength = slice.length;
    offset += slice.length;
    pieces.push(slice);
  }
  return Buffer.concat(pieces);
}

async function main() {
  if (!existsSync(seedPath)) {
    console.error(`種子不存在：${seedPath}`);
    process.exit(1);
  }
  copyFileSync(seedPath, staged);
  const {json, bin} = readGlb(staged);
  if (!json.extensionsUsed?.includes('VRM')) throw new Error('種子不是 VRM 0.0 GLB');
  const patchedBin = await patchHair(bin, json);
  writeGlb(staged, json, patchedBin);
  const recompress = join(root, 'scripts/recompress-anime-glb.mjs');
  const rc = spawnSync(process.execPath, [recompress, staged, output], {stdio: 'inherit'});
  if (rc.status !== 0) process.exit(rc.status ?? 1);
  console.log(`完成 ${output}`);
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});