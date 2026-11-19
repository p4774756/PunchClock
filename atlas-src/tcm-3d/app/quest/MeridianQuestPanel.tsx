import {useCallback,useEffect,useMemo,useRef,useState} from 'react';
import {publicAssetUrl} from '../public-assets';
import {QUEST_BY_CODE,questAvatarLocateCodes,questAvatarTrunkLocate,questPointName} from './quest-data';
import {generateStage} from './quest-generator';
import {allQuestStages,nextStageId,questStageMeta} from './quest-stages';
import {comboBonus,scoreForAnswer,starsFromRun} from './quest-score';
import {latestPlayableStage,stageUnlocked,writeLocalProgress} from './quest-progress';
import type {QuestProgressV1} from './types';
import {persistQuestProgress} from './quest-sync';
import {playQuestCorrect,playQuestStar,playQuestWrong,questMuted,setQuestMuted} from './quest-sounds';
import {questNewRecordFlags} from './quest-record';
import {MapGridShell} from './MapSnakePath';
import type {ChoiceQuestion,MeridianQuestId,QuestQuestion,StageProgress} from './types';
import './quest.css';

type View='map'|'play'|'result';

function formatMs(ms:number){
 const sec=Math.max(0,Math.round(ms/1000));
 const m=Math.floor(sec/60);
 const s=sec%60;
 return `${m}:${s.toString().padStart(2,'0')}`;
}

function formatFloatScore(gain:number,combo:number,usedHint:boolean){
 if(usedHint)return `+${gain}`;
 return `+${gain}${combo>1?` (+${comboBonus(combo)})`:''}`;
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
 onLocateFraming?:(meridianId:MeridianQuestId,excludeAnswer:string)=>void;
 onNeedPracticeOutfit:()=>void;
 onLocateAnswer?:(code:string)=>void;
 registerLocateHandler?:(fn:((code:string)=>void)|null)=>void;
}

