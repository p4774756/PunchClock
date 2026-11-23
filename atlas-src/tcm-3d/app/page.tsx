import {publicAssetUrl} from './public-assets';
import {ANIME_DEFAULT_SET} from './character-mode';
import {ANIME_AVATARS,DEFAULT_AVATAR_ID,animeAvatar,type AvatarId,avatarLessonNote,avatarPracticeOpen,avatarSupportsPoint,avatarSupportedCodes} from './avatars';
import {defaultOutfitForAvatar,outfitBelongsToAvatar,outfitNeedsAltBuild,outfitsForAvatar,animeOutfitForAvatar,type OutfitId} from './avatar-outfits';
import {LOAD_STEP_LABELS,type AtlasLoadProgress} from './load-progress';
import {lazy,Suspense,useCallback,useEffect,useMemo,useRef,useState,type CSSProperties} from 'react';
import {flushSync} from 'react-dom';
import {ArrowUpRight,Check,ChevronDown,Eye,EyeOff,Filter,Focus,Info,Layers3,MapPin,Maximize2,RotateCw,ScanFace,Search,Shirt,X} from 'lucide-react';
import type {SceneState,View} from './anatomy';
import {acupointMuscleNote} from './acupoint-muscles';
import {CURRICULUM_SETS,curriculumBadge,curriculumSet,initialSimulation,isPracticeDeferred,libraryPointResults,LOCATION,POINTS,POINT_BY_CODE,MERIDIANS,pointInCurriculum,practiceOpenCodes,REGIONS,STANDARD,TOOLS,type CurriculumSetId,type RegionId,type SimulationState,type SurfaceTarget,type ToolId,type ToolDragPhase} from './teaching';

const CharacterScene=lazy(()=>import('./character-scene'));

