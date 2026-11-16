import {publicAssetUrl} from './public-assets';
import {animeAvatar,avatarPointTarget,type AvatarId} from './avatars';
import {animeOutfit,type AnimeOutfit,type OutfitId} from './outfits';
import {useEffect,useRef} from 'react';
import * as T from 'three';
import {OrbitControls} from 'three/examples/jsm/controls/OrbitControls.js';
import {RoomEnvironment} from 'three/examples/jsm/environments/RoomEnvironment.js';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import {VRMLoaderPlugin,VRMUtils} from '@pixiv/three-vrm';
import {PointerTap} from './pointer-tap';
import {createTeachingOverlay} from './teaching-overlay';
import {installSurfaceToolInput} from './surface-tool-input';
import type {SceneState} from './anatomy';
import type {SimulationState,SurfaceTarget,ToolDragPhase} from './teaching';

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

const toonGradient=()=>{
 const data=new Uint8Array([90,90,90,255,168,168,168,255,255,255,255,255]);
 const tex=new T.DataTexture(data,3,1,T.RGBAFormat);
 tex.magFilter=T.NearestFilter;tex.minFilter=T.NearestFilter;tex.needsUpdate=true;return tex;
};

const materialLabel=(mat:T.Material,obj:T.Object3D)=>`${mat.name||''} ${obj.name||''}`;

// Warm neck fill — keep chroma above the #f6efe4 clear color so the V is not a beige void.
const NECK_SKIN_COLOR=new T.Color(232/255,198/255,178/255);

/**
 * Sendagaya Body_00_SKIN uses MASK cutouts under the vest. Hiding／cutting Tops
 * opens a torso void. study-neck keeps Tops covering that cavity, hides only
 * Accessory (領結 fills the shirt V), and restores Body_00_SKIN alpha in the collar V
 * so 人迎／天突 show real lit MToon skin that tools can hit — no NeckOpen filler mesh.
 */
const inCollarSkinBand=(x:number,y:number,z:number)=>{
 // Only the shirt V／天突窝 (and matching back MASK). Do not paint 人迎 side-neck
 // (y≈1.32) — flat NECK_SKIN_COLOR there reads as a peach oval under focus.
 if(y<1.2||y>1.295||Math.abs(x)>.1)return false;
 // Front+back of the shirt V／鎖骨帶 — front-only fill still sees through back MASK.
 return Math.abs(z)<.12;
};

const fillSkinTexel=(data:ImageData,i:number)=>{
 if(data.data[i+3]>=200)return;
 data.data[i]=NECK_SKIN_COLOR.r*255;
 data.data[i+1]=NECK_SKIN_COLOR.g*255;
 data.data[i+2]=NECK_SKIN_COLOR.b*255;
 data.data[i+3]=255;
};

const fillUvTriangle=(data:ImageData,width:number,height:number,flipY:boolean,u0:number,v0:number,u1:number,v1:number,u2:number,v2:number)=>{
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
  fillSkinTexel(data,(y*width+x)*4);
 }
};

const restoreCollarSkinAlpha=(root:T.Object3D)=>{
 const skinMats:T.MeshStandardMaterial[]=[];
 const skinMeshes:(T.Mesh|T.SkinnedMesh)[]=[];
 root.traverse((obj:T.Object3D)=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  const body=mats.filter(m=>/Body_00_SKIN/i.test(m.name||'')&&!/Outline|NeckOpen/i.test(m.name||'')) as T.MeshStandardMaterial[];
  if(!body.length)return;
  skinMeshes.push(obj);
  for(const m of body)if(!skinMats.includes(m))skinMats.push(m);
 });
 if(!skinMeshes.length||!skinMats.length)return;
 const findMap=(m:T.MeshStandardMaterial & {map?:T.Texture|null;uniforms?:Record<string,{value:unknown}>})=>{
  if(m.map?.image)return m.map;
  const uni=m.uniforms?.map?.value;
  if(uni&&typeof uni==='object'&&'image' in (uni as object)&&(uni as T.Texture).image)return uni as T.Texture;
  return null;
 };
 const src=skinMats.map(findMap).find(Boolean)??null;
 if(!src?.image)return;
 const img=src.image as CanvasImageSource & {width:number;height:number};
 const canvas=document.createElement('canvas');
 canvas.width=img.width;canvas.height=img.height;
 const ctx=canvas.getContext('2d',{willReadFrequently:true});
 if(!ctx)return;
 try{ctx.drawImage(img,0,0);}catch{return;}
 const data=ctx.getImageData(0,0,canvas.width,canvas.height);
 const flipY=!!src.flipY;
 let filled=0;
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
   const hit=inCollarSkinBand(a.x,a.y,a.z)||inCollarSkinBand(b.x,b.y,b.z)||inCollarSkinBand(c.x,c.y,c.z)
    ||inCollarSkinBand((a.x+b.x+c.x)/3,(a.y+b.y+c.y)/3,(a.z+b.z+c.z)/3);
   if(!hit)continue;
   fillUvTriangle(data,canvas.width,canvas.height,flipY,uv.getX(i0),uv.getY(i0),uv.getX(i1),uv.getY(i1),uv.getX(i2),uv.getY(i2));
   filled++;
  }
 }
 if(!filled)return;
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
   if(/HAIR|髪|HairBack/i.test(name)){
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
 // restoreCollarSkinAlpha paints lit MToon skin in the shirt V. Do not add
 // Body_00_SKIN_NeckOpen plugs — the old MeshBasicMaterial spheres
 // (toneMapped:false, renderOrder:3) read as a flesh balloon over the chest
 // in 人迎／天突 close-ups and stole needle／finger contacts.
 if(outfit.openCollar)restoreCollarSkinAlpha(root);
};

