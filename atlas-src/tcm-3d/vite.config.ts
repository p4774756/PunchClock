import {fileURLToPath} from 'node:url';
import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
const path=(relative:string)=>fileURLToPath(new URL(relative,import.meta.url));
export default defineConfig({
 root:path('./web'),
 publicDir:path('./public'),
 plugins:[react()],
 resolve:{alias:{'@':path('./')}},
 server:{watch:{usePolling:true}},
 build:{
  outDir:path('./dist'),
  emptyOutDir:true,
  chunkSizeWarningLimit:900,
  rolldownOptions:{
   output:{
    manualChunks(id:string){
     if(id.includes('node_modules/three'))return 'three';
     if(id.includes('teaching-points.json')||id.includes('meridian-surface.json'))return 'teaching-data';
     if(id.includes('node_modules/lucide-react'))return 'icons';
    },
   },
  },
 },
});
