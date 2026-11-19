// 資工人生紀念館展件世界座標（電腦島研發樓前庭 U 形步道；不擋門口與橋）。
// 左臂：求學引言＋韌體時期；右臂：純軟體時期＋ PunchClock 收尾；中央 x≈0.45 留進門線。
export const CAREER_EXHIBIT_LAYOUT = Object.freeze([
  { id: 'thu-2007', x: -2.15, z: 15.22, r: 0.4, obstacleR: 0.14 },
  { id: 'firmware-2013-2022', x: -1.95, z: 16.38, r: 0.44, obstacleR: 0.15 },
  { id: 'software-2022-now', x: 1.88, z: 16.42, r: 0.44, obstacleR: 0.15 },
  { id: 'punchclock', x: 1.38, z: 15.18, r: 0.42, obstacleR: 0.14 }
]);

export const CAREER_ZONE_SIGNS = Object.freeze([
  { phase: '韌體時期', years: '2013–2022', x: -1.78, z: 16.62, yaw: Math.PI },
  { phase: '純軟體時期', years: '2022–至今', x: 1.84, z: 16.27, yaw: Math.PI }
]);

/** 走廊採樣點：驗證可走（半徑 0.29）。 */
export const CAREER_WALK_SAMPLES = Object.freeze([
  { x: -1.55, z: 15.4 },
  { x: -1.45, z: 15.95 },
  { x: -1.35, z: 16.55 },
  { x: 0.45, z: 15.35 },
  { x: 0.45, z: 15.75 },
  { x: 0.45, z: 16.15 },
  { x: 0.45, z: 16.55 },
  { x: 0.45, z: 16.82 },
  { x: 1.25, z: 16.35 },
  { x: 1.75, z: 15.95 }
]);

export const CAREER_PROMPT_LABEL = '查看經歷';

export function careerExhibitObstacleCircles() {
  return CAREER_EXHIBIT_LAYOUT.map((row) =>
    Object.freeze({ id: 'career-' + row.id, x: row.x, z: row.z, r: row.obstacleR })
  );
}