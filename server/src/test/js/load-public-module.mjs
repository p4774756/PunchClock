// 把 public/js 的 ES module（含相對 import）包成 data URL，讓 node:test 不依賴 package.json "type"。
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const jsDir = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'main', 'resources', 'public', 'js');

const SCENE_BUNDLE_FILES = [
  'private-team-scene.js',
  'private-team-scene-secretary.js',
  'private-team-scene-controls.js',
  'private-team-scene-window.js',
  'private-team-scene-room.js'
];

/** 拆檔後單元測試仍可 grep 完整場景原始碼（與舊版讀單檔行為等價）。 */
export function readPrivateTeamSceneBundle() {
  return SCENE_BUNDLE_FILES.map((name) => readFileSync(join(jsDir, name), 'utf8')).join('\n');
}

export function bundleSource(name, stack = [], cache) {
  if (!cache) cache = new Map();
  if (stack.indexOf(name) !== -1) throw new Error('循環引用 ' + name);
  if (cache.has(name)) return cache.get(name);
  const next = stack.concat(name);
  let source = readFileSync(join(jsDir, name), 'utf8');
  source = source.replace(/from\s+(['"])\.\/([^'"]+)\1/g, (all, quote, dep) => {
    const bundled = bundleSource(dep, next, cache);
    const url = 'data:text/javascript;base64,' + Buffer.from(bundled).toString('base64');
    return 'from ' + quote + url + quote;
  });
  cache.set(name, source);
  return source;
}

export async function loadPublicModule(name) {
  const source = bundleSource(name);
  return import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
}
