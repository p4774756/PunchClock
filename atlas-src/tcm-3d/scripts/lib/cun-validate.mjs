import assert from 'node:assert/strict';
import {readFile, writeFile, mkdir, access} from 'node:fs/promises';
import {constants} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import * as T from 'three';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const project = resolve(scriptDir, '../..');

const v3 = (arr) => new T.Vector3(arr[0], arr[1], arr[2]);

/** @param {Record<string, number[]>} landmarks */
function cunScalars(landmarks, cunFrom) {
  const out = {vertical: null, headVertical: null, lateral: null, anteroposterior: null};
  for (const row of cunFrom) {
    const a = landmarks[row.from];
    const b = landmarks[row.to];
    assert.ok(a && b, `cunFrom 缺少地標 ${row.from} 或 ${row.to}`);
    const axis = row.axis;
    let span;
    if (axis === 'vertical') span = Math.abs(a[1] - b[1]);
    else if (axis === 'lateral') {
      span = row.from === 'midline' ? Math.abs(b[0]) : Math.abs(a[0] - b[0]);
    } else if (axis === 'anteroposterior') span = Math.abs(a[2] - b[2]);
    else throw new Error(`未知 cun 軸 ${axis}`);
    const per = span / row.cun;
    assert.ok(per > 1e-6, `骨度跨度為零：${row.from}→${row.to}`);
    if (row.verticalKind === 'head') out.headVertical = per;
    else if (axis === 'vertical') out.vertical = per;
    else if (axis === 'lateral') out.lateral = per;
    else out.anteroposterior = per;
  }
  if (out.lateral == null && landmarks.left_nipple) {
    out.lateral = Math.abs(landmarks.left_nipple[0]) / 4;
  }
  if (out.vertical == null && landmarks.xiphoid && landmarks.navel) {
    out.vertical = Math.abs(landmarks.xiphoid[1] - landmarks.navel[1]) / 8;
  }
  if (out.headVertical == null && landmarks.anterior_hairline && landmarks.yintang) {
    out.headVertical = Math.abs(landmarks.anterior_hairline[1] - landmarks.yintang[1]) / 3;
  }
  if (out.anteroposterior == null) out.anteroposterior = out.lateral ?? out.vertical;
  assert.ok(out.vertical && out.lateral, '無法推算寸（缺少地標或骨度跨度）');
  return out;
}

