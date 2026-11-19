import {ANIME_MODEL_BYTES} from './anime-model-digest';
import {publicAssetUrl} from './public-assets';
import {animePointTarget} from './character-mode';
import {animeAvatar,avatarPointTarget,avatarPracticePoints,type AvatarId} from './avatars';
import {animeOutfit,ANIME_OUTFITS,type AnimeOutfit,type OutfitId} from './outfits';
import {useEffect,useRef} from 'react';
import * as T from 'three';
import {OrbitControls} from 'three/examples/jsm/controls/OrbitControls.js';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import {clone as cloneSkinnedObject} from 'three/examples/jsm/utils/SkeletonUtils.js';
import {VRMLoaderPlugin,VRMUtils} from '@pixiv/three-vrm';
import {MToonMaterialOutlineWidthMode} from '@pixiv/three-vrm-materials-mtoon';
import {PointerTap} from './pointer-tap';
import {createTeachingOverlay} from './teaching-overlay';
import {installSurfaceToolInput} from './surface-tool-input';
import {focusViewFromNormal} from './focus-view';
import type {SceneState} from './anatomy';
import {STUDIO_CAMERA_FOV_DEG} from './camera-constants';
import type {SimulationState,SurfaceTarget,ToolDragPhase} from './teaching';

export {focusViewFromNormal} from './focus-view';

interface Props{
 state:SceneState;
 simulation:SimulationState;
 avatarId:AvatarId;
 outfitId:OutfitId;
 onPoint:(code:string,side:1|-1)=>void;
 onToolDrag:(target:SurfaceTarget|null,phase:ToolDragPhase)=>void;
 onProgress:(n:number)=>void;
 onError:(s:string)=>void;
 onPracticeOutfitPreparing?:(preparing:boolean)=>void;
 /** 闖關模式：隱藏穴名工具標籤、停用針／指壓拖曳。 */
 questMode?:boolean;
}

/** Three-step cel shade ramp for MeshToonMaterial.gradientMap (and leftover dispose). */
const toonGradient=()=>{
 const data=new Uint8Array([90,90,90,255,168,168,168,255,255,255,255,255]);
 const tex=new T.DataTexture(data,3,1,T.RGBAFormat);
 tex.magFilter=T.NearestFilter;tex.minFilter=T.NearestFilter;tex.needsUpdate=true;return tex;
};

type MToonLike=T.Material & {
 isMToonMaterial?:boolean;
 shadeColorFactor?:T.Color;
 shadingToonyFactor?:number;
 shadingShiftFactor?:number;
 giEqualizationFactor?:number;
 outlineWidthFactor?:number;
 outlineWidthMode?:MToonMaterialOutlineWidthMode;
 outlineColorFactor?:T.Color;
 parametricRimColorFactor?:T.Color;
 rimLightingMixFactor?:number;
 matcapFactor?:T.Color;
};

const isHairMaterial=(mat:T.Material)=>/HAIR|HairBack|PracticeHairBun/i.test(mat.name||'');

/** TV anime cel look: sharp shade bands + ink outline; keep #75 hide／hair／practice intact. */
const applyCelShading=(root:T.Object3D,gradient:T.DataTexture)=>{
 root.traverse((obj:T.Object3D)=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as MToonLike & T.MeshStandardMaterial & {isMeshToonMaterial?:boolean;gradientMap?:T.Texture|null;envMapIntensity?:number};
   const hair=isHairMaterial(m);
   if(m.isMToonMaterial){
    m.shadingToonyFactor=hair?.88:.94;
    m.shadingShiftFactor=Math.min(m.shadingShiftFactor??0,hair?-.02:-.06);
    if(m.shadeColorFactor)m.shadeColorFactor.multiplyScalar(hair?.97:.88);
    m.giEqualizationFactor=Math.min(m.giEqualizationFactor??1,hair?.64:.52);
    m.outlineWidthMode=MToonMaterialOutlineWidthMode.WorldCoordinates;
    const ow=m.outlineWidthFactor??0.0024;
    m.outlineWidthFactor=Math.min(Math.max(ow,.0016),.0022);
    if(m.outlineColorFactor)m.outlineColorFactor.setRGB(.12,.09,.07);
    if(m.parametricRimColorFactor)m.parametricRimColorFactor.setRGB(0,0,0);
    if(m.rimLightingMixFactor!=null)m.rimLightingMixFactor=0;
    if(m.matcapFactor)m.matcapFactor.multiplyScalar(.3);
    m.needsUpdate=true;
    continue;
   }
   if(m.isMeshToonMaterial){
    m.gradientMap=gradient;
    if(hair&&m.color)m.color.copy(m.color).multiplyScalar(1.04);
    m.needsUpdate=true;
    continue;
   }
   if(m.isMeshStandardMaterial||(m as T.MeshPhysicalMaterial).isMeshPhysicalMaterial){
    m.roughness=Math.max(m.roughness??.5,.82);
    m.metalness=Math.min(m.metalness??0,.04);
    if(m.envMapIntensity!=null)m.envMapIntensity=.12;
    m.needsUpdate=true;
   }
  }
 });
};

/** 360° woodcut landscape panorama on an open inward cylinder (see issue #104). */
type WoodcutBackdrop={mesh:T.Mesh;tex:T.Texture;mat:T.Material;geo:T.BufferGeometry};

/** Map mesh angle so central 1280 px (u≈.344–.656) covers ±40° behind avatar; sides use fade extensions. */
const remapWoodcutPanoramaUv=(geo:T.BufferGeometry,radial:number)=>{
 const uv=geo.getAttribute('uv') as T.BufferAttribute;
 const perRing=radial+1;
 const frontHalf=T.MathUtils.degToRad(40);
 const uLo=.34375,uHi=.65625,uSpan=uHi-uLo;
 const f=(phi:number)=>{
  if(Math.abs(phi)<=frontHalf)return .5+(phi/frontHalf)*(uSpan*.5);
  if(phi>frontHalf)return uHi+(phi-frontHalf)/(Math.PI-frontHalf)*(1-uHi);
  return uLo*(1-(phi+frontHalf)/(-Math.PI+frontHalf));
 };
 for(let i=0;i<uv.count;i++){
  const ring=i%perRing;
  const theta=ring/radial*Math.PI*2;
  let phi=-Math.PI/2-theta;
  let shift=0;
  if(phi<=-Math.PI){phi+=Math.PI*2;shift=-1;}
  uv.setX(i,f(phi)+shift);
 }
 uv.needsUpdate=true;
};

const buildWoodcutBackdrop=(tex:T.Texture):WoodcutBackdrop=>{
 tex.colorSpace=T.SRGBColorSpace;
 tex.wrapS=T.RepeatWrapping;
 tex.wrapT=T.ClampToEdgeWrapping;
 tex.needsUpdate=true;
 const mat=new T.MeshBasicMaterial({
  map:tex,
  side:T.BackSide,
  depthWrite:false,
  transparent:true,
  opacity:.84,
  color:new T.Color('#fffaf4'),
 });
 const radius=15;
 const height=radius*1.396*(720/1280);
 const bottomY=1.4-radius*Math.tan(T.MathUtils.degToRad(17));
 const centerY=bottomY+height/2;
 const geo=new T.CylinderGeometry(radius,radius,height,96,1,true);
 remapWoodcutPanoramaUv(geo,96);
 const mesh=new T.Mesh(geo,mat);
 mesh.position.set(0,centerY,0);
 // Texture u=0.5 (center blank) behind avatar at world −Z when camera sits on +Z.
 mesh.rotation.y=Math.PI/2;
 mesh.renderOrder=-2;
 return {mesh,tex,mat,geo};
};

const makeFeetGround=()=>{
 const canvas=document.createElement('canvas');
 canvas.width=256;canvas.height=256;
 const ctx=canvas.getContext('2d')!;
 const g=ctx.createRadialGradient(128,128,0,128,128,128);
 g.addColorStop(0,'rgba(232,220,196,1)');
 g.addColorStop(.42,'rgba(232,220,196,.75)');
 g.addColorStop(1,'rgba(232,220,196,0)');
 ctx.fillStyle=g;ctx.fillRect(0,0,256,256);
 const map=new T.CanvasTexture(canvas);
 map.colorSpace=T.SRGBColorSpace;
 return new T.Mesh(
  new T.CircleGeometry(3.5,64),
  new T.MeshBasicMaterial({map,transparent:true,depthWrite:false}),
 );
};

