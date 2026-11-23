import * as T from 'three';

const SKIN_FILL=new T.Color(232/255,198/255,178/255);
const SUIT_TOPS={r:176,g:178,b:182};
const SUIT_BOTTOMS={r:156,g:158,b:164};
const SUIT_SHOES={r:62,g:58,b:56};
const CASUAL_TOPS={r:252,g:252,b:252};
const CASUAL_BOTTOMS={r:136,g:184,b:226};
const CASUAL_SHOES={r:245,g:245,b:242};
const ADULT_SPORT={r:38,g:128,b:122};
const SHEER_INNER={r:248,g:246,b:242};

type Rgb={r:number;g:number;b:number};
type Style='suit'|'casual';

const findGarmentMap=(m:T.MeshStandardMaterial)=>{
 const viaUniform=(m as {uniforms?:{litMap?:{value:unknown};map?:{value:unknown}}}).uniforms;
 const fromUni=viaUniform?.litMap?.value??viaUniform?.map?.value;
 if(fromUni&&typeof fromUni==='object'&&'isTexture' in (fromUni as object))return fromUni as T.Texture;
 return m.map??null;
};

const setGarmentMap=(m:T.MeshStandardMaterial,tex:T.Texture)=>{
 m.map=tex;
 const viaUniform=(m as {uniforms?:{litMap?:{value:unknown};map?:{value:unknown}}}).uniforms;
 if(viaUniform?.litMap)viaUniform.litMap.value=tex;
 if(viaUniform?.map)viaUniform.map.value=tex;
 m.needsUpdate=true;
};

const findSkinMap=(m:T.MeshStandardMaterial)=>{
 const viaUniform=(m as {uniforms?:{map?:{value:unknown}}}).uniforms;
 const fromUni=viaUniform?.map?.value;
 if(fromUni&&typeof fromUni==='object'&&'isTexture' in (fromUni as object))return fromUni as T.Texture;
 return m.map??null;
};

const setSkinMap=(m:T.MeshStandardMaterial,tex:T.Texture)=>{
 m.map=tex;
 const viaUniform=(m as {uniforms?:{map?:{value:unknown}}}).uniforms;
 if(viaUniform?.map)viaUniform.map.value=tex;
 m.needsUpdate=true;
};

const tintCanvas=(src:T.Texture,rgb:Rgb):T.Texture|null=>{
 if(!src?.image)return null;
 const img=src.image as CanvasImageSource & {width:number;height:number};
 const canvas=document.createElement('canvas');
 canvas.width=img.width;canvas.height=img.height;
 const ctx=canvas.getContext('2d',{willReadFrequently:true});
 if(!ctx)return null;
 try{ctx.drawImage(img,0,0);}catch{return null;}
 const data=ctx.getImageData(0,0,canvas.width,canvas.height);
 for(let i=0;i<data.data.length;i+=4){
  if(data.data[i+3]<16)continue;
  data.data[i]=Math.round(data.data[i]*0.15+rgb.r*0.85);
  data.data[i+1]=Math.round(data.data[i+1]*0.15+rgb.g*0.85);
  data.data[i+2]=Math.round(data.data[i+2]*0.15+rgb.b*0.85);
  data.data[i+3]=255;
 }
 ctx.putImageData(data,0,0);
 const next=src.clone();
 next.image=canvas;
 next.colorSpace=src.colorSpace??T.SRGBColorSpace;
 next.needsUpdate=true;
 return next;
};

const slotRgb=(token:string,style:Style):Rgb=>{
 if(style==='suit'){
  if(/Shoes/i.test(token))return SUIT_SHOES;
  if(/Bottoms/i.test(token))return SUIT_BOTTOMS;
  return SUIT_TOPS;
 }
 if(/Shoes/i.test(token))return CASUAL_SHOES;
 if(/Bottoms/i.test(token))return CASUAL_BOTTOMS;
 return CASUAL_TOPS;
};

export const snapshotAdultClothMaps=(root:T.Object3D)=>{
 const maps=new Map<T.Material,T.Texture>();
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   const label=`${m.name||''} ${obj.name||''}`;
   if(!/Tops|Bottoms|Shoes/i.test(label)||/Outline/i.test(label))continue;
   const map=findGarmentMap(m);
   if(map)maps.set(m,map);
  }
 });
 return maps;
};

export const applyAdultClothStyle=(root:T.Object3D,style:Style,baseline:Map<T.Material,T.Texture>)=>{
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  obj.visible=true;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial & {visible?:boolean};
   const label=`${m.name||''} ${obj.name||''}`;
   if(!/Tops|Bottoms|Shoes/i.test(label)||/Outline/i.test(label))continue;
   m.visible=true;
   m.transparent=false;
   m.opacity=1;
   m.depthWrite=true;
   const base=baseline.get(m)??findGarmentMap(m);
   if(!base)return;
   const token=label;
   const painted=tintCanvas(base,slotRgb(token,style));
   if(painted)setGarmentMap(m,painted);
  }
 });
};

