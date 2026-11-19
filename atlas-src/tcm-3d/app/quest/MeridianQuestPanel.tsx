import {useCallback,useEffect,useMemo,useState} from 'react';
import {publicAssetUrl} from '../public-assets';
import {QUEST_BY_CODE,questAvatarLocateCodes,questPointName} from './quest-data';
import {generateStage} from './quest-generator';
import {allQuestStages,nextStageId,questStageMeta} from './quest-stages';
import {comboBonus,scoreForAnswer,starsFromRun} from './quest-score';
import {stageUnlocked,writeLocalProgress} from './quest-progress';
import type {QuestProgressV1} from './types';
import {persistQuestProgress} from './quest-sync';
import {playQuestCorrect,playQuestStar,playQuestWrong,questMuted,setQuestMuted} from './quest-sounds';
import type {ChoiceQuestion,MeridianQuestId,QuestQuestion,StageProgress} from './types';
import './quest.css';

type View='map'|'play'|'result';

function formatMs(ms:number){
 const sec=Math.max(0,Math.round(ms/1000));
 const m=Math.floor(sec/60);
 const s=sec%60;
 return `${m}:${s.toString().padStart(2,'0')}`;
}

function starRow(n:number){
 return [0,1,2].map(i=><span key={i}>{i<n?'★':'☆'}</span>);
}

interface Props{
 open:boolean;
 onClose:()=>void;
 progress:QuestProgressV1;
 onProgress:(p:QuestProgressV1)=>void;
 onPlaying:(active:boolean,meridian:MeridianQuestId|null)=>void;
 onMeridianFilter:(id:MeridianQuestId|null)=>void;
 onHighlight:(code:string|null)=>void;
 onHintFocus:(code:string|null)=>void;
 onNeedPracticeOutfit:()=>void;
 onLocateAnswer?:(code:string)=>void;
 registerLocateHandler?:(fn:((code:string)=>void)|null)=>void;
}

