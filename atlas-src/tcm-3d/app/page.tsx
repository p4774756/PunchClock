import {publicAssetUrl} from './public-assets';
import {ANIME_DEFAULT_SET} from './character-mode';
import {DEFAULT_AVATAR_ID,animeAvatar,avatarLessonNote,avatarPracticeOpen,avatarSupportsPoint,avatarSupportedCodes} from './avatars';
import {ANIME_OUTFITS,DEFAULT_OUTFIT_ID,animeOutfit,type OutfitId} from './outfits';
import {lazy,Suspense,useCallback,useEffect,useMemo,useRef,useState,type CSSProperties,type PointerEvent as ReactPointerEvent} from 'react';
import {ArrowUpRight,Check,ChevronDown,Eye,EyeOff,Focus,Info,Layers3,MapPin,Maximize2,Pause,Play,RotateCcw,RotateCw,Search,SlidersHorizontal,X} from 'lucide-react';
import type {SceneState,View} from './anatomy';
import {CURRICULUM_SETS,curriculumBadge,curriculumSet,initialSimulation,isPracticeDeferred,LOCATION,POINTS,POINT_BY_CODE,MERIDIANS,pointInCurriculum,practiceOpenCodes,REGIONS,STANDARD,TOOLS,type CurriculumSetId,type RegionId,type SimulationState,type SurfaceTarget,type ToolId,type ToolDragPhase} from './teaching';

const CharacterScene=lazy(()=>import('./character-scene'));

const initialScene:SceneState={explode:0,visible:[],selected:[],isolate:false,view:'front',rotate:false,reset:0};
const VIEW_LABELS:Record<View,string>={'three-quarter':'斜視',front:'正面',back:'背面',side:'側面'};
const clean=(value:string)=>value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[\s\-_]/g,'');
const HOME_HREF=import.meta.env.BASE_URL.startsWith('/atlas')?'/':'https://tcm.jic.io/';
const BACK_HOME_LABEL=HOME_HREF==='/'?'回島':'返回首頁';
const BACK_HOME_ARIA=HOME_HREF==='/'?'回島':'返回中醫首頁';
const readLaunchParams=()=>{
 if(typeof window==='undefined')return {set:null as CurriculumSetId|null,code:null as string|null};
 const params=new URLSearchParams(window.location.search);
 const setId=params.get('set');
 const set=curriculumSet(setId)?.id??null;
 const rawCode=params.get('code')??params.get('point');
 const code=rawCode&&POINTS.some(p=>p.code===rawCode)?rawCode:null;
 return {set,code};
};

function ToolGlyph({tool}:{tool:ToolId}){
 return <svg viewBox="0 0 40 40" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
  {tool==='needle'&&<><path d="m9 32 14-14M23 18l8-10 3 3-10 8-1-1Z"/><path d="m26 12 4 4m-2-6 4 4M7 34l2-2"/></>}
  {tool==='finger'&&<><path d="M15 33 9 23c-2-4 1-6 4-3l3 4V8c0-4 6-4 6 0v12l3-2c2-1 3 0 4 1l2 2c2 1 2 4 1 7l-2 6H17"/><path d="M22 20v5m4-5v5m4-2v3M17 36h12"/></>}
 </svg>;
}

