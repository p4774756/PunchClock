import {PAIN_CASES,painCase,type PainSelection,type PainTopicId} from './pain-relations';
import {POINTS} from './teaching';

export function PainPanel({pain,selected,onCase,onPoint,onUpdate,onClear,onOverview}:{pain?:PainSelection;selected:string|null;onCase:(id:PainTopicId,side?:1|-1)=>void;onPoint:(code:string)=>void;onUpdate:(next:Partial<PainSelection>)=>void;onClear:()=>void;onOverview:()=>void}){
 const topic=painCase(pain?.topic);
 return <section className="pain-relations-panel" aria-label="疼痛與穴位關係">
  <div className="pain-panel-heading"><h2>疼痛與穴位</h2><button onClick={onClear}>退出</button></div>
  <p>在人體上點選痛處，或選擇課堂示例。</p>
  <div className="pain-case-grid">{PAIN_CASES.map(c=><button key={c.id} aria-pressed={pain?.topic===c.id} onClick={()=>onCase(c.id)}>{c.name}</button>)}</div>
  {pain&&<>
   <div className="pain-legend"><span className="pain-red">● 痛處</span><span className="pain-local">● 局部</span><span className="pain-distal">● 遠端</span></div>
   {topic?<>
    <div className="pain-controls"><button onClick={onOverview}>關係全景</button><button aria-pressed={pain.side===1} onClick={()=>onCase(topic.id,1)}>左側示例</button><button aria-pressed={pain.side===-1} onClick={()=>onCase(topic.id,-1)}>右側示例</button></div>
    <p className="pain-region-match">{pain.target?'按所點體表區域匹配，可用上方示例更改。':'課堂示例位置，可重新點選痛處。'}</p>
    {(['local','distal'] as const).map(kind=><div className={`pain-point-group ${kind}`} key={kind}><h3>{kind==='local'?'局部穴位':'遠端關聯'}</h3>{topic[kind].length?topic[kind].map(code=>{const point=POINTS.find(p=>p.code===code)!;return <button key={code} aria-pressed={selected===code} onClick={()=>onPoint(code)}><span>{point.name}</span><small>{code}</small><span aria-hidden="true">↗</span></button>}):<small>此示例未列出本地已收錄的局部穴位。</small>}</div>)}
    <details className="pain-evidence"><summary>關聯依據與適用範圍</summary><p>{topic.scope}</p><a href={topic.source} target="_blank" rel="noreferrer">{topic.sourceName} ↗</a><p>僅顯示本版已收錄穴位。研究方案中的關聯不等於單穴獨立療效，也不能推廣到所有工具。</p></details>
   </>:<p className="pain-unmatched">此部位尚未收錄關聯資料。保留痛處標記，不自動給出配穴；可選擇上方課堂示例。</p>}
   <label className="pain-level">痛感演示強度<input aria-label="痛感演示強度" type="range" min="0.1" max="1" step="0.1" value={pain.level} onChange={e=>onUpdate({level:Number(e.target.value)})}/><small>僅由你調整標記；工具操作不會自動降低痛感。</small></label>
   <label className="pain-link-toggle"><input type="checkbox" checked={pain.showLinks} onChange={e=>onUpdate({showLinks:e.target.checked})}/>顯示關係連線</label>
   <small className="pain-relation-note">虛線表示教學關聯，不是經絡、神經走向或療效預測。</small>
  </>}
 </section>;
}