const inSportTop=(x:number,y:number,z:number)=>{
 if(Math.abs(x)>.14)return false;
 const t=Math.max(0,Math.min(1,(z+.02)/.1));
 const y0=1.12+(1.14-1.12)*t;
 const y1=1.26+(1.24-1.26)*t;
 return y>=y0&&y<=y1;
};
const inSportShorts=(x:number,y:number,z:number)=>{
 if(y<.82||y>1.02||Math.abs(x)>.17)return false;
 return Math.abs(z)<.15;
};
const inBandeau=(x:number,y:number,z:number)=>{
 if(Math.abs(x)>.13)return false;
 return y>=1.14&&y<=1.24;
};
const inSheerShorts=(x:number,y:number,z:number)=>{
 if(y<.82||y>1.0||Math.abs(x)>.16)return false;
 return Math.abs(z)<.14;
};

const writeUvTriangleFill=(data:ImageData,width:number,height:number,flipY:boolean,u0:number,v0:number,u1:number,v1:number,u2:number,v2:number,rgb:T.Color)=>{
 const toX=(u:number)=>Math.min(width-1,Math.max(0,Math.round(u*(width-1))));
 const toY=(v:number)=>Math.min(height-1,Math.max(0,Math.round((flipY?1-v:v)*(height-1))));
 const x0=toX(u0),y0=toY(v0),x1=toX(u1),y1=toY(v1),x2=toX(u2),y2=toY(v2);
 const minX=Math.max(0,Math.min(x0,x1,x2)),maxX=Math.min(width-1,Math.max(x0,x1,x2));
 const minY=Math.max(0,Math.min(y0,y1,y2)),maxY=Math.min(height-1,Math.max(y0,y1,y2));
 const area=(x1-x0)*(y2-y0)-(x2-x0)*(y1-y0);
 if(area===0)return;
 for(let y=minY;y<=maxY;y++)for(let x=minX;x<=maxX;x++){
  const w0=((x1-x)*(y2-y)-(x2-x)*(y1-y))/area;
  const w1=((x2-x)*(y0-y)-(x0-x)*(y2-y))/area;
  const w2=1-w0-w1;
  if(w0<-.01||w1<-.01||w2<-.01)continue;
  const i=(y*width+x)*4;
  if(data.data[i+3]>=200)continue;
  data.data[i]=rgb.r*255;
  data.data[i+1]=rgb.g*255;
  data.data[i+2]=rgb.b*255;
  data.data[i+3]=255;
 }
};

const writeUvTriangleCloth=(data:ImageData,width:number,height:number,flipY:boolean,u0:number,v0:number,wa:T.Vector3,u1:number,v1:number,wb:T.Vector3,u2:number,v2:number,wc:T.Vector3,pred:(x:number,y:number,z:number)=>boolean,rgb:Rgb)=>{
 const toX=(u:number)=>Math.min(width-1,Math.max(0,Math.round(u*(width-1))));
 const toY=(v:number)=>Math.min(height-1,Math.max(0,Math.round((flipY?1-v:v)*(height-1))));
 const x0=toX(u0),y0=toY(v0),x1=toX(u1),y1=toY(v1),x2=toX(u2),y2=toY(v2);
 const minX=Math.max(0,Math.min(x0,x1,x2)),maxX=Math.min(width-1,Math.max(x0,x1,x2));
 const minY=Math.max(0,Math.min(y0,y1,y2)),maxY=Math.min(height-1,Math.max(y0,y1,y2));
 const area=(x1-x0)*(y2-y0)-(x2-x0)*(y1-y0);
 if(area===0)return;
 const cx=(wa.x+wb.x+wc.x)/3,cy=(wa.y+wb.y+wc.y)/3,cz=(wa.z+wb.z+wc.z)/3;
 if(!pred(cx,cy,cz))return;
 const smooth=(e0:number,e1:number,x:number)=>{const t=Math.max(0,Math.min(1,(x-e0)/Math.max(1e-4,e1-e0)));return t*t*(3-2*t);};
 for(let y=minY;y<=maxY;y++)for(let x=minX;x<=maxX;x++){
  const w0=((x1-x)*(y2-y)-(x2-x)*(y1-y))/area;
  const w1=((x2-x)*(y0-y)-(x0-x)*(y2-y))/area;
  const w2=1-w0-w1;
  if(w0<-.02||w1<-.02||w2<-.02)continue;
  const wx=w0*wa.x+w1*wb.x+w2*wc.x;
  const wy=w0*wa.y+w1*wb.y+w2*wc.y;
  const wz=w0*wa.z+w1*wb.z+w2*wc.z;
  if(!pred(wx,wy,wz))continue;
  const i=(y*width+x)*4;
  const blend=Math.min(1,Math.max(smooth(0.34,0.66,w0),Math.max(smooth(0.34,0.66,w1),smooth(0.34,0.66,w2))));
  data.data[i]=Math.round(data.data[i]*(1-blend)+rgb.r*blend);
  data.data[i+1]=Math.round(data.data[i+1]*(1-blend)+rgb.g*blend);
  data.data[i+2]=Math.round(data.data[i+2]*(1-blend)+rgb.b*blend);
  data.data[i+3]=255;
 }
};

