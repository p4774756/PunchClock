import {ANIME_MODEL_URL} from './character-mode';

export type OutfitId='school'|'study-body';

export interface AnimeOutfit{
 id:OutfitId;
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
}

export const ANIME_OUTFITS:AnimeOutfit[]=[
 {
  id:'school',
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