const disposeWoodcutBackdrop=(woodcut:WoodcutBackdrop|null)=>{
 if(!woodcut)return;
 woodcut.tex.dispose();
 woodcut.mat.dispose();
 woodcut.geo.dispose();
};

const materialLabel=(mat:T.Material,obj:T.Object3D)=>`${mat.name||''} ${obj.name||''}`;

/** Warm fill for MASK cutouts — chroma above #f6efe4 so restored skin is not a beige void. */
const SKIN_FILL=new T.Color(232/255,198/255,178/255);
/** Deep teal as sRGB bytes — do not feed hex through ColorManagement (would go near-black on the map). */
const PRACTICE_TEAL_SRGB={r:13,g:74,b:79};
/** VRoid Sendagaya Shino hair — lit / shade sRGB when GLB MToon is not sampled. */
const HAIR_LIT=new T.Color(0x192338);

const inTorsoSkinBand=(x:number,y:number,z:number)=>{
 if(y<.8||y>1.42||Math.abs(x)>.22)return false;
 // Skip clavicle／shoulder shelf — flat fill there reads as bright patches.
 if(y>1.30&&Math.abs(x)>.07)return false;
 if(y>1.33&&Math.abs(z)<.08)return false;
 return Math.abs(z)<.16||(y>1.15&&Math.abs(z)<.14);
};

/** Sports bra: front covers breasts below 天突; back strap sits under GV14／BL14. */
const inPracticeBra=(x:number,y:number,z:number)=>{
 if(Math.abs(x)>.13)return false;
 // Blend front／back y bands across the side so the strap meets without a step.
 const t=Math.max(0,Math.min(1,(z+.02)/.08));
 const y0=1.15+(1.17-1.15)*t;
 const y1=1.235+(1.23-1.235)*t;
 return y>=y0&&y<=y1;
};

/** High-waist shorts: below 神闕／命門, cover white underwear front＋back. */
const inPracticeShorts=(x:number,y:number,z:number)=>{
 if(y<.8||y>.97||Math.abs(x)>.16)return false;
 return Math.abs(z)<.14;
};

const writeUvTriangleFill=(data:ImageData,width:number,height:number,flipY:boolean,u0:number,v0:number,u1:number,v1:number,u2:number,v2:number,rgb:T.Color)=>{
 const toX=(u:number)=>Math.min(width-1,Math.max(0,Math.round(u*(width-1))));
 const toY=(v:number)=>Math.min(height-1,Math.max(0,Math.round((flipY?1-v:v)*(height-1))));
 const x0=toX(u0),y0=toY(v0),x1=toX(u1),y1=toY(v1),x2=toX(u2),y2=toY(v2);
 const minX=Math.max(0,Math.min(x0,x1,x2)),maxX=Math.min(width-1,Math.max(x0,x1,x2));
 const minY=Math.max(0,Math.min(y0,y1,y2)),maxY=Math.min(height-1,Math.max(y0,y1,y2));
 const area=(x1-x0)*(y2-y0)-(x2-x0)*(y1-y0);
 if(area===0)return;
 for(let y=minY;y<=maxY;y++)for(let x=minX;x<=maxX;x++){
  const w0=((x1-x)*(y2-y)-(x2-x)*(y1-y))/area;
  const w1=((x2-x)*(y0-y)-(x0-x)*(y2-y))/area;
  const w2=1-w0-w1;
  if(w0<-.01||w1<-.01||w2<-.01)continue;
  const i=(y*width+x)*4;
  if(data.data[i+3]>=200)continue;
  data.data[i]=rgb.r*255;
  data.data[i+1]=rgb.g*255;
  data.data[i+2]=rgb.b*255;
  data.data[i+3]=255;
 }
};

/** Paint cloth only where interpolated world position is inside the garment band (smooth edges). */
const writeUvTriangleCloth=(data:ImageData,width:number,height:number,flipY:boolean,u0:number,v0:number,wa:T.Vector3,u1:number,v1:number,wb:T.Vector3,u2:number,v2:number,wc:T.Vector3,pred:(x:number,y:number,z:number)=>boolean,rgb:{r:number;g:number;b:number})=>{
 const toX=(u:number)=>Math.min(width-1,Math.max(0,Math.round(u*(width-1))));
 const toY=(v:number)=>Math.min(height-1,Math.max(0,Math.round((flipY?1-v:v)*(height-1))));
 const x0=toX(u0),y0=toY(v0),x1=toX(u1),y1=toY(v1),x2=toX(u2),y2=toY(v2);
 const minX=Math.max(0,Math.min(x0,x1,x2)),maxX=Math.min(width-1,Math.max(x0,x1,x2));
 const minY=Math.max(0,Math.min(y0,y1,y2)),maxY=Math.min(height-1,Math.max(y0,y1,y2));
 const area=(x1-x0)*(y2-y0)-(x2-x0)*(y1-y0);
 if(area===0)return;
 for(let y=minY;y<=maxY;y++)for(let x=minX;x<=maxX;x++){
  const w0=((x1-x)*(y2-y)-(x2-x)*(y1-y))/area;
  const w1=((x2-x)*(y0-y)-(x0-x)*(y2-y))/area;
  const w2=1-w0-w1;
  if(w0<-.01||w1<-.01||w2<-.01)continue;
  const wx=wa.x*w0+wb.x*w1+wc.x*w2;
  const wy=wa.y*w0+wb.y*w1+wc.y*w2;
  const wz=wa.z*w0+wb.z*w1+wc.z*w2;
  if(!pred(wx,wy,wz))continue;
  const i=(y*width+x)*4;
  data.data[i]=rgb.r;
  data.data[i+1]=rgb.g;
  data.data[i+2]=rgb.b;
  data.data[i+3]=255;
 }
};

const looksLikeSkinPixel=(r:number,g:number,b:number,a:number)=>{
 if(a<200)return false;
 if(r<40&&g>55&&b>65)return false;
 // Reject orange-lit／vest bleed that skews shoulder inpaint warm.
 if(r>g+12||r>b+10)return false;
 return r>92&&g>72&&b>62&&r>=g-20;
};

type ShoulderHoleBounds={minX:number;maxX:number;minY:number;maxY:number};
type SkinRgb={r:number;g:number;b:number};

/** One pass over padded triangle AABB — fallback when dilation cannot reach a hole. */
const averageSkinInBounds=(data:ImageData,width:number,height:number,bounds:ShoulderHoleBounds):SkinRgb|null=>{
 const loX=Math.max(0,bounds.minX),hiX=Math.min(width-1,bounds.maxX);
 const loY=Math.max(0,bounds.minY),hiY=Math.min(height-1,bounds.maxY);
 let sumR=0,sumG=0,sumB=0,n=0;
 for(let y=loY;y<=hiY;y++)for(let x=loX;x<=hiX;x++){
  const i=(y*width+x)*4;
  if(!looksLikeSkinPixel(data.data[i],data.data[i+1],data.data[i+2],data.data[i+3]))continue;
  sumR+=data.data[i];sumG+=data.data[i+1];sumB+=data.data[i+2];n++;
 }
 if(!n)return null;
 return {r:Math.round(sumR/n),g:Math.round(sumG/n),b:Math.round(sumB/n)};
};

const fillShoulderHolePixel=(data:ImageData,width:number,height:number,x:number,y:number,rgb:SkinRgb)=>{
 const i=(y*width+x)*4;
 data.data[i]=rgb.r;data.data[i+1]=rgb.g;data.data[i+2]=rgb.b;data.data[i+3]=255;
};

/** Outer shoulder MASK cutouts — outside inTorsoSkinBand (clavicle guard unchanged). */
const inOuterShoulderWorld=(x:number,y:number,z:number)=>{
 if(y<1.24||y>1.36)return false;
 const ax=Math.abs(x);
 if(ax<=.06||ax>=.32)return false;
 return true;
};

const paintShoulderAlphaHole=(data:ImageData,width:number,height:number,x:number,y:number)=>{
 const i=(y*width+x)*4;
 if(data.data[i+3]>=200)return true;
 return dilateShoulderHoleFromNeighbors(data,width,height,x,y);
};

const dilateShoulderHoleFromNeighbors=(data:ImageData,width:number,height:number,x:number,y:number)=>{
 const i=(y*width+x)*4;
 if(data.data[i+3]>=200)return true;
 for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
  if(!dx&&!dy)continue;
  const nx=x+dx,ny=y+dy;
  if(nx<0||ny<0||nx>=width||ny>=height)continue;
  const j=(ny*width+nx)*4;
  if(data.data[j+3]<200)continue;
  if(!looksLikeSkinPixel(data.data[j],data.data[j+1],data.data[j+2],data.data[j+3]))continue;
  data.data[i]=data.data[j];data.data[i+1]=data.data[j+1];data.data[i+2]=data.data[j+2];data.data[i+3]=255;
  return true;
 }
 return false;
};