export default function CharacterScene({state,simulation,avatarId,outfitId,onPoint,onToolDrag,onProgress,onError}:Props){
 const host=useRef<HTMLDivElement>(null);
 const latest=useRef(state);const sim=useRef(simulation);const pointSelect=useRef(onPoint);const toolDrag=useRef(onToolDrag);
 latest.current=state;sim.current=simulation;pointSelect.current=onPoint;toolDrag.current=onToolDrag;

 useEffect(()=>{
  const avatar=animeAvatar(avatarId);
  const outfit=animeOutfit(outfitId);
  const points=avatar.points;
  const el=host.current!;let disposed=false,frame=0,dirty=true,ready=false,lastView='',lastReset=-1,lastFocus=-1;
  let renderer:T.WebGLRenderer;
  try{renderer=new T.WebGLRenderer({antialias:true,alpha:false,powerPreference:'high-performance'});}
  catch{onError('無法啟動 3D 視圖，請使用支持 WebGL 的瀏覽器。');return;}
  renderer.setPixelRatio(Math.min(devicePixelRatio,innerWidth<768?1.5:2));
  renderer.setClearColor('#f6efe4');renderer.outputColorSpace=T.SRGBColorSpace;
  renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=1.05;
  el.appendChild(renderer.domElement);
  renderer.domElement.setAttribute('aria-label',`${avatar.name}：拖動旋轉，滾輪縮放，點擊穴位標記。`);

  const scene=new T.Scene(),camera=new T.PerspectiveCamera(34,1,.005,100),controls=new OrbitControls(camera,renderer.domElement);
  camera.position.set(0,1.45,2.4);controls.target.set(0,1.35,0);
  controls.enableDamping=true;controls.dampingFactor=.085;controls.minDistance=.35;controls.maxDistance=8;
  controls.maxPolarAngle=Math.PI*.96;controls.addEventListener('change',()=>{dirty=true;});

  const pmrem=new T.PMREMGenerator(renderer),room=new RoomEnvironment(),env=pmrem.fromScene(room,.04);
  scene.environment=env.texture;room.dispose();pmrem.dispose();
  scene.add(new T.HemisphereLight(0xfff6ea,0xb7cfc4,1.15));
  const key=new T.DirectionalLight(0xfff3df,2.1);key.position.set(-1.6,3.4,2.2);scene.add(key);
  const rim=new T.DirectionalLight(0xd7fff4,1.35);rim.position.set(2.2,1.8,-2.4);scene.add(rim);
  const fill=new T.DirectionalLight(0xffe7c8,1.1);fill.position.set(0,-1.2,1.4);scene.add(fill);

  const ground=new T.Mesh(new T.CircleGeometry(18,64),new T.MeshStandardMaterial({color:0xe8dcc8,roughness:1}));
  ground.rotation.x=-Math.PI/2;ground.position.y=-.01;scene.add(ground);
  const platform=new T.Mesh(new T.CylinderGeometry(.62,.64,.024,80),new T.MeshStandardMaterial({color:0xfff8ef,metalness:.08,roughness:.72}));
  platform.position.y=-.008;scene.add(platform);
  const ring=new T.Mesh(new T.RingGeometry(.57,.575,96),new T.MeshBasicMaterial({color:0xc9a45a,transparent:true,opacity:.45,side:T.DoubleSide}));
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
   const modelUrl=avatar.hasOutfits?outfit.modelUrl:avatar.modelUrl;
   loader.load(publicAssetUrl(modelUrl),(gltf)=>{
    if(disposed)return;
    vrm=gltf.userData.vrm??null;
    const model=vrm?.scene??gltf.scene;
    if(vrm){
     VRMUtils.removeUnnecessaryVertices(gltf.scene);
     VRMUtils.combineSkeletons(gltf.scene);
    }
    if(avatar.hasOutfits)applyOutfitMaterials(model,outfit);
    else model.traverse((obj:T.Object3D)=>{
     if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
     obj.frustumCulled=false;
     const mats=Array.isArray(obj.material)?obj.material:[obj.material];
     for(const mat of mats){
      const m=mat as T.MeshStandardMaterial;
      if(m.map){m.map.colorSpace=T.SRGBColorSpace;m.map.needsUpdate=true;}
     }
    });
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
     let viewDir=new T.Vector3();
     let distance=.62;
     if(simState.pointCode==='GV20'||normal.y>.85){
      viewDir.set(.06,.22,-.97).normalize();aim.y-=.1;aim.z-=.06;distance=1.35;
     }else if(simState.pointCode==='ST9'||simState.pointCode==='CV22'||simState.pointCode==='CV23'){
      viewDir.set(Math.sign(normal.x||simState.side)*.15,.04,-1).normalize();aim.y+=.02;aim.z-=.01;distance=.85;
     }else{
      viewDir.set(normal.x*.28-.1,Math.max(.06,normal.y*.25+.1),-Math.max(.82,Math.abs(normal.z))).normalize();
      distance=.72;
     }
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
   scene.traverse(o=>{if(o instanceof T.Mesh){o.geometry.dispose();const ms=Array.isArray(o.material)?o.material:[o.material];ms.forEach(m=>m.dispose());}});
   env.dispose();teaching.label.remove();renderer.dispose();renderer.domElement.remove();
  };
 },[avatarId,outfitId,onProgress,onError]);

 return <div className="scene" ref={host}/>;
}
