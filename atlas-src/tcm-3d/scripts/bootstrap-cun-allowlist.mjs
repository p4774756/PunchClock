#!/usr/bin/env node
/** One-off: print deviations to tune knownExceedances (not run in CI). */
import {runCunValidation} from './lib/cun-validate.mjs';

const prev = process.env.CUN_SKIP_FAIL;
process.env.CUN_SKIP_FAIL = '1';
const {rows, threshold} = await runCunValidation({writeReport: false, thresholdCun: Number(process.env.CUN_THRESHOLD) || 0.3});
for (const r of rows.filter(r => r.deviationCun > threshold).sort((a, b) => b.deviationCun - a.deviationCun)) {
  const src = r.source === '少女' ? 'anime' : 'catalog';
  console.log(`${r.code}\t${r.deviationCun.toFixed(3)}\t${src}\t${r.rule}`);
}
if (prev) process.env.CUN_SKIP_FAIL = prev;
else delete process.env.CUN_SKIP_FAIL;