import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const css = await readFile(join(root, 'app/globals.css'), 'utf8');
const page = await readFile(join(root, 'app/page.tsx'), 'utf8');

assert.match(css, /\.rpg-studio\{--rpg-dialog-offset:76px\}/, '收合預留高度應較矮，避免擋住角色');
assert.match(css, /\.acupoint-rpg-dialog:not\(\.is-expanded\)/, '收合態需限制卡片高度');
assert.match(css, /\.acupoint-rpg-dialog\.is-expanded\{--rpg-dialog-offset:min\(38dvh,340px\)\}/, '展開偏移不可過高');
assert.doesNotMatch(css, /max-width:min\(40vw,168px\)/, '手機控制列不可裁切上一穴／下一穴');
assert.match(css, /\.rpg-dialog-blurb[\s\S]*color:#f5f9ff/, '頂欄備註文字需提高對比');
assert.match(css, /width:min\(640px,calc\(100vw - 348px\)\)/, '桌面卡片需有最大寬度');
assert.match(page, /className="rpg-nav-btn"[\s\S]*?>上一穴<\/button>/);
assert.match(page, /className="rpg-nav-btn"[\s\S]*?>下一穴<\/button>/);
assert.match(page, /className="rpg-dialog-expand"/);

console.log('經絡研習室穴位卡版面約束檢查通過。');