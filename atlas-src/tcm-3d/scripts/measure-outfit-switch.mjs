#!/usr/bin/env node
/**
 * Headless SwiftShader outfit-switch timing (load card gone → 0/800/3000 ms → practice toggles).
 */
import {spawn} from 'node:child_process';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readFileSync} from 'node:fs';
import playwright from 'playwright';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const waitForUrl = async (url, timeoutMs = 60_000) => {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {
      /* retry */
    }
    await new Promise((r) => setTimeout(r, 200));
  }
  throw new Error(`preview not ready: ${url}`);
};

const startPreview = (port) =>
  new Promise((resolve, reject) => {
    const child = spawn(
      'npx',
      [
        'vite',
        'preview',
        '--host',
        '127.0.0.1',
        '--port',
        String(port),
        '--strictPort',
        '--base',
        '/atlas/tcm-3d/',
      ],
      {cwd: root, stdio: 'ignore', env: process.env},
    );
    child.on('error', reject);
    resolve(child);
  });

const parseBase = () => {
  const html = readFileSync(join(root, 'dist/index.html'), 'utf8');
  const base = html.match(/base href="([^"]+)"/)?.[1] ?? '/atlas/tcm-3d/';
  return base.endsWith('/') ? base : `${base}/`;
};

const armObservers = () => `
(() => {
  window.__atlasPerf = {longTasks: [], lafs: []};
  try {
    new PerformanceObserver((list) => {
      for (const e of list.getEntries()) window.__atlasPerf.longTasks.push(e.duration);
    }).observe({type: 'longtask', buffered: true});
  } catch {}
  try {
    new PerformanceObserver((list) => {
      for (const e of list.getEntries()) {
        window.__atlasPerf.lafs.push(e.blockingDuration ?? e.duration ?? 0);
      }
    }).observe({type: 'long-animation-frame', buffered: true});
  } catch {}
})();
`;

const waitLoadDone = async (page) => {
  await page.waitForFunction(
    () => {
      const card = document.querySelector('.loading-card.woodcut-loading:not(.load-error)');
      return !card;
    },
    {timeout: 180_000},
  );
  await page.getByRole('button', {name: /換裝：.*點擊開啟選單/}).waitFor({timeout: 30_000});
  await page.waitForTimeout(200);
};

const openOutfitMenu = async page => {
  const shirt = page.getByRole('button', {name: /換裝：.*點擊開啟選單/});
  if ((await shirt.getAttribute('aria-expanded')) !== 'true') await shirt.click();
  await page.waitForSelector('.outfit-bar button', {timeout: 10_000});
};

const measureClick = async (page, label) => {
  await page.evaluate(() => {
    window.__atlasPerf.longTasks = [];
    window.__atlasPerf.lafs = [];
    performance.clearMeasures('atlas:outfit-switch');
    performance.clearMarks('atlas:outfit-pressed');
    performance.clearMarks('atlas:outfit-ui-painted');
  });
  const practiceBtn = page.locator('.outfit-bar button').filter({hasText: '練習裝'});
  const schoolBtn = page.locator('.outfit-bar button').filter({hasText: '制服'});
  await openOutfitMenu(page);
  await practiceBtn.click({force: true, noWaitAfter: true});
  const pressedHandle = await page.waitForFunction(
    () => {
      const painted = performance.getEntriesByName('atlas:outfit-ui-painted', 'mark').at(-1);
      const pressed = performance.getEntriesByName('atlas:outfit-pressed', 'mark').at(-1);
      if (!painted || !pressed) return null;
      return painted.startTime - pressed.startTime;
    },
    {timeout: 15_000, polling: 50},
  );
  const pressedMs = await pressedHandle.jsonValue();
  await page.waitForTimeout(400);
  await openOutfitMenu(page);
  await schoolBtn.click({force: true});
  await page.waitForTimeout(400);
  await openOutfitMenu(page);
  await practiceBtn.click({force: true});
  await page
    .waitForFunction(() => performance.getEntriesByName('atlas:outfit-switch').length > 0, {
      timeout: 45_000,
    })
    .catch(() => {});
  return page.evaluate(({lbl, pressedLabel}) => {
    const switchMs = performance.getEntriesByName('atlas:outfit-switch').at(-1)?.duration ?? null;
    const longTasks = window.__atlasPerf?.longTasks ?? [];
    const maxLong = longTasks.length ? Math.max(...longTasks) : 0;
    const maxLaf = window.__atlasPerf?.lafs?.length ? Math.max(...window.__atlasPerf.lafs) : 0;
    return {label: lbl, pressedMs: pressedLabel, switchMs, maxLong, maxLaf};
  }, {lbl: label, pressedLabel: pressedMs});
};

const run = async () => {
  const port = 4179;
  const preview = await startPreview(port);
  const base = parseBase();
  const baseUrl = `http://127.0.0.1:${port}${base}`;
  await waitForUrl(baseUrl);

  const browser = await playwright.chromium.launch({
    headless: true,
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
  });
  const page = await browser.newPage();
  page.setDefaultTimeout(180_000);
  page.setDefaultNavigationTimeout(180_000);
  const results = [];
  for (const delay of [0, 800, 3000]) {
    await page.addInitScript(armObservers());
    await page.goto(baseUrl, {waitUntil: 'domcontentloaded', timeout: 120_000});
    await waitLoadDone(page);
    if (delay) await page.waitForTimeout(delay);
    results.push(await measureClick(page, `t+${delay}ms`));
  }
  console.log(JSON.stringify(results, null, 2));
  await browser.close();
  preview.kill('SIGTERM');
};

run().catch((err) => {
  console.error(err);
  process.exit(1);
});