const writeUvTriangleShoulderFix=(data:ImageData,width:number,height:number,flipY:boolean,u0:number,v0:number,u1:number,v1:number,u2:number,v2:number,fallbackSkin:SkinRgb)=>{
 const toX=(u:number)=>Math.min(width-1,Math.max(0,Math.round(u*(width-1))));
 const toY=(v:number)=>Math.min(height-1,Math.max(0,Math.round((flipY?1-v:v)*(height-1))));
 const x0=toX(u0),y0=toY(v0),x1=toX(u1),y1=toY(v1),x2=toX(u2),y2=toY(v2);
 const minX=Math.max(0,Math.min(x0,x1,x2)),maxX=Math.min(width-1,Math.max(x0,x1,x2));
 const minY=Math.max(0,Math.min(y0,y1,y2)),maxY=Math.min(height-1,Math.max(y0,y1,y2));
 const area=(x1-x0)*(y2-y0)-(x2-x0)*(y1-y0);
 if(area===0)return;
 const pad=24;
 const sampleBounds:ShoulderHoleBounds={minX:minX-pad,maxX:maxX+pad,minY:minY-pad,maxY:maxY+pad};
 const fillRgb=averageSkinInBounds(data,width,height,sampleBounds)??fallbackSkin;
 const holes:{x:number;y:number}[]=[];
 for(let y=minY;y<=maxY;y++)for(let x=minX;x<=maxX;x++){
  const w0=((x1-x)*(y2-y)-(x2-x)*(y1-y))/area;
  const w1=((x2-x)*(y0-y)-(x0-x)*(y2-y))/area;
  const w2=1-w0-w1;
  if(w0<-.01||w1<-.01||w2<-.01)continue;
  const i=(y*width+x)*4;
  if(data.data[i+3]>=200)continue;
  if(!paintShoulderAlphaHole(data,width,height,x,y))holes.push({x,y});
 }
 for(let pass=0;pass<48&&holes.length;pass++){
  const remain:typeof holes=[];
  for(const {x,y} of holes){
   if(x<minX||x>maxX||y<minY||y>maxY)continue;
   if(!dilateShoulderHoleFromNeighbors(data,width,height,x,y))remain.push({x,y});
  }
  if(remain.length===holes.length)break;
  holes.length=0;holes.push(...remain);
 }
 for(const {x,y} of holes)fillShoulderHolePixel(data,width,height,x,y,fillRgb);
 for(let y=minY;y<=maxY;y++)for(let x=minX;x<=maxX;x++){
  const w0=((x1-x)*(y2-y)-(x2-x)*(y1-y))/area;
  const w1=((x2-x)*(y0-y)-(x0-x)*(y2-y))/area;
  const w2=1-w0-w1;
  if(w0<-.01||w1<-.01||w2<-.01)continue;
  const i=(y*width+x)*4;
  if(data.data[i+3]>=200)continue;
  fillShoulderHolePixel(data,width,height,x,y,fillRgb);
 }
};

type MToonUniforms=Record<string,{value:unknown}>;

const findSkinMap=(m:T.MeshStandardMaterial & {map?:T.Texture|null;uniforms?:Record<string,{value:unknown}>})=>{
 if(m.map?.image)return m.map;
 const uni=m.uniforms?.map?.value;
 if(uni&&typeof uni==='object'&&'image' in (uni as object)&&(uni as T.Texture).image)return uni as T.Texture;
 return null;
};

const skinMapUniform=(m:T.MeshStandardMaterial & {uniforms?:MToonUniforms})=>{
 const uni=m.uniforms?.map?.value;
 if(uni&&typeof uni==='object'&&'isTexture' in (uni as object))return uni as T.Texture;
 return m.map??null;
};

/** MToon samples uniforms.map — setter alone can miss WebGL refresh after clone／fork. */
const setMToonBodyMap=(m:T.MeshStandardMaterial,tex:T.Texture)=>{
 m.map=tex;
 const uni=(m as T.MeshStandardMaterial & {uniforms?:MToonUniforms}).uniforms;
 if(uni?.map)uni.map.value=tex;
 const mtoon=m as MToonLike & {_uploadUniformsWorkaround?:()=>void;uniformsNeedUpdate?:boolean};
 if(mtoon.isMToonMaterial&&mtoon._uploadUniformsWorkaround)mtoon._uploadUniformsWorkaround();
 if('uniformsNeedUpdate' in mtoon)mtoon.uniformsNeedUpdate=true;
 tex.needsUpdate=true;
 m.needsUpdate=true;
};

const MTOON_TEXTURE_UNIFORM_KEYS=new Set([
 'map','normalMap','emissiveMap','shadeMultiplyTexture','shadingShiftTexture','matcapTexture',
 'rimMultiplyTexture','outlineWidthMultiplyTexture','uvAnimationMaskTexture',
]);

/**
 * Unmask Body_00_SKIN under hidden Tops／Bottoms, then paint teal bra＋shorts into the
 * same lit MToon map so cloth follows the skinned mesh (no separate Mesh / no onBeforeCompile).
 */
const paintPracticeBodySkin=(root:T.Object3D)=>{
 const skinMats:T.MeshStandardMaterial[]=[];
 const skinMeshes:(T.Mesh|T.SkinnedMesh)[]=[];
 root.traverse((obj:T.Object3D)=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  const body=mats.filter(m=>/Body_00_SKIN/i.test(m.name||'')&&!/Outline/i.test(m.name||'')) as T.MeshStandardMaterial[];
  if(!body.length)return;
  skinMeshes.push(obj);
  for(const m of body)if(!skinMats.includes(m))skinMats.push(m);
 });
 if(!skinMeshes.length||!skinMats.length)return;
 const src=skinMats.map(findSkinMap).find(Boolean)??null;
 if(!src?.image)return;
 const img=src.image as CanvasImageSource & {width:number;height:number};
 const canvas=document.createElement('canvas');
 canvas.width=img.width;canvas.height=img.height;
 const ctx=canvas.getContext('2d',{willReadFrequently:true});
 if(!ctx)return;
 try{ctx.drawImage(img,0,0);}catch{return;}
 const data=ctx.getImageData(0,0,canvas.width,canvas.height);
 let r=0,g=0,b=0,n=0;
 for(let i=0;i<data.data.length;i+=16){
  if(data.data[i+3]<200)continue;
  r+=data.data[i];g+=data.data[i+1];b+=data.data[i+2];n++;
 }
 const skinRgb=n?new T.Color(r/n/255,g/n/255,b/n/255):SKIN_FILL.clone();
 const fallbackSkin:SkinRgb={r:Math.round(skinRgb.r*255),g:Math.round(skinRgb.g*255),b:Math.round(skinRgb.b*255)};
 const flipY=!!src.flipY;
 let touched=0;
 for(const mesh of skinMeshes){
  const geom=mesh.geometry;
  const pos=geom.getAttribute('position');
  const uv=geom.getAttribute('uv');
  const index=geom.index;
  if(!pos||!uv||!index)continue;
  mesh.updateMatrixWorld(true);
  const a=new T.Vector3(),b=new T.Vector3(),c=new T.Vector3();
  for(let i=0;i<index.count;i+=3){
   const i0=index.getX(i),i1=index.getX(i+1),i2=index.getX(i+2);
   a.fromBufferAttribute(pos,i0).applyMatrix4(mesh.matrixWorld);
   b.fromBufferAttribute(pos,i1).applyMatrix4(mesh.matrixWorld);
   c.fromBufferAttribute(pos,i2).applyMatrix4(mesh.matrixWorld);
   const cx=(a.x+b.x+c.x)/3,cy=(a.y+b.y+c.y)/3,cz=(a.z+b.z+c.z)/3;
   const torso=inTorsoSkinBand(a.x,a.y,a.z)||inTorsoSkinBand(b.x,b.y,b.z)||inTorsoSkinBand(c.x,c.y,c.z)||inTorsoSkinBand(cx,cy,cz);
   if(!torso)continue;
   const u0=uv.getX(i0),v0=uv.getY(i0),u1=uv.getX(i1),v1=uv.getY(i1),u2=uv.getX(i2),v2=uv.getY(i2);
   writeUvTriangleFill(data,canvas.width,canvas.height,flipY,u0,v0,u1,v1,u2,v2,skinRgb);
   // Broad AABB cull: only triangles that could intersect bra／shorts get per-texel paint.
   const yMin=Math.min(a.y,b.y,c.y),yMax=Math.max(a.y,b.y,c.y);
   if(yMax>=.8&&yMin<=1.24){
    writeUvTriangleCloth(data,canvas.width,canvas.height,flipY,u0,v0,a,u1,v1,b,u2,v2,c,(x,y,z)=>inPracticeBra(x,y,z)||inPracticeShorts(x,y,z),PRACTICE_TEAL_SRGB);
   }
   touched++;
  }
  for(let i=0;i<index.count;i+=3){
   const i0=index.getX(i),i1=index.getX(i+1),i2=index.getX(i+2);
   a.fromBufferAttribute(pos,i0).applyMatrix4(mesh.matrixWorld);
   b.fromBufferAttribute(pos,i1).applyMatrix4(mesh.matrixWorld);
   c.fromBufferAttribute(pos,i2).applyMatrix4(mesh.matrixWorld);
   const cx=(a.x+b.x+c.x)/3,cy=(a.y+b.y+c.y)/3,cz=(a.z+b.z+c.z)/3;
   const shoulder=inOuterShoulderWorld(a.x,a.y,a.z)||inOuterShoulderWorld(b.x,b.y,b.z)||inOuterShoulderWorld(c.x,c.y,c.z)||inOuterShoulderWorld(cx,cy,cz);
   if(!shoulder)continue;
   const u0=uv.getX(i0),v0=uv.getY(i0),u1=uv.getX(i1),v1=uv.getY(i1),u2=uv.getX(i2),v2=uv.getY(i2);
   writeUvTriangleShoulderFix(data,canvas.width,canvas.height,flipY,u0,v0,u1,v1,u2,v2,fallbackSkin);
   touched++;
  }
 }
 if(!touched)return;
 ctx.putImageData(data,0,0);
 const next=src.clone();
 next.image=canvas;
 next.colorSpace=src.colorSpace??T.SRGBColorSpace;
 next.generateMipmaps=true;
 next.minFilter=T.LinearMipmapLinearFilter;
 next.magFilter=T.LinearFilter;
 next.needsUpdate=true;
 for(const m of skinMats)setMToonBodyMap(m,next);
 for(const mesh of skinMeshes){
  const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   if(!/Body_00_SKIN/i.test(m.name||'')||/Outline/i.test(m.name||''))continue;
   setMToonBodyMap(m,next);
  }
 }
 return next;
};

