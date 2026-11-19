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

const bundlePath=join(temporaryDir,'entry.mjs');
const bundle=await build({
 stdin:{
  contents:`export {QUEST_POINTS,questAvatarLocateCodes,questBankByMeridian,isIndicationEligible,questPointName} from './app/quest/quest-data';
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
 for(const row of M.QUEST_POINTS){
  const tpRow=tpBy.get(row.code);
  assert.ok(tpRow,`${row.code} 不存在於 teaching-points.json`);
  assert.equal(row.meridianId,tpRow.meridianId);
  assert.ok(row.name===tpRow.name||row.nameVariant===tpRow.name,`${row.code} 名稱不一致`);
  assert.ok(row.indications.length>=2&&row.indications.length<=6);
  assert.ok(row.location?.trim());
  const num=Number(row.code.replace(/^[A-Z]+/,'').replace('+',''));
  if(num)assert.equal(row.order,num);
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

await test('題目產生器 12 關 10 題',async()=>{
 for(const id of M.QUEST_STAGE_ORDER){
  const plan=M.generateStage(id,{seed:42});
  assert.equal(plan.questions.length,10,`${id} 題數`);
  const again=M.generateStage(id,{seed:42});
  assert.deepEqual(plan.questions,again.questions);
  for(const q of plan.questions){
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
   assert.equal(new Set(q.kind==='locate'?[q.answerCode]:q.choices).size,q.kind==='locate'?1:4);
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

console.log(`\n驗證完成：${passed} 通過，${failed} 失敗`);
process.exitCode=failed?1:0;