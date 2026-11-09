/** Compresses existing uncompressed .bin chunks. Run `npm run inflate-models` first if bins are missing. */
import fs from 'node:fs';
import {gzipSync} from 'node:zlib';
const base=new URL('../public/models/',import.meta.url);
for(const name of fs.readdirSync(base).filter(n=>n==='atlas.json')){
 const path=new URL(name,base),atlas=JSON.parse(fs.readFileSync(path));
 let bytes=0;
 for(const c of atlas.chunks){
  const bin=new URL(c.url.split('/').pop(),base);
  if(!fs.existsSync(bin))throw new Error(`缺少 ${c.url}；先跑 npm run inflate-models`);
  const compressed=gzipSync(fs.readFileSync(bin),{level:9});c.gzip=c.url+'.gz';c.gzipBytes=compressed.length;fs.writeFileSync(new URL(c.gzip.split('/').pop(),base),compressed);bytes+=compressed.length;
 }
 fs.writeFileSync(path,JSON.stringify(atlas));console.log(`${name}: ${(bytes/1e6).toFixed(1)} MB compressed download`);
}
