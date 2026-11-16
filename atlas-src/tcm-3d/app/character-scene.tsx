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

const SKIN_HINT=/Face_00_SKIN|Body_00_SKIN|FaceMouth/i;

const toonGradient=()=>{
 const data=new Uint8Array([90,90,90,255,168,168,168,255,255,255,255,255]);
 const tex=new T.DataTexture(data,3,1,T.RGBAFormat);
 tex.magFilter=T.NearestFilter;tex.minFilter=T.NearestFilter;tex.needsUpdate=true;return tex;
};

const materialLabel=(mat:T.Material,obj:T.Object3D)=>`${mat.name||''} ${obj.name||''}`;

const applyOutfitMaterials=(root:T.Object3D,outfit:AnimeOutfit)=>{
 root.traverse((obj:T.Object3D)=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  obj.frustumCulled=false;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   const name=materialLabel(m,obj);
   const hide=outfit.hideMaterials.some(token=>name.includes(token));
   m.visible=!hide;
   if(hide){m.needsUpdate=true;continue;}
   if(m.map){m.map.colorSpace=T.SRGBColorSpace;m.map.needsUpdate=true;}
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
};

/** Finger-pressure local dent + tint on anime skin materials (Face / Body). */
const attachTeachDeform=(mat:T.Material,effect:{
 target:{value:T.Vector3};normal:{value:T.Vector3};deform:{value:number};
 activity:{value:number};color:{value:T.Color};tool:{value:number};
})=>{
 const m=mat as T.MeshStandardMaterial;
 if((m as unknown as {userData:{teachHooked?:boolean}}).userData?.teachHooked)return;
 m.userData={...(m.userData||{}),teachHooked:true};
 m.onBeforeCompile=shader=>{
  shader.uniforms.teachTarget=effect.target;
  shader.uniforms.teachNormal=effect.normal;
  shader.uniforms.teachDeform=effect.deform;
  shader.uniforms.teachActivity=effect.activity;
  shader.uniforms.teachColor=effect.color;
  shader.uniforms.teachTool=effect.tool;
  shader.vertexShader='varying vec3 teachPosition; uniform vec3 teachTarget; uniform vec3 teachNormal; uniform float teachDeform; varying float teachInfluence;\n'+shader.vertexShader;
  shader.vertexShader=shader.vertexShader.replace('#include <begin_vertex>','#include <begin_vertex>\nteachPosition=transformed; teachInfluence = exp(-pow(distance(transformed, teachTarget) / 0.028, 2.0)); float skinProfile=exp(-pow(distance(transformed,teachTarget)/0.016,2.0)); transformed += teachNormal * skinProfile * teachDeform;');
  shader.fragmentShader='varying vec3 teachPosition; uniform vec3 teachTarget; uniform float teachTool; uniform float teachActivity; uniform vec3 teachColor; varying float teachInfluence;\n'+shader.fragmentShader;
  shader.fragmentShader=shader.fragmentShader.replace('#include <color_fragment>',`#include <color_fragment>
   diffuseColor.rgb = mix(diffuseColor.rgb, teachColor, teachInfluence * teachActivity * 0.45);
   if(teachTool > 0.5){
    float pressure=exp(-pow(distance(teachPosition,teachTarget)/0.014,2.0));
    diffuseColor.rgb=mix(diffuseColor.rgb,vec3(0.94,0.79,0.65),pressure*teachActivity*0.35);
   }
  `);
 };
 m.needsUpdate=true;
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
  controls.enableDamping=true;controls.dampingFactor=.085;controls.minDistance=.12;controls.maxDistance=8;
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

  const effect={
   target:{value:new T.Vector3(100,100,100)},
   normal:{value:new T.Vector3(0,0,1)},
   deform:{value:0},
   activity:{value:0},
   color:{value:new T.Color('#5c9c91')},
   tool:{value:0},
  };
  let lastSimulation:SimulationState|null=null;

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

  const hookSkinShaders=(root:T.Object3D)=>{
   root.traverse(obj=>{
    if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
    const mats=Array.isArray(obj.material)?obj.material:[obj.material];
    const name=mats.map(m=>m?.name||'').join(' ')+' '+obj.name;
    if(!SKIN_HINT.test(name))return;
    for(const mat of mats)if(mat)attachTeachDeform(mat,effect);
   });
  };

  if(avatar.modelStatus==='pending'||!avatar.modelUrl){
   ready=true;onProgress(100);fit(latest.current.view);dirty=true;
  }else{
   const loader=new GLTFLoader();
   loader.register(parser=>new VRMLoaderPlugin(parser));
   onProgress(8);
   const modelUrl=avatar.hasOutfits?outfit.modelUrl:avatar.modelUrl;
   loader.load(publicAssetUrl(modelUrl),(gltf)=>{
    if(disposed)return;
    const vrm=gltf.userData.vrm;
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
    hookSkinShaders(model);
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
   const s=latest.current,simState=sim.current;
   toolInput.update();
   if(s.view!==lastView||s.reset!==lastReset){fit(s.view);lastView=s.view;lastReset=s.reset;}
   controls.autoRotate=s.rotate&&!toolInput.isDragging();controls.autoRotateSpeed=.55;
   if(!toolInput.isDragging())controls.update();
   if(controls.autoRotate)dirty=true;

   if(ready&&!teaching.isAnchored()&&characterRoot.children.length)teaching.anchor(characterRoot);
   if(teaching.focus(simState,camera,controls)){lastFocus=simState.focus;dirty=true;}
   else if(simState.focus&&lastFocus!==simState.focus&&ready){
    lastFocus=simState.focus;
    const target=simState.pointCode?avatarPointTarget(avatar,simState.pointCode,simState.side):null;
    if(target){
     const aim=new T.Vector3().fromArray(target.position);
     const viewDir=new T.Vector3().fromArray(target.normal).normalize();
     if(viewDir.lengthSq()<.01)viewDir.set(0,.08,-1);
     viewDir.add(new T.Vector3(0,.14,0)).normalize();
     controls.target.copy(aim);
     camera.position.copy(aim).addScaledVector(viewDir,.95);
     controls.update();dirty=true;
    }
   }

   const response=teaching.update(simState,camera,el.clientWidth,el.clientHeight);
   if(response){
    effect.target.value.fromArray(response.target.position);
    effect.normal.value.fromArray(response.target.normal);
    effect.deform.value=response.deform;
    effect.activity.value=response.activity;
    effect.color.value.set(response.color);
    effect.tool.value=simState.tool==='finger'?1:0;
   }else{
    effect.deform.value=0;effect.activity.value=0;
   }
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
