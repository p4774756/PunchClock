import * as T from 'three';
import {createInstrument,updateInstrument} from './instruments';
import {POINTS,POINT_BY_CODE,TOOLS,curriculumSet,type SimulationState,type SurfaceTarget,type ToolId} from './teaching';
import type {AnimePoint} from './character-mode';

const SKIN_MESH_HINT=/Face_00_SKIN|Body_00_SKIN|FaceMouth/i;

/** Teaching overlay for the anime assistant: needle / finger on selected skin points. */
export function createTeachingOverlay(
 scene:T.Scene,
 opts:{points:AnimePoint[];resolvePoint:(code:string,side:1|-1)=>SurfaceTarget|null},
){
 const root=new T.Group(),dots=new T.Group(),contact=new T.Group();root.add(dots,contact);scene.add(root);
 const surface=new Map<string,SurfaceTarget>();
 const sphere=new T.SphereGeometry(.0042,14,10);
 const normalMat=new T.MeshStandardMaterial({color:'#4e958b',emissive:'#24463e',emissiveIntensity:.15,roughness:.3});
 const activeMat=new T.MeshStandardMaterial({color:'#e9ae58',emissive:'#b17830',emissiveIntensity:.4,roughness:.3});
 const markers:T.Mesh[]=[];
 for(const point of opts.points)for(const side of point.midline?[1 as const]:[1 as const,-1 as const]){
  const seed=opts.resolvePoint(point.code,side);
  if(!seed)continue;
  const dot=new T.Mesh(sphere,normalMat);dot.userData={point:point.code,side};
  dot.position.fromArray(seed.position).addScaledVector(new T.Vector3().fromArray(seed.normal).normalize(),.003);
  dots.add(dot);markers.push(dot);surface.set(`${point.code}:${side}`,seed);
 }
 const instruments=new Map<ToolId,T.Group>();
 for(const tool of TOOLS){const g=createInstrument(tool.id);g.visible=false;contact.add(g);instruments.set(tool.id,g);}
 const halo=new T.Mesh(new T.RingGeometry(.012,.015,64),new T.MeshBasicMaterial({color:'#5c9c91',transparent:true,opacity:.8,side:T.DoubleSide,depthWrite:false}));halo.position.z=.0018;contact.add(halo);
 const patch=new T.Mesh(new T.CircleGeometry(.03,64),new T.MeshBasicMaterial({color:'#79b8a6',transparent:true,opacity:.12,side:T.DoubleSide,depthWrite:false}));patch.position.z=.001;contact.add(patch);
 const pulses=Array.from({length:3},()=>{const mesh=new T.Mesh(new T.RingGeometry(.019,.020,64),new T.MeshBasicMaterial({color:'#5c9c91',transparent:true,opacity:0,side:T.DoubleSide,depthWrite:false}));mesh.position.z=.002;contact.add(mesh);return mesh;});
 const axis=new T.Vector3(0,0,1),n=new T.Vector3(),v=new T.Vector3(),origin=new T.Vector3();
 const ray=new T.Raycaster();let anchored=false,lastFocus=-1,lastRelease=0,releaseAt=-1;
 let skinMeshes:T.Object3D[]=[];
 let activeCurriculum:ReturnType<typeof curriculumSet>;
 const label=document.createElement('button');label.type='button';label.className='contact-label tool-drag-handle';label.setAttribute('aria-label','移動當前工具');label.title='按住拖動工具；方向鍵微調位置';label.hidden=true;
 const resolvePoint=(code:string,side:1|-1)=>{
  const key=`${code}:${POINT_BY_CODE.get(code)?.midline||opts.points.find(p=>p.code===code)?.midline?1:side}`;
  return surface.get(key)??opts.resolvePoint(code,side);
 };
 const getTarget=(s:SimulationState):SurfaceTarget|null=>s.customTarget??(s.pointCode?resolvePoint(s.pointCode,s.side):null);
 const allowedByCurriculum=(code:string)=>!activeCurriculum||activeCurriculum.codes.includes(code);
 const snapTarget=(target:SurfaceTarget,maxDistance=.018):SurfaceTarget=>{
  let best=maxDistance*maxDistance,match:{code:string;side:1|-1;target:SurfaceTarget}|null=null;
  const position=new T.Vector3().fromArray(target.position),normal=new T.Vector3().fromArray(target.normal).normalize();
  for(const [key,candidate] of surface){
   const candidateNormal=new T.Vector3().fromArray(candidate.normal).normalize();
   if(normal.dot(candidateNormal)<.35)continue;
   const distance=position.distanceToSquared(new T.Vector3().fromArray(candidate.position));
   if(distance>=best)continue;
   const separator=key.lastIndexOf(':'),code=key.slice(0,separator),side=Number(key.slice(separator+1)) as 1|-1;
   if(!allowedByCurriculum(code))continue;
   best=distance;match={code,side,target:candidate};
  }
  return match?{...match.target,pointCode:match.code,pointSide:match.side}:target;
 };
 const collectSkins=(rootObj:T.Object3D)=>{
  const found:T.Object3D[]=[];
  rootObj.traverse(obj=>{
   if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
   const mats=Array.isArray(obj.material)?obj.material:[obj.material];
   const name=`${mats.map(m=>m?.name||'').join(' ')} ${obj.name||''}`;
   if(SKIN_MESH_HINT.test(name))found.push(obj);
  });
  return found;
 };
 const anchor=(characterRoot:T.Object3D)=>{
  if(anchored)return;
  skinMeshes=collectSkins(characterRoot);
  if(!skinMeshes.length)return;
  characterRoot.updateMatrixWorld(true);
  for(const dot of markers){
   const seed=opts.resolvePoint(dot.userData.point,dot.userData.side)??{position:dot.position.toArray() as [number,number,number],normal:[0,0,-1]};
   v.fromArray(seed.position);n.fromArray(seed.normal).normalize();origin.copy(v).addScaledVector(n,.08);
   ray.set(origin,n.clone().negate());ray.far=.2;
   const hits=ray.intersectObjects(skinMeshes,false);
   let hit=hits.find(h=>h.point.distanceTo(v)<.08)??hits[0];
   if(!hit){ray.set(v.clone().add(new T.Vector3(0,0,-.12)),new T.Vector3(0,0,1));ray.far=.25;hit=ray.intersectObjects(skinMeshes,false)[0];}
   if(hit){
    const outward=(hit.face?.normal.clone()??n.clone()).transformDirection(hit.object.matrixWorld);
    if(outward.dot(n)<0)outward.negate();
    const hitMat=(hit.object as T.Mesh).material;
    const meshId=(Array.isArray(hitMat)?hitMat[0]:hitMat)?.name||hit.object.name||'anime-skin';
    const target:SurfaceTarget={position:hit.point.toArray() as [number,number,number],normal:outward.toArray() as [number,number,number],meshId,faceIndex:hit.faceIndex};
    surface.set(`${dot.userData.point}:${dot.userData.side}`,target);
    dot.position.copy(hit.point).addScaledVector(outward,.003);
   }else{
    surface.set(`${dot.userData.point}:${dot.userData.side}`,{...seed,meshId:'anime-skin'});
   }
  }
  anchored=true;
 };
 const update=(s:SimulationState,camera:T.Camera,width:number,height:number)=>{
  activeCurriculum=curriculumSet(s.curriculumSet==='all'?undefined:s.curriculumSet);
  root.visible=true;dots.visible=s.showPoints;
  const target=getTarget(s);contact.visible=!!target;
  for(const dot of markers){
   const selected=dot.userData.point===s.pointCode&&(dot.userData.side===s.side||opts.points.find(p=>p.code===s.pointCode)?.midline);
   const meridianOk=!s.meridianFilter||s.meridianFilter==='all'||POINT_BY_CODE.get(dot.userData.point)?.meridianId===s.meridianFilter;
   dot.visible=meridianOk&&allowedByCurriculum(dot.userData.point);
   dot.material=selected?activeMat:normalMat;
   dot.scale.setScalar(selected?1.35:1);
  }
  label.hidden=!target||!root.visible;
  if(!target)return null;
  contact.position.fromArray(target.position);n.fromArray(target.normal).normalize();contact.quaternion.setFromUnitVectors(axis,n);
  const tool=TOOLS.find(t=>t.id===s.tool)!;
  if((s.release??0)!==lastRelease){lastRelease=s.release??0;releaseAt=performance.now();}
  if(s.running||s.elapsed===0)releaseAt=-1;
  const releaseElapsed=releaseAt<0?0:(performance.now()-releaseAt)/1000;
  const envelope=releaseAt<0?1:releaseElapsed>.8?0:Math.exp(-releaseElapsed*7);
  const active=s.dragging&&s.tool==='needle'?0:s.elapsed>0?s.amplitude*envelope:0;
  const cycle=s.dragging?1:.5+.5*Math.sin(s.elapsed*2.5);
  for(const [id,g]of instruments){
   g.visible=id===s.tool;
   if(g.visible){
    updateInstrument(g,s.dragging?Math.PI/5:s.elapsed,active);
    if(s.dragging){const motion=g.getObjectByName('instrument-motion');if(motion){motion.position.x=0;motion.position.y=0;}}
   }
  }
  halo.scale.setScalar(s.tool==='finger'?1+cycle*.18*active:.6);
  patch.scale.set(1,1,1);
  const skinColor=s.tool==='finger'?'#e3b4a0':tool.color;
  halo.material.opacity=active>0?.18:.65;halo.material.color.set(tool.color);
  patch.material.color.set(skinColor);patch.material.opacity=s.effects&&active>0?.1+cycle*.09*active:0;
  for(let i=0;i<pulses.length;i++){const m=pulses[i];m.material.opacity=0;}
  v.copy(contact.position).project(camera);
  label.hidden=label.hidden||v.z<-1||v.z>1;
  label.style.left=`${(v.x+1)*width/2+20}px`;
  label.style.top=`${(1-v.y)*height/2-18}px`;
  label.textContent=`↔ 移動${tool.name} · ${s.customTarget?'自選部位':POINTS.find(p=>p.code===s.pointCode)?.name??''}`;
  label.classList?.toggle('is-dragging',!!s.dragging);
  return {
   target,
   deform:s.effects&&root.visible?(s.tool==='finger'?-.006*cycle*active:0):0,
   activity:s.effects&&root.visible?active:0,
   mark:s.effects&&root.visible&&s.elapsed>0?s.amplitude:0,
   color:skinColor,
   settling:releaseAt>=0&&releaseElapsed<=.85,
  };
 };
 const focus=(s:SimulationState,camera:T.PerspectiveCamera,controls:{target:T.Vector3;update:()=>void})=>{
  if(!s.focus||lastFocus===s.focus||!anchored)return false;
  const target=getTarget(s);if(!target)return false;lastFocus=s.focus;camera.clearViewOffset();
  const aim=new T.Vector3().fromArray(target.position);
  n.fromArray(target.normal).normalize();
  let distance=.62;
  if(s.customTarget){
   n.copy(camera.position).sub(aim).normalize();
   distance=.7;
  }else if(s.pointCode==='GV20'||n.y>.85){
   // 百會／巔頂：偏正前方略抬高，看見頭頂與臉，勿從正上鑽進髮內
   n.set(.06,.22,-.97).normalize();
   aim.y-=.1;aim.z-=.06;
   distance=1.35;
  }else if(s.pointCode==='ST9'||s.pointCode==='CV22'||s.pointCode==='CV23'){
   // 人迎／天突／廉泉：近距正前方，讓針／指壓清楚可見
   const side=Math.sign(n.x||(s.side||1))*.15;
   n.set(side,.04,-1).normalize();
   aim.y+=.02;aim.z-=.01;
   distance=.6;
  }else if(n.z>.3){
   // 背面穴：從後方看，勿把 viewDir.z 鎖成負值轉到正面
   n.set(n.x*.28,Math.max(.06,n.y*.25+.1),Math.max(.82,Math.abs(n.z))).normalize();
   distance=.78;
  }else if(Math.abs(n.x)>.55&&Math.abs(n.z)<.55){
   // 側面穴（如帶脈）：從側向看
   n.set(Math.sign(n.x)*Math.max(.82,Math.abs(n.x)),Math.max(.06,n.y*.25+.1),n.z*.28).normalize();
   distance=.75;
  }else if(n.y<-.75){
   n.set(.25,-.45,-1).normalize();
   distance=.7;
  }else{
   // 顏面等：沿外向法線略抬高並退到正前，保持工具與穴位在框內
   n.set(n.x*.28-.1,Math.max(.06,n.y*.25+.1),-Math.max(.82,Math.abs(n.z))).normalize();
   distance=.72;
  }
  controls.target.copy(aim);
  camera.position.copy(controls.target).addScaledVector(n,distance);
  controls.update();return true;
 };
 const getDraggables=(tool:ToolId)=>{const meshes:T.Object3D[]=[];instruments.get(tool)?.traverseVisible(o=>{if(o instanceof T.Mesh)meshes.push(o);});return meshes;};
 return {
  root,markers,label,anchor,update,focus,getTarget,getDraggables,snapTarget,
  getSkinMeshes:()=>skinMeshes,
  isAnchored:()=>anchored,
  dispose:()=>{
   normalMat.dispose();activeMat.dispose();sphere.dispose();
   instruments.forEach(g=>g.traverse(o=>{if(o instanceof T.Mesh){o.geometry.dispose();const ms=Array.isArray(o.material)?o.material:[o.material];ms.forEach(m=>m.dispose());}}));
  },
 };
}

export const ANIME_SKIN_MESH_PATTERN=SKIN_MESH_HINT.source;
