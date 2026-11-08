import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {transform} from 'esbuild';

const source = await readFile(new URL('../app/tool-drag.ts', import.meta.url), 'utf8');
const {code} = await transform(source, {loader: 'ts', format: 'esm', target: 'node22'});
const {ToolDrag} = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
let passed = 0;
const test = (name, run) => {
  run();
  passed++;
  console.log(`通过：${name}`);
};

test('空闲状态及重复结束、取消安全', () => {
  const drag = new ToolDrag();
  assert.equal(drag.pointerId, null);
  assert.equal(drag.moved, false);
  assert.equal(drag.move(1, 10, 10), null);
  assert.equal(drag.end(1), false);
  assert.equal(drag.cancel(), null);
  assert.equal(drag.cancel(), null);
});

test('抓住器具偏离接触点的位置，拖动仍保持偏移而不跳点', () => {
  const drag = new ToolDrag();
  assert.equal(drag.begin(7, 120, 75, 100, 100), true);
  assert.equal(drag.move(7, 120, 75), null);
  assert.deepEqual(drag.move(7, 140, 80), {x: 120, y: 105});
  assert.deepEqual(drag.move(7, 80, 45), {x: 60, y: 70});
  assert.equal(drag.pointerId, 7);
});

test('3 像素阈值由起始距离计算，小幅来回不累计成拖动', () => {
  const drag = new ToolDrag();
  drag.begin(1, 10, 10, 10, 10);
  for (let i = 0; i < 20; i++) {
    assert.equal(drag.move(1, i % 2 ? 12.9 : 7.1, 10), null);
  }
  assert.equal(drag.move(1, 12, 12), null);
  assert.equal(drag.moved, false);
  assert.deepEqual(drag.move(1, 13, 10), {x: 13, y: 10});
  assert.equal(drag.moved, true);
});

test('对角线跨阈值生效，回到起点仍保持拖动语义', () => {
  const drag = new ToolDrag();
  drag.begin(1, 0, 0, 0, 0);
  assert.deepEqual(drag.move(1, 2, 2.5), {x: 2, y: 2.5});
  assert.deepEqual(drag.move(1, 0, 0), {x: 0, y: 0});
  assert.equal(drag.moved, true);
});

test('同一或第二指不能覆盖拥有者，非拥有者不能移动或结束', () => {
  const drag = new ToolDrag();
  assert.equal(drag.begin(4, 20, 30, 10, 10), true);
  assert.equal(drag.begin(4, 100, 100, 100, 100), false);
  assert.equal(drag.begin(5, 100, 100, 100, 100), false);
  assert.equal(drag.move(5, 200, 200), null);
  assert.equal(drag.end(5), false);
  assert.equal(drag.pointerId, 4);
  assert.equal(drag.moved, false);
  assert.deepEqual(drag.move(4, 25, 35), {x: 15, y: 15});
});

test('结束清理状态，新的手势使用新偏移与新阈值', () => {
  const drag = new ToolDrag();
  drag.begin(3, 10, 10, 0, 0);
  drag.move(3, 30, 30);
  assert.equal(drag.end(3), true);
  assert.equal(drag.end(3), false);
  assert.equal(drag.pointerId, null);
  assert.equal(drag.moved, false);
  assert.equal(drag.begin(9, 50, 60, 45, 65), true);
  assert.equal(drag.move(9, 51, 60), null);
  assert.deepEqual(drag.move(9, 55, 60), {x: 50, y: 65});
});

test('取消返回原拥有者，可重复安全且下一次手势恢复', () => {
  const drag = new ToolDrag();
  drag.begin(0, 0, 0, 0, 0);
  drag.move(0, 20, 0);
  assert.equal(drag.cancel(), 0);
  assert.equal(drag.cancel(), null);
  assert.equal(drag.moved, false);
  assert.equal(drag.move(0, 30, 0), null);
  assert.equal(drag.begin(2, 0, 0, 0, 0), true);
  assert.deepEqual(drag.move(2, 3, 0), {x: 3, y: 0});
});

test('begin 中每个非有限输入均拒绝，不创建残留拥有者', () => {
  for (const invalid of [NaN, Infinity, -Infinity]) {
    for (let field = 0; field < 5; field++) {
      const drag = new ToolDrag();
      const args = [1, 20, 30, 10, 10];
      args[field] = invalid;
      assert.equal(drag.begin(...args), false);
      assert.equal(drag.pointerId, null);
      assert.equal(drag.moved, false);
      assert.equal(drag.begin(1, 0, 0, 0, 0), true);
    }
  }
});

test('move 非有限输入不改变状态，也不能抢占或结束拥有者', () => {
  const drag = new ToolDrag();
  drag.begin(1, 10, 10, 0, 0);
  for (const invalid of [NaN, Infinity, -Infinity]) {
    assert.equal(drag.move(invalid, 100, 100), null);
    assert.equal(drag.move(1, invalid, 100), null);
    assert.equal(drag.move(1, 100, invalid), null);
    assert.equal(drag.end(invalid), false);
    assert.equal(drag.begin(invalid, 0, 0, 0, 0), false);
    assert.equal(drag.pointerId, 1);
    assert.equal(drag.moved, false);
  }
  assert.deepEqual(drag.move(1, 15, 10), {x: 5, y: 0});
});

test('有限输入相减溢出时拒绝，输出始终保持有限', () => {
  const drag = new ToolDrag();
  assert.equal(drag.begin(1, Number.MAX_VALUE, 0, -Number.MAX_VALUE, 0), false);
  assert.equal(drag.begin(1, 0, 0, Number.MAX_VALUE, 0), true);
  assert.equal(drag.move(1, Number.MAX_VALUE, 0), null);
  assert.equal(drag.moved, false);
  assert.deepEqual(drag.move(1, -10, 0), {x: Number.MAX_VALUE, y: 0});
});

console.log(`${passed} 项工具拖动状态检查通过。`);
