import * as T from 'three';

const SKIN_FILL=new T.Color(232/255,198/255,178/255);
const SUIT_TOPS={r:42,g:46,b:58};
const SUIT_SHIRT={r:248,g:246,b:242};
const SUIT_BOTTOMS={r:58,g:62,b:72};
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

type TintOpts={solid?:boolean;preserveAlpha?:boolean;keep?:number};

const resetMapUv=(tex:T.Texture)=>{
 tex.repeat.set(1,1);
 tex.offset.set(0,0);
 tex.wrapS=T.ClampToEdgeWrapping;
 tex.wrapT=T.ClampToEdgeWrapping;
 tex.matrix.identity();
 tex.needsUpdate=true;
};

const tintCanvas=(src:T.Texture,rgb:Rgb,opts:TintOpts|boolean=false):T.Texture|null=>{
 const o=typeof opts==='boolean'?{solid:opts}:{...opts};
 const solid=!!o.solid;
 const preserveAlpha=!!o.preserveAlpha;
 const keep=solid?0:o.keep??.15;
 if(!src?.image)return null;
 const img=src.image as CanvasImageSource & {width:number;height:number};
 const canvas=document.createElement('canvas');
 canvas.width=img.width;canvas.height=img.height;
 const ctx=canvas.getContext('2d',{willReadFrequently:true});
 if(!ctx)return null;
 try{ctx.drawImage(img,0,0);}catch{return null;}
 const data=ctx.getImageData(0,0,canvas.width,canvas.height);
 for(let i=0;i<data.data.length;i+=4){
  const a=data.data[i+3];
  if(a<16)continue;
  if(solid){
   data.data[i]=rgb.r;data.data[i+1]=rgb.g;data.data[i+2]=rgb.b;
   data.data[i+3]=255;
  }else{
   data.data[i]=Math.round(data.data[i]*keep+rgb.r*(1-keep));
   data.data[i+1]=Math.round(data.data[i+1]*keep+rgb.g*(1-keep));
   data.data[i+2]=Math.round(data.data[i+2]*keep+rgb.b*(1-keep));
   if(!preserveAlpha)data.data[i+3]=255;
  }
 }
 ctx.putImageData(data,0,0);
 const next=src.clone();
 next.image=canvas;
 next.colorSpace=T.SRGBColorSpace;
 next.generateMipmaps=true;
 next.minFilter=T.LinearMipmapLinearFilter;
 next.magFilter=T.LinearFilter;
 resetMapUv(next);
 next.needsUpdate=true;
 return next;
};

const textureHasSoftAlpha=(tex:T.Texture):boolean=>{
 const img=tex.image as {width?:number;height?:number}|null;
 if(!img?.width||!img?.height)return false;
 const canvas=document.createElement('canvas');
 canvas.width=img.width;canvas.height=img.height;
 const ctx=canvas.getContext('2d',{willReadFrequently:true});
 if(!ctx)return false;
 try{ctx.drawImage(img as CanvasImageSource,0,0);}catch{return false;}
 const data=ctx.getImageData(0,0,canvas.width,canvas.height);
 for(let i=3;i<data.data.length;i+=4){
  const a=data.data[i];
  if(a>=24&&a<248)return true;
 }
 return false;
};

const isInnerTop=(token:string)=>/Inner|Tops_01|Shirt|Blouse/i.test(token)&&!/Outer|Cardigan|Jacket|Coat/i.test(token);

