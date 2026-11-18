// 研發樓製茶機數據牆與開發時間軸螢幕行（純函式，可測）。

import { formatLabDevScreenLines, formatTaipeiTime } from './island-roadmap.js';

const SAMPLE_LABEL = '取樣中';
const TEA_FAIL = '製茶機狀態暫時讀不到';
const TIMELINE_EMPTY = '近 7 天沒有動態';

export function formatTeaBytes(bytes) {
  if (bytes == null) return SAMPLE_LABEL;
  const n = Number(bytes);
  if (!Number.isFinite(n) || n < 0) return SAMPLE_LABEL;
  if (n < 1024) return Math.round(n) + ' B';
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
  if (n < 1024 * 1024 * 1024) return (n / (1024 * 1024)).toFixed(1) + ' MB';
  return (n / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
}

export function formatTeaPercent(value) {
  if (value == null) return SAMPLE_LABEL;
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) return SAMPLE_LABEL;
  return n.toFixed(1) + '%';
}

export function teaBarFraction(used, total) {
  if (used == null || total == null) return null;
  const u = Number(used);
  const t = Number(total);
  if (!Number.isFinite(u) || !Number.isFinite(t) || t <= 0 || u < 0) return null;
  return Math.min(1, Math.max(0, u / t));
}

export function formatTeaCpuLine(data) {
  if (!data || data.success === false) return TEA_FAIL;
  const proc = data.processCpuPercent;
  const sys = data.systemCpuPercent;
  const procText = formatTeaPercent(proc);
  if (procText !== SAMPLE_LABEL) {
    const sysText = formatTeaPercent(sys);
    if (sysText !== SAMPLE_LABEL) {
      return '製茶機 CPU：' + procText + '（系統 ' + sysText + '）';
    }
    return '製茶機 CPU：' + procText;
  }
  if (sys != null && Number.isFinite(Number(sys)) && Number(sys) >= 0) {
    return '製茶機 CPU：' + formatTeaPercent(sys) + ' 系統';
  }
  return '製茶機 CPU：' + SAMPLE_LABEL;
}

export function formatTeaMemoryLine(data) {
  if (!data || data.success === false) return TEA_FAIL;
  const total = data.osTotalMemoryBytes;
  const used = data.osUsedMemoryBytes;
  if (total == null || used == null) {
    const heapUsed = data.heapUsedBytes;
    const heapMax = data.heapMaxBytes;
    if (heapUsed != null && heapMax != null) {
      return '記憶體（heap）：' + formatTeaBytes(heapUsed) + ' / ' + formatTeaBytes(heapMax);
    }
    return '記憶體：' + SAMPLE_LABEL;
  }
  return '記憶體：' + formatTeaBytes(used) + ' / ' + formatTeaBytes(total);
}

/** 供 Canvas 繪製用的結構（無 NaN）。 */
export function teaHealthViewModel(data) {
  if (!data || data.success === false) {
    return {
      ok: false,
      title: '製茶機',
      error: TEA_FAIL,
      cpuLabel: TEA_FAIL,
      memLabel: '',
      cpuBar: null,
      memBar: null,
      loadLabel: ''
    };
  }
  const cpuBar = teaBarFraction(data.processCpuPercent, 100);
  const memBar = teaBarFraction(data.osUsedMemoryBytes, data.osTotalMemoryBytes);
  const loadAvg = Number(data.loadAverage);
  const cpus = Number(data.cpuCount) || 1;
  let loadLabel = '';
  if (Number.isFinite(loadAvg) && loadAvg >= 0) {
    loadLabel = '負載 ' + loadAvg.toFixed(2) + ' / ' + cpus + ' 核';
  } else {
    loadLabel = cpus + ' 核';
  }
  return {
    ok: true,
    title: '製茶機',
    error: '',
    cpuLabel: formatTeaCpuLine(data),
    memLabel: formatTeaMemoryLine(data),
    cpuBar: cpuBar,
    memBar: memBar,
    loadLabel: loadLabel
  };
}