const initialScene:SceneState={explode:0,visible:[],selected:[],isolate:false,view:'front',rotate:false,reset:0};
const VIEW_LABELS:Record<View,string>={'three-quarter':'斜視',front:'正面',back:'背面',side:'側面'};
const VIEW_CYCLE:View[]=['front','back','side','three-quarter'];
const HOME_HREF=import.meta.env.BASE_URL.startsWith('/atlas')?'/':'https://tcm.jic.io/';
const BACK_HOME_ARIA=HOME_HREF==='/'?'回辦公室':'返回中醫首頁';
const readLaunchParams=()=>{
 if(typeof window==='undefined')return {set:null as CurriculumSetId|null,code:null as string|null};
 const params=new URLSearchParams(window.location.search);
 const setId=params.get('set');
 const set=curriculumSet(setId)?.id??null;
 const rawCode=params.get('code')??params.get('point');
 const code=rawCode&&POINTS.some(p=>p.code===rawCode)?rawCode:null;
 return {set,code};
};
const MUSCLE_STUDIO_VALUES=new Set(['muscle','anatomy','muscles','layer','layers']);
const stripLegacyMuscleStudio=()=>{
 if(typeof window==='undefined')return;
 const params=new URLSearchParams(window.location.search);
 let changed=false;
 for(const key of ['view','mode','studio','tab']){
  const v=params.get(key);
  if(v&&MUSCLE_STUDIO_VALUES.has(v.toLowerCase())){params.delete(key);changed=true;}
 }
 let hash=window.location.hash;
 if(/muscle|anatomy/i.test(hash)){hash='';changed=true;}
 try{
  for(const key of ['studioView','tcm-studio-view','tcm-3d-studio']){
   const v=localStorage.getItem(key);
   if(v&&MUSCLE_STUDIO_VALUES.has(v.toLowerCase()))localStorage.removeItem(key);
  }
 }catch{/* private mode */}
 if(!changed)return;
 const q=params.toString();
 window.history.replaceState(null,'',`${window.location.pathname}${q?`?${q}`:''}${hash}`);
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
 const [avatarId,setAvatarId]=useState<AvatarId>(DEFAULT_AVATAR_ID);
 const currentAvatar=animeAvatar(avatarId);
 const avatarCodes=useMemo(()=>avatarSupportedCodes(currentAvatar),[currentAvatar]);
 const [simulation,setSimulation]=useState<SimulationState>(()=>{
  const set=launch.set??'all';
  // Deep-link ?code= focuses that point; otherwise keep 印堂 and the full-body fit view
  // (do not jump to 百會 / focus:1 — that drills into hair).
  const preferred=launch.code&&avatarPracticeOpen(currentAvatar,launch.code)?launch.code:null;
  const code=preferred??initialSimulation.pointCode;
  return {...initialSimulation,curriculumSet:set,pointCode:code,side:1,focus:preferred?1:0};
 });
 const [progress,setProgress]=useState(6),[loadStep,setLoadStep]=useState(LOAD_STEP_LABELS.program),[scenePainted,setScenePainted]=useState(false),[error,setError]=useState('');
 const [region,setRegion]=useState<RegionId|'all'>('all'),[query,setQuery]=useState('');
 const [mobilePanel,setMobilePanel]=useState<'library'|null>(null);
 const [libraryFilterOpen,setLibraryFilterOpen]=useState(false);
 const [dialogExpanded,setDialogExpanded]=useState(false);
 const [about,setAbout]=useState(false);
 const [assistantOutfitId,setAssistantOutfitId]=useState<OutfitId>('school');
 const [adultOutfitId,setAdultOutfitId]=useState<OutfitId>('suit');
 const outfitId=avatarId==='adult'?adultOutfitId:assistantOutfitId;
 const setOutfitId=avatarId==='adult'?setAdultOutfitId:setAssistantOutfitId;
 const [practicePreparing,setPracticePreparing]=useState(false);
 const [outfitPickerOpen,setOutfitPickerOpen]=useState(false);
 const outfitBarRef=useRef<HTMLDivElement>(null);
 const outfitShirtRef=useRef<HTMLButtonElement>(null);
 const avatarOutfitList=useMemo(()=>outfitsForAvatar(avatarId),[avatarId]);
 const currentOutfit=animeOutfitForAvatar(avatarId,outfitId);
 const showOutfitBar=currentAvatar.hasOutfits&&avatarOutfitList.length>1;
 const practiceCodes=useMemo(()=>new Set(practiceOpenCodes(avatarCodes)),[avatarCodes]);
 const [feedback,setFeedback]=useState(()=>{
  if(launch.set){
   const lesson=curriculumSet(launch.set);
   if(lesson){
    const open=practiceOpenCodes([...avatarCodes].filter(c=>lesson.codes.includes(c))).length;
    return `課堂：${lesson.name}（可練習 ${open} 穴）。胸腹背腰與人迎／天突請切「練習裝」。`;
   }
  }
  if(launch.code&&avatarPracticeOpen(currentAvatar,launch.code))return `已開啟穴位 ${launch.code}。`;
  if(launch.code)return avatarLessonNote(currentAvatar,launch.code);
  return '選擇穴位，拖動針具或抓住移動手柄練習。';
 });
 const dialog=useRef<HTMLDialogElement>(null),searchInput=useRef<HTMLInputElement>(null);
 const draggedPoint=useRef<{code:string;name:string}|null>(null);
 const bootedUrl=useRef(false);
 const point=POINTS.find(p=>p.code===simulation.pointCode);
 const tool=TOOLS.find(t=>t.id===simulation.tool)!;
 const activeLesson=simulation.curriculumSet==='all'?undefined:curriculumSet(simulation.curriculumSet);
 const animePointReady=avatarPracticeOpen(currentAvatar,simulation.pointCode);
 const lessonOpenCodes=useMemo(()=>activeLesson?practiceOpenCodes(activeLesson.codes.filter(c=>avatarCodes.has(c))):[...practiceCodes],[activeLesson,avatarCodes,practiceCodes]);
 const lessonIndex=activeLesson&&simulation.pointCode?lessonOpenCodes.indexOf(simulation.pointCode):-1;
 const lessonProgress=activeLesson&&lessonIndex>=0?{name:activeLesson.shortName,index:lessonIndex+1,total:lessonOpenCodes.length}:null;
 const hasTarget=!!point||!!simulation.customTarget;
 const location=point?LOCATION[point.code]:null;
 const muscleNear=point&&location?.text?acupointMuscleNote(point.code):null;
 const results=useMemo(()=>libraryPointResults(query,region,simulation.meridianFilter,simulation.curriculumSet,avatarCodes),[query,region,simulation.meridianFilter,simulation.curriculumSet,avatarCodes]);

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
 useEffect(()=>{stripLegacyMuscleStudio();},[]);
 useEffect(()=>{if(!simulation.running)return;let frame=0,last=performance.now();const tick=(now:number)=>{const delta=Math.min((now-last)/1000,.1);last=now;setSimulation(s=>s.running?{...s,elapsed:s.elapsed+delta}:s);frame=requestAnimationFrame(tick);};frame=requestAnimationFrame(tick);return()=>cancelAnimationFrame(frame);},[simulation.running]);
 const pause=useCallback((message='演示已暫停，當前畫面保持。')=>{setSimulation(s=>({...s,dragging:false,running:false}));setFeedback(message);},[]);
 useEffect(()=>{const blur=()=>pause('窗口已離開，演示自動暫停。');const visibility=()=>{if(document.hidden)blur();};window.addEventListener('blur',blur);document.addEventListener('visibilitychange',visibility);return()=>{window.removeEventListener('blur',blur);document.removeEventListener('visibilitychange',visibility);};},[pause]);
 useEffect(()=>{if(about){dialog.current?.showModal();pause('已打開來源與許可，演示暫停。');}else dialog.current?.close();},[about,pause]);
 useEffect(()=>{const key=(event:KeyboardEvent)=>{if(event.key==='Escape'){if(outfitPickerOpen){setOutfitPickerOpen(false);return;}if(libraryFilterOpen){setLibraryFilterOpen(false);return;}setAbout(false);setMobilePanel(null);setDialogExpanded(false);pause();}if(event.key==='/'&&!(event.target instanceof HTMLInputElement)&&!(event.target instanceof HTMLTextAreaElement)){event.preventDefault();setMobilePanel('library');requestAnimationFrame(()=>searchInput.current?.focus());}};window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);},[pause,libraryFilterOpen,outfitPickerOpen]);

 const prepareFocus=()=>setState(s=>({...s,explode:0,rotate:false,isolate:false,selected:[]}));
 useEffect(()=>{
  if(!outfitPickerOpen)return;
  const onPointer=(event:PointerEvent)=>{
   const t=event.target;
   if(!(t instanceof Node))return;
   if(outfitBarRef.current?.contains(t)||outfitShirtRef.current?.contains(t))return;
   setOutfitPickerOpen(false);
  };
  window.addEventListener('pointerdown',onPointer,true);
  return ()=>window.removeEventListener('pointerdown',onPointer,true);
 },[outfitPickerOpen]);
 const toggleOutfit=()=>{
  if(!showOutfitBar)return;
  setOutfitPickerOpen(open=>{
   const next=!open;
   if(next){
    setFeedback(`換裝：目前為「${currentOutfit.shortName}」。請在選單中選擇。`);
    requestAnimationFrame(()=>outfitBarRef.current?.querySelector<HTMLButtonElement>('button.is-active,button')?.focus());
   }
   return next;
  });
 };
 const switchOutfit=(id:OutfitId)=>{
  if(id===outfitId||!currentAvatar.hasOutfits||!outfitBelongsToAvatar(avatarId,id))return;
  performance.clearMarks('atlas:outfit-switch-start');
  performance.clearMarks('atlas:outfit-switch-end');
  performance.clearMarks('atlas:outfit-pressed');
  performance.mark('atlas:outfit-switch-start');
  performance.mark('atlas:outfit-pressed');
  const next=animeOutfitForAvatar(avatarId,id);
  setError('');
  setFeedback(`已換裝：${next.name}。`);
  setOutfitPickerOpen(false);
  requestAnimationFrame(()=>{
   setTimeout(()=>{
    flushSync(()=>setOutfitId(id));
    performance.mark('atlas:outfit-ui-painted');
   },0);
  });
 };
 const choosePoint=(code:string,side:1|-1=simulation.side)=>{
  const next=POINTS.find(p=>p.code===code);if(!next)return;
  if(isPracticeDeferred(code)||!avatarSupportsPoint(currentAvatar,code)){
   setFeedback(avatarLessonNote(currentAvatar,code));
   setDialogExpanded(true);
   return;
  }
  prepareFocus();
  setSimulation(s=>({...s,pointCode:code,side:next.midline?1:side,customTarget:null,dragging:false,running:false,elapsed:0,focus:s.focus+1,pickMode:'point'}));
  const tip=(code==='ST9'||code==='CV22')&&outfitId!=='study-body'
   ?`已選擇${next.name} ${next.code}。請切「練習裝」露出頸前皮膚再操作。`
   :`已選擇${next.name} ${next.code}，可拖動針具或移動手柄練習。`;
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
 const chooseSide=(side:1|-1)=>{if(point?.midline)return;prepareFocus();setSimulation(s=>({...s,side,dragging:false,running:false,elapsed:0,focus:s.focus+1}));setFeedback(`已切換到患者${side===1?'左':'右'}側。`);};
 const onToolDrag=useCallback((target:SurfaceTarget|null,phase:ToolDragPhase)=>{
  if(phase==='start'){draggedPoint.current=null;setState(s=>({...s,rotate:false}));setSimulation(s=>({...s,dragging:true,running:true,elapsed:Math.max(.01,s.elapsed)}));setFeedback('正在操作工具：拖到穴位附近可自動選中。');}
  else if(phase==='move'&&target){const selected=target.pointCode?POINTS.find(p=>p.code===target.pointCode):null;draggedPoint.current=selected?{code:selected.code,name:selected.name}:null;setSimulation(s=>selected?{...s,customTarget:null,pointCode:selected.code,side:selected.midline?1:(target.pointSide??s.side),pickMode:'point',dragging:true}:{...s,customTarget:target,pointCode:null,pickMode:'point',dragging:true});}
  else if(phase==='end'||phase==='cancel'){setSimulation(s=>({...s,dragging:false,running:false,release:(s.release??0)+1}));const selected=draggedPoint.current;setFeedback(phase==='cancel'?'工具移動已結束，已恢復人體旋轉。':selected?`已選中${selected.name} ${selected.code}，可繼續拖動工具。`:'工具已停在新的皮膚位置，可繼續拖動。');draggedPoint.current=null;}
 },[]);
 const changeView=(view:View)=>{pause(`已切換為${VIEW_LABELS[view]}。`);setState(s=>({...s,view,rotate:false,reset:s.reset+1}));};
 const cycleView=()=>{const i=VIEW_CYCLE.indexOf(state.view);changeView(VIEW_CYCLE[(i+1)%VIEW_CYCLE.length]);};
 const toggleLibrary=()=>{
  setLibraryFilterOpen(false);
  setMobilePanel(p=>{
   const open=p!=='library';
   if(open)requestAnimationFrame(()=>searchInput.current?.focus());
   return open?'library':null;
  });
 };
 const selectCurriculumAll=()=>{
  if(simulation.curriculumSet==='all')return;
  setRegion('all');
  setQuery('');
  prepareFocus();
  setSimulation(s=>({...s,curriculumSet:'all',meridianFilter:'all',dragging:false,running:false,elapsed:0,pickMode:'point'}));
  setFeedback('正在顯示全部已烘穴位。');
 };
 const selectCurriculumLesson=(set:typeof CURRICULUM_SETS[number])=>{
  if(simulation.curriculumSet===set.id)return;
  const open=practiceOpenCodes(set.codes.filter(c=>avatarCodes.has(c)));
  if(!open.length){setFeedback(`${set.shortName} 尚未烘到角色身上。`);return;}
  setRegion('all');
  setQuery('');
  prepareFocus();
  setSimulation(s=>({...s,curriculumSet:set.id,meridianFilter:'all',pointCode:open[0]??s.pointCode,side:1,customTarget:null,dragging:false,running:false,elapsed:0,focus:s.focus+1,pickMode:'point'}));
  setFeedback(`課堂：${set.name}。依教材順序校正與演示。`);
 };
 const fullBody=()=>{pause('已回到全身視角。');setState(s=>({...s,explode:0,isolate:false,selected:[],rotate:false,reset:s.reset+1}));};
 const focusTarget=()=>{if(!hasTarget)return;prepareFocus();pause('已聚焦當前教學位置。');setSimulation(s=>({...s,focus:s.focus+1}));};
 const onProgress=useCallback((value:AtlasLoadProgress|number)=>{
  const next=typeof value==='number'?{percent:value,step:'download' as const,stepLabel:'載入中…'}:value;
  setProgress(prev=>Math.max(prev,next.percent));
  setLoadStep(next.stepLabel);
  if(next.percent===100)setError('');
 },[]);
 const onFrameReady=useCallback(()=>setScenePainted(true),[]);
 const loadingVisible=!!error||(!scenePainted||progress<100);
 const loadingHint=error?'':progress<100?loadStep||('載入 '+currentOutfit.name+' 與已對應穴位標記。'):'正在繪製第一幅畫面…';
 const onError=useCallback((_message:string)=>{setError('3D 模型加載出現問題，請檢查網絡與瀏覽器 WebGL 支持後重試。');setSimulation(s=>({...s,dragging:false,running:false}));},[]);
 const onPracticeOutfitPreparing=useCallback((preparing:boolean)=>{setPracticePreparing(preparing);},[]);
 const selectAvatar=useCallback((next:AvatarId)=>{
  if(next===avatarId)return;
  pause(`已切換為${animeAvatar(next).shortName}。`);
  setAvatarId(next);
  setOutfitPickerOpen(false);
  if(next==='adult')setAdultOutfitId(defaultOutfitForAvatar('adult'));
  setScenePainted(false);
  setProgress(6);
  setLoadStep(LOAD_STEP_LABELS.program);
  setError('');
  setSimulation(s=>({...s,dragging:false,running:false,customTarget:null}));
 },[avatarId,pause]);
 const selectedTitle=point?.name??(simulation.customTarget?'自選體表位置':'點選穴位標記');
 const outfitPending=practicePreparing&&outfitNeedsAltBuild(avatarId,outfitId);
 const dialogBlurb=useMemo(()=>{
  if(point&&location?.text){
   const lead=location.text.trim().split(/[。；!\n]/)[0]?.trim()||location.text.trim();
   return lead.length>76?`${lead.slice(0,76)}…`:lead;
  }
  return feedback;
 },[point,location,feedback]);
 return <main className={`studio rpg-studio is-anime${outfitPickerOpen?' outfit-picker-open':''}`} style={{'--tool-color':tool.color} as CSSProperties}>
  <header className="studio-header rpg-header">
   <div className="brand-block">
    <div className="brand" aria-hidden="true"><span className="brand-mark">經</span><span><span className="brand-name">經絡研習室</span><span className="brand-subtitle">中醫人體 · 動漫助教</span></span></div>
   </div>
   <div className="header-actions">
    <div className="model-switch" role="group" aria-label="角色切換">
     {ANIME_AVATARS.map(avatar=><button key={avatar.id} type="button" className={avatarId===avatar.id?'selected':''} aria-pressed={avatarId===avatar.id} onClick={()=>selectAvatar(avatar.id)}>{avatar.shortName}</button>)}
    </div>
    <div className="header-status"><span className="status-dot"/>穴位練習中<span className="header-divider"/>{currentAvatar.shortName}</div>
    <a className="roadmap-close-x" href={HOME_HREF} aria-label={BACK_HOME_ARIA}>×</a>
   </div>
  </header>

  <section className="scene-stage" aria-label="3D 人體教學視窗">
   <Suspense fallback={null}><CharacterScene key={avatarId} state={{...state,inspectorOpen:false}} simulation={simulation} avatarId={avatarId} outfitId={outfitId} onPoint={choosePoint} onToolDrag={onToolDrag} onProgress={onProgress} onFrameReady={onFrameReady} onError={onError} onPracticeOutfitPreparing={onPracticeOutfitPreparing}/></Suspense>
   <div className="scene-guide"><span className="guide-cross">+</span><span>{currentAvatar.shortName}{showOutfitBar?` · ${currentOutfit.shortName}`:''}</span></div>
   {loadingVisible&&(
    <div className={error?'loading-card woodcut-loading load-error':'loading-card woodcut-loading'} role="status">
     <span className="loading-symbol">{error?(<Info size={23}/>):(<Layers3 size={23}/>)}</span>
     <strong>{error?'模型暫未就緒':('正在迎接'+currentAvatar.shortName)}</strong>
     <p>{error||loadingHint}</p>
     {!error&&<><div className="load-track load-track-shimmer"><i style={{width:Math.max(progress,scenePainted?100:progress)+'%'}}/></div><small>{scenePainted?100:progress}%</small></>}
     {error&&<button className="text-button" onClick={()=>window.location.reload()}>重新加載</button>}
    </div>
   )}
   <div className="scene-mode-hint"><span className="tiny-dot"/>{animePointReady?'點選金色穴位 · 抓住工具移動 · 拖動旋轉 · 滾動縮放':avatarLessonNote(currentAvatar,simulation.pointCode)}</div>
  </section>

  <nav className="studio-tool-rail" aria-label="視角與工具">
   <button type="button" className="studio-tool-btn" aria-label={`人體視角：${VIEW_LABELS[state.view]}，點擊切換`} title={`視角 ${VIEW_LABELS[state.view]}`} onClick={cycleView}><ScanFace size={18} aria-hidden="true"/><span className="studio-tool-tag">{VIEW_LABELS[state.view]}</span></button>
   <button type="button" className="studio-tool-btn" aria-label="全身視角" title="全身" onClick={fullBody}><Maximize2 size={17} aria-hidden="true"/><span className="studio-tool-tag studio-tool-tag-desktop">全身</span></button>
   <button type="button" className="studio-tool-btn" aria-label="聚焦當前穴位" title="聚焦" disabled={!hasTarget} onClick={focusTarget}><Focus size={17} aria-hidden="true"/><span className="studio-tool-tag studio-tool-tag-desktop">聚焦</span></button>
   <button type="button" className={`studio-tool-btn${state.rotate?' is-active':''}`} aria-label={state.rotate?'停止自動旋轉':'自動旋轉'} aria-pressed={state.rotate} title={state.rotate?'停止旋轉':'自動旋轉'} onClick={()=>{pause();setState(s=>({...s,rotate:!s.rotate}));setFeedback(state.rotate?'自動旋轉已停止。':'正在自動旋轉。');}}><RotateCw size={17} aria-hidden="true"/><span className="studio-tool-tag studio-tool-tag-desktop">{state.rotate?'停轉':'旋轉'}</span></button>
   {showOutfitBar&&<div className="outfit-picker-anchor">
    {outfitPickerOpen&&<div className="outfit-bar" ref={outfitBarRef} role="group" aria-label="換裝">
     {avatarOutfitList.map(o=><button key={o.id} type="button" className={outfitId===o.id?'is-active':''} aria-pressed={outfitId===o.id} title={outfitPending&&outfitNeedsAltBuild(avatarId,o.id as OutfitId)?'準備中…':`${o.shortName} · ${o.blurb}`} disabled={outfitPending&&outfitId!==o.id} onClick={()=>switchOutfit(o.id as OutfitId)}>{o.shortName}</button>)}
    </div>}
    <button ref={outfitShirtRef} type="button" className={`studio-tool-btn${outfitPending?' is-pending':''}${outfitPickerOpen?' is-active':''}`} aria-label={outfitPending?'練習裝準備中':`換裝：${currentOutfit.shortName}，點擊開啟選單`} aria-expanded={outfitPickerOpen} aria-haspopup="true" title={outfitPending?'準備中…':`${currentOutfit.shortName} · ${currentOutfit.blurb??''}`} onClick={toggleOutfit}><Shirt size={17} aria-hidden="true"/><span className="studio-tool-tag studio-tool-tag-desktop studio-tool-tag-outfit">{currentOutfit.shortName}</span><span className="studio-tool-outfit-pip" aria-hidden="true">{currentOutfit.shortName}</span>{outfitPending&&<span className="studio-tool-pending" aria-hidden="true"/>}</button>
   </div>}
   <button type="button" className="studio-tool-btn" aria-label="來源與許可" title="來源與許可" onClick={()=>setAbout(true)}><Info size={17} aria-hidden="true"/><span className="studio-tool-tag studio-tool-tag-desktop">來源</span></button>
   <button type="button" className={`studio-tool-btn${mobilePanel==='library'?' is-active':''}`} aria-label="穴位庫" aria-expanded={mobilePanel==='library'} title="穴位庫" onClick={toggleLibrary}><Layers3 size={17} aria-hidden="true"/><span className="studio-tool-tag studio-tool-tag-desktop">穴庫</span></button>
  </nav>
  {outfitPending&&<p className="practice-outfit-hint" role="status" aria-live="polite">準備中…</p>}

  <aside className={`library-panel surface ${mobilePanel==='library'?'mobile-expanded':''}`} aria-label="穴位庫">
   <div className="library-window-bar">
    <span className="library-window-title">穴位庫</span>
    <button type="button" className="roadmap-close-x" aria-label="關閉穴位庫" onClick={toggleLibrary}>×</button>
   </div>
   <div className="panel-content">
    <div className="tab-body" id="points-panel">
     <div className="library-sticky">
      <div className="library-search-row">
       <label className="search-box"><Search size={15}/><input ref={searchInput} value={query} onChange={e=>setQuery(e.target.value)} placeholder="穴名、注音或編號" aria-label="搜索教學穴位"/>{query?<button aria-label="清空搜索" onClick={()=>setQuery('')}><X size={13}/></button>:<kbd>/</kbd>}</label>
       <button type="button" className="library-filter-btn" aria-expanded={libraryFilterOpen} aria-controls="library-filter-sheet" onClick={()=>setLibraryFilterOpen(v=>!v)}><Filter size={14} aria-hidden="true"/><span>篩選</span></button>
      </div>
      {libraryFilterOpen&&<div id="library-filter-sheet" className="library-filter-sheet" role="region" aria-label="篩選條件">
       <div className="section-label">選擇身體區域<button type="button" className={region==='all'?'current':''} onClick={()=>{setRegion('all');setFeedback('正在顯示全部已烘穴位。');}}>全部</button></div>
       <div className="region-grid">{REGIONS.map(r=><button type="button" className={region===r.id?'selected':''} key={r.id} aria-pressed={region===r.id} onClick={()=>{setRegion(r.id);setQuery('');if(avatarCodes.has(r.example))choosePoint(r.example);}}>{r.name}</button>)}</div>
       <div className="meridian-controls"><label>十四經脈<select aria-label="選擇經脈" value={simulation.meridianFilter??'all'} onChange={e=>{setRegion('all');setQuery('');setSimulation(s=>({...s,meridianFilter:e.target.value}));}}><option value="all">全部經脈 · {practiceCodes.size} 可練</option>{MERIDIANS.map(m=><option key={m.id} value={m.id}>{m.name}</option>)}</select></label><small>經脈體表連線暫未烘到角色；目錄保留供之後加穴。</small></div>
      </div>}
      <div className="curriculum-controls library-curriculum-scroll" role="group" aria-label="課堂基準穴">
       <button type="button" className={simulation.curriculumSet==='all'?'selected':''} aria-pressed={simulation.curriculumSet==='all'} onClick={selectCurriculumAll}>全部 {practiceCodes.size}</button>
       {CURRICULUM_SETS.filter(set=>set.id===ANIME_DEFAULT_SET||set.codes.some(c=>avatarCodes.has(c))).map(set=><button type="button" key={set.id} className={simulation.curriculumSet===set.id?'selected':''} aria-pressed={simulation.curriculumSet===set.id} onClick={()=>selectCurriculumLesson(set)}>{set.shortName}</button>)}
      </div>
     </div>
     <div className="point-list">{results.map(p=>{const badge=curriculumBadge(p.code);const deferred=isPracticeDeferred(p.code)||!avatarCodes.has(p.code);return <button key={p.code} type="button" className={`point-row ${p.code===simulation.pointCode?'selected':''} ${pointInCurriculum(p.code,activeLesson?.id)?'curriculum-point':''} ${p.reviewed&&!deferred?'reviewed-point':''} ${deferred?'is-deferred':''}`} onClick={()=>choosePoint(p.code)} aria-pressed={p.code===simulation.pointCode} aria-disabled={deferred||undefined} title={deferred?avatarLessonNote(currentAvatar,p.code):undefined}><span className="point-dot"/><span className="point-row-copy"><span className="point-name">{p.name}{badge&&<em className={`curriculum-badge ${deferred?'is-deferred':p.reviewed?'is-reviewed':''}`}>{badge}</em>}<span>{p.zhuyin}</span></span><small>{p.meridian}</small></span><span className="point-code">{p.code}</span></button>;})}{results.length===0&&<div className="empty-state"><Search size={20}/><p>未找到匹配穴位</p><button className="text-button" type="button" onClick={()=>{setQuery('');setRegion('all');setSimulation(s=>({...s,curriculumSet:'all',meridianFilter:'all'}));}}>查看全部已烘穴位</button></div>}</div>
     <div className="point-list-foot"><span>可練習 {practiceCodes.size} 穴 · 胸腹背腰切練習裝</span><button type="button" className="icon-button" aria-label={simulation.showPoints?'隱藏穴位點':'顯示穴位點'} aria-pressed={simulation.showPoints} onClick={()=>{setSimulation(s=>({...s,showPoints:!s.showPoints}));setFeedback(simulation.showPoints?'已隱藏穴位點。':'已顯示穴位點。');}}>{simulation.showPoints?<Eye size={15}/>:<EyeOff size={15}/>}</button></div>
    </div>
   </div>
  </aside>

  <section className={`acupoint-rpg-dialog surface${dialogExpanded?' is-expanded':''}`} aria-label="當前教學選擇">
   <div className="rpg-dialog-bar">
    <div className="rpg-dialog-primary">
     {point?<>
      <div className="rpg-dialog-headline">
       <span className="rpg-dialog-title">{selectedTitle}</span>
       <span className="rpg-dialog-meta"><span className="rpg-dialog-code">{point.code}</span><span className="rpg-dialog-zhuyin">{point.zhuyin}</span><span className="rpg-dialog-meridian">{point.meridian}</span></span>
      </div>
      <p className="rpg-dialog-blurb" role="status" aria-live="polite">{dialogBlurb}</p>
     </>:simulation.customTarget?<>
      <div className="rpg-dialog-headline"><span className="rpg-dialog-title">自選體表位置</span><span className="rpg-dialog-meta"><span className="rpg-dialog-code">體表</span></span></div>
      <p className="rpg-dialog-blurb" role="status" aria-live="polite">{dialogBlurb}</p>
     </>:<>
      <div className="rpg-dialog-headline"><span className="rpg-dialog-title">點選金色穴位開始</span></div>
      <p className="rpg-dialog-blurb" role="status" aria-live="polite">{dialogBlurb}</p>
     </>}
    </div>
    <div className="rpg-dialog-controls">
     <button type="button" className="rpg-nav-btn" disabled={!point} onClick={()=>stepLesson(-1)}>上一穴</button>
     <button type="button" className="rpg-nav-btn" disabled={!point} onClick={()=>stepLesson(1)}>下一穴</button>
     <button type="button" className="rpg-dialog-expand" aria-expanded={dialogExpanded} aria-label={dialogExpanded?'收合穴位詳情':'展開穴位詳情'} onClick={()=>setDialogExpanded(v=>!v)}><ChevronDown size={17} aria-hidden="true"/></button>
    </div>
   </div>
   {dialogExpanded&&<div className="rpg-dialog-body">
    <div className="selection-scroll">
     {point?<>
      <div className="side-control"><span>患者側別</span><div><button disabled={point.midline} className={simulation.side===1&&!point.midline?'selected':''} onClick={()=>chooseSide(1)} aria-pressed={simulation.side===1&&!point.midline}>左</button><button disabled={point.midline} className={simulation.side===-1&&!point.midline?'selected':''} onClick={()=>chooseSide(-1)} aria-pressed={simulation.side===-1&&!point.midline}>右</button></div>{point.midline&&<small>正中線</small>}</div>
      <div className="location-card"><h2><span className="section-dash"/>標準定位</h2>{location?<><p>{location.text}</p>{location.curriculum?<p className="curriculum-source">{location.curriculum}</p>:null}{location.classic?<p className="classic-source">{location.classic}</p>:null}{muscleNear?<p className="acupoint-muscle-near"><Info size={13} aria-hidden="true"/>鄰近肌肉：{muscleNear}</p>:null}{location.section?<a href={STANDARD} target="_blank" rel="noreferrer">國標條款 {location.section}<ArrowUpRight size={12}/></a>:null}<small>“寸”為身體分段比例，不是固定釐米。</small></>:<><p>本穴已收錄至教學目錄。標準定位摘要待逐條核錄，請對照原文學習。</p><a href={point.source||STANDARD} target="_blank" rel="noreferrer">查閱定位來源<ArrowUpRight size={12}/></a></>}</div>
      {lessonProgress&&<p className="lesson-progress-inline">{lessonProgress.name} · 第 {lessonProgress.index}／{lessonProgress.total} 穴</p>}
      <div className={`review-note ${animePointReady?'is-reviewed':''}`}><span className="review-dot"/><span>{animePointReady?`${currentAvatar.shortName}已對應並完成檢查`:'尚未烘到角色'}<small>動漫視圖標記為教學示意。</small></span></div>
     </>:simulation.customTarget?<div className="pain-card"><div className="pain-target"><MapPin size={23}/><Check size={12}/></div><h2>當前工具接觸位置</h2><p>拖動器具或「移動工具」手柄，在皮膚上自由操作。</p><small>自主教學選擇，不自動推薦療法。</small></div>:<div className="pain-card"><div className="pain-target"><MapPin size={23}/></div><h2>點選金色穴位開始</h2><p>選中穴位後可拖動針具或移動手柄練習。</p></div>}
     <div className="tool-summary"><span className="tool-summary-icon"><ToolGlyph tool={tool.id}/></span><span className="tool-summary-name">{tool.name}</span></div>
    </div>
   </div>}
  </section>
  <dialog ref={dialog} className="source-dialog" onCancel={()=>setAbout(false)} onClose={()=>setAbout(false)} onClick={e=>{if(e.target===e.currentTarget){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)setAbout(false);}}} aria-labelledby="source-title">
   <div className="dialog-heading"><span className="eyebrow">資料、許可與當前邊界</span><button className="icon-button" onClick={()=>setAbout(false)} aria-label="關閉來源與許可" autoFocus><X size={19}/></button></div>
   <h2 id="source-title">讓每一層，有據可查。</h2><p className="dialog-intro">這裡整理了經絡研習室引用的標準、角色與教具來源，方便課堂查證與討論。</p>
   <section><h3>穴位定位</h3><p>以 GB/T 12346—2021《經穴名稱與定位》為主要核查來源。研習室目前只在角色身上顯示已對位完成的穴位（頭頸 21 穴、頭胸腹背腰 30 穴，共 51 穴；含人迎、天突）。其餘經穴名稱仍在我們的教學目錄中，會依課堂進度陸續烘上並對到角色皮膚。</p><a href={STANDARD} target="_blank" rel="noreferrer">查閱國家標準正文<ArrowUpRight size={13}/></a></section>
   <section><h3>原本角色（CC0）</h3><p>Sendagaya Shino 為 VRoid Studio β 版樣本（pixiv Inc.，CC0 1.0）。換裝可切「制服」與「練習裝」（深青運動內衣＋高腰短褲、高包頭）；胸腹背腰與人迎／天突請用練習裝，針與指壓打在真實皮膚上。</p><a href="https://creativecommons.org/publicdomain/zero/1.0/" target="_blank" rel="noreferrer">CC0 1.0 說明<ArrowUpRight size={13}/></a></section>
   <section><h3>成年角色（VRoid 官方樣本）</h3><p>AvatarSample_A 依 <a href="https://vroid.pixiv.help/hc/en-us/articles/4402394424089-VRoidPreset-A-Z" target="_blank" rel="noreferrer">VRoid 官方樣本利用條件</a> 使用與改作（<strong>不是 CC0</strong>，著作權未放棄）。可營利／非營利使用與免費再發布；不可當成 CC0、不可販售模型檔，也不可讓人誤以為 pixiv 官方背書本產品。髮色為研習室改作（純黑側分瀏海）。換裝可切套裝、休閒、練習裝；薄紗僅手動選取，重新整理後回到套裝。</p><a href="https://hub.vroid.com/en/characters/2843975675147313744/models/5644550979324015604" target="_blank" rel="noreferrer">官方介紹頁<ArrowUpRight size={13}/></a><a href={publicAssetUrl('/ATTRIBUTION.md')} target="_blank" rel="noreferrer">署名細節<ArrowUpRight size={13}/></a></section>
   <section><h3>工具與局部反饋</h3><p>針具接觸與指壓形變為教學視覺示意，不預測療效，也不提供真人操作參數。</p><a href="https://www.nccih.nih.gov/health/acupuncture-effectiveness-and-safety" target="_blank" rel="noreferrer">NCCIH：針灸資料<ArrowUpRight size={13}/></a><a href="https://www.mskcc.org/cancer-care/patient-education/acupressure-pain-and-headaches" target="_blank" rel="noreferrer">MSK：指壓資料<ArrowUpRight size={13}/></a></section>
   <button className="dialog-done" onClick={()=>setAbout(false)}>返回研習室</button>
  </dialog>
 </main>;
}
