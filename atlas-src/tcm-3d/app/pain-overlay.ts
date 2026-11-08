import * as T from 'three';
import {PAIN_CASES,painCase,painSeed,type PainSelection} from './pain-relations';
import type {SimulationState,SurfaceTarget} from './teaching';

export function createPainOverlay(resolvePoint:(code:string,side:1|-1)=>SurfaceTarget){
 const root=new T.Group();root.name='pain-relationships';
 const marker=new T.Group();marker.name='pain-location';root.add(marker);
 const red=new T.MeshBasicMaterial({color:'#c15759',transparent:true,opacity:.26,side:T.DoubleSide,depthWrite:false});
 const disk=new T.Mesh(new T.CircleGeometry(.032,48),red);marker.add(disk);
 const ring=new T.Mesh(new T.RingGeometry(.034,.037,48),new T.MeshBasicMaterial({color:'#bd4c54',transparent:true,opacity:.8,side:T.DoubleSide,depthWrite:false}));marker.add(ring);
 const dots=Array.from({length:8},()=>{const dot=new T.Mesh(new T.SphereGeometry(.006,12,8),new T.MeshBasicMaterial({color:'#8c9fbe',depthTest:false}));dot.renderOrder=20;root.add(dot);return dot;});
 // Dashed explanatory links float outside the body; they are not anatomical tracks.
 const links=Array.from({length:8},()=>{const geometry=new T.BufferGeometry();geometry.setAttribute('position',new T.BufferAttribute(new Float32Array(25*3),3));geometry.setAttribute('lineDistance',new T.BufferAttribute(new Float32Array(25),1));const line=new T.Line(geometry,new T.LineDashedMaterial({color:'#8c9fbe',transparent:true,opacity:.6,dashSize:.012,gapSize:.012,depthTest:false}));line.renderOrder=19;root.add(line);return line;});
 const presets=new Map<string,SurfaceTarget>();let skinAttached=false;
 const axis=new T.Vector3(0,0,1),normal=new T.Vector3(),a=new T.Vector3(),b=new T.Vector3(),mid=new T.Vector3(),v=new T.Vector3(),prev=new T.Vector3();
 const target=(pain:PainSelection|undefined):SurfaceTarget|null=>pain?(pain.target??(pain.topic?(presets.get(`${pain.topic}:${pain.side}`)??painSeed(pain.topic,pain.side)):null)):null;
 const anchor=(skin:T.Mesh)=>{if(skinAttached)return;skin.updateMatrixWorld(true);const ray=new T.Raycaster();for(const c of PAIN_CASES)for(const side of [1,-1] as const){const seed=painSeed(c.id,side);a.fromArray(seed.position);normal.fromArray(seed.normal);ray.set(a.clone().addScaledVector(normal,.2),normal.clone().negate());ray.far=.4;const hit=ray.intersectObject(skin,false)[0];if(hit){const outward=(hit.face?.normal.clone()??normal.clone()).transformDirection(skin.matrixWorld);if(outward.dot(normal)<0)outward.negate();presets.set(`${c.id}:${side}`,{position:hit.point.toArray() as [number,number,number],normal:outward.toArray() as [number,number,number],faceIndex:hit.faceIndex,meshId:'FJ2810'});}}skinAttached=true;};
 const update=(s:SimulationState,visible:boolean)=>{
  const pain=s.pain,t=target(pain),c=painCase(pain?.topic);root.visible=visible&&!!t;if(!t||!pain)return;
  normal.fromArray(t.normal).normalize();marker.position.fromArray(t.position).addScaledVector(normal,.003);marker.quaternion.setFromUnitVectors(axis,normal);marker.scale.setScalar(.7+pain.level*.6);red.opacity=.12+pain.level*.22;
  const codes=c?[...c.local,...c.distal]:[];
  dots.forEach((dot,i)=>{const code=codes[i];dot.visible=links[i].visible=!!code&&pain.showLinks;if(!code)return;const dest=resolvePoint(code,pain.side),local=c!.local.includes(code),color=local?'#b58948':'#42779f';dot.material.color.set(color);dot.position.fromArray(dest.position).addScaledVector(v.fromArray(dest.normal),.004);dot.scale.setScalar(code===s.pointCode&&s.side===pain.side?1.6:1);
   const line=links[i];line.material.color.set(color);line.material.opacity=code===s.pointCode&&s.side===pain.side?(s.running?.8+.2*Math.sin(s.elapsed*3):1):.65;a.copy(marker.position);b.copy(dot.position);mid.copy(a).lerp(b,.5);mid.x+=pain.side*Math.min(.28,a.distanceTo(b)*.35+.045);mid.z+=c!.view==='back'?-.09:.09;const positions=line.geometry.attributes.position,distances=line.geometry.attributes.lineDistance;let distance=0;
   for(let j=0;j<=24;j++){const t=j/24;v.copy(a).multiplyScalar((1-t)*(1-t)).addScaledVector(mid,2*(1-t)*t).addScaledVector(b,t*t);positions.setXYZ(j,v.x,v.y,v.z);if(j)distance+=v.distanceTo(prev);distances.setX(j,distance);prev.copy(v);}positions.needsUpdate=distances.needsUpdate=true;line.geometry.computeBoundingSphere();
  });
 };
 const dispose=()=>{root.traverse(o=>{if(o instanceof T.Mesh||o instanceof T.Line){o.geometry.dispose();const materials=Array.isArray(o.material)?o.material:[o.material];materials.forEach(m=>m.dispose());}});root.removeFromParent();};
 return {root,anchor,target,update,dispose};
}
