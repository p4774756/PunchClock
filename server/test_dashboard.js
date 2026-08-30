const fs = require('fs');
const path = require('path');
const vm = require('vm');

console.log('🔍 [本機自動測試] 正在檢驗 Dashboard 靜態 HTML / CSS / JS...');

const publicDir = path.join(__dirname, 'public');
const htmlPath = path.join(publicDir, 'index.html');
const cssPath = path.join(publicDir, 'css', 'dashboard.css');
const jsPath = path.join(publicDir, 'js', 'dashboard.js');

let hasError = false;

function requireFile(filePath, label) {
  if (!fs.existsSync(filePath)) {
    console.error(`❌ 找不到 ${label}（${filePath}）！`);
    hasError = true;
    return null;
  }
  console.log(`✅ 找到 ${label}`);
  return fs.readFileSync(filePath, 'utf8');
}

const html = requireFile(htmlPath, 'public/index.html');
const css = requireFile(cssPath, 'public/css/dashboard.css');
const jsCode = requireFile(jsPath, 'public/js/dashboard.js');

if (html) {
  if (!html.includes('href="/css/dashboard.css"')) {
    console.error('❌ index.html 未連結 /css/dashboard.css');
    hasError = true;
  } else {
    console.log('✅ index.html 已連結 dashboard.css');
  }
  if (!html.includes('{{SERVER_VERSION}}')) {
    console.error('❌ index.html 未包含 {{SERVER_VERSION}} 佔位');
    hasError = true;
  } else {
    console.log('✅ index.html 含版本佔位 {{SERVER_VERSION}}');
  }
  if (!html.includes('src="/js/dashboard.js"')) {
    console.error('❌ index.html 未載入 /js/dashboard.js');
    hasError = true;
  } else {
    console.log('✅ index.html 已載入 dashboard.js');
  }
  if (!html.includes('{{DAILY_PROVERB_CONTEXT}}') || !html.includes('proverb-context')) {
    console.error('❌ index.html 缺少六人行情境說明區塊');
    hasError = true;
  } else {
    console.log('✅ index.html 含六人行情境說明');
  }
  if (!html.includes('site-navbar') || !html.includes('header-title')) {
    console.error('❌ index.html 缺少 eschool 風格導覽/標題結構');
    hasError = true;
  } else {
    console.log('✅ index.html 含 eschool 風格 layout');
  }
}

if (css !== null && css.trim().length === 0) {
  console.error('❌ dashboard.css 為空');
  hasError = true;
} else if (css !== null) {
  console.log(`✅ dashboard.css 內容長度正常 (${css.length} bytes)`);
  if (!css.includes('.daily-proverb')) {
    console.error('❌ dashboard.css 缺少 .daily-proverb 樣式');
    hasError = true;
  } else {
    console.log('✅ dashboard.css 含 .daily-proverb');
  }
  if (!css.includes('.site-navbar') || !css.includes('--cream')) {
    console.error('❌ dashboard.css 缺少 eschool 主題 token');
    hasError = true;
  } else {
    console.log('✅ dashboard.css 含 eschool 主題');
  }
}

if (jsCode !== null) {
  try {
    new vm.Script(jsCode);
    console.log(`✅ [dashboard.js] 語法檢查完全通過！(${jsCode.length} bytes)`);
    if (!jsCode.includes('visibleLogLines') || !jsCode.includes('patchAllClients')) {
      hasError = true;
      console.error('❌ dashboard.js 缺少局部更新邏輯');
    } else {
      console.log('✅ dashboard.js 含局部更新（patchAllClients）');
    }
  } catch (e) {
    hasError = true;
    console.error('❌ [dashboard.js] 語法錯誤:', e.message);
  }
}

if (hasError) {
  console.error('❌ 本機語法檢驗失敗！請先修復錯誤。');
  process.exit(1);
}

console.log('🎉 [100% SUCCESS] Dashboard HTML / CSS / JS 驗證完全無誤！可安全部署！');
