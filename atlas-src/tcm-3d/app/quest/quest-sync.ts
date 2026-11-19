import {mergeProgress,readLocalProgress,sanitizeProgress,writeLocalProgress} from './quest-progress';
import type {QuestProgressV1} from './types';

let questServerGuest:boolean|null=null;

export function isQuestGuestMode():boolean{
 return questServerGuest===true;
}

export async function syncQuestProgressWithServer():Promise<QuestProgressV1>{
 if(questServerGuest===true)return readLocalProgress();
 const local=readLocalProgress();
 try{
  const put=await fetch('/api/meridian-quest/progress',{
   method:'PUT',
   headers:{'content-type':'application/json'},
   credentials:'include',
   body:JSON.stringify(local),
  });
  if(put.status===401){
   questServerGuest=true;
   return local;
  }
  if(!put.ok)return local;
  const body=await put.json();
  const remote=sanitizeProgress(body?.progress??body);
  const merged=mergeProgress(local,remote);
  writeLocalProgress(merged);
  return merged;
 }catch{
  return local;
 }
}

export async function fetchServerQuestProgress():Promise<QuestProgressV1|null>{
 try{
  const res=await fetch('/api/meridian-quest/progress',{credentials:'include'});
  if(res.status===401){
   questServerGuest=true;
   return null;
  }
  if(!res.ok)return null;
  questServerGuest=false;
  const body=await res.json();
  return sanitizeProgress(body?.progress??body);
 }catch{
  return null;
 }
}

export function bootstrapQuestProgress(onReady:(p:QuestProgressV1)=>void){
 const local=readLocalProgress();
 onReady(local);
 void (async()=>{
  const remote=await fetchServerQuestProgress();
  if(!remote){
   writeLocalProgress(local);
   return;
  }
  questServerGuest=false;
  const merged=mergeProgress(local,remote);
  writeLocalProgress(merged);
  onReady(merged);
  await syncQuestProgressWithServer();
 })();
}

export function persistQuestProgress(progress:QuestProgressV1){
 writeLocalProgress(progress);
 if(questServerGuest===true)return;
 void fetch('/api/meridian-quest/progress',{
  method:'PUT',
  headers:{'content-type':'application/json'},
  credentials:'include',
  body:JSON.stringify(progress),
 }).then(res=>{if(res.status===401)questServerGuest=true;}).catch(()=>{/* 未登入或離線沿用 localStorage */});
}