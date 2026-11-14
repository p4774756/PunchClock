// 把 public/js 的 ES module（含相對 import）包成 data URL，讓 node:test 不依賴 package.json "type"。
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const jsDir = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'main', 'resources', 'public', 'js');

export function bundleSource(name, stack = []) {
  if (stack.indexOf(name) !== -1) throw new Error('循環引用 ' + name);
  const next = stack.concat(name);
  let source = readFileSync(join(jsDir, name), 'utf8');
  source = source.replace(/from\s+(['"])\.\/([^'"]+)\1/g, (all, quote, dep) => {
    const bundled = bundleSource(dep, next);
    const url = 'data:text/javascript;base64,' + Buffer.from(bundled).toString('base64');
    return 'from ' + quote + url + quote;
  });
  return source;
}

export async function loadPublicModule(name) {
  const source = bundleSource(name);
  return import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
}