const copyMToonTextureUniform=(bun:MToonUniforms,hair:MToonUniforms,key:string)=>{
 const src=hair[key]?.value;
 if(!src||typeof src!=='object'||!('isTexture' in (src as object)))return;
 const tex=src as T.Texture;
 const dst=bun[key];
 if(dst)dst.value=tex;
};

/** Bun MToon matches primary hair lit／shade and map; strip parametric rim only. */
const syncHairBunMaterial=(bun:MToonLike,hair:MToonLike)=>{
 const h=hair as MToonLike & {litColorFactor?:T.Color;color?:T.Color;map?:T.Texture|null};
 const b=bun as MToonLike & {litColorFactor?:T.Color;color?:T.Color;map?:T.Texture|null};
 if(h.color&&b.color)b.color.copy(h.color);
 if(h.litColorFactor&&b.litColorFactor)b.litColorFactor.copy(h.litColorFactor);
 if(hair.shadeColorFactor&&bun.shadeColorFactor)bun.shadeColorFactor.copy(hair.shadeColorFactor);
 if(h.map)b.map=h.map;
 const hUni=(hair as MToonLike & {uniforms?:MToonUniforms}).uniforms;
 const bUni=(bun as MToonLike & {uniforms?:MToonUniforms}).uniforms;
 if(hUni&&bUni){
  for(const key of ['map','shadeMultiplyTexture','matcap','emissiveMap'] as const)copyMToonTextureUniform(bUni,hUni,key);
  const hLit=hUni.litFactor?.value;
  const bLit=bUni.litFactor?.value;
  if(hLit instanceof T.Color&&bLit instanceof T.Color)bLit.copy(hLit);
  const hShade=hUni.shadeColorFactor?.value;
  const bShade=bUni.shadeColorFactor?.value;
  if(hShade instanceof T.Color&&bShade instanceof T.Color)bShade.copy(hShade);
 }
 bun.shadingToonyFactor=hair.shadingToonyFactor;
 bun.shadingShiftFactor=hair.shadingShiftFactor;
 if(bun.parametricRimColorFactor)bun.parametricRimColorFactor.setRGB(0,0,0);
 if(bun.rimLightingMixFactor!=null)bun.rimLightingMixFactor=0;
 bun.needsUpdate=true;
};

const cloneHairMaterialForBun=(hair:MToonLike)=>{
 const bun=hair.clone() as MToonLike;
 bun.name='PracticeHairBun';
 syncHairBunMaterial(bun,hair);
 return bun;
};

type MeshAssetSnapshot={materials:unknown[];geometry:unknown;indexCount:number|null};

const snapshotMeshAssets=(root:T.Object3D)=>{
 const snap=new Map<string,MeshAssetSnapshot>();
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  snap.set(obj.uuid,{materials:[...mats],geometry:obj.geometry,indexCount:obj.geometry.index?.count??null});
 });
 return snap;
};

const assertUniformOutfitUntouched=(before:Map<string,MeshAssetSnapshot>,school:T.Object3D)=>{
 const err:string[]=[];
 school.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const prev=before.get(obj.uuid);
  if(!prev)return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  if(prev.geometry!==obj.geometry)err.push(`geometry mutated on ${obj.name||obj.uuid}`);
  if(prev.indexCount!==(obj.geometry.index?.count??null))err.push(`index length mutated on ${obj.name||obj.uuid}`);
  if(prev.materials.length!==mats.length)err.push(`material slot count mutated on ${obj.name||obj.uuid}`);
  for(let i=0;i<mats.length;i++){
   if(prev.materials[i]!==mats[i])err.push(`material identity mutated on ${obj.name||obj.uuid} slot ${i}`);
  }
 });
 if(err.length)throw new Error(`school outfit touched by practice build: ${err.join('; ')}`);
};

const isOutlineMaterial=(m:T.Material)=>{
 const x=m as MToonLike & {isOutline?:boolean};
 return !!(x.isOutline||/Outline/i.test(m.name||''));
};

const syncMaterialRenderState=(dst:T.Material,src:T.Material)=>{
 dst.transparent=src.transparent;
 dst.opacity=src.opacity;
 dst.alphaTest=src.alphaTest;
 dst.depthWrite=src.depthWrite;
 dst.depthTest=src.depthTest;
 dst.side=src.side;
 dst.blending=src.blending;
 dst.polygonOffset=src.polygonOffset;
 dst.polygonOffsetFactor=src.polygonOffsetFactor;
 dst.polygonOffsetUnits=src.polygonOffsetUnits;
};

const forkUniformValue=(value:unknown):unknown=>{
 if(value instanceof T.Color)return value.clone();
 if(value instanceof T.Vector2)return value.clone();
 if(value instanceof T.Vector3)return value.clone();
 if(value instanceof T.Matrix3)return value.clone();
 if(value instanceof T.Matrix4)return value.clone();
 return value;
};

/** MToon ShaderMaterial.clone() shares Color uniforms with the school rig — fork on practice only. */
const detachMToonUniforms=(dst:MToonLike,src:MToonLike)=>{
 const su=(src as {uniforms?:MToonUniforms}).uniforms;
 const du=(dst as {uniforms?:MToonUniforms}).uniforms;
 if(!su||!du)return;
 for(const key of Object.keys(su)){
  if(MTOON_TEXTURE_UNIFORM_KEYS.has(key))continue;
  const slot=du[key];
  if(!slot)continue;
  slot.value=forkUniformValue(su[key].value);
 }
};

/** MToon.copy() aliases school map — fork texture uniforms so practice can swap maps safely. */
const forkMToonTextureUniforms=(mat:MToonLike)=>{
 const uni=(mat as {uniforms?:MToonUniforms}).uniforms;
 if(!uni)return;
 for(const key of MTOON_TEXTURE_UNIFORM_KEYS){
  const slot=uni[key];
  const tex=slot?.value;
  if(!tex||typeof tex!=='object'||!('isTexture' in (tex as object)))continue;
  const forked=(tex as T.Texture).clone();
  forked.needsUpdate=true;
  slot.value=forked;
  if(key==='map')setMToonBodyMap(mat as T.MeshStandardMaterial,forked);
 }
};

