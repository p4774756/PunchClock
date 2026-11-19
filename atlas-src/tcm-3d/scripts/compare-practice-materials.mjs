#!/usr/bin/env node
/** Listen for atlas:practice-material-compare after post-card practice warm (DEV build). */
import {spawn} from 'node:child_process';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
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
      ['vite', '--host', '127.0.0.1', '--port', String(port), '--strictPort', '--base', '/atlas/tcm-3d/'],
      {cwd: root, stdio: 'ignore', env: process.env},
    );
    child.on('error', reject);
    resolve(child);
  });

const run = async () => {
  const port = 4182;
  const preview = await startPreview(port);
  const baseUrl = `http://127.0.0.1:${port}/atlas/tcm-3d/`;
  await waitForUrl(baseUrl);

  const browser = await playwright.chromium.launch({
    headless: true,
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
  });
  const page = await browser.newPage();
  let compareMsg = null;
  page.on('console', (msg) => {
    const t = msg.text();
    if (t.includes('atlas:practice-material-compare')) compareMsg = t;
  });
  await page.goto(baseUrl, {waitUntil: 'domcontentloaded', timeout: 120_000});
  await page.waitForFunction(
    () => !document.querySelector('.loading-card.woodcut-loading:not(.load-error)'),
    {timeout: 180_000},
  );
  await page.waitForFunction(
    () => {
      const entries = performance.getEntriesByName('practice-outfit-build');
      return entries.length > 0;
    },
    {timeout: 120_000},
  );
  await page.waitForTimeout(300);
  console.log(compareMsg ?? 'no atlas:practice-material-compare log');
  const ok = compareMsg?.includes('ok');
  await browser.close();
  preview.kill('SIGTERM');
  process.exit(ok ? 0 : 1);
};

run().catch((err) => {
  console.error(err);
  process.exit(1);
});