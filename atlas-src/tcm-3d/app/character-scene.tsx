import {publicAssetUrl} from './public-assets';
import {ANIME_MODEL_URL,ANIME_POINTS,animePointTarget} from './character-mode';
import {useEffect,useRef} from 'react';
import * as T from 'three';
import {OrbitControls} from 'three/examples/jsm/controls/OrbitControls.js';
import {RoomEnvironment} from 'three/examples/jsm/environments/RoomEnvironment.js';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import {VRMLoaderPlugin,VRMUtils} from '@pixiv/three-vrm';
import {PointerTap} from './pointer-tap';
import type {SceneState} from './anatomy';
import type {SimulationState} from './teaching';

interface Props{
 state:SceneState;
 simulation:SimulationState;
 onPoint:(code:string,side:1|-1)=>void;
 onProgress:(n:number)=>void;
 onError:(s:string)=>void;
}

const toonGradient=()=>{
 const data=new Uint8Array([90,90,90,255,168,168,168,255,255,255,255,255]);
 const tex=new T.DataTexture(data,3,1,T.RGBAFormat);
 tex.magFilter=T.NearestFilter;tex.minFilter=T.NearestFilter;tex.needsUpdate=true;return tex;
};

export default function CharacterScene({state,simulation,onPoint,onProgress,onError}:Props){
 const host=useRef<HTMLDivElement>(null);
 const latest=useRef(state);const sim=useRef(simulation);const pointSelect=useRef(onPoint);
 latest.current=state;sim.current=simulation;pointSelect.current=onPoint;

 useEffect(()=>{
  const el=host.current!;let disposed=false,frame=0,dirty=true,ready=false,lastView='',lastReset=-1,lastFocus=-1;
  let renderer:T.WebGLRenderer;
  try{renderer=new T.WebGLRenderer({antialias:true,alpha:false,powerPreference:'high-performance'});}
  catch{onError('無法啟動 3D 視圖，請使用支持 WebGL 的瀏覽器。');return;}
  renderer.setPixelRatio(Math.min(devicePixelRatio,innerWidth<768?1.5:2));
  renderer.setClearColor('#f6efe4');renderer.outputColorSpace=T.SRGBColorSpace;
  renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=1.05;
  el.appendChild(renderer.domElement);
  renderer.domElement.setAttribute('aria-label','經絡研習室動漫角色：拖動旋轉，滾輪縮放，點擊穴位標記。');

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
  const markerRoot=new T.Group();scene.add(markerRoot);
  // Depth-tested dots: translucent hair／collar keep landmarks readable from the outside,
  // while back-facing points stay hidden so the rear view does not show facial holes.
  const sphere=new T.SphereGeometry(.0064,16,12);
  const idleMat=new T.MeshBasicMaterial({color:'#2f8f7c',transparent:true,opacity:.94,depthTest:true,depthWrite:false});
  const activeColor='#e2b15a';
  const markers:T.Mesh[]=[];
  const toCamera=new T.Vector3(),outward=new T.Vector3();
   for(const point of ANIME_POINTS)for(const side of point.midline?[1 as const]:[1 as const,-1 as const]){
   const target=animePointTarget(point.code,side)!;
   const dot=new T.Mesh(sphere,idleMat.clone());
   const normal=new T.Vector3().fromArray(target.normal).normalize();
   // Neck points sit near collar／bow; pull farther out so ST9／CV22 stay readable.
   const lift=/^(ST9|CV22|CV23)$/.test(point.code)?.02:.012;
   dot.userData={point:point.code,side,normal};
   dot.renderOrder=20;
   dot.position.fromArray(target.position).addScaledVector(normal,lift);
   markerRoot.add(dot);markers.push(dot);
  }

  const label=document.createElement('div');label.className='contact-label';label.hidden=true;el.appendChild(label);

  const fit=(view:string)=>{
   const mobile=el.clientWidth<768||(el.clientWidth<=1100&&el.clientHeight<=500);
   const reserved=mobile?210:260;
   const distance=mobile?Math.max(1.55*el.clientHeight/Math.max(160,el.clientHeight-reserved),.9/camera.aspect)/(2*Math.tan(T.MathUtils.degToRad(camera.fov/2))):2.55;
   // Anime face looks toward -Z, so "front" places the camera on -Z.
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

  const loader=new GLTFLoader();
  loader.register(parser=>new VRMLoaderPlugin(parser));
  loader.load(publicAssetUrl(ANIME_MODEL_URL),(gltf)=>{
   if(disposed)return;
   const vrm=gltf.userData.vrm;
   const model=vrm?.scene??gltf.scene;
   if(vrm){
    VRMUtils.removeUnnecessaryVertices(gltf.scene);
    VRMUtils.combineSkeletons(gltf.scene);
    // Keep the authored facing used by anime-points.json (face toward -Z). Do not rotateVRM0 here.
   }
   model.traverse((obj:T.Object3D)=>{
    if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
    obj.frustumCulled=false;
    const mats=Array.isArray(obj.material)?obj.material:[obj.material];
    for(const mat of mats){
     const m=mat as T.MeshStandardMaterial;
     const name=`${m.name||''} ${obj.name||''}`;
     if(m.map){m.map.colorSpace=T.SRGBColorSpace;m.map.needsUpdate=true;}
     if(/HAIR|髪|HairBack/i.test(name)){
      // Keep silhouette, but clear ear／temple／nape so TE23・太陽・GB1 stay visible.
      m.transparent=true;
      m.opacity=/HairBack|Back/i.test(name)?Math.min(m.opacity??1,.42):Math.min(m.opacity??1,.55);
      m.depthWrite=false;
      m.side=T.DoubleSide;
      m.needsUpdate=true;
     }else if(/Tops|CLOTH|Accessory/i.test(name)){
      // Soften collar／bow so 人迎／天突 near the neckline are not buried.
      m.transparent=true;
      m.opacity=Math.min(m.opacity??1,.62);
      m.depthWrite=false;
      m.needsUpdate=true;
     }
    }
   });
   characterRoot.add(model);
   model.updateMatrixWorld(true);
   ready=true;onProgress(100);fit(latest.current.view);dirty=true;
  },xhr=>{if(xhr.total)onProgress(Math.min(99,Math.round(xhr.loaded/xhr.total*100)));},()=>{if(!disposed)onError('動漫角色模型載入失敗，請確認 models/anime-assistant.glb 是否存在。');});

  const raycaster=new T.Raycaster(),pointer=new T.Vector2(),tap=new PointerTap();
  const down=(e:PointerEvent)=>tap.down(e.pointerId,e.clientX,e.clientY,e.pointerType==='touch'?12:5);
  const move=(e:PointerEvent)=>{tap.move(e.pointerId,e.clientX,e.clientY);};
  const cancel=(e:PointerEvent)=>tap.cancel(e.pointerId);
  const up=(e:PointerEvent)=>{
   if(!tap.up(e.pointerId,e.clientX,e.clientY)||!ready)return;
   const rect=renderer.domElement.getBoundingClientRect();
   pointer.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);
   raycaster.setFromCamera(pointer,camera);
   const hit=raycaster.intersectObjects(markers.filter(m=>m.visible),false)[0];
   if(hit)pointSelect.current(hit.object.userData.point,hit.object.userData.side);
  };
  renderer.domElement.addEventListener('pointerdown',down);
  renderer.domElement.addEventListener('pointermove',move);
  renderer.domElement.addEventListener('pointerup',up);
  renderer.domElement.addEventListener('pointercancel',cancel);

  const projected=new T.Vector3();
  const animate=()=>{
   if(disposed)return;frame=requestAnimationFrame(animate);
   const s=latest.current,simState=sim.current;
   if(s.view!==lastView||s.reset!==lastReset){fit(s.view);lastView=s.view;lastReset=s.reset;}
   controls.autoRotate=s.rotate;controls.autoRotateSpeed=.55;controls.update();if(controls.autoRotate)dirty=true;

   for(const dot of markers){
    const selected=dot.userData.point===simState.pointCode&&(dot.userData.side===simState.side||ANIME_POINTS.find(p=>p.code===simState.pointCode)?.midline);
    const allowed=!simState.curriculumSet||simState.curriculumSet==='all'||simState.curriculumSet==='head-neck-21';
    const facing=outward.copy(dot.userData.normal as T.Vector3).dot(toCamera.copy(camera.position).sub(dot.position).normalize())>.08;
    // Selected points stay visible even near grazing angles so focus labels do not vanish.
    dot.visible=!!simState.showPoints&&allowed&&!!(facing||selected);
    const mat=dot.material as T.MeshBasicMaterial;
    mat.color.set(selected?activeColor:'#2f8f7c');
    mat.opacity=selected?.98:.9;
    dot.scale.setScalar(selected?1.55:1);
   }

   if(simState.focus&&lastFocus!==simState.focus&&ready){
    lastFocus=simState.focus;
    const target=simState.pointCode?animePointTarget(simState.pointCode,simState.side):null;
    if(target){
     const aim=new T.Vector3().fromArray(target.position);
     // Outward surface normal points away from skin; place the camera outside along it
     // (same convention as the anatomy overlay). Negating it was putting face points behind the head.
     const viewDir=new T.Vector3().fromArray(target.normal).normalize();
     if(viewDir.lengthSq()<.01)viewDir.set(0,.08,-1);
     viewDir.add(new T.Vector3(0,.14,0)).normalize();
     controls.target.copy(aim);
     camera.position.copy(aim).addScaledVector(viewDir,.95);
     controls.update();dirty=true;
    }
   }

   const active=simState.pointCode?animePointTarget(simState.pointCode,simState.side):null;
   if(active){
    projected.fromArray(active.position).project(camera);
    const onScreen=projected.z>=-1&&projected.z<=1;
    label.hidden=!onScreen;label.textContent=ANIME_POINTS.find(p=>p.code===simState.pointCode)?.name??'';
    label.style.left=`${(projected.x+1)*el.clientWidth/2+14}px`;
    label.style.top=`${(1-projected.y)*el.clientHeight/2-18}px`;
   }else label.hidden=true;

   if(dirty){renderer.render(scene,camera);dirty=false;}
  };
  animate();

  const contextLost=(e:Event)=>{e.preventDefault();onError('設備暫停了 3D 顯示，請重新載入頁面。');};
  renderer.domElement.addEventListener('webglcontextlost',contextLost);

  return()=>{
   disposed=true;cancelAnimationFrame(frame);observer.disconnect();
   controls.dispose();gradient.dispose();sphere.dispose();idleMat.dispose();
   markers.forEach(m=>{(m.material as T.Material).dispose();});
   scene.traverse(o=>{if(o instanceof T.Mesh){o.geometry.dispose();const ms=Array.isArray(o.material)?o.material:[o.material];ms.forEach(m=>m.dispose());}});
   env.dispose();label.remove();renderer.dispose();renderer.domElement.remove();
  };
 },[]);

 return <div className="scene" ref={host}/>;
}
