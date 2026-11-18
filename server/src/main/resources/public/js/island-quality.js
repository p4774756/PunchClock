// 探索模式自適應畫質：純邏輯，供 island-quality.test.mjs 與 island.js 使用。

export const QUALITY_SLOW_MS = 22;
export const QUALITY_FAST_MS = 17;
export const DEFAULT_MAX_PIXEL_RATIO = 1.5;
export const DEFAULT_SHADOW_MAP = 1024;

export const QUALITY_TIER_LABELS = Object.freeze({
  high: '高',
  dpr1: '標準',
  shadowLow: '精簡陰影',
  shadowOff: '低'
});

const TIERS = ['high', 'dpr1', 'shadowLow', 'shadowOff'];

export function qualityTierSettings(tier, devicePixelRatio) {
  const dpr = Math.max(1, Number(devicePixelRatio) || 1);
  switch (tier) {
    case 'dpr1':
      return { pixelRatio: 1, shadowEnabled: true, shadowMapSize: DEFAULT_SHADOW_MAP };
    case 'shadowLow':
      return { pixelRatio: 1, shadowEnabled: true, shadowMapSize: 512 };
    case 'shadowOff':
      return { pixelRatio: 1, shadowEnabled: false, shadowMapSize: 512 };
    case 'high':
    default:
      return {
        pixelRatio: Math.min(dpr, DEFAULT_MAX_PIXEL_RATIO),
        shadowEnabled: true,
        shadowMapSize: DEFAULT_SHADOW_MAP
      };
  }
}

export function createQualityAdvisor(opts) {
  const slowMs = opts && opts.slowMs != null ? opts.slowMs : QUALITY_SLOW_MS;
  const fastMs = opts && opts.fastMs != null ? opts.fastMs : QUALITY_FAST_MS;
  const slowNeed = opts && opts.slowNeed != null ? opts.slowNeed : 12;
  const fastNeed = opts && opts.fastNeed != null ? opts.fastNeed : 45;
  const getDevicePixelRatio =
    opts && typeof opts.getDevicePixelRatio === 'function'
      ? opts.getDevicePixelRatio
      : () => 1;

  let tier = 'high';
  let slowStreak = 0;
  let fastStreak = 0;
  let fps = 60;

  function settings() {
    return qualityTierSettings(tier, getDevicePixelRatio());
  }

  function observeFrame(frameMs) {
    const ms = Math.max(0, Number(frameMs) || 0);
    if (ms > 1e-6) fps = 1000 / ms;

    if (ms >= slowMs) {
      slowStreak += 1;
      fastStreak = 0;
    } else if (ms <= fastMs) {
      fastStreak += 1;
      slowStreak = 0;
    } else {
      slowStreak = Math.max(0, slowStreak - 1);
      fastStreak = Math.max(0, fastStreak - 1);
    }

    const before = tier;
    const idx = TIERS.indexOf(tier);
    let tierDirection = null;
    if (slowStreak >= slowNeed && idx < TIERS.length - 1) {
      tier = TIERS[idx + 1];
      tierDirection = 'down';
      slowStreak = 0;
      fastStreak = 0;
    } else if (fastStreak >= fastNeed && idx > 0) {
      tier = TIERS[idx - 1];
      tierDirection = 'up';
      slowStreak = 0;
      fastStreak = 0;
    }

    return {
      changed: tier !== before,
      tier: tier,
      tierDirection: tierDirection,
      label: QUALITY_TIER_LABELS[tier] || tier,
      fps: fps,
      settings: settings()
    };
  }

  function reset() {
    tier = 'high';
    slowStreak = 0;
    fastStreak = 0;
    fps = 60;
    return settings();
  }

  return {
    get tier() { return tier; },
    get fps() { return fps; },
    label: () => QUALITY_TIER_LABELS[tier] || tier,
    settings: settings,
    observeFrame: observeFrame,
    reset: reset
  };
}

/** ?perf=1：近 windowMs 內幀率滾動統計。 */
export function createFpsRollingStats(windowMs) {
  const span = Math.max(1000, Number(windowMs) || 10000);
  const samples = [];

  function push(nowMs, fps) {
    const t = Number(nowMs) || 0;
    const f = Math.max(0, Number(fps) || 0);
    samples.push({ t: t, fps: f });
    const cutoff = t - span;
    while (samples.length && samples[0].t < cutoff) samples.shift();
  }

  function snapshot() {
    if (!samples.length) return { avg: 0, min: 0, count: 0 };
    let sum = 0;
    let min = Infinity;
    for (let i = 0; i < samples.length; i++) {
      sum += samples[i].fps;
      if (samples[i].fps < min) min = samples[i].fps;
    }
    return { avg: sum / samples.length, min: min, count: samples.length };
  }

  return { push: push, snapshot: snapshot };
}

/** ?perf=1 左上角面板文字（繁中）。 */
export function formatExplorePerfOverlay(sample, rolling, tierChanges) {
  const lines = [
    '幀率約 ' + Math.round(sample.fps) + ' · 畫質：' + sample.label
  ];
  const r = rolling && rolling.count > 0 ? rolling : null;
  if (r) {
    lines.push(
      '近 10 秒：平均 ' + Math.round(r.avg) + ' · 最低 ' + Math.round(r.min)
    );
  }
  const down = tierChanges && tierChanges.down ? tierChanges.down : 0;
  const up = tierChanges && tierChanges.up ? tierChanges.up : 0;
  lines.push('降階：' + down + ' 次 · 升階：' + up + ' 次');
  return lines.join('\n');
}