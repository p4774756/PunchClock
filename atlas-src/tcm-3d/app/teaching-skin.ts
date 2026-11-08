import {BufferGeometry,BufferAttribute} from 'three';
/** Classroom surface simplification; original atlas buffers on disk stay intact. */
export function softenPelvicSurface(geometry:BufferGeometry){
 const position=geometry.getAttribute('position').clone();geometry.setAttribute('position',position);
 const original=position.clone();
 const smooth=(a:number,b:number,x:number)=>{const t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);};
 for(let i=0;i<position.count;i++){
  const x=position.getX(i),y=position.getY(i),z=position.getZ(i);
  const w=(1-smooth(.027,.060,Math.abs(x)))*smooth(.735,.770,y)*(1-smooth(.855,.900,y))*smooth(.005,.038,z);
  if(w===0)continue;
  position.setY(i,y+(.835+(y-.835)*.22-y)*w);
  position.setZ(i,z+(.022+(z-.022)*.1-z)*w);
 }
 // Relax the existing connected surface after flattening. Compressing vertices alone
 // leaves the original folds and normals; this removes those folds with a soft boundary.
 const indices=geometry.index!;
 const neighbors=Array.from({length:position.count},()=>new Set<number>());
 for(let i=0;i<indices.count;i+=3){const a=indices.getX(i),b=indices.getX(i+1),c=indices.getX(i+2);neighbors[a].add(b).add(c);neighbors[b].add(a).add(c);neighbors[c].add(a).add(b);}
 const active:number[]=[],weights=new Float32Array(position.count);
 for(let i=0;i<position.count;i++){
  const x=original.getX(i),y=original.getY(i),z=original.getZ(i);
  const w=(1-smooth(.035,.075,Math.abs(x)))*smooth(.715,.765,y)*(1-smooth(.87,.925,y))*smooth(-.005,.035,z);
  if(w>0){active.push(i);weights[i]=w*.72;}
 }
 const next=new Float32Array(position.count*3);
 for(let iteration=0;iteration<160;iteration++){
  for(const i of active){const adjacent=neighbors[i];if(!adjacent.size)continue;let x=0,y=0,z=0;for(const j of adjacent){x+=position.getX(j);y+=position.getY(j);z+=position.getZ(j);}const w=weights[i];next[i*3]=position.getX(i)*(1-w)+x/adjacent.size*w;next[i*3+1]=position.getY(i)*(1-w)+y/adjacent.size*w;next[i*3+2]=position.getZ(i)*(1-w)+z/adjacent.size*w;}
  for(const i of active)if(neighbors[i].size)position.setXYZ(i,next[i*3],next[i*3+1],next[i*3+2]);
 }
 // Replace the cut region and stitch the new surface to its actual mesh boundary.
 const vertices=Array.from(position.array),faces:number[]=[],edgeMap=new Map<string,{a:number;b:number;kept:number;cut:number}>();
 const cy=.845,rx=.09,ry=.12;
 for(let i=0;i<indices.count;i+=3){const ids=[indices.getX(i),indices.getX(i+1),indices.getX(i+2)],x=ids.reduce((v,j)=>v+original.getX(j),0)/3,y=ids.reduce((v,j)=>v+original.getY(j),0)/3,z=ids.reduce((v,j)=>v+original.getZ(j),0)/3,cut=(x/rx)**2+((y-cy)/ry)**2<1&&z>-.065;
  if(!cut)faces.push(...ids);
  for(let k=0;k<3;k++){const a=ids[k],b=ids[(k+1)%3],key=Math.min(a,b)+':'+Math.max(a,b),edge=edgeMap.get(key)??{a,b,kept:0,cut:0};if(cut)edge.cut++;else edge.kept++;edgeMap.set(key,edge);}
 }
 const boundary=[...edgeMap.values()].filter(e=>e.kept===1&&e.cut>0),adjacent=new Map<number,number[]>();
 for(const e of boundary){adjacent.set(e.a,[...(adjacent.get(e.a)??[]),e.b]);adjacent.set(e.b,[...(adjacent.get(e.b)??[]),e.a]);}
 const visited=new Set<number>();
 for(const first of adjacent.keys()){
  if(visited.has(first))continue;const loop:number[]=[];let current=first,previous=-1;
  while(!visited.has(current)){visited.add(current);loop.push(current);const next=adjacent.get(current)?.find(v=>v!==previous);if(next===undefined)break;previous=current;current=next;}
  if(loop.length<3)continue;
  // Orient the replacement toward the front, and share boundary vertices exactly.
  let area=0;for(let j=0;j<loop.length;j++){const a=loop[j],b=loop[(j+1)%loop.length];area+=position.getX(a)*position.getY(b)-position.getX(b)*position.getY(a);}if(area<0)loop.reverse();
  const mx=loop.reduce((v,j)=>v+position.getX(j),0)/loop.length,my=loop.reduce((v,j)=>v+position.getY(j),0)/loop.length,mz=loop.reduce((v,j)=>v+position.getZ(j),0)/loop.length;
  const center=vertices.length/3;vertices.push(mx,my,mz);let inner=[center];const rings=14;
  for(let r=1;r<=rings;r++){const t=r/rings,outer=r===rings?loop:loop.map(j=>{const id=vertices.length/3;vertices.push(mx+(position.getX(j)-mx)*t,my+(position.getY(j)-my)*t,mz+(position.getZ(j)-mz)*smooth(0,1,t));return id;});
   for(let j=0;j<loop.length;j++){const k=(j+1)%loop.length;if(r===1)faces.push(center,outer[j],outer[k]);else faces.push(inner[j],outer[j],inner[k],inner[k],outer[j],outer[k]);}inner=outer;
  }
 }
 const joined=Array.from({length:vertices.length/3},()=>new Set<number>());
 for(let i=0;i<faces.length;i+=3){const [a,b,c]=faces.slice(i,i+3);joined[a].add(b).add(c);joined[b].add(a).add(c);joined[c].add(a).add(b);}
 const relax=new Float32Array(vertices.length),movable:number[]=[];
 for(let i=0;i<joined.length;i++){const x=vertices[i*3],y=vertices[i*3+1],z=vertices[i*3+2];if(i>=position.count||((x/(rx*1.15))**2+((y-cy)/(ry*1.15))**2<1&&z>-.07))movable.push(i);}
 for(let iteration=0;iteration<180;iteration++){
  for(const i of movable){const ns=joined[i];if(!ns.size)continue;for(let axis=0;axis<3;axis++){let total=0;for(const j of ns)total+=vertices[j*3+axis];relax[i*3+axis]=vertices[i*3+axis]*.35+total/ns.size*.65;}}
  for(const i of movable)if(joined[i].size)for(let axis=0;axis<3;axis++)vertices[i*3+axis]=relax[i*3+axis];
 }
 geometry.setAttribute('position',new BufferAttribute(new Float32Array(vertices),3));geometry.setIndex(new BufferAttribute(new Uint32Array(faces),1));geometry.deleteAttribute('normal');
 geometry.computeVertexNormals();geometry.computeBoundingBox();geometry.computeBoundingSphere();
}
