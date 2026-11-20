import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const css = readFileSync(join(root, 'css/lab-office.css'), 'utf8');

function chromiumBin() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || join(process.env.HOME || '', 'PunchClock-oki/pw');
  const candidates = [
    join(base, 'chromium_headless_shell-1161/chrome-mac/headless_shell'),
    join(base, 'chromium-1161/chrome-mac/Chromium.app/Contents/MacOS/Chromium')
  ];
  return candidates.find((p) => existsSync(p));
}

function pageHtml() {
  const hash = 'f2cdbae83d24aea832968cd6708bf62ed1af8639';
  return `<!DOCTYPE html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>${css}</style>
</head>
<body class="lab-office-page">
  <section class="lab-office-root is-page" id="officePage" aria-label="虛擬辦公室">
    <div class="lab-office-frame">
      <header class="lab-office-head"><h1>虛擬辦公室</h1></header>
      <div class="lab-office-stage" data-lab-office-stage></div>
      <div class="lab-office-card-backdrop" data-lab-office-card>
        <div class="lab-office-card" role="dialog" aria-label="角色詳情">
          <img class="lab-office-portrait" alt="" width="480" height="374"
            src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7">
          <div class="lab-office-card-body">
            <h3>猛虎工程師</h3>
            <p class="lab-office-card-line">在線</p>
            <p class="lab-office-card-line">電量 76%</p>
            <p class="lab-office-card-line">任務 #254</p>
            <p class="lab-office-card-line">ai/issue-254</p>
            <p class="lab-office-card-line" data-commit>commit ${hash}</p>
            <p class="lab-office-card-line">模型 grok-4.7</p>
            <p class="lab-office-card-line">#254 · R4 · grok-4.7 · 1:35</p>
          </div>
        </div>
      </div>
    </div>
  </section>
  <pre id="metrics"></pre>
  <script>
    const card = document.querySelector('.lab-office-card');
    const line = document.querySelector('[data-commit]');
    const cr = card.getBoundingClientRect();
    const lr = line.getBoundingClientRect();
    document.getElementById('metrics').textContent = JSON.stringify({
      scroll: card.scrollWidth,
      client: card.clientWidth,
      cardRight: cr.right,
      lineRight: lr.right,
      inner: window.innerWidth
    });
  </script>
</body>
</html>`;
}

function measure(bin, width, height) {
  const dir = mkdtempSync(join(tmpdir(), 'office-card-'));
  const file = join(dir, 'card.html');
  writeFileSync(file, pageHtml());
  const args = [
    '--headless',
    '--disable-gpu',
    '--no-sandbox',
    '--allow-file-access-from-files',
    `--window-size=${width},${height}`,
    '--virtual-time-budget=2000',
    '--dump-dom',
    'file://' + file
  ];
  const run = spawnSync(bin, args, { encoding: 'utf8', timeout: 20000 });
  if (run.status !== 0 && !run.stdout.includes('metrics')) {
    throw new Error('chromium exit ' + run.status + '\n' + run.stderr);
  }
  const m = run.stdout.match(/<pre id="metrics">([\s\S]*?)<\/pre>/);
  if (!m) throw new Error('no metrics in dump: ' + run.stdout.slice(0, 500));
  return JSON.parse(m[1].trim());
}

test('詳情卡 commit 不超出卡片（390 與 1280）', (t) => {
  const bin = chromiumBin();
  if (!bin) {
    t.skip('找不到本機 Playwright Chromium（CI 環境略過）');
    return;
  }
  for (const vp of [
    { w: 390, h: 844 },
    { w: 1280, h: 720 }
  ]) {
    const m = measure(bin, vp.w, vp.h);
    assert.ok(m.client > 0, vp.w + ' clientWidth');
    assert.ok(
      m.scroll <= m.client,
      vp.w + ' scrollWidth ' + m.scroll + ' > clientWidth ' + m.client
    );
    assert.ok(m.lineRight <= m.cardRight + 1, vp.w + ' commit 超出卡片');
    assert.ok(m.cardRight <= m.inner + 1, vp.w + ' 卡片超出視窗');
  }
});
