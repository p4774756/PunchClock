import * as T from 'three';

/**
 * Shared focus framing for teaching overlay and character-scene fallback.
 * Neck points (ST9／CV22／CV23) must be clearly closer than the default .62 —
 * a .6 tweak is visually identical to default and looks “too far”.
 */
export const focusViewFromNormal=(pointCode:string|null|undefined,normal:T.Vector3,side:1|-1)=>{
 const viewDir=new T.Vector3();
 let distance=.62;
 let aimAdjust=new T.Vector3();
 if(pointCode==='GV20'||normal.y>.85){
  viewDir.set(.06,.22,-.97).normalize();
  aimAdjust.set(0,-.1,-.06);
  distance=1.35;
 }else if(pointCode==='ST9'||pointCode==='CV22'||pointCode==='CV23'){
  // 人迎／天突／廉泉：近距正前方，針／指壓要清楚（勿再用 ≈ 預設的 .6）
  viewDir.set(Math.sign(normal.x||side)*.12,.03,-1).normalize();
  aimAdjust.set(0,.015,-.008);
  distance=.34;
 }else if(pointCode==='SI11'){
  // 天宗：從背面正看肩胛岡下窩
  viewDir.set(Math.sign(normal.x||side)*.06,.04,.98).normalize();
  aimAdjust.set(0,.01,.005);
  distance=.68;
 }else if(normal.z>.3){
  viewDir.set(normal.x*.28,Math.max(.06,normal.y*.25+.1),Math.max(.82,Math.abs(normal.z))).normalize();
  distance=.78;
 }else if(Math.abs(normal.x)>.55&&Math.abs(normal.z)<.55){
  viewDir.set(Math.sign(normal.x)*Math.max(.82,Math.abs(normal.x)),Math.max(.06,normal.y*.25+.1),normal.z*.28).normalize();
  distance=.75;
 }else if(normal.y<-.75){
  viewDir.set(.25,-.45,-1).normalize();
  distance=.7;
 }else{
  viewDir.set(normal.x*.28-.1,Math.max(.06,normal.y*.25+.1),-Math.max(.82,Math.abs(normal.z))).normalize();
  distance=.72;
 }
 return {viewDir,distance,aimAdjust};
};