const clonePracticeMaterial=(src:T.Material)=>{
 const dst=src.clone();
 syncMaterialRenderState(dst,src);
 const s=src as MToonLike;
 const d=dst as MToonLike;
 if(s.isMToonMaterial){
  detachMToonUniforms(d,s);
  forkMToonTextureUniforms(d);
 }
 const sm=src as T.MeshStandardMaterial;
 const dm=dst as T.MeshStandardMaterial;
 if(sm.map&&dm.map)dm.map.colorSpace=sm.map.colorSpace;
 return dst;
};

const collectBodySkinMapTex=(root:T.Object3D,sink:Set<T.Texture>)=>{
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   if(!/Body_00_SKIN/i.test(m.name||'')||/Outline/i.test(m.name||''))continue;
   const map=skinMapUniform(m);
   if(map)sink.add(map);
  }
 });
};

const assertPracticeBodySkinMaps=(practice:T.Object3D,school:T.Object3D,painted:T.Texture)=>{
 const schoolMaps=new Set<T.Texture>();
 const practiceMaps=new Set<T.Texture>();
 collectBodySkinMapTex(school,schoolMaps);
 collectBodySkinMapTex(practice,practiceMaps);
 for(const map of practiceMaps){
  if(map===painted)continue;
  if(schoolMaps.has(map))throw new Error('practice Body_00_SKIN map still references school albedo');
  if(!(map.image instanceof HTMLCanvasElement))throw new Error('practice Body_00_SKIN map must be painted canvas');
 }
 for(const map of schoolMaps){
  if(map===painted)throw new Error('school outfit map was replaced by practice paint');
 }
 practice.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   if(!/Body_00_SKIN/i.test(m.name||'')||/Outline/i.test(m.name||''))continue;
   const viaUniform=skinMapUniform(m);
   if(viaUniform!==painted)throw new Error('Body mesh render map uniform must be the painted skin texture');
  }
 });
};

const isPracticeUniformMesh=(obj:T.Mesh|T.SkinnedMesh,outfit:AnimeOutfit)=>{
 if(!outfit.practiceBody||!outfit.hideMaterials.length)return false;
 const mats=Array.isArray(obj.material)?obj.material:[obj.material];
 const surfaces=mats.filter(m=>!isOutlineMaterial(m));
 if(!surfaces.length)return false;
 return surfaces.every(m=>outfit.hideMaterials.some(token=>materialLabel(m,obj).includes(token)));
};

/** SkeletonUtils.clone shares geometry／material — deep-copy on practice rig only. */
const isolatePracticeRig=(practice:T.Object3D)=>{
 practice.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  obj.geometry=obj.geometry.clone();
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  const next=mats.map(clonePracticeMaterial);
  obj.material=next.length===1?next[0]:next;
 });
};

const disposeCharacterRoot=(root:T.Object3D|null)=>{
 if(!root)return;
 root.traverse(o=>{
  if(!(o instanceof T.Mesh)&&!(o instanceof T.SkinnedMesh))return;
  o.geometry.dispose();
  const ms=Array.isArray(o.material)?o.material:[o.material];
  ms.forEach(m=>m.dispose());
 });
};

const findPrimaryHairMaterial=(root:T.Object3D):MToonLike|null=>{
 let found:MToonLike|null=null;
 root.traverse((obj:T.Object3D)=>{
  if(found)return;
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  const label=mats.map(m=>materialLabel(m,obj)).join(' ');
  if(!/Hair_00_HAIR_01/i.test(label))return;
  for(const mat of mats){
   const m=mat as MToonLike;
   if(m.isMToonMaterial&&!/Outline/i.test(m.name||'')){found=m;break;}
  }
 });
 return found;
};

/** Drop nape／occiput hair triangles; keep bangs and crown. Bun parents to Head bone. */
const restyleHairBun=(root:T.Object3D)=>{
 root.traverse((obj:T.Object3D)=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  const name=mats.map(m=>materialLabel(m,obj)).join(' ');
  if(/HairBack|Hair_00_HAIR_01/i.test(name)){
   const geom=obj.geometry;
   const pos=geom.getAttribute('position');
   const index=geom.index;
   if(pos&&index){
    obj.updateMatrixWorld(true);
    const keep:number[]=[];
    const a=new T.Vector3(),b=new T.Vector3(),c=new T.Vector3();
    for(let i=0;i<index.count;i+=3){
     const i0=index.getX(i),i1=index.getX(i+1),i2=index.getX(i+2);
     a.fromBufferAttribute(pos,i0).applyMatrix4(obj.matrixWorld);
     b.fromBufferAttribute(pos,i1).applyMatrix4(obj.matrixWorld);
     c.fromBufferAttribute(pos,i2).applyMatrix4(obj.matrixWorld);
     const cy=(a.y+b.y+c.y)/3,cz=(a.z+b.z+c.z)/3;
     // Nape／lower occiput blocks 風府／風池／啞門／天柱.
     if(cz>.01&&cy<1.47)continue;
     keep.push(i0,i1,i2);
    }
    geom.setIndex(keep);
    geom.computeVertexNormals();
   }
  }else if(/Hair_00_HAIR_02/i.test(name)){
   const m=mats[0] as T.MeshStandardMaterial;
   m.transparent=true;
   m.opacity=Math.min(m.opacity??1,.9);
   // Translucent hair must not write depth or sorting against skin／bun glitches.
   m.depthWrite=false;
   m.needsUpdate=true;
  }
 });
 const head=root.getObjectByName('J_Bip_C_Head');
 if(!head)return;
 const hairSrc=findPrimaryHairMaterial(root);
 const bunMat=hairSrc?cloneHairMaterialForBun(hairSrc):new T.MeshToonMaterial({color:HAIR_LIT.clone()});
 if(!hairSrc)(bunMat as T.MeshToonMaterial).color.copy(HAIR_LIT);
 const bun=new T.Mesh(new T.SphereGeometry(.055,20,16),bunMat as T.Material);
 bun.name='PracticeHairBun';
 // Crown／occiput bun — lower half under hair, top ~2.5–3.5 cm above hair line.
 bun.position.set(0,.205,.065);
 bun.scale.set(1.02,.86,1.02);
 bun.frustumCulled=false;
 head.add(bun);
 const coilMat=hairSrc?cloneHairMaterialForBun(hairSrc):(bunMat as T.Material).clone();
 const coil=new T.Mesh(new T.TorusGeometry(.038,.013,8,20),coilMat);
 coil.name='PracticeHairBunCoil';
 coil.position.set(0,.192,.063);
 coil.rotation.x=Math.PI/2;
 coil.frustumCulled=false;
 head.add(coil);
};

const applyOutfitMaterials=(root:T.Object3D,outfit:AnimeOutfit)=>{
 root.traverse((obj:T.Object3D)=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  obj.frustumCulled=false;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial & {isMToonMaterial?:boolean};
   const name=materialLabel(m,obj);
   const hide=outfit.hideMaterials.some(token=>name.includes(token));
   m.visible=!hide;
   if(hide){m.needsUpdate=true;continue;}
   // MToon manages its own texture color space; forcing SRGB can mute litFactor maps.
   if(m.map&&!m.isMToonMaterial){m.map.colorSpace=T.SRGBColorSpace;m.map.needsUpdate=true;}
   if(outfit.practiceBody){
    // Hair trimmed／bun separately; do not force school-uniform translucency.
   }else if(/HAIR|髪|HairBack/i.test(name)){
    m.transparent=true;
    m.opacity=/HairBack|Back/i.test(name)?Math.min(m.opacity??1,.42):Math.min(m.opacity??1,.55);
    m.depthWrite=false;
    m.side=T.DoubleSide;
    m.needsUpdate=true;
   }else if(outfit.softenMaterials.some(token=>name.includes(token))){
    m.transparent=true;
    m.opacity=Math.min(m.opacity??1,.78);
    m.depthWrite=true;
    m.needsUpdate=true;
   }
  }
  if(outfit.practiceBody&&isPracticeUniformMesh(obj,outfit))obj.visible=false;
 });
 // Never add MeshBasic flesh balloons or hook MToon onBeforeCompile.
 if(outfit.practiceBody){
  const painted=paintPracticeBodySkin(root);
  if(painted)root.userData.practiceBodySkinMap=painted;
  restyleHairBun(root);
 }
};

