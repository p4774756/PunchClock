import meridians from '../meridians.json';
import type {MeridianQuestId} from './types';

export const QUEST_STAGE_ORDER:MeridianQuestId[]=['LU','LI','ST','SP','HT','SI','BL','KI','PC','TE','GB','LR'];

const meridianById=new Map(meridians.map(m=>[m.id,m]));

export interface QuestStageMeta{
 id:MeridianQuestId;
 name:string;
 count:number;
 orderIndex:number;
}

export function questStageMeta(id:MeridianQuestId):QuestStageMeta{
 const row=meridianById.get(id);
 if(!row)throw new Error(`未知經絡：${id}`);
 return {id,name:row.name,count:row.count,orderIndex:QUEST_STAGE_ORDER.indexOf(id)};
}

export function allQuestStages():QuestStageMeta[]{
 return QUEST_STAGE_ORDER.map(questStageMeta);
}

export function nextStageId(id:MeridianQuestId):MeridianQuestId|null{
 const i=QUEST_STAGE_ORDER.indexOf(id);
 return i>=0&&i<QUEST_STAGE_ORDER.length-1?QUEST_STAGE_ORDER[i+1]:null;
}
