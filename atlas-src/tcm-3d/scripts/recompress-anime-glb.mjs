/**
 * Re-encode anime-assistant.glb textures only (VRM 0.0 JSON / mesh unchanged).
 * Thumbnail → 1024 JPEG q82; normals → WebP lossless; colors → WebP q85 (alpha preserved).
 */
import {createHash} from 'node:crypto';
import {mkdirSync, readFileSync, renameSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import sharp from 'sharp';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const modelPath = join(root, 'public/models/anime-assistant.glb');
const digestPath = join(root, 'app/anime-model-digest.ts');

const GLB_MAGIC = 0x46546c67;
const GLB_JSON = 0x4e4f534a;
const GLB_BIN = 0x004e4942;

function readGlb(path) {
  const file = readFileSync(path);
  if (file.readUInt32LE(0) !== GLB_MAGIC) throw new Error('不是 GLB');
  const jsonLen = file.readUInt32LE(12);
  const json = JSON.parse(file.slice(20, 20 + jsonLen).toString('utf8'));
  const binChunk = 20 + jsonLen;
  const binType = file.readUInt32LE(binChunk + 4);
  if (binType !== GLB_BIN) throw new Error('缺少 BIN chunk');
  const binLen = file.readUInt32LE(binChunk);
  const bin = file.slice(binChunk + 8, binChunk + 8 + binLen);
  if (bin.length !== json.buffers[0].byteLength) throw new Error('BIN 長度與 JSON 不符');
  return {json, bin};
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
  writeFileSync(path, out);
}

async function pngHasMeaningfulAlpha(png) {
  const {channels, width, height} = await sharp(png).metadata();
  if (!channels || channels < 4 || !width || !height) return false;
  if (width * height > 512 * 512) {
    const sample = await sharp(png).resize(256, 256, {fit: 'inside'}).ensureAlpha().raw().toBuffer();
    for (let i = 3; i < sample.length; i += 4) if (sample[i] < 250) return true;
    return false;
  }
  const raw = await sharp(png).ensureAlpha().raw().toBuffer();
  for (let i = 3; i < raw.length; i += 4) if (raw[i] < 250) return false;
  return false;
}

function encodingForImage(name, width, height) {
  if (name === 'Thumbnail') return 'thumbnail';
  if (name === 'Shader_NoneNormal' || name === 'Shader_NoneBlack') return 'png';
  if (width <= 8 && height <= 8) return 'png';
  if (/_nml/i.test(name)) return 'normal';
  return 'color';
}

async function encodeImage(name, bytes) {
  const meta = await sharp(bytes).metadata();
  const kind = encodingForImage(name, meta.width ?? 0, meta.height ?? 0);
  if (kind === 'png') return {bytes, mimeType: 'image/png'};
  if (kind === 'thumbnail') {
    const out = await sharp(bytes).resize(1024, 1024, {fit: 'inside', withoutEnlargement: true})
      .jpeg({quality: 82, mozjpeg: true}).toBuffer();
    return {bytes: out, mimeType: 'image/jpeg'};
  }
  if (kind === 'normal') {
    const out = await sharp(bytes).webp({lossless: true, effort: 6}).toBuffer();
    return {bytes: out, mimeType: 'image/webp'};
  }
  const alpha = await pngHasMeaningfulAlpha(bytes);
  const pipeline = sharp(bytes);
  const out = alpha
    ? await pipeline.webp({quality: 85, alphaQuality: 85, effort: 6}).toBuffer()
    : await pipeline.webp({quality: 85, effort: 6}).toBuffer();
  return {bytes: out, mimeType: 'image/webp'};
}

async function main() {
  const inPlace = process.argv.includes('--in-place');
  const positional = process.argv.slice(2).filter(a => !a.startsWith('--'));
  const input = positional[0] ?? modelPath;
  let output = positional[1] ?? (inPlace ? `${modelPath}.tmp` : modelPath);
  const {json, bin} = readGlb(input);
  if (!json.extensionsUsed?.includes('VRM')) throw new Error('缺少 VRM 擴充');
  const imageByBv = new Map(json.images.map((img, idx) => [img.bufferView, {img, idx}]));
  const sorted = [...json.bufferViews.keys()].sort(
    (a, b) => json.bufferViews[a].byteOffset - json.bufferViews[b].byteOffset,
  );
  const pieces = [];
  let offset = 0;
  for (const bvIndex of sorted) {
    const bv = json.bufferViews[bvIndex];
    const start = bv.byteOffset ?? 0;
    const slice = bin.subarray(start, start + bv.byteLength);
    let data;
    let mime;
    const hit = imageByBv.get(bvIndex);
    if (hit) {
      const encoded = await encodeImage(hit.img.name || `image-${hit.idx}`, slice);
      data = encoded.bytes;
      mime = encoded.mimeType;
      hit.img.mimeType = mime;
      if (hit.img.uri) delete hit.img.uri;
    } else {
      data = Buffer.from(slice);
    }
    bv.byteOffset = offset;
    bv.byteLength = data.length;
    offset += data.length;
    pieces.push(data);
  }
  const newBin = Buffer.concat(pieces);
  mkdirSync(dirname(output), {recursive: true});
  writeGlb(output, json, newBin);
  const written = inPlace ? modelPath : output;
  if (inPlace) {
    renameSync(output, modelPath);
  }
  const final = readFileSync(written);
  const digest = createHash('sha256').update(final).digest('hex').slice(0, 12);
  writeFileSync(digestPath, `/** Content digest for anime-assistant.glb (cache bust). */\nexport const ANIME_MODEL_DIGEST='${digest}';\nexport const ANIME_MODEL_BYTES=${final.length};\n`);
  const gzip = Number(process.env.STAT_GZIP);
  console.log(`寫入 ${written}`);
  console.log(`原始位元組：${final.length}`);
  console.log(`SHA-256 前 12 字：${digest}`);
  if (!Number.isNaN(gzip)) console.log(`（請在本機用 gzip -9 量測傳輸大小）`);
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});