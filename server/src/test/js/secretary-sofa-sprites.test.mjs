import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { loadPublicModule } from './load-public-module.mjs';

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '../../../..');
const assetDir = join(repoRoot, 'server/src/main/resources/public/assets/private-team');

const SOFA_FILES = ['secretary-sofa-v2.webp', 'secretary-stage-sofa.webp'];
const SOFA_W = 864;
const SOFA_H = 1152;

/** 掃 RIFF chunk，回傳 chunk 名稱與 VP8X 旗標。 */
function webpChunks(buf) {
  assert.equal(buf.toString('latin1', 0, 4), 'RIFF');
  assert.equal(buf.toString('latin1', 8, 12), 'WEBP');
  const names = [];
  let flags = null;
  let p = 12;
  while (p + 8 <= buf.length) {
    const name = buf.toString('latin1', p, p + 4);
    const len = buf.readUInt32LE(p + 4);
    if (name === 'VP8X') flags = buf[p + 8];
    names.push(name);
    p += 8 + len + (len & 1);
  }
  return { names, flags };
}

test('沙發坐姿立繪是帶 alpha 的 WebP（舊檔無 alpha、黑底會在手機變成黑方塊）', () => {
  for (const name of SOFA_FILES) {
    const path = join(assetDir, name);
    assert.ok(existsSync(path), name);
    const st = statSync(path);
    assert.ok(st.size > 20_000 && st.size < 160_000, `${name} size ${st.size}`);
    const { names, flags } = webpChunks(readFileSync(path));
    assert.ok(names.includes('VP8X'), `${name} 缺 VP8X 擴充標頭`);
    assert.ok(names.includes('ALPH'), `${name} 缺 ALPH 透明通道`);
    assert.ok((flags & 0x10) !== 0, `${name} VP8X 沒開 alpha 旗標`);
  }
});

test('預設服沙發立繪換了新檔名，不再沿用被瀏覽器長快取的舊網址', async () => {
  assert.equal(existsSync(join(assetDir, 'secretary-sofa.webp')), false);
  const { secretarySpritePackForOutfit } = await loadPublicModule('private-team-secretary-outfit.js');
  assert.equal(secretarySpritePackForOutfit('default').sofa, '/assets/private-team/secretary-sofa-v2.webp');
  assert.equal(secretarySpritePackForOutfit('stage').sofa, '/assets/private-team/secretary-stage-sofa.webp');
});

test('沙發立繪像素 alpha 檢查：四周全透明、無整張底色、無大塊純黑不透明', () => {
  const py = process.env.PYTHON_BINARY?.trim() || 'python3';
  const code = `
import sys
import numpy as np
from PIL import Image

ok = True
for path in sys.argv[1:]:
    im = Image.open(path)
    if im.mode != "RGBA":
        print("FAIL", path, "mode", im.mode); ok = False; continue
    if im.size != (${SOFA_W}, ${SOFA_H}):
        print("FAIL", path, "size", im.size); ok = False; continue
    a = np.array(im)
    al = a[..., 3]
    ring = 8
    border = np.concatenate([al[:ring].ravel(), al[-ring:].ravel(), al[:, :ring].ravel(), al[:, -ring:].ravel()])
    if border.max() != 0:
        print("FAIL", path, "border alpha max", int(border.max())); ok = False
    clear = float((al == 0).mean())
    solid = float((al >= 200).mean())
    if clear < 0.5:
        print("FAIL", path, "transparent share", round(clear, 3)); ok = False
    if not (0.10 <= solid <= 0.40):
        print("FAIL", path, "opaque share", round(solid, 3)); ok = False
    ys, xs = np.where(al > 128)
    bbox = (xs.max() - xs.min() + 1) * (ys.max() - ys.min() + 1)
    fill = float((al > 128).sum()) / float(bbox)
    if fill > 0.5:
        print("FAIL", path, "bbox fill", round(fill, 3), "looks like a filled card"); ok = False
    rgb = a[..., :3].astype(int)
    black = (al >= 200) & (rgb.max(axis=2) < 24)
    black_share = float(black.sum()) / max(float((al >= 200).sum()), 1.0)
    if black_share > 0.02:
        print("FAIL", path, "opaque near-black share", round(black_share, 4)); ok = False
    print("ok", path.split("/")[-1], "clear", round(clear, 3), "solid", round(solid, 3), "fill", round(fill, 3), "black", round(black_share, 4))
sys.exit(0 if ok else 1)
`;
  const r = spawnSync(py, ['-c', code, ...SOFA_FILES.map((n) => join(assetDir, n))], {
    encoding: 'utf8',
    timeout: 120_000
  });
  const out = (r.stdout || '') + (r.stderr || '');
  assert.equal(r.status, 0, out);
  assert.match(out, /ok secretary-sofa-v2\.webp/);
  assert.match(out, /ok secretary-stage-sofa\.webp/);
});
