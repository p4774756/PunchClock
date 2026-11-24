import * as T from 'three';
import type {createTeachingOverlay} from './teaching-overlay';

type TeachingOverlay=ReturnType<typeof createTeachingOverlay>;

export const stashTeachingOverlay=(teaching:TeachingOverlay)=>{
 teaching.label.hidden=true;
 teaching.label.remove();
 teaching.root.removeFromParent();
 teaching.root.visible=false;
 teaching.resetAnchorState?.();
};

export const restoreTeachingOverlay=(teaching:TeachingOverlay,scene:T.Scene,host:HTMLElement)=>{
 if(teaching.root.parent!==scene)scene.add(teaching.root);
 teaching.root.visible=true;
 if(!host.contains(teaching.label))host.appendChild(teaching.label);
};

export const reparentCharacterModel=(model:T.Object3D|null,characterRoot:T.Group)=>{
 if(!model)return;
 model.removeFromParent();
 characterRoot.add(model);
};

export const detachCharacterModels=(characterRoot:T.Group)=>{
 while(characterRoot.children.length)characterRoot.children[0].removeFromParent();
};