export const paintAdultSwimSkin=(root:T.Object3D,kind:'sport'|'sheer-inner')=>{
 const rgb=kind==='sport'?ADULT_SPORT:SHEER_INNER;
 const skinMats:T.MeshStandardMaterial[]=[];
 const skinMeshes:(T.Mesh|T.SkinnedMesh)[]=[];
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  const body=mats.filter(m=>/Body_00_SKIN/i.test(m.name||'')&&!/Outline/i.test(m.name||'')) as T.MeshStandardMaterial[];
  if(!body.length)return;
  skinMeshes.push(obj);
  for(const m of body)if(!skinMats.includes(m))skinMats.push(m);
 });
 if(!skinMeshes.length)return;
 const src=skinMats.map(findSkinMap).find(Boolean)??null;
 if(!src?.image)return;
 const img=src.image as CanvasImageSource & {width:number;height:number};
 const canvas=document.createElement('canvas');
 canvas.width=img.width;canvas.height=img.height;
 const ctx=canvas.getContext('2d',{willReadFrequently:true});
 if(!ctx)return;
 try{ctx.drawImage(img,0,0);}catch{return;}
 const data=ctx.getImageData(0,0,canvas.width,canvas.height);
 const skinRgb=new T.Color(SKIN_FILL);
 const flipY=!!src.flipY;
 const topPred=kind==='sport'?inSportTop:inBandeau;
 const shortsPred=kind==='sport'?inSportShorts:inSheerShorts;
 for(const mesh of skinMeshes){
  const geom=mesh.geometry;
  const pos=geom.getAttribute('position');
  const uv=geom.getAttribute('uv');
  const index=geom.index;
  if(!pos||!uv||!index)continue;
  mesh.updateMatrixWorld(true);
  const a=new T.Vector3(),b=new T.Vector3(),c=new T.Vector3();
  for(let i=0;i<index.count;i+=3){
   const i0=index.getX(i),i1=index.getX(i+1),i2=index.getX(i+2);
   a.fromBufferAttribute(pos,i0).applyMatrix4(mesh.matrixWorld);
   b.fromBufferAttribute(pos,i1).applyMatrix4(mesh.matrixWorld);
   c.fromBufferAttribute(pos,i2).applyMatrix4(mesh.matrixWorld);
   const u0=uv.getX(i0),v0=uv.getY(i0),u1=uv.getX(i1),v1=uv.getY(i1),u2=uv.getX(i2),v2=uv.getY(i2);
   writeUvTriangleFill(data,canvas.width,canvas.height,flipY,u0,v0,u1,v1,u2,v2,skinRgb);
   writeUvTriangleCloth(data,canvas.width,canvas.height,flipY,u0,v0,a,u1,v1,b,u2,v2,c,topPred,rgb);
   writeUvTriangleCloth(data,canvas.width,canvas.height,flipY,u0,v0,a,u1,v1,b,u2,v2,c,shortsPred,rgb);
  }
 }
 ctx.putImageData(data,0,0);
 const next=src.clone();
 next.image=canvas;
 next.colorSpace=src.colorSpace??T.SRGBColorSpace;
 next.needsUpdate=true;
 root.userData.practiceBodySkinMap=next;
 for(const mesh of skinMeshes){
  const mats=Array.isArray(mesh.material)?mesh.material:[mesh.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   if(!/Body_00_SKIN/i.test(m.name||'')||/Outline/i.test(m.name||''))continue;
   setSkinMap(m,next);
  }
 }
 return next;
};

export const applyAdultSheerOuter=(root:T.Object3D)=>{
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   const label=`${m.name||''} ${obj.name||''}`;
   if(!/Tops/i.test(label)||/Outline/i.test(label))continue;
   m.visible=true;
   m.transparent=true;
   m.opacity=0.42;
   m.depthWrite=false;
   m.side=T.DoubleSide;
   const map=findGarmentMap(m);
   if(map){
    const white=tintCanvas(map,SHEER_INNER);
    if(white)setGarmentMap(m,white);
   }
   m.renderOrder=2;
   m.needsUpdate=true;
  }
 });
};

export const resetAdultSheerTopsOpaque=(root:T.Object3D,baseline:Map<T.Material,T.Texture>)=>{
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   const label=`${m.name||''} ${obj.name||''}`;
   if(!/Tops/i.test(label)||/Outline/i.test(label))continue;
   m.transparent=false;
   m.opacity=1;
   m.depthWrite=true;
   m.renderOrder=0;
   const base=baseline.get(m);
   if(base)setGarmentMap(m,base);
  }
 });
};