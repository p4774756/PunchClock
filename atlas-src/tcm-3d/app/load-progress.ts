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
  program: '載入程式',
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