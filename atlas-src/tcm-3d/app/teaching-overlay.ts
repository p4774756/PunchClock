import * as T from 'three';
import {createInstrument,updateInstrument} from './instruments';
import {focusViewFromNormal} from './focus-view';
import {buildSkinRaycastProxiesIncremental} from './skin-raycast-proxy';
import {MERIDIANS,POINTS,POINT_BY_CODE,TOOLS,curriculumSet,meridianLinePaths,meridianLinesEnabled,type SimulationState,type SurfaceTarget,type ToolId,type MeridianPathSide} from './teaching';
import type {AnimePoint} from './character-mode';

const SKIN_MESH_HINT=/Face_00_SKIN|Body_00_SKIN|FaceMouth/i;

/** Teaching overlay for the anime assistant: needle / finger on selected skin points. */
export function createTeachingOverlay(
 scene:T.Scene,
 opts:{points:AnimePoint[];resolvePoint:(code:string,side:1|-1)=>SurfaceTarget|null;skipSkinRaycastRefine?:boolean},
){
 const skipSkinRaycastRefine=!!opts.skipSkinRaycastRefine;
 const root=new T.Group(),dots=new T.Group(),meridians=new T.Group(),contact=new T.Group();root.add(dots,meridians,contact);scene.add(root);
 const meridianMaterials=new Map<string,T.LineBasicMaterial>();
 const surface=new Map<string,SurfaceTarget>();
 const sphere=new T.SphereGeometry(.0042,14,10);
 const normalMat=new T.MeshStandardMaterial({color:'#4e958b',emissive:'#24463e',emissiveIntensity:.22,roughness:.35,metalness:0});
 const activeMat=new T.MeshStandardMaterial({color:'#f5c84a',emissive:'#d4881a',emissiveIntensity:.85,roughness:.25,metalness:.12});
 const pickRing=new T.Mesh(
  new T.RingGeometry(.0055,.0082,28),
  new T.MeshBasicMaterial({color:'#f8dc6e',transparent:true,opacity:.92,side:T.DoubleSide,depthTest:true,depthWrite:false}),
 );
 pickRing.visible=false;pickRing.renderOrder=20;dots.add(pickRing);
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
 const worldFromOverlayLocal=(local:[number,number,number],out:T.Vector3)=>{
  out.fromArray(local);
  if(root.parent)root.parent.localToWorld(out);
  return out;
 };
 const selectedMarker=(s:SimulationState)=>{
  if(!s.pointCode)return null;
  const midline=!!(POINT_BY_CODE.get(s.pointCode)?.midline||opts.points.find(p=>p.code===s.pointCode)?.midline);
  return markers.find(m=>m.userData.point===s.pointCode&&(midline||m.userData.side===s.side))??null;
 };
 const ray=new T.Raycaster();let anchored=false,lastFocus=-1,lastRelease=0,releaseAt=-1;
 let skinMeshes:T.Object3D[]=[];
 let raycastTargets:T.Object3D[]=[];
 let anchorSnapshot:Map<string,SurfaceTarget>|null=null;
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
   if(!obj.visible)return;
   if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
   const mats=Array.isArray(obj.material)?obj.material:[obj.material];
   const name=`${mats.map(m=>m?.name||'').join(' ')} ${obj.name||''}`;
   if(SKIN_MESH_HINT.test(name))found.push(obj);
  });
  return found;
 };
 const applySeed=(dot:T.Mesh,seed:SurfaceTarget,meshId='anime-skin')=>{
  n.fromArray(seed.normal).normalize();
  surface.set(`${dot.userData.point}:${dot.userData.side}`,{...seed,meshId});
  dot.position.fromArray(seed.position).addScaledVector(n,.003);
 };
 const snapDot=(dot:T.Mesh,seed:SurfaceTarget)=>{
  v.fromArray(seed.position);n.fromArray(seed.normal).normalize();origin.copy(v).addScaledVector(n,.08);
  if(raycastTargets.length){
   ray.set(origin,n.clone().negate());ray.far=.2;
   const hits=ray.intersectObjects(raycastTargets,false);
   let hit=hits.find(h=>h.point.distanceTo(v)<.08)??hits[0];
   if(!hit){ray.set(v.clone().add(new T.Vector3(0,0,-.12)),new T.Vector3(0,0,1));ray.far=.25;hit=ray.intersectObjects(raycastTargets,false)[0];}
   if(hit){
    const outward=(hit.face?.normal.clone()??n.clone()).transformDirection(hit.object.matrixWorld);
    if(outward.dot(n)<0)outward.negate();
    const midlinePoint=opts.points.find(p=>p.code===dot.userData.point)?.midline;
    if(midlinePoint&&Math.abs(hit.point.x)>.002){
     const rear=new T.Vector3(0,0,1);
     ray.set(new T.Vector3(0,hit.point.y,hit.point.z).addScaledVector(rear,-.12),rear);ray.far=.25;
     const midHit=ray.intersectObjects(raycastTargets,false)[0];
     if(midHit){
      hit=midHit;
      const o=(hit.face?.normal.clone()??rear).transformDirection(hit.object.matrixWorld);
      if(o.dot(rear)<0)o.negate();
      outward.copy(o);
      hit.point.x=0;
     }else hit.point.x=0;
    }
    if(midlinePoint){outward.x=0;outward.normalize();}
    const hitMat=(hit.object as T.Mesh).material;
    const meshId=(Array.isArray(hitMat)?hitMat[0]:hitMat)?.name||hit.object.name||'anime-skin';
    const target:SurfaceTarget={position:hit.point.toArray() as [number,number,number],normal:outward.toArray() as [number,number,number],meshId,faceIndex:hit.faceIndex};
    surface.set(`${dot.userData.point}:${dot.userData.side}`,target);
    dot.position.copy(hit.point).addScaledVector(outward,.003);
    return;
   }
  }
  applySeed(dot,seed);
 };
 let proxyBakePromise:Promise<void>|null=null;
 let lastMeridianKey='';
 const anchor=(characterRoot:T.Object3D)=>{
  if(anchored)return;
  if(root.parent!==characterRoot)characterRoot.add(root);
  skinMeshes=collectSkins(characterRoot);
  if(!skinMeshes.length)return;
  characterRoot.updateMatrixWorld(true);
  for(const dot of markers){
   const seed=opts.resolvePoint(dot.userData.point,dot.userData.side)??{position:dot.position.toArray() as [number,number,number],normal:[0,0,-1]};
   applySeed(dot,seed);
  }
  anchorSnapshot=new Map(surface);
  anchored=true;
 };
 const refineAnchorsFromProxies=()=>{
  if(skipSkinRaycastRefine||!raycastTargets.length)return;
  performance.mark('atlas:anchor-start');
  for(const dot of markers){
   const seed=surface.get(`${dot.userData.point}:${dot.userData.side}`)??opts.resolvePoint(dot.userData.point,dot.userData.side);
   if(seed)snapDot(dot,seed);
  }
  anchorSnapshot=new Map(surface);
  lastMeridianKey='';
  performance.mark('atlas:anchor-end');
  try{performance.measure('atlas:anchor','atlas:anchor-start','atlas:anchor-end');}catch{/* duplicate */}
 };
 const ensureSkinProxyBake=async(characterRoot:T.Object3D,disposed:()=>boolean)=>{
  if(raycastTargets.length)return;
  if(proxyBakePromise)return proxyBakePromise;
  proxyBakePromise=(async()=>{
   raycastTargets=await buildSkinRaycastProxiesIncremental(characterRoot,SKIN_MESH_HINT,disposed);
   if(disposed())return;
   refineAnchorsFromProxies();
  })();
  return proxyBakePromise;
 };
 /** Outfit switches share body geometry — restore baked targets without another raycast. */
 const applyCachedAnchors=()=>{
  if(!anchorSnapshot?.size)return;
  for(const dot of markers){
   const key=`${dot.userData.point}:${dot.userData.side}`;
   const target=anchorSnapshot.get(key);
   if(!target)continue;
   surface.set(key,target);
   v.fromArray(target.position);n.fromArray(target.normal).normalize();
   dot.position.copy(v).addScaledVector(n,.003);
  }
  lastMeridianKey='';
  anchored=true;
 };
 /** Adult rig: re-resolve dot + surface from bone bindings (standing pose, not T-pose bake). */
 const syncResolvedPoints=(resolve:(code:string,side:1|-1)=>SurfaceTarget|null)=>{
  for(const dot of markers){
   const code=dot.userData.point as string;
   const side=dot.userData.side as 1|-1;
   const target=resolve(code,side);
   if(!target)continue;
   applySeed(dot,target);
  }
  anchorSnapshot=new Map(surface);
  lastMeridianKey='';
  anchored=true;
 };
 const reanchor=(characterRoot:T.Object3D)=>{
  if(anchorSnapshot?.size){applyCachedAnchors();return;}
  anchored=false;
  anchor(characterRoot);
 };
 const markerPosition=(code:string,side:MeridianPathSide)=>{
  const want=side==='midline'?1:side;
  const dot=markers.find(m=>m.userData.point===code&&m.userData.side===want);
  return dot?dot.position.clone():null;
 };
 const meridianColor=(id:string)=>{
  const hex=MERIDIANS.find(m=>m.id===id)?.color??'#7aa8c4';
  let mat=meridianMaterials.get(hex);
  if(!mat){
   mat=new T.LineBasicMaterial({color:hex,transparent:true,opacity:.62,depthTest:true,depthWrite:false});
   meridianMaterials.set(hex,mat);
  }
  return mat;
 };
 const clearMeridianLines=()=>{
  while(meridians.children.length){
   const child=meridians.children[0];
   meridians.remove(child);
   if(child instanceof T.Line){
    child.geometry.dispose();
   }
  }
 };
 const syncMeridianLines=(s:SimulationState)=>{
  const bakedCodes=new Set(opts.points.map(p=>p.code));
  const enabled=meridianLinesEnabled(s.meridianFilter,s.showMeridians);
  const key=enabled?`${s.meridianFilter}:${s.showMeridians}:${[...bakedCodes].sort().join(',')}`:'off';
  if(key===lastMeridianKey)return;
  lastMeridianKey=key;
  clearMeridianLines();
  meridians.visible=enabled&&s.showPoints;
  if(!enabled){return;}
  const paths=meridianLinePaths(s.meridianFilter,bakedCodes);
  const mat=meridianColor(s.meridianFilter!);
  for(const path of paths){
   const verts:T.Vector3[]=[];
   for(const code of path.codes){
    const pos=markerPosition(code,path.side);
    if(pos)verts.push(pos);
   }
   if(verts.length<2)continue;
   const line=new T.Line(new T.BufferGeometry().setFromPoints(verts),mat);
   line.renderOrder=11;
   meridians.add(line);
  }
 };
 const update=(s:SimulationState,camera:T.Camera,width:number,height:number,show=true)=>{
  activeCurriculum=curriculumSet(s.curriculumSet==='all'?undefined:s.curriculumSet);
  root.visible=show;dots.visible=s.showPoints;
  syncMeridianLines(s);
  meridians.visible=meridianLinesEnabled(s.meridianFilter,s.showMeridians)&&s.showPoints&&show;
  const target=getTarget(s);contact.visible=!!target;
  let showPickRing=false;
  for(const dot of markers){
   const selected=dot.userData.point===s.pointCode&&(dot.userData.side===s.side||opts.points.find(p=>p.code===s.pointCode)?.midline);
   const meridianOk=!s.meridianFilter||s.meridianFilter==='all'||POINT_BY_CODE.get(dot.userData.point)?.meridianId===s.meridianFilter;
   dot.visible=meridianOk&&allowedByCurriculum(dot.userData.point);
   dot.material=selected?activeMat:normalMat;
   dot.scale.setScalar(selected?1.55:1);
   dot.renderOrder=selected?12:0;
   if(selected&&dot.visible){
    pickRing.position.copy(dot.position);
    pickRing.quaternion.copy(camera.quaternion);
    pickRing.visible=true;
    showPickRing=true;
   }
  }
  if(!showPickRing)pickRing.visible=false;
  label.hidden=!target||!root.visible;
  if(!target)return null;
  const activeDot=s.customTarget?null:selectedMarker(s);
  if(activeDot)contact.position.copy(activeDot.position);
  else contact.position.fromArray(target.position);
  n.fromArray(target.normal).normalize();contact.quaternion.setFromUnitVectors(axis,n);
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
  contact.getWorldPosition(v).project(camera);
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
 type FocusPose={aim:T.Vector3;viewDir:T.Vector3;distance:number};
 type FocusOptions={force?:boolean};
 const computeFocusPose=(s:SimulationState,camera:T.PerspectiveCamera):FocusPose|null=>{
  if(!s.focus||!anchored)return null;
  const target=getTarget(s);if(!target)return null;
  const aim=new T.Vector3();
  const activeDot=s.customTarget?null:selectedMarker(s);
  if(activeDot)activeDot.getWorldPosition(aim);
  else worldFromOverlayLocal(target.position,aim);
  n.fromArray(target.normal).normalize();
  let distance=.62;
  if(s.customTarget){
   n.copy(camera.position).sub(aim).normalize();
   distance=.7;
  }else{
   const framed=focusViewFromNormal(s.pointCode,n,s.side||1);
   n.copy(framed.viewDir);
   aim.add(framed.aimAdjust);
   distance=framed.distance;
  }
  return {aim,viewDir:n.clone(),distance};
 };
 const focus=(s:SimulationState,camera:T.PerspectiveCamera,controls:{target:T.Vector3;update:()=>void},options?:FocusOptions)=>{
  if(!options?.force&&lastFocus===s.focus)return false;
  const pose=computeFocusPose(s,camera);if(!pose)return false;
  lastFocus=s.focus;camera.clearViewOffset();
  controls.target.copy(pose.aim);
  camera.position.copy(pose.aim).addScaledVector(pose.viewDir,pose.distance);
  controls.update();return true;
 };
 const getDraggables=(tool:ToolId)=>{const meshes:T.Object3D[]=[];instruments.get(tool)?.traverseVisible(o=>{if(o instanceof T.Mesh)meshes.push(o);});return meshes;};
 return {
  root,markers,label,anchor,reanchor,applyCachedAnchors,syncResolvedPoints,ensureSkinProxyBake,update,focus,computeFocusPose,getTarget,getDraggables,snapTarget,
  getSkinMeshes:()=>skinMeshes,
  isAnchored:()=>anchored,
  hasSkinProxies:()=>raycastTargets.length>0,
  dispose:()=>{
   clearMeridianLines();
   meridianMaterials.forEach(m=>m.dispose());
   meridianMaterials.clear();
   normalMat.dispose();activeMat.dispose();sphere.dispose();
   pickRing.geometry.dispose();(pickRing.material as T.Material).dispose();
   instruments.forEach(g=>g.traverse(o=>{if(o instanceof T.Mesh){o.geometry.dispose();const ms=Array.isArray(o.material)?o.material:[o.material];ms.forEach(m=>m.dispose());}}));
  },
 };
}

export const ANIME_SKIN_MESH_PATTERN=SKIN_MESH_HINT.source;