export default function MeridianQuestPanel({
 open,onClose,progress,onProgress,onPlaying,onMeridianFilter,onHighlight,onHintFocus,onNeedPracticeOutfit,registerLocateHandler,
}:Props){
 const stages=allQuestStages();
 const [view,setView]=useState<View>('map');
 const [selected,setSelected]=useState<MeridianQuestId>('LU');
 const [playId,setPlayId]=useState<MeridianQuestId|null>(null);
 const [seed,setSeed]=useState(()=>Math.floor(Math.random()*1e9));
 const plan=useMemo(()=>playId?generateStage(playId,{seed,avatarCodes:questAvatarLocateCodes(playId)}):null,[playId,seed]);
 const [index,setIndex]=useState(0);
 const [combo,setCombo]=useState(0);
 const [score,setScore]=useState(0);
 const [correct,setCorrect]=useState(0);
 const [hints,setHints]=useState(2);
 const [usedHint,setUsedHint]=useState(false);
 const [wrong,setWrong]=useState<string[]>([]);
 const [maxCombo,setMaxCombo]=useState(0);
 const [started,setStarted]=useState(0);
 const [elapsed,setElapsed]=useState(0);
 const [paused,setPaused]=useState(false);
 const [cardOpen,setCardOpen]=useState(false);
 const [feedback,setFeedback]=useState<'idle'|'right'|'wrong'>('idle');
 const [floatScore,setFloatScore]=useState<string|null>(null);
 const [shake,setShake]=useState(false);
 const [hiddenChoices,setHiddenChoices]=useState<Set<string>>(new Set());
 const [mute,setMute]=useState(questMuted);
 const [confirmLeave,setConfirmLeave]=useState(false);
 const q=plan?.questions[index];

 const trunkLocate=useMemo(()=>{
  if(!q||q.kind!=='locate')return false;
  const text=QUEST_BY_CODE.get(q.code)?.location??'';
  return /胸|腹|背|腰|人迎|天突|膻中/.test(text);
 },[q]);

 useEffect(()=>{if(open)setView('map');},[open]);

 useEffect(()=>{
  if(view!=='play'||!playId||paused||cardOpen)return;
  const tick=window.setInterval(()=>{if(started)setElapsed(Date.now()-started);},250);
  return()=>window.clearInterval(tick);
 },[view,playId,paused,cardOpen,started]);

 useEffect(()=>{
  onPlaying(view==='play',playId);
  onMeridianFilter(view==='play'&&playId?playId:null);
  if(view!=='play'){onHighlight(null);onHintFocus(null);}
 },[view,playId,onPlaying,onMeridianFilter,onHighlight,onHintFocus]);

 useEffect(()=>{if(view==='play'&&trunkLocate)onNeedPracticeOutfit();},[view,trunkLocate,onNeedPracticeOutfit]);

 const saveStage=useCallback((stars:0|1|2|3,totalMs:number,totalScore:number)=>{
  if(!playId)return;
  const prev=progress.stages[playId];
  const next:StageProgress={
   stars:Math.max(prev?.stars??0,stars) as 0|1|2|3,
   bestScore:Math.max(prev?.bestScore??0,totalScore),
   bestTimeMs:stars>=1?(prev?.bestTimeMs==null?totalMs:Math.min(prev.bestTimeMs,totalMs)):prev?.bestTimeMs??null,
   clearedAt:stars>=1?new Date().toISOString():(prev?.clearedAt??null),
  };
  const merged={version:1 as const,stages:{...progress.stages,[playId]:next}};
  onProgress(merged);
  writeLocalProgress(merged);
  persistQuestProgress(merged);
 },[playId,progress,onProgress]);

 const finishCard=useCallback(()=>{
  setCardOpen(false);setPaused(false);setFeedback('idle');setFloatScore(null);onHighlight(null);
  if(!plan)return;
  if(index>=plan.questions.length-1){
   const stars=starsFromRun(correct,plan.questions.length,elapsed);
   saveStage(stars,elapsed,score);
   if(stars>0)playQuestStar();
   setView('result');
   return;
  }
  setIndex(i=>i+1);setUsedHint(false);setHiddenChoices(new Set());
 },[plan,index,correct,elapsed,score,saveStage,onHighlight]);

 const answerLocate=useCallback((code:string)=>{
  if(!q||q.kind!=='locate'||cardOpen)return;
  const ok=code===q.answerCode;
  const nextCombo=ok?combo+1:0;
  const gain=scoreForAnswer(ok,ok?nextCombo:0,usedHint);
  if(ok){
   setCorrect(c=>c+1);setCombo(nextCombo);setMaxCombo(m=>Math.max(m,nextCombo));playQuestCorrect();
   setFloatScore(`+${gain}${nextCombo>1?` (+${comboBonus(nextCombo)})`:''}`);onHighlight(q.answerCode);
  }else{
   setWrong(w=>w.includes(q.answerCode)?w:[...w,q.answerCode]);setCombo(0);setShake(true);playQuestWrong();onHighlight(q.answerCode);
   window.setTimeout(()=>setShake(false),400);
  }
  setScore(s=>s+gain);setFeedback(ok?'right':'wrong');setCardOpen(true);setPaused(true);
 },[q,cardOpen,combo,usedHint,onHighlight]);

 useEffect(()=>{registerLocateHandler?.(view==='play'?answerLocate:null);return()=>registerLocateHandler?.(null);},[registerLocateHandler,view,answerLocate]);

 const answerChoice=(code:string)=>{
  if(!q||q.kind==='locate'||cardOpen)return;
  const ok=code===q.answerCode;
  const nextCombo=ok?combo+1:0;
  const gain=scoreForAnswer(ok,ok?nextCombo:0,usedHint);
  if(ok){setCorrect(c=>c+1);setCombo(nextCombo);setMaxCombo(m=>Math.max(m,nextCombo));playQuestCorrect();setFloatScore(`+${gain}`);}
  else{setWrong(w=>w.includes(q.answerCode)?w:[...w,q.answerCode]);setCombo(0);setShake(true);playQuestWrong();}
  setScore(s=>s+gain);setFeedback(ok?'right':'wrong');setCardOpen(true);setPaused(true);
 };

 const useHint=()=>{
  if(hints<=0||!q||cardOpen)return;
  setHints(h=>h-1);setUsedHint(true);
  if(q.kind==='locate')onHintFocus(q.answerCode);
  else{
   const cq=q as ChoiceQuestion;
   const wrongCodes=cq.choices.filter(c=>c!==cq.answerCode);
   setHiddenChoices(new Set(wrongCodes.slice(0,2)));
  }
 };

 const beginStage=(id:MeridianQuestId)=>{
  if(!stageUnlocked(progress,id))return;
  setPlayId(id);setSeed(Math.floor(Math.random()*1e9));setIndex(0);setCombo(0);setScore(0);setCorrect(0);setHints(2);
  setUsedHint(false);setWrong([]);setMaxCombo(0);setElapsed(0);setStarted(Date.now());setPaused(false);
  setCardOpen(false);setFeedback('idle');setHiddenChoices(new Set());setView('play');
 };

 const attemptClose=()=>{
  if(view==='play'&&index<(plan?.questions.length??0)&&!cardOpen){setConfirmLeave(true);return;}
  onClose();
 };

 useEffect(()=>{
  const esc=(e:KeyboardEvent)=>{if(e.key==='Escape')attemptClose();};
  if(open)window.addEventListener('keydown',esc);
  return()=>window.removeEventListener('keydown',esc);
 },[open,view,index,plan,cardOpen]);

 if(!open)return null;

 const locateCount=questAvatarLocateCodes(selected).length;
 const selectedMeta=questStageMeta(selected);
 const unlocked=stageUnlocked(progress,selected);
 const best=progress.stages[selected]?.bestTimeMs;

 return <div className="mq-root mq-overlay" role="dialog" aria-modal="true" aria-label="經絡闖關">
  <div className="mq-window">
   <div className="mq-titlebar"><h2>經絡闖關</h2><button type="button" className="mq-close" onClick={attemptClose}><span className="mq-esc">Esc</span>關閉</button></div>
   <div className="mq-body">
    {view==='map'&&<>
     <div className="mq-map-bg" style={{backgroundImage:`url(${publicAssetUrl('textures/woodcut-landscape.webp')})`}}>
      <div className="mq-map-grid">
       {stages.map((st,idx)=>{
        const canPlay=stageUnlocked(progress,st.id);
        const stars=progress.stages[st.id]?.stars??0;
        const row=idx<6?0:1;
        const col=row===0?idx:11-idx;
        return <button key={st.id} type="button" className={`mq-node ${canPlay?'':'locked'} ${selected===st.id?'current':''}`}
         style={{gridColumn:`${col+1}`,gridRow:`${row+1}`}} onClick={()=>setSelected(st.id)}>
         <span className="mq-node-name">{st.name}</span>
         <span className="mq-node-count">{st.count} 穴</span>
         <span className="mq-stars">{starRow(stars)}</span>
         {!canPlay&&<span className="mq-lock" aria-hidden="true">🔒</span>}
        </button>;
       })}
      </div>
     </div>
     <div className="mq-info">
      <h3>{selectedMeta.name} · {selectedMeta.count} 穴</h3>
      <p>{locateCount>0?`角色標記 ${locateCount} 穴，本關最多 ${Math.min(4,locateCount)} 題找穴位。`:'角色標記 0 穴，本關以主治與定位題為主。'}</p>
      <p>最佳時間：{best!=null?formatMs(best):'—'}</p>
      {!unlocked&&<p>先通過上一關</p>}
      <button type="button" className="mq-primary" disabled={!unlocked} onClick={()=>beginStage(selected)}>開始</button>
     </div>
    </>}

    {view==='play'&&plan&&playId&&q&&<>
     <div className="mq-toolbar">
      <button type="button" className="mq-toolbtn" onClick={()=>{const next=!mute;setMute(next);setQuestMuted(next);}}>{mute?'🔇靜音':'🔊音效'}</button>
      <button type="button" className="mq-toolbtn" onClick={useHint} disabled={hints<=0||cardOpen}>提示（{hints}）</button>
     </div>
     <div className={`mq-hud ${shake?'mq-shake':''}`}>
      <div className="mq-hud-panel">
       <div className="mq-hud-top">第 {stages.findIndex(s=>s.id===playId)+1} 關 · {questStageMeta(playId).name}</div>
       <div className="mq-hud-q">{q.prompt}</div>
       <div className="mq-hud-meta">
        <span>{index+1}/{plan.questions.length}</span>
        <div className="mq-bar"><i style={{width:`${((index+1)/plan.questions.length)*100}%`}}/></div>
        <span>{formatMs(elapsed)}</span>
        {combo>1&&<span>連擊 x{combo}</span>}
       </div>
      </div>
     </div>
     {floatScore&&<div className="mq-float">{floatScore}</div>}
     {q.kind!=='locate'&&<div className="mq-choices">
      {(q as ChoiceQuestion).choices.filter(c=>!hiddenChoices.has(c)).map(code=>
       <button key={code} type="button" className={`mq-choice ${cardOpen&&code===q.answerCode?'correct':''}`}
        onClick={()=>answerChoice(code)} disabled={cardOpen}>{questPointName(code)}</button>
      )}
     </div>}
     {cardOpen&&<IndicationCard q={q} onNext={finishCard}/>}
    </>}

    {view==='result'&&playId&&plan&&<ResultView playId={playId} plan={plan} correct={correct} elapsed={elapsed} score={score} maxCombo={maxCombo} wrong={wrong}
     progress={progress} onRetry={()=>beginStage(playId)} onMap={()=>setView('map')} onNext={()=>{const n=nextStageId(playId);if(n&&stageUnlocked(progress,n))beginStage(n);}}/>}
   </div>
  </div>
  {confirmLeave&&<ConfirmLeave onStay={()=>setConfirmLeave(false)} onQuit={()=>{setConfirmLeave(false);onClose();}}/>}
 </div>;
}

