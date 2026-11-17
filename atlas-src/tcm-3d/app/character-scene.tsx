import {publicAssetUrl} from './public-assets';
import {animeAvatar,avatarPointTarget,avatarPracticePoints,type AvatarId} from './avatars';
import {animeOutfit,type AnimeOutfit,type OutfitId} from './outfits';
import {useEffect,useRef} from 'react';
import * as T from 'three';
import {OrbitControls} from 'three/examples/jsm/controls/OrbitControls.js';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import {VRMLoaderPlugin,VRMUtils} from '@pixiv/three-vrm';
import {MToonMaterialOutlineWidthMode} from '@pixiv/three-vrm-materials-mtoon';
import {PointerTap} from './pointer-tap';
import {createTeachingOverlay} from './teaching-overlay';
import {installSurfaceToolInput} from './surface-tool-input';
import {focusViewFromNormal} from './focus-view';
import type {SceneState} from './anatomy';
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

/** TV anime cel look: sharp shade bands + ink outline; keep #75 hide／hair／practice intact. */
const applyCelShading=(root:T.Object3D,gradient:T.DataTexture)=>{
 root.traverse((obj:T.Object3D)=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as MToonLike & T.MeshStandardMaterial & {isMeshToonMaterial?:boolean;gradientMap?:T.Texture|null;envMapIntensity?:number};
   if(m.isMToonMaterial){
    m.shadingToonyFactor=.94;
    m.shadingShiftFactor=Math.min(m.shadingShiftFactor??0,-.06);
    if(m.shadeColorFactor)m.shadeColorFactor.multiplyScalar(.78);
    m.giEqualizationFactor=Math.min(m.giEqualizationFactor??1,.5);
    m.outlineWidthMode=MToonMaterialOutlineWidthMode.WorldCoordinates;
    m.outlineWidthFactor=Math.max(m.outlineWidthFactor??0,.0028);
    if(m.outlineColorFactor)m.outlineColorFactor.setRGB(.12,.09,.07);
    if(m.parametricRimColorFactor)m.parametricRimColorFactor.setRGB(0,0,0);
    if(m.rimLightingMixFactor!=null)m.rimLightingMixFactor=0;
    if(m.matcapFactor)m.matcapFactor.multiplyScalar(.3);
    m.needsUpdate=true;
    continue;
   }
   if(m.isMeshToonMaterial){
    m.gradientMap=gradient;
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

/** Procedural woodcut parchment for the far backdrop (no external bitmap). */
const makeWoodcutBackdrop=()=>{
 const size=1024;
 const canvas=document.createElement('canvas');
 canvas.width=size;canvas.height=size;
 const ctx=canvas.getContext('2d');
 if(!ctx)return null;
 ctx.fillStyle='#e4d4b4';
 ctx.fillRect(0,0,size,size);
 const grain=ctx.createImageData(size,size);
 for(let i=0;i<grain.data.length;i+=4){
  const n=220+((Math.sin(i*.013)+Math.cos(i*.007))*8|0);
  grain.data[i]=n;grain.data[i+1]=n-8;grain.data[i+2]=n-28;grain.data[i+3]=255;
 }
 ctx.putImageData(grain,0,0);
 ctx.globalAlpha=.22;
 ctx.fillStyle='#e8d9b8';
 ctx.fillRect(0,0,size,size);
 ctx.globalAlpha=1;
 ctx.strokeStyle='#3a2e22';
 ctx.lineWidth=2.2;
 ctx.lineJoin='round';
 // Cloud bands (墨線雲)
 const cloud=(cx:number,cy:number,s:number)=>{
  ctx.beginPath();
  ctx.moveTo(cx-s,cy);
  ctx.bezierCurveTo(cx-s*.6,cy-s*.55,cx-s*.15,cy-s*.5,cx,cy-s*.15);
  ctx.bezierCurveTo(cx+s*.2,cy-s*.55,cx+s*.7,cy-s*.45,cx+s,cy);
  ctx.bezierCurveTo(cx+s*.55,cy+s*.25,cx+s*.15,cy+s*.2,cx,cy+s*.08);
  ctx.bezierCurveTo(cx-s*.25,cy+s*.28,cx-s*.7,cy+s*.2,cx-s,cy);
  ctx.stroke();
 };
 cloud(220,180,90);cloud(780,140,110);cloud(520,90,70);
 // Bamboo stalks
 ctx.lineWidth=3;
 for(const x of [90,140,880,940]){
  ctx.beginPath();
  ctx.moveTo(x,size);ctx.quadraticCurveTo(x+18,size*.55,x-8,80);
  ctx.stroke();
  for(let y=size-40;y>120;y-=70){
   ctx.beginPath();
   ctx.moveTo(x-10,y);ctx.lineTo(x+12,y-6);
   ctx.stroke();
   ctx.beginPath();
   ctx.moveTo(x,y-20);
   ctx.quadraticCurveTo(x+40,y-50,x+70,y-35);
   ctx.stroke();
   ctx.beginPath();
   ctx.moveTo(x,y-35);
   ctx.quadraticCurveTo(x-35,y-65,x-65,y-40);
   ctx.stroke();
  }
 }
 ctx.strokeStyle='#5a4634';
 ctx.lineWidth=1.2;
 ctx.strokeRect(28,28,size-56,size-56);
 const tex=new T.CanvasTexture(canvas);
 tex.colorSpace=T.SRGBColorSpace;
 tex.needsUpdate=true;
 const mat=new T.MeshBasicMaterial({map:tex,depthWrite:false});
 const plane=new T.Mesh(new T.PlaneGeometry(22,14),mat);
 plane.position.set(0,1.35,-5.2);
 plane.renderOrder=-2;
 return {plane,tex,mat};
};

const materialLabel=(mat:T.Material,obj:T.Object3D)=>`${mat.name||''} ${obj.name||''}`;

/** Warm fill for MASK cutouts — chroma above #f6efe4 so restored skin is not a beige void. */
const SKIN_FILL=new T.Color(232/255,198/255,178/255);
/** Deep teal as sRGB bytes — do not feed hex through ColorManagement (would go near-black on the map). */
const PRACTICE_TEAL_SRGB={r:13,g:74,b:79};
const BUN_BROWN=0x3b2a22;

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

const findSkinMap=(m:T.MeshStandardMaterial & {map?:T.Texture|null;uniforms?:Record<string,{value:unknown}>})=>{
 if(m.map?.image)return m.map;
 const uni=m.uniforms?.map?.value;
 if(uni&&typeof uni==='object'&&'image' in (uni as object)&&(uni as T.Texture).image)return uni as T.Texture;
 return null;
};

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
 }
 if(!touched)return;
 ctx.putImageData(data,0,0);
 const next=src.clone();
 next.image=canvas;
 next.generateMipmaps=true;
 next.minFilter=T.LinearMipmapLinearFilter;
 next.magFilter=T.LinearFilter;
 next.needsUpdate=true;
 for(const m of skinMats){
  m.map=next;
  const uni=(m as T.MeshStandardMaterial & {uniforms?:Record<string,{value:unknown}>}).uniforms;
  if(uni?.map)uni.map.value=next;
  m.needsUpdate=true;
 }
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
 const bunMat=new T.MeshToonMaterial({name:'PracticeHairBun',color:BUN_BROWN});
 // Head local +Y/+Z align with world; place above＋behind GV20 (~1.62) so the bun is visible from front.
 const bun=new T.Mesh(new T.SphereGeometry(.06,20,16),bunMat);
 bun.name='PracticeHairBun';
 bun.position.set(0,.24,.055);
 bun.scale.set(1.05,.92,1.08);
 bun.frustumCulled=false;
 head.add(bun);
 const coil=new T.Mesh(new T.TorusGeometry(.042,.015,8,20),bunMat);
 coil.name='PracticeHairBunCoil';
 coil.position.set(0,.225,.06);
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
 });
 // Never add MeshBasic flesh balloons or hook MToon onBeforeCompile.
 if(outfit.practiceBody){
  paintPracticeBodySkin(root);
  restyleHairBun(root);
 }
};

