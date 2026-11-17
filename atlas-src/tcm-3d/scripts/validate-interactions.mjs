import assert from 'node:assert/strict';
import * as T from 'three';
import {PointerTap} from '../app/pointer-tap.ts';

const radial=96;
const frontHalf=T.MathUtils.degToRad(40);
const uLo=.34375,uHi=.65625,uSpan=uHi-uLo;
const woodcutPanoramaTexU=(phi)=>{
 if(Math.abs(phi)<=frontHalf) return .5+(phi/frontHalf)*(uSpan*.5);
 if(phi>frontHalf){const t=(phi-frontHalf)/(Math.PI-frontHalf);return uHi+t*(1-uHi);}
 const t=(phi+frontHalf)/(-Math.PI+frontHalf);return uLo*(1-t);
};
const remapWoodcutPanoramaUv=(geo)=>{
 const uv=geo.getAttribute('uv');
 const perRing=radial+1;
 for(let i=0;i<uv.count;i++){
  const ring=i%perRing;
  let uTex;
  if(ring===0) uTex=0;
  else if(ring===radial) uTex=1;
  else{const theta=(ring/radial)*Math.PI*2;uTex=woodcutPanoramaTexU(theta-3*Math.PI/2);}
  uv.setX(i,uTex);
 }
 const rows=Math.floor(uv.count/perRing);
 for(let row=0;row<rows;row++){
  const base=row*perRing;
  for(let x=0;x<radial;x++){
   const du=Math.abs(uv.getX(base+x+1)-uv.getX(base+x));
   if(x===0||x===radial-1) continue;
   assert(du<.05,`woodcut UV jump ${du.toFixed(4)} row ${row} seg ${x}`);
  }
 }
};
remapWoodcutPanoramaUv(new T.CylinderGeometry(15,15,20,radial,1,true));

const tap=new PointerTap();
tap.down(1,10,10,5);assert.equal(tap.up(1,12,11),true);
tap.down(1,10,10,5);tap.move(1,40,10);assert.equal(tap.up(1,10,10),false);
tap.down(1,10,10,12);tap.down(2,20,20,12);assert.equal(tap.up(2,20,20),false);assert.equal(tap.up(1,10,10),false);
tap.down(1,10,10,5);tap.cancel(1);assert.equal(tap.up(1,10,10),false);
tap.down(1,10,10,5);assert.equal(tap.up(1,10,10),true);
console.log('Woodcut panorama UV continuity checks passed.');
console.log('Tap, drag, multitouch, cancellation checks passed (anatomy packing removed).');
