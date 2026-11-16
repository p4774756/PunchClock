import {ANIME_MODEL_URL} from './character-mode';

export type OutfitId='school'|'study-neck'|'study-body';

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
  * restore collar-V Body_00_SKIN alpha so 人迎／天突 can hit lit MToon skin.
  */
 openCollar?:boolean;
 /**
  * study-body: hide Tops／Bottoms／Accessory, unmask torso Body_00_SKIN,
  * add teal sports bra＋shorts, and restyle hair into a high bun.
  */
 practiceBody?:boolean;
}

export const ANIME_OUTFITS:AnimeOutfit[]=[
 {
  id:'school',
  name:'制服研習裝',
  shortName:'制服',
  blurb:'Sendagaya 原裝制服。頸前或軀幹穴被擋住時可切「露頸」或「練習裝」。',
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
  blurb:'拿掉領結，上衣仍蓋住胸腔挖空，並還原衣領 V 區皮膚，方便對照人迎、天突。',
  modelUrl:ANIME_MODEL_URL,
  hideMaterials:['Accessory'],
  softenMaterials:[],
  openCollar:true,
 },
 {
  id:'study-body',
  name:'軀幹練習裝',
  shortName:'練習裝',
  blurb:'深青運動內衣＋高腰短褲，盤高包頭；露出頸前、胸腹、背腰的真實皮膚供針／指壓。',
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
