import {ANIME_MODEL_URL} from './character-mode';

export type OutfitId='school'|'study-neck';

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
  * study-neck: keep Tops covering the MASK hollow chest, hide only Accessory,
  * and restore collar-V Body_00_SKIN alpha so 人迎／天突 hit lit MToon skin.
  */
 openCollar?:boolean;
}

export const ANIME_OUTFITS:AnimeOutfit[]=[
 {
  id:'school',
  name:'制服研習裝',
  shortName:'制服',
  blurb:'Sendagaya 原裝制服。頸前穴被衣領遮擋時可切「露頸」。',
  modelUrl:ANIME_MODEL_URL,
  hideMaterials:[],
  // Do not soften Tops/CLOTH: VRoid bakes vest＋shirt as one material; transparent
  // depthWrite=false causes armpit／side silhouette artifacts.
  softenMaterials:[],
 },
 {
  id:'study-neck',
  name:'露頸練習裝',
  shortName:'露頸',
  blurb:'拿掉領結，上衣仍蓋住胸腔挖空，並讓衣領 V 的皮膚可見，方便人迎、天突對照。',
  modelUrl:ANIME_MODEL_URL,
  hideMaterials:['Accessory'],
  softenMaterials:[],
  openCollar:true,
 },
];

export const DEFAULT_OUTFIT_ID:OutfitId='school';
export const OUTFIT_BY_ID=new Map(ANIME_OUTFITS.map(o=>[o.id,o]));

export function animeOutfit(id:OutfitId|string|null|undefined):AnimeOutfit{
 return OUTFIT_BY_ID.get(id as OutfitId)??ANIME_OUTFITS[0];
}
