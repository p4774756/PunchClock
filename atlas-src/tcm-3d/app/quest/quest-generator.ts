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

function formatLocationPrompt(location:string){
 const text=location.replace(/^在/,'').trim();
 return `哪個穴位位在：${text}`;
}

function balancedAnswerSlots(count:number,rand:()=>number):number[]{
 const slots:number[]=[];
 while(slots.length<count){
  for(let p=0;p<4&&slots.length<count;p++)slots.push(p);
 }
 return shuffle(slots,rand);
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
 locationText:string|null,
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
  if(!p.location?.trim())return false;
  if(locationText&&p.location.trim()===locationText.trim())return false;
  return true;
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

function orderChoices(codes:string[],answerCode:string,targetIndex:number,rand:()=>number):string[]{
 const answerName=questPointName(answerCode);
 const others=codes.filter(c=>c!==answerCode).map(questPointName);
 const names:(string|null)[]=[null,null,null,null];
 names[targetIndex]=answerName;
 let oi=0;
 for(let i=0;i<4;i++)if(i!==targetIndex)names[i]=others[oi++]??answerName;
 const nameToCode=new Map<string,string>();
 for(const code of codes)nameToCode.set(questPointName(code),code);
 return names.map(n=>nameToCode.get(n!)??answerCode);
}

function makeChoiceQuestion(
 kind:'indication'|'location',
 point:QuestPointRecord,
 meridianId:MeridianQuestId,
 bank:Map<MeridianQuestId,QuestPointRecord[]>,
 rand:()=>number,
 used:Set<string>,
 targetIndex:number,
):QuestQuestion|null{
 if(kind==='indication'&&!isIndicationEligible(point))return null;
 let symptom:string|null=null;
 if(kind==='indication'){
  symptom=pickSymptom(point,[],rand);
  if(!symptom)return null;
 }
 const locationText=kind==='location'?point.location:null;
 const distractors=buildDistractors(meridianId,point,bank,kind,symptom,locationText,rand,used);
 if(distractors.length<3){
  if(kind==='indication'&&symptom){
   const retry=buildDistractors(meridianId,point,bank,kind,symptom,locationText,rand,used);
   if(retry.length<3)return null;
   distractors.length=0;
   distractors.push(...retry);
  }else return null;
 }
 const rawChoices=[point.code,...distractors.map(d=>d.code)];
 const balanced=orderChoices(rawChoices,point.code,targetIndex,rand);
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
   :formatLocationPrompt(point.location),
 };
}

export function generateStage(
 meridianId:MeridianQuestId,
 opts:{seed:number;avatarCodes?:string[];todayCode?:string|null},
):StageQuestionPlan{
 const rand=mulberry32(opts.seed>>>0);
 const bank=questBankByMeridian();
 const meridianPool=[...(bank.get(meridianId)??[])];
 const bankSize=meridianPool.length;
 const maxUses=bankSize<10?2:1;
 const locatePool=(opts.avatarCodes??questAvatarLocateCodes(meridianId)).filter(c=>QUEST_BY_CODE.get(c)?.meridianId===meridianId);
 const locateCodes=shuffle(locatePool,rand).slice(0,MAX_LOCATE);
 const used=new Set<string>();
 const questions:QuestQuestion[]=[];
 const answerUses=new Map<string,number>();
 const answerKinds=new Map<string,Set<string>>();

 const canUseAnswer=(code:string,kind:string,prevCode:string|null)=>{
  const uses=answerUses.get(code)??0;
  if(uses>=maxUses)return false;
  if(uses>=1&&maxUses<2)return false;
  if(uses===1&&maxUses===2){
   const kinds=answerKinds.get(code);
   if(kinds?.has(kind))return false;
   if(prevCode===code)return false;
  }
  return true;
 };

 const markAnswer=(code:string,kind:string)=>{
  answerUses.set(code,(answerUses.get(code)??0)+1);
  const kinds=answerKinds.get(code)??new Set();
  kinds.add(kind);
  answerKinds.set(code,kinds);
 };

 for(const code of locateCodes){
  if(!canUseAnswer(code,'locate',questions.at(-1)?.answerCode??null))continue;
  const name=questPointName(code);
  used.add(code);
  markAnswer(code,'locate');
  questions.push({kind:'locate',code,answerCode:code,prompt:`請點出「${name}」`});
 }

 const choiceSlots=balancedAnswerSlots(TARGET_QUESTIONS-locateCodes.length,rand);
 let slotIndex=0;
 const indicationPool=shuffle(meridianPool.filter(isIndicationEligible),rand);
 const locationPool=shuffle(meridianPool.filter(p=>p.location?.trim()),rand);
 let safety=0;

 while(questions.length<TARGET_QUESTIONS&&safety++<200){
  const prev=questions.at(-1)?.answerCode??null;
  const preferIndication=questions.filter(q=>q.kind==='indication').length<=questions.filter(q=>q.kind==='location').length;
  const tryKinds:('indication'|'location')[]=preferIndication?['indication','location']:['location','indication'];
  let added=false;
  for(const kind of tryKinds){
   const pool=kind==='indication'?[...indicationPool]:[...locationPool];
   for(let pi=0;pi<pool.length;pi++){
    const point=pool[pi];
    if(!canUseAnswer(point.code,kind,prev))continue;
    const q=makeChoiceQuestion(kind,point,meridianId,bank,rand,used,choiceSlots[slotIndex]??0);
    if(!q)continue;
    pool.splice(pi,1);
    if(kind==='indication')indicationPool.splice(indicationPool.indexOf(point),1);
    else locationPool.splice(locationPool.indexOf(point),1);
    markAnswer(point.code,kind);
    questions.push(q);
    slotIndex++;
    added=true;
    break;
   }
   if(added)break;
  }
  if(!added)break;
 }

 if(questions.length<TARGET_QUESTIONS){
  for(const p of shuffle(meridianPool,rand)){
   if(questions.length>=TARGET_QUESTIONS)break;
   const prev=questions.at(-1)?.answerCode??null;
   const kind=isIndicationEligible(p)?'indication':'location';
   if(!canUseAnswer(p.code,kind,prev))continue;
   const q=makeChoiceQuestion(kind,p,meridianId,bank,rand,used,choiceSlots[slotIndex]??questions.length%4);
   if(!q)continue;
   markAnswer(p.code,kind);
   questions.push(q);
   slotIndex++;
  }
 }

 return {
  meridianId,
  seed:opts.seed>>>0,
  questions:questions.slice(0,TARGET_QUESTIONS),
  locateCount:questions.filter(q=>q.kind==='locate').length,
  bonusToday:null,
 };
}