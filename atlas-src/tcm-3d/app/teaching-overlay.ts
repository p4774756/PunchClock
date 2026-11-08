import baked from './meridian-surface.json';
import {createPainOverlay} from './pain-overlay';
import * as T from 'three';
import {createInstrument,updateInstrument} from './instruments';
import {POINTS,POINT_BY_CODE,MERIDIANS,TOOLS,pointTarget,type SimulationState,type SurfaceTarget,type ToolId} from './teaching';

/** Visual teaching overlay. These surface anchors are unreviewed, never clinical coordinates. */
export function createTeachingOverlay(scene:T.Scene,useBaked=true){
 const root=new T.Group(),dots=new T.Group(),contact=new T.Group();root.add(dots,contact);scene.add(root);
 const surface=new Map<string,SurfaceTarget>();
 const sphere=new T.SphereGeometry(.0042,14,10);
 const normalMat=new T.MeshStandardMaterial({color:'#4e958b',emissive:'#24463e',emissiveIntensity:.15,roughness:.3});
 const activeMat=new T.MeshStandardMaterial({color:'#e9ae58',emissive:'#b17830',emissiveIntensity:.4,roughness:.3});
 const markers:T.Mesh[]=[];
 for(const point of POINTS.filter(p=>!p.surfaceUnavailable))for(const side of point.midline?[1]:[1,-1]){
  const dot=new T.Mesh(sphere,normalMat);dot.userData={point:point.code,side};dot.position.fromArray(pointTarget(point.code,side as 1|-1).position);dots.add(dot);markers.push(dot);
 }
 const instruments=new Map<ToolId,T.Group>();for(const tool of TOOLS){const g=createInstrument(tool.id);g.visible=false;contact.add(g);instruments.set(tool.id,g);}
 const halo=new T.Mesh(new T.RingGeometry(.012,.015,64),new T.MeshBasicMaterial({color:'#5c9c91',transparent:true,opacity:.8,side:T.DoubleSide,depthWrite:false}));halo.position.z=.0018;contact.add(halo);
 const patch=new T.Mesh(new T.CircleGeometry(.03,64),new T.MeshBasicMaterial({color:'#79b8a6',transparent:true,opacity:.12,side:T.DoubleSide,depthWrite:false}));patch.position.z=.001;contact.add(patch);
 const pulses=Array.from({length:3},()=>{const mesh=new T.Mesh(new T.RingGeometry(.019,.020,64),new T.MeshBasicMaterial({color:'#5c9c91',transparent:true,opacity:0,side:T.DoubleSide,depthWrite:false}));mesh.position.z=.002;contact.add(mesh);return mesh;});
 const stroke=new T.Mesh(new T.PlaneGeometry(.05,.013),new T.MeshBasicMaterial({color:'#b4826c',transparent:true,opacity:0,side:T.DoubleSide,depthWrite:false}));stroke.position.z=.0025;contact.add(stroke);
 const trailRoot=new T.Group();trailRoot.name='manual-scrape-trail';root.add(trailRoot);const trailGeometry=new T.CircleGeometry(.0075,18),trailMaterial=new T.MeshBasicMaterial({color:'#b35e58',transparent:true,opacity:.12,side:T.DoubleSide,depthWrite:false});
 const trail=Array.from({length:128},()=>{const mesh=new T.Mesh(trailGeometry,trailMaterial);mesh.visible=false;trailRoot.add(mesh);return mesh;});let trailCount=0;const previousStroke=new T.Vector3(Infinity,Infinity,Infinity);
 const axis=new T.Vector3(0,0,1),n=new T.Vector3(),v=new T.Vector3(),origin=new T.Vector3();
 const ray=new T.Raycaster();let anchored=false,lastFocus=-1,lastRelease=0,releaseAt=-1;let skinSurface:T.Mesh|null=null;
 const label=document.createElement('button');label.type='button';label.className='contact-label tool-drag-handle';label.setAttribute('aria-label','移动当前工具');label.title='按住拖动工具；方向键微调位置';label.hidden=true;
 const resolvePoint=(code:string,side:1|-1)=>surface.get(`${code}:${POINT_BY_CODE.get(code)?.midline?1:side}`)??pointTarget(code,side);
 const pain=createPainOverlay(resolvePoint);root.add(pain.root);
 const meridianRoot=new T.Group();meridianRoot.name='meridian-paths';root.add(meridianRoot);
 const pointMaterials=new Map(MERIDIANS.map(m=>[m.id,new T.MeshStandardMaterial({color:m.color,roughness:.4})]));
 const getTarget=(s:SimulationState):SurfaceTarget|null=>s.pointCode&&POINT_BY_CODE.get(s.pointCode)?.surfaceUnavailable?null:s.customTarget??(s.pointCode?resolvePoint(s.pointCode,s.side):pain.target(s.pain));
 const snapTarget=(target:SurfaceTarget,maxDistance=.018):SurfaceTarget=>{
  let best=maxDistance*maxDistance,match:{code:string;side:1|-1;target:SurfaceTarget}|null=null;
  const position=new T.Vector3().fromArray(target.position),normal=new T.Vector3().fromArray(target.normal).normalize();
  for(const [key,candidate] of surface){
   const candidateNormal=new T.Vector3().fromArray(candidate.normal).normalize();
   if(normal.dot(candidateNormal)<.35)continue;
   const distance=position.distanceToSquared(new T.Vector3().fromArray(candidate.position));
   if(distance>=best)continue;
   const separator=key.lastIndexOf(':'),code=key.slice(0,separator),side=Number(key.slice(separator+1)) as 1|-1;
   best=distance;match={code,side,target:candidate};
  }
  return match?{...match.target,pointCode:match.code,pointSide:match.side}:target;
 };
 const anchor=(skin:T.Mesh)=>{
  if(anchored)return;skinSurface=skin;skin.updateMatrixWorld(true);pain.anchor(skin);
  if(useBaked&&baked.points.length){for(const dot of markers){const entry=baked.points.find(p=>p.key===`${dot.userData.point}:${dot.userData.side}`);if(!entry)continue;const target=entry.target as SurfaceTarget;surface.set(entry.key,target);dot.position.fromArray(target.position).addScaledVector(new T.Vector3().fromArray(target.normal),.003);}for(const path of baked.paths){const line=new T.LineSegments(new T.BufferGeometry().setAttribute('position',new T.Float32BufferAttribute(path.vertices,3)),new T.LineBasicMaterial({color:MERIDIANS.find(m=>m.id===path.id)!.color,transparent:true,opacity:.7}));line.userData.meridian=path.id;meridianRoot.add(line);}anchored=true;return;}
  for(const dot of markers){const seed=pointTarget(dot.userData.point,dot.userData.side);v.fromArray(seed.position);n.fromArray(seed.normal).normalize();origin.copy(v).addScaledVector(n,.18);ray.set(origin,n.clone().negate());ray.far=.36;
   const hits=ray.intersectObject(skin,false);let hit=hits.find(h=>h.point.distanceTo(v)<.14);
   // Bounded nearest triangle fallback for a grazing ray; this is geometric fitting only.
   if(!hit){const pos=skin.geometry.getAttribute('position'),idx=skin.geometry.index!,tri=new T.Triangle(),q=new T.Vector3();let best=.025*.025;
    for(let f=0;f<idx.count/3;f++){tri.a.fromBufferAttribute(pos,idx.getX(f*3));tri.b.fromBufferAttribute(pos,idx.getX(f*3+1));tri.c.fromBufferAttribute(pos,idx.getX(f*3+2));tri.closestPointToPoint(v,q);const d=q.distanceToSquared(v);if(d<best){best=d;hit={point:q.clone(),faceIndex:f,face:{normal:tri.getNormal(new T.Vector3())}} as T.Intersection;}}
   }
   if(hit){const outward=(hit.face?.normal.clone()??n.clone()).transformDirection(skin.matrixWorld);if(outward.dot(n)<0)outward.negate();const target:SurfaceTarget={position:hit.point.toArray() as [number,number,number],normal:outward.toArray() as [number,number,number],meshId:'FJ2810',faceIndex:hit.faceIndex};surface.set(`${dot.userData.point}:${dot.userData.side}`,target);dot.position.copy(hit.point).addScaledVector(outward,.003);}
  }
  // Number order is a teaching reference, not a reconstruction of internal circulation.
  // Break known branch transitions rather than drawing across unrelated body regions.
  const breaks=new Set(['BL41','BL55','KI11','LR13','ST9','GV25']);
  for(const m of MERIDIANS)for(const side of m.id==='GV'||m.id==='CV'?[1]:[1,-1]){
   const ordered=POINTS.filter(p=>p.meridianId===m.id&&!p.surfaceUnavailable&&p.code!=='GV24+');
   const vertices:T.Vector3[]=[];
   for(let i=1;i<ordered.length;i++){
    if(breaks.has(ordered[i].code))continue;
    const a=surface.get(`${ordered[i-1].code}:${side}`),b=surface.get(`${ordered[i].code}:${side}`);if(!a||!b)continue;
    const start=new T.Vector3().fromArray(a.position),end=new T.Vector3().fromArray(b.position);if(start.distanceTo(end)>.3)continue;
    let previous:T.Vector3|null=null;
    const steps=Math.max(2,Math.ceil(start.distanceTo(end)/.007));
    for(let k=0;k<=steps;k++){
     const t=k/steps,seed=start.clone().lerp(end,t),direction=new T.Vector3().fromArray(a.normal).lerp(new T.Vector3().fromArray(b.normal),t).normalize();
     ray.set(seed.clone().addScaledVector(direction,.045),direction.clone().negate());ray.far=.09;
     const hit=ray.intersectObject(skin,false).find(h=>h.point.distanceTo(seed)<.035);
     if(!hit){previous=null;continue;}
     const vertex=hit.point.clone().addScaledVector(direction,.0035);
     if(previous&&previous.distanceTo(vertex)<.025)vertices.push(previous,vertex);previous=vertex;
    }
   }
   const line=new T.LineSegments(new T.BufferGeometry().setFromPoints(vertices),new T.LineBasicMaterial({color:m.color,transparent:true,opacity:.7}));line.userData.meridian=m.id;meridianRoot.add(line);
  }
  anchored=true;
 };
 const update=(s:SimulationState,amount:number,camera:T.Camera,width:number,height:number)=>{
  root.visible=amount<.002;pain.update(s,root.visible&&s.pickMode!=='anatomy');dots.visible=s.showPoints;meridianRoot.visible=!!s.showMeridians&&s.pickMode!=='anatomy';for(const line of meridianRoot.children)line.visible=!s.meridianFilter||s.meridianFilter==='all'||line.userData.meridian===s.meridianFilter;const target=getTarget(s);contact.visible=!!target;
  for(const dot of markers){const selected=dot.userData.point===s.pointCode&&(dot.userData.side===s.side||POINT_BY_CODE.get(s.pointCode??'')?.midline);const meridian=POINT_BY_CODE.get(dot.userData.point)!.meridianId;dot.visible=!s.meridianFilter||s.meridianFilter==='all'||meridian===s.meridianFilter;dot.material=selected?activeMat:pointMaterials.get(meridian)??normalMat;dot.scale.setScalar(selected?1.35:1);}
  label.hidden=!target||!root.visible;
  if(s.tool!=='scraper'||s.elapsed===0){trailCount=0;previousStroke.set(Infinity,Infinity,Infinity);trail.forEach(mesh=>mesh.visible=false);}
  trailRoot.visible=s.effects&&!!target;trailMaterial.opacity=.10+s.amplitude*.08;
  if(!target)return null;
  contact.position.fromArray(target.position);n.fromArray(target.normal).normalize();contact.quaternion.setFromUnitVectors(axis,n);
  if(s.dragging&&s.tool==='scraper'&&s.effects&&contact.position.distanceTo(previousStroke)>.003){const mark=trail[trailCount%trail.length];mark.position.copy(contact.position).addScaledVector(n,.001);mark.quaternion.copy(contact.quaternion);mark.visible=true;trailCount++;previousStroke.copy(contact.position);}
  const tool=TOOLS.find(t=>t.id===s.tool)!;if((s.release??0)!==lastRelease){lastRelease=s.release??0;releaseAt=performance.now();}if(s.running||s.elapsed===0)releaseAt=-1;const releaseElapsed=releaseAt<0?0:(performance.now()-releaseAt)/1000;const envelope=releaseAt<0?1:releaseElapsed>.8?0:Math.exp(-releaseElapsed*7);const active=s.dragging&&s.tool==='needle'?0:s.elapsed>0?s.amplitude*envelope:0;const cycle=s.dragging?1:.5+.5*Math.sin(s.elapsed*2.5);
  for(const [id,g]of instruments){g.visible=id===s.tool; if(g.visible){updateInstrument(g,s.dragging?Math.PI/5:s.elapsed,active);if(s.dragging){const motion=g.getObjectByName('instrument-motion');if(motion){motion.position.x=0;motion.position.y=0;}}const indicator=g.getObjectByName("contact-indicator");if(indicator)indicator.visible=s.effects&&active>0;}}
  halo.scale.setScalar(s.tool==='cup'?1.9:s.tool==='finger'?1+cycle*.18*active:s.tool==='needle'?.6:1);patch.scale.set(s.tool==='stone'?.8:1,s.tool==='stone'?1.3:1,1);
  const skinColor=s.tool==='finger'?'#e3b4a0':s.tool==='cup'?'#a95157':s.tool==='scraper'?'#bc6962':s.tool==='stone'?'#d8a892':tool.color;halo.material.opacity=active>0?.18:.65;halo.material.color.set(tool.color);patch.material.color.set(skinColor);patch.material.opacity=s.effects&&active>0?.1+cycle*.09*active:0;
  for(let i=0;i<pulses.length;i++){const m=pulses[i],phase=(s.elapsed*.55+i/3)%1;m.scale.setScalar(s.tool==='needle'?.5+phase:s.tool==='moxa'?1.1+phase*1.5:s.tool==='cup'?1.45+phase*.1:1+phase*1.4);m.position.z=s.tool==='moxa'?.004+phase*.06:.002;m.material.opacity=s.effects&&s.tool==='moxa'&&active>0?(1-phase)*.32*active:0;m.material.color.set(tool.color);}
  stroke.position.x=Math.sin(s.elapsed*1.8)*.009;stroke.scale.set(s.tool==='scraper'?1+.2*Math.sin(s.elapsed*1.8):1,1,1);
  stroke.material.opacity=s.effects&&s.tool==='scraper'&&active>0?.18*active:0;
  v.copy(contact.position).project(camera);label.hidden=label.hidden||v.z< -1||v.z>1;label.style.left=`${(v.x+1)*width/2+20}px`;label.style.top=`${(1-v.y)*height/2-18}px`;label.textContent=`↔ 移动${tool.name} · ${s.customTarget?'自选部位':s.pain&&!s.pointCode?'痛处':POINTS.find(p=>p.code===s.pointCode)?.name??''}`;label.classList?.toggle('is-dragging',!!s.dragging);
  return {target,deform:s.effects&&root.visible?((s.tool==='finger'||s.tool==='stone')?-.006*cycle*active:s.tool==='cup'?.008*active:0):0,activity:s.effects&&root.visible?active:0,mark:s.effects&&root.visible&&s.elapsed>0?s.amplitude:0,color:skinColor,settling:releaseAt>=0&&releaseElapsed<=.85};
 };
 const focus=(s:SimulationState,camera:T.PerspectiveCamera,controls:{target:T.Vector3;update:()=>void},amount:number)=>{
  if(!s.focus||lastFocus===s.focus||amount>=.002||!anchored)return false;const target=getTarget(s);if(!target)return false;lastFocus=s.focus;camera.clearViewOffset();
  n.fromArray(target.normal).normalize();if(s.customTarget)n.copy(camera.position).sub(new T.Vector3().fromArray(target.position)).normalize();else if(n.y<-.8)n.set(.3,-.7,1).normalize();else n.add(new T.Vector3(.6,.25,0)).normalize();
  const targetPosition=new T.Vector3().fromArray(target.position);
  const candidates=[n.clone(),new T.Vector3(n.x*.3,.15,1).normalize(),new T.Vector3(n.x*.3,.15,-1).normalize(),new T.Vector3(0,.1,1).normalize()];
  // A medial point's outward normal can aim through the opposite limb. Keep the selected surface visible.
  if(skinSurface)for(const direction of candidates){ray.set(targetPosition.clone().addScaledVector(direction,.65),direction.clone().negate());ray.far=.68;const hit=ray.intersectObject(skinSurface,false)[0];if(!hit||hit.point.distanceTo(targetPosition)<.012){n.copy(direction);break;}}
  controls.target.copy(targetPosition);camera.position.copy(controls.target).addScaledVector(n,.65);controls.update();return true;
 };

 const getDraggables=(tool:ToolId)=>{const meshes:T.Object3D[]=[];instruments.get(tool)?.traverseVisible(o=>{if(o instanceof T.Mesh)meshes.push(o);});return meshes;};
 return {root,markers,label,anchor,update,focus,getTarget,getDraggables,snapTarget,exportSurface:()=>({points:[...surface].map(([key,target])=>({key,target})),paths:meridianRoot.children.map(o=>({id:o.userData.meridian as string,vertices:Array.from(((o as T.LineSegments).geometry.getAttribute('position').array))}))}),dispose:()=>{pain.dispose();pointMaterials.forEach(m=>m.dispose());meridianRoot.children.forEach(o=>{const l=o as T.LineSegments;l.geometry.dispose();(l.material as T.Material).dispose();});}};
}
