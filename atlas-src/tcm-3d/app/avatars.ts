import {ANIME_ADULT_MODEL_DIGEST} from './anime-adult-model-digest';
import {ANIME_MODEL_URL,ANIME_POINTS,type AnimePoint} from './character-mode';
import type {SurfaceTarget} from './teaching';
import {POINT_BY_CODE,isPracticeDeferred} from './teaching';

export type AvatarId='assistant'|'adult';

const ANIME_ADULT_MODEL_URL=`/models/anime-adult.glb?v=${ANIME_ADULT_MODEL_DIGEST}`;

export interface AnimeAvatar{
 id:AvatarId;
 name:string;
 shortName:string;
 /** null = model not in repo yet */
 modelUrl:string|null;
 modelStatus:'ready'|'pending';
 /** Expected drop path when the GLB arrives. */
 expectedModelPath:string;
 points:AnimePoint[];
 /** Wardrobe switch UI; false while only school uniform is shipped. */
 hasOutfits:boolean;
 blurb:string;
}

export const ANIME_AVATARS:AnimeAvatar[]=[
 {
  id:'assistant',
  name:'原本角色',
  shortName:'原本角色',
  modelUrl:ANIME_MODEL_URL,
  modelStatus:'ready',
  expectedModelPath:'atlas-src/tcm-3d/public/models/anime-assistant.glb',
  points:ANIME_POINTS,
  hasOutfits:true,
  blurb:'Sendagaya Shino 研習室助教（制服／練習裝）。',
 },
 {
  id:'adult',
  name:'成年角色',
  shortName:'成年角色',
  modelUrl:ANIME_ADULT_MODEL_URL,
  modelStatus:'ready',
  expectedModelPath:'atlas-src/tcm-3d/public/models/anime-adult.glb',
  points:[],
  hasOutfits:true,
  blurb:'VRoid 官方樣本（黑髮側分瀏海）；套裝／休閒／練習裝／薄紗可切換。',
 },
];

export const DEFAULT_AVATAR_ID:AvatarId='assistant';
export const AVATAR_BY_ID=new Map(ANIME_AVATARS.map(a=>[a.id,a]));

export function animeAvatar(id:AvatarId|string|null|undefined):AnimeAvatar{
 return AVATAR_BY_ID.get(id as AvatarId)??ANIME_AVATARS[0];
}

export function avatarSupportedCodes(avatar:AnimeAvatar){
 return new Set(avatar.points.map(p=>p.code));
}

export function avatarPointByCode(avatar:AnimeAvatar){
 return new Map(avatar.points.map(p=>[p.code,p]));
}

/** Anatomical left is +X in JSON; anime face −Z flips left to −X at runtime. */
export function avatarPointTarget(avatar:AnimeAvatar,code:string,side:1|-1):SurfaceTarget|null{
 const p=avatarPointByCode(avatar).get(code);
 if(!p)return null;
 return {
  position:[p.midline?0:-p.position[0]*side,p.position[1],p.position[2]],
  normal:[p.midline?p.normal[0]:-p.normal[0]*side,p.normal[1],p.normal[2]],
 };
}

export function avatarSupportsPoint(avatar:AnimeAvatar,code:string|null|undefined){
 return !!code&&avatarSupportedCodes(avatar).has(code);
}

/** Baked and open for needle／finger demos. */
export function avatarPracticeOpen(avatar:AnimeAvatar,code:string|null|undefined){
 return avatarSupportsPoint(avatar,code)&&!isPracticeDeferred(code);
}

export function avatarPracticePoints(avatar:AnimeAvatar){
 return avatar.points.filter(p=>!isPracticeDeferred(p.code));
}

export function avatarLessonNote(avatar:AnimeAvatar,code:string|null|undefined){
 if(avatar.modelStatus==='pending')return `${avatar.name} 的 3D 模型尚未加入，請把 .glb 放到 ${avatar.expectedModelPath}。`;
 if(!code){
  if(avatar.points.length===0)return `${avatar.name} 的穴位尚未對位，之後依課堂進度加入。`;
  return `${avatar.name} 目前提供已對應穴位練習。`;
 }
 if(avatarSupportsPoint(avatar,code))return `此穴已在${avatar.name}完成對應，可點選標記練習。`;
 const name=POINT_BY_CODE.get(code)?.name??code;
 return `${name} 尚未烘到${avatar.name}身上，之後依課堂進度加入。`;
}
