import * as T from 'three';
import type {AvatarId} from './avatars';
import type {AdultPointBindings} from './adult-point-bindings';
import type {createTeachingOverlay} from './teaching-overlay';
type MeshAssetSnapshot={materials:unknown[];geometry:unknown;indexCount:number|null};

export type CachedAvatarRig={
 vrm:import('@pixiv/three-vrm-core').VRMCore|null;
 schoolModel:T.Object3D|null;
 practiceModel:T.Object3D|null;
 practiceShell:T.Object3D|null;
 practiceCleanTemplate:T.Object3D|null;
 practiceBuilt:boolean;
 sheerModel:T.Object3D|null;
 sheerShell:T.Object3D|null;
 sheerBuilt:boolean;
 practiceWarm:boolean;
 cacheAnimeOutfitsFlag:boolean;
 adultClothBaseline:Map<T.Material,T.Texture>|null;
 uniformAssetSnap:Map<string,MeshAssetSnapshot>|null;
 postWarmWarmedKeys:Set<string>;
 teaching:ReturnType<typeof createTeachingOverlay>;
 adultPointBindings:AdultPointBindings|null;
 clothPeekMaterials:Set<T.Material>;
 ready:boolean;
 skinProxyBakeScheduled:boolean;
 postWarmScheduled:boolean;
 postWarmQueueLen:number;
};

const rigs=new Map<AvatarId,CachedAvatarRig>();

export const peekAvatarRig=(id:AvatarId)=>rigs.get(id)??null;
export const takeAvatarRig=(id:AvatarId)=>{
 const hit=rigs.get(id);
 if(hit)rigs.delete(id);
 return hit??null;
};
export const stashAvatarRig=(id:AvatarId,rig:CachedAvatarRig)=>{rigs.set(id,rig);};

export const clearAvatarSceneCache=(disposeRoot:(root:T.Object3D)=>void,disposeTeaching:(t:ReturnType<typeof createTeachingOverlay>)=>void)=>{
 for(const rig of rigs.values()){
  if(rig.schoolModel)disposeRoot(rig.schoolModel);
  if(rig.practiceModel)disposeRoot(rig.practiceModel);
  if(rig.sheerModel)disposeRoot(rig.sheerModel);
  if(rig.practiceShell&&!rig.practiceBuilt)disposeRoot(rig.practiceShell);
  if(rig.sheerShell&&!rig.sheerBuilt)disposeRoot(rig.sheerShell);
  if(rig.practiceCleanTemplate)disposeRoot(rig.practiceCleanTemplate);
  disposeTeaching(rig.teaching);
 }
 rigs.clear();
};