const slotRgb=(token:string,style:Style):Rgb=>{
 if(style==='suit'){
  if(/Shoes/i.test(token))return SUIT_SHOES;
  if(/Bottoms/i.test(token))return SUIT_BOTTOMS;
  if(isInnerTop(token))return SUIT_SHIRT;
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
   const base=baseline.get(m)??findGarmentMap(m);
   if(!base)return;
   resetMapUv(base);
   const token=label;
   const flat=style==='suit'&&(/Tops/i.test(label)||isInnerTop(token));
   const painted=tintCanvas(base,slotRgb(token,style),flat?{solid:true}:{preserveAlpha:true,keep:.12});
   if(painted){
    setGarmentMap(m,painted);
    const lace=!flat&&textureHasSoftAlpha(painted);
    m.transparent=lace;
    m.opacity=1;
    m.depthWrite=!lace;
    m.alphaTest=0;
    if(m.map){
     m.map.colorSpace=T.SRGBColorSpace;
     m.map.anisotropy=Math.max(m.map.anisotropy??1,4);
     resetMapUv(m.map);
    }
   }else{
    m.transparent=false;
    m.opacity=1;
    m.depthWrite=true;
   }
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

const writeUvTriangleCloth=(data:ImageData,width:number,height:number,flipY:boolean,u0:number,v0:number,wa:T.Vector3,u1:number,v1:number,wb:T.Vector3,u2:number,v2:number,wc:T.Vector3,pred:(x:number,y:number,z:number)=>boolean,rgb:Rgb,minBlend=0)=>{
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
  let blend=Math.min(1,Math.max(smooth(0.34,0.66,w0),Math.max(smooth(0.34,0.66,w1),smooth(0.34,0.66,w2))));
  if(minBlend>0)blend=Math.max(blend,minBlend);
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
   const fill=kind==='sport'?.94:0;
   writeUvTriangleCloth(data,canvas.width,canvas.height,flipY,u0,v0,a,u1,v1,b,u2,v2,c,topPred,rgb,fill);
   writeUvTriangleCloth(data,canvas.width,canvas.height,flipY,u0,v0,a,u1,v1,b,u2,v2,c,shortsPred,rgb,fill);
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

/** Drop lace garment layers for opaque sport practice (adult only). */
export const hideAdultSportGarments=(root:T.Object3D)=>{
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  const label=`${mats.map(m=>`${m.name||''}`).join(' ')} ${obj.name||''}`;
  if(/Body_00_SKIN|Face_00/i.test(label))return;
  if(!/Tops|Bottoms|Shoes|Accessory|Skirt|Dress|OnePiece|Lace/i.test(label))return;
  obj.visible=false;
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   m.visible=false;
   m.transparent=false;
   m.opacity=1;
   m.depthWrite=true;
   m.alphaTest=0;
   m.needsUpdate=true;
  }
 });
};

/** Hide lace bottoms so sheer uses fine net tops only (not sport lace circles). */
export const hideAdultSheerLaceGarments=(root:T.Object3D)=>{
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  const label=`${mats.map(m=>`${m.name||''}`).join(' ')} ${obj.name||''}`;
  if(/Body_00_SKIN|Face_00/i.test(label))return;
  if(/Bottoms|Lace|Skirt|Dress|OnePiece/i.test(label)){
   obj.visible=false;
   for(const mat of mats){
    const m=mat as T.MeshStandardMaterial;
    m.visible=false;
    m.needsUpdate=true;
   }
  }
 });
};

const makeAdultSheerNetMap=(src:T.Texture):T.Texture|null=>{
 const img=src.image as CanvasImageSource & {width:number;height:number}|null;
 if(!img?.width||!img.height)return null;
 const w=Math.min(512,img.width),h=Math.min(512,img.height);
 const canvas=document.createElement('canvas');
 canvas.width=w;canvas.height=h;
 const ctx=canvas.getContext('2d');
 if(!ctx)return null;
 ctx.clearRect(0,0,w,h);
 ctx.fillStyle='rgba(248,246,242,0.12)';
 ctx.fillRect(0,0,w,h);
 ctx.strokeStyle='rgba(255,255,255,0.42)';
 ctx.lineWidth=1;
 const step=14;
 for(let x=0;x<=w;x+=step){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke();}
 for(let y=0;y<=h;y+=step){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke();}
 const next=src.clone();
 next.image=canvas;
 next.colorSpace=T.SRGBColorSpace;
 next.wrapS=T.RepeatWrapping;
 next.wrapT=T.RepeatWrapping;
 resetMapUv(next);
 next.repeat.set(3.4,3.4);
 next.needsUpdate=true;
 return next;
};

export const applyAdultSheerOuter=(root:T.Object3D)=>{
 hideAdultSheerLaceGarments(root);
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as T.MeshStandardMaterial;
   const label=`${m.name||''} ${obj.name||''}`;
   if(!/Tops/i.test(label)||/Outline/i.test(label))continue;
   m.visible=true;
   m.transparent=true;
   m.opacity=0.32;
   m.depthWrite=false;
   m.alphaTest=0.06;
   m.side=T.DoubleSide;
   const map=findGarmentMap(m);
   const net=map?makeAdultSheerNetMap(map):null;
   if(net)setGarmentMap(m,net);
   obj.renderOrder=2;
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
   obj.renderOrder=0;
   const base=baseline.get(m);
   if(base)setGarmentMap(m,base);
  }
 });
};