function IndicationCard({q,onNext}:{q:QuestQuestion;onNext:()=>void}){
 const row=QUEST_BY_CODE.get(q.answerCode);
 if(!row)return null;
 return <div className="mq-card">
  <h3>{row.name}（{row.code}）· {questStageMeta(row.meridianId).name}</h3>
  <p>{row.location}</p>
  <div className="mq-tags">{row.indications.map(t=><span key={t}>{t}</span>)}</div>
  {row.category&&<p>特定穴：{row.category}</p>}
  <p className="mq-disclaimer">教學參考，非診療建議</p>
  <button type="button" className="mq-primary" onClick={onNext}>下一題</button>
 </div>;
}

function ResultView({playId,plan,correct,elapsed,score,maxCombo,wrong,progress,onRetry,onMap,onNext}:{playId:MeridianQuestId;plan:ReturnType<typeof generateStage>;correct:number;elapsed:number;score:number;maxCombo:number;wrong:string[];progress:QuestProgressV1;onRetry:()=>void;onMap:()=>void;onNext:()=>void}){
 const stars=starsFromRun(correct,plan.questions.length,elapsed);
 const nxt=nextStageId(playId);
 const nextOk=!!nxt&&stageUnlocked(progress,nxt);
 return <div className="mq-result">
  <h3>{questStageMeta(playId).name} 結算</h3>
  <p className="mq-stars" style={{fontSize:22}}>{starRow(stars)}</p>
  <p>正確率 {Math.round(correct/plan.questions.length*100)}% · 時間 {formatMs(elapsed)} · 分數 {score} · 最高連擊 {maxCombo}</p>
  <div style={{display:'flex',flexWrap:'wrap',gap:8}}>
   <button type="button" className="mq-primary" onClick={onRetry}>再挑戰</button>
   <button type="button" className="mq-primary" disabled={!nextOk} onClick={onNext}>下一關</button>
   <button type="button" className="mq-primary" onClick={onMap}>回選關</button>
  </div>
  {wrong.length>0&&<p>本關答錯：{wrong.map(c=>questPointName(c)).join('、')}</p>}
 </div>;
}

function ConfirmLeave({onStay,onQuit}:{onStay:()=>void;onQuit:()=>void}){
 return <div className="mq-overlay" style={{zIndex:130}}>
  <div className="mq-window" style={{height:'auto',maxWidth:360}}>
   <div className="mq-titlebar"><h2>放棄這一關？</h2></div>
   <div className="mq-result" style={{padding:16}}>
    <button type="button" className="mq-primary" onClick={onQuit}>是，放棄</button>
    <button type="button" className="mq-primary" onClick={onStay}>繼續</button>
   </div>
  </div>
 </div>;
}