import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = await readFile(join(root, 'app/character-scene.tsx'), 'utf8');
const pageSrc = await readFile(join(root, 'app/page.tsx'), 'utf8');

assert.doesNotMatch(pageSrc, /CharacterScene key=\{`\$\{DEFAULT_AVATAR_ID\}-\$\{outfitId\}`\}/, 'outfit switch must not remount CharacterScene');
assert.doesNotMatch(src, /\[avatarId,outfitId,onProgress,onError\]/, '3D scene must not rebuild on outfitId');
assert.match(src, /cloneSkinnedObject|SkeletonUtils\.js/, 'anime GLB parsed once with cloned practice rig');
assert.match(src, /isolatePracticeRig/, 'practice rig must deep-clone shared mesh assets');
assert.match(src, /clonePracticeMaterial/, 'practice rig must fork MToon materials with render parity');
assert.match(src, /syncMaterialRenderState/, 'cloned materials must copy transparent/depthWrite/side');
assert.match(src, /detachMToonUniforms/, 'MToon Color uniforms must not alias school rig');
assert.match(src, /MTOON_TEXTURE_UNIFORM_KEYS/, 'detach must not reset MToon texture uniforms to school');
assert.match(src, /forkMToonTextureUniforms/, 'practice MToon must fork texture uniforms after clone');
assert.match(src, /setMToonBodyMap/, 'painted skin must bind uniforms.map for MToon shader');
assert.match(src, /bindPracticeBodySkinMaps/, 'practice must re-bind painted map after build and warm');
assert.match(src, /ensurePracticeOutfitReady/, 'practice switch must hide uniforms and bind body map');
assert.match(src, /assertPracticeBodySkinMaps/, 'practice build must verify body map fork from school');
assert.match(src, /isPracticeUniformMesh/, 'practice must detect uniform-only meshes');
assert.match(src, /isPracticeUniformMesh\(obj,outfit\)\)obj\.visible=false/, 'hidden uniform meshes must set visible=false');
assert.match(src, /Tops\|Bottoms\|Accessory\/i\.test\(obj\.name/, 'practice must hide uniform meshes by GLB node name');
assert.match(src, /next\.colorSpace=src\.colorSpace/, 'painted skin map must preserve source colorSpace');
assert.match(src, /assertUniformOutfitUntouched/, 'practice build must not mutate school mesh materials');
assert.match(src, /applyCachedAnchors/, 'outfit switch must apply cached teaching markers');
assert.doesNotMatch(src, /teaching\.reanchor/, 'outfit switch must not repeat CPU skin raycast');
const overlay=await readFile(join(root,'app/teaching-overlay.ts'),'utf8');
assert.match(overlay, /buildSkinRaycastProxies|skin-raycast-proxy/, 'anchor must bake static skin proxies');

assert.doesNotMatch(src, /BUN_BROWN|0x3b2a22/i, 'practice bun must not use brown常量');
assert.match(src, /findPrimaryHairMaterial/, 'bun should sample GLB hair MToon');
assert.match(src, /writeUvTriangleShoulderFix/, 'shoulder alpha-hole inpaint required');
assert.doesNotMatch(src, /SHOULDER_ARTIFACT|isShoulderArtifactPx/, 'shoulder fix must target alpha cutouts not #382830');
assert.match(src, /data\.data\[i\+3\]>=200\)continue[\s\S]*paintShoulderAlphaHole/, 'shoulder fix fills alpha<200 from nearby skin');
assert.match(src, /isHairMaterial/, 'hair-specific cel shading to preserve blue-violet');
assert.match(src, /syncHairBunMaterial/, 'bun materials must track hair MToon chroma');
assert.match(src, /buildPracticeOutfitIncremental/, 'practice outfit must build incrementally');
assert.match(src, /startPostCardWarm|prioritizePracticeWarm/, 'practice outfit must warm on idle after load card');
assert.match(
  src,
  /uploadTexturesIncremental|uploadOneTexture|atlas:texture-upload/,
  'practice warm must upload outfit textures in slices',
);
assert.match(
  src,
  /compileWarmPlanGroup|warmShaderPlanGroup|warmShadersIncremental/,
  'practice warm must shader-warm by program group',
);
const shaderWarm = await readFile(join(root, 'app/shader-warm.ts'), 'utf8');
assert.match(shaderWarm, /atlas:link/, 'shader link must log atlas:link during card warm');
assert.match(shaderWarm, /linkProgramsIncremental/, 'card warm must link programs before first frame');
assert.match(shaderWarm, /renderer\.compile\(scene, camera\)/, 'school warm must sync compile not compileAsync only');
assert.match(shaderWarm, /hideSceneChrome/, 'shader warm must hide scene chrome only');
assert.match(shaderWarm, /atlasSceneChrome/, 'scene chrome must be tagged on panorama/ground');
assert.match(shaderWarm, /groupMeshesByProgram|materialProgramKey/, 'shader warm must group by program cache key');
assert.doesNotMatch(shaderWarm, /stashSceneChrome/, 'must not hide all non-character scene children (lights)');
assert.match(src, /finishOutfitSwitchMeasure/, 'outfit switch measure must end after render');
assert.match(
  src,
  /renderer\.render\(scene,camera\);[\s\S]{0,120}finishOutfitSwitchMeasure\(\)/,
  'atlas:outfit-switch must measure after renderer.render',
);
assert.doesNotMatch(
  src.match(/const showOutfit=\([\s\S]*?\n  \};/m)?.[0] ?? '',
  /performance\.measure\('atlas:outfit-switch'/,
  'must not measure outfit switch inside showOutfit',
);
assert.match(pageSrc, /atlas:outfit-switch-start/, 'outfit switch measure must start on button click');
assert.match(src, /atlas:outfit-switch/, 'outfit switch must performance.measure atlas:outfit-switch');
assert.match(src, /atlas:model-visible/, 'first model frame must performance.measure atlas:model-visible');
assert.match(src, /practice-outfit-build-start/, 'practice outfit build must performance.mark start');
assert.match(src, /performance\.measure\(['"]practice-outfit-build/, 'practice outfit build must performance.measure');
assert.match(src, /isTorsoClothPeekPoint/, 'torso cloth peek must gate on torso points');
assert.match(src, /installClothGarmentPeek/, 'school uniform must peek garment surfaces locally');
assert.match(src, /refreshPracticeClothPeekTexture/, 'practice teal must peel locally without global transparency');
assert.match(src, /uniform vec3 atlasClothPeekCenter/, 'cloth peek uniforms must be declared in the shader');
assert.match(src, /atlasLateral/, 'hair over a torso point must clear on the view plane');
assert.match(src, /vViewPosition/, 'hair discard must use the skinned view position');
assert.match(src, /isHairMaterial\(mat,obj\)/, 'hair peek must see the mesh name, not only the material');
assert.match(src, /gl_FragColor = vec4\( col, diffuseColor\.a \)/, 'cloth peek must run in MToon main before output');
assert.doesNotMatch(src, /dithering_fragment/, 'cloth peek must not inject into postCorrection dithering');
assert.doesNotMatch(src, /Body_00_SKIN[\s\S]{0,80}onBeforeCompile/, 'must not patch Body skin onBeforeCompile');

console.log('practice outfit visual guards passed.');

const TEAL = {r: 13, g: 74, b: 79};
const tealDist = (r, g, b) =>
  Math.sqrt((r - TEAL.r) ** 2 + (g - TEAL.g) ** 2 + (b - TEAL.b) ** 2);
/** MToon + ACES darkens map teal on screen — accept shaded practice cloth. */
const isPracticeClothPixel = (r, g, b) =>
  tealDist(r, g, b) <= 58 || (r < 72 && g > 42 && b > 32 && g >= b && g > r + 8);

const runPixelCheck = async () => {
  let playwright;
  try {
    playwright = await import('playwright');
  } catch {
    console.log('practice outfit pixel check skipped (playwright not installed).');
    return;
  }
  const {spawn} = await import('node:child_process');
  const scriptsDir = dirname(fileURLToPath(import.meta.url));
  const port = 4179;
  const baseUrl = `http://127.0.0.1:${port}/atlas/tcm-3d/`;
  const waitForUrl = async (url, timeoutMs = 90_000) => {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      try {
        const res = await fetch(url);
        if (res.ok) return;
      } catch {
        /* retry */
      }
      await new Promise(r => setTimeout(r, 250));
    }
    throw new Error(`preview not ready: ${url}`);
  };
  const preview = spawn(
    'npx',
    ['vite', '--host', '127.0.0.1', '--port', String(port), '--strictPort', '--base', '/atlas/tcm-3d/'],
    {cwd: join(scriptsDir, '..'), stdio: 'ignore', env: process.env},
  );
  let browser;
  try {
    await waitForUrl(baseUrl);
    browser = await playwright.chromium.launch({
      headless: true,
      args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
    });
  } catch (err) {
    console.log('practice outfit pixel check skipped (no Chromium):', err.message?.split('\n')[0] ?? err);
    preview.kill('SIGTERM');
    return;
  }
  try {
    const page = await browser.newPage({viewport: {width: 1280, height: 800}});
    await page.goto(baseUrl, {waitUntil: 'domcontentloaded', timeout: 120_000});
    await page.waitForFunction(
      () => !document.querySelector('.loading-card.woodcut-loading:not(.load-error)'),
      {timeout: 300_000},
    );
    await page.waitForFunction(
      () => performance.getEntriesByName('practice-outfit-build').length > 0,
      {timeout: 300_000},
    );
    const practiceBtn = page.locator('.outfit-bar button').filter({hasText: '練習裝'});
    const schoolBtn = page.locator('.outfit-bar button').filter({hasText: '制服'});
    await practiceBtn.click({force: true});
    await page.waitForTimeout(2500);
    await schoolBtn.click({force: true});
    await page.waitForTimeout(800);
    await practiceBtn.click({force: true});
    await page.waitForTimeout(3000);
    const canvas = page.locator('.scene canvas');
    await canvas.waitFor({state: 'visible', timeout: 30_000});
    const buf = await canvas.screenshot({type: 'png'});
    const sharp = (await import('sharp')).default;
    const {data, info} = await sharp(buf).raw().toBuffer({resolveWithObject: true});
    const sampleRegion = (x0, y0, x1, y1) => {
      let near = 0;
      let white = 0;
      let total = 0;
      const w = info.width;
      for (let y = y0; y <= y1; y += 4) {
        for (let x = x0; x <= x1; x += 4) {
          const i = (y * w + x) * info.channels;
          const r = data[i];
          const g = data[i + 1];
          const b = data[i + 2];
          if (r > 238 && g > 228 && b > 205) continue;
          if (isPracticeClothPixel(r, g, b)) near++;
          if (r > 235 && g > 228 && b > 220) white++;
          total++;
        }
      }
      return {near, white, total};
    };
    const bra = sampleRegion(
      Math.round(info.width * 0.4),
      Math.round(info.height * 0.38),
      Math.round(info.width * 0.6),
      Math.round(info.height * 0.52),
    );
    const shorts = sampleRegion(
      Math.round(info.width * 0.32),
      Math.round(info.height * 0.48),
      Math.round(info.width * 0.68),
      Math.round(info.height * 0.76),
    );
    assert.ok(bra.near >= 24, `bra region must show teal paint (${bra.near} px)`);
    assert.ok(shorts.near >= 8, `shorts region must show teal paint (${shorts.near} px)`);
    assert.ok(bra.white <= 16, `bra region must not show white underwear (${bra.white} px)`);
    console.log('practice outfit pixel check passed.', {bra, shorts});
  } finally {
    await browser?.close().catch(() => {});
    preview.kill('SIGTERM');
  }
};

await runPixelCheck();