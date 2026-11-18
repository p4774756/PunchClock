#!/usr/bin/env node
/** 從 atlas teaching 資料產出 public/island/tcm-bronze-points.json（check in，不在執行期編譯）。 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const pointsPath = join(root, 'atlas-src/tcm-3d/app/teaching-points.json');
const teachingTs = readFileSync(join(root, 'atlas-src/tcm-3d/app/teaching.ts'), 'utf8');

const COMMON_10 = Object.freeze({
  LI4: {
    indications: '頭痛、齒痛、面口病、感冒發熱、上肢痠痛等。',
    method:
      '在第 2 掌骨橈側中點，以拇指按揉或按壓，由輕而重，局部酸脹即可。孕婦慎用。'
  },
  ST36: {
    indications: '脾胃不和、腹痛、腹脹、消化不良、下肢痠痛；保健要穴。',
    method: '犢鼻下 3 寸、脛骨前嵴外約一橫指，以拇指按揉，酸脹為度。'
  },
  SP6: {
    indications: '脾胃不和、月經不調、失眠、下肢痠痛等。',
    method: '內踝尖上 3 寸、脛骨內側後緣，以拇指按揉。孕婦慎用。'
  },
  LR3: {
    indications: '頭痛、眩暈、煩躁、目疾、下肢痠痛等。',
    method: '足背第 1、2 蹠骨結合部前方凹陷，以拇指按揉，酸脹為度。'
  },
  PC6: {
    indications: '胸悶、心悸、噁心、嘔吐、前臂痠痛等。',
    method: '腕掌側橫紋上 2 寸，兩筋之間，以拇指按揉。'
  },
  TE5: {
    indications: '感冒發熱、頭痛、耳疾、上肢痠痛等。',
    method: '腕背橫紋上 2 寸，尺橈骨之間，以拇指按揉。'
  },
  GB20: {
    indications: '頭痛、眩暈、頸項強痛、感冒、目疾等。',
    method: '枕骨下，胸鎖乳突肌與斜方肌之間凹陷，以拇指向鼻尖方向按揉，力道溫和。'
  },
  GB21: {
    indications: '肩頸痠痛、頭痛、頸項不利等。',
    method: '肩部高點、大椎與肩峰連線中點，以指腹按揉，力道勿過猛。孕婦慎用。'
  },
  BL40: {
    indications: '腰痛、下肢痿痹、膝痛等。',
    method: '膕橫紋中點，以拇指適度按揉，避免重壓。'
  },
  KI1: {
    indications: '頭痛、失眠、心煩、咽喉不適等。',
    method: '足底前 1/3 凹陷，捲足時取穴，以拇指按揉。'
  }
});

function extractCurriculumCodes(src) {
  const block = src.slice(src.indexOf('export const CURRICULUM_SETS'), src.indexOf('export const CURRICULUM_BY_ID'));
  const re = /'([A-Z]{1,2}\d+|GV\d+\+|EX-HN\d+)'/g;
  const codes = new Set();
  let m;
  while ((m = re.exec(block))) codes.add(m[1]);
  return [...codes];
}

function extractLocationMap(src) {
  const start = src.indexOf('export const LOCATION');
  const end = src.indexOf('export interface SurfaceTarget');
  const block = src.slice(start, end);
  const loc = {};
  const entryRe = /'([^']+)':\s*\{\s*text:\s*'((?:\\'|[^'])*)'/g;
  let m;
  while ((m = entryRe.exec(block))) {
    loc[m[1]] = m[2].replace(/\\'/g, "'");
  }
  return loc;
}

const allPoints = JSON.parse(readFileSync(pointsPath, 'utf8'));
const byCode = new Map(allPoints.map((p) => [p.code, p]));
const curriculumCodes = extractCurriculumCodes(teachingTs);
const location = extractLocationMap(teachingTs);

const missing = curriculumCodes.filter((c) => !byCode.has(c));
if (missing.length) {
  console.error('缺少 teaching-points 條目：', missing.join(', '));
  process.exit(1);
}

const out = curriculumCodes.map((code) => {
  const p = byCode.get(code);
  const common = COMMON_10[code];
  return {
    code: p.code,
    name: p.name,
    pinyin: p.pinyin,
    meridian: p.meridian,
    position: p.position,
    normal: p.normal,
    midline: !!p.midline,
    location: location[code] || '',
    indications: common ? common.indications : '',
    method: common ? common.method : ''
  };
});

out.sort((a, b) => a.code.localeCompare(b.code));

const dest = join(root, 'server/src/main/resources/public/island/tcm-bronze-points.json');
writeFileSync(dest, JSON.stringify(out, null, 2) + '\n', 'utf8');
console.log('Wrote', dest, '—', out.length, 'codes');