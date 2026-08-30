const http = require('http');

const PORT = process.env.PORT || 3000;
const SECRET = process.env.HEARTBEAT_SECRET || 'clickclick-dev-secret';
const BASE = `http://127.0.0.1:${PORT}`;

function postHeartbeat(token) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ clientId: 'test-worker', status: 'ONLINE', tasks: [] });
    const req = http.request(`${BASE}/api/heartbeat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        ...(token != null ? { Authorization: `Bearer ${token}` } : {})
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => resolve({ status: res.statusCode, body: JSON.parse(data) }));
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

(async () => {
  console.log('🔍 [心跳 API 測試] 驗證 Bearer token 驗證...');

  const noToken = await postHeartbeat(null);
  if (noToken.status !== 401) {
    console.error('❌ 預期無 token 回傳 401，實際:', noToken.status);
    process.exit(1);
  }
  console.log('✅ 無 token 正確回傳 401');

  const badToken = await postHeartbeat('wrong-secret');
  if (badToken.status !== 401) {
    console.error('❌ 預期錯誤 token 回傳 401，實際:', badToken.status);
    process.exit(1);
  }
  console.log('✅ 錯誤 token 正確回傳 401');

  const ok = await postHeartbeat(SECRET);
  if (ok.status !== 200 || !ok.body.success) {
    console.error('❌ 預期正確 token 回傳 200，實際:', ok.status, ok.body);
    process.exit(1);
  }
  console.log('✅ 正確 token 回傳 200');

  console.log('🎉 心跳 API 驗證測試全部通過！');
})().catch(err => {
  console.error('❌ 測試失敗（請確認 server 已啟動）:', err.message);
  process.exit(1);
});
