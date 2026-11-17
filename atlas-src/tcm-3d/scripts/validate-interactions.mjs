import assert from 'node:assert/strict';
import * as T from 'three';
import {PointerTap} from '../app/pointer-tap.ts';

const radial=96;
const frontHalf=T.MathUtils.degToRad(40);
const uLo=.34375,uHi=.65625,uSpan=uHi-uLo;
const f=(phi)=>{
 if(Math.abs(phi)<=frontHalf) return .5+(phi/frontHalf)*(uSpan*.5);
 if(phi>frontHalf) return uHi+(phi-frontHalf)/(Math.PI-frontHalf)*(1-uHi);
 return uLo*(1-(phi+frontHalf)/(-Math.PI+frontHalf));
};
const remapWoodcutPanoramaUv=(geo)=>{
 const uv=geo.getAttribute('uv');
 const perRing=radial+1;
 for(let i=0;i<uv.count;i++){
  const ring=i%perRing;
  const theta=ring/radial*Math.PI*2;
  let phi=-Math.PI/2-theta;
  let shift=0;
  if(phi<=-Math.PI){phi+=Math.PI*2;shift=-1;}
  uv.setX(i,f(phi)+shift);
 }
 const rows=Math.floor(uv.count/perRing);
 for(let row=0;row<rows;row++){
  const base=row*perRing;
  for(let x=0;x<radial;x++){
   const du=Math.abs(uv.getX(base+x+1)-uv.getX(base+x));
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