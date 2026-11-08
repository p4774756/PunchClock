import {PainPanel} from './pain-panel';
import {painCase,matchPainRegion,type PainTopicId} from './pain-relations';
import {publicAssetUrl} from './public-assets';
import {useCallback,useEffect,useMemo,useRef,useState,type CSSProperties,type PointerEvent as ReactPointerEvent} from 'react';
import {ArrowUpRight,Check,ChevronDown,Eye,EyeOff,Focus,Info,Layers3,MapPin,Maximize2,MousePointer2,Pause,Play,RotateCcw,RotateCw,Search,SlidersHorizontal,X} from 'lucide-react';
import AnatomyScene from './scene';
import {SYSTEMS,type Atlas,type SceneState,type SystemId,type View} from './anatomy';
import {initialSimulation,LOCATION,POINTS,MERIDIANS,REGIONS,STANDARD,SYSTEM_ZH,TOOLS,type RegionId,type SimulationState,type SurfaceTarget,type ToolId,type ToolDragPhase} from './teaching';

const INITIAL_VISIBLE:SystemId[]=['integumentary'];
const initialScene:SceneState={explode:0,visible:INITIAL_VISIBLE,selected:[],isolate:false,view:'three-quarter',rotate:false,reset:0};
const VIEW_LABELS:Record<View,string>={'three-quarter':'斜视',front:'正面',back:'背面',side:'侧面'};
const clean=(value:string)=>value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[\s\-_]/g,'');

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
 const [atlas,setAtlas]=useState<Atlas|null>(null);
 const [state,setState]=useState<SceneState>(initialScene);
 const [simulation,setSimulation]=useState<SimulationState>(initialSimulation);
 const [progress,setProgress]=useState(0),[error,setError]=useState('');
 const [region,setRegion]=useState<RegionId|'all'>('all'),[query,setQuery]=useState('');
 const [libraryTab,setLibraryTab]=useState<'points'|'layers'>('points');
 const [toolsExpanded,setToolsExpanded]=useState(false);
 const [mobilePanel,setMobilePanel]=useState<'library'|'selection'|null>(null);
 const [about,setAbout]=useState(false),[holding,setHolding]=useState(false);
 const [feedback,setFeedback]=useState('选择穴位与工具，开始一次课堂演示。');
 const dialog=useRef<HTMLDialogElement>(null),searchInput=useRef<HTMLInputElement>(null);
 const heldPointer=useRef<number|null>(null);
 const draggedPoint=useRef<{code:string;name:string}|null>(null);
 const point=POINTS.find(p=>p.code===simulation.pointCode);
 const tool=TOOLS.find(t=>t.id===simulation.tool)!;
 const hasTarget=!!point||!!simulation.customTarget||!!simulation.pain?.topic||!!simulation.pain?.target;
 const canPlay=hasTarget&&progress===100&&!error&&state.explode===0&&simulation.pickMode!=='anatomy';
 const location=point?LOCATION[point.code]:null;
 const selectedPart=atlas?.parts.find(p=>p.id===state.selected[0]);
 const results=useMemo(()=>{const c=painCase(simulation.pain?.topic),codes=c?[...c.local,...c.distal]:null;return POINTS.filter(p=>(!simulation.meridianFilter||simulation.meridianFilter==='all'||p.meridianId===simulation.meridianFilter)&&(!codes||codes.includes(p.code))&&(region==='all'||p.region===region)&&(!query||clean(`${p.code} ${p.name} ${p.pinyin}`).includes(clean(query))));},[query,region,simulation.pain?.topic,simulation.meridianFilter]);
 const counts=useMemo(()=>Object.fromEntries(SYSTEMS.map(s=>[s.id,atlas?.parts.filter(p=>p.system===s.id).length??0])),[atlas]);
 const visibleCount=useMemo(()=>atlas?.parts.filter(p=>state.visible.includes(p.system)).length??0,[atlas,state.visible]);

 useEffect(()=>{const abort=new AbortController();fetch(publicAssetUrl('/models/atlas.json'),{signal:abort.signal}).then(response=>{if(!response.ok)throw new Error('人体模型目录加载失败，请刷新重试。');return response.json();}).then(data=>setAtlas(data as Atlas)).catch(e=>{if(e.name!=='AbortError')setError('人体模型目录加载失败，请检查网络后刷新重试。');});return()=>abort.abort();},[]);
 useEffect(()=>{if(!simulation.running)return;let frame=0,last=performance.now();const tick=(now:number)=>{const delta=Math.min((now-last)/1000,.1);last=now;setSimulation(s=>s.running?{...s,elapsed:s.elapsed+delta}:s);frame=requestAnimationFrame(tick);};frame=requestAnimationFrame(tick);return()=>cancelAnimationFrame(frame);},[simulation.running]);
 const pause=useCallback((message='演示已暂停，当前画面保持。')=>{heldPointer.current=null;setHolding(false);setSimulation(s=>({...s,dragging:false,running:false}));setFeedback(message);},[]);
 const releaseHold=useCallback(()=>{heldPointer.current=null;setHolding(false);setSimulation(s=>({...s,dragging:false,running:false,release:(s.release??0)+1}));setFeedback('已松开，皮肤与器具逐渐回弹。');},[]);
 useEffect(()=>{const blur=()=>pause('窗口已离开，演示自动暂停。');const visibility=()=>{if(document.hidden)blur();};window.addEventListener('blur',blur);document.addEventListener('visibilitychange',visibility);return()=>{window.removeEventListener('blur',blur);document.removeEventListener('visibilitychange',visibility);};},[pause]);
 useEffect(()=>{if(about){dialog.current?.showModal();pause('已打开来源与许可，演示暂停。');}else dialog.current?.close();},[about,pause]);
 useEffect(()=>{const key=(event:KeyboardEvent)=>{if(event.key==='Escape'){setAbout(false);setMobilePanel(null);pause();}if(event.key==='/'&&!(event.target instanceof HTMLInputElement)&&!(event.target instanceof HTMLTextAreaElement)){event.preventDefault();setLibraryTab('points');setMobilePanel('library');searchInput.current?.focus();}};window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);},[pause]);

 const prepareFocus=()=>setState(s=>({...s,explode:0,rotate:false,isolate:false,selected:[]}));
 const choosePoint=(code:string,side:1|-1=simulation.side)=>{const next=POINTS.find(p=>p.code===code);if(!next)return;const related=painCase(simulation.pain?.topic);if(related&&[...related.local,...related.distal].includes(code)&&side===simulation.pain?.side){chooseRelatedPoint(code);return;}prepareFocus();setHolding(false);heldPointer.current=null;setSimulation(s=>({...s,pointCode:code,side:next.midline?1:side,customTarget:null,dragging:false,running:false,elapsed:0,focus:s.focus+1,pickMode:'point'}));setFeedback(`已选择${next.name} ${next.code}，可查看定位并选择演示工具。`);setMobilePanel(null);};
 const chooseTool=(id:ToolId)=>{setHolding(false);heldPointer.current=null;setSimulation(s=>({...s,tool:id,dragging:false,running:false,elapsed:0}));setFeedback(`已切换为${TOOLS.find(t=>t.id===id)!.name}，演示回到起点。`);};
 const chooseSide=(side:1|-1)=>{if(point?.midline)return;prepareFocus();setSimulation(s=>({...s,side,dragging:false,running:false,elapsed:0,focus:s.focus+1}));setFeedback(`已切换到患者${side===1?'左':'右'}侧。`);};
 const changeMode=(mode:SimulationState['pickMode'])=>{pause();setState(s=>({...s,explode:0,rotate:false,isolate:false,selected:[]}));setSimulation(s=>({...s,pickMode:mode,dragging:false,running:false,elapsed:0,...(mode==='pain'?{pointCode:null,customTarget:null,pain:undefined}:{}),...(mode==='anatomy'?{pain:undefined}:{})}));setFeedback(mode==='pain'?'点选人体痛处，或选择头痛、颈部痛、腰痛、膝痛课堂示例。':mode==='anatomy'?'点选可见解剖结构，观察它与体表的位置关系。':'点击教学点，或在左侧查找穴位。');};
 const onSurface=(target:SurfaceTarget)=>{prepareFocus();setRegion('all');setQuery('');const topic=matchPainRegion(target.position);setSimulation(s=>({...s,customTarget:target,pointCode:null,side:target.position[0]>=0?1:-1,pain:{topic,target,side:target.position[0]>=0?1:-1,level:s.pain?.level??.5,showLinks:true},dragging:false,running:false,elapsed:0,pickMode:'pain'}));setMobilePanel('selection');setFeedback(topic?'痛处已标记：可查看局部与远端穴位的教学关联。':'痛处已保留，此区域尚无关联资料，不自动生成配穴。');};
 const choosePainCase=(id:PainTopicId,side:1|-1=simulation.pain?.side??simulation.side)=>{pause();setRegion('all');setQuery('');const c=painCase(id)!;setState(s=>({...s,visible:['integumentary'],explode:0,isolate:false,selected:[],rotate:false,view:c.view,reset:s.reset+1}));setSimulation(s=>({...s,pain:{topic:id,target:null,side,level:s.pain?.level??.5,showLinks:true},pointCode:null,customTarget:null,side,pickMode:'pain',elapsed:0}));setMobilePanel('selection');setFeedback(`${c.name}示例：红色标记痛处，金色为局部穴位，蓝色为远端关联。`);};
 const relationOverview=()=>{pause();setState(s=>({...s,explode:0,isolate:false,selected:[],visible:['integumentary'],rotate:false,view:painCase(simulation.pain?.topic)?.view??s.view,reset:s.reset+1}));};
 const chooseRelatedPoint=(code:string)=>{relationOverview();setSimulation(s=>({...s,pointCode:code,customTarget:null,side:s.pain?.side??s.side,pickMode:'point',elapsed:0}));setFeedback(`已选择${POINTS.find(p=>p.code===code)!.name}，保留原痛处；连线说明关联，不代表必然止痛。`);};
 const clearPain=()=>{pause();setSimulation(s=>({...s,pain:undefined,pickMode:'point'}));setFeedback('已退出疼痛关联，恢复普通穴位与工具演示。');};
 const onToolDrag=useCallback((target:SurfaceTarget|null,phase:ToolDragPhase)=>{
  if(phase==='start'){draggedPoint.current=null;setState(s=>({...s,rotate:false}));setSimulation(s=>({...s,dragging:true,running:true,elapsed:Math.max(.01,s.elapsed)}));setFeedback('正在操作工具：拖到穴位附近可自动选中。');}
  else if(phase==='move'&&target){const selected=target.pointCode?POINTS.find(p=>p.code===target.pointCode):null;draggedPoint.current=selected?{code:selected.code,name:selected.name}:null;setSimulation(s=>selected?{...s,customTarget:null,pointCode:selected.code,side:selected.midline?1:(target.pointSide??s.side),pickMode:'point',dragging:true}:{...s,customTarget:target,pointCode:null,pickMode:'point',dragging:true});}
  else if(phase==='end'||phase==='cancel'){setSimulation(s=>({...s,dragging:false,running:false,release:(s.release??0)+1}));const selected=draggedPoint.current;setFeedback(phase==='cancel'?'工具移动已结束，已恢复人体旋转。':selected?`已选中${selected.name} ${selected.code}，可继续演示。`:'工具已停在新的皮肤位置，可继续拖动或播放演示。');draggedPoint.current=null;}
 },[]);
 const onSelect=(id:string)=>{pause();setState(s=>({...s,selected:[id],rotate:false,isolate:false}));setFeedback('已选择解剖结构，可查看所属系统或单独观察。');};
 const toggleLayer=(id:SystemId)=>{pause('解剖层已更新，演示暂停。');setState(s=>({...s,selected:[],isolate:false,visible:s.visible.includes(id)?s.visible.filter(v=>v!==id):[...s.visible,id]}));};
 const preset=(visible:SystemId[],label:string)=>{pause(`已切换到${label}。`);setState(s=>({...s,visible,selected:[],isolate:false}));};
 const changeView=(view:View)=>{pause(`已切换为${VIEW_LABELS[view]}。`);setState(s=>({...s,view,rotate:false,reset:s.reset+1}));};
 const fullBody=()=>{pause('已回到全身视角。');setState(s=>({...s,explode:0,isolate:false,selected:[],rotate:false,reset:s.reset+1}));};
 const focusTarget=()=>{if(!hasTarget)return;prepareFocus();pause('已聚焦当前教学位置。');setSimulation(s=>({...s,focus:s.focus+1}));};
 const play=(restart=false)=>{if(!canPlay)return;setState(s=>({...s,explode:0,rotate:false,isolate:false}));setSimulation(s=>({...s,running:true,elapsed:restart?0:s.elapsed,focus:s.pain?s.focus:s.focus+1}));setFeedback(`${tool.name}演示进行中：${tool.effect}。`);};
 const startHold=(event:ReactPointerEvent<HTMLButtonElement>)=>{if(!canPlay)return;event.preventDefault();event.currentTarget.setPointerCapture(event.pointerId);heldPointer.current=event.pointerId;setHolding(true);play();};
 const endHold=(event:ReactPointerEvent<HTMLButtonElement>)=>{if(heldPointer.current!==event.pointerId)return;heldPointer.current=null;if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId);releaseHold();};
 const onProgress=useCallback((value:number)=>{setProgress(value);if(value===100)setError('');},[]);
 const onError=useCallback((_message:string)=>{setError('3D 模型加载出现问题，请检查网络与浏览器 WebGL 支持后重试。');setSimulation(s=>({...s,dragging:false,running:false}));},[]);
 const selectedTitle=simulation.pickMode==='anatomy'?'解剖结构':point?.name??(simulation.pain?(painCase(simulation.pain.topic)?.name??'自选痛处'):simulation.customTarget?'自选体表位置':'点选人体表面');
 const animationStatus=simulation.dragging?'操作中':simulation.running?'演示中':simulation.elapsed>0?'已暂停':'待演示';

 return <main className="studio" style={{'--tool-color':tool.color} as CSSProperties}>
  <header className="studio-header">
   <a className="brand" href="https://tcm.jic.io/" aria-label="返回 JIC 中医首页"><span className="brand-mark">经</span><span><span className="brand-name">中医人体<span className="edition">教学台</span></span><span className="brand-subtitle">JIC 中医 · 穴位与工具演示</span></span></a>
   <div className="header-status"><span className="status-dot"/>工程教学预览<span className="header-divider"/>成人男性参考模型</div>
   <button className="source-button" onClick={()=>setAbout(true)}><Info size={16}/><span>来源与许可</span></button>
  </header>

  <section className="scene-stage" aria-label="3D 人体教学视窗">
   {atlas&&<AnatomyScene atlas={atlas} state={{...state,inspectorOpen:false}} simulation={simulation} onSelect={onSelect} onPoint={choosePoint} onSurface={onSurface} onToolDrag={onToolDrag} onProgress={onProgress} onError={onError}/>}
   <div className="scene-guide"><span className="guide-cross">+</span><span>成人男性 · 体表参考</span></div>
   {(progress<100||error)&&<div className={`loading-card ${error?'load-error':''}`} role="status"><span className="loading-symbol">{error?<Info size={23}/>:<Layers3 size={23}/>}</span><strong>{error?'模型暂未就绪':'正在加载人体解剖'}</strong><p>{error||'正在准备真实解剖结构与教学点。'}</p>{!error&&<><div className="load-track"><i style={{width:`${progress}%`}}/></div><small>{progress}%</small></>}{error&&<button className="text-button" onClick={()=>window.location.reload()}>重新加载</button>}</div>}
   <div className={`scene-mode-hint ${simulation.pickMode==='pain'?'pain-hint':''}`}><span className="tiny-dot"/>{simulation.pain?'红色：痛处 · 金色：局部 · 蓝色：远端 · 虚线仅表示关联':simulation.pickMode==='pain'?'点选人体上的痛处，或选择课堂示例':simulation.pickMode==='anatomy'?'点击解剖结构，观察层次关系':'抓住工具移动 · 拖动其他位置旋转 · 滚动缩放'}</div>
  </section>

  <nav className="camera-bar surface" aria-label="人体视角">
   {(['front','back','side','three-quarter'] as View[]).map(view=><button key={view} className={state.view===view?'selected':''} aria-pressed={state.view===view} onClick={()=>changeView(view)}>{VIEW_LABELS[view]}</button>)}
   <span className="control-divider"/><button onClick={fullBody} title="显示全身"><Maximize2 size={14}/><span>全身</span></button><button onClick={focusTarget} disabled={!hasTarget} title="聚焦当前穴位或痛处"><Focus size={14}/><span>聚焦</span></button><button className={`icon-button ${state.rotate?'selected':''}`} aria-label={state.rotate?'停止旋转':'自动旋转'} aria-pressed={state.rotate} onClick={()=>{pause();setState(s=>({...s,rotate:!s.rotate}));setFeedback(state.rotate?'自动旋转已停止。':'正在自动旋转人体。');}}><RotateCw size={15}/></button>
  </nav>

  <button className={`pain-entry ${simulation.pain||simulation.pickMode==='pain'?'active':''}`} aria-pressed={!!simulation.pain||simulation.pickMode==='pain'} onClick={()=>{changeMode('pain');setMobilePanel('selection');}}>◎ 疼痛关联</button>

  <aside className={`library-panel surface ${mobilePanel==='library'?'mobile-expanded':''}`} aria-label="穴位与解剖层">
   <button className="mobile-panel-heading" aria-expanded={mobilePanel==='library'} onClick={()=>setMobilePanel(p=>p==='library'?null:'library')}><span><Layers3 size={16}/>穴位与解剖层</span><ChevronDown size={17}/></button>
   <div className="panel-content">
    <div className="panel-tabs" role="tablist" aria-label="学习内容"><button role="tab" aria-selected={libraryTab==='points'} className={libraryTab==='points'?'selected':''} onClick={()=>setLibraryTab('points')} id="points-tab" aria-controls="points-panel">穴位<span>{POINTS.length}</span></button><button role="tab" aria-selected={libraryTab==='layers'} className={libraryTab==='layers'?'selected':''} onClick={()=>setLibraryTab('layers')} id="layers-tab" aria-controls="layers-panel">取穴参考<span>15</span></button></div>
    {libraryTab==='points'?<div className="tab-body" id="points-panel" role="tabpanel" aria-labelledby="points-tab">
     <label className="search-box"><Search size={15}/><input ref={searchInput} value={query} onChange={e=>setQuery(e.target.value)} placeholder="穴名、拼音或编号" aria-label="搜索教学穴位"/>{query?<button aria-label="清空搜索" onClick={()=>setQuery('')}><X size={13}/></button>:<kbd>/</kbd>}</label>
     <details className="region-disclosure"><summary>按身体区域筛选</summary><div className="section-label">选择身体区域<button className={region==='all'?'current':''} onClick={()=>{setRegion('all');setFeedback('正在显示全部教学点。');}}>全部</button></div>
     <div className="region-grid">{REGIONS.map(r=><button className={region===r.id?'selected':''} key={r.id} aria-pressed={region===r.id} onClick={()=>{setRegion(r.id);setQuery('');choosePoint(r.example);}}>{r.name}</button>)}</div>
     </details><div className="meridian-controls"><label>十四经脉<select aria-label="选择经脉" value={simulation.meridianFilter??'all'} onChange={e=>{setRegion('all');setQuery('');setSimulation(s=>({...s,meridianFilter:e.target.value,pain:undefined}));}}><option value="all">全部经脉 · 362 穴</option>{MERIDIANS.map(m=><option key={m.id} value={m.id}>{m.name} · {m.count} 穴</option>)}</select></label><label className="meridian-toggle"><input type="checkbox" checked={!!simulation.showMeridians} onChange={()=>setSimulation(s=>({...s,showMeridians:!s.showMeridians}))}/>显示经脉体表连线</label><small>依穴序分段示意；不代表深部循行。奇穴、耳穴另属其他体系。</small></div>
     <div className="list-heading"><span>{simulation.pain?.topic?'关联穴位':'定位学习入口'}</span><span>{results.length} 穴</span></div>
     <div className="point-list">{results.map(p=><button key={p.code} className={`point-row ${p.code===simulation.pointCode?'selected':''}`} onClick={()=>choosePoint(p.code)} aria-pressed={p.code===simulation.pointCode}><span className="point-dot"/><span className="point-row-copy"><span className="point-name">{p.name}<span>{p.pinyin}</span></span><small>{p.meridian}</small></span><span className="point-code">{p.code}</span></button>)}{results.length===0&&<div className="empty-state"><Search size={20}/><p>未找到匹配穴位</p><button className="text-button" onClick={()=>{setQuery('');setRegion('all');}}>查看全部教学点</button></div>}</div>
     <div className="point-list-foot"><span>{POINTS.length} 穴工程示意坐标</span><button className="icon-button" aria-label={simulation.showPoints?'隐藏穴位点':'显示穴位点'} aria-pressed={simulation.showPoints} onClick={()=>{setSimulation(s=>({...s,showPoints:!s.showPoints}));setFeedback(simulation.showPoints?'已隐藏穴位点。':'已显示穴位点。');}}>{simulation.showPoints?<Eye size={15}/>:<EyeOff size={15}/>}</button></div>
    </div>:<div className="tab-body" id="layers-panel" role="tabpanel" aria-labelledby="layers-tab">
     <div className="layer-presets"><button onClick={()=>preset(INITIAL_VISIBLE,'中医铜人体表')}>体表参考</button><button onClick={()=>preset(['skeletal'],'骨骼层')}>骨骼</button><button onClick={()=>preset(SYSTEMS.map(s=>s.id),'全部解剖层')}>全部</button></div>
     <details className="anatomy-details"><summary>取穴参考 · 辅助解剖<span>15 层</span></summary><div className="system-list">{SYSTEMS.map(s=><label className={`system-row ${state.visible.includes(s.id)?'enabled':''}`} key={s.id}><span className="system-dot" style={{background:s.color}}/><span className="system-label">{SYSTEM_ZH[s.id]}</span><span className="system-count">{counts[s.id]||0}</span><input type="checkbox" checked={state.visible.includes(s.id)} onChange={()=>toggleLayer(s.id)} aria-label={`显示${SYSTEM_ZH[s.id]}`}/><span className="switch-track" aria-hidden="true"/></label>)}</div></details>
     <div className="point-list-foot"><span>{visibleCount.toLocaleString()} 个结构可见</span><button className="text-button" onClick={()=>preset([],'全部隐藏')}>隐藏全部</button></div>
    </div>}
   </div>
  </aside>

  <aside className={`selection-panel surface ${mobilePanel==='selection'?'mobile-expanded':''}`} aria-label="当前教学选择">
   <button className="mobile-panel-heading" aria-expanded={mobilePanel==='selection'} onClick={()=>setMobilePanel(p=>p==='selection'?null:'selection')}><span><MapPin size={16}/>{selectedTitle}<small>{point?.code}</small></span><ChevronDown size={17}/></button>
   <div className="panel-content">
    <div className="selection-eyebrow"><span>当前选择</span><span className="selection-number">{point?.code??(simulation.pain?'痛处关联':simulation.customTarget?'体表位置':simulation.pickMode==='pain'?'体表标记':'解剖参考')}</span></div>
    <div className="selection-title"><h1>{selectedTitle}</h1>{point&&<p>{point.pinyin}<span>·</span>{point.meridian}</p>}</div>
    <div className="mode-control" aria-label="选择方式">{([{id:'point',label:'选穴',icon:MapPin},{id:'pain',label:'点选痛处',icon:MousePointer2},{id:'anatomy',label:'解剖',icon:Layers3}] as const).map(mode=><button key={mode.id} className={simulation.pickMode===mode.id?'selected':''} aria-pressed={simulation.pickMode===mode.id} onClick={()=>changeMode(mode.id)}><mode.icon size={13}/>{mode.label}</button>)}</div>
    <div className="selection-scroll">
     {(simulation.pain||simulation.pickMode==='pain')&&<PainPanel pain={simulation.pain} selected={simulation.pointCode} onCase={choosePainCase} onPoint={chooseRelatedPoint} onOverview={relationOverview} onClear={clearPain} onUpdate={next=>setSimulation(s=>({...s,pain:s.pain?{...s.pain,...next}:undefined}))}/>}
     {simulation.pickMode==='anatomy'?<div className="location-card"><h2>解剖观察</h2>{selectedPart?<><p>{SYSTEM_ZH[selectedPart.system]} · 已选结构</p><div className="anatomy-name">{selectedPart.name}</div><button className={`outline-button ${state.isolate?'selected':''}`} onClick={()=>{pause();setState(s=>({...s,isolate:!s.isolate,rotate:false}));setFeedback(state.isolate?'已恢复其他可见结构。':'正在单独观察所选解剖结构。');}}>{state.isolate?'恢复其他结构':'单独观察此结构'}</button></>:<p>点选人体或拆解后的结构，查看它所属的解剖系统。</p>}</div>:point?<>
      <div className="side-control"><span>患者侧别</span><div><button disabled={point.midline} className={simulation.side===1&&!point.midline?'selected':''} onClick={()=>chooseSide(1)} aria-pressed={simulation.side===1&&!point.midline}>左</button><button disabled={point.midline} className={simulation.side===-1&&!point.midline?'selected':''} onClick={()=>chooseSide(-1)} aria-pressed={simulation.side===-1&&!point.midline}>右</button></div>{point.midline&&<small>正中线</small>}</div>
      <div className="location-card"><h2><span className="section-dash"/>标准定位</h2>{location?<><p>{location.text}</p><a href={STANDARD} target="_blank" rel="noreferrer">国标条款 {location.section}<ArrowUpRight size={12}/></a><small>“寸”为身体分段比例，不是固定厘米。</small></>:<><p>本穴已收录至教学目录。标准定位摘要待逐条核录，请对照原文学习。</p><a href={point.source||STANDARD} target="_blank" rel="noreferrer">查阅定位来源<ArrowUpRight size={12}/></a></>}</div>
      <div className="meridian-navigation"><button onClick={()=>{const list=POINTS.filter(p=>p.meridianId===point.meridianId),i=list.findIndex(p=>p.code===point.code);choosePoint(list[(i-1+list.length)%list.length].code);}}>上一穴</button><button onClick={()=>{const list=POINTS.filter(p=>p.meridianId===point.meridianId),i=list.findIndex(p=>p.code===point.code);choosePoint(list[(i+1)%list.length].code);}}>下一穴</button></div>{point.surfaceUnavailable&&<p className="review-note">本穴位于口内，当前体表模式仅提供目录，不显示皮肤标记或器具。</p>}<div className="review-note"><span className="review-dot"/><span>模型坐标待专业校核<small>图中位置为工程示意。{point.position[1]<.95&&point.position[1]>.73&&Math.abs(point.position[0])<.065?'会阴与外生殖器外形已简化，此区不用于精细取穴。':''}</small></span></div>
     </>:simulation.pain||simulation.pickMode==='pain'?null:<div className="pain-card"><div className="pain-target"><MapPin size={23}/>{simulation.customTarget&&<Check size={12}/>}</div><h2>{simulation.customTarget?'当前工具接触位置':'在人体上点选痛处'}</h2><p>拖动器具或“移动工具”手柄，在皮肤上自由操作；也可点选另一个部位。</p><small>自主教学选择，不自动推荐疗法。</small></div>}
     <div className="tool-summary"><span className="tool-summary-icon"><ToolGlyph tool={tool.id}/></span><div><small>当前工具</small><h2>{tool.name}<span>{tool.effect}</span></h2></div></div><p className="tool-note">{tool.note}</p>
     <div className="amplitude-control"><label htmlFor="amplitude"><span><SlidersHorizontal size={13}/>演示幅度</span><output>{simulation.amplitude<.35?'轻':simulation.amplitude<.7?'中':'强'}</output></label><input id="amplitude" type="range" min="0.15" max="1" step="0.01" value={simulation.amplitude} onChange={e=>{setSimulation(s=>({...s,amplitude:Number(e.target.value)}));setFeedback('已调整动画幅度，仅改变视觉反馈强弱。');}}/><small>仅调整动画，不对应实际操作力度。</small></div>
     <label className="effect-toggle"><span>显示局部视觉反馈</span><input type="checkbox" checked={simulation.effects} onChange={()=>{setSimulation(s=>({...s,effects:!s.effects}));setFeedback(simulation.effects?'已隐藏局部视觉反馈，仍可观察工具。':'已开启局部视觉反馈。');}}/><span className="switch-track" aria-hidden="true"/></label>
    </div>
    <div className="selection-bottom"><span className={`play-state ${simulation.running?'running':''}`}><span/>{animationStatus}</span><span>皮肤反应模拟 · 参数未作人体校准</span></div>
   </div>
  </aside>

  <section className={`tool-dock surface ${toolsExpanded?'tools-open':''}`} aria-label="工具与演示控制">
   <button className="mobile-tools-toggle" aria-expanded={toolsExpanded} aria-controls="tool-palette" onClick={()=>{setToolsExpanded(v=>!v);setMobilePanel(null);}}><ToolGlyph tool={tool.id}/><span>{tool.name}</span><ChevronDown size={14}/></button>
   <div id="tool-palette" className="dock-tools" role="group" aria-label="选择演示工具">{TOOLS.map(t=><button className={`tool-button ${simulation.tool===t.id?'selected':''}`} key={t.id} aria-pressed={simulation.tool===t.id} onClick={()=>{chooseTool(t.id);setToolsExpanded(false);}} title={t.effect}><ToolGlyph tool={t.id}/><span>{t.name}</span><i/></button>)}</div>
   <div className="dock-separator"/>
   <div className="playback-controls"><button className={`play-button ${simulation.running?'playing':''}`} disabled={!canPlay} onClick={()=>simulation.running?pause():play()}>{simulation.running?<Pause size={15} fill="currentColor"/>:<Play size={15} fill="currentColor"/>}<span>{simulation.running?'暂停':'播放演示'}</span></button><div className="secondary-playback"><button className={`hold-button ${holding?'holding':''}`} disabled={!canPlay} onPointerDown={startHold} onPointerUp={endHold} onPointerCancel={endHold} onLostPointerCapture={()=>{if(heldPointer.current!==null)releaseHold();}} onKeyDown={e=>{if((e.key===' '||e.key==='Enter')&&!e.repeat&&canPlay){e.preventDefault();setHolding(true);play();}}} onKeyUp={e=>{if((e.key===' '||e.key==='Enter')&&holding){e.preventDefault();releaseHold();}}} onBlur={()=>{if(holding)pause();}}>按住演示</button><button className="replay-button" disabled={!canPlay} onClick={()=>play(true)} aria-label="从头重播演示" title="从头重播"><RotateCcw size={14}/></button></div></div>
  </section>

  <footer className="studio-footer"><span className="status-message" role="status" aria-live="polite">{feedback}</span><span className="footer-scope">课堂模拟<span>·</span>坐标未经专业审校</span></footer>
  <dialog ref={dialog} className="source-dialog" onCancel={()=>setAbout(false)} onClose={()=>setAbout(false)} onClick={e=>{if(e.target===e.currentTarget){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)setAbout(false);}}} aria-labelledby="source-title">
   <div className="dialog-heading"><span className="eyebrow">资料、许可与当前边界</span><button className="icon-button" onClick={()=>setAbout(false)} aria-label="关闭来源与许可" autoFocus><X size={19}/></button></div>
   <h2 id="source-title">让每一层，有据可查。</h2><p className="dialog-intro">这是供课堂讨论与交互验证的工程预览。标准文字、解剖模型与操作动画分别保留来源和状态。</p>
   <section><h3>穴位定位</h3><p>以 GB/T 12346—2021《经穴名称与定位》为主要核查来源。本版收录十四经脉 362 个经穴名称（含国标印堂），龈交仅收录目录；经脉按穴序作分段体表示意，工程坐标尚未经过针灸教师与解剖人员逐穴审核。</p><a href={STANDARD} target="_blank" rel="noreferrer">查阅国家标准正文<ArrowUpRight size={13}/></a></section>
   <section><h3>人体解剖与许可</h3><p>BodyParts3D，© The Database Center for Life Science，采用 CC Attribution 4.0 International 许可。参考成人男性解剖，保留 {atlas?.parts.length.toLocaleString()??'2,234'} 个原始结构；显示几何经过网页简化、坐标转换与颜色分组。</p><a href="https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html" target="_blank" rel="noreferrer">数据库许可<ArrowUpRight size={13}/></a><a href="https://dbarchive.biosciencedbc.jp/en/bodyparts3d/download.html" target="_blank" rel="noreferrer">原始数据与说明<ArrowUpRight size={13}/></a><a href={publicAssetUrl('/ATTRIBUTION.md')} target="_blank" rel="noreferrer">本项目完整署名与改编记录<ArrowUpRight size={13}/></a></section>
   <section><h3>工具与局部反馈</h3><p>针具接触、温热光场、按压形变、刮拭轨迹与杯口吸附均为教学视觉示意，不预测疗效，也不提供真人操作参数。标记痛处不会自动生成诊断、配穴或治疗推荐。</p><a href="https://www.nccih.nih.gov/health/acupuncture-effectiveness-and-safety" target="_blank" rel="noreferrer">NCCIH：针灸资料<ArrowUpRight size={13}/></a><a href="https://www.nccih.nih.gov/health/cupping" target="_blank" rel="noreferrer">NCCIH：拔罐资料<ArrowUpRight size={13}/></a><a href="https://www.mskcc.org/cancer-care/patient-education/acupressure-pain-and-headaches" target="_blank" rel="noreferrer">MSK：指压资料<ArrowUpRight size={13}/></a><a href="https://www.whsyy.net/view_10591.html" target="_blank" rel="noreferrer">武汉市第三医院：艾灸资料<ArrowUpRight size={13}/></a><a href="https://yjj.scjgj.fujian.gov.cn/ztzl/kpzl/spyjts/ylqxjj/202008/t20200811_5352550.htm" target="_blank" rel="noreferrer">福建省药监局：刮痧资料<ArrowUpRight size={13}/></a><a href="https://bowuguan.bucm.edu.cn/kpzl/ysmt/10927.htm" target="_blank" rel="noreferrer">北京中医药大学：砭石形制与历史<ArrowUpRight size={13}/></a></section>
   <button className="dialog-done" onClick={()=>setAbout(false)}>返回教学台</button>
  </dialog>
 </main>;
}
