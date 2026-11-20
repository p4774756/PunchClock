/**
 * Round-6: 找穴遮擋（含頭髮）、像素可見、點頭髮不得中、全關 locate。
 */
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createReadStream,existsSync} from 'node:fs';

const mime=(file)=>{
 if(file.endsWith('.html'))return 'text/html';
 if(file.endsWith('.js'))return 'application/javascript';
 if(file.endsWith('.css'))return 'text/css';
 if(file.endsWith('.webp'))return 'image/webp';
 if(file.endsWith('.glb'))return 'model/gltf-binary';
 if(file.endsWith('.json'))return 'application/json';
 return 'application/octet-stream';
};

const root=join(dirname(fileURLToPath(import.meta.url)),'..');
const dist=join(root,'dist');
assert.ok(existsSync(dist),`缺少 build 產物：${dist}`);

const qp=JSON.parse(await readFile(join(root,'app/quest/quest-points.json'),'utf8'));
const nameToCode=new Map(qp.map(p=>[p.name,p.code]));

const server=createServer(async(req,res)=>{
 const url=new URL(req.url||'/',`http://${req.headers.host}`);
 let path=decodeURIComponent(url.pathname);
 if(path.startsWith('/atlas/tcm-3d'))path=path.slice('/atlas/tcm-3d'.length)||'/';
 const file=join(dist,path==='/'?'index.html':path.replace(/^\//,''));
 if(!existsSync(file)||file.includes('..')){res.statusCode=404;res.end('not found');return;}
 res.setHeader('Content-Type',mime(file));
 createReadStream(file).pipe(res);
});

await new Promise(r=>server.listen(0,r));
const port=server.address().port;
const base=`http://127.0.0.1:${port}/atlas/tcm-3d/`;

const {chromium,devices}=await import('playwright');

const PROGRESS_INIT=()=>{
 const p={version:1,stages:{}};
 for(const id of ['LU','LI','ST','SP','HT','SI','BL','KI','PC','TE','GB','LR']){
  p.stages[id]={stars:3,bestTimeMs:60000,bestScore:900,clearedAt:'2020-01-01'};
 }
 localStorage.setItem('punchclock.meridianQuest.v1',JSON.stringify(p));
 Math.random=()=>0.492401;
};

const LOCATE_STAGES=[
 {idx:1,id:'LI',label:'LI'},
 {idx:2,id:'ST',label:'ST'},
 {idx:3,id:'SP',label:'SP'},
 {idx:5,id:'SI',label:'SI'},
 {idx:6,id:'BL',label:'BL'},
 {idx:7,id:'KI',label:'KI'},
 {idx:8,id:'PC',label:'PC'},
 {idx:9,id:'TE',label:'TE'},
 {idx:10,id:'GB',label:'GB'},
];

const MARKER_FILL={r:61,g:255,b:90};
const markerPixelScore=(r,g,b)=>{
 const dr=Math.abs(r-MARKER_FILL.r),dg=Math.abs(g-MARKER_FILL.g),db=Math.abs(b-MARKER_FILL.b);
 const ring=Math.abs(r-22)+Math.abs(g-58)+Math.abs(b-112);
 return Math.min(dr+dg+db,ring);
};

async function dismissNonLocate(page){
 const prompt=await page.locator('.mq-hud-q').innerText().catch(()=>'');
 if(prompt.includes('請點出'))return false;
 const choice=page.locator('.mq-choice').first();
 if(await choice.count()){
  await choice.click();
  await page.waitForTimeout(180);
 }
 const next=page.locator('.mq-card .mq-primary');
 if(await next.count()){
  await next.first().click();
  await page.waitForTimeout(280);
 }
 return true;
}

async function assertLocateQuestion(page,label,code){
 await page.waitForFunction(()=>typeof window.__mqDebug==='function',{timeout:90_000});
 await page.waitForFunction(()=>{
  const d=window.__mqDebug?.();
  return d&&d.markers.some(m=>m.inFrustum);
 },{timeout:120_000});
 await page.waitForTimeout(3200);
 await page.evaluate(()=>{for(let i=0;i<4;i++)window.__mqRefineLocate?.();});
 await page.waitForTimeout(220);
 const layout=await page.evaluate(({code})=>{
  const dbg=window.__mqDebug?.();
  if(!dbg)return {error:'no dbg'};
  const margin=8;
  const {markers,hudBottom,hintTop}=dbg;
  const inBounds=(x,y)=>x>=margin&&y>=hudBottom+2&&y<=hintTop-2&&x<=window.innerWidth-margin;
  const answer=markers.find(x=>x.code===code);
  return {answer,answerOccluded:answer?.occluded,answerInBounds:answer?inBounds(answer.screenX,answer.screenY):false};
 },{code});
 assert.equal(layout.answerOccluded,false,`${label} 答案 ${code} 被遮擋`);
 assert.ok(layout.answer?.inFrustum,`${label} 答案標記不可見`);
 assert.ok(layout.answerInBounds,`${label} 答案超出可用區`);

 const pick=layout.answer;
 assert.ok(pick,`${label} 找不到答案標記`);

 const pixel=await page.evaluate(({code})=>window.__mqMarkerPixelBest?.(code),{code});
 assert.ok(pixel,`${label} 無法取樣標記像素`);
 assert.ok(pixel.hasGreen||pixel.best<95,`${label} ${code} 標記像素不明顯 rgb=${pixel.r},${pixel.g},${pixel.b} best=${pixel.best} maxG=${pixel.maxG} green=${pixel.hasGreen}`);

 const hairX=pick.screenX;
 const hairY=pick.screenY-30;
 await page.mouse.click(hairX,hairY);
 await page.waitForTimeout(350);
 const cardOpen=await page.locator('.mq-card').count();
 assert.equal(cardOpen,0,`${label} 點頭髮旁應未答對`);

 await page.mouse.click(pick.screenX,pick.screenY);
 await page.waitForSelector('.mq-card',{timeout:15_000});
 await page.locator('.mq-card .mq-primary').click();
 await page.waitForTimeout(350);
}

async function runStageLocates(page,stage,profile){
 await page.goto(`${base}?mode=quest`,{waitUntil:'domcontentloaded',timeout:120_000});
 await page.waitForSelector('.mq-map-grid',{timeout:90_000});
 await page.click(`.mq-node[data-idx="${stage.idx}"]`);
 await page.click('.mq-primary:not(:disabled)');
 await page.waitForSelector('.mq-hud-panel',{timeout:60_000});
 for(let guard=0;guard<14;guard++){
  const prompt=await page.locator('.mq-hud-q').innerText().catch(()=>'');
  if(!prompt||prompt.includes('第')&&prompt.includes('關')&&!prompt.includes('請點出')&&!prompt.match(/「/))break;
  if(prompt.includes('請點出')){
   const m=prompt.match(/「([^」]+)」/);
   assert.ok(m,`${profile}-${stage.label} 找不到穴名`);
   const code=nameToCode.get(m[1]);
   assert.ok(code,`未知穴名 ${m[1]}`);
   await assertLocateQuestion(page,`${profile}-${stage.label}`,code);
   continue;
  }
  const progressed=await dismissNonLocate(page);
  if(!progressed)break;
 }
}

try{
 const browser=await chromium.launch({headless:true});

 const mobileCtx=await browser.newContext({...devices['iPhone 12'],viewport:{width:390,height:844},deviceScaleFactor:2});
 await mobileCtx.addInitScript(PROGRESS_INIT);
 const mobile=await mobileCtx.newPage();
 for(const stage of LOCATE_STAGES){
  await runStageLocates(mobile,stage,'mobile');
 }
 await mobileCtx.close();

 const deskCtx=await browser.newContext({viewport:{width:1280,height:720}});
 await deskCtx.addInitScript(PROGRESS_INIT);
 for(const stage of LOCATE_STAGES){
  const desk=await deskCtx.newPage();
  await runStageLocates(desk,stage,'desktop');
  await desk.close();
 }
 await deskCtx.close();
 await browser.close();
 console.log('Playwright meridian-quest r6 OK');
}finally{
 server.close();
}