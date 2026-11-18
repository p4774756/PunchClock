// 探索模式戶外晝夜光量：純函式（島上主場景，不含室內）。

/** 白天端點（維持 i80 前數值，勿改）。 */
export const EXPLORE_LIGHT_DAY = Object.freeze({
  hemiIntensity: 0.62,
  sunIntensity: 1.42,
  toneMappingExposure: 1.02
});

/**
 * 夜間端點：略高於 i80 前（補點光縮減後的環境光）；路燈暖色僅靠地面光暈＋點光。
 * 不增加點光數量與陰影成本。
 */
export const EXPLORE_LIGHT_NIGHT = Object.freeze({
  hemiIntensity: 0.74,
  sunIntensity: 0.65,
  toneMappingExposure: 1.17
});

export function exploreLightAtMix(mix) {
  const t = Math.max(0, Math.min(1, Number(mix) || 0));
  const d = EXPLORE_LIGHT_DAY;
  const n = EXPLORE_LIGHT_NIGHT;
  return {
    hemiIntensity: d.hemiIntensity + (n.hemiIntensity - d.hemiIntensity) * t,
    sunIntensity: d.sunIntensity + (n.sunIntensity - d.sunIntensity) * t,
    toneMappingExposure: d.toneMappingExposure + (n.toneMappingExposure - d.toneMappingExposure) * t
  };
}