const finishCharacterLook=(root:T.Object3D,outfit:AnimeOutfit,gradient:T.DataTexture)=>{
 applyOutfitMaterials(root,outfit);
 applyCelShading(root,gradient);
};

export default function CharacterScene({state,simulation,avatarId,outfitId,onPoint,onToolDrag,onProgress,onError}:Props){
 const host=useRef<HTMLDivElement>(null);
 const latest=useRef(state);const sim=useRef(simulation);const pointSelect=useRef(onPoint);const toolDrag=useRef(onToolDrag);
 latest.current=state;sim.current=simulation;pointSelect.current=onPoint;toolDrag.current=onToolDrag;

 useEffect(()=>{
  const avatar=animeAvatar(avatarId);
  const outfit=animeOutfit(outfitId);
  const points=avatarPracticePoints(avatar);
  const el=host.current!;let disposed=false,frame=0,dirty=true,ready=false,lastView='',lastReset=-1,lastFocus=-1;
  let renderer:T.WebGLRenderer;
  try{renderer=new T.WebGLRenderer({antialias:true,alpha:false,powerPreference:'high-performance'});}
  catch{onError('無法啟動 3D 視圖，請使用支持 WebGL 的瀏覽器。');return;}
  renderer.setPixelRatio(Math.min(devicePixelRatio,innerWidth<768?1.5:2));
  // Style 4 parchment clear; no RoomEnvironment IBL (avoids oily specular on skin).
  renderer.setClearColor('#e6d5b3');renderer.outputColorSpace=T.SRGBColorSpace;
  renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=.86;
  el.appendChild(renderer.domElement);
  renderer.domElement.setAttribute('aria-label',`${avatar.name}：拖動旋轉，滾輪縮放，點擊穴位標記。`);

  const scene=new T.Scene(),camera=new T.PerspectiveCamera(34,1,.005,100),controls=new OrbitControls(camera,renderer.domElement);
  camera.position.set(0,1.45,2.4);controls.target.set(0,1.35,0);
  controls.enableDamping=true;controls.dampingFactor=.085;controls.minDistance=.2;controls.maxDistance=8;
  controls.maxPolarAngle=Math.PI*.96;controls.addEventListener('change',()=>{dirty=true;});
  scene.environment=null;

  scene.add(new T.HemisphereLight(0xfff1dc,0x8a9a88,.85));
  const key=new T.DirectionalLight(0xffe8c8,1.15);key.position.set(-1.5,3.2,2.1);scene.add(key);
  const rim=new T.DirectionalLight(0xd8e8df,.42);rim.position.set(2.1,1.6,-2.2);scene.add(rim);
  const fill=new T.DirectionalLight(0xffe2c0,.55);fill.position.set(.2,-.8,1.6);scene.add(fill);

  const woodcut=makeWoodcutBackdrop();
  if(woodcut)scene.add(woodcut.plane);

  const ground=new T.Mesh(new T.CircleGeometry(18,64),new T.MeshBasicMaterial({color:0xdcc9a4}));
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
  let vrm: {scene:T.Object3D;update:(delta:number)=>void}|null=null;
  let lastFrame=performance.now();

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
   const modelUrl=outfit.modelUrl||avatar.modelUrl;
   loader.load(publicAssetUrl(modelUrl!),(gltf)=>{
    if(disposed)return;
    vrm=gltf.userData.vrm??null;
    const model=vrm?.scene??gltf.scene;
    if(vrm){
     VRMUtils.removeUnnecessaryVertices(gltf.scene);
     VRMUtils.combineSkeletons(gltf.scene);
    }
    finishCharacterLook(model,outfit,gradient);
    characterRoot.clear();
    characterRoot.add(model);
    model.updateMatrixWorld(true);
    teaching.anchor(characterRoot);
    ready=true;onProgress(100);fit(latest.current.view);dirty=true;
   },xhr=>{if(xhr.total)onProgress(Math.min(99,Math.round(xhr.loaded/xhr.total*100)));},()=>{if(!disposed)onError(`${avatar.name} 模型載入失敗，請確認 ${modelUrl} 是否存在。`);});
  }

  const raycaster=new T.Raycaster(),pointer=new T.Vector2(),tap=new PointerTap();
  const down=(e:PointerEvent)=>tap.down(e.pointerId,e.clientX,e.clientY,e.pointerType==='touch'?12:5);
  const move=(e:PointerEvent)=>{tap.move(e.pointerId,e.clientX,e.clientY);};
  const cancel=(e:PointerEvent)=>tap.cancel(e.pointerId);
  const up=(e:PointerEvent)=>{
   if(!tap.up(e.pointerId,e.clientX,e.clientY)||!ready)return;
   const rect=renderer.domElement.getBoundingClientRect();
   pointer.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);
   raycaster.setFromCamera(pointer,camera);
   const hit=raycaster.intersectObjects(teaching.markers.filter(m=>m.visible),false)[0];
   if(hit)pointSelect.current(hit.object.userData.point,hit.object.userData.side);
  };
  renderer.domElement.addEventListener('pointerdown',down);
  renderer.domElement.addEventListener('pointermove',move);
  renderer.domElement.addEventListener('pointerup',up);
  renderer.domElement.addEventListener('pointercancel',cancel);

  const toolInput=installSurfaceToolInput({
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

  const animate=()=>{
   if(disposed)return;frame=requestAnimationFrame(animate);
   const now=performance.now();const delta=Math.min(.05,(now-lastFrame)/1000);lastFrame=now;
   if(vrm)vrm.update(delta);
   const s=latest.current,simState=sim.current;
   toolInput.update();
   if(s.view!==lastView||s.reset!==lastReset){fit(s.view);lastView=s.view;lastReset=s.reset;}
   controls.autoRotate=s.rotate&&!toolInput.isDragging();controls.autoRotateSpeed=.55;
   if(!toolInput.isDragging())controls.update();
   if(controls.autoRotate)dirty=true;

   if(ready&&!teaching.isAnchored()&&characterRoot.children.length)teaching.anchor(characterRoot);
   if(teaching.focus(simState,camera,controls)){lastFocus=simState.focus;dirty=true;}
   else if(simState.focus&&lastFocus!==simState.focus&&ready){
    // Pre-anchor fallback: same framing rules as teaching-overlay (do not ride the surface normal).
    lastFocus=simState.focus;
    const target=simState.pointCode?avatarPointTarget(avatar,simState.pointCode,simState.side):null;
    if(target){
     const aim=new T.Vector3().fromArray(target.position);
     const normal=new T.Vector3().fromArray(target.normal).normalize();
     const {viewDir,distance,aimAdjust}=focusViewFromNormal(simState.pointCode,normal,simState.side);
     aim.add(aimAdjust);
     controls.target.copy(aim);
     camera.position.copy(aim).addScaledVector(viewDir,distance);
     controls.update();dirty=true;
    }
   }

   const response=teaching.update(simState,camera,el.clientWidth,el.clientHeight);
   if(lastSimulation!==simState||simState.running||response?.settling){dirty=true;lastSimulation=simState;}

   if(dirty){renderer.render(scene,camera);dirty=false;}
  };
  animate();

  const contextLost=(e:Event)=>{e.preventDefault();onError('設備暫停了 3D 顯示，請重新載入頁面。');};
  renderer.domElement.addEventListener('webglcontextlost',contextLost);

  return()=>{
   disposed=true;cancelAnimationFrame(frame);observer.disconnect();
   toolInput.dispose();teaching.dispose();controls.dispose();gradient.dispose();
   woodcut?.tex.dispose();
   scene.traverse(o=>{if(o instanceof T.Mesh){o.geometry.dispose();const ms=Array.isArray(o.material)?o.material:[o.material];ms.forEach(m=>m.dispose());}});
   teaching.label.remove();renderer.dispose();renderer.domElement.remove();
  };
 },[avatarId,outfitId,onProgress,onError]);

 return <div className="scene" ref={host}/>;
}
