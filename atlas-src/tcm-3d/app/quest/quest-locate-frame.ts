import * as T from 'three';
import {animePointTarget} from '../character-mode';
import {focusViewFromNormal} from '../focus-view';
import {POINT_BY_CODE,type LocateFrameSpec,type LocateViewOffset,type RegionId} from '../teaching';
import {STUDIO_CAMERA_FOV_DEG} from '../camera-constants';
import {questAvatarLocateCodes} from './quest-data';
import type {MeridianQuestId} from './types';

export type {LocateFrameSpec,LocateViewOffset} from '../teaching';

type BodyGroup='head-neck'|'chest-abdomen'|'back'|'upper-limb'|'lower-limb';
type ViewSide='front'|'back'|'side';

const MIN_HALF_Y=.12;

function regionGroup(region:RegionId):BodyGroup{
 if(region==='head')return 'head-neck';
 if(region==='abdomen')return 'chest-abdomen';
 if(region==='waist')return 'back';
 if(region==='shoulder'||region==='arm')return 'upper-limb';
 return 'lower-limb';
}

function fnv1a(s:string){
 let h=2166136261;
 for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619);}
 return h>>>0;
}

function jitterFrac(seed:number){
 const u=(seed%10000)/10000;
 return(u-.5)*.24;
}

function worldPositions(codes:string[]):[number,number,number][]{
 const out:[number,number,number][]=[];
 for(const code of codes){
  const t=animePointTarget(code,1);
  if(t)out.push(t.position);
 }
 return out;
}

function viewSideForCode(code:string):ViewSide{
 const t=animePointTarget(code,1);
 if(!t)return 'front';
 const [nx,,nz]=t.normal;
 if(nz>.25)return 'back';
 if(Math.abs(nx)>.45&&Math.abs(nz)<.45)return 'side';
 return 'front';
}

function landmarkPositions(group:BodyGroup):[number,number,number][]{
 switch(group){
  case 'head-neck':
   return [[0,1.72,-.06],[0,1.48,-.06],[0,1.36,-.07],[0,1.63,-.05],[-.1,1.58,-.04],[.1,1.58,-.04]];
  case 'chest-abdomen':
   return [[0,1.32,-.08],[0,.98,-.07],[-.14,1.15,-.05],[.14,1.15,-.05]];
  case 'back':
   return [[0,1.38,.1],[0,1.05,.08],[-.12,1.22,.09],[.12,1.22,.09]];
  case 'upper-limb':
   return [[-.22,1.28,-.02],[.22,1.28,-.02],[-.24,1.05,.02],[.24,1.05,.02]];
  default:
   return [[-.08,.75,-.04],[.08,.75,-.04],[-.1,.35,.02],[.1,.35,.02]];
 }
}

function bboxOf(pos:[number,number,number][],pad=.15){
 let minX=Infinity,minY=Infinity,minZ=Infinity,maxX=-Infinity,maxY=-Infinity,maxZ=-Infinity;
 for(const [x,y,z] of pos){
  minX=Math.min(minX,x);maxX=Math.max(maxX,x);
  minY=Math.min(minY,y);maxY=Math.max(maxY,y);
  minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
 }
 const dx=(maxX-minX)*(1+pad*2),dy=(maxY-minY)*(1+pad*2),dz=(maxZ-minZ)*(1+pad*2);
 const cx=(minX+maxX)/2,cy=(minY+maxY)/2,cz=(minZ+maxZ)/2;
 return {
  center:[cx,cy,cz] as [number,number,number],
  halfY:Math.max(dy/2,MIN_HALF_Y),
  halfX:Math.max(dx/2,dz/2,MIN_HALF_Y*.85),
 };
}

function fitDistance(halfY:number,halfX:number,aspect:number,viewportHeight:number,hudTop:number,bottomReserved:number){
 const fov=STUDIO_CAMERA_FOV_DEG*Math.PI/180;
 const usable=Math.max(.35,(viewportHeight-hudTop-bottomReserved)/viewportHeight);
 const distY=halfY/(Math.tan(fov/2)*usable);
 const distX=halfX/(Math.tan(fov/2)*aspect*.9);
 return Math.min(2.2,Math.max(.35,Math.max(distY,distX)));
}

