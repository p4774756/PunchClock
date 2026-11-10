import animePoints from './anime-points.json';
import type {CurriculumSetId, SurfaceTarget, Vec3} from './teaching';
import {POINT_BY_CODE} from './teaching';

export type ModelMode='anatomy'|'anime';

export interface AnimePoint{
 code:string;
 name:string;
 midline:boolean;
 position:Vec3;
 normal:Vec3;
 reviewed:boolean;
 set:string;
}

export const ANIME_MODEL_URL='/models/anime-assistant.glb';
export const ANIME_POINTS=(animePoints.points as AnimePoint[]);
export const ANIME_POINT_BY_CODE=new Map(ANIME_POINTS.map(p=>[p.code,p]));
export const ANIME_SUPPORTED_CODES=new Set(ANIME_POINTS.map(p=>p.code));
export const ANIME_DEFAULT_SET:CurriculumSetId='head-neck-21';

/** Anatomical left is stored as +X; anime face looks toward -Z so left becomes -X. */
export function animePointTarget(code:string,side:1|-1):SurfaceTarget|null{
 const p=ANIME_POINT_BY_CODE.get(code);
 if(!p)return null;
 return {
  position:[p.midline?0:-p.position[0]*side,p.position[1],p.position[2]],
  normal:[p.midline?p.normal[0]:-p.normal[0]*side,p.normal[1],p.normal[2]],
 };
}

export function animeSupportsPoint(code:string|null|undefined){
 return !!code&&ANIME_SUPPORTED_CODES.has(code);
}

export function animeLessonNote(code:string|null|undefined){
 if(!code)return '動漫角色目前提供頭頸基準穴練習。';
 if(ANIME_SUPPORTED_CODES.has(code))return '此穴已在動漫角色完成對應，可點選標記練習。';
 const name=POINT_BY_CODE.get(code)?.name??code;
 return `${name} 尚未對應到動漫角色，請切回解剖模型查看。`;
}