function clipTimelineTitle(text, maxLen) {
  const raw = text == null ? '' : String(text);
  if (raw.length <= maxLen) return raw;
  if (maxLen < 4) return raw.slice(0, maxLen);
  return raw.slice(0, maxLen - 1) + '…';
}

function sortedTimeline(events) {
  const list = Array.isArray(events) ? events.slice() : [];
  list.sort((a, b) => {
    const ta = Number(a && a.at) || 0;
    const tb = Number(b && b.at) || 0;
    return tb - ta;
  });
  return list;
}

/** 時間軸 → 研發樓螢幕文字行。 */
export function formatLabTimelineScreenLines(timeline, opts) {
  const maxEvents = (opts && opts.maxEvents) || 8;
  const titleMax = (opts && opts.titleMax) || 22;
  const sorted = sortedTimeline(timeline);
  if (!sorted.length) {
    return [TIMELINE_EMPTY];
  }
  const slice = sorted.slice(0, maxEvents);
  const lines = [];
  for (let i = 0; i < slice.length; i++) {
    const ev = slice[i] || {};
    const when = formatTaipeiTime(ev.at);
    const action = ev.action || '';
    const ref = ev.ref || '';
    const title = clipTimelineTitle(ev.title, titleMax);
    const head = [when, action, ref].filter(Boolean).join(' ');
    lines.push(head + (title ? ' ' + title : ''));
  }
  return lines;
}

export function formatLabDevScreenWithTimeline(data, opts) {
  const base = formatLabDevScreenLines(data, opts);
  if (!data || data.success === false) {
    return base;
  }
  const timeline = data.timeline;
  const events = Array.isArray(timeline) ? timeline : [];
  const out = base.slice();
  out.push('');
  out.push('【最近動態】');
  const tlOpts = {
    maxEvents: (opts && opts.maxTimeline) || 6,
    titleMax: (opts && opts.timelineTitleMax) || 18
  };
  out.push.apply(out, formatLabTimelineScreenLines(events, tlOpts));
  return out;
}

export function drawTeaHealthCanvas(ctx, data, width, height) {
  if (!ctx) return;
  const w = width || 512;
  const h = height || 384;
  const vm = teaHealthViewModel(data);
  ctx.fillStyle = '#07111f';
  ctx.fillRect(0, 0, w, h);
  const pad = 28;
  const barW = w - pad * 2;
  const barH = 22;
  let y = 36;
  ctx.fillStyle = '#9ae8ff';
  ctx.font = '700 34px "PingFang TC", "Noto Sans TC", sans-serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'top';
  ctx.fillText(vm.title, pad, y);
  y += 52;
  ctx.font = '600 24px "PingFang TC", "Noto Sans TC", sans-serif';
  if (!vm.ok) {
    ctx.fillStyle = '#b8d8f0';
    ctx.fillText(vm.error, pad, y);
    return;
  }
  ctx.fillStyle = '#c8f0ff';
  ctx.fillText(vm.cpuLabel, pad, y);
  y += 34;
  drawHealthBar(ctx, pad, y, barW, barH, vm.cpuBar, 0x3cf0ff);
  y += barH + 28;
  ctx.fillText(vm.memLabel, pad, y);
  y += 34;
  drawHealthBar(ctx, pad, y, barW, barH, vm.memBar, 0x5ad8c8);
  y += barH + 24;
  if (vm.loadLabel) {
    ctx.font = '500 20px "PingFang TC", "Noto Sans TC", sans-serif';
    ctx.fillStyle = '#88b8d8';
    ctx.fillText(vm.loadLabel, pad, y);
  }
}

function drawHealthBar(ctx, x, y, w, h, fraction, colorHex) {
  const track = '#1a2838';
  ctx.fillStyle = track;
  ctx.fillRect(x, y, w, h);
  if (fraction == null || !Number.isFinite(fraction)) return;
  const fillW = Math.max(2, Math.round(w * fraction));
  const r = (colorHex >> 16) & 0xff;
  const g = (colorHex >> 8) & 0xff;
  const b = colorHex & 0xff;
  ctx.fillStyle = 'rgb(' + r + ',' + g + ',' + b + ')';
  ctx.fillRect(x, y, fillW, h);
}