function viewOffsetFor(
 viewportWidth:number,
 viewportHeight:number,
 hudTop:number,
 bottomReserved:number,
 jitterX:number,
 jitterY:number,
 extraPanY:number,
):LocateViewOffset{
 const usableCenter=hudTop+(viewportHeight-hudTop-bottomReserved)/2;
 const lift=viewportHeight/2-usableCenter;
 const offsetY=Math.round(lift*(extraPanY?-.1:-.16)+extraPanY*.12+jitterY);
 return {
  fullWidth:Math.round(viewportWidth),
  fullHeight:Math.round(viewportHeight),
  offsetX:Math.round(jitterX),
  offsetY,
  width:Math.round(viewportWidth),
  height:Math.round(viewportHeight),
 };
}

function answerVerticalPan(answerCode:string,center:[number,number,number],distance:number,viewportHeight:number){
 const ans=animePointTarget(answerCode,1);
 if(!ans)return 0;
 const dy=center[1]-ans.position[1];
 if(Math.abs(dy)<.015)return 0;
 const fov=STUDIO_CAMERA_FOV_DEG*Math.PI/180;
 const pxPerWorld=viewportHeight/(2*distance*Math.tan(fov/2));
 return Math.round(dy*pxPerWorld*.55);
}

const HEAD_LATERAL_CODES=new Set([
 'TE17','TE21','TE23','GB1','GB2','SI19','SI18','GB14','EX-HN5','ST6','LI20',
]);

export function isBackLocate(code:string):boolean{
 const t=animePointTarget(code,1);
 if(!t)return false;
 if(t.normal[2]>.15)return true;
 const region=POINT_BY_CODE.get(code)?.region;
 return region==='waist';
}

export function isHeadLateralLocate(code:string):boolean{
 const region=POINT_BY_CODE.get(code)?.region;
 if(region!=='head'&&!HEAD_LATERAL_CODES.has(code))return false;
 if(HEAD_LATERAL_CODES.has(code))return true;
 const t=animePointTarget(code,1);
 if(!t)return false;
 const [nx,,nz]=t.normal;
 return Math.abs(nx)>.32&&Math.abs(nz)<.55;
}

/** 頭側顳／耳穴：3/4 前方（略高於耳），不可側後方。 */
export function headLateralViewDir(answerCode:string):[number,number,number]{
 const t=animePointTarget(answerCode,1);
 const sign=Math.sign(t?.position[0]??t?.normal[0]??1)||1;
 const ear=/^(SI19|TE17|TE21|GB2|GB1)$/u.test(answerCode);
 const v=new T.Vector3(-sign*(ear?.38:.48),ear?.1:.14,-.9).normalize();
 return [v.x,v.y,v.z];
}

export function backLocateViewDirs():[number,number,number][]{
 const raw=[
  new T.Vector3(0,.08,.96),
  new T.Vector3(.22,.12,.92),
  new T.Vector3(-.22,.12,.92),
  new T.Vector3(.12,.18,.95),
  new T.Vector3(-.12,.18,.95),
 ];
 return raw.map(v=>{v.normalize();return [v.x,v.y,v.z] as [number,number,number];});
}

export function locateViewDirCandidates(answerCode:string,frameCodes:string[]):[number,number,number][]{
 if(isBackLocate(answerCode))return backLocateViewDirs();
 if(!isHeadLateralLocate(answerCode)){
  return [viewDirFromCodes(frameCodes,answerCode)];
 }
 const t=animePointTarget(answerCode,1);
 const sign=Math.sign(t?.position[0]??t?.normal[0]??1)||1;
 const raw=[
  new T.Vector3(-sign*.48,.14,-.86),
  new T.Vector3(-sign*.26,.12,-.94),
  new T.Vector3(-sign*.62,.16,-.72),
  new T.Vector3(-sign*.18,.1,-.98),
  new T.Vector3(0,.08,-1),
 ];
 const seen=new Set<string>(),out:[number,number,number][]=[];
 for(const v of raw){
  v.normalize();
  const key=v.toArray().map(n=>n.toFixed(2)).join(',');
  if(seen.has(key))continue;
  seen.add(key);
  out.push([v.x,v.y,v.z]);
 }
 return out;
}

function viewDirFromCodes(codes:string[],answerCode:string):[number,number,number]{
 if(isHeadLateralLocate(answerCode))return headLateralViewDir(answerCode);
 const t0=animePointTarget(answerCode,1);
 const region=POINT_BY_CODE.get(answerCode)?.region;
 if(t0&&t0.normal[2]>.2&&region!=='head'){
  const {viewDir}=focusViewFromNormal(answerCode,new T.Vector3().fromArray(t0.normal).normalize(),1);
  return [viewDir.x,viewDir.y,viewDir.z];
 }
 const avg=new T.Vector3();
 let n=0;
 for(const code of codes){
  const t=animePointTarget(code,1);
  if(!t)continue;
  avg.add(new T.Vector3().fromArray(t.normal));
  n++;
 }
 if(n)avg.multiplyScalar(1/n).normalize();
 else avg.set(0,.02,-1);
 const {viewDir}=focusViewFromNormal(answerCode,avg,1);
 return [viewDir.x,viewDir.y,viewDir.z];
}

function buildFrameCodes(pool:string[],answerCode:string,answerRegion:RegionId|undefined){
 const group=answerRegion?regionGroup(answerRegion):'chest-abdomen';
 let codes=pool.filter(c=>{
  const r=POINT_BY_CODE.get(c)?.region;
  return r&&regionGroup(r)===group;
 });
 if(!codes.includes(answerCode))codes=[...codes,answerCode];
 if(codes.length<3){
  const side=viewSideForCode(answerCode);
  const sameSide=pool.filter(c=>viewSideForCode(c)===side);
  codes=[...new Set([...codes,...sameSide])];
 }
 return {codes,group};
}

function framePositions(codes:string[],group:BodyGroup){
 let pos=worldPositions(codes);
 if(pos.length<3)pos=[...pos,...landmarkPositions(group)];
 return pos;
}

export function questLocateViewForAnswer(answerCode:string):'front'|'back'|'three-quarter'{
 if(isHeadLateralLocate(answerCode))return 'three-quarter';
 const side=viewSideForCode(answerCode);
 if(side==='back')return 'back';
 if(side==='side')return 'three-quarter';
 return 'front';
}

export function computeLocateFrame(
 meridianId:MeridianQuestId,
 answerCode:string,
 opts:{
  mobile:boolean;
  aspect:number;
  viewportWidth:number;
  viewportHeight:number;
  hudTop:number;
  bottomReserved:number;
  seed?:number;
  questionIndex?:number;
 },
):LocateFrameSpec|null{
 const codes=questAvatarLocateCodes(meridianId);
 if(!codes.length)return null;
 const answerRegion=POINT_BY_CODE.get(answerCode)?.region;
 const {codes:frameCodes,group}=buildFrameCodes(codes,answerCode,answerRegion);
 const positions=framePositions(frameCodes,group);
 if(!positions.length)return null;

 const {center,halfY,halfX}=bboxOf(positions);
 const distance=fitDistance(halfY,halfX,opts.aspect,opts.viewportHeight,opts.hudTop,opts.bottomReserved);
 const viewDir=viewDirFromCodes(frameCodes,answerCode);

 const jitKey=fnv1a(`${opts.seed??0}:${answerCode}:${opts.questionIndex??0}`);
 const jitterX=jitterFrac(jitKey)*opts.viewportWidth;
 const jitterY=jitterFrac(jitKey>>>12)*opts.viewportHeight;
 const extraPan=opts.mobile?answerVerticalPan(answerCode,center,distance,opts.viewportHeight):0;
 const viewOffset=viewOffsetFor(opts.viewportWidth,opts.viewportHeight,opts.hudTop,opts.bottomReserved,jitterX,jitterY,extraPan);

 return {aim:center,distance,viewDir,viewOffset,answerCode,frameCodes};
}