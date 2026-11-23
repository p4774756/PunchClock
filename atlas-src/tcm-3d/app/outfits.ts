import {ANIME_MODEL_URL} from './character-mode';
import {POINT_BY_CODE,type RegionId} from './teaching';

import type {AvatarId} from './avatars';

export type AssistantOutfitId='school'|'study-body';
/** @deprecated Use OutfitId from avatar-outfits.ts */
export type OutfitId=AssistantOutfitId;

/** Chest／abdomen／back／waist. Upper-back 天宗一帶 is shoulder or arm in the catalog, not abdomen. */
const TORSO_CLOTH_PEEK_REGIONS=new Set<RegionId>(['abdomen','waist']);
const TORSO_CLOTH_PEEK_CODES=new Set(['SI9','SI11','SI12','SI13','SI14','SI15','BL11','BL12','BL13','GB30']);

export function isTorsoClothPeekPoint(code:string|null|undefined){
 if(!code)return false;
 if(TORSO_CLOTH_PEEK_CODES.has(code))return true;
 const point=POINT_BY_CODE.get(code);
 return !!point&&TORSO_CLOTH_PEEK_REGIONS.has(point.region);
}

/** VRoid garment material name tokens (surface pass only — outlines stay for the peek halo). */
export const CLOTH_GARMENT_MATERIAL_TOKENS=['Tops','Bottoms','Accessory','CLOTH'] as const;

export interface AnimeOutfit{
 id:AssistantOutfitId|import('./adult-outfits').AdultOutfitId;
 /** When set, this row only applies to the matching avatar. */
 avatarId?:AvatarId;
 name:string;
 shortName:string;
 blurb:string;
 modelUrl:string;
 /** Material name substrings to fully hide (multi-material Body.baked groups). */
 hideMaterials:string[];
 /** Material name substrings to soften so neck points stay readable. */
 softenMaterials:string[];
 /**
  * study-body: hide Tops／Bottoms／Accessory, unmask torso Body_00_SKIN,
  * paint teal sports bra＋shorts into the skin map, and restyle hair into a high bun.
  */
 practiceBody?:boolean;
 /** Adult suit／casual retexture on the base rig (no clone). */
 adultCloth?:'suit'|'casual';
 /** Adult painted swimsuit on skin (practice or sheer inner). */
 adultPaint?:'sport'|'sheer-inner';
 /** Semi-transparent white shirt over opaque inner paint. */
 sheerOuter?:boolean;
 /** Do not restyle hair into practice bun (adult keeps shoulder-length hair). */
 skipHairRestyle?:boolean;
}

export const ANIME_OUTFITS:AnimeOutfit[]=[
 {
  id:'school',
  avatarId:'assistant',
  name:'制服研習裝',
  shortName:'制服',
  blurb:'Sendagaya 原裝制服。頸前或軀幹穴被擋住時可切「練習裝」。',
  modelUrl:ANIME_MODEL_URL,
  hideMaterials:[],
  // Do not soften Tops/CLOTH: VRoid bakes vest＋shirt as one material; transparent
  // depthWrite=false causes armpit／side silhouette artifacts.
  softenMaterials:[],
 },
 {
  id:'study-body',
  avatarId:'assistant',
  name:'軀幹練習裝',
  shortName:'練習裝',
  blurb:'深青運動內衣＋高腰短褲畫在皮膚貼圖上，盤高包頭；露出頸前、胸腹、背腰供針／指壓。',
  modelUrl:ANIME_MODEL_URL,
  hideMaterials:['Tops','Bottoms','Accessory'],
  softenMaterials:[],
  practiceBody:true,
 },
];

export const DEFAULT_OUTFIT_ID:OutfitId='school';
export const OUTFIT_BY_ID=new Map(ANIME_OUTFITS.map(o=>[o.id,o]));

export function animeOutfit(id:OutfitId|string|null|undefined):AnimeOutfit{
 return OUTFIT_BY_ID.get(id as OutfitId)??ANIME_OUTFITS[0];
}
