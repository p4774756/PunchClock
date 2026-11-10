import {ANIME_MODEL_URL,ANIME_POINTS,type AnimePoint} from './character-mode';
import type {SurfaceTarget} from './teaching';
import {POINT_BY_CODE} from './teaching';

export type AvatarId='assistant';

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
 /** Original Sendagaya wardrobe (制服／露頸). */
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
  blurb:'Sendagaya Shino 研習室助教；可換制服／露頸。',
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

export function avatarLessonNote(avatar:AnimeAvatar,code:string|null|undefined){
 if(avatar.modelStatus==='pending')return `${avatar.name} 的 3D 模型尚未加入，請把 .glb 放到 ${avatar.expectedModelPath}。`;
 if(!code)return `${avatar.name} 目前提供已對應穴位練習。`;
 if(avatarSupportsPoint(avatar,code))return `此穴已在${avatar.name}完成對應，可點選標記練習。`;
 const name=POINT_BY_CODE.get(code)?.name??code;
 return `${name} 尚未對應到${avatar.name}，請切換解剖模型查看。`;
}