export default function MeridianQuestPanel({
 open,onClose,progress,onProgress,onPlaying,onMeridianFilter,onHighlight,onHintFocus,onLocateFraming,onNeedPracticeOutfit,registerLocateHandler,
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
 const [accumulatedMs,setAccumulatedMs]=useState(0);
 const [segmentStart,setSegmentStart]=useState(0);
 const [elapsed,setElapsed]=useState(0);
 const [paused,setPaused]=useState(false);
 const [cardOpen,setCardOpen]=useState(false);
 const [feedback,setFeedback]=useState<'idle'|'right'|'wrong'>('idle');
 const [lastPick,setLastPick]=useState<string|null>(null);
 const [floatScore,setFloatScore]=useState<string|null>(null);
 const [shake,setShake]=useState(false);
 const [hiddenChoices,setHiddenChoices]=useState<Set<string>>(new Set());
 const [mute,setMute]=useState(questMuted);
 const [confirmLeave,setConfirmLeave]=useState(false);
 const [resultStars,setResultStars]=useState(0);
 const [resultPrev,setResultPrev]=useState<StageProgress|undefined>();
 const [reviewCode,setReviewCode]=useState<string|null>(null);
 const practiceOnce=useRef(false);
 const playable=latestPlayableStage(progress);
 const q=plan?.questions[index];

 const tickElapsed=useCallback(()=>{
  if(!paused)setElapsed(accumulatedMs+Math.max(0,Date.now()-segmentStart));
 },[accumulatedMs,paused,segmentStart]);

 useEffect(()=>{if(open)setView('map');},[open]);

 useEffect(()=>{
  if(view!=='play'||!playId||paused)return;
  const tick=window.setInterval(tickElapsed,250);
  return()=>window.clearInterval(tick);
 },[view,playId,paused,tickElapsed]);

 useEffect(()=>{
  onPlaying(view==='play',playId);
  onMeridianFilter(view==='play'&&playId?playId:null);
  if(view!=='play'){onHighlight(null);onHintFocus(null);}
 },[view,playId,onPlaying,onMeridianFilter,onHighlight,onHintFocus]);

 useEffect(()=>{
  if(view!=='play'||!plan||practiceOnce.current)return;
  if(plan.questions.some(qq=>qq.kind==='locate'&&questAvatarTrunkLocate(qq.code))){
   practiceOnce.current=true;
   onNeedPracticeOutfit();
  }
 },[view,plan,onNeedPracticeOutfit]);

 useEffect(()=>{
  if(view!=='play'||!playId||!q||q.kind!=='locate'||cardOpen)return;
  onLocateFraming?.(playId,q.answerCode);
 },[view,playId,q,cardOpen,onLocateFraming,index]);

 const pauseTimer=useCallback(()=>{
  setAccumulatedMs(ms=>ms+Math.max(0,Date.now()-segmentStart));
  setPaused(true);
 },[segmentStart]);

 const resumeTimer=useCallback(()=>{
  setSegmentStart(Date.now());
  setPaused(false);
 },[]);

 useEffect(()=>{
  if(confirmLeave&&!paused)pauseTimer();
 },[confirmLeave,paused,pauseTimer]);

 const saveStage=useCallback((stars:0|1|2|3,totalMs:number,totalScore:number)=>{
  if(!playId)return;
  const prev=progress.stages[playId];
  const prevStars=prev?.stars??0;
  const prevTime=prev?.bestTimeMs;
  const prevScore=prev?.bestScore??0;
  const next:StageProgress={
   stars:Math.max(prevStars,stars) as 0|1|2|3,
   bestScore:Math.max(prevScore,totalScore),
   bestTimeMs:stars>=1?(prevTime==null?totalMs:Math.min(prevTime,totalMs)):prev?.bestTimeMs??null,
   clearedAt:stars>=1?new Date().toISOString():(prev?.clearedAt??null),
  };
  const merged={version:1 as const,stages:{...progress.stages,[playId]:next}};
  onProgress(merged);
  writeLocalProgress(merged);
  persistQuestProgress(merged);
 },[playId,progress,onProgress]);

 const finishCard=useCallback(()=>{
  setCardOpen(false);
  resumeTimer();
  setFeedback('idle');
  setFloatScore(null);
  setLastPick(null);
  onHighlight(null);
  if(!plan)return;
  if(index>=plan.questions.length-1){
   tickElapsed();
   const totalMs=accumulatedMs+Math.max(0,Date.now()-segmentStart);
   const stars=starsFromRun(correct,plan.questions.length,totalMs);
   setElapsed(totalMs);
   saveStage(stars,totalMs,score);
   setResultStars(0);
   window.setTimeout(()=>setResultStars(stars),50);
   if(stars>0)playQuestStar();
   setView('result');
   return;
  }
  setIndex(i=>i+1);
  setUsedHint(false);
  setHiddenChoices(new Set());
 },[plan,index,correct,accumulatedMs,segmentStart,score,saveStage,onHighlight,resumeTimer,tickElapsed]);

 const answerLocate=useCallback((code:string)=>{
  if(!q||q.kind!=='locate'||cardOpen)return;
  const ok=code===q.answerCode;
  const nextCombo=ok?combo+1:0;
  const gain=scoreForAnswer(ok,ok?nextCombo:0,usedHint);
  pauseTimer();
  if(ok){
   setCorrect(c=>c+1);
   setCombo(nextCombo);
   setMaxCombo(m=>Math.max(m,nextCombo));
   playQuestCorrect();
   setFloatScore(formatFloatScore(gain,nextCombo,usedHint));
   onHighlight(q.answerCode);
  }else{
   setWrong(w=>w.includes(q.answerCode)?w:[...w,q.answerCode]);
   setCombo(0);
   setShake(true);
   playQuestWrong();
   onHighlight(q.answerCode);
   window.setTimeout(()=>setShake(false),400);
  }
  setScore(s=>s+gain);
  setFeedback(ok?'right':'wrong');
  setCardOpen(true);
 },[q,cardOpen,combo,usedHint,onHighlight,pauseTimer]);

 useEffect(()=>{registerLocateHandler?.(view==='play'?answerLocate:null);return()=>registerLocateHandler?.(null);},[registerLocateHandler,view,answerLocate]);

 const answerChoice=(code:string)=>{
  if(!q||q.kind==='locate'||cardOpen)return;
  setLastPick(code);
  const ok=code===q.answerCode;
  const nextCombo=ok?combo+1:0;
  const gain=scoreForAnswer(ok,ok?nextCombo:0,usedHint);
  pauseTimer();
  if(ok){
   setCorrect(c=>c+1);
   setCombo(nextCombo);
   setMaxCombo(m=>Math.max(m,nextCombo));
   playQuestCorrect();
   setFloatScore(formatFloatScore(gain,nextCombo,usedHint));
  }else{
   setWrong(w=>w.includes(q.answerCode)?w:[...w,q.answerCode]);
   setCombo(0);
   setShake(true);
   playQuestWrong();
   window.setTimeout(()=>setShake(false),400);
  }
  setScore(s=>s+gain);
  setFeedback(ok?'right':'wrong');
  setCardOpen(true);
 };

 const useHint=()=>{
  if(hints<=0||!q||cardOpen)return;
  setHints(h=>h-1);
  setUsedHint(true);
  if(q.kind==='locate')onHintFocus(q.answerCode);
  else{
   const cq=q as ChoiceQuestion;
   const wrongCodes=cq.choices.filter(c=>c!==cq.answerCode);
   setHiddenChoices(new Set(wrongCodes.slice(0,2)));
  }
 };

 const beginStage=(id:MeridianQuestId)=>{
  if(!stageUnlocked(progress,id))return;
  practiceOnce.current=false;
  setResultPrev(progress.stages[id]?{...progress.stages[id]}:undefined);
  setPlayId(id);
  setSeed(Math.floor(Math.random()*1e9));
  setIndex(0);
  setCombo(0);
  setScore(0);
  setCorrect(0);
  setHints(2);
  setUsedHint(false);
  setWrong([]);
  setMaxCombo(0);
  setAccumulatedMs(0);
  setSegmentStart(Date.now());
  setElapsed(0);
  setPaused(false);
  setCardOpen(false);
  setFeedback('idle');
  setLastPick(null);
  setHiddenChoices(new Set());
  setReviewCode(null);
  setView('play');
 };

 const attemptClose=()=>{
  if(view==='play'){setConfirmLeave(true);return;}
  onClose();
 };

 useEffect(()=>{
  const esc=(e:KeyboardEvent)=>{if(e.key==='Escape')attemptClose();};
  if(open)window.addEventListener('keydown',esc);
  return()=>window.removeEventListener('keydown',esc);
 },[open,view]);

 if(!open)return null;

 const locateCount=questAvatarLocateCodes(selected).length;
 const selectedMeta=questStageMeta(selected);
 const unlocked=stageUnlocked(progress,selected);
 const best=progress.stages[selected]?.bestTimeMs;
 const showWindow=view!=='play';

 const playHud=view==='play'&&plan&&playId&&q?<>
  <div className={`mq-play-layer ${cardOpen?'mq-card-open':''}`} aria-hidden="false">
   <div className={`mq-hud ${shake?'mq-shake':''}`}>
    <div className="mq-hud-panel">
     <div className="mq-hud-top">
      <span className="mq-hud-title">第 {stages.findIndex(s=>s.id===playId)+1} 關 · {questStageMeta(playId).name}</span>
      <div className="mq-hud-tools">
       <button type="button" className="mq-toolbtn" onClick={()=>{const next=!mute;setMute(next);setQuestMuted(next);}} aria-label="音效">{mute?'🔇':'🔊'}</button>
       <button type="button" className="mq-toolbtn mq-hud-close" onClick={attemptClose}><span className="mq-esc">Esc</span>關閉</button>
      </div>
     </div>
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
   <button type="button" className="mq-hint-btn mq-toolbtn" onClick={useHint} disabled={hints<=0||cardOpen}>提示（{hints}）</button>
   {q.kind!=='locate'&&<div className="mq-choices">
    {(q as ChoiceQuestion).choices.filter(c=>!hiddenChoices.has(c)).map(code=>{
     let cls='mq-choice';
     if(feedback!=='idle'&&code===q.answerCode)cls+=' correct';
     else if(feedback==='wrong'&&code===lastPick)cls+=' wrong-pick';
     return <button key={code} type="button" className={cls} onClick={()=>answerChoice(code)} disabled={cardOpen}>{questPointName(code)}</button>;
    })}
   </div>}
   {cardOpen&&<IndicationCard q={q} onNext={finishCard} desktopSide={q.kind!=='locate'}/>}
  </div>
 </>:null;

 return <div className={`mq-root ${view==='play'?'mq-root-play':''}`} role="dialog" aria-modal="true" aria-label="經絡闖關">
  {showWindow&&<div className="mq-overlay">
   <div className="mq-window">
    <div className="mq-titlebar"><h2>經絡闖關</h2><button type="button" className="mq-close" onClick={attemptClose}><span className="mq-esc">Esc</span>關閉</button></div>
    <div className="mq-body">
     {view==='map'&&<>
      <div className="mq-map-bg" style={{backgroundImage:`url(${publicAssetUrl('textures/woodcut-landscape.webp')})`}}>
       <MapGridShell stages={stages} progress={progress}>
        {stages.map((st,idx)=>{
         const canPlay=stageUnlocked(progress,st.id);
         const stars=progress.stages[st.id]?.stars??0;
         const isLatest=canPlay&&st.id===playable;
         return <button key={st.id} type="button"
          className={`mq-node ${canPlay?'':'locked'} ${selected===st.id?'picked':''} ${isLatest?'latest':''}`}
          data-idx={idx}
          onClick={()=>setSelected(st.id)}>
          {!canPlay&&<span className="mq-lock" aria-hidden="true">🔒</span>}
          <span className="mq-node-name">{st.name}</span>
          <span className="mq-node-count">{st.count} 穴</span>
          <span className="mq-stars">{starRow(stars)}</span>
         </button>;
        })}
       </MapGridShell>
      </div>
      <div className="mq-info">
       <h3>{selectedMeta.name} · {selectedMeta.count} 穴</h3>
       <p>{locateCount>0?`角色標記 ${locateCount} 穴，本關最多 ${Math.min(4,locateCount)} 題找穴位。`:'角色標記 0 穴，本關以主治與定位題為主。'}</p>
       <p>最佳時間：{best!=null?formatMs(best):'—'}</p>
       {!unlocked&&<p>先通過上一關</p>}
       <button type="button" className="mq-primary" disabled={!unlocked} onClick={()=>beginStage(selected)}>開始</button>
      </div>
     </>}

     {view==='result'&&playId&&plan&&<ResultView playId={playId} plan={plan} correct={correct} elapsed={elapsed} score={score} maxCombo={maxCombo} wrong={wrong}
      prevStage={resultPrev} progress={progress} stars={resultStars} onRetry={()=>beginStage(playId)} onMap={()=>setView('map')}
      onNext={()=>{const n=nextStageId(playId);if(n&&stageUnlocked(progress,n))beginStage(n);}}
      onReview={code=>setReviewCode(code)}/>}
    </div>
   </div>
  </div>}
  {playHud}
  {reviewCode&&<ReviewCard code={reviewCode} onClose={()=>setReviewCode(null)}/>}
  {confirmLeave&&<ConfirmLeave onStay={()=>{setConfirmLeave(false);if(view==='play'&&!cardOpen)resumeTimer();}} onQuit={()=>{setConfirmLeave(false);onClose();}}/>}
 </div>;
}

function starRow(n:number){
 return [0,1,2].map(i=><span key={i} className={i<n?'mq-star-on':''}>{i<n?'★':'☆'}</span>);
}

function IndicationCard({q,onNext,desktopSide}:{q:QuestQuestion;onNext:()=>void;desktopSide?:boolean}){
 const row=QUEST_BY_CODE.get(q.answerCode);
 if(!row)return null;
 return <div className={`mq-card ${desktopSide?'mq-card-side':''}`}>
  <h3>{row.name}（{row.code}）· {questStageMeta(row.meridianId).name}</h3>
  <p>{row.location}</p>
  <div className="mq-tags">{row.indications.map(t=><span key={t}>{t}</span>)}</div>
  {row.category&&<p>特定穴：{row.category}</p>}
  <p className="mq-disclaimer">教學參考，非診療建議</p>
  <button type="button" className="mq-primary" onClick={onNext}>下一題</button>
 </div>;
}

function ResultView({playId,plan,correct,elapsed,score,maxCombo,wrong,prevStage,progress,stars,onRetry,onMap,onNext,onReview}:{playId:MeridianQuestId;plan:ReturnType<typeof generateStage>;correct:number;elapsed:number;score:number;maxCombo:number;wrong:string[];prevStage:StageProgress|undefined;progress:QuestProgressV1;stars:number;onRetry:()=>void;onMap:()=>void;onNext:()=>void;onReview:(code:string)=>void}){
 const nxt=nextStageId(playId);
 const nextOk=!!nxt&&stageUnlocked(progress,nxt);
 const computed=starsFromRun(correct,plan.questions.length,elapsed);
 const record=questNewRecordFlags(prevStage,computed as 0|1|2|3,elapsed,score);
 const [shown,setShown]=useState(0);
 useEffect(()=>{
  if(stars<=0)return;
  let i=0;
  const step=()=>{i++;setShown(i);if(i<stars)window.setTimeout(step,250);};
  step();
 },[stars]);
 return <div className="mq-result">
  <h3>{questStageMeta(playId).name} 結算</h3>
  <p className="mq-stars mq-result-stars">{starRow(shown||computed)}</p>
  {record.show&&<span className="mq-new-record">新紀錄</span>}
  <p>正確率 {Math.round(correct/plan.questions.length*100)}% · 時間 {formatMs(elapsed)} · 分數 {score} · 最高連擊 {maxCombo}</p>
  <div className="mq-result-actions">
   <button type="button" className="mq-primary" onClick={onRetry}>再挑戰</button>
   <button type="button" className="mq-primary" disabled={!nextOk} onClick={onNext}>下一關</button>
   <button type="button" className="mq-primary" onClick={onMap}>回選關</button>
  </div>
  {wrong.length>0&&<div className="mq-wrong-list">
   <p>本關答錯：</p>
   {wrong.map(c=><button key={c} type="button" className="mq-wrong-btn" onClick={()=>onReview(c)}>{questPointName(c)}（{c}）</button>)}
  </div>}
 </div>;
}

function ReviewCard({code,onClose}:{code:string;onClose:()=>void}){
 const row=QUEST_BY_CODE.get(code);
 if(!row)return null;
 return <div className="mq-overlay" style={{zIndex:130}}>
  <div className="mq-window" style={{height:'auto',maxWidth:520,maxHeight:'80dvh'}}>
   <div className="mq-titlebar"><h2>複習</h2></div>
   <div className="mq-card" style={{position:'relative',left:'auto',right:'auto',bottom:'auto',transform:'none',width:'auto',maxHeight:'none'}}>
    <h3>{row.name}（{row.code}）· {questStageMeta(row.meridianId).name}</h3>
    <p>{row.location}</p>
    <div className="mq-tags">{row.indications.map(t=><span key={t}>{t}</span>)}</div>
    {row.category&&<p>特定穴：{row.category}</p>}
    <p className="mq-disclaimer">教學參考，非診療建議</p>
    <button type="button" className="mq-primary" onClick={onClose}>關閉</button>
   </div>
  </div>
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