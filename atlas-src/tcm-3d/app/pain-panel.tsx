import {PAIN_CASES,painCase,type PainSelection,type PainTopicId} from './pain-relations';
import {POINTS} from './teaching';

export function PainPanel({pain,selected,onCase,onPoint,onUpdate,onClear,onOverview}:{pain?:PainSelection;selected:string|null;onCase:(id:PainTopicId,side?:1|-1)=>void;onPoint:(code:string)=>void;onUpdate:(next:Partial<PainSelection>)=>void;onClear:()=>void;onOverview:()=>void}){
 const topic=painCase(pain?.topic);
 return <section className="pain-relations-panel" aria-label="疼痛与穴位关系">
  <div className="pain-panel-heading"><h2>疼痛与穴位</h2><button onClick={onClear}>退出</button></div>
  <p>在人体上点选痛处，或选择课堂示例。</p>
  <div className="pain-case-grid">{PAIN_CASES.map(c=><button key={c.id} aria-pressed={pain?.topic===c.id} onClick={()=>onCase(c.id)}>{c.name}</button>)}</div>
  {pain&&<>
   <div className="pain-legend"><span className="pain-red">● 痛处</span><span className="pain-local">● 局部</span><span className="pain-distal">● 远端</span></div>
   {topic?<>
    <div className="pain-controls"><button onClick={onOverview}>关系全景</button><button aria-pressed={pain.side===1} onClick={()=>onCase(topic.id,1)}>左侧示例</button><button aria-pressed={pain.side===-1} onClick={()=>onCase(topic.id,-1)}>右侧示例</button></div>
    <p className="pain-region-match">{pain.target?'按所点体表区域匹配，可用上方示例更改。':'课堂示例位置，可重新点选痛处。'}</p>
    {(['local','distal'] as const).map(kind=><div className={`pain-point-group ${kind}`} key={kind}><h3>{kind==='local'?'局部穴位':'远端关联'}</h3>{topic[kind].length?topic[kind].map(code=>{const point=POINTS.find(p=>p.code===code)!;return <button key={code} aria-pressed={selected===code} onClick={()=>onPoint(code)}><span>{point.name}</span><small>{code}</small><span aria-hidden="true">↗</span></button>}):<small>此示例未列出本地已收录的局部穴位。</small>}</div>)}
    <details className="pain-evidence"><summary>关联依据与适用范围</summary><p>{topic.scope}</p><a href={topic.source} target="_blank" rel="noreferrer">{topic.sourceName} ↗</a><p>仅显示本版已收录穴位。研究方案中的关联不等于单穴独立疗效，也不能推广到所有工具。</p></details>
   </>:<p className="pain-unmatched">此部位尚未收录关联资料。保留痛处标记，不自动给出配穴；可选择上方课堂示例。</p>}
   <label className="pain-level">痛感演示强度<input aria-label="痛感演示强度" type="range" min="0.1" max="1" step="0.1" value={pain.level} onChange={e=>onUpdate({level:Number(e.target.value)})}/><small>仅由你调整标记；工具操作不会自动降低痛感。</small></label>
   <label className="pain-link-toggle"><input type="checkbox" checked={pain.showLinks} onChange={e=>onUpdate({showLinks:e.target.checked})}/>显示关系连线</label>
   <small className="pain-relation-note">虚线表示教学关联，不是经络、神经走向或疗效预测。</small>
  </>}
 </section>;
}
