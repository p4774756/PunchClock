/**
 * Round-4: 找穴位標記可見／螢幕框、多關鏡頭、__mqDebug 點擊。
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
};

async function skipToLocate(page){
 for(let guard=0;guard<14;guard++){
  const prompt=await page.locator('.mq-hud-q').innerText().catch(()=>'');
  if(prompt.includes('請點出'))return prompt;
  const next=page.locator('.mq-card .mq-primary');
  if(await next.count()){await next.first().click();await page.waitForTimeout(350);}
  else{
   const choice=page.locator('.mq-choice').first();
   if(await choice.count()){await choice.click();await page.waitForTimeout(200);await next.first().click().catch(()=>{});await page.waitForTimeout(350);}
   else break;
  }
 }
 return null;
}

async function assertLocateLayout(page,label){
 await page.waitForFunction(()=>typeof window.__mqDebug==='function',{timeout:90_000});
 await page.waitForFunction(()=>{
  const d=window.__mqDebug?.();
  return d&&d.markers.some(m=>m.inFrustum);
 },{timeout:120_000});
 await page.waitForTimeout(3200);
 const prompt=await page.locator('.mq-hud-q').innerText();
 const m=prompt.match(/「([^」]+)」/);
 assert.ok(m,`${label} 找不到穴位名`);
 const code=nameToCode.get(m[1]);
 assert.ok(code,`${label} 未知穴名 ${m[1]}`);
 const layout=await page.evaluate(({code})=>{
  const dbg=window.__mqDebug?.();
  if(!dbg)return {error:'no dbg'};
  const margin=8;
  const {markers,hudBottom,hintTop}=dbg;
  const answer=markers.find(x=>x.code===code);
  const inBounds=(x,y)=>x>=margin&&y>=hudBottom+2&&y<=hintTop-2&&x<=window.innerWidth-margin;
  const answerMarkers=markers.filter(x=>x.code===code&&x.inFrustum);
  const good=answerMarkers.filter(x=>inBounds(x.screenX,x.screenY));
  return {
   answer,
   good,
   count:markers.length,
   hudBottom,
   hintTop,
   answerY:answer?.screenY,
  };
 },{code});
 assert.ok(layout.answer?.inFrustum,`${label} 答案標記 ${code} 不可見`);
 assert.ok(layout.good?.length,`${label} 答案標記超出可用區 y=${layout.answerY} hud=${layout.hudBottom} hint=${layout.hintTop}`);
 const pick=layout.good?.[0]??layout.answer;
 const canvas=page.locator('.scene-stage canvas');
 const box=await canvas.boundingBox();
 assert.ok(box,`${label} canvas 缺失`);
 const clickX=pick.screenX;
 const clickY=pick.screenY;
 await page.mouse.click(clickX,clickY);
 await page.waitForSelector('.mq-card',{timeout:15_000});
 const card=await page.locator('.mq-card h3').innerText();
 assert.ok(card.includes(m[1]),`${label} 點擊未判定正確`);
}

async function runStage(page,nodeIdx,label){
 await page.waitForSelector('.mq-map-grid',{timeout:90_000});
 await page.click(`.mq-node[data-idx="${nodeIdx}"]`);
 await page.click('.mq-primary:not(:disabled)');
 await page.waitForSelector('.mq-hud-panel',{timeout:60_000});
 const prompt=await skipToLocate(page);
 assert.ok(prompt,`${label} 無找穴位題`);
 await assertLocateLayout(page,label);
}

try{
 const browser=await chromium.launch({headless:true});

 const mobileCtx=await browser.newContext({...devices['iPhone 12'],viewport:{width:390,height:844},deviceScaleFactor:2});
 await mobileCtx.addInitScript(PROGRESS_INIT);
 const mobile=await mobileCtx.newPage();
 await mobile.goto(`${base}?mode=quest`,{waitUntil:'networkidle',timeout:120_000});
 await runStage(mobile,2,'mobile-ST');
 await mobileCtx.close();

 const deskCtx=await browser.newContext({viewport:{width:1280,height:720}});
 await deskCtx.addInitScript(PROGRESS_INIT);
 let desk=await deskCtx.newPage();
 await desk.goto(`${base}?mode=quest`,{waitUntil:'networkidle',timeout:120_000});
 await runStage(desk,2,'desktop-ST');
 await desk.close();
 desk=await deskCtx.newPage();
 await desk.goto(`${base}?mode=quest`,{waitUntil:'networkidle',timeout:120_000});
 await runStage(desk,6,'desktop-BL');
 await desk.close();
 desk=await deskCtx.newPage();
 await desk.goto(`${base}?mode=quest`,{waitUntil:'networkidle',timeout:120_000});
 await runStage(desk,1,'desktop-LI');
 await deskCtx.close();
 await browser.close();
 console.log('Playwright meridian-quest r4 OK');
}finally{
 server.close();
}