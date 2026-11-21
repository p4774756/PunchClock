#!/usr/bin/env node
import {runCunValidation} from './lib/cun-validate.mjs';

const {passed, failures, rows, threshold} = await runCunValidation({writeReport: true});
if (!passed) {
  console.error(`骨度分寸校驗失敗（門檻 ${threshold} 寸）：\n${failures.join('\n')}`);
  process.exit(1);
}
const ok = rows.filter(r => r.status === 'OK').length;
const known = rows.filter(r => r.status.startsWith('已知')).length;
const landmark = rows.filter(r => r.status === '地標來源').length;
console.log(`骨度分寸校驗通過：${rows.length} 穴受檢（${ok} 在門檻內，${known} 已知超標允許，${landmark} 為地標本身）。`);