import raw from './quest-points.json';
import anime from '../anime-points.json';
import {POINT_BY_CODE} from '../teaching';
import {ANIME_SUPPORTED_CODES} from '../character-mode';
import type {MeridianQuestId,QuestPointRecord} from './types';
import {QUEST_STAGE_ORDER} from './quest-stages';

const ANIME_META=new Map(anime.points.map(p=>[p.code,p]));

export const QUEST_POINTS=raw as QuestPointRecord[];
export const QUEST_BY_CODE=new Map(QUEST_POINTS.map(p=>[p.code,p]));

export function questPointsForMeridian(id:MeridianQuestId):QuestPointRecord[]{
 return QUEST_POINTS.filter(p=>p.meridianId===id);
}

export function questBankByMeridian():Map<MeridianQuestId,QuestPointRecord[]>{
 const map=new Map<MeridianQuestId,QuestPointRecord[]>();
 for(const id of QUEST_STAGE_ORDER)map.set(id,[]);
 for(const p of QUEST_POINTS){
  const list=map.get(p.meridianId as MeridianQuestId);
  if(list)list.push(p);
 }
 for(const [,list] of map)list.sort((a,b)=>a.order-b.order);
 return map;
}

export function questPointName(code:string):string{
 return QUEST_BY_CODE.get(code)?.name??POINT_BY_CODE.get(code)?.name??code;
}

export function questAvatarLocateCodes(meridianId:MeridianQuestId):string[]{
 return questPointsForMeridian(meridianId)
  .map(p=>p.code)
  .filter(code=>ANIME_SUPPORTED_CODES.has(code));
}

export function isIndicationEligible(point:QuestPointRecord):boolean{
 return !point.uncertain&&point.indications.length>=2;
}

export function questAvatarTrunkLocate(code:string):boolean{
 return ANIME_META.get(code)?.set==='trunk-30';
}

/** Pick a bake proxy in the densest body region for locate framing (never the answer). */
export function questLocateFramingCode(meridianId:MeridianQuestId,exclude?:string):string|null{
 const codes=questAvatarLocateCodes(meridianId).filter(c=>c!==exclude);
 if(!codes.length)return null;
 const byRegion=new Map<string,string[]>();
 for(const code of codes){
  const region=POINT_BY_CODE.get(code)?.region??'abdomen';
  const list=byRegion.get(region)??[];
  list.push(code);
  byRegion.set(region,list);
 }
 let pool:string[]=[];
 for(const list of byRegion.values())if(list.length>pool.length)pool=list;
 pool.sort((a,b)=>(POINT_BY_CODE.get(a)?.position[1]??0)-(POINT_BY_CODE.get(b)?.position[1]??0));
 return pool[Math.floor(pool.length/2)]??pool[0]??null;
}