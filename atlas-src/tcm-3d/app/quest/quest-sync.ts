import {mergeProgress,readLocalProgress,sanitizeProgress,writeLocalProgress} from './quest-progress';
import type {QuestProgressV1} from './types';

export async function syncQuestProgressWithServer():Promise<QuestProgressV1>{
 const local=readLocalProgress();
 try{
  const put=await fetch('/api/meridian-quest/progress',{
   method:'PUT',
   headers:{'content-type':'application/json'},
   credentials:'include',
   body:JSON.stringify(local),
  });
  if(put.status===401)return local;
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
  if(res.status===401)return null;
  if(!res.ok)return null;
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
  const merged=mergeProgress(local,remote);
  writeLocalProgress(merged);
  onReady(merged);
  await syncQuestProgressWithServer();
 })();
}

export function persistQuestProgress(progress:QuestProgressV1){
 writeLocalProgress(progress);
 void fetch('/api/meridian-quest/progress',{
  method:'PUT',
  headers:{'content-type':'application/json'},
  credentials:'include',
  body:JSON.stringify(progress),
 }).catch(()=>{/* 未登入或離線沿用 localStorage */});
}