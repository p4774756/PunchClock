import * as T from 'three';
import type {VRMCore} from '@pixiv/three-vrm-core';
import {VRMHumanBoneName} from '@pixiv/three-vrm-core';

type BoneName=(typeof VRMHumanBoneName)[keyof typeof VRMHumanBoneName];
import type {AnimePoint} from './character-mode';
import {avatarPointTarget,type AnimeAvatar} from './avatars';
import type {SurfaceTarget} from './teaching';

const BONE_PRIORITY:BoneName[]=[
 VRMHumanBoneName.LeftHand,VRMHumanBoneName.RightHand,
 VRMHumanBoneName.LeftLowerArm,VRMHumanBoneName.RightLowerArm,
 VRMHumanBoneName.LeftUpperArm,VRMHumanBoneName.RightUpperArm,
 VRMHumanBoneName.LeftShoulder,VRMHumanBoneName.RightShoulder,
 VRMHumanBoneName.Head,VRMHumanBoneName.Neck,VRMHumanBoneName.UpperChest,
 VRMHumanBoneName.Chest,VRMHumanBoneName.Spine,VRMHumanBoneName.Hips,
 VRMHumanBoneName.LeftUpperLeg,VRMHumanBoneName.RightUpperLeg,
 VRMHumanBoneName.LeftLowerLeg,VRMHumanBoneName.RightLowerLeg,
 VRMHumanBoneName.LeftFoot,VRMHumanBoneName.RightFoot,
];

type Binding={bone:BoneName;localPos:T.Vector3;localNormal:T.Vector3};

const _w=new T.Vector3(),_n=new T.Vector3(),_inv=new T.Matrix4();

const nearestBone=(vrm:VRMCore,world:T.Vector3):{name:BoneName;dist:number}|null=>{
 let best:BoneName|null=null,bestD=Infinity;
 for(const name of BONE_PRIORITY){
  const node=vrm.humanoid.getNormalizedBoneNode(name);
  if(!node)continue;
  node.getWorldPosition(_w);
  const d=_w.distanceToSquared(world);
  if(d<bestD){bestD=d;best=name;}
 }
 return best?{name:best,dist:Math.sqrt(bestD)}:null;
};

export type AdultPointBindings={
 resolve:(code:string,side:1|-1)=>SurfaceTarget|null;
 refresh:()=>void;
};

export const buildAdultPointBindings=(vrm:VRMCore,avatar:AnimeAvatar,characterRoot:T.Object3D):AdultPointBindings=>{
 vrm.humanoid.update();
 characterRoot.updateMatrixWorld(true);
 vrm.scene.updateMatrixWorld(true);
 const worldToRoot=new T.Matrix4();
 const table=new Map<string,Binding>();
 for(const point of avatar.points){
  for(const side of point.midline?([1] as const):([1,-1] as const)){
   const seed=avatarPointTarget(avatar,point.code,side);
   if(!seed)continue;
   _w.fromArray(seed.position).applyMatrix4(characterRoot.matrixWorld);
   const pick=nearestBone(vrm,_w);
   if(!pick)continue;
   const boneNode=vrm.humanoid.getNormalizedBoneNode(pick.name)!;
   boneNode.updateMatrixWorld(true);
   _inv.copy(boneNode.matrixWorld).invert();
   const localPos=_w.clone().applyMatrix4(_inv);
   _n.fromArray(seed.normal).transformDirection(characterRoot.matrixWorld).normalize();
   const localNormal=_n.clone().transformDirection(_inv).normalize();
   table.set(`${point.code}:${side}`,{bone:pick.name,localPos,localNormal});
  }
 }
 const syncSkeleton=()=>{
  vrm.humanoid.update();
  characterRoot.updateMatrixWorld(true);
  vrm.scene.updateMatrixWorld(true);
 };
 const resolve=(code:string,side:1|-1):SurfaceTarget|null=>{
  const key=`${code}:${side}`;
  const bind=table.get(key);
  if(!bind){
   return avatarPointTarget(avatar,code,side);
  }
  const node=vrm.humanoid.getNormalizedBoneNode(bind.bone);
  if(!node)return avatarPointTarget(avatar,code,side);
  syncSkeleton();
  node.updateMatrixWorld(true);
  const pos=bind.localPos.clone().applyMatrix4(node.matrixWorld);
  worldToRoot.copy(characterRoot.matrixWorld).invert();
  pos.applyMatrix4(worldToRoot);
  const normal=bind.localNormal.clone().transformDirection(node.matrixWorld).transformDirection(worldToRoot).normalize();
  return {position:pos.toArray() as [number,number,number],normal:normal.toArray() as [number,number,number]};
 };
 return {resolve,refresh:syncSkeleton};
};