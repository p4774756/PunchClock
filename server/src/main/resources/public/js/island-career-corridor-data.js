// 資工人生紀念館展件世界座標（電腦島研發樓前庭 U 形步道；不擋門口與橋）。
export const CAREER_EXHIBIT_LAYOUT = Object.freeze([
  { id: 'thu-2007', x: -2.45, z: 15.15, r: 0.4, obstacleR: 0.14 },
  { id: 'hongzhen', x: -2.35, z: 16.05, r: 0.4, obstacleR: 0.14 },
  { id: 'dc-power', x: -2.0, z: 17.15, r: 0.4, obstacleR: 0.14 },
  { id: 'chengfeng', x: -1.35, z: 15.35, r: 0.4, obstacleR: 0.14 },
  { id: 'sugar-frog', x: -1.25, z: 16.2, r: 0.4, obstacleR: 0.14 },
  { id: 'geein', x: -0.75, z: 17.0, r: 0.4, obstacleR: 0.14 },
  { id: 'twilight-exchange', x: 1.62, z: 16.9, r: 0.4, obstacleR: 0.14 },
  { id: 'twilight-ops', x: 2.25, z: 16.7, r: 0.4, obstacleR: 0.14 },
  { id: 'weichiao', x: 2.15, z: 15.45, r: 0.4, obstacleR: 0.14 },
  { id: 'punchclock', x: 1.35, z: 15.15, r: 0.42, obstacleR: 0.14 }
]);

/** 走廊採樣點：驗證可走（半徑 0.29）。 */
export const CAREER_WALK_SAMPLES = Object.freeze([
  { x: -1.85, z: 15.45 },
  { x: -1.65, z: 16.0 },
  { x: -1.55, z: 16.75 },
  { x: 0.45, z: 15.35 },
  { x: 0.45, z: 15.75 },
  { x: 0.45, z: 16.15 },
  { x: 0.45, z: 16.55 },
  { x: 0.45, z: 16.82 },
  { x: 1.5, z: 16.4 },
  { x: 2.0, z: 15.95 }
]);

export const CAREER_PROMPT_LABEL = '查看經歷';

export function careerExhibitObstacleCircles() {
  return CAREER_EXHIBIT_LAYOUT.map((row) =>
    Object.freeze({ id: 'career-' + row.id, x: row.x, z: row.z, r: row.obstacleR })
  );
}