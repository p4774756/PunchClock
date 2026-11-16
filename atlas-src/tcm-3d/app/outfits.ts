import {ANIME_MODEL_URL} from './character-mode';

export type OutfitId='school';

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
}

/** School uniform only; 露頸練習裝（study-neck）deferred to issue #75. */
export const ANIME_OUTFITS:AnimeOutfit[]=[
 {
  id:'school',
  name:'制服研習裝',
  shortName:'制服',
  blurb:'Sendagaya 原裝制服。',
  modelUrl:ANIME_MODEL_URL,
  hideMaterials:[],
  // Do not soften Tops/CLOTH: VRoid bakes vest＋shirt as one material; transparent
  // depthWrite=false causes armpit／side silhouette artifacts.
  softenMaterials:[],
 },
];

export const DEFAULT_OUTFIT_ID:OutfitId='school';
export const OUTFIT_BY_ID=new Map(ANIME_OUTFITS.map(o=>[o.id,o]));

export function animeOutfit(id:OutfitId|string|null|undefined):AnimeOutfit{
 return OUTFIT_BY_ID.get(id as OutfitId)??ANIME_OUTFITS[0];
}
