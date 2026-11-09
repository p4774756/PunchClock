import {PainPanel} from './pain-panel';
import {painCase,painRelationCodes,matchPainRegion,type PainTopicId} from './pain-relations';
import {publicAssetUrl} from './public-assets';
import {lazy,Suspense,useCallback,useEffect,useMemo,useRef,useState,type CSSProperties,type PointerEvent as ReactPointerEvent} from 'react';
import {ArrowUpRight,Check,ChevronDown,Eye,EyeOff,Focus,Info,Layers3,MapPin,Maximize2,MousePointer2,Pause,Play,RotateCcw,RotateCw,Search,SlidersHorizontal,X} from 'lucide-react';
import {SYSTEMS,type Atlas,type SceneState,type SystemId,type View} from './anatomy';
import {CURRICULUM_SETS,curriculumBadge,curriculumSet,initialSimulation,LOCATION,POINTS,POINT_BY_CODE,MERIDIANS,pointInCurriculum,REGIONS,STANDARD,SYSTEM_ZH,TOOLS,type CurriculumSetId,type RegionId,type SimulationState,type SurfaceTarget,type ToolId,type ToolDragPhase} from './teaching';

const AnatomyScene=lazy(()=>import('./scene'));

const INITIAL_VISIBLE:SystemId[]=['integumentary'];
const initialScene:SceneState={explode:0,visible:INITIAL_VISIBLE,selected:[],isolate:false,view:'three-quarter',rotate:false,reset:0};
const VIEW_LABELS:Record<View,string>={'three-quarter':'斜視',front:'正面',back:'背面',side:'側面'};
const clean=(value:string)=>value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[\s\-_]/g,'');
const HOME_HREF=import.meta.env.BASE_URL.startsWith('/atlas')?'/':'https://tcm.jic.io/';
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
  {tool==='moxa'&&<><path d="m11 29 16-16 5 5-16 16-5-5Z"/><path d="m11 29 5 5M27 13l5 5M12 28l5 5"/><path d="M28 9c-4-3 4-5 1-8M34 14c-3-3 4-4 2-7"/><path d="m9 34-3 1m1-6-3-1"/></>}
  {tool==='stone'&&<><path d="M7 25c0-8 8-17 16-16 7 1 12 8 10 15-2 7-13 11-21 8-3-1-5-4-5-7Z"/><path d="M13 21c1-3 4-6 7-7M27 27l2-2"/></>}
  {tool==='finger'&&<><path d="M15 33 9 23c-2-4 1-6 4-3l3 4V8c0-4 6-4 6 0v12l3-2c2-1 3 0 4 1l2 2c2 1 2 4 1 7l-2 6H17"/><path d="M22 20v5m4-5v5m4-2v3M17 36h12"/></>}
  {tool==='scraper'&&<><path d="M9 9c3-3 6-1 8 1l4 3c4 2 5-7 10-4 5 4 2 10-1 15l-7 10c-2 3-5 1-7-2l-6-12c-2-4-4-8-1-11Z"/><path d="m11 13 10 19"/></>}
  {tool==='cup'&&<><path d="M12 30c-1-6-6-9-4-16C10 5 30 5 32 14c2 7-3 10-4 16"/><path d="M12 30h16v4H12zM13 16c0-4 3-6 6-6m2 7v9m-4-4 4 4 4-4"/></>}
 </svg>;
}

