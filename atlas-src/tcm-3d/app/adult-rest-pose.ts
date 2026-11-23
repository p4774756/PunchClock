import * as T from 'three';
import type {VRMCore} from '@pixiv/three-vrm-core';
import {VRMHumanBoneName} from '@pixiv/three-vrm-core';
import type {VRMPose} from '@pixiv/three-vrm-core';

const quatFromEuler=(x:number,y:number,z:number)=>{
 const q=new T.Quaternion().setFromEuler(new T.Euler(x,y,z,'XYZ'));
 return [q.x,q.y,q.z,q.w] as [number,number,number,number];
};

/** Natural standing (arms down, slight away from body) on normalized humanoid — adult only. */
export const applyAdultStandingPose=(vrm:VRMCore)=>{
 const humanoid=vrm.humanoid;
 const pose:VRMPose={
  [VRMHumanBoneName.LeftUpperArm]:{rotation:quatFromEuler(-.2,-.06,.78)},
  [VRMHumanBoneName.RightUpperArm]:{rotation:quatFromEuler(-.2,.06,-.78)},
  [VRMHumanBoneName.LeftLowerArm]:{rotation:quatFromEuler(-.12,0,0)},
  [VRMHumanBoneName.RightLowerArm]:{rotation:quatFromEuler(-.12,0,0)},
 };
 humanoid.setNormalizedPose(pose);
 humanoid.update();
 vrm.scene.updateMatrixWorld(true);
};