import * as T from 'three';
import type {VRMCore} from '@pixiv/three-vrm-core';
import {VRMHumanBoneName} from '@pixiv/three-vrm-core';
import type {VRMPose} from '@pixiv/three-vrm-core';

const quatFromEuler=(x:number,y:number,z:number)=>{
 const q=new T.Quaternion().setFromEuler(new T.Euler(x,y,z,'XYZ'));
 return [q.x,q.y,q.z,q.w] as [number,number,number,number];
};

/** Delta from T-pose: upper arms down (~70°), slightly away from torso, soft elbow bend. */
export const ADULT_STANDING_VRM_POSE:VRMPose={
 [VRMHumanBoneName.LeftUpperArm]:{rotation:quatFromEuler(-0.12,-0.1,1.15)},
 [VRMHumanBoneName.RightUpperArm]:{rotation:quatFromEuler(-0.12,0.1,-1.15)},
 [VRMHumanBoneName.LeftLowerArm]:{rotation:quatFromEuler(0.12,0,0.06)},
 [VRMHumanBoneName.RightLowerArm]:{rotation:quatFromEuler(0.12,0,-0.06)},
};

/** Re-apply normalized pose; pair with `vrm.update` each frame for adult rigs. */
export const primeAdultStandingPose=(vrm:VRMCore)=>{vrm.humanoid.setNormalizedPose(ADULT_STANDING_VRM_POSE);};

/** Natural standing (arms down, slight away from body) on normalized humanoid — adult only. */
export const applyAdultStandingPose=(vrm:VRMCore)=>{
 primeAdultStandingPose(vrm);
 vrm.humanoid.update();
 vrm.scene.updateMatrixWorld(true);
};