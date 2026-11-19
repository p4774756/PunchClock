import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,mkdtemp} from 'node:fs/promises';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {build} from 'esbuild';

const project=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const validationDir=join(project,'.validation');
await mkdir(validationDir,{recursive:true});
const temporaryDir=await mkdtemp(join(validationDir,'meridian-quest-'));
let passed=0,failed=0;
const test=async(name,run)=>{try{await run();passed++;console.log(`通過：${name}`);}catch(e){failed++;console.error(`失敗：${name}\n${e.message}`);}};

const tp=JSON.parse(await readFile(join(project,'app/teaching-points.json'),'utf8'));
const ap=JSON.parse(await readFile(join(project,'app/anime-points.json'),'utf8'));
const avatar=new Set(ap.points.map(p=>p.code));
const tpBy=new Map(tp.map(p=>[p.code,p]));

const YUAN=new Map([
 ['LU9','太淵'],['LI4','合谷'],['SP3','太白'],['HT7','神門'],['SI4','腕骨'],['KI3','太溪'],
 ['PC7','大陵'],['TE4','陽池'],['GB40','丘墟'],['LR3','太衝'],['ST42','衝陽'],['BL64','京骨'],
]);
const LUO=new Map([
 ['LU7','列缺'],['LI6','偏歷'],['ST40','豐隆'],['SP4','公孫'],['HT5','通里'],['SI7','後郄'],
 ['KI4','大鐘'],['PC6','內關'],['TE5','外關'],['GB37','光明'],['LR5','蠡溝'],['BL58','飛陽'],
]);
const BAMAI=new Map([
 ['LU7','任脈'],['SP4','衝脈'],['PC6','陰維脈'],['SI3','督脈'],['BL62','陽蹻脈'],['KI6','陰蹻脈'],['TE5','陽維脈'],['GB41','帶脈'],
]);
const BACK_SHU=[
 ['BL13','肺','第 3 胸椎'],['BL14','心包','第 4 胸椎'],['BL15','心','第 5 胸椎'],
 ['BL18','肝','第 9 胸椎'],['BL20','脾','第 11 胸椎'],['BL21','胃','第 12 胸椎'],['BL23','腎','第 2 腰椎'],['BL25','大腸','第 4 腰椎'],
];

const bundlePath=join(temporaryDir,'entry.mjs');
const bundle=await build({
 stdin:{
  contents:`export {QUEST_POINTS,QUEST_BY_CODE,questAvatarLocateCodes,questBankByMeridian,isIndicationEligible,questPointName} from './app/quest/quest-data';
export {generateStage} from './app/quest/quest-generator';
export {starsFromRun,scoreForAnswer,comboBonus} from './app/quest/quest-score';
export {mergeProgress,sanitizeProgress,stageUnlocked,emptyProgress} from './app/quest/quest-progress';
export {QUEST_STAGE_ORDER} from './app/quest/quest-stages';`,
  resolveDir:project,sourcefile:'validate-meridian-quest-entry.ts',loader:'ts',
 },
 bundle:true,platform:'node',format:'esm',target:'node22',write:false,logLevel:'silent',
});
await writeFile(bundlePath,bundle.outputFiles[0].contents);
const M=await import(pathToFileURL(bundlePath));

await test('題庫與 teaching-points 一致',async()=>{
 assert.equal(M.QUEST_POINTS.length,159);
 for(const row of M.QUEST_POINTS){
  const tpRow=tpBy.get(row.code);
  assert.ok(tpRow,`${row.code} 不存在於 teaching-points.json`);
  assert.equal(row.meridianId,tpRow.meridianId);
  assert.ok(row.name===tpRow.name||row.nameVariant===tpRow.name,`${row.code} 名稱不一致`);
  assert.ok(row.indications.length>=2&&row.indications.length<=6);
  assert.ok(row.location?.trim());
  assert.ok(!row.location.includes('待補'),`${row.code} location 待補`);
  const num=Number(row.code.replace(/^[A-Z]+/,'').replace('+',''));
  if(num)assert.equal(row.order,num);
 }
});

await test('同一經 location 不重複',async()=>{
 const bank=M.questBankByMeridian();
 for(const id of M.QUEST_STAGE_ORDER){
  const seen=new Set();
  for(const p of bank.get(id)??[]){
   const loc=p.location.trim();
   assert.ok(!seen.has(loc),`${id} ${p.code} location 重複`);
   seen.add(loc);
  }
 }
});

await test('特定穴 category 對照',async()=>{
 const byCode=new Map(M.QUEST_POINTS.map(p=>[p.code,p]));
 const assertCat=(map,label,re)=>{
  for(const [code,name] of map){
   const row=byCode.get(code);
   if(!row)continue;
   assert.ok(row.category,`${code} ${name} 缺少 category`);
   assert.match(row.category,new RegExp(re),`${code} ${name} category 不符`);
  }
 };
 assertCat(YUAN,'原穴','原穴');
 assertCat(LUO,'絡穴','絡穴');
 for(const [code,mai] of BAMAI){
  const row=byCode.get(code);
  if(!row)continue;
  assert.ok(row.category.includes('八脈交會'),`${code} 應標八脈交會`);
  assert.ok(row.category.includes(mai),`${code} 應通${mai}`);
 }
 for(const [code,,vert] of BACK_SHU){
  const row=byCode.get(code);
  if(!row)continue;
  assert.ok(row.location.includes(vert),`${code} 背俞定位應含${vert}`);
  assert.ok(row.category?.includes('背俞')||row.category?.includes('俞穴'),`${code} 應為背俞穴`);
 }
});

