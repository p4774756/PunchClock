export type AtlasLoadStep =
  | 'program'
  | 'download'
  | 'parse'
  | 'anchor'
  | 'materials'
  | 'done';

export type AtlasLoadProgress = {
  percent: number;
  step: AtlasLoadStep;
  stepLabel: string;
};

export const LOAD_STEP_LABELS: Record<AtlasLoadStep, string> = {
  program: '建立畫面',
  download: '下載模型',
  parse: '解析模型',
  anchor: '對應穴位',
  materials: '準備材質',
  done: '完成',
};

export const yieldToMain = (): Promise<void> =>
  new Promise(resolve => {
    requestAnimationFrame(() => resolve());
  });

/** rAF + macrotask so layout/paint and input can run between heavy GL slices. */
export const yieldSlice = (): Promise<void> =>
  new Promise(resolve => {
    requestAnimationFrame(() => setTimeout(() => resolve(), 0));
  });

/** Single macrotask yield — keeps shader link off the critical path between programs. */
export const yieldOnce = (): Promise<void> =>
  new Promise(resolve => {
    if (typeof MessageChannel !== 'undefined') {
      const ch = new MessageChannel();
      ch.port1.onmessage = () => resolve();
      ch.port2.postMessage(null);
    } else {
      setTimeout(() => resolve(), 0);
    }
  });

export const DOWNLOAD_PERCENT_MIN = 12;
export const DOWNLOAD_PERCENT_MAX = 78;
export const PARSE_PERCENT_START = 80;

export type DownloadProgressState = {
  highWaterPct: number;
  parseEntered: boolean;
};

export const createDownloadProgressState = (): DownloadProgressState => ({
  highWaterPct: 0,
  parseEntered: false,
});

export type DownloadProgressResult = {
  state: DownloadProgressState;
  step: 'download' | 'parse' | null;
  percent: number | null;
};

/** Pure download bar mapping for Chrome gzip totals and Safari inflated loaded. */
export function nextModelDownloadProgress(
  loaded: number,
  contentLength: number,
  gzipBytes: number,
  rawBytes: number,
  state: DownloadProgressState,
): DownloadProgressResult {
  if (state.parseEntered) return {state, step: null, percent: null};

  const hasTotal = contentLength > 0;
  if (hasTotal && loaded >= contentLength) {
    const next: DownloadProgressState = {
      parseEntered: true,
      highWaterPct: Math.max(state.highWaterPct, PARSE_PERCENT_START),
    };
    return {state: next, step: 'parse', percent: PARSE_PERCENT_START};
  }
  if (!hasTotal && loaded >= rawBytes) {
    const next: DownloadProgressState = {
      parseEntered: true,
      highWaterPct: Math.max(state.highWaterPct, PARSE_PERCENT_START),
    };
    return {state: next, step: 'parse', percent: PARSE_PERCENT_START};
  }

  const denom = hasTotal ? contentLength : gzipBytes > 0 ? gzipBytes : rawBytes;
  if (denom <= 0) return {state, step: null, percent: null};

  const ratio = Math.min(1, loaded / denom);
  const pct = Math.min(
    DOWNLOAD_PERCENT_MAX,
    DOWNLOAD_PERCENT_MIN + Math.round(ratio * (DOWNLOAD_PERCENT_MAX - DOWNLOAD_PERCENT_MIN)),
  );
  if (pct <= state.highWaterPct) return {state, step: null, percent: null};

  const next: DownloadProgressState = {...state, highWaterPct: pct};
  return {state: next, step: 'download', percent: pct};
}

/** Time-based creep so a step never looks frozen beyond ~700 ms. */
export function creepLoadingPercent(current: number, floor: number, ceiling: number, delta: number): number {
  const base = Math.max(current, floor);
  return Math.min(ceiling, base + delta);
}