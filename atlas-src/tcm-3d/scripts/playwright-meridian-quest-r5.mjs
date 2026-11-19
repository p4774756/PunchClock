/**
 * Round-5: 找穴位群組框選、遮擋、可用區、題序偏移、多關全測。
 * 不 commit 截圖。
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

function stddevPx(vals){
 if(vals.length<2)return 0;
 const mean=vals.reduce((a,b)=>a+b,0)/vals.length;
 return Math.sqrt(vals.reduce((s,v)=>s+(v-mean)**2,0)/vals.length);
}

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

async function assertLocateQuestion(page,label,code,positions){
 await page.waitForFunction(()=>typeof window.__mqDebug==='function',{timeout:90_000});
 await page.waitForFunction(()=>{
  const d=window.__mqDebug?.();
  return d&&d.markers.some(m=>m.inFrustum);
 },{timeout:120_000});
 await page.waitForTimeout(3200);
 await page.evaluate(()=>{for(let i=0;i<3;i++)window.__mqRefineLocate?.();});
 await page.waitForTimeout(180);
 const layout=await page.evaluate(({code})=>{
  const dbg=window.__mqDebug?.();
  if(!dbg)return {error:'no dbg'};
  const margin=8;
  const {markers,hudBottom,hintTop,usableMid,frameCenter}=dbg;
  const inBounds=(x,y)=>x>=margin&&y>=hudBottom+2&&y<=hintTop-2&&x<=window.innerWidth-margin;
  const answer=markers.find(x=>x.code===code);
  const visible=markers.filter(m=>m.inFrustum);
  const groupVisible=visible.length;
  const usableSpan=hintTop-hudBottom;
  const frameTol=window.innerWidth<720?0.15:0.28;
  const frameOk=frameCenter?Math.abs(frameCenter.screenY-usableMid)<=usableSpan*frameTol:true;
  return {
   answer,
   answerOccluded:answer?.occluded,
   visibleCount:groupVisible,
   inBoundsVisible:visible.filter(m=>inBounds(m.screenX,m.screenY)).length,
   answerInBounds:answer?inBounds(answer.screenX,answer.screenY):false,
   frameOk,
   frameY:(()=>{const dbg=window.__mqDebug?.();return dbg?.frameCenter?.screenY})(),
   hudBottom,
   hintTop,
   usableMid,
   answerY:answer?.screenY,
  };
 },{code});
 assert.equal(layout.answerOccluded,false,`${label} 答案 ${code} 被遮擋`);
 assert.ok(layout.answer?.inFrustum,`${label} 答案標記不可見`);
 assert.ok(layout.answerInBounds,`${label} 答案超出可用區 y=${layout.answerY} hud=${layout.hudBottom} hint=${layout.hintTop}`);
 const frameDev=layout.frameY!=null?Math.abs(layout.frameY-layout.usableMid):NaN;
 assert.ok(layout.frameOk,`${label} 群組中心偏離可用區中心 dev=${frameDev?.toFixed(0)} frameY=${layout.frameY} mid=${layout.usableMid}`);
 if(layout.visibleCount>=2){
  assert.ok(layout.inBoundsVisible>=2,`${label} 同群組可見標記應 ≥2`);
 }
 positions.push(layout.answerY??0);
 const pick=layout.answer;
 assert.ok(pick,`${label} 找不到答案標記`);
 await page.mouse.click(pick.screenX,pick.screenY);
 await page.waitForSelector('.mq-card',{timeout:15_000});
 await page.locator('.mq-card .mq-primary').click();
 await page.waitForTimeout(350);
}

async function runStageLocates(page,stage,profile){
 await page.goto(`${base}?mode=quest`,{waitUntil:'networkidle',timeout:120_000});
 await page.waitForSelector('.mq-map-grid',{timeout:90_000});
 await page.click(`.mq-node[data-idx="${stage.idx}"]`);
 await page.click('.mq-primary:not(:disabled)');
 await page.waitForSelector('.mq-hud-panel',{timeout:60_000});
 const positions=[];
 for(let guard=0;guard<14;guard++){
  const prompt=await page.locator('.mq-hud-q').innerText().catch(()=>'');
  if(!prompt||prompt.includes('第')&&prompt.includes('關')&&!prompt.includes('請點出')&&!prompt.match(/「/))break;
  if(prompt.includes('請點出')){
   const m=prompt.match(/「([^」]+)」/);
   assert.ok(m,`${profile}-${stage.label} 找不到穴名`);
   const code=nameToCode.get(m[1]);
   assert.ok(code,`未知穴名 ${m[1]}`);
   await assertLocateQuestion(page,`${profile}-${stage.label}`,code,positions);
   continue;
  }
  const progressed=await dismissNonLocate(page);
  if(!progressed)break;
 }
 if(positions.length>=2){
  const sd=stddevPx(positions);
  assert.ok(sd>20,`${profile}-${stage.label} 答案 Y 標準差過小 sd=${sd.toFixed(1)} ys=${positions.join(',')}`);
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
 console.log('Playwright meridian-quest r5 OK');
}finally{
 server.close();
}