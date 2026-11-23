import {ANIME_ADULT_MODEL_DIGEST} from './anime-adult-model-digest';
import type {AnimeOutfit} from './outfits';

const ADULT_MODEL_URL=`/models/anime-adult.glb?v=${ANIME_ADULT_MODEL_DIGEST}`;

export type AdultOutfitId='suit'|'casual'|'practice'|'sheer';

export const ADULT_OUTFITS:AnimeOutfit[]=[
 {
  id:'suit',
  avatarId:'adult',
  name:'淺灰套裝',
  shortName:'套裝',
  blurb:'淺灰西裝外套、白襯衫、過膝窄裙、低跟鞋。',
  modelUrl:ADULT_MODEL_URL,
  hideMaterials:[],
  softenMaterials:[],
  adultCloth:'suit',
 },
 {
  id:'casual',
  avatarId:'adult',
  name:'休閒穿搭',
  shortName:'休閒',
  blurb:'白色短版針織上衣、淺藍迷你裙、白球鞋。',
  modelUrl:ADULT_MODEL_URL,
  hideMaterials:[],
  softenMaterials:[],
  adultCloth:'casual',
 },
 {
  id:'practice',
  avatarId:'adult',
  name:'運動練習裝',
  shortName:'練習裝',
  blurb:'藍綠兩件式運動泳裝，赤腳。',
  modelUrl:ADULT_MODEL_URL,
  hideMaterials:['Tops','Bottoms','Shoes'],
  softenMaterials:[],
  practiceBody:true,
  adultPaint:'sport',
  skipHairRestyle:true,
 },
 {
  id:'sheer',
  avatarId:'adult',
  name:'薄紗泳裝',
  shortName:'薄紗',
  blurb:'白色半透襯衫外罩與不透明棉布泳裝內層。',
  modelUrl:ADULT_MODEL_URL,
  hideMaterials:['Bottoms','Shoes'],
  softenMaterials:[],
  practiceBody:true,
  adultPaint:'sheer-inner',
  sheerOuter:true,
  skipHairRestyle:true,
 },
];

export const DEFAULT_ADULT_OUTFIT_ID:AdultOutfitId='suit';
export const ADULT_OUTFIT_BY_ID=new Map(ADULT_OUTFITS.map(o=>[o.id,o]));