export default function Home(){
 const launch=useMemo(()=>readLaunchParams(),[]);
 const [atlas,setAtlas]=useState<Atlas|null>(null);
 const [state,setState]=useState<SceneState>(()=>launch.set||launch.code?{...initialScene,view:'front'}:initialScene);
 const [simulation,setSimulation]=useState<SimulationState>(()=>{
  const set=launch.set;
  const lesson=set?curriculumSet(set):undefined;
  const code=launch.code??lesson?.codes[0]??initialSimulation.pointCode;
  return {...initialSimulation,curriculumSet:set??'all',pointCode:code,side:1,focus:set||launch.code?1:0};
 });
 const [progress,setProgress]=useState(0),[error,setError]=useState('');
 const [region,setRegion]=useState<RegionId|'all'>('all'),[query,setQuery]=useState('');
 const [libraryTab,setLibraryTab]=useState<'points'|'layers'>('points');
 const [toolsExpanded,setToolsExpanded]=useState(false);
 const [mobilePanel,setMobilePanel]=useState<'library'|'selection'|null>(null);
 const [about,setAbout]=useState(false),[holding,setHolding]=useState(false);
 const [feedback,setFeedback]=useState(()=>{
  const lesson=curriculumSet(launch.set);
  if(lesson)return `課堂：${lesson.name}（${lesson.codes.length} 穴）。可依教材順序上一穴／下一穴。`;
  if(launch.code)return `已開啟穴位 ${launch.code}。`;
  return '選擇穴位與工具，開始一次課堂演示。';
 });
 const dialog=useRef<HTMLDialogElement>(null),searchInput=useRef<HTMLInputElement>(null);
 const heldPointer=useRef<number|null>(null);
 const draggedPoint=useRef<{code:string;name:string}|null>(null);
 const bootedUrl=useRef(false);
 const point=POINTS.find(p=>p.code===simulation.pointCode);
 const tool=TOOLS.find(t=>t.id===simulation.tool)!;
 const activeLesson=curriculumSet(simulation.curriculumSet);
 const hasTarget=!!point||!!simulation.customTarget||!!simulation.pain?.topic||!!simulation.pain?.target;
 const canPlay=hasTarget&&progress===100&&!error&&state.explode===0&&simulation.pickMode!=='anatomy';
 const location=point?LOCATION[point.code]:null;
 const selectedPart=atlas?.parts.find(p=>p.id===state.selected[0]);
 const results=useMemo(()=>{
  const c=painCase(simulation.pain?.topic),relations=c?painRelationCodes(c):null,codes=relations?[...relations.local,...relations.distal]:null;
  const lesson=curriculumSet(simulation.curriculumSet);
  const ordered=lesson?lesson.codes.map(code=>POINTS.find(p=>p.code===code)!).filter(Boolean):POINTS;
  return ordered.filter(p=>(!simulation.meridianFilter||simulation.meridianFilter==='all'||p.meridianId===simulation.meridianFilter)&&(!codes||codes.includes(p.code))&&(region==='all'||p.region===region)&&(!query||clean(`${p.code} ${p.name} ${p.pinyin}`).includes(clean(query))));
 },[query,region,simulation.pain?.topic,simulation.meridianFilter,simulation.curriculumSet]);
 const counts=useMemo(()=>Object.fromEntries(SYSTEMS.map(s=>[s.id,atlas?.parts.filter(p=>p.system===s.id).length??0])),[atlas]);
 const visibleCount=useMemo(()=>atlas?.parts.filter(p=>state.visible.includes(p.system)).length??0,[atlas,state.visible]);

 useEffect(()=>{const abort=new AbortController();fetch(publicAssetUrl('/models/atlas.json'),{signal:abort.signal}).then(response=>{if(!response.ok)throw new Error('人體模型目錄加載失敗，請刷新重試。');return response.json();}).then(data=>setAtlas(data as Atlas)).catch(e=>{if(e.name!=='AbortError')setError('人體模型目錄加載失敗，請檢查網絡後刷新重試。');});return()=>abort.abort();},[]);
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
 useEffect(()=>{const key=(event:KeyboardEvent)=>{if(event.key==='Escape'){setAbout(false);setMobilePanel(null);pause();}if(event.key==='/'&&!(event.target instanceof HTMLInputElement)&&!(event.target instanceof HTMLTextAreaElement)){event.preventDefault();setLibraryTab('points');setMobilePanel('library');searchInput.current?.focus();}};window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);},[pause]);

 const prepareFocus=()=>setState(s=>({...s,explode:0,rotate:false,isolate:false,selected:[]}));
 const choosePoint=(code:string,side:1|-1=simulation.side)=>{const next=POINTS.find(p=>p.code===code);if(!next)return;const related=painCase(simulation.pain?.topic);if(related){const relations=painRelationCodes(related);if([...relations.local,...relations.distal].includes(code)&&side===simulation.pain?.side){chooseRelatedPoint(code);return;}}prepareFocus();setHolding(false);heldPointer.current=null;setSimulation(s=>({...s,pointCode:code,side:next.midline?1:side,customTarget:null,dragging:false,running:false,elapsed:0,focus:s.focus+1,pickMode:'point'}));setFeedback(`已選擇${next.name} ${next.code}，可查看定位並選擇演示工具。`);setMobilePanel(null);};
 const chooseTool=(id:ToolId)=>{setHolding(false);heldPointer.current=null;setSimulation(s=>({...s,tool:id,dragging:false,running:false,elapsed:0}));setFeedback(`已切換為${TOOLS.find(t=>t.id===id)!.name}，演示回到起點。`);};
 const chooseSide=(side:1|-1)=>{if(point?.midline)return;prepareFocus();setSimulation(s=>({...s,side,dragging:false,running:false,elapsed:0,focus:s.focus+1}));setFeedback(`已切換到患者${side===1?'左':'右'}側。`);};
 const changeMode=(mode:SimulationState['pickMode'])=>{pause();setState(s=>({...s,explode:0,rotate:false,isolate:false,selected:[]}));setSimulation(s=>({...s,pickMode:mode,dragging:false,running:false,elapsed:0,...(mode==='pain'?{pointCode:null,customTarget:null,pain:undefined}:{}),...(mode==='anatomy'?{pain:undefined}:{})}));setFeedback(mode==='pain'?'點選人體痛處，或選擇頭痛、頸部痛、腰痛、膝痛課堂示例。':mode==='anatomy'?'點選可見解剖結構，觀察它與體表的位置關係。':'點擊教學點，或在左側查找穴位。');};
 const onSurface=(target:SurfaceTarget)=>{prepareFocus();setRegion('all');setQuery('');const topic=matchPainRegion(target.position);setSimulation(s=>({...s,customTarget:target,pointCode:null,side:target.position[0]>=0?1:-1,pain:{topic,target,side:target.position[0]>=0?1:-1,level:s.pain?.level??.5,showLinks:true},dragging:false,running:false,elapsed:0,pickMode:'pain'}));setMobilePanel('selection');setFeedback(topic?'痛處已標記：可查看局部與遠端穴位的教學關聯。':'痛處已保留，此區域尚無關聯資料，不自動生成配穴。');};
 const choosePainCase=(id:PainTopicId,side:1|-1=simulation.pain?.side??simulation.side)=>{pause();setRegion('all');setQuery('');const c=painCase(id)!;setState(s=>({...s,visible:['integumentary'],explode:0,isolate:false,selected:[],rotate:false,view:c.view,reset:s.reset+1}));setSimulation(s=>({...s,pain:{topic:id,target:null,side,level:s.pain?.level??.5,showLinks:true},pointCode:null,customTarget:null,side,pickMode:'pain',elapsed:0}));setMobilePanel('selection');setFeedback(`${c.name}示例：紅色標記痛處，金色為局部穴位，藍色為遠端關聯。`);};
 const relationOverview=()=>{pause();setState(s=>({...s,explode:0,isolate:false,selected:[],visible:['integumentary'],rotate:false,view:painCase(simulation.pain?.topic)?.view??s.view,reset:s.reset+1}));};
 const chooseRelatedPoint=(code:string)=>{relationOverview();setSimulation(s=>({...s,pointCode:code,customTarget:null,side:s.pain?.side??s.side,pickMode:'point',elapsed:0}));setFeedback(`已選擇${POINTS.find(p=>p.code===code)!.name}，保留原痛處；連線說明關聯，不代表必然止痛。`);};
 const clearPain=()=>{pause();setSimulation(s=>({...s,pain:undefined,pickMode:'point'}));setFeedback('已退出疼痛關聯，恢復普通穴位與工具演示。');};
 const onToolDrag=useCallback((target:SurfaceTarget|null,phase:ToolDragPhase)=>{
  if(phase==='start'){draggedPoint.current=null;setState(s=>({...s,rotate:false}));setSimulation(s=>({...s,dragging:true,running:true,elapsed:Math.max(.01,s.elapsed)}));setFeedback('正在操作工具：拖到穴位附近可自動選中。');}
  else if(phase==='move'&&target){const selected=target.pointCode?POINTS.find(p=>p.code===target.pointCode):null;draggedPoint.current=selected?{code:selected.code,name:selected.name}:null;setSimulation(s=>selected?{...s,customTarget:null,pointCode:selected.code,side:selected.midline?1:(target.pointSide??s.side),pickMode:'point',dragging:true}:{...s,customTarget:target,pointCode:null,pickMode:'point',dragging:true});}
  else if(phase==='end'||phase==='cancel'){setSimulation(s=>({...s,dragging:false,running:false,release:(s.release??0)+1}));const selected=draggedPoint.current;setFeedback(phase==='cancel'?'工具移動已結束，已恢復人體旋轉。':selected?`已選中${selected.name} ${selected.code}，可繼續演示。`:'工具已停在新的皮膚位置，可繼續拖動或播放演示。');draggedPoint.current=null;}
 },[]);
 const onSelect=(id:string)=>{pause();setState(s=>({...s,selected:[id],rotate:false,isolate:false}));setFeedback('已選擇解剖結構，可查看所屬系統或單獨觀察。');};
 const toggleLayer=(id:SystemId)=>{pause('解剖層已更新，演示暫停。');setState(s=>({...s,selected:[],isolate:false,visible:s.visible.includes(id)?s.visible.filter(v=>v!==id):[...s.visible,id]}));};
 const preset=(visible:SystemId[],label:string)=>{pause(`已切換到${label}。`);setState(s=>({...s,visible,selected:[],isolate:false}));};
 const changeView=(view:View)=>{pause(`已切換為${VIEW_LABELS[view]}。`);setState(s=>({...s,view,rotate:false,reset:s.reset+1}));};
 const fullBody=()=>{pause('已回到全身視角。');setState(s=>({...s,explode:0,isolate:false,selected:[],rotate:false,reset:s.reset+1}));};
 const focusTarget=()=>{if(!hasTarget)return;prepareFocus();pause('已聚焦當前教學位置。');setSimulation(s=>({...s,focus:s.focus+1}));};
 const play=(restart=false)=>{if(!canPlay)return;setState(s=>({...s,explode:0,rotate:false,isolate:false}));setSimulation(s=>({...s,running:true,elapsed:restart?0:s.elapsed,focus:s.pain?s.focus:s.focus+1}));setFeedback(`${tool.name}演示進行中：${tool.effect}。`);};
 const startHold=(event:ReactPointerEvent<HTMLButtonElement>)=>{if(!canPlay)return;event.preventDefault();event.currentTarget.setPointerCapture(event.pointerId);heldPointer.current=event.pointerId;setHolding(true);play();};
 const endHold=(event:ReactPointerEvent<HTMLButtonElement>)=>{if(heldPointer.current!==event.pointerId)return;heldPointer.current=null;if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId);releaseHold();};
 const onProgress=useCallback((value:number)=>{setProgress(value);if(value===100)setError('');},[]);
 const onError=useCallback((_message:string)=>{setError('3D 模型加載出現問題，請檢查網絡與瀏覽器 WebGL 支持後重試。');setSimulation(s=>({...s,dragging:false,running:false}));},[]);
 const selectedTitle=simulation.pickMode==='anatomy'?'解剖結構':point?.name??(simulation.pain?(painCase(simulation.pain.topic)?.name??'自選痛處'):simulation.customTarget?'自選體表位置':'點選人體表面');
 const animationStatus=simulation.dragging?'操作中':simulation.running?'演示中':simulation.elapsed>0?'已暫停':'待演示';

 return <main className="studio" style={{'--tool-color':tool.color} as CSSProperties}>
  <header className="studio-header">
   <a className="brand" href={HOME_HREF} aria-label={HOME_HREF==='/'?'回店面':'返回中醫首頁'}><span className="brand-mark">經</span><span><span className="brand-name">中醫人體<span className="edition">教學臺</span></span><span className="brand-subtitle">{HOME_HREF==='/'?'PunchClock · 穴位與工具演示':'JIC 中醫 · 穴位與工具演示'}</span></span></a>
   <div className="header-status"><span className="status-dot"/>工程教學預覽<span className="header-divider"/>成人男性參考模型</div>
   <button className="source-button" onClick={()=>setAbout(true)}><Info size={16}/><span>來源與許可</span></button>
  </header>

  <section className="scene-stage" aria-label="3D 人體教學視窗">
   {atlas&&<Suspense fallback={null}><AnatomyScene atlas={atlas} state={{...state,inspectorOpen:false}} simulation={simulation} onSelect={onSelect} onPoint={choosePoint} onSurface={onSurface} onToolDrag={onToolDrag} onProgress={onProgress} onError={onError}/></Suspense>}
   <div className="scene-guide"><span className="guide-cross">+</span><span>成人男性 · 體表參考</span></div>
   {(progress<100||error)&&<div className={`loading-card ${error?'load-error':''}`} role="status"><span className="loading-symbol">{error?<Info size={23}/>:<Layers3 size={23}/>}</span><strong>{error?'模型暫未就緒':'正在加載人體解剖'}</strong><p>{error||'正在準備真實解剖結構與教學點。'}</p>{!error&&<><div className="load-track"><i style={{width:`${progress}%`}}/></div><small>{progress}%</small></>}{error&&<button className="text-button" onClick={()=>window.location.reload()}>重新加載</button>}</div>}
   <div className={`scene-mode-hint ${simulation.pickMode==='pain'?'pain-hint':''}`}><span className="tiny-dot"/>{simulation.pain?'紅色：痛處 · 金色：局部 · 藍色：遠端 · 虛線僅表示關聯':simulation.pickMode==='pain'?'點選人體上的痛處，或選擇課堂示例':simulation.pickMode==='anatomy'?'點擊解剖結構，觀察層次關係':'抓住工具移動 · 拖動其他位置旋轉 · 滾動縮放'}</div>
  </section>

  <nav className="camera-bar surface" aria-label="人體視角">
   {(['front','back','side','three-quarter'] as View[]).map(view=><button key={view} className={state.view===view?'selected':''} aria-pressed={state.view===view} onClick={()=>changeView(view)}>{VIEW_LABELS[view]}</button>)}
   <span className="control-divider"/><button onClick={fullBody} title="顯示全身"><Maximize2 size={14}/><span>全身</span></button><button onClick={focusTarget} disabled={!hasTarget} title="聚焦當前穴位或痛處"><Focus size={14}/><span>聚焦</span></button><button className={`icon-button ${state.rotate?'selected':''}`} aria-label={state.rotate?'停止旋轉':'自動旋轉'} aria-pressed={state.rotate} onClick={()=>{pause();setState(s=>({...s,rotate:!s.rotate}));setFeedback(state.rotate?'自動旋轉已停止。':'正在自動旋轉人體。');}}><RotateCw size={15}/></button>
  </nav>

  <button className={`pain-entry ${simulation.pain||simulation.pickMode==='pain'?'active':''}`} aria-pressed={!!simulation.pain||simulation.pickMode==='pain'} onClick={()=>{changeMode('pain');setMobilePanel('selection');}}>◎ 疼痛關聯</button>

  <aside className={`library-panel surface ${mobilePanel==='library'?'mobile-expanded':''}`} aria-label="穴位與解剖層">
   <button className="mobile-panel-heading" aria-expanded={mobilePanel==='library'} onClick={()=>setMobilePanel(p=>p==='library'?null:'library')}><span><Layers3 size={16}/>穴位與解剖層</span><ChevronDown size={17}/></button>
   <div className="panel-content">
    <div className="panel-tabs" role="tablist" aria-label="學習內容"><button role="tab" aria-selected={libraryTab==='points'} className={libraryTab==='points'?'selected':''} onClick={()=>setLibraryTab('points')} id="points-tab" aria-controls="points-panel">穴位<span>{POINTS.length}</span></button><button role="tab" aria-selected={libraryTab==='layers'} className={libraryTab==='layers'?'selected':''} onClick={()=>setLibraryTab('layers')} id="layers-tab" aria-controls="layers-panel">取穴參考<span>15</span></button></div>
    {libraryTab==='points'?<div className="tab-body" id="points-panel" role="tabpanel" aria-labelledby="points-tab">
     <label className="search-box"><Search size={15}/><input ref={searchInput} value={query} onChange={e=>setQuery(e.target.value)} placeholder="穴名、拼音或編號" aria-label="搜索教學穴位"/>{query?<button aria-label="清空搜索" onClick={()=>setQuery('')}><X size={13}/></button>:<kbd>/</kbd>}</label>
     <div className="curriculum-controls" role="group" aria-label="課堂基準穴">{CURRICULUM_SETS.map(set=><button key={set.id} className={simulation.curriculumSet===set.id?'selected':''} aria-pressed={simulation.curriculumSet===set.id} onClick={()=>{const on=simulation.curriculumSet!==set.id;setRegion('all');setQuery('');prepareFocus();setSimulation(s=>({...s,curriculumSet:on?set.id:'all',meridianFilter:'all',pain:undefined,pointCode:on?set.codes[0]:s.pointCode,side:1,customTarget:null,dragging:false,running:false,elapsed:0,focus:s.focus+1,pickMode:'point'}));setFeedback(on?`課堂：${set.name}。依教材順序校正與演示。`:'已退出課堂篩選，顯示全部穴位。');}}>{set.shortName}<small>{set.codes.length}</small></button>)}{activeLesson&&<button className="curriculum-clear" onClick={()=>{setSimulation(s=>({...s,curriculumSet:'all'}));setFeedback('已退出課堂篩選，顯示全部穴位。');}}>全部穴位</button>}</div>
     {activeLesson&&<p className="curriculum-note">{activeLesson.lesson}{activeLesson.status==='reviewed'?' · 已課堂核對':' · 座標待核對'}</p>}
     <details className="region-disclosure"><summary>按身體區域篩選</summary><div className="section-label">選擇身體區域<button className={region==='all'?'current':''} onClick={()=>{setRegion('all');setFeedback('正在顯示全部教學點。');}}>全部</button></div>
     <div className="region-grid">{REGIONS.map(r=><button className={region===r.id?'selected':''} key={r.id} aria-pressed={region===r.id} onClick={()=>{setRegion(r.id);setQuery('');choosePoint(r.example);}}>{r.name}</button>)}</div>
     </details><div className="meridian-controls"><label>十四經脈<select aria-label="選擇經脈" value={simulation.meridianFilter??'all'} onChange={e=>{setRegion('all');setQuery('');setSimulation(s=>({...s,meridianFilter:e.target.value,pain:undefined}));}}><option value="all">全部經脈 · {POINTS.length} 穴</option>{MERIDIANS.map(m=><option key={m.id} value={m.id}>{m.name} · {m.count} 穴</option>)}</select></label><label className="meridian-toggle"><input type="checkbox" checked={!!simulation.showMeridians} onChange={()=>setSimulation(s=>({...s,showMeridians:!s.showMeridians}))}/>顯示經脈體表連線</label><small>依穴序分段示意；不代表深部循行。奇穴、耳穴另屬其他體系。</small></div>
     <div className="list-heading"><span>{simulation.pain?.topic?'關聯穴位':activeLesson?activeLesson.name:'定位學習入口'}</span><span>{results.length} 穴</span></div>
     <div className="point-list">{results.map(p=>{const badge=curriculumBadge(p.code);return <button key={p.code} className={`point-row ${p.code===simulation.pointCode?'selected':''} ${pointInCurriculum(p.code,activeLesson?.id)?'curriculum-point':''} ${p.reviewed?'reviewed-point':''}`} onClick={()=>choosePoint(p.code)} aria-pressed={p.code===simulation.pointCode}><span className="point-dot"/><span className="point-row-copy"><span className="point-name">{p.name}{badge&&<em className={`curriculum-badge ${p.reviewed?'is-reviewed':''}`}>{badge}</em>}<span>{p.pinyin}</span></span><small>{p.meridian}</small></span><span className="point-code">{p.code}</span></button>;})}{results.length===0&&<div className="empty-state"><Search size={20}/><p>未找到匹配穴位</p><button className="text-button" onClick={()=>{setQuery('');setRegion('all');setSimulation(s=>({...s,curriculumSet:'all'}));}}>查看全部教學點</button></div>}</div>
     <div className="point-list-foot"><span>{activeLesson?`${activeLesson.shortName} · ${activeLesson.status==='reviewed'?'已校正':'待校正'}`:`${POINTS.length} 穴工程示意座標`}</span><button className="icon-button" aria-label={simulation.showPoints?'隱藏穴位點':'顯示穴位點'} aria-pressed={simulation.showPoints} onClick={()=>{setSimulation(s=>({...s,showPoints:!s.showPoints}));setFeedback(simulation.showPoints?'已隱藏穴位點。':'已顯示穴位點。');}}>{simulation.showPoints?<Eye size={15}/>:<EyeOff size={15}/>}</button></div>
    </div>:<div className="tab-body" id="layers-panel" role="tabpanel" aria-labelledby="layers-tab">
     <div className="layer-presets"><button onClick={()=>preset(INITIAL_VISIBLE,'中醫銅人體表')}>體表參考</button><button onClick={()=>preset(['skeletal'],'骨骼層')}>骨骼</button><button onClick={()=>preset(SYSTEMS.map(s=>s.id),'全部解剖層')}>全部</button></div>
     <details className="anatomy-details"><summary>取穴參考 · 輔助解剖<span>15 層</span></summary><div className="system-list">{SYSTEMS.map(s=><label className={`system-row ${state.visible.includes(s.id)?'enabled':''}`} key={s.id}><span className="system-dot" style={{background:s.color}}/><span className="system-label">{SYSTEM_ZH[s.id]}</span><span className="system-count">{counts[s.id]||0}</span><input type="checkbox" checked={state.visible.includes(s.id)} onChange={()=>toggleLayer(s.id)} aria-label={`顯示${SYSTEM_ZH[s.id]}`}/><span className="switch-track" aria-hidden="true"/></label>)}</div></details>
     <div className="point-list-foot"><span>{visibleCount.toLocaleString()} 個結構可見</span><button className="text-button" onClick={()=>preset([],'全部隱藏')}>隱藏全部</button></div>
    </div>}
   </div>
  </aside>

  <aside className={`selection-panel surface ${mobilePanel==='selection'?'mobile-expanded':''}`} aria-label="當前教學選擇">
   <button className="mobile-panel-heading" aria-expanded={mobilePanel==='selection'} onClick={()=>setMobilePanel(p=>p==='selection'?null:'selection')}><span><MapPin size={16}/>{selectedTitle}<small>{point?.code}</small></span><ChevronDown size={17}/></button>
   <div className="panel-content">
    <div className="selection-eyebrow"><span>當前選擇</span><span className="selection-number">{point?.code??(simulation.pain?'痛處關聯':simulation.customTarget?'體表位置':simulation.pickMode==='pain'?'體表標記':'解剖參考')}</span></div>
    <div className="selection-title"><h1>{selectedTitle}</h1>{point&&<p>{point.pinyin}<span>·</span>{point.meridian}</p>}</div>
    <div className="mode-control" aria-label="選擇方式">{([{id:'point',label:'選穴',icon:MapPin},{id:'pain',label:'點選痛處',icon:MousePointer2},{id:'anatomy',label:'解剖',icon:Layers3}] as const).map(mode=><button key={mode.id} className={simulation.pickMode===mode.id?'selected':''} aria-pressed={simulation.pickMode===mode.id} onClick={()=>changeMode(mode.id)}><mode.icon size={13}/>{mode.label}</button>)}</div>
    <div className="selection-scroll">
     {(simulation.pain||simulation.pickMode==='pain')&&<PainPanel pain={simulation.pain} selected={simulation.pointCode} onCase={choosePainCase} onPoint={chooseRelatedPoint} onOverview={relationOverview} onClear={clearPain} onUpdate={next=>setSimulation(s=>({...s,pain:s.pain?{...s.pain,...next}:undefined}))}/>}
     {simulation.pickMode==='anatomy'?<div className="location-card"><h2>解剖觀察</h2>{selectedPart?<><p>{SYSTEM_ZH[selectedPart.system]} · 已選結構</p><div className="anatomy-name">{selectedPart.name}</div><button className={`outline-button ${state.isolate?'selected':''}`} onClick={()=>{pause();setState(s=>({...s,isolate:!s.isolate,rotate:false}));setFeedback(state.isolate?'已恢復其他可見結構。':'正在單獨觀察所選解剖結構。');}}>{state.isolate?'恢復其他結構':'單獨觀察此結構'}</button></>:<p>點選人體或拆解後的結構，查看它所屬的解剖系統。</p>}</div>:point?<>
      <div className="side-control"><span>患者側別</span><div><button disabled={point.midline} className={simulation.side===1&&!point.midline?'selected':''} onClick={()=>chooseSide(1)} aria-pressed={simulation.side===1&&!point.midline}>左</button><button disabled={point.midline} className={simulation.side===-1&&!point.midline?'selected':''} onClick={()=>chooseSide(-1)} aria-pressed={simulation.side===-1&&!point.midline}>右</button></div>{point.midline&&<small>正中線</small>}</div>
      <div className="location-card"><h2><span className="section-dash"/>標準定位</h2>{location?<><p>{location.text}</p>{location.curriculum?<p className="curriculum-source">{location.curriculum}</p>:null}{location.classic?<p className="classic-source">{location.classic}</p>:null}{location.section?<a href={STANDARD} target="_blank" rel="noreferrer">國標條款 {location.section}<ArrowUpRight size={12}/></a>:null}<small>“寸”為身體分段比例，不是固定釐米。</small></>:<><p>本穴已收錄至教學目錄。標準定位摘要待逐條核錄，請對照原文學習。</p><a href={point.source||STANDARD} target="_blank" rel="noreferrer">查閱定位來源<ArrowUpRight size={12}/></a></>}</div>
      <div className="meridian-navigation"><button onClick={()=>{const list=activeLesson?activeLesson.codes.map(code=>POINT_BY_CODE.get(code)!).filter(Boolean):POINTS.filter(p=>p.meridianId===point.meridianId);const i=list.findIndex(p=>p.code===point.code);choosePoint(list[(i-1+list.length)%list.length].code);}}>{activeLesson?'上一組內穴':'上一穴'}</button><button onClick={()=>{const list=activeLesson?activeLesson.codes.map(code=>POINT_BY_CODE.get(code)!).filter(Boolean):POINTS.filter(p=>p.meridianId===point.meridianId);const i=list.findIndex(p=>p.code===point.code);choosePoint(list[(i+1)%list.length].code);}}>{activeLesson?'下一組內穴':'下一穴'}</button></div>{point.surfaceUnavailable&&<p className="review-note">本穴位於口內，當前體表模式僅提供目錄，不顯示皮膚標記或器具。</p>}<div className={`review-note ${point.reviewed?'is-reviewed':''}`}><span className="review-dot"/><span>{point.reviewed?'課堂已核對座標':pointInCurriculum(point.code)?'常用練習穴 · 座標待核對':'模型座標待專業校核'}<small>圖中位置為教學示意。{point.position[1]<.95&&point.position[1]>.73&&Math.abs(point.position[0])<.065?'會陰與外生殖器外形已簡化，此區不用於精細取穴。':''}</small></span></div>
     </>:simulation.pain||simulation.pickMode==='pain'?null:<div className="pain-card"><div className="pain-target"><MapPin size={23}/>{simulation.customTarget&&<Check size={12}/>}</div><h2>{simulation.customTarget?'當前工具接觸位置':'在人體上點選痛處'}</h2><p>拖動器具或“移動工具”手柄，在皮膚上自由操作；也可點選另一個部位。</p><small>自主教學選擇，不自動推薦療法。</small></div>}
     <div className="tool-summary"><span className="tool-summary-icon"><ToolGlyph tool={tool.id}/></span><div><small>當前工具</small><h2>{tool.name}<span>{tool.effect}</span></h2></div></div><p className="tool-note">{tool.note}</p>
     <div className="amplitude-control"><label htmlFor="amplitude"><span><SlidersHorizontal size={13}/>演示幅度</span><output>{simulation.amplitude<.35?'輕':simulation.amplitude<.7?'中':'強'}</output></label><input id="amplitude" type="range" min="0.15" max="1" step="0.01" value={simulation.amplitude} onChange={e=>{setSimulation(s=>({...s,amplitude:Number(e.target.value)}));setFeedback('已調整動畫幅度，僅改變視覺反饋強弱。');}}/><small>僅調整動畫，不對應實際操作力度。</small></div>
     <label className="effect-toggle"><span>顯示局部視覺反饋</span><input type="checkbox" checked={simulation.effects} onChange={()=>{setSimulation(s=>({...s,effects:!s.effects}));setFeedback(simulation.effects?'已隱藏局部視覺反饋，仍可觀察工具。':'已開啟局部視覺反饋。');}}/><span className="switch-track" aria-hidden="true"/></label>
    </div>
    <div className="selection-bottom"><span className={`play-state ${simulation.running?'running':''}`}><span/>{animationStatus}</span><span>皮膚反應模擬 · 參數未作人體校準</span></div>
   </div>
  </aside>

  <section className={`tool-dock surface ${toolsExpanded?'tools-open':''}`} aria-label="工具與演示控制">
   <button className="mobile-tools-toggle" aria-expanded={toolsExpanded} aria-controls="tool-palette" onClick={()=>{setToolsExpanded(v=>!v);setMobilePanel(null);}}><ToolGlyph tool={tool.id}/><span>{tool.name}</span><ChevronDown size={14}/></button>
   <div id="tool-palette" className="dock-tools" role="group" aria-label="選擇演示工具">{TOOLS.map(t=><button className={`tool-button ${simulation.tool===t.id?'selected':''}`} key={t.id} aria-pressed={simulation.tool===t.id} onClick={()=>{chooseTool(t.id);setToolsExpanded(false);}} title={t.effect}><ToolGlyph tool={t.id}/><span>{t.name}</span><i/></button>)}</div>
   <div className="dock-separator"/>
   <div className="playback-controls"><button className={`play-button ${simulation.running?'playing':''}`} disabled={!canPlay} onClick={()=>simulation.running?pause():play()}>{simulation.running?<Pause size={15} fill="currentColor"/>:<Play size={15} fill="currentColor"/>}<span>{simulation.running?'暫停':'播放演示'}</span></button><div className="secondary-playback"><button className={`hold-button ${holding?'holding':''}`} disabled={!canPlay} onPointerDown={startHold} onPointerUp={endHold} onPointerCancel={endHold} onLostPointerCapture={()=>{if(heldPointer.current!==null)releaseHold();}} onKeyDown={e=>{if((e.key===' '||e.key==='Enter')&&!e.repeat&&canPlay){e.preventDefault();setHolding(true);play();}}} onKeyUp={e=>{if((e.key===' '||e.key==='Enter')&&holding){e.preventDefault();releaseHold();}}} onBlur={()=>{if(holding)pause();}}>按住演示</button><button className="replay-button" disabled={!canPlay} onClick={()=>play(true)} aria-label="從頭重播演示" title="從頭重播"><RotateCcw size={14}/></button></div></div>
  </section>

  <footer className="studio-footer"><span className="status-message" role="status" aria-live="polite">{feedback}</span><span className="footer-scope">課堂模擬<span>·</span>座標未經專業審校</span></footer>
  <dialog ref={dialog} className="source-dialog" onCancel={()=>setAbout(false)} onClose={()=>setAbout(false)} onClick={e=>{if(e.target===e.currentTarget){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)setAbout(false);}}} aria-labelledby="source-title">
   <div className="dialog-heading"><span className="eyebrow">資料、許可與當前邊界</span><button className="icon-button" onClick={()=>setAbout(false)} aria-label="關閉來源與許可" autoFocus><X size={19}/></button></div>
   <h2 id="source-title">讓每一層，有據可查。</h2><p className="dialog-intro">這是供課堂討論與交互驗證的工程預覽。標準文字、解剖模型與操作動畫分別保留來源和狀態。</p>
   <section><h3>穴位定位</h3><p>以 GB/T 12346—2021《經穴名稱與定位》為主要核查來源。本版收錄十四經脈 362 個經穴名稱（含國標印堂），齦交僅收錄目錄；經脈按穴序作分段體表示意，工程座標尚未經過針灸教師與解剖人員逐穴審核。</p><a href={STANDARD} target="_blank" rel="noreferrer">查閱國家標準正文<ArrowUpRight size={13}/></a></section>
   <section><h3>人體解剖與許可</h3><p>BodyParts3D，© The Database Center for Life Science，採用 CC Attribution 4.0 International 許可。參考成人男性解剖，保留 {atlas?.parts.length.toLocaleString()??'2,234'} 個原始結構；顯示幾何經過網頁簡化、座標轉換與顏色分組。</p><a href="https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html" target="_blank" rel="noreferrer">數據庫許可<ArrowUpRight size={13}/></a><a href="https://dbarchive.biosciencedbc.jp/en/bodyparts3d/download.html" target="_blank" rel="noreferrer">原始數據與說明<ArrowUpRight size={13}/></a><a href={publicAssetUrl('/ATTRIBUTION.md')} target="_blank" rel="noreferrer">本項目完整署名與改編記錄<ArrowUpRight size={13}/></a></section>
   <section><h3>工具與局部反饋</h3><p>針具接觸、溫熱光場、按壓形變、刮拭軌跡與杯口吸附均為教學視覺示意，不預測療效，也不提供真人操作參數。標記痛處不會自動生成診斷、配穴或治療推薦。</p><a href="https://www.nccih.nih.gov/health/acupuncture-effectiveness-and-safety" target="_blank" rel="noreferrer">NCCIH：針灸資料<ArrowUpRight size={13}/></a><a href="https://www.nccih.nih.gov/health/cupping" target="_blank" rel="noreferrer">NCCIH：拔罐資料<ArrowUpRight size={13}/></a><a href="https://www.mskcc.org/cancer-care/patient-education/acupressure-pain-and-headaches" target="_blank" rel="noreferrer">MSK：指壓資料<ArrowUpRight size={13}/></a><a href="https://www.whsyy.net/view_10591.html" target="_blank" rel="noreferrer">武漢市第三醫院：艾灸資料<ArrowUpRight size={13}/></a><a href="https://yjj.scjgj.fujian.gov.cn/ztzl/kpzl/spyjts/ylqxjj/202008/t20200811_5352550.htm" target="_blank" rel="noreferrer">福建省藥監局：刮痧資料<ArrowUpRight size={13}/></a><a href="https://bowuguan.bucm.edu.cn/kpzl/ysmt/10927.htm" target="_blank" rel="noreferrer">北京中醫藥大學：砭石形制與歷史<ArrowUpRight size={13}/></a></section>
   <button className="dialog-done" onClick={()=>setAbout(false)}>返回教學臺</button>
  </dialog>
 </main>;
}
