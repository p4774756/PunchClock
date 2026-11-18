// 資工人生紀念館展件世界座標（電腦島研發樓前庭步道；不擋門口與橋）。
export const CAREER_EXHIBIT_LAYOUT = Object.freeze([
  { id: 'thu-2007', x: -0.95, z: 16.22, r: 0.48, obstacleR: 0.16 },
  { id: 'hongzhen', x: -1.22, z: 16.38, r: 0.46, obstacleR: 0.16 },
  { id: 'dc-power', x: -1.35, z: 16.52, r: 0.46, obstacleR: 0.16 },
  { id: 'chengfeng', x: -1.25, z: 16.62, r: 0.46, obstacleR: 0.16 },
  { id: 'sugar-frog', x: -0.95, z: 16.68, r: 0.46, obstacleR: 0.16 },
  { id: 'geein', x: -0.62, z: 16.72, r: 0.46, obstacleR: 0.16 },
  { id: 'twilight-exchange', x: -0.28, z: 16.7, r: 0.46, obstacleR: 0.16 },
  { id: 'twilight-ops', x: 0.05, z: 16.65, r: 0.46, obstacleR: 0.16 },
  { id: 'weichiao', x: -0.58, z: 16.66, r: 0.46, obstacleR: 0.16 },
  { id: 'punchclock', x: 1.12, z: 16.12, r: 0.48, obstacleR: 0.16 }
]);

/** 走廊採樣點：驗證可走（半徑 0.29）。 */
export const CAREER_WALK_SAMPLES = Object.freeze([
  { x: -0.44, z: 16.05 },
  { x: -0.28, z: 16.17 },
  { x: -0.2, z: 16.11 },
  { x: 0.35, z: 16.18 },
  { x: 0.48, z: 16.24 },
  { x: 0.55, z: 16.52 },
  { x: 0.62, z: 16.38 }
]);

export const CAREER_PROMPT_LABEL = '查看經歷';

export function careerExhibitObstacleCircles() {
  return CAREER_EXHIBIT_LAYOUT.map((row) =>
    Object.freeze({ id: 'career-' + row.id, x: row.x, z: row.z, r: row.obstacleR })
  );
}