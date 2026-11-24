#!/usr/bin/env node
/** Headless 研習室：成年角色穴位、移動針、聚焦印堂、薄紗透明度（Playwright）。 */
import assert from 'node:assert/strict';
import {spawn, spawnSync} from 'node:child_process';
import {existsSync, readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const overlaySrc = readFileSync(join(root, 'app/teaching-overlay.ts'), 'utf8');
const sceneSrc = readFileSync(join(root, 'app/character-scene.tsx'), 'utf8');
const restoreSrc = readFileSync(join(root, 'app/avatar-scene-restore.ts'), 'utf8');
const paintSrc = readFileSync(join(root, 'app/adult-outfit-paint.ts'), 'utf8');

assert.match(overlaySrc, /bindToCharacterRoot/, 'adult overlay must parent to characterRoot');
assert.match(sceneSrc, /syncResolvedPoints\(characterRoot/, 'adult sync must bind characterRoot');
assert.match(restoreSrc, /resetAnchorState/, 'stash must reset anchor for restore');
assert.match(sceneSrc, /__atlasTeachingProbe/, 'probe hook for headless assertions');
assert.match(paintSrc, /transparentWithZWrite/, 'sheer must set MToon transparency');

const waitForUrl = async (url, timeoutMs = 120_000) => {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {
      /* retry */
    }
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`preview not ready: ${url}`);
};

const ensureDist = () => {
  const index = join(root, 'dist/index.html');
  if (existsSync(index)) return;
  const built = spawnSync('npm', ['run', 'build:punchclock'], {cwd: root, stdio: 'inherit', env: process.env});
  assert.equal(built.status, 0, 'build:punchclock must succeed before e2e');
};

const parseBase = () => {
  const html = readFileSync(join(root, 'dist/index.html'), 'utf8');
  const base = html.match(/base href="([^"]+)"/)?.[1] ?? '/atlas/tcm-3d/';
  return base.endsWith('/') ? base : `${base}/`;
};

const runBrowser = async () => {
  let playwright;
  try {
    playwright = await import('playwright');
  } catch {
    console.log('adult teaching room e2e skipped (playwright not installed).');
    return;
  }

  ensureDist();
  const port = Number(process.env.ATLAS_E2E_PORT ?? 9476);
  const base = parseBase();
  const preview = spawn(
    'npx',
    ['vite', 'preview', '--host', '127.0.0.1', '--port', String(port), '--strictPort', '--base', base.replace(/\/$/, '')],
    {cwd: root, stdio: 'ignore', env: process.env},
  );

  const baseUrl = `http://127.0.0.1:${port}${base}?probe=1`;
  const pwPath = process.env.PLAYWRIGHT_BROWSERS_PATH;
  const launchOpts = {
    headless: true,
    args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
  };
  if (pwPath) launchOpts.env = {...process.env, PLAYWRIGHT_BROWSERS_PATH: pwPath};

  let browser;
  try {
    await waitForUrl(baseUrl.split('?')[0]);
    browser = await playwright.chromium.launch(launchOpts);
  } catch (err) {
    console.log('adult teaching room e2e skipped (preview/Chromium):', err.message?.split('\n')[0] ?? err);
    preview.kill('SIGTERM');
    return;
  }

  try {
    const page = await browser.newPage({viewport: {width: 1280, height: 800}});
    page.setDefaultTimeout(180_000);
    await page.goto(baseUrl, {waitUntil: 'domcontentloaded', timeout: 180_000});
    await page.waitForFunction(
      () => !document.querySelector('.loading-card.woodcut-loading:not(.load-error)'),
      {timeout: 300_000},
    );

    const adultBtn = page.locator('button', {hasText: '成年角色'});
    await adultBtn.waitFor({state: 'visible', timeout: 60_000});
    await adultBtn.click();

    await page.waitForFunction(
      () => performance.getEntriesByName('atlas:model-visible').length >= 2,
      {timeout: 300_000},
    );

    await page.waitForFunction(
      () => {
        const p = window.__atlasTeachingProbe?.();
        return p && p.avatar === 'adult' && p.skinMeshes > 0 && p.anchored && p.gvMarkerY > 1.2;
      },
      {timeout: 300_000},
    );
    await page.waitForFunction(
      () => {
        const p = window.__atlasTeachingProbe?.();
        return p && p.toolDisabled === false;
      },
      {timeout: 120_000},
    );

    const probe = await page.evaluate(() => window.__atlasTeachingProbe());
    assert.equal(probe.avatar, 'adult');
    assert.ok(probe.skinMeshes >= 1, `skin meshes: ${probe.skinMeshes}`);
    assert.ok(probe.anchored, 'overlay anchored');
    assert.equal(probe.toolDisabled, false, '移動針 must be enabled');
    assert.ok(probe.gvMarkerY > 1.25, `GV24+ marker y=${probe.gvMarkerY}`);
    const band = probe.meshNdc.maxY - probe.meshNdc.minY;
    assert.ok(probe.markerNdcY > probe.meshNdc.minY + band * 0.55, 'GV24+ in head/bust NDC band');
    assert.ok(
      probe.markerNdcX >= probe.meshNdc.minX - 0.05 &&
        probe.markerNdcX <= probe.meshNdc.maxX + 0.05 &&
        probe.markerNdcY >= probe.meshNdc.minY - 0.05 &&
        probe.markerNdcY <= probe.meshNdc.maxY + 0.05,
      'marker NDC inside mesh projection',
    );

    const focusBtn = page.getByRole('button', {name: '聚焦當前穴位'});
    await focusBtn.click();
    await page.waitForTimeout(1500);
    const afterFocus = await page.evaluate(() => window.__atlasTeachingProbe());
    assert.ok(afterFocus.controlsTargetY > 1.25, `focus target y=${afterFocus.controlsTargetY}`);

    const outfitToggle = page.getByRole('button', {name: /換裝：/});
    if ((await outfitToggle.getAttribute('aria-expanded')) !== 'true') await outfitToggle.click();
    const sheerBtn = page.locator('.outfit-bar button').filter({hasText: '薄紗'});
    await sheerBtn.click({force: true});
    await page.waitForFunction(
      () => {
        const p = window.__atlasTeachingProbe?.();
        return p?.sheerTops?.some((t) => t.transparent && (t.opacity < 0.8 || t.hasAlphaMap));
      },
      {timeout: 300_000},
    );

    const sheerProbe = await page.evaluate(() => window.__atlasTeachingProbe());
    assert.ok(sheerProbe.sheerTops?.length, 'sheer tops materials expected');
    const sheerOk = sheerProbe.sheerTops.some(
      (t) => t.transparent && (t.opacity < 0.8 || t.hasAlphaMap),
    );
    assert.ok(sheerOk, `sheer tops not transparent: ${JSON.stringify(sheerProbe.sheerTops)}`);

    console.log('adult teaching room headless checks passed.');
  } finally {
    await browser.close().catch(() => {});
    preview.kill('SIGTERM');
  }
};

await runBrowser();
console.log('validate-adult-teaching-room static checks passed.');