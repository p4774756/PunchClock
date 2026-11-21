#!/usr/bin/env node
/** Merge current exceedances into data/cun-validation.json knownExceedances. */
import {readFileSync, writeFileSync} from 'node:fs';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {runCunValidation} from './lib/cun-validate.mjs';

const project = join(dirname(fileURLToPath(import.meta.url)), '..');
const configPath = join(project, 'data/cun-validation.json');
const config = JSON.parse(readFileSync(configPath, 'utf8'));
config.knownExceedances = [];

const {rows, threshold} = await runCunValidation({writeReport: false});
for (const r of rows) {
  if (r.deviationCun <= threshold) continue;
  const source = r.source === '少女' ? 'anime' : 'catalog';
  config.knownExceedances.push({
    code: r.code,
    source,
    maxDeviationCun: +r.deviationCun.toFixed(3),
    reason: '第一期基準既有偏差；修點另開單，勿為通過校驗硬移座標',
  });
}
config.knownExceedances.sort((a, b) => a.code.localeCompare(b.code));
writeFileSync(configPath, JSON.stringify(config, null, 2) + '\n');
console.log(`allowlist ${config.knownExceedances.length} entries`);