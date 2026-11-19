import {animePointTarget} from '../character-mode';
import {POINT_BY_CODE,type LocateFrameSpec,type LocateViewOffset,type RegionId} from '../teaching';
import {STUDIO_CAMERA_FOV_DEG} from '../camera-constants';
import {questAvatarLocateCodes} from './quest-data';
import type {MeridianQuestId} from './types';

export type {LocateFrameSpec,LocateViewOffset} from '../teaching';

type BodyGroup='head-neck'|'chest-abdomen'|'back'|'upper-limb'|'lower-limb';

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

function fitDistance(halfY:number,halfX:number,aspect:number,viewportHeight:number,hudTop:number,bottomReserved:number){
 const fov=STUDIO_CAMERA_FOV_DEG*Math.PI/180;
 const usable=Math.max(0.35,(viewportHeight-hudTop-bottomReserved)/viewportHeight);
 const distY=halfY/(Math.tan(fov/2)*usable);
 const distX=halfX/(Math.tan(fov/2)*aspect*0.9);
 return Math.min(2.2,Math.max(0.35,Math.max(distY,distX)));
}

function markerPxAt(distance:number,viewportHeight:number){
 const fov=STUDIO_CAMERA_FOV_DEG*Math.PI/180;
 return MARKER_WORLD/(2*distance*Math.tan(fov/2))*viewportHeight;
}

function viewOffsetFor(viewportWidth:number,viewportHeight:number,hudTop:number,bottomReserved:number):LocateViewOffset{
 const usableCenter=hudTop+(viewportHeight-hudTop-bottomReserved)/2;
 const offsetY=Math.max(0,Math.round(usableCenter-viewportHeight/2));
 return {
  fullWidth:Math.round(viewportWidth),
  fullHeight:Math.round(viewportHeight),
  offsetX:0,
  offsetY,
  width:Math.round(viewportWidth),
  height:Math.round(viewportHeight),
 };
}

function aimVerticalNudge(viewportHeight:number,hudTop:number,bottomReserved:number):number{
 return (bottomReserved-hudTop)/viewportHeight*0.55;
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
 opts:{mobile:boolean;aspect:number;viewportWidth:number;viewportHeight:number;hudTop:number;bottomReserved:number},
):LocateFrameSpec|null{
 const codes=questAvatarLocateCodes(meridianId);
 if(!codes.length)return null;
 const pool=codes;
 let positions=worldPositions(pool);
 if(!positions.length)return null;

 const answerRegion=POINT_BY_CODE.get(answerCode)?.region;
 const tryFit=(list:string[])=>{
  const pos=worldPositions(list);
  if(!pos.length)return null;
  const {center,halfY,halfX}=bboxOf(pos);
  const distance=fitDistance(halfY,halfX,opts.aspect,opts.viewportHeight,opts.hudTop,opts.bottomReserved);
  return {center,distance,halfY,halfX};
 };

 let fit=tryFit([answerCode])??tryFit(pool);
 if(!fit)return null;

 if(opts.mobile){
  let px=markerPxAt(fit.distance,opts.viewportHeight);
  if(px<MIN_TAP_PX){
   const single=tryFit([answerCode]);
   if(single){
    fit=single;
    px=markerPxAt(fit.distance,opts.viewportHeight);
   }
  }
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
 const viewOffset=viewOffsetFor(opts.viewportWidth,opts.viewportHeight,opts.hudTop,opts.bottomReserved);
 const nudge=aimVerticalNudge(opts.viewportHeight,opts.hudTop,opts.bottomReserved);
 const aim:[number,number,number]=[fit.center[0],fit.center[1]+nudge,fit.center[2]];
 return {aim,distance:fit.distance,viewDir,viewOffset};
}