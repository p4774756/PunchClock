import raw from './quest-points.json';
import {POINT_BY_CODE} from '../teaching';
import {ANIME_SUPPORTED_CODES} from '../character-mode';
import type {MeridianQuestId,QuestPointRecord} from './types';
import {QUEST_STAGE_ORDER} from './quest-stages';

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