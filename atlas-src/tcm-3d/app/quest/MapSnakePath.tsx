import {useEffect,useRef,useState,type ReactNode} from 'react';
import {stageUnlocked} from './quest-progress';
import type {MeridianQuestId,QuestProgressV1} from './types';
import type {QuestStageMeta} from './quest-stages';

type Pt={x:number;y:number};

/** Grid shell with ResizeObserver snake path behind stage nodes. */
export function MapGridShell({stages,progress,children}:{stages:QuestStageMeta[];progress:QuestProgressV1;children:ReactNode}){
 const gridRef=useRef<HTMLDivElement>(null);
 const [pts,setPts]=useState<Pt[]>([]);

 useEffect(()=>{
  const grid=gridRef.current;
  if(!grid)return;
  const measure=()=>{
   const box=grid.getBoundingClientRect();
   const next:Pt[]=[];
   for(let i=0;i<stages.length;i++){
    const node=grid.querySelector<HTMLElement>(`.mq-node[data-idx="${i}"]`);
    if(!node)continue;
    const r=node.getBoundingClientRect();
    next.push({x:r.left+r.width/2-box.left,y:r.top+r.height/2-box.top});
   }
   setPts(next);
  };
  const ro=new ResizeObserver(()=>measure());
  ro.observe(grid);
  window.addEventListener('resize',measure);
  const t=window.setTimeout(measure,0);
  return()=>{ro.disconnect();window.removeEventListener('resize',measure);window.clearTimeout(t);};
 },[stages,progress]);

 return <div className="mq-map-grid" ref={gridRef}>
  {pts.length>=2&&<svg className="mq-path-layer" aria-hidden="true">
   {pts.slice(0,-1).map((a,i)=>{
    const b=pts[i+1]!;
    const toId=stages[i+1]!.id as MeridianQuestId;
    const on=stageUnlocked(progress,toId);
    return <g key={i}>
     <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} className="mq-path-inner"/>
     <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} className={on?'mq-path-on':'mq-path-off'}/>
    </g>;
   })}
  </svg>}
  {children}
 </div>;
}