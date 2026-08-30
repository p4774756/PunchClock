// 儲存心跳客戶端資訊 (key: clientId)
const clients = new Map();

// 逾時設定：超過 3 分鐘未收到心跳判定為 離線 (OFFLINE)
const HEARTBEAT_TIMEOUT_MS = 3 * 60 * 1000;
const PENDING_ACTION_TTL_MS = 30 * 1000;

function getClients() {
  return clients;
}

function getClient(clientId) {
  return clients.get(clientId);
}

function setClient(clientId, clientInfo) {
  clients.set(clientId, clientInfo);
  return clientInfo;
}

function deleteClient(clientId) {
  return clients.delete(clientId);
}

function getOrCreateClient(clientId) {
  return clients.get(clientId) || {
    clientId,
    status: 'ONLINE',
    tasks: [],
    lastSeen: new Date(),
    message: '',
    transport: 'unknown'
  };
}

const EVENT_LOG_MAX = 50;

const TASK_STATUS_LABEL = {
  PENDING: '待命中',
  SCHEDULED: '等待中',
  CHECKING_IN: '執行中',
  SUCCESS: '成功',
  FAILED: '失敗',
  CANCELLED: '已取消'
};

function taskStatusLabel(status) {
  if (!status) return '未知';
  return TASK_STATUS_LABEL[status] || status;
}

function appendClientEvent(client, text) {
  if (!client || !text) return;
  if (!Array.isArray(client.eventLog)) {
    client.eventLog = [];
  }
  client.eventLog.push({
    time: new Date().toISOString(),
    text: String(text)
  });
  if (client.eventLog.length > EVENT_LOG_MAX) {
    client.eventLog = client.eventLog.slice(-EVENT_LOG_MAX);
  }
}

function findTaskName(tasks, taskId) {
  const list = Array.isArray(tasks) ? tasks : [];
  const found = list.find((t) => t && t.id === taskId);
  return found && found.name ? found.name : taskId;
}

function logTaskTransitions(existing, nextTasks) {
  const prevById = new Map((existing.tasks || []).map((t) => [t && t.id, t]));
  const next = Array.isArray(nextTasks) ? nextTasks : [];
  for (let i = 0; i < next.length; i++) {
    const t = next[i];
    if (!t || !t.id) continue;
    const name = t.name || t.id;
    const prev = prevById.get(t.id);
    if (!prev) {
      const when = t.actualTime || t.targetTime || '';
      appendClientEvent(existing, '任務【' + name + '】上報狀態：' + taskStatusLabel(t.status)
        + (when ? '（' + when + '）' : ''));
      continue;
    }
    if (prev.status !== t.status) {
      const detail = t.message ? '；原因：' + t.message : '';
      appendClientEvent(existing, '任務【' + name + '】' + taskStatusLabel(prev.status)
        + ' → ' + taskStatusLabel(t.status) + detail);
    }
  }
}

const PEER_MESSAGE_MAX_LEN = 500;

function encodePeerMessage(fromClientId, text) {
  const payload = Buffer.from(String(text), 'utf8').toString('base64url');
  return 'MSG|' + fromClientId + '|' + payload;
}

function encodePeerPoke(fromClientId) {
  return 'POKE|' + fromClientId;
}

function queueClientAction(clientId, action) {
  const existing = getOrCreateClient(clientId);
  if (!Array.isArray(existing.pendingActions)) {
    existing.pendingActions = [];
  }
  // 相容舊欄位
  if (existing.pendingAction) {
    existing.pendingActions.push({
      action: existing.pendingAction,
      time: existing.pendingActionTime || Date.now()
    });
    delete existing.pendingAction;
    delete existing.pendingActionTime;
  }
  existing.pendingActions.push({ action, time: Date.now() });
  if (action === 'CANCEL_SCHEDULE') {
    appendClientEvent(existing, '後台已送出【取消全部任務】指令（等待桌面端下次心跳執行）');
  } else if (typeof action === 'string' && action.startsWith('CANCEL_TASK:')) {
    const taskId = action.slice('CANCEL_TASK:'.length);
    const name = findTaskName(existing.tasks, taskId);
    appendClientEvent(existing, '後台已送出【取消任務】指令：' + name + '（' + taskId + '）（等待桌面端下次心跳執行）');
  } else if (typeof action === 'string' && action.startsWith('MSG|')) {
    const parts = action.split('|');
    const fromId = parts[1] || '未知';
    appendClientEvent(existing, '同事【' + fromId + '】傳來訊息（等待桌面端下次心跳收取）');
  } else if (typeof action === 'string' && action.startsWith('POKE|')) {
    const fromId = action.slice('POKE|'.length) || '未知';
    appendClientEvent(existing, '同事【' + fromId + '】戳了你（等待桌面端下次心跳收取）');
  }
  clients.set(clientId, existing);
  return existing;
}

function queuePeerMessage(toClientId, fromClientId, text) {
  const trimmed = String(text || '').trim();
  if (!toClientId || !fromClientId || !trimmed) {
    return { ok: false, message: '缺少收件人或訊息內容' };
  }
  if (toClientId === fromClientId) {
    return { ok: false, message: '不能發送訊息給自己' };
  }
  if (trimmed.length > PEER_MESSAGE_MAX_LEN) {
    return { ok: false, message: '訊息長度不可超過 ' + PEER_MESSAGE_MAX_LEN + ' 字' };
  }
  const action = encodePeerMessage(fromClientId, trimmed);
  queueClientAction(toClientId, action);
  const sender = getOrCreateClient(fromClientId);
  appendClientEvent(sender, '已傳送訊息給【' + toClientId + '】（等待對方心跳收取）');
  clients.set(fromClientId, sender);
  return { ok: true, message: '訊息已排入佇列，對方約 15 秒內收到' };
}

function queuePeerPoke(toClientId, fromClientId) {
  if (!toClientId || !fromClientId) {
    return { ok: false, message: '缺少收件人或發送者' };
  }
  if (toClientId === fromClientId) {
    return { ok: false, message: '不能戳自己' };
  }
  const action = encodePeerPoke(fromClientId);
  queueClientAction(toClientId, action);
  const sender = getOrCreateClient(fromClientId);
  appendClientEvent(sender, '已戳【' + toClientId + '】（等待對方心跳收取）');
  clients.set(fromClientId, sender);
  return { ok: true, message: '戳一下已排入佇列，對方約 15 秒內收到' };
}

function peerSnapshot(excludeClientId) {
  const now = Date.now();
  return Array.from(clients.values())
    .filter((c) => c && c.clientId && c.clientId !== excludeClientId)
    .map((c) => {
      const lastSeenMs = new Date(c.lastSeen).getTime();
      const offline = Number.isNaN(lastSeenMs) || (now - lastSeenMs > HEARTBEAT_TIMEOUT_MS);
      const tasks = Array.isArray(c.tasks) ? c.tasks : [];
      return {
        clientId: c.clientId,
        status: offline ? 'OFFLINE' : (c.status || 'ONLINE'),
        appVersion: c.appVersion || '',
        taskCount: tasks.length,
        scheduledCount: tasks.filter((t) => t && t.status === 'SCHEDULED').length,
        lastSeen: c.lastSeen
      };
    })
    .sort((a, b) => a.clientId.localeCompare(b.clientId));
}

function drainPendingActions(existing) {
  const now = Date.now();
  const actions = [];
  const queue = Array.isArray(existing.pendingActions) ? existing.pendingActions.slice() : [];

  if (existing.pendingAction) {
    queue.push({
      action: existing.pendingAction,
      time: existing.pendingActionTime || 0
    });
  }

  for (const item of queue) {
    if (!item || !item.action) continue;
    if (now - (item.time || 0) < PENDING_ACTION_TTL_MS) {
      actions.push(item.action);
    }
  }

  delete existing.pendingActions;
  delete existing.pendingAction;
  delete existing.pendingActionTime;
  return actions;
}

/**
 * 打卡網址：協定保留，網域各段只留開頭，路徑／參數一律 /***
 * https://tw.yahoo.com/checkin?id=1 → https://t**.ya***.com/***
 */
function maskTargetUrl(url) {
  if (url == null || typeof url !== 'string' || url === '') return url;
  try {
    const parsed = new URL(url);
    const host = maskHostname(parsed.hostname);
    const port = parsed.port ? ':' + parsed.port : '';
    return parsed.protocol + '//' + host + port + '/***';
  } catch {
    return maskHostname(url) + '/***';
  }
}

function maskHostname(hostname) {
  if (!hostname) return '***';
  return hostname.split('.').map((label, index, parts) => {
    const isTld = index === parts.length - 1 && parts.length > 1;
    if (isTld) return label;
    return maskLabel(label);
  }).join('.');
}

function maskLabel(label) {
  if (!label) return '***';
  if (label.length === 1) return '*';
  if (label.length === 2) return label[0] + '*';
  return label.slice(0, 2) + '***';
}

function sanitizeClientForApi(client) {
  if (!client || typeof client !== 'object') return client;
  const copy = { ...client };
  if (copy.targetUrl != null) copy.targetUrl = maskTargetUrl(copy.targetUrl);
  if (Array.isArray(copy.tasks)) {
    copy.tasks = copy.tasks.map((t) => {
      if (!t || typeof t !== 'object') return t;
      const task = { ...t };
      if (task.targetUrl != null) task.targetUrl = maskTargetUrl(task.targetUrl);
      return task;
    });
  }
  return copy;
}

function publicClientsSnapshot() {
  return Array.from(clients.values()).map(sanitizeClientForApi);
}

/**
 * 定時巡檢客戶端存活狀態；狀態變更時透過 callback 廣播。
 * @returns {NodeJS.Timeout} interval handle
 */
function startOfflineMonitor(broadcastToDashboards) {
  return setInterval(() => {
    const now = Date.now();
    let changed = false;

    for (const [clientId, data] of clients.entries()) {
      if (data.status !== 'OFFLINE' && (now - new Date(data.lastSeen).getTime() > HEARTBEAT_TIMEOUT_MS)) {
        data.status = 'OFFLINE';
        data.offlineSince = new Date();
        changed = true;
        console.log(`[Heartbeat Monitor] 客戶端 ${clientId} 已逾時，標記為 離線 (OFFLINE)`);
      }
    }

    if (changed && typeof broadcastToDashboards === 'function') {
      broadcastToDashboards({ type: 'STATUS_UPDATE', clients: publicClientsSnapshot() });
    }
  }, 10 * 1000);
}

module.exports = {
  clients,
  getClients,
  getClient,
  setClient,
  deleteClient,
  PENDING_ACTION_TTL_MS,
  HEARTBEAT_TIMEOUT_MS,
  EVENT_LOG_MAX,
  PEER_MESSAGE_MAX_LEN,
  getOrCreateClient,
  queueClientAction,
  queuePeerMessage,
  queuePeerPoke,
  encodePeerMessage,
  encodePeerPoke,
  peerSnapshot,
  drainPendingActions,
  appendClientEvent,
  logTaskTransitions,
  taskStatusLabel,
  sanitizeClientForApi,
  maskTargetUrl,
  publicClientsSnapshot,
  startOfflineMonitor
};