/** @param {number[]} actual @param {number[]} expected @param {{vertical:number,lateral:number,anteroposterior:number}} scalars */
function deviationCun(actual, expected, scalars, midline) {
  const d = v3(actual).sub(v3(expected));
  const dx = midline ? 0 : Math.abs(d.x) / scalars.lateral;
  const dy = Math.abs(d.y) / scalars.vertical;
  const dz = Math.abs(d.z) / scalars.anteroposterior;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

/**
 * @param {object} rule
 * @param {Record<string, number[]>} landmarks
 * @param {{vertical:number,lateral:number,anteroposterior:number}} scalars
 * @param {'anime'|'catalog'} spaceKey
 */
function expectedFromRule(rule, landmarks, scalars, spaceKey) {
  const axisSign = spaceKey === 'anime'
    ? {vertical: 1, lateral: 1, anterior: -1}
    : {vertical: 1, lateral: 1, anterior: 1};
  const vertUnit = rule.verticalKind === 'head' ? scalars.headVertical ?? scalars.vertical : scalars.vertical;

  if (rule.at) {
    const lm = landmarks[rule.at];
    assert.ok(lm, `地標 ${rule.at}`);
    return [...lm];
  }

  if (rule.midpointOf) {
    const a = landmarks[rule.midpointOf[0]];
    const b = landmarks[rule.midpointOf[1]];
    assert.ok(a && b);
    return [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2];
  }

  const ref = landmarks[rule.ref];
  assert.ok(ref, `規則 ${rule.code} 缺少地標 ${rule.ref}`);
  const pos = v3(ref);

  if (rule.verticalCun != null) pos.y += rule.verticalCun * vertUnit;
  if (rule.lateralCun != null) pos.x += rule.lateralCun * scalars.lateral;
  if (rule.anteriorCun != null) pos.z += rule.anteriorCun * scalars.anteroposterior * axisSign.anterior;
  if (rule.along) {
    const to = landmarks[rule.along.to];
    assert.ok(to);
    const t = rule.along.fraction;
    const mixed = v3(ref).lerp(v3(to), t);
    return mixed.toArray();
  }
  if (rule.posterior && spaceKey === 'anime') {
    pos.z = Math.abs(pos.z) > 0.02 ? pos.z : (landmarks.c7_spinous?.[2] ?? 0.08);
    pos.x = 0;
  }
  if (rule.posterior && spaceKey === 'catalog') {
    pos.z = landmarks.c7_spinous?.[2] ?? pos.z;
    pos.x = 0;
  }
  return [pos.x, pos.y, pos.z];
}

function ruleSummary(rule) {
  if (rule.summary) return rule.summary;
  const parts = [];
  if (rule.at) parts.push(`@${rule.at}`);
  if (rule.ref) parts.push(`ref ${rule.ref}`);
  if (rule.verticalCun != null) parts.push(`垂直 ${rule.verticalCun} 寸`);
  if (rule.lateralCun != null) parts.push(`旁開 ${rule.lateralCun} 寸`);
  if (rule.anteriorCun != null) parts.push(`前後 ${rule.anteriorCun} 寸`);
  if (rule.along) parts.push(`沿 ${rule.along.to} ${rule.along.fraction}`);
  if (rule.midpointOf) parts.push(`中點 ${rule.midpointOf.join('–')}`);
  return parts.join('；');
}

async function modelExists() {
  try {
    await access(join(project, 'public/models/anime-assistant.glb'), constants.R_OK);
    return true;
  } catch { return false; }
}

/**
 * @param {{writeReport?: boolean, thresholdCun?: number}} [opts]
 */
export async function runCunValidation(opts = {}) {
  const config = JSON.parse(await readFile(join(project, 'data/cun-validation.json'), 'utf8'));
  const threshold = opts.thresholdCun ?? config.thresholdCun ?? 0.3;
  const worsen = config.worsenSlackCun ?? 0.08;
  const animeJson = JSON.parse(await readFile(join(project, 'app/anime-points.json'), 'utf8'));
  const catalog = JSON.parse(await readFile(join(project, 'app/teaching-points.json'), 'utf8'));
  const catalogBy = new Map(catalog.map(p => [p.code, p]));
  const animeBy = new Map((animeJson.points || []).map(p => [p.code, p]));
  const hasModel = await modelExists();

  const rows = [];
  const failures = [];

  for (const rule of config.rules) {
    const code = rule.code;
    const name = catalogBy.get(code)?.name ?? animeBy.get(code)?.name ?? code;
    const onAnime = animeBy.has(code);
    const source = onAnime ? '少女' : '目錄';
    if (onAnime && !hasModel) {
      failures.push(`${code} 已烘上身但缺少 anime-assistant.glb，無法比對少女座標`);
      continue;
    }
    const actualSource = onAnime ? animeBy : catalogBy;
    const point = actualSource.get(code);
    if (!point) {
      failures.push(`${code} 不在比對來源`);
      continue;
    }
    const spaceKey = onAnime ? 'anime' : 'catalog';
    const space = config.spaces[spaceKey];
    const landmarks = {...space.landmarks};
    if (!landmarks.midline) landmarks.midline = [0, 0, 0];
    const scalars = cunScalars(landmarks, space.cunFrom);

    let actual = [...point.position];
    if (onAnime && !point.midline && spaceKey === 'anime') {
      actual = [Math.abs(actual[0]), actual[1], actual[2]];
    }
    const expected = expectedFromRule(rule, landmarks, scalars, spaceKey);
    const mid = rule.midline ?? point.midline ?? false;
    const dev = deviationCun(actual, expected, scalars, mid);

    const allow = config.knownExceedances?.find(
      e => e.code === code && (e.source === spaceKey || e.source === (onAnime ? 'anime' : 'catalog')),
    );
    let status = dev <= threshold ? 'OK' : '超標';
    if (allow) {
      const cap = allow.maxDeviationCun ?? threshold;
      if (dev <= cap) status = '已知超標（允許）';
      else if (dev > cap + worsen) {
        status = '惡化';
        failures.push(`${code} 偏差惡化：${dev.toFixed(3)} 寸 > 允許 ${cap.toFixed(3)}（${allow.reason ?? ''}）`);
      } else {
        status = '已知超標（允許）';
      }
    } else if (dev > threshold) {
      failures.push(`${code}（${name}）偏差 ${dev.toFixed(3)} 寸 > 門檻 ${threshold} [${source}]`);
    }

    rows.push({
      code, name, source, rule: ruleSummary(rule),
      expected, actual, deviationCun: dev, status,
      note: onAnime ? '' : '僅目錄、未烘上身',
    });
  }

  rows.sort((a, b) => a.code.localeCompare(b.code));

  const lines = [
    `# 骨度分寸穴位偏差報表`,
    `門檻：${threshold} 寸；受檢 ${rows.length} 穴`,
    '',
    '| 代碼 | 名稱 | 來源 | 規則 | 偏差(寸) | 狀態 |',
    '| --- | --- | --- | --- | ---: | --- |',
  ];
  for (const r of rows) {
    lines.push(`| ${r.code} | ${r.name} | ${r.source} | ${r.rule} | ${r.deviationCun.toFixed(3)} | ${r.status}${r.note ? '；' + r.note : ''} |`);
  }
  const report = lines.join('\n');
  console.log(report);

  if (opts.writeReport !== false) {
    const outDir = join(project, '.validation');
    await mkdir(outDir, {recursive: true});
    await writeFile(join(outDir, 'cun-offset-report.md'), report, 'utf8');
  }

  return {passed: failures.length === 0, failures, rows, report, threshold};
}