export default function Home(){
 const launch=useMemo(()=>readLaunchParams(),[]);
 const [state,setState]=useState<SceneState>(()=>launch.set||launch.code?{...initialScene,view:'front'}:initialScene);
 const currentAvatar=animeAvatar(DEFAULT_AVATAR_ID);
 const avatarCodes=useMemo(()=>avatarSupportedCodes(currentAvatar),[currentAvatar]);
 const [simulation,setSimulation]=useState<SimulationState>(()=>{
  const set=launch.set??ANIME_DEFAULT_SET;
  // Deep-link ?code= focuses that point; otherwise keep 印堂 and the full-body fit view
  // (do not jump to 百會 / focus:1 — that drills into hair).
  const preferred=launch.code&&avatarPracticeOpen(currentAvatar,launch.code)?launch.code:null;
  const code=preferred??initialSimulation.pointCode;
  return {...initialSimulation,curriculumSet:set,pointCode:code,side:1,focus:preferred?1:0};
 });
 const [progress,setProgress]=useState(0),[error,setError]=useState('');
 const [region,setRegion]=useState<RegionId|'all'>('all'),[query,setQuery]=useState('');
 const [toolsExpanded,setToolsExpanded]=useState(false);
 const [mobilePanel,setMobilePanel]=useState<'library'|'selection'|null>(null);
 const [about,setAbout]=useState(false),[holding,setHolding]=useState(false);
 const [outfitId,setOutfitId]=useState<OutfitId>(DEFAULT_OUTFIT_ID);
 const currentOutfit=animeOutfit(outfitId);
 const showOutfitBar=currentAvatar.hasOutfits&&ANIME_OUTFITS.length>1;
 const practiceCodes=useMemo(()=>new Set(practiceOpenCodes(avatarCodes)),[avatarCodes]);
 const [feedback,setFeedback]=useState(()=>{
  const lesson=curriculumSet(launch.set??ANIME_DEFAULT_SET);
  if(lesson){
   const open=practiceOpenCodes([...avatarCodes].filter(c=>lesson.codes.includes(c))).length;
   return `課堂：${lesson.name}（可練習 ${open} 穴）。胸腹背腰與人迎／天突請切「練習裝」。`;
  }
  if(launch.code&&avatarPracticeOpen(currentAvatar,launch.code))return `已開啟穴位 ${launch.code}。`;
  if(launch.code)return avatarLessonNote(currentAvatar,launch.code);
  return '選擇穴位與工具，開始一次課堂演示。';
 });
 const dialog=useRef<HTMLDialogElement>(null),searchInput=useRef<HTMLInputElement>(null);
 const heldPointer=useRef<number|null>(null);
 const draggedPoint=useRef<{code:string;name:string}|null>(null);
 const bootedUrl=useRef(false);
 const point=POINTS.find(p=>p.code===simulation.pointCode);
 const tool=TOOLS.find(t=>t.id===simulation.tool)!;
 const activeLesson=curriculumSet(simulation.curriculumSet);
 const animePointReady=avatarPracticeOpen(currentAvatar,simulation.pointCode);
 const lessonOpenCodes=useMemo(()=>activeLesson?practiceOpenCodes(activeLesson.codes.filter(c=>avatarCodes.has(c))):[...practiceCodes],[activeLesson,avatarCodes,practiceCodes]);
 const lessonIndex=activeLesson&&simulation.pointCode?lessonOpenCodes.indexOf(simulation.pointCode):-1;
 const lessonProgress=activeLesson&&lessonIndex>=0?{name:activeLesson.shortName,index:lessonIndex+1,total:lessonOpenCodes.length}:null;
 const hasTarget=!!point||!!simulation.customTarget;
 const canPlayDemo=hasTarget&&(!!simulation.customTarget||animePointReady)&&progress===100&&!error;
 const location=point?LOCATION[point.code]:null;
 const results=useMemo(()=>{
  const lesson=curriculumSet(simulation.curriculumSet);
  const ordered=lesson?lesson.codes.map(code=>POINTS.find(p=>p.code===code)!).filter(Boolean):POINTS.filter(p=>avatarCodes.has(p.code));
  return ordered.filter(p=>avatarCodes.has(p.code)&&(!simulation.meridianFilter||simulation.meridianFilter==='all'||p.meridianId===simulation.meridianFilter)&&(region==='all'||p.region===region)&&(!query||clean(`${p.code} ${p.name} ${p.pinyin}`).includes(clean(query))));
 },[query,region,simulation.meridianFilter,simulation.curriculumSet,avatarCodes]);

 useEffect(()=>{
  if(typeof window==='undefined')return;
  const params=new URLSearchParams();
  if(activeLesson)params.set('set',activeLesson.id);
  if(simulation.pointCode)params.set('code',simulation.pointCode);
  const next=`${params.toString()?`?${params}`:''}${window.location.hash}`;
  if(!bootedUrl.current){bootedUrl.current=true;if(next!==`${window.location.search}${window.location.hash}`)window.history.replaceState(null,'',`${window.location.pathname}${next}`);return;}
  const current=`${window.location.search}${window.location.hash}`;
  if(next!==current)window.history.replaceState(null,'',`${window.location.pathname}${next}`);
 },[activeLesson,simulation.pointCode]);
 useEffect(()=>{if(!simulation.running)return;let frame=0,last=performance.now();const tick=(now:number)=>{const delta=Math.min((now-last)/1000,.1);last=now;setSimulation(s=>s.running?{...s,elapsed:s.elapsed+delta}:s);frame=requestAnimationFrame(tick);};frame=requestAnimationFrame(tick);return()=>cancelAnimationFrame(frame);},[simulation.running]);
 const pause=useCallback((message='演示已暫停，當前畫面保持。')=>{heldPointer.current=null;setHolding(false);setSimulation(s=>({...s,dragging:false,running:false}));setFeedback(message);},[]);
 const releaseHold=useCallback(()=>{heldPointer.current=null;setHolding(false);setSimulation(s=>({...s,dragging:false,running:false,release:(s.release??0)+1}));setFeedback('已鬆開，皮膚與器具逐漸回彈。');},[]);
 useEffect(()=>{const blur=()=>pause('窗口已離開，演示自動暫停。');const visibility=()=>{if(document.hidden)blur();};window.addEventListener('blur',blur);document.addEventListener('visibilitychange',visibility);return()=>{window.removeEventListener('blur',blur);document.removeEventListener('visibilitychange',visibility);};},[pause]);
 useEffect(()=>{if(about){dialog.current?.showModal();pause('已打開來源與許可，演示暫停。');}else dialog.current?.close();},[about,pause]);
 useEffect(()=>{const key=(event:KeyboardEvent)=>{if(event.key==='Escape'){setAbout(false);setMobilePanel(null);pause();}if(event.key==='/'&&!(event.target instanceof HTMLInputElement)&&!(event.target instanceof HTMLTextAreaElement)){event.preventDefault();setMobilePanel('library');searchInput.current?.focus();}};window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);},[pause]);

 const prepareFocus=()=>setState(s=>({...s,explode:0,rotate:false,isolate:false,selected:[]}));
 const switchOutfit=(id:OutfitId)=>{
  if(id===outfitId||!currentAvatar.hasOutfits)return;
  const next=animeOutfit(id);
  setOutfitId(id);
  setProgress(0);
  setError('');
  setFeedback(`已換裝：${next.name}。`);
 };
 const choosePoint=(code:string,side:1|-1=simulation.side)=>{
  const next=POINTS.find(p=>p.code===code);if(!next)return;
  if(isPracticeDeferred(code)||!avatarSupportsPoint(currentAvatar,code)){
   setFeedback(avatarLessonNote(currentAvatar,code));
   setMobilePanel('selection');
   return;
  }
  prepareFocus();setHolding(false);heldPointer.current=null;
  setSimulation(s=>({...s,pointCode:code,side:next.midline?1:side,customTarget:null,dragging:false,running:false,elapsed:0,focus:s.focus+1,pickMode:'point'}));
  const tip=(code==='ST9'||code==='CV22')&&outfitId!=='study-body'
   ?`已選擇${next.name} ${next.code}。請切「練習裝」露出頸前皮膚再演示。`
   :`已選擇${next.name} ${next.code}，可播放針／指壓演示。`;
  setFeedback(tip);
  setMobilePanel(null);
 };
 const stepLesson=(dir:-1|1)=>{
  if(!point)return;
  const list=lessonOpenCodes.map(code=>POINT_BY_CODE.get(code)!).filter(Boolean);
  const i=list.findIndex(p=>p.code===point.code);
  if(i<0){setFeedback('目前穴位不在此課程可練習列表內。');return;}
  choosePoint(list[(i+dir+list.length)%list.length].code);
 };
 const chooseTool=(id:ToolId)=>{setHolding(false);heldPointer.current=null;setSimulation(s=>({...s,tool:id,dragging:false,running:false,elapsed:0}));setFeedback(`已切換為${TOOLS.find(t=>t.id===id)!.name}，演示回到起點。`);};
 const chooseSide=(side:1|-1)=>{if(point?.midline)return;prepareFocus();setSimulation(s=>({...s,side,dragging:false,running:false,elapsed:0,focus:s.focus+1}));setFeedback(`已切換到患者${side===1?'左':'右'}側。`);};
 const onToolDrag=useCallback((target:SurfaceTarget|null,phase:ToolDragPhase)=>{
  if(phase==='start'){draggedPoint.current=null;setState(s=>({...s,rotate:false}));setSimulation(s=>({...s,dragging:true,running:true,elapsed:Math.max(.01,s.elapsed)}));setFeedback('正在操作工具：拖到穴位附近可自動選中。');}
  else if(phase==='move'&&target){const selected=target.pointCode?POINTS.find(p=>p.code===target.pointCode):null;draggedPoint.current=selected?{code:selected.code,name:selected.name}:null;setSimulation(s=>selected?{...s,customTarget:null,pointCode:selected.code,side:selected.midline?1:(target.pointSide??s.side),pickMode:'point',dragging:true}:{...s,customTarget:target,pointCode:null,pickMode:'point',dragging:true});}
  else if(phase==='end'||phase==='cancel'){setSimulation(s=>({...s,dragging:false,running:false,release:(s.release??0)+1}));const selected=draggedPoint.current;setFeedback(phase==='cancel'?'工具移動已結束，已恢復人體旋轉。':selected?`已選中${selected.name} ${selected.code}，可繼續演示。`:'工具已停在新的皮膚位置，可繼續拖動或播放演示。');draggedPoint.current=null;}
 },[]);
 const changeView=(view:View)=>{pause(`已切換為${VIEW_LABELS[view]}。`);setState(s=>({...s,view,rotate:false,reset:s.reset+1}));};
 const fullBody=()=>{pause('已回到全身視角。');setState(s=>({...s,explode:0,isolate:false,selected:[],rotate:false,reset:s.reset+1}));};
 const focusTarget=()=>{if(!hasTarget)return;prepareFocus();pause('已聚焦當前教學位置。');setSimulation(s=>({...s,focus:s.focus+1}));};
 const play=(restart=false)=>{if(!canPlayDemo)return;setState(s=>({...s,explode:0,rotate:false,isolate:false}));setSimulation(s=>({...s,running:true,elapsed:restart?0:s.elapsed,focus:s.focus+1}));setFeedback(`${tool.name}演示進行中：${tool.effect}。`);};
 const startHold=(event:ReactPointerEvent<HTMLButtonElement>)=>{if(!canPlayDemo)return;event.preventDefault();event.currentTarget.setPointerCapture(event.pointerId);heldPointer.current=event.pointerId;setHolding(true);play();};
 const endHold=(event:ReactPointerEvent<HTMLButtonElement>)=>{if(heldPointer.current!==event.pointerId)return;heldPointer.current=null;if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId);releaseHold();};
 const onProgress=useCallback((value:number)=>{setProgress(value);if(value===100)setError('');},[]);
 const onError=useCallback((_message:string)=>{setError('3D 模型加載出現問題，請檢查網絡與瀏覽器 WebGL 支持後重試。');setSimulation(s=>({...s,dragging:false,running:false}));},[]);
 const selectedTitle=point?.name??(simulation.customTarget?'自選體表位置':'點選穴位標記');
 const animationStatus=simulation.dragging?'操作中':simulation.running?'演示中':simulation.elapsed>0?'已暫停':'待演示';

 return <main className={`studio rpg-studio is-anime${showOutfitBar?' has-outfit-bar':''}`} style={{'--tool-color':tool.color} as CSSProperties}>
  <header className="studio-header rpg-header">
   <div className="brand-block">
    <a className="brand" href={HOME_HREF} aria-label={BACK_HOME_ARIA}><span className="brand-mark">經</span><span><span className="brand-name">經絡研習室<span className="edition">RPG</span></span><span className="brand-subtitle">中醫人體 · 動漫助教</span></span></a>
    <a className="back-home" href={HOME_HREF}>{BACK_HOME_LABEL}</a>
   </div>
   <div className="header-actions">
    <div className="header-status"><span className="status-dot"/>穴位練習中<span className="header-divider"/>{currentAvatar.shortName}</div>
    <button className="source-button" onClick={()=>setAbout(true)}><Info size={16}/><span>來源與許可</span></button>
   </div>
  </header>

  <section className="scene-stage" aria-label="3D 人體教學視窗">
   <Suspense fallback={null}><CharacterScene key={`${DEFAULT_AVATAR_ID}-${outfitId}`} state={{...state,inspectorOpen:false}} simulation={simulation} avatarId={DEFAULT_AVATAR_ID} outfitId={outfitId} onPoint={choosePoint} onToolDrag={onToolDrag} onProgress={onProgress} onError={onError}/></Suspense>
   <div className="scene-guide"><span className="guide-cross">+</span><span>{currentAvatar.shortName} · {currentOutfit.shortName}</span></div>
   {(progress<100||error)&&(
    <div className={error?'loading-card woodcut-loading load-error':'loading-card woodcut-loading'} role="status">
     <span className="loading-symbol">{error?(<Info size={23}/>):(<Layers3 size={23}/>)}</span>
     <strong>{error?'模型暫未就緒':('正在迎接'+currentAvatar.shortName)}</strong>
     <p>{error||('載入 '+currentOutfit.name+' 與已對應穴位標記。')}</p>
     {!error&&<><div className="load-track"><i style={{width:progress+'%'}}/></div><small>{progress}%</small></>}
     {error&&<button className="text-button" onClick={()=>window.location.reload()}>重新加載</button>}
    </div>
   )}
   <div className="scene-mode-hint"><span className="tiny-dot"/>{animePointReady?'點選金色穴位 · 抓住工具移動 · 拖動旋轉 · 滾動縮放':avatarLessonNote(currentAvatar,simulation.pointCode)}</div>
  </section>

  <nav className="camera-bar surface" aria-label="人體視角">
   {(['front','back','side','three-quarter'] as View[]).map(view=><button key={view} className={state.view===view?'selected':''} aria-pressed={state.view===view} onClick={()=>changeView(view)}>{VIEW_LABELS[view]}</button>)}
   <span className="control-divider"/><button onClick={fullBody} title="顯示全身"><Maximize2 size={14}/><span>全身</span></button><button onClick={focusTarget} disabled={!hasTarget} title="聚焦當前穴位"><Focus size={14}/><span>聚焦</span></button><button className={`icon-button ${state.rotate?'selected':''}`} aria-label={state.rotate?'停止旋轉':'自動旋轉'} aria-pressed={state.rotate} onClick={()=>{pause();setState(s=>({...s,rotate:!s.rotate}));setFeedback(state.rotate?'自動旋轉已停止。':'正在自動旋轉。');}}><RotateCw size={15}/></button>
  </nav>

  {showOutfitBar&&<nav className="outfit-bar surface" aria-label="動漫服裝"><span className="outfit-label">換裝</span>{ANIME_OUTFITS.map(outfit=><button key={outfit.id} type="button" className={outfitId===outfit.id?'selected':''} aria-pressed={outfitId===outfit.id} title={outfit.blurb} onClick={()=>switchOutfit(outfit.id)}>{outfit.shortName}</button>)}</nav>}

  <aside className={`library-panel surface ${mobilePanel==='library'?'mobile-expanded':''}`} aria-label="穴位庫">
   <button className="mobile-panel-heading" aria-expanded={mobilePanel==='library'} onClick={()=>setMobilePanel(p=>p==='library'?null:'library')}><span><Layers3 size={16}/>穴位庫</span><ChevronDown size={17}/></button>
   <div className="panel-content">
    <div className="panel-tabs" role="tablist" aria-label="學習內容"><button role="tab" aria-selected className="selected" id="points-tab" aria-controls="points-panel">穴位<span>{practiceCodes.size}</span></button></div>
    <div className="tab-body" id="points-panel" role="tabpanel" aria-labelledby="points-tab">
     <label className="search-box"><Search size={15}/><input ref={searchInput} value={query} onChange={e=>setQuery(e.target.value)} placeholder="穴名、拼音或編號" aria-label="搜索教學穴位"/>{query?<button aria-label="清空搜索" onClick={()=>setQuery('')}><X size={13}/></button>:<kbd>/</kbd>}</label>
     <div className="curriculum-controls" role="group" aria-label="課堂基準穴">{CURRICULUM_SETS.filter(set=>set.id===ANIME_DEFAULT_SET||set.codes.some(c=>avatarCodes.has(c))).map(set=>{const open=practiceOpenCodes(set.codes.filter(c=>avatarCodes.has(c)));return <button key={set.id} className={simulation.curriculumSet===set.id?'selected':''} aria-pressed={simulation.curriculumSet===set.id} onClick={()=>{const on=simulation.curriculumSet!==set.id;if(on&&!open.length){setFeedback(`${set.shortName} 尚未烘到角色身上。`);return;}setRegion('all');setQuery('');prepareFocus();setSimulation(s=>({...s,curriculumSet:on?set.id:ANIME_DEFAULT_SET,meridianFilter:'all',pointCode:on?(open[0]??s.pointCode):s.pointCode,side:1,customTarget:null,dragging:false,running:false,elapsed:0,focus:on?s.focus+1:s.focus,pickMode:'point'}));setFeedback(on?`課堂：${set.name}。依教材順序校正與演示。`:'已回到頭頸基準穴。');}}>{set.shortName}<small>{open.length}</small></button>;})}</div>
     {activeLesson&&<p className="curriculum-note">{activeLesson.lesson}{activeLesson.status==='reviewed'?' · 已課堂核對':' · 座標待核對'} · 僅顯示已烘上穴位</p>}
     {lessonProgress&&<div className="lesson-progress-card" aria-live="polite"><strong>{lessonProgress.name} · 第 {lessonProgress.index}／{lessonProgress.total} 穴</strong><div className="lesson-progress-actions"><button type="button" onClick={()=>stepLesson(-1)}>上一穴</button><button type="button" onClick={()=>stepLesson(1)}>下一穴</button></div></div>}
     <details className="region-disclosure"><summary>按身體區域篩選</summary><div className="section-label">選擇身體區域<button className={region==='all'?'current':''} onClick={()=>{setRegion('all');setFeedback('正在顯示全部已烘穴位。');}}>全部</button></div>
     <div className="region-grid">{REGIONS.map(r=><button className={region===r.id?'selected':''} key={r.id} aria-pressed={region===r.id} onClick={()=>{setRegion(r.id);setQuery('');if(avatarCodes.has(r.example))choosePoint(r.example);}}>{r.name}</button>)}</div>
     </details>
     <div className="meridian-controls"><label>十四經脈<select aria-label="選擇經脈" value={simulation.meridianFilter??'all'} onChange={e=>{setRegion('all');setQuery('');setSimulation(s=>({...s,meridianFilter:e.target.value}));}}><option value="all">全部經脈 · {practiceCodes.size} 可練</option>{MERIDIANS.map(m=><option key={m.id} value={m.id}>{m.name}</option>)}</select></label><small>經脈體表連線暫未烘到角色；目錄保留供之後加穴。</small></div>
     <div className="list-heading"><span>{activeLesson?activeLesson.name:'已烘穴位'}</span><span>{results.length} 穴</span></div>
     <div className="point-list">{results.map(p=>{const badge=curriculumBadge(p.code);const deferred=isPracticeDeferred(p.code);return <button key={p.code} type="button" className={`point-row ${p.code===simulation.pointCode?'selected':''} ${pointInCurriculum(p.code,activeLesson?.id)?'curriculum-point':''} ${p.reviewed&&!deferred?'reviewed-point':''} ${deferred?'is-deferred':''}`} onClick={()=>choosePoint(p.code)} aria-pressed={p.code===simulation.pointCode} aria-disabled={deferred||undefined} title={deferred?avatarLessonNote(currentAvatar,p.code):undefined}><span className="point-dot"/><span className="point-row-copy"><span className="point-name">{p.name}{badge&&<em className={`curriculum-badge ${deferred?'is-deferred':p.reviewed?'is-reviewed':''}`}>{badge}</em>}<span>{p.pinyin}</span></span><small>{p.meridian}</small></span><span className="point-code">{p.code}</span></button>;})}{results.length===0&&<div className="empty-state"><Search size={20}/><p>未找到匹配穴位</p><button className="text-button" onClick={()=>{setQuery('');setRegion('all');}}>查看全部已烘穴位</button></div>}</div>
     <div className="point-list-foot"><span>可練習 {practiceCodes.size} 穴 · 胸腹背腰切練習裝</span><button className="icon-button" aria-label={simulation.showPoints?'隱藏穴位點':'顯示穴位點'} aria-pressed={simulation.showPoints} onClick={()=>{setSimulation(s=>({...s,showPoints:!s.showPoints}));setFeedback(simulation.showPoints?'已隱藏穴位點。':'已顯示穴位點。');}}>{simulation.showPoints?<Eye size={15}/>:<EyeOff size={15}/>}</button></div>
    </div>
   </div>
  </aside>

  <aside className={`selection-panel surface ${mobilePanel==='selection'?'mobile-expanded':''}`} aria-label="當前教學選擇">
   <button className="mobile-panel-heading" aria-expanded={mobilePanel==='selection'} onClick={()=>setMobilePanel(p=>p==='selection'?null:'selection')}><span><MapPin size={16}/>{selectedTitle}<small>{point?.code}</small></span><ChevronDown size={17}/></button>
   <div className="panel-content">
    <div className="selection-eyebrow"><span>當前選擇</span><span className="selection-number">{point?.code??(simulation.customTarget?'體表位置':'選穴')}</span></div>
    <div className="selection-title"><h1>{selectedTitle}</h1>{point&&<p>{point.pinyin}<span>·</span>{point.meridian}</p>}</div>
    <div className="selection-scroll">
     {point?<>
      <div className="side-control"><span>患者側別</span><div><button disabled={point.midline} className={simulation.side===1&&!point.midline?'selected':''} onClick={()=>chooseSide(1)} aria-pressed={simulation.side===1&&!point.midline}>左</button><button disabled={point.midline} className={simulation.side===-1&&!point.midline?'selected':''} onClick={()=>chooseSide(-1)} aria-pressed={simulation.side===-1&&!point.midline}>右</button></div>{point.midline&&<small>正中線</small>}</div>
      <div className="location-card"><h2><span className="section-dash"/>標準定位</h2>{location?<><p>{location.text}</p>{location.curriculum?<p className="curriculum-source">{location.curriculum}</p>:null}{location.classic?<p className="classic-source">{location.classic}</p>:null}{location.section?<a href={STANDARD} target="_blank" rel="noreferrer">國標條款 {location.section}<ArrowUpRight size={12}/></a>:null}<small>“寸”為身體分段比例，不是固定釐米。</small></>:<><p>本穴已收錄至教學目錄。標準定位摘要待逐條核錄，請對照原文學習。</p><a href={point.source||STANDARD} target="_blank" rel="noreferrer">查閱定位來源<ArrowUpRight size={12}/></a></>}</div>
      <div className="meridian-navigation lesson-nav-block">
       {lessonProgress&&<span className="lesson-progress-inline">{lessonProgress.name} · 第 {lessonProgress.index}／{lessonProgress.total} 穴</span>}
       <div className="meridian-navigation-buttons"><button onClick={()=>stepLesson(-1)}>上一穴</button><button onClick={()=>stepLesson(1)}>下一穴</button></div>
      </div>
      <div className={`review-note ${animePointReady?'is-reviewed':''}`}><span className="review-dot"/><span>{animePointReady?`${currentAvatar.shortName}已對應並完成檢查`:'尚未烘到角色'}<small>動漫視圖標記為教學示意。</small></span></div>
     </>:simulation.customTarget?<div className="pain-card"><div className="pain-target"><MapPin size={23}/><Check size={12}/></div><h2>當前工具接觸位置</h2><p>拖動器具或「移動工具」手柄，在皮膚上自由操作。</p><small>自主教學選擇，不自動推薦療法。</small></div>:<div className="pain-card"><div className="pain-target"><MapPin size={23}/></div><h2>點選金色穴位開始</h2><p>選中穴位後可播放針或指壓演示。</p></div>}
     <div className="tool-summary"><span className="tool-summary-icon"><ToolGlyph tool={tool.id}/></span><div><small>當前工具</small><h2>{tool.name}<span>{tool.effect}</span></h2></div></div><p className="tool-note">{tool.note}</p>
     <div className="amplitude-control"><label htmlFor="amplitude"><span><SlidersHorizontal size={13}/>演示幅度</span><output>{simulation.amplitude<.35?'輕':simulation.amplitude<.7?'中':'強'}</output></label><input id="amplitude" type="range" min="0.15" max="1" step="0.01" value={simulation.amplitude} onChange={e=>{setSimulation(s=>({...s,amplitude:Number(e.target.value)}));setFeedback('已調整動畫幅度，僅改變視覺反饋強弱。');}}/><small>僅調整動畫，不對應實際操作力度。</small></div>
     <label className="effect-toggle"><span>顯示局部視覺反饋</span><input type="checkbox" checked={simulation.effects} onChange={()=>{setSimulation(s=>({...s,effects:!s.effects}));setFeedback(simulation.effects?'已隱藏局部視覺反饋，仍可觀察工具。':'已開啟局部視覺反饋。');}}/><span className="switch-track" aria-hidden="true"/></label>
    </div>
    <div className="selection-bottom"><span className={`play-state ${simulation.running?'running':''}`}><span/>{animationStatus}</span><span>皮膚反應模擬 · 參數未作人體校準</span></div>
   </div>
  </aside>

  <section className={`tool-dock surface ${toolsExpanded?'tools-open':''}`} aria-label="工具與演示控制">
   <button className="mobile-tools-toggle" aria-expanded={toolsExpanded} aria-controls="tool-palette" onClick={()=>{setToolsExpanded(v=>!v);setMobilePanel(null);}}><ToolGlyph tool={tool.id}/><span>{tool.name}</span><ChevronDown size={14}/></button>
   <div id="tool-palette" className="dock-tools dock-tools-two" role="group" aria-label="選擇演示工具">{TOOLS.map(t=><button className={`tool-button ${simulation.tool===t.id?'selected':''}`} key={t.id} aria-pressed={simulation.tool===t.id} onClick={()=>{chooseTool(t.id);setToolsExpanded(false);}} title={t.effect}><ToolGlyph tool={t.id}/><span>{t.name}</span><i/></button>)}</div>
   <div className="dock-separator"/>
   <div className="playback-controls"><button className={`play-button ${simulation.running?'playing':''}`} disabled={!canPlayDemo} onClick={()=>simulation.running?pause():play()}>{simulation.running?<Pause size={15} fill="currentColor"/>:<Play size={15} fill="currentColor"/>}<span>{simulation.running?'暫停':'播放演示'}</span></button><div className="secondary-playback"><button className={`hold-button ${holding?'holding':''}`} disabled={!canPlayDemo} onPointerDown={startHold} onPointerUp={endHold} onPointerCancel={endHold} onLostPointerCapture={()=>{if(heldPointer.current!==null)releaseHold();}} onKeyDown={e=>{if((e.key===' '||e.key==='Enter')&&!e.repeat&&canPlayDemo){e.preventDefault();setHolding(true);play();}}} onKeyUp={e=>{if((e.key===' '||e.key==='Enter')&&holding){e.preventDefault();releaseHold();}}} onBlur={()=>{if(holding)pause();}}>按住演示</button><button className="replay-button" disabled={!canPlayDemo} onClick={()=>play(true)} aria-label="從頭重播演示" title="從頭重播"><RotateCcw size={14}/></button></div></div>
  </section>

  <footer className="studio-footer"><span className="status-message" role="status" aria-live="polite">{feedback}</span><span className="footer-scope">經絡研習室<span>·</span>動漫對應示意</span></footer>
  <dialog ref={dialog} className="source-dialog" onCancel={()=>setAbout(false)} onClose={()=>setAbout(false)} onClick={e=>{if(e.target===e.currentTarget){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)setAbout(false);}}} aria-labelledby="source-title">
   <div className="dialog-heading"><span className="eyebrow">資料、許可與當前邊界</span><button className="icon-button" onClick={()=>setAbout(false)} aria-label="關閉來源與許可" autoFocus><X size={19}/></button></div>
   <h2 id="source-title">讓每一層，有據可查。</h2><p className="dialog-intro">這是供課堂討論與交互驗證的工程預覽。標準文字、角色模型與操作動畫分別保留來源和狀態。</p>
   <section><h3>穴位定位</h3><p>以 GB/T 12346—2021《經穴名稱與定位》為主要核查來源。本版研習室只在角色身上顯示已烘上的穴位（頭頸 21＋頭胸腹背腰 30，共 51；含人迎、天突）。`teaching-points.json` 仍保留完整目錄與歷史解剖空間座標，供之後每週加穴對位參考，並不代表解剖銅人模型仍在頁面中。</p><a href={STANDARD} target="_blank" rel="noreferrer">查閱國家標準正文<ArrowUpRight size={13}/></a></section>
   <section><h3>動漫角色</h3><p>研習室角色為 VRoid Studio β 版樣本「Sendagaya Shino」（pixiv Inc.，CC0 1.0）。換裝可切「制服」與「練習裝」（深青運動內衣＋高腰短褲、高包頭）；胸腹背腰與人迎／天突請用練習裝，針與指壓打在真實皮膚上。</p><a href="https://creativecommons.org/publicdomain/zero/1.0/" target="_blank" rel="noreferrer">CC0 1.0 說明<ArrowUpRight size={13}/></a><a href={publicAssetUrl('/ATTRIBUTION.md')} target="_blank" rel="noreferrer">署名細節<ArrowUpRight size={13}/></a></section>
   <section><h3>工具與局部反饋</h3><p>針具接觸與指壓形變為教學視覺示意，不預測療效，也不提供真人操作參數。</p><a href="https://www.nccih.nih.gov/health/acupuncture-effectiveness-and-safety" target="_blank" rel="noreferrer">NCCIH：針灸資料<ArrowUpRight size={13}/></a><a href="https://www.mskcc.org/cancer-care/patient-education/acupressure-pain-and-headaches" target="_blank" rel="noreferrer">MSK：指壓資料<ArrowUpRight size={13}/></a></section>
   <button className="dialog-done" onClick={()=>setAbout(false)}>返回研習室</button>
  </dialog>
 </main>;
}
