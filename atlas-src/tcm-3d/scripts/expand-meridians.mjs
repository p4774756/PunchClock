import fs from 'node:fs';
import M from '../data/legacy-meridians.mjs';
const file=new URL('../app/teaching-points.json',import.meta.url);
const base=JSON.parse(fs.readFileSync(file)).filter(p=>!p.generated);
const raw=new Map(Object.entries(M).flatMap(([k,m])=>m.path.map((v,i)=>[k+(i+1),v])));
const region=([x,y,z])=>y>1.55?'head':x>.17&&y<1.42?'arm':y>1.42?'shoulder':y>.85?(z<-.025?'waist':'abdomen'):'leg';
const points=[];
for(const [key,m] of Object.entries(M))for(let i=0;i<m.pts.length;i++){
 const code=key+(i+1),old=base.find(p=>p.code===code);if(old){points.push({...old,meridianId:key});continue;}
 const seed=m.path[i],r=region(seed);
 const refs=base.filter(p=>raw.has(p.code)&&region(raw.get(p.code))===r).map(p=>({p,v:raw.get(p.code),d:Math.hypot(...seed.map((x,j)=>x-raw.get(p.code)[j]))})).sort((a,b)=>a.d-b.d).slice(0,4);
 const weights=refs.map(a=>1/(a.d*a.d+.0002)),sum=weights.reduce((a,b)=>a+b,0);
 const position=seed.map((v,j)=>v*.96+refs.reduce((s,a,k)=>s+weights[k]*(a.p.position[j]-a.v[j]*.96),0)/(sum||1));if(m.side===1)position[0]=0;
 let normal=[0,0,seed[2]<-.015?-1:1];if(r==='head')normal=[seed[0]*7,(seed[1]-1.67)*10,seed[2]*10];if(seed[1]<.12)normal=[0,1,.3];
 points.push({code,name:m.pts[i][1],pinyin:m.pts[i][0],meridian:m.name.match(/[\u4e00-\u9fff].*$/)[0],meridianId:key,position,normal,region:r,midline:m.side===1,reviewed:false,generated:true,source:'https://www.ntcamsac.ac.cn/cms/content?id=353',positionProvenance:'舊銅人逐穴種子，經同區域參考點加權姿態適配；運行時射線吸附皮膚。未經逐穴專業審核。',originalPosition:seed,...(code==='GV28'?{surfaceUnavailable:true,uncertainty:'齦交位於口內，當前體表模式不放置標記、不演示器具。'}:{})});
}
points.splice(points.findIndex(p=>p.code==='GV25'),0,{code:'GV24+',name:'印堂',pinyin:'Yintang',meridian:'督脈',meridianId:'GV',position:[0,1.615,.075],normal:[0,0,1],region:'head',midline:true,reviewed:false,generated:true,source:'https://www.ntcamsac.ac.cn/cms/content?id=353',positionProvenance:'GB/T 12346—2021 編號；眉間工程種子，未專業審校。'});
fs.writeFileSync(file,JSON.stringify(points,null,2)+'\n');
fs.writeFileSync(new URL('../app/meridians.json',import.meta.url),JSON.stringify(Object.entries(M).map(([id,m])=>({id,name:m.name.match(/[\u4e00-\u9fff].*$/)[0],color:'#'+m.color.toString(16).padStart(6,'0'),count:points.filter(p=>p.meridianId===id).length})),null,2)+'\n');
console.log(points.length+' point names; 14 meridians');
