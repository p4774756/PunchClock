import {QUEST_STAGE_ORDER} from './quest-stages';
import type {MeridianQuestId,QuestProgressV1,StageProgress} from './types';

export const QUEST_STORAGE_KEY='punchclock.meridianQuest.v1';

const STAGE_SET=new Set<string>(QUEST_STAGE_ORDER);

const emptyStage=():StageProgress=>({stars:0,bestTimeMs:null,bestScore:0,clearedAt:null});

export function emptyProgress():QuestProgressV1{
 return {version:1,stages:{}};
}

function clampStar(n:number):0|1|2|3{
 if(!Number.isFinite(n))return 0;
 const v=Math.round(n);
 if(v<=0)return 0;
 if(v>=3)return 3;
 return v as 1|2;
}

function sanitizeStage(raw:unknown):StageProgress|null{
 if(!raw||typeof raw!=='object')return null;
 const s=raw as Record<string,unknown>;
 const stars=clampStar(Number(s.stars));
 let bestTimeMs=Number(s.bestTimeMs);
 if(!Number.isFinite(bestTimeMs)||bestTimeMs<1000||bestTimeMs>3_600_000)bestTimeMs=NaN;
 if(stars<1)bestTimeMs=NaN;
 let bestScore=Number(s.bestScore);
 if(!Number.isFinite(bestScore)||bestScore<0||bestScore>10_000)bestScore=0;
 const clearedAt=typeof s.clearedAt==='string'&&s.clearedAt.trim()?s.clearedAt:null;
 return {
  stars,
  bestTimeMs:Number.isFinite(bestTimeMs)?Math.round(bestTimeMs):null,
  bestScore:Math.round(bestScore),
  clearedAt,
 };
}

export function sanitizeProgress(input:unknown):QuestProgressV1{
 const base=emptyProgress();
 if(!input||typeof input!=='object')return base;
 const src=input as Record<string,unknown>;
 if(src.version!==1)return base;
 const stages=src.stages;
 if(!stages||typeof stages!=='object')return base;
 const out:QuestProgressV1['stages']={};
 for(const [key,val] of Object.entries(stages)){
  if(!STAGE_SET.has(key))continue;
  const clean=sanitizeStage(val);
  if(clean)out[key as MeridianQuestId]=clean;
 }
 return {version:1,stages:out};
}

export function mergeStage(a:StageProgress|undefined,b:StageProgress|undefined):StageProgress{
 const left=a??emptyStage();
 const right=b??emptyStage();
 const stars=Math.max(left.stars,right.stars) as 0|1|2|3;
 const bestScore=Math.max(left.bestScore,right.bestScore);
 let bestTimeMs=left.bestTimeMs;
 if(right.bestTimeMs!=null){
  if(bestTimeMs==null||right.bestTimeMs<bestTimeMs)bestTimeMs=right.bestTimeMs;
 }
 if(stars<1)bestTimeMs=null;
 const clearedAt=[left.clearedAt,right.clearedAt].filter(Boolean).sort().pop()??null;
 return {stars,bestTimeMs,bestScore,clearedAt};
}

export function mergeProgress(a:QuestProgressV1,b:QuestProgressV1):QuestProgressV1{
 const A=sanitizeProgress(a);
 const B=sanitizeProgress(b);
 const stages:QuestProgressV1['stages']={};
 for(const id of QUEST_STAGE_ORDER){
  stages[id]=mergeStage(A.stages[id],B.stages[id]);
 }
 return {version:1,stages};
}

export function stageUnlocked(progress:QuestProgressV1,id:MeridianQuestId):boolean{
 const i=QUEST_STAGE_ORDER.indexOf(id);
 if(i<=0)return true;
 const prev=QUEST_STAGE_ORDER[i-1];
 return (progress.stages[prev]?.stars??0)>=1;
}

export function readLocalProgress():QuestProgressV1{
 if(typeof localStorage==='undefined')return emptyProgress();
 try{
  const raw=localStorage.getItem(QUEST_STORAGE_KEY);
  if(!raw)return emptyProgress();
  return sanitizeProgress(JSON.parse(raw));
 }catch{
  return emptyProgress();
 }
}

export function writeLocalProgress(progress:QuestProgressV1){
 if(typeof localStorage==='undefined')return;
 const clean=sanitizeProgress(progress);
 localStorage.setItem(QUEST_STORAGE_KEY,JSON.stringify(clean));
}