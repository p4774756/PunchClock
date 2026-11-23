import * as T from 'three';

type MToonTexMaterial=T.MeshStandardMaterial & {isMToonMaterial?:boolean;uniforms?:Record<string,{value:unknown}>};

const tuneMap=(tex:T.Texture,aniso:number)=>{
 tex.colorSpace=T.SRGBColorSpace;
 if(tex.image){
  tex.generateMipmaps=true;
  tex.minFilter=T.LinearMipmapLinearFilter;
  tex.magFilter=T.LinearFilter;
  tex.anisotropy=Math.max(tex.anisotropy,aniso);
 }
 tex.needsUpdate=true;
};

/** sRGB、mipmap、anisotropy for character MToon + cloth maps (adult grooming). */
export const tuneCharacterTextures=(root:T.Object3D,maxAnisotropy=8)=>{
 const aniso=Math.min(maxAnisotropy,8);
 root.traverse(obj=>{
  if(!(obj instanceof T.Mesh)&&!(obj instanceof T.SkinnedMesh))return;
  const mats=Array.isArray(obj.material)?obj.material:[obj.material];
  for(const mat of mats){
   const m=mat as MToonTexMaterial;
   if(m.map)tuneMap(m.map,aniso);
   if(m.normalMap)tuneMap(m.normalMap,aniso);
   if(m.emissiveMap)tuneMap(m.emissiveMap,aniso);
   const uni=m.uniforms;
   if(uni)for(const key of Object.keys(uni)){
    const val=uni[key]?.value;
    if(val&&typeof val==='object'&&'isTexture' in (val as object))tuneMap(val as T.Texture,aniso);
   }
   if(m.isMToonMaterial&&m.map)m.map.colorSpace=T.SRGBColorSpace;
   m.needsUpdate=true;
  }
 });
};