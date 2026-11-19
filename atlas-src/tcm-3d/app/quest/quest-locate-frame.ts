import {animePointTarget} from '../character-mode';
import {POINT_BY_CODE,type RegionId} from '../teaching';
import {questAvatarLocateCodes} from './quest-data';
import type {MeridianQuestId} from './types';

export type LocateFrameSpec={
 aim:[number,number,number];
 distance:number;
 viewDir:[number,number,number];
};

type BodyGroup='head-neck'|'chest-abdomen'|'back'|'upper-limb'|'lower-limb';

const FOV_DEG=34;
const MARKER_WORLD=.026;
const MIN_TAP_PX=24;

function regionGroup(region:RegionId):BodyGroup{
 if(region==='head')return 'head-neck';
 if(region==='abdomen')return 'chest-abdomen';
 if(region==='waist')return 'back';
 if(region==='shoulder'||region==='arm')return 'upper-limb';
 return 'lower-limb';
}

function worldPositions(codes:string[]):[number,number,number][]{
 const out:[number,number,number][]=[];
 for(const code of codes){
  const t=animePointTarget(code,1);
  if(t)out.push(t.position);
 }
 return out;
}

function bboxOf(pos:[number,number,number][],pad=0.15){
 let minX=Infinity,minY=Infinity,minZ=Infinity,maxX=-Infinity,maxY=-Infinity,maxZ=-Infinity;
 for(const [x,y,z] of pos){
  minX=Math.min(minX,x);maxX=Math.max(maxX,x);
  minY=Math.min(minY,y);maxY=Math.max(maxY,y);
  minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
 }
 const dx=(maxX-minX)*(1+pad*2),dy=(maxY-minY)*(1+pad*2),dz=(maxZ-minZ)*(1+pad*2);
 const cx=(minX+maxX)/2,cy=(minY+maxY)/2,cz=(minZ+maxZ)/2;
 return {center:[cx,cy,cz] as [number,number,number],halfY:dy/2,halfX:Math.max(dx,dz)/2};
}

function fitDistance(halfY:number,halfX:number,mobile:boolean,aspect:number,viewportHeight:number,hudReserved:number){
 const fov=FOV_DEG*Math.PI/180;
 const visible=Math.max(120,viewportHeight-hudReserved);
 const distY=halfY/Math.tan(fov/2);
 const distX=halfX/(Math.tan(fov/2)*aspect);
 const base=Math.max(distY,distX,0.42);
 return mobile?Math.max(base,visible/(2*Math.tan(fov/2))*.36):Math.max(base,mobile?0.9:.55);
}

function markerPxAt(distance:number,viewportHeight:number,hudReserved:number){
 const fov=FOV_DEG*Math.PI/180;
 const visible=Math.max(120,viewportHeight-hudReserved);
 return (MARKER_WORLD/distance)*visible/(2*Math.tan(fov/2));
}

function viewDirFor(region:RegionId|undefined):[number,number,number]{
 if(region==='head'||region==='shoulder')return [0,.02,-1];
 if(region==='waist')return [0,.02,1];
 if(region==='leg')return [.35,.08,-1];
 return [.42,.08,-1];
}

export function questLocateViewForAnswer(answerCode:string):'front'|'back'|'three-quarter'{
 const region=POINT_BY_CODE.get(answerCode)?.region;
 if(region==='head'||region==='shoulder')return 'front';
 if(region==='waist')return 'back';
 return 'three-quarter';
}

export function computeLocateFrame(
 meridianId:MeridianQuestId,
 answerCode:string,
 opts:{mobile:boolean;aspect:number;viewportHeight:number;hudReserved:number},
):LocateFrameSpec|null{
 const codes=questAvatarLocateCodes(meridianId);
 if(!codes.length)return null;
 let pool=codes;
 let positions=worldPositions(pool);
 if(!positions.length)return null;

 const answerRegion=POINT_BY_CODE.get(answerCode)?.region;
 const tryFit=(list:string[])=>{
  const pos=worldPositions(list);
  if(!pos.length)return null;
  const {center,halfY,halfX}=bboxOf(pos);
  const distance=fitDistance(halfY,halfX,opts.mobile,opts.aspect,opts.viewportHeight,opts.hudReserved);
  return {center,distance,halfY,halfX};
 };

 let fit=tryFit(pool);
 if(!fit)return null;

 if(opts.mobile){
  const px=markerPxAt(fit.distance,opts.viewportHeight,opts.hudReserved);
  if(px<MIN_TAP_PX&&answerRegion){
   const group=regionGroup(answerRegion);
   const grouped=pool.filter(c=>{
    const r=POINT_BY_CODE.get(c)?.region;
    return r&&regionGroup(r)===group;
   });
   if(grouped.length){
    const gfit=tryFit(grouped);
    if(gfit)fit=gfit;
   }
  }
 }

 const viewDir=viewDirFor(answerRegion);
 return {aim:fit.center,distance:fit.distance,viewDir};
}