await test('每經至少 10 穴或全經',async()=>{
 const bank=M.questBankByMeridian();
 const full=new Set(['LU','HT','PC','LR']);
 for(const id of M.QUEST_STAGE_ORDER){
  const list=bank.get(id)??[];
  if(full.has(id))assert.equal(list.length,tp.filter(p=>p.meridianId===id).length,`${id} 應全經收錄`);
  else assert.ok(list.length>=10,`${id} 僅 ${list.length} 穴`);
 }
});

function assertStagePlan(id,plan){
 assert.equal(plan.questions.length,10,`${id} 題數`);
 const bankSize=(M.questBankByMeridian().get(id)??[]).length;
 const maxDup=bankSize<10?2:1;
 const counts=new Map();
 const kinds=new Map();
 let lastCode=null;
 const choiceSlots=[];
 for(const q of plan.questions){
  const c=counts.get(q.answerCode)??0;
  assert.ok(c+1<=maxDup,`${id} seed ${plan.seed} ${q.answerCode} 重複超過 ${maxDup}`);
  counts.set(q.answerCode,c+1);
  const kset=kinds.get(q.answerCode)??new Set();
  if(c>=1)assert.ok(!kset.has(q.kind),`${id} 重複穴相鄰同型`);
  if(c>=1&&maxDup>1)assert.notEqual(q.answerCode,lastCode,`${id} 重複穴相鄰`);
  kset.add(q.kind);
  kinds.set(q.answerCode,kset);
  lastCode=q.answerCode;
  if(q.kind==='locate')assert.ok(avatar.has(q.answerCode));
  if(q.kind==='indication'){
   const symptom=q.symptom;
   assert.ok(symptom);
   for(const code of q.choices){
    if(code===q.answerCode)continue;
    const row=M.QUEST_POINTS.find(p=>p.code===code);
    assert.ok(row);
    assert.ok(!row.indications.includes(symptom),`干擾項 ${code} 含症狀 ${symptom}`);
   }
  }
  if(q.kind==='location'){
   const ans=M.QUEST_BY_CODE.get(q.answerCode);
   for(const code of q.choices){
    if(code===q.answerCode)continue;
    const row=M.QUEST_POINTS.find(p=>p.code===code);
    assert.ok(row);
    assert.notEqual(row.location.trim(),ans.location.trim(),'定位干擾項 location 相同');
   }
  }
  assert.equal(new Set(q.kind==='locate'?[q.answerCode]:q.choices).size,q.kind==='locate'?1:4);
  if(q.kind!=='locate'){
   const idx=q.choices.indexOf(q.answerCode);
   assert.ok(idx>=0);
   choiceSlots.push(idx);
  }
 }
 if(choiceSlots.length){
  const freq=[0,0,0,0];
  for(const i of choiceSlots)freq[i]++;
  const min=Math.min(...freq),max=Math.max(...freq);
  assert.ok(max-min<=1,`${id} 正解位置分布差 >1 (${freq})`);
 }
}

await test('題目產生器 12 關 10 題',async()=>{
 for(const id of M.QUEST_STAGE_ORDER){
  const plan=M.generateStage(id,{seed:42});
  assertStagePlan(id,plan);
  const again=M.generateStage(id,{seed:42});
  assert.deepEqual(plan.questions,again.questions);
 }
});

await test('題目產生器 300 seeds',async()=>{
 for(const id of M.QUEST_STAGE_ORDER){
  for(let seed=0;seed<300;seed++){
   assertStagePlan(id,M.generateStage(id,{seed}));
  }
 }
});

await test('肺心肝經找穴 0 題',async()=>{
 for(const id of ['LU','HT','LR']){
  const plan=M.generateStage(id,{seed:7,avatarCodes:[]});
  assert.equal(plan.locateCount,0);
  assert.ok(!plan.questions.some(q=>q.kind==='locate'));
 }
});

await test('計分星星門檻',async()=>{
 assert.equal(M.starsFromRun(9,10,119000),3);
 assert.equal(M.starsFromRun(9,10,121000),2);
 assert.equal(M.starsFromRun(8,10,10000),2);
 assert.equal(M.starsFromRun(6,10,10000),1);
 assert.equal(M.starsFromRun(5,10,10000),0);
 assert.equal(M.scoreForAnswer(true,3,false),100+M.comboBonus(3));
 assert.equal(M.scoreForAnswer(true,3,true),50);
 assert.equal(M.comboBonus(6),100);
});

await test('進度 merge 不倒退',async()=>{
 const a=M.emptyProgress();
 a.stages.LU={stars:2,bestTimeMs:90000,bestScore:800,clearedAt:'2020-01-01'};
 const b=M.emptyProgress();
 b.stages.LU={stars:1,bestTimeMs:120000,bestScore:1200,clearedAt:null};
 const m=M.mergeProgress(a,b);
 assert.equal(m.stages.LU.stars,2);
 assert.equal(m.stages.LU.bestScore,1200);
 assert.equal(m.stages.LU.bestTimeMs,90000);
 assert.ok(M.stageUnlocked(m,'LI'));
});

await test('關卡中 HUD 不遮蔽全螢幕',async()=>{
 const css=await readFile(join(project,'app/quest/quest.css'),'utf8');
 assert.ok(css.includes('.mq-root-play'),'缺少 play 透明根');
 assert.ok(css.includes('pointer-events:none'),'缺少 HUD 穿透');
 assert.ok(css.includes('.mq-play-layer'),'缺少 play 圖層');
});

console.log(`\n驗證完成：${passed} 通過，${failed} 失敗`);
process.exitCode=failed?1:0;