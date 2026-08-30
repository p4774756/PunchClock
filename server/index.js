const http = require('http');
const path = require('path');
const fs = require('fs');
const express = require('express');
const cookieParser = require('cookie-parser');
const { WebSocketServer, WebSocket } = require('ws');

const {
  AUTH_COOKIE_VALUE,
  isAuth,
  isAuthFromCookieHeader,
  isHeartbeatAuthorized,
  getLoginLockStatus,
  recordLoginFailure,
  clearLoginAttempt,
  checkAdminPassword,
  authCookieOptions,
  authCookieClearOptions
} = require('./lib/auth');
const {
  clients,
  getOrCreateClient,
  queueClientAction,
  queuePeerMessage,
  queuePeerPoke,
  peerSnapshot,
  drainPendingActions,
  appendClientEvent,
  logTaskTransitions,
  publicClientsSnapshot,
  deleteClient,
  setClient,
  startOfflineMonitor
} = require('./lib/clientStore');
const { renderLoginPage } = require('./lib/loginPage');
const { getDailyProverb } = require('./lib/dailyProverb');

const app = express();
const PORT = process.env.PORT || 3000;
const SERVER_VERSION = require('./package.json').version;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser());
// 只開放 CSS/JS；控制台 HTML 必須走登入後的 GET /
app.use('/css', express.static(path.join(__dirname, 'public', 'css')));
app.use('/js', express.static(path.join(__dirname, 'public', 'js')));

// 建立 HTTP 伺服器
const server = http.createServer(app);

// 建立 WebSocket 伺服器（僅 Dashboard 狀態推送）
const wssDashboard = new WebSocketServer({ noServer: true });

// 儲存所有 Dashboard 的 WebSocket 連線
const dashboardSockets = new Set();

/**
 * 通訊協定（HTTP-primary）：
 * - Worker（clickClick）：POST /api/heartbeat + Bearer token，從回應 action/actions 取指令
 * - Dashboard：REST 下指令；WebSocket /ws/dashboard 僅推送 STATUS_UPDATE
 */

// 廣播訊息給所有在線的 Web Dashboard
function broadcastToDashboards(payload) {
  const data = JSON.stringify(payload);
  for (const socket of dashboardSockets) {
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(data);
    }
  }
}

startOfflineMonitor(broadcastToDashboards);

// 登入頁面 GET
app.get('/login', (req, res) => {
  if (isAuth(req)) return res.redirect('/');
  const clientIp = req.ip || req.headers['x-forwarded-for'] || req.socket.remoteAddress;
  const lockStatus = getLoginLockStatus(clientIp);
  const hasError = req.query.error === '1';

  let alertMessage = '';
  if (lockStatus.isLocked) {
    const mins = Math.floor(lockStatus.remainingSec / 60);
    const secs = lockStatus.remainingSec % 60;
    const timeDisplay = mins > 0 ? `${mins} 分 ${secs} 秒` : `${secs} 秒`;
    alertMessage = `<div class="error-alert" id="lockAlert">⛔ 嘗試錯誤過多！帳號已鎖定，請等待 <span id="countdown">${timeDisplay}</span>。</div>`;
  } else if (hasError) {
    const remainingAttempts = Math.max(0, 5 - lockStatus.attempts);
    alertMessage = `<div class="error-alert">[警告] 密碼錯誤！剩餘嘗試次數：${remainingAttempts} 次 (滿5次鎖定15分鐘)</div>`;
  }

  res.send(renderLoginPage({ alertMessage, lockStatus, version: SERVER_VERSION }));
});

// 登入驗證 POST
app.post('/login', (req, res) => {
  const clientIp = req.ip || req.headers['x-forwarded-for'] || req.socket.remoteAddress;
  const lockStatus = getLoginLockStatus(clientIp);

  if (lockStatus.isLocked) {
    return res.redirect('/login');
  }

  const { password } = req.body;
  if (checkAdminPassword(password)) {
    clearLoginAttempt(clientIp);
    res.cookie('auth', AUTH_COOKIE_VALUE, authCookieOptions(req));
    return res.redirect('/');
  }

  recordLoginFailure(clientIp);
  res.redirect('/login?error=1');
});

// 登出 GET
app.get('/logout', (req, res) => {
  res.clearCookie('auth', authCookieClearOptions(req));
  res.redirect('/login');
});

// 傳統 Ping 測試
/** 今日六人行台詞（公開；桌面端與控制台共用同一套選取邏輯） */
app.get('/api/daily-proverb', (req, res) => {
  res.json(getDailyProverb());
});

app.get('/ping', (req, res) => {
  res.json({ message: 'pong', timestamp: new Date() });
});

// 心跳接收 REST API（需 Bearer token，供 Worker Client 上報）
app.post('/api/heartbeat', (req, res) => {
  if (!isHeartbeatAuthorized(req)) {
    return res.status(401).json({ success: false, message: 'Unauthorized: invalid or missing heartbeat token' });
  }

  const { clientId = 'company-worker', status = 'ONLINE', tasks = [], message = '', appVersion = '' } = req.body;
  const now = new Date();

  const existing = getOrCreateClient(clientId);
  const drainedActions = drainPendingActions(existing);
  const actionToSend = drainedActions.length > 0 ? drainedActions[0] : 'NONE';

  const effectiveTasks = Array.isArray(tasks) ? tasks : (existing.tasks || []);
  if (drainedActions.length > 0) {
    appendClientEvent(existing, '桌面端心跳已取走指令：' + drainedActions.join('、'));
  }
  logTaskTransitions(existing, effectiveTasks);

  const clientInfo = {
    ...existing,
    clientId,
    status: status === 'OFFLINE' ? 'ONLINE' : status,
    tasks: effectiveTasks,
    eventLog: existing.eventLog || [],
    message: message || existing.message || '',
    appVersion: appVersion || existing.appVersion || '',
    lastSeen: now,
    transport: 'http',
    clientIp: req.ip || req.headers['x-forwarded-for'] || req.socket.remoteAddress
  };
  delete clientInfo.pendingActions;
  delete clientInfo.pendingAction;
  delete clientInfo.pendingActionTime;

  setClient(clientId, clientInfo);

  if (status === 'SUCCESS' || status === 'FAILED') {
    console.log(`[Checkin Report] 設備 ${clientId} 上報打卡結果 (${status}): ${message}`);
  }

  broadcastToDashboards({ type: 'STATUS_UPDATE', clients: publicClientsSnapshot() });

  res.json({
    success: true,
    message: 'Heartbeat acknowledged',
    action: actionToSend,
    actions: drainedActions,
    peers: peerSnapshot(clientId),
    ackTimestamp: now.toISOString()
  });
});

// 同事互動：傳送訊息（需 Bearer token，供桌面端 Worker 呼叫）
app.post('/api/peer/message', (req, res) => {
  if (!isHeartbeatAuthorized(req)) {
    return res.status(401).json({ success: false, message: 'Unauthorized: invalid or missing heartbeat token' });
  }
  const { fromClientId, toClientId, text } = req.body || {};
  const result = queuePeerMessage(toClientId, fromClientId, text);
  if (!result.ok) {
    return res.status(400).json({ success: false, message: result.message });
  }
  broadcastToDashboards({ type: 'STATUS_UPDATE', clients: publicClientsSnapshot() });
  res.json({ success: true, message: result.message });
});

// 同事互動：戳一下（需 Bearer token，供桌面端 Worker 呼叫）
app.post('/api/peer/poke', (req, res) => {
  if (!isHeartbeatAuthorized(req)) {
    return res.status(401).json({ success: false, message: 'Unauthorized: invalid or missing heartbeat token' });
  }
  const { fromClientId, toClientId } = req.body || {};
  const result = queuePeerPoke(toClientId, fromClientId);
  if (!result.ok) {
    return res.status(400).json({ success: false, message: result.message });
  }
  broadcastToDashboards({ type: 'STATUS_UPDATE', clients: publicClientsSnapshot() });
  res.json({ success: true, message: result.message });
});

// 狀態 API（需登入；targetUrl 遮路徑，buttonId 保留）
app.get('/api/status', (req, res) => {
  if (!isAuth(req)) {
    return res.status(401).json({ success: false, message: '未登入或權限不足' });
  }
  res.json({
    serverTimestamp: new Date().toISOString(),
    serverVersion: SERVER_VERSION,
    totalClients: clients.size,
    clients: publicClientsSnapshot()
  });
});

// 遠端取消所有排程 API (需登入)
app.post('/api/clients/:clientId/cancel-schedule', (req, res) => {
  if (!isAuth(req)) {
    return res.status(401).json({ success: false, message: '未登入或權限不足' });
  }
  const { clientId } = req.params;
  queueClientAction(clientId, 'CANCEL_SCHEDULE');
  broadcastToDashboards({ type: 'STATUS_UPDATE', clients: publicClientsSnapshot() });
  res.json({ success: true, message: `已成功將【取消所有排程】指令派送至 ${clientId} 佇列` });
});

// 遠端取消指定任務 API (需登入)
app.post('/api/clients/:clientId/cancel-task/:taskId', (req, res) => {
  if (!isAuth(req)) {
    return res.status(401).json({ success: false, message: '未登入或權限不足' });
  }
  const { clientId, taskId } = req.params;
  queueClientAction(clientId, `CANCEL_TASK:${taskId}`);
  broadcastToDashboards({ type: 'STATUS_UPDATE', clients: publicClientsSnapshot() });
  res.json({ success: true, message: `已成功將【取消任務 ${taskId}】指令派送至 ${clientId} 佇列` });
});

// 刪除客戶端紀錄 API (需登入)
app.delete('/api/clients/:clientId', (req, res) => {
  if (!isAuth(req)) {
    return res.status(401).json({ success: false, message: '未登入或權限不足' });
  }
  const { clientId } = req.params;
  deleteClient(clientId);
  broadcastToDashboards({ type: 'STATUS_UPDATE', clients: publicClientsSnapshot() });
  res.json({ success: true, message: `已移除設備紀錄：${clientId}` });
});

function sendDashboardHtml(res) {
  const htmlPath = path.join(__dirname, 'public', 'index.html');
  const proverb = getDailyProverb();
  const html = fs.readFileSync(htmlPath, 'utf8')
    .replace(/\{\{SERVER_VERSION\}\}/g, SERVER_VERSION)
    .replace(/\{\{DAILY_PROVERB_EN\}\}/g, escapeHtml(proverb.en))
    .replace(/\{\{DAILY_PROVERB_ZH\}\}/g, escapeHtml(proverb.zh))
    .replace(/\{\{DAILY_PROVERB_CONTEXT\}\}/g, escapeHtml(proverb.context || ''))
    .replace(/\{\{DAILY_PROVERB_DATE\}\}/g, escapeHtml(proverb.date));
  res.type('html').send(html);
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// Web 儀表板 (需要登入)；不透過靜態檔提供 index.html
app.get('/', (req, res) => {
  if (!isAuth(req)) {
    return res.redirect('/login');
  }
  sendDashboardHtml(res);
});

app.get('/index.html', (req, res) => {
  res.redirect(isAuth(req) ? '/' : '/login');
});

// ----------------------------------------------------
// WebSocket：僅 Dashboard 狀態推送
// ----------------------------------------------------
server.on('upgrade', (request, socket, head) => {
  const pathname = new URL(request.url, `http://${request.headers.host}`).pathname;

  if (pathname === '/ws/dashboard') {
    if (!isAuthFromCookieHeader(request.headers.cookie)) {
      socket.write('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }
    wssDashboard.handleUpgrade(request, socket, head, (ws) => {
      wssDashboard.emit('connection', ws, request);
    });
  } else {
    socket.destroy();
  }
});

wssDashboard.on('connection', (ws) => {
  console.log(`[WebSocket Dashboard] Dashboard 畫面已連線`);
  dashboardSockets.add(ws);

  ws.send(JSON.stringify({ type: 'STATUS_UPDATE', clients: publicClientsSnapshot() }));

  ws.on('close', () => {
    dashboardSockets.delete(ws);
  });
});

server.listen(PORT, () => {
  console.log(`Server v${SERVER_VERSION} listening on port ${PORT}`);
  console.log(`- Web Dashboard: http://localhost:${PORT} (login required)`);
  console.log(`- Heartbeat API: POST /api/heartbeat (Bearer token required)`);
  console.log(`- Protocol: HTTP heartbeat for workers; Dashboard WS for status push only`);
  console.log(`- Admin password: ${process.env.ADMIN_PASSWORD ? 'from ADMIN_PASSWORD env' : 'default (secret)'}`);
});