const finishCharacterLook=(root:T.Object3D,outfit:AnimeOutfit,gradient:T.DataTexture)=>{
 applyOutfitMaterials(root,outfit);
 applyCelShading(root,gradient);
};

type OutfitBridge={setOutfit:(id:OutfitId)=>void};

export default function CharacterScene({state,simulation,avatarId,outfitId,onPoint,onToolDrag,onProgress,onError,onPracticeOutfitPreparing,questMode=false}:Props){
 const host=useRef<HTMLDivElement>(null);
 const outfitIdRef=useRef(outfitId);
 outfitIdRef.current=outfitId;
 const outfitBridge=useRef<OutfitBridge|null>(null);
 const latest=useRef(state);const sim=useRef(simulation);const pointSelect=useRef(onPoint);const toolDrag=useRef(onToolDrag);
 latest.current=state;sim.current=simulation;pointSelect.current=onPoint;toolDrag.current=onToolDrag;

 useEffect(()=>{
  const avatar=animeAvatar(avatarId);
  const schoolOutfit=ANIME_OUTFITS.find(o=>o.id==='school')!;
  const practiceOutfit=ANIME_OUTFITS.find(o=>o.id==='study-body')!;
  const points=avatarPracticePoints(avatar);
  const el=host.current!;let disposed=false,frame=0,dirty=true,ready=false,lastView='',lastReset=-1,lastFocus=-1;
  let locateLerp:{t0:number;fromT:[number,number,number];fromP:[number,number,number];toT:[number,number,number];toP:[number,number,number]}|null=null;
  let renderer:T.WebGLRenderer;
  try{renderer=new T.WebGLRenderer({antialias:true,alpha:false,powerPreference:'high-performance'});}
  catch{onError('無法啟動 3D 視圖，請使用支持 WebGL 的瀏覽器。');return;}
  renderer.setPixelRatio(Math.min(devicePixelRatio,innerWidth<768?1.5:2));
  // Parchment clear matches panorama top／bottom fade (no box lid).
  renderer.setClearColor('#f6ead7');renderer.outputColorSpace=T.SRGBColorSpace;
  renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=.86;
  el.appendChild(renderer.domElement);
  renderer.domElement.setAttribute('aria-label',`${avatar.name}：拖動旋轉，滾輪縮放，點擊穴位標記。`);

  const scene=new T.Scene(),camera=new T.PerspectiveCamera(STUDIO_CAMERA_FOV_DEG,1,.005,100),controls=new OrbitControls(camera,renderer.domElement);
  camera.position.set(0,1.45,2.4);controls.target.set(0,1.35,0);
  controls.enableDamping=true;controls.dampingFactor=.085;controls.minDistance=.2;controls.maxDistance=8;
  controls.maxPolarAngle=Math.PI*.96;controls.addEventListener('change',()=>{dirty=true;});
  scene.environment=null;

  scene.add(new T.HemisphereLight(0xfff1dc,0x8a9a88,.85));
  const key=new T.DirectionalLight(0xffe8c8,1.15);key.position.set(-1.5,3.2,2.1);scene.add(key);
  const rim=new T.DirectionalLight(0xd8e8df,.42);rim.position.set(2.1,1.6,-2.2);scene.add(rim);
  const fill=new T.DirectionalLight(0xffe2c0,.55);fill.position.set(.2,-.8,1.6);scene.add(fill);

  let woodcut:WoodcutBackdrop|null=null;
  const panoramaLoader=new T.TextureLoader();
  panoramaLoader.load(publicAssetUrl('textures/woodcut-landscape.webp'),loaded=>{
   if(disposed)return;
   woodcut=buildWoodcutBackdrop(loaded);
   scene.add(woodcut.mesh);
   dirty=true;
  });

  const ground=makeFeetGround();
  ground.rotation.x=-Math.PI/2;ground.position.y=-.01;scene.add(ground);
  const platform=new T.Mesh(new T.CylinderGeometry(.62,.64,.024,80),new T.MeshBasicMaterial({color:0xe8d8b8}));
  platform.position.y=-.008;scene.add(platform);
  const ring=new T.Mesh(new T.RingGeometry(.57,.575,96),new T.MeshBasicMaterial({color:0x6a5238,transparent:true,opacity:.35,side:T.DoubleSide}));
  ring.rotation.x=-Math.PI/2;ring.position.y=.001;scene.add(ring);

  const gradient=toonGradient();
  const characterRoot=new T.Group();scene.add(characterRoot);
  const teaching=createTeachingOverlay(scene,{
   points,
   resolvePoint:(code,side)=>avatarPointTarget(avatar,code,side),
  });
  el.appendChild(teaching.label);

  // Finger-pressure feedback uses teaching-overlay halo/patch only.
  // Do not patch VRM MToon onBeforeCompile — injecting into Face/Body skin
  // breaks THREE_VRM_THREE_REVISION / WebGLProgram and turns skin black.
  let lastSimulation:SimulationState|null=null;
  let lastRefinedLocate=-1;
  let vrm: {scene:T.Object3D;update:(delta:number)=>void}|null=null;
  let schoolModel:T.Object3D|null=null;
  let practiceModel:T.Object3D|null=null;
  let practiceShell:T.Object3D|null=null;
  let practiceBuilt=false;
  let uniformAssetSnap:Map<string,MeshAssetSnapshot>|null=null;
  let lastFrame=performance.now();

  const showOutfit=(id:OutfitId)=>{
   if(schoolModel)schoolModel.visible=id==='school';
   if(practiceModel)practiceModel.visible=id==='study-body';
   characterRoot.updateMatrixWorld(true);
   if(ready)teaching.reanchor(characterRoot);
   dirty=true;
  };

  const buildPracticeOutfit=()=>{
   if(practiceBuilt||!practiceShell||disposed)return;
   performance.mark('practice-outfit-build-start');
   finishCharacterLook(practiceShell,practiceOutfit,gradient);
   const painted=practiceShell.userData.practiceBodySkinMap as T.Texture|undefined;
   if(schoolModel&&painted)assertPracticeBodySkinMaps(practiceShell,schoolModel,painted);
   if(schoolModel&&uniformAssetSnap)assertUniformOutfitUntouched(uniformAssetSnap,schoolModel);
   performance.mark('practice-outfit-build-end');
   try{performance.measure('practice-outfit-build','practice-outfit-build-start','practice-outfit-build-end');}catch{/* duplicate measure */}
   practiceModel=practiceShell;
   characterRoot.add(practiceShell);
   practiceShell.updateMatrixWorld(true);
   practiceBuilt=true;
   onPracticeOutfitPreparing?.(false);
   dirty=true;
  };

  const setOutfit=(id:OutfitId)=>{
   if(!schoolModel)return;
   if(id==='study-body'&&!practiceBuilt){
    onPracticeOutfitPreparing?.(true);
    requestAnimationFrame(()=>{
     requestAnimationFrame(()=>{
      if(disposed||practiceBuilt||!practiceShell)return;
      buildPracticeOutfit();
      showOutfit(id);
      if(ready)teaching.reanchor(characterRoot);
     });
    });
    return;
   }
   showOutfit(id);
  };
  outfitBridge.current={setOutfit};

  const fit=(view:string)=>{
   const mobile=el.clientWidth<768||(el.clientWidth<=1100&&el.clientHeight<=500);
   const reserved=mobile?210:260;
   const distance=mobile?Math.max(1.55*el.clientHeight/Math.max(160,el.clientHeight-reserved),.9/camera.aspect)/(2*Math.tan(T.MathUtils.degToRad(camera.fov/2))):2.55;
   const direction=view==='front'?new T.Vector3(0,.02,-1):view==='back'?new T.Vector3(0,.02,1):view==='side'?new T.Vector3(1,.02,0):new T.Vector3(.42,.08,-1).normalize();
   controls.target.set(0,mobile?1.38:1.32,0);
   camera.position.copy(controls.target).addScaledVector(direction,distance);
   controls.update();dirty=true;
  };

  const resize=()=>{
   renderer.setPixelRatio(Math.min(devicePixelRatio,el.clientWidth<768||el.clientHeight<600?1.5:2));
   camera.aspect=el.clientWidth/el.clientHeight;camera.updateProjectionMatrix();
   renderer.setSize(el.clientWidth,el.clientHeight);fit(latest.current.view);
  };
  const observer=new ResizeObserver(resize);observer.observe(el);

  if(avatar.modelStatus==='pending'||!avatar.modelUrl){
   ready=true;onProgress(100);fit(latest.current.view);dirty=true;
  }else{
   const loader=new GLTFLoader();
   loader.register(parser=>new VRMLoaderPlugin(parser));
   onProgress(8);
   const modelUrl=schoolOutfit.modelUrl||avatar.modelUrl;
   const isAnimeModel=!!modelUrl?.includes('anime-assistant.glb');
   const cacheAnimeOutfits=avatar.hasOutfits&&isAnimeModel;
   let loadProgress=8;
   const reportLoadProgress=(pct:number)=>{
    const next=Math.min(99,pct);
    if(next<=loadProgress)return;
    loadProgress=next;
    onProgress(loadProgress);
   };
   loader.load(publicAssetUrl(modelUrl!),(gltf)=>{
    if(disposed)return;
    vrm=gltf.userData.vrm??null;
    const model=vrm?.scene??gltf.scene;
    if(vrm){
     VRMUtils.removeUnnecessaryVertices(gltf.scene);
     VRMUtils.combineSkeletons(gltf.scene);
    }
    if(cacheAnimeOutfits){
     practiceShell=cloneSkinnedObject(model);
     isolatePracticeRig(practiceShell);
     finishCharacterLook(model,schoolOutfit,gradient);
     uniformAssetSnap=snapshotMeshAssets(model);
     schoolModel=model;
     schoolModel.visible=outfitIdRef.current==='school';
     characterRoot.clear();
     characterRoot.add(schoolModel);
    }else{
     finishCharacterLook(model,animeOutfit(outfitIdRef.current),gradient);
     characterRoot.clear();
     characterRoot.add(model);
    }
    model.updateMatrixWorld(true);
    teaching.anchor(characterRoot);
    ready=true;setOutfit(outfitIdRef.current);onProgress(100);fit(latest.current.view);dirty=true;
   },xhr=>{
    if(xhr.total)reportLoadProgress(Math.round(xhr.loaded/xhr.total*100));
    else if(isAnimeModel&&ANIME_MODEL_BYTES>0)reportLoadProgress(Math.round(xhr.loaded/ANIME_MODEL_BYTES*100));
   },()=>{if(!disposed)onError(`${avatar.name} 模型載入失敗，請確認 ${modelUrl} 是否存在。`);});
  }

  const raycaster=new T.Raycaster(),pointer=new T.Vector2(),tap=new PointerTap();
  const down=(e:PointerEvent)=>tap.down(e.pointerId,e.clientX,e.clientY,e.pointerType==='touch'?12:5);
  const move=(e:PointerEvent)=>{tap.move(e.pointerId,e.clientX,e.clientY);};
  const cancel=(e:PointerEvent)=>tap.cancel(e.pointerId);
  const markerFacingCamera=(m:T.Mesh)=>{
   const code=m.userData.point as string;
   const side=(m.userData.side as number)||1;
   const meta=animePointTarget(code,side as 1|-1)??avatarPointTarget(avatar,code,side as 1|-1);
   if(!meta)return true;
   const n=new T.Vector3().fromArray(meta.normal);
   characterRoot.updateMatrixWorld();
   n.transformDirection(characterRoot.matrixWorld).normalize();
   const toCam=camera.position.clone().sub(m.position).normalize();
   const thresh=questMode?0.05:0.2;
   return n.dot(toCam)>thresh;
  };
  const markerOccluded=(m:T.Mesh)=>{
   if(!ready)return true;
   const fl=sim.current.frameLocate;
   if(questMode&&fl){
    const p=m.position.clone().project(camera);
    if(p.z<-1||p.z>1)return true;
    if(fl.viewDir[2]>=-.25)return false;
   }
   if(!markerFacingCamera(m))return true;
   const skins=teaching.getSkinMeshes();
   if(!skins.length)return false;
   const target=m.position.clone();
   const camPos=camera.position.clone();
   const dir=target.clone().sub(camPos);
   const dist=dir.length();
   if(dist<1e-4)return false;
   dir.normalize();
   raycaster.set(camPos,dir);
   raycaster.far=dist-.01;
   raycaster.near=0.001;
   return raycaster.intersectObjects(skins,true).length>0;
  };
  const pickQuestMarker=(clientX:number,clientY:number)=>{
   const rect=renderer.domElement.getBoundingClientRect();
   const minPx=el.clientWidth<=720?24:10;
   let best:T.Mesh|null=null,bestD=minPx;
   for(const m of teaching.markers){
    if(!m.visible||markerOccluded(m))continue;
    const p=m.position.clone().project(camera);
    if(p.z<-1||p.z>1)continue;
    const sx=(p.x+1)/2*rect.width,sy=(1-p.y)/2*rect.height;
    const d=Math.hypot(clientX-rect.left-sx,clientY-rect.top-sy);
    if(d<=bestD){bestD=d;best=m;}
   }
   return best;
  };
  const up=(e:PointerEvent)=>{
   if(!tap.up(e.pointerId,e.clientX,e.clientY)||!ready)return;
   const rect=renderer.domElement.getBoundingClientRect();
   pointer.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);
   raycaster.setFromCamera(pointer,camera);
   let hit=raycaster.intersectObjects(teaching.markers.filter(m=>m.visible&&!markerOccluded(m)),false)[0];
   if(!hit&&questMode){
    const near=pickQuestMarker(e.clientX,e.clientY);
    if(near)hit={object:near,distance:0,point:new T.Vector3(),face:null,instanceId:null as unknown as number} as T.Intersection;
   }
   if(hit)pointSelect.current(hit.object.userData.point,hit.object.userData.side);
  };
  renderer.domElement.addEventListener('pointerdown',down);
  renderer.domElement.addEventListener('pointermove',move);
  renderer.domElement.addEventListener('pointerup',up);
  renderer.domElement.addEventListener('pointercancel',cancel);

  const toolInput=questMode?{update:()=>{},dispose:()=>{},isDragging:()=>false}:installSurfaceToolInput({
   canvas:renderer.domElement,
   handle:teaching.label,
   camera,
   controls,
   getSkins:()=>teaching.getSkinMeshes(),
   getSimulation:()=>sim.current,
   getState:()=>latest.current,
   isReady:()=>ready&&teaching.isAnchored(),
   getTarget:teaching.getTarget,
   getDraggables:teaching.getDraggables,
   snapTarget:teaching.snapTarget,
   onDrag:(target,phase)=>toolDrag.current(target,phase),
   invalidate:()=>{dirty=true;},
  });
  if(questMode)teaching.label.hidden=true;
  const applyLocateViewOffset=(fl:NonNullable<SimulationState['frameLocate']>)=>{
   camera.clearViewOffset();
   if(fl?.viewOffset){
    const vo=fl.viewOffset;
    camera.setViewOffset(vo.fullWidth,vo.fullHeight,vo.offsetX,vo.offsetY,vo.width,vo.height);
    camera.updateProjectionMatrix();
   }
  };
  const clamp=(n:number,lo:number,hi:number)=>Math.max(lo,Math.min(hi,n));
  const refineLocateView=(fl:NonNullable<SimulationState['frameLocate']>)=>{
   if(!fl.viewOffset||!fl.answerCode)return;
   const desktop=fl.viewOffset.fullWidth>=720;
   const hudBox=document.querySelector('.mq-hud-panel')?.getBoundingClientRect();
   const hintBox=document.querySelector('.mq-hint-btn')?.getBoundingClientRect();
   if(!hudBox||!hintBox)return;
   const rect=renderer.domElement.getBoundingClientRect();
   const margin=8;
   const vo=fl.viewOffset;
   for(let pass=0;pass<3;pass++){
    applyLocateViewOffset(fl);
    const answer=teaching.markers.find(m=>m.userData.point===fl.answerCode);
    if(!answer)break;
    const p=answer.position.clone().project(camera);
    const ay=rect.top+(1-p.y)/2*rect.height;
    const maxY=hintBox.top-margin;
    const minY=hudBox.bottom+margin;
    let moved=false;
    if(ay>maxY){
     vo.offsetY+=clamp(Math.round((ay-maxY)*.55),2,48);moved=true;
    }else if(ay<minY){
     vo.offsetY-=clamp(Math.round((minY-ay)*.55),2,48);moved=true;
    }
    const aim=new T.Vector3().fromArray(fl.aim).project(camera);
    const cy=rect.top+(1-aim.y)/2*rect.height;
    const mid=(hudBox.bottom+hintBox.top)/2;
    const span=Math.max(56,hintBox.top-hudBox.bottom);
    const drift=cy-mid;
    if(!desktop&&Math.abs(drift)>span*.15){
     vo.offsetY+=clamp(Math.round(drift*.42),2,40);moved=true;
    }
    vo.offsetY=clamp(vo.offsetY,-120,120);
    if(!moved&&ay>=minY&&ay<=maxY)break;
   }
   applyLocateViewOffset(fl);
   dirty=true;
  };
  type MqDebugWindow=Window&{__mqRefineLocate?:()=>void;__mqDebug?:()=>{
   markers:{code:string;side:number;screenX:number;screenY:number;inFrustum:boolean;occluded:boolean}[];
   hudBottom:number;hintTop:number;usableMid:number;frameCenter:{screenX:number;screenY:number}|null;
  }};
  if(questMode)(window as MqDebugWindow).__mqRefineLocate=()=>{
   if(sim.current.frameLocate)refineLocateView(sim.current.frameLocate);
  };
  if(questMode)(window as MqDebugWindow).__mqDebug=()=>{
   const rect=renderer.domElement.getBoundingClientRect();
   const hudBox=(document.querySelector('.mq-hud-panel')??document.querySelector('.mq-hud'))?.getBoundingClientRect();
   const hintBox=(document.querySelector('.mq-hint-btn')??document.querySelector('.mq-choices'))?.getBoundingClientRect();
   const hudBottom=hudBox?.bottom??0;
   const hintTop=hintBox?.top??window.innerHeight;
   const usableMid=(hudBottom+hintTop)/2;
   const markers=teaching.markers.filter(m=>m.visible).map(m=>{
    const p=m.position.clone().project(camera);
    const onScreen=p.z>=-1&&p.z<=1;
    const occluded=onScreen?markerOccluded(m):true;
    return {
     code:m.userData.point as string,
     side:m.userData.side as number,
     screenX:rect.left+(p.x+1)/2*rect.width,
     screenY:rect.top+(1-p.y)/2*rect.height,
     inFrustum:onScreen&&!occluded,
     occluded,
    };
   });
   let frameCenter:{screenX:number;screenY:number}|null=null;
   const fl=sim.current.frameLocate;
   if(fl){
    const p=new T.Vector3().fromArray(fl.aim).project(camera);
    frameCenter={
     screenX:rect.left+(p.x+1)/2*rect.width,
     screenY:rect.top+(1-p.y)/2*rect.height,
    };
   }
   return {markers,hudBottom,hintTop,usableMid,frameCenter};
  };

  const animate=()=>{
   if(disposed)return;frame=requestAnimationFrame(animate);
   const now=performance.now();const delta=Math.min(.05,(now-lastFrame)/1000);lastFrame=now;
   if(vrm)vrm.update(delta);
   const s=latest.current;
   let simState=sim.current;
   toolInput.update();
   if(s.view!==lastView||s.reset!==lastReset){
    const holdLocate=questMode&&!!simState.frameLocate;
    if(!holdLocate)fit(s.view);
    lastView=s.view;lastReset=s.reset;
   }
   controls.autoRotate=s.rotate&&!toolInput.isDragging();controls.autoRotateSpeed=.55;
   if(!toolInput.isDragging())controls.update();
   if(controls.autoRotate)dirty=true;

   if(ready&&!teaching.isAnchored()&&characterRoot.children.length)teaching.anchor(characterRoot);
   const beginLocateLerp=(fl:NonNullable<typeof simState.frameLocate>)=>{
    const aim=new T.Vector3().fromArray(fl.aim);
    const viewDir=new T.Vector3().fromArray(fl.viewDir).normalize();
    const camEnd=aim.clone().addScaledVector(viewDir,fl.distance);
    applyLocateViewOffset(fl);
    locateLerp={
     t0:performance.now(),
     fromT:[controls.target.x,controls.target.y,controls.target.z],
     fromP:[camera.position.x,camera.position.y,camera.position.z],
     toT:[aim.x,aim.y,aim.z],
     toP:[camEnd.x,camEnd.y,camEnd.z],
    };
    dirty=true;
   };
   if(simState.focus&&lastFocus!==simState.focus&&ready&&questMode&&simState.frameLocate){
    lastFocus=simState.focus;
    beginLocateLerp(simState.frameLocate);
   }else if(teaching.focus(simState,camera,controls)){
    lastFocus=simState.focus;locateLerp=null;dirty=true;
   }else if(simState.focus&&lastFocus!==simState.focus&&ready){
    // Pre-anchor fallback: same framing rules as teaching-overlay (do not ride the surface normal).
    lastFocus=simState.focus;
    if(simState.frameLocate){
     beginLocateLerp(simState.frameLocate);
    }else{
     const frameCode=simState.framePointCode??simState.pointCode;
     const frameSide=simState.framePointCode?(simState.frameSide??1):simState.side;
     const target=frameCode?avatarPointTarget(avatar,frameCode,frameSide):null;
     if(target){
      const aim=new T.Vector3().fromArray(target.position);
      const normal=new T.Vector3().fromArray(target.normal).normalize();
      const {viewDir,distance,aimAdjust}=focusViewFromNormal(frameCode,normal,frameSide);
      aim.add(aimAdjust);
      controls.target.copy(aim);
      camera.position.copy(aim).addScaledVector(viewDir,distance);
      controls.update();dirty=true;
     }
    }
   }
   if(locateLerp){
    const u=Math.min(1,(performance.now()-locateLerp.t0)/420);
    const ease=u*u*(3-2*u);
    controls.target.x=locateLerp.fromT[0]+(locateLerp.toT[0]-locateLerp.fromT[0])*ease;
    controls.target.y=locateLerp.fromT[1]+(locateLerp.toT[1]-locateLerp.fromT[1])*ease;
    controls.target.z=locateLerp.fromT[2]+(locateLerp.toT[2]-locateLerp.fromT[2])*ease;
    camera.position.x=locateLerp.fromP[0]+(locateLerp.toP[0]-locateLerp.fromP[0])*ease;
    camera.position.y=locateLerp.fromP[1]+(locateLerp.toP[1]-locateLerp.fromP[1])*ease;
    camera.position.z=locateLerp.fromP[2]+(locateLerp.toP[2]-locateLerp.fromP[2])*ease;
    controls.update();dirty=true;
    if(u>=1){
     locateLerp=null;
     if(simState.frameLocate){
      applyLocateViewOffset(simState.frameLocate);
      if(questMode&&lastRefinedLocate!==simState.focus){
       lastRefinedLocate=simState.focus;
       refineLocateView(simState.frameLocate);
      }
     }
    }
   }

   if(questMode){
    simState={...simState,dragging:false,running:false,customTarget:null};
    teaching.label.hidden=true;
   }
   const response=teaching.update(simState,camera,el.clientWidth,el.clientHeight);
   if(questMode)teaching.label.hidden=true;
   if(lastSimulation!==simState||simState.running||response?.settling){dirty=true;lastSimulation=simState;}

   if(dirty){renderer.render(scene,camera);dirty=false;}
  };
  animate();

  const contextLost=(e:Event)=>{e.preventDefault();onError('設備暫停了 3D 顯示，請重新載入頁面。');};
  renderer.domElement.addEventListener('webglcontextlost',contextLost);

  return()=>{
   disposed=true;outfitBridge.current=null;cancelAnimationFrame(frame);observer.disconnect();
   if(questMode){delete (window as MqDebugWindow).__mqDebug;delete (window as MqDebugWindow).__mqRefineLocate;}
   toolInput.dispose();teaching.dispose();controls.dispose();gradient.dispose();
   if(woodcut){
    scene.remove(woodcut.mesh);
    disposeWoodcutBackdrop(woodcut);
    woodcut=null;
   }
   characterRoot.clear();
   disposeCharacterRoot(schoolModel);
   disposeCharacterRoot(practiceModel);
   if(practiceShell&&!practiceBuilt)disposeCharacterRoot(practiceShell);
   scene.traverse(o=>{
    if(!(o instanceof T.Mesh))return;
    o.geometry.dispose();
    const ms=Array.isArray(o.material)?o.material:[o.material];
    ms.forEach(m=>m.dispose());
   });
   teaching.label.remove();renderer.dispose();renderer.domElement.remove();
  };
 },[avatarId,onProgress,onError,questMode]);

 useEffect(()=>{outfitBridge.current?.setOutfit(outfitId);},[outfitId]);

 return <div className="scene" ref={host}/>;
}
