import * as T from 'three';
import type {OrbitControls} from 'three/examples/jsm/controls/OrbitControls.js';
import {ToolDrag} from './tool-drag';
import type {SceneState} from './anatomy';
import type {SimulationState,SurfaceTarget,ToolDragPhase} from './teaching';

interface Options {
 canvas:HTMLCanvasElement;handle:HTMLButtonElement;camera:T.PerspectiveCamera;controls:OrbitControls;
 getSkin:()=>T.Mesh|undefined;getSimulation:()=>SimulationState;getState:()=>SceneState;
 isReady:()=>boolean;getAmount:()=>number;
 getTarget:(s:SimulationState)=>SurfaceTarget|null;getDraggables:(s:SimulationState['tool'])=>T.Object3D[];
 snapTarget:(target:SurfaceTarget)=>SurfaceTarget;
 onDrag:(target:SurfaceTarget|null,phase:ToolDragPhase)=>void;invalidate:()=>void;
}
/** Own complete tool pointer sequences before OrbitControls sees their pointerdown. */
export function installSurfaceToolInput(o:Options){
 const {canvas,handle,camera,controls}=o,drag=new ToolDrag(),pointers=new Set<number>();
 const ray=new T.Raycaster(),ndc=new T.Vector2(),projected=new T.Vector3();
 let blocked=false,disposed=false,seenDragging=false,pending:{x:number;y:number}|null=null;
 let stamp={tool:'',focus:0,reset:0};
 const canDrag=()=>o.isReady()&&o.getAmount()<.002&&o.getState().explode===0&&!o.getState().isolate&&o.getState().visible.includes('integumentary')&&o.getSimulation().pickMode!=='anatomy'&&!!o.getSkin()&&!!o.getTarget(o.getSimulation());
 const setRay=(x:number,y:number)=>{const r=canvas.getBoundingClientRect();if(!r.width||!r.height)return false;ndc.set((x-r.left)/r.width*2-1,-(y-r.top)/r.height*2+1);ray.setFromCamera(ndc,camera);return true;};
 const contactScreen=()=>{const target=o.getTarget(o.getSimulation());if(!target)return null;const r=canvas.getBoundingClientRect();projected.fromArray(target.position).project(camera);return {x:r.left+(projected.x+1)*r.width/2,y:r.top+(1-projected.y)*r.height/2};};
 const targetAt=(x:number,y:number):SurfaceTarget|null=>{
  const skin=o.getSkin();if(!skin||!setRay(x,y))return null;const hit=ray.intersectObject(skin,false)[0];if(!hit)return null;
  const normal=(hit.face?.normal.clone()??new T.Vector3(0,0,1)).transformDirection(skin.matrixWorld);if(normal.dot(ray.ray.direction)>0)normal.negate();
  const target={position:hit.point.toArray() as [number,number,number],normal:normal.toArray() as [number,number,number],meshId:'FJ2810',faceIndex:hit.faceIndex};
  const simulation=o.getSimulation();
  return simulation.pickMode==='point'&&simulation.showPoints?o.snapTarget(target):target;
 };
 const toolAt=(x:number,y:number)=>{
  if(!canDrag()||!setRay(x,y))return false;const hit=ray.intersectObjects(o.getDraggables(o.getSimulation().tool),false)[0];if(!hit)return false;
  const skin=o.getSkin(),front=skin?ray.intersectObject(skin,false)[0]:null;
  return !front||hit.distance<=front.distance+.012;
 };
 const consume=(event:Event)=>{event.preventDefault();event.stopImmediatePropagation();};
 const applyPending=()=>{if(!pending)return;const target=targetAt(pending.x,pending.y);pending=null;if(target){o.onDrag(target,'move');canvas.style.cursor='grabbing';o.invalidate();}else canvas.style.cursor='not-allowed';};
 const finish=(phase:'end'|'cancel',notify=true)=>{
  const id=drag.pointerId;if(id===null)return;if(phase==='end')applyPending();drag.cancel();pending=null;seenDragging=false;
  if(canvas.hasPointerCapture(id))canvas.releasePointerCapture(id);
  controls.enabled=!blocked;canvas.classList.remove('is-tool-dragging');canvas.style.cursor='grab';if(notify&&!disposed)o.onDrag(null,phase);o.invalidate();
 };
 const begin=(event:PointerEvent,force=false)=>{
  if(event.button!==0)return;pointers.add(event.pointerId);
  if(blocked){consume(event);return;}
  if(drag.pointerId!==null){blocked=true;finish('cancel');controls.enabled=false;consume(event);return;}
  if(pointers.size!==1||!canDrag()||(!force&&!toolAt(event.clientX,event.clientY)))return;
  const contact=contactScreen();if(!contact||!drag.begin(event.pointerId,event.clientX,event.clientY,contact.x,contact.y))return;
  consume(event);stamp={tool:o.getSimulation().tool,focus:o.getSimulation().focus,reset:o.getState().reset};
  // Drain residual orbit damping without moving the saved camera or target.
  const position=camera.position.clone(),target=controls.target.clone(),damping=controls.enableDamping;
  controls.autoRotate=false;controls.enableDamping=false;controls.update();camera.position.copy(position);controls.target.copy(target);controls.enableDamping=damping;controls.update();controls.enabled=false;
  try{canvas.setPointerCapture(event.pointerId);}catch{pointers.delete(event.pointerId);finish('cancel',false);return;}
  canvas.classList.add('is-tool-dragging');canvas.style.cursor='grabbing';o.onDrag(null,'start');o.invalidate();
 };
 const down=(e:PointerEvent)=>begin(e);
 const handleDown=(e:PointerEvent)=>begin(e,true);
 const move=(e:PointerEvent)=>{
  if(blocked){consume(e);return;}
  if(drag.pointerId!==null){consume(e);const point=drag.move(e.pointerId,e.clientX,e.clientY);if(point)pending=point;return;}
  if(!e.buttons&&o.getAmount()<.002)canvas.style.cursor=toolAt(e.clientX,e.clientY)?'move':'grab';
 };
 const up=(e:PointerEvent)=>{
  if(!pointers.has(e.pointerId)&&drag.pointerId!==e.pointerId)return;
  pointers.delete(e.pointerId);
  if(blocked){consume(e);if(!pointers.size){blocked=false;controls.enabled=true;}return;}
  if(drag.pointerId!==e.pointerId)return;consume(e);const point=drag.move(e.pointerId,e.clientX,e.clientY);if(point)pending=point;finish('end');
 };
 const cancel=(e:PointerEvent)=>{if(!pointers.has(e.pointerId)&&drag.pointerId!==e.pointerId)return;pointers.delete(e.pointerId);if(drag.pointerId===e.pointerId){consume(e);finish('cancel');}if(blocked){consume(e);if(!pointers.size){blocked=false;controls.enabled=true;}}};
 const lost=(e:PointerEvent)=>{if(drag.pointerId===e.pointerId){pointers.delete(e.pointerId);finish('cancel');}};
 const blur=()=>{blocked=false;pointers.clear();finish('cancel');controls.enabled=true;};
 const visibility=()=>{if(document.hidden)blur();};
 const key=(e:KeyboardEvent)=>{
  if(!canDrag()||drag.pointerId!==null||blocked)return;const delta:Record<string,[number,number]>={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};if(!delta[e.key])return;consume(e);
  const at=contactScreen();if(!at)return;const step=e.shiftKey?24:8,target=targetAt(at.x+delta[e.key][0]*step,at.y+delta[e.key][1]*step);if(target){o.onDrag(null,'start');o.onDrag(target,'move');o.onDrag(null,'end');o.invalidate();}
 };
 const wheel=(e:WheelEvent)=>{if(drag.pointerId!==null||blocked)consume(e);};
 canvas.addEventListener('pointerdown',down,true);canvas.addEventListener('pointermove',move,true);window.addEventListener('pointerup',up,true);window.addEventListener('pointercancel',cancel,true);canvas.addEventListener('lostpointercapture',lost,true);canvas.addEventListener('wheel',wheel,{capture:true,passive:false});
 handle.addEventListener('pointerdown',handleDown);handle.addEventListener('keydown',key);window.addEventListener('blur',blur);document.addEventListener('visibilitychange',visibility);
 return {
  isDragging:()=>drag.pointerId!==null||blocked,
  update:()=>{
   handle.disabled=!canDrag();handle.title=handle.disabled?'显示皮肤后可移动工具':'按住拖动工具；方向键微调，Shift 加速';
   if(drag.pointerId===null)return;const s=o.getSimulation();if(s.dragging)seenDragging=true;
   if(!canDrag()||s.tool!==stamp.tool||s.focus!==stamp.focus||o.getState().reset!==stamp.reset||(seenDragging&&!s.dragging)){finish('cancel');return;}
   applyPending();
  },
  dispose:()=>{disposed=true;blocked=false;pointers.clear();finish('cancel',false);controls.enabled=true;
   canvas.removeEventListener('pointerdown',down,true);canvas.removeEventListener('pointermove',move,true);window.removeEventListener('pointerup',up,true);window.removeEventListener('pointercancel',cancel,true);canvas.removeEventListener('lostpointercapture',lost,true);canvas.removeEventListener('wheel',wheel,true);handle.removeEventListener('pointerdown',handleDown);handle.removeEventListener('keydown',key);window.removeEventListener('blur',blur);document.removeEventListener('visibilitychange',visibility);
  },
 };
}
