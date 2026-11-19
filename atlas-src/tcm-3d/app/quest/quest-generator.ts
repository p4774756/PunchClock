import {questAvatarLocateCodes,questBankByMeridian,isIndicationEligible,questPointName,QUEST_BY_CODE} from './quest-data';
import {QUEST_STAGE_ORDER} from './quest-stages';
import type {MeridianQuestId,QuestPointRecord,QuestQuestion,StageQuestionPlan} from './types';

const MAX_LOCATE=4;
const TARGET_QUESTIONS=10;

export function questOrderFromCode(code:string):number{
 const m=code.match(/(\d+)/);
 return m?Number(m[1]):0;
}

function mulberry32(seed:number){
 let t=seed>>>0;
 return ()=>{
  t+=0x6D2B79F5;
  let r=Math.imul(t^(t>>>15),1|t);
  r^=r+Math.imul(r^(r>>>7),61|r);
  return ((r^(r>>>14))>>>0)/4294967296;
 };
}

function shuffle<T>(list:T[],rand:()=>number):T[]{
 const arr=[...list];
 for(let i=arr.length-1;i>0;i--){
  const j=Math.floor(rand()*(i+1));
  [arr[i],arr[j]]=[arr[j],arr[i]];
 }
 return arr;
}

function neighborMeridians(id:MeridianQuestId):MeridianQuestId[]{
 const i=QUEST_STAGE_ORDER.indexOf(id);
 const out:MeridianQuestId[]=[];
 if(i>0)out.push(QUEST_STAGE_ORDER[i-1]);
 if(i<QUEST_STAGE_ORDER.length-1)out.push(QUEST_STAGE_ORDER[i+1]);
 return out;
}

function pickSymptom(point:QuestPointRecord,distractors:QuestPointRecord[],rand:()=>number):string|null{
 const pool=point.indications.filter(sym=>!distractors.some(d=>d.indications.includes(sym)));
 if(!pool.length)return null;
 return pool[Math.floor(rand()*pool.length)];
}

function buildDistractors(
 meridianId:MeridianQuestId,
 answer:QuestPointRecord,
 bank:Map<MeridianQuestId,QuestPointRecord[]>,
 kind:'indication'|'location',
 symptom:string|null,
 rand:()=>number,
 used:Set<string>,
):QuestPointRecord[]{
 const same=bank.get(meridianId)??[];
 const pool:QuestPointRecord[]=[];
 const allow=(p:QuestPointRecord)=>{
  if(p.code===answer.code)return false;
  if(used.has(p.code))return false;
  if(kind==='indication'){
   if(!isIndicationEligible(p))return false;
   if(symptom&&p.indications.includes(symptom))return false;
   return true;
  }
  return !!p.location?.trim();
 };
 for(const p of shuffle(same,rand))if(allow(p))pool.push(p);
 if(pool.length<3){
  for(const nid of neighborMeridians(meridianId)){
   for(const p of shuffle(bank.get(nid)??[],rand))if(allow(p))pool.push(p);
  }
 }
 if(pool.length<3){
  for(const nid of QUEST_STAGE_ORDER){
   if(nid===meridianId)continue;
   for(const p of shuffle(bank.get(nid)??[],rand))if(allow(p))pool.push(p);
  }
 }
 return pool.slice(0,3);
}

function balanceCorrectIndex(choiceCodes:string[],answerCode:string,questionIndex:number,rand:()=>number):string[]{
 const answerName=questPointName(answerCode);
 const otherCodes=choiceCodes.filter(c=>c!==answerCode);
 const otherNames=otherCodes.map(questPointName);
 const target=questionIndex%4;
 const orderedNames:(string|null)[]=[null,null,null,null];
 orderedNames[target]=answerName;
 let oi=0;
 for(let i=0;i<4;i++)if(i!==target)orderedNames[i]=otherNames[oi++]??answerName;
 if(rand()>.5&&target!==((questionIndex+2)%4)){
  const swap=(target+1)%4;
  const a=orderedNames[target],b=orderedNames[swap];
  orderedNames[target]=b;orderedNames[swap]=a;
 }
 const nameToCode=new Map<string,string>();
 for(const code of choiceCodes)nameToCode.set(questPointName(code),code);
 return orderedNames.map(n=>nameToCode.get(n!)??answerCode);
}

function makeChoiceQuestion(
 kind:'indication'|'location',
 point:QuestPointRecord,
 meridianId:MeridianQuestId,
 bank:Map<MeridianQuestId,QuestPointRecord[]>,
 rand:()=>number,
 used:Set<string>,
 questionIndex:number,
):QuestQuestion|null{
 if(kind==='indication'&&!isIndicationEligible(point))return null;
 const distractors=buildDistractors(meridianId,point,bank,kind,kind==='indication'?null:null,rand,used);
 if(distractors.length<3)return null;
 let symptom:string|null=null;
 if(kind==='indication'){
  symptom=pickSymptom(point,distractors,rand);
  if(!symptom){
   symptom=pickSymptom(point,[],rand);
   if(!symptom)return null;
   const retry=buildDistractors(meridianId,point,bank,kind,symptom,rand,used);
   if(retry.length<3)return null;
   distractors.length=0;
   distractors.push(...retry);
  }
 }
 const rawChoices=[point.code,...distractors.map(d=>d.code)];
 const balanced=balanceCorrectIndex(rawChoices,point.code,questionIndex,rand);
 used.add(point.code);
 return {
  kind,
  code:point.code,
  answerCode:point.code,
  choices:balanced,
  symptom:symptom??undefined,
  locationText:kind==='location'?point.location:undefined,
  prompt:kind==='indication'
   ?`哪個穴位主治「${symptom}」？`
   :`哪個穴位位在：${point.location}`,
 };
}

export function generateStage(
 meridianId:MeridianQuestId,
 opts:{seed:number;avatarCodes?:string[];todayCode?:string|null},
):StageQuestionPlan{
 const rand=mulberry32(opts.seed>>>0);
 const bank=questBankByMeridian();
 const meridianPool=[...(bank.get(meridianId)??[])];
 const locatePool=(opts.avatarCodes??questAvatarLocateCodes(meridianId)).filter(c=>QUEST_BY_CODE.get(c)?.meridianId===meridianId);
 const locateCodes=shuffle(locatePool,rand).slice(0,MAX_LOCATE);
 const used=new Set<string>();
 const questions:QuestQuestion[]=[];

 for(const code of locateCodes){
  const name=questPointName(code);
  used.add(code);
  questions.push({kind:'locate',code,answerCode:code,prompt:`請點出「${name}」`});
 }

 const indicationPool=shuffle(meridianPool.filter(isIndicationEligible),rand);
 const locationPool=shuffle(meridianPool.filter(p=>p.location?.trim()),rand);
 let iq=0,lq=0;
 const answerUse=new Map<string,number>();

 const nextPoint=(kind:'indication'|'location'):QuestPointRecord|null=>{
  const pool=kind==='indication'?indicationPool:locationPool;
  while(pool.length){
   const p=pool.pop()!;
   const times=answerUse.get(p.code)??0;
   if(times>=1&&questions.length<TARGET_QUESTIONS-1)continue;
   if(kind==='indication'&&!isIndicationEligible(p))continue;
   if(used.has(p.code)&&times>=1)continue;
   return p;
  }
  return null;
 };

 while(questions.length<TARGET_QUESTIONS){
  const preferIndication=questions.filter(q=>q.kind==='indication').length<=questions.filter(q=>q.kind==='location').length;
  const kind: 'indication'|'location'=preferIndication?'indication':'location';
  const point=nextPoint(kind)??nextPoint(kind==='indication'?'location':'indication');
  if(!point)break;
  const q=makeChoiceQuestion(kind,point,meridianId,bank,rand,used,questions.length);
  if(!q){
   if(kind==='indication')iq++;
   else lq++;
   if(iq>8&&lq>8)break;
   continue;
  }
  answerUse.set(point.code,(answerUse.get(point.code)??0)+1);
  questions.push(q);
 }

 if(questions.length<TARGET_QUESTIONS){
  for(const p of shuffle(meridianPool,rand)){
   if(questions.length>=TARGET_QUESTIONS)break;
   const kind=isIndicationEligible(p)?'indication':'location';
   const q=makeChoiceQuestion(kind,p,meridianId,bank,rand,used,questions.length);
   if(q)questions.push(q);
  }
 }

 return {
  meridianId,
  seed:opts.seed>>>0,
  questions:questions.slice(0,TARGET_QUESTIONS),
  locateCount:locateCodes.length,
  bonusToday:null,
 };
}