#!/usr/bin/env node
/** Headless A/B atlas:model-visible (SwiftShader). Usage: node scripts/measure-model-visible.mjs [pr|main] */
import {spawn} from 'node:child_process';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readFileSync} from 'node:fs';
import playwright from 'playwright';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const label = process.argv[2] ?? 'pr';

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

const startPreview = (port, cwd) =>
  new Promise((resolve, reject) => {
    const child = spawn(
      'npx',
      ['vite', 'preview', '--host', '127.0.0.1', '--port', String(port), '--strictPort', '--base', '/atlas/tcm-3d/'],
      {cwd, stdio: 'ignore', env: process.env},
    );
    child.on('error', reject);
    resolve(child);
  });

const parseBase = (cwd) => {
  const html = readFileSync(join(cwd, 'dist/index.html'), 'utf8');
  const base = html.match(/base href="([^"]+)"/)?.[1] ?? '/atlas/tcm-3d/';
  return base.endsWith('/') ? base : `${base}/`;
};

const armObservers = () => `
(() => {
  window.__atlasPerf = {longTasks: [], maxGap: 0, lastFrame: performance.now()};
  try {
    new PerformanceObserver((list) => {
      for (const e of list.getEntries()) window.__atlasPerf.longTasks.push(e.duration);
    }).observe({type: 'longtask', buffered: true});
  } catch {}
  const track = () => {
    const now = performance.now();
    const gap = now - window.__atlasPerf.lastFrame;
    if (gap > window.__atlasPerf.maxGap) window.__atlasPerf.maxGap = gap;
    window.__atlasPerf.lastFrame = now;
    requestAnimationFrame(track);
  };
  requestAnimationFrame(track);
})();
`;

const measureVisit = async (page, baseUrl, reload) => {
  if (reload) {
    await page.goto(baseUrl, {waitUntil: 'domcontentloaded', timeout: 120_000});
  }
  await page.waitForFunction(
    () => !document.querySelector('.loading-card.woodcut-loading:not(.load-error)'),
    {timeout: 180_000},
  );
  const stats = await page.evaluate(() => {
    const model = performance.getEntriesByName('atlas:model-visible').at(-1)?.duration ?? null;
    const frame = performance.getEntriesByName('atlas:model-frame').at(-1)?.duration ?? null;
    const long = window.__atlasPerf?.longTasks?.length ? Math.max(...window.__atlasPerf.longTasks) : 0;
    const maxGap = window.__atlasPerf?.maxGap ?? 0;
    const programs = window.__atlasShaderPrograms ?? null;
    return {model, frame, maxLong: long, maxGap, programs};
  });
  return stats;
};

const run = async () => {
  const port = label === 'main' ? 4181 : 4180;
  const cwd = label === 'main' ? '/tmp/wt-229-main-ab/atlas-src/tcm-3d' : root;
  const preview = await startPreview(port, cwd);
  const baseUrl = `http://127.0.0.1:${port}${parseBase(cwd)}`;
  await waitForUrl(baseUrl);

  const browser = await playwright.chromium.launch({
    headless: true,
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
  });
  const results = [];
  for (let i = 0; i < 2; i++) {
    const page = await browser.newPage();
    page.setDefaultTimeout(180_000);
    page.setDefaultNavigationTimeout(180_000);
    await page.addInitScript(armObservers());
    await page.addInitScript(() => {
      const orig = console.debug;
      console.debug = (...args) => {
        if (String(args[0]).includes('atlas:shader-warm programs')) {
          window.__atlasShaderPrograms = args.slice(1);
        }
        orig(...args);
      };
    });
    await page.goto(baseUrl, {waitUntil: 'domcontentloaded', timeout: 120_000});
    results.push({visit: i === 0 ? 'cold' : 'warm', ...(await measureVisit(page, baseUrl, false))});
    await page.close();
    if (i === 0) await new Promise((r) => setTimeout(r, 400));
  }
  console.log(JSON.stringify({label, results}, null, 2));
  await browser.close();
  preview.kill('SIGTERM');
};

run().catch((err) => {
  console.error(err);
  process.exit(1);
});