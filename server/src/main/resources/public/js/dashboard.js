    let ws;
    let clientData = [];
    let clientLogs = {};
    let lastClientState = {};
    let logSkipCount = {};
    let expandedClients = new Set();
    let lastStructureFingerprint = '';
    let lastContentFingerprint = '';
    let lastLogFingerprints = {};
    const pendingTaskCancels = new Set();
    const pendingCancelAllClients = new Set();
    const pendingSince = new Map();
    const PENDING_CANCEL_MAX_MS = 45000;
    function cancelPendingKey(clientId, taskId) {
      return clientId + '|' + taskId;
    }

    function isTaskCancelPending(clientId, taskId) {
      return pendingTaskCancels.has(cancelPendingKey(clientId, taskId))
        || pendingCancelAllClients.has(clientId);
    }

    function markTaskCancelPending(clientId, taskId) {
      const key = cancelPendingKey(clientId, taskId);
      pendingTaskCancels.add(key);
      pendingSince.set(key, Date.now());
    }

    function clearTaskCancelPending(clientId, taskId) {
      const key = cancelPendingKey(clientId, taskId);
      pendingTaskCancels.delete(key);
      pendingSince.delete(key);
    }

    function markCancelAllPending(clientId) {
      pendingCancelAllClients.add(clientId);
      pendingSince.set(clientId + '|*', Date.now());
    }

    function clearCancelAllPending(clientId) {
      pendingCancelAllClients.delete(clientId);
      pendingSince.delete(clientId + '|*');
    }

    function isTerminalTaskStatus(status) {
      return status === 'CANCELLED' || status === 'SUCCESS' || status === 'FAILED';
    }

    function isRemoteCancelMessage(message) {
      const text = String(message || '');
      return text.includes('遠端取消') || text.includes('槽位已停用') || text.includes('取消全部');
    }

    function eventTimeMs(e) {
      if (!e || !e.time) return NaN;
      return Date.parse(e.time);
    }

    /** 僅認可「本次取消請求」之後寫入的狀態轉換，避免舊日誌誤判為已確認 */
    function isTaskCancelAckEvent(e, task, pendingAt) {
      const text = String(e.text || '');
      if (!text.includes('→ 已取消')) return false;
      const label = task.name || task.id || '';
      if (!text.includes(label) && !text.includes(task.id)) return false;
      if (pendingAt == null) return true;
      const eventMs = eventTimeMs(e);
      return !Number.isNaN(eventMs) && eventMs >= pendingAt;
    }

    function syncPendingFromEventLog(c) {
      if (!c) return;
      const events = Array.isArray(c.eventLog) ? c.eventLog : [];
      const tasks = Array.isArray(c.tasks) ? c.tasks : [];
      for (let i = 0; i < tasks.length; i++) {
        const task = tasks[i];
        if (!isTaskCancelPending(c.clientId, task.id)) continue;
        const pendingAt = pendingSince.get(cancelPendingKey(c.clientId, task.id));
        const confirmed = events.some((e) => isTaskCancelAckEvent(e, task, pendingAt));
        if (confirmed) {
          clearTaskCancelPending(c.clientId, task.id);
        }
      }
    }

    function syncCancelPendingFromTasks(clientId, tasks) {
      const now = Date.now();
      for (let i = 0; i < (tasks || []).length; i++) {
        const t = tasks[i];
        const key = cancelPendingKey(clientId, t.id);
        const pendingAt = pendingSince.get(key);
        const timedOut = pendingAt != null && (now - pendingAt) > PENDING_CANCEL_MAX_MS;
        if (isTerminalTaskStatus(t.status) || isRemoteCancelMessage(t.message) || timedOut) {
          clearTaskCancelPending(clientId, t.id);
        }
      }
      const allKey = clientId + '|*';
      const allPendingAt = pendingSince.get(allKey);
      const allTimedOut = allPendingAt != null && (now - allPendingAt) > PENDING_CANCEL_MAX_MS;
      const hasActive = (tasks || []).some((t) => t.status === 'SCHEDULED' || t.status === 'CHECKING_IN');
      if (!hasActive || allTimedOut) {
        clearCancelAllPending(clientId);
      }
    }

    function buildTaskStatusBadgeHtml(status, clientId, taskId) {
      if (status === 'SCHEDULED' && isTaskCancelPending(clientId, taskId)) {
        return '<span class="chip chip-pending" title="已送出取消指令，等候桌面端心跳確認">取消中…</span>';
      }
      return getStatusBadgeHtml(status);
    }

    function buildCancelButtonHtml(clientId, taskId, isConnected, taskStatus) {
      if (!isConnected || taskStatus !== 'SCHEDULED') return '';
      if (isTaskCancelPending(clientId, taskId)) return '';
      return '<button class="btn btn-ghost-danger" onclick="remoteCancelTask(\'' + clientId + '\', \'' + taskId + '\')">取消</button>';
    }

    function hasCancellableTasks(tasks) {
      return (tasks || []).some((t) => t.status === 'SCHEDULED' || t.status === 'CHECKING_IN');
    }

    function deviceHeadBadgeHelpers() {
      const g = typeof window !== 'undefined' ? window : globalThis;
      return (g && g.PunchclockDeviceHeadBadge) || {};
    }

    function buildDeviceHeadBadgeText(isConnected, transportLabel, tasks) {
      const fn = deviceHeadBadgeHelpers().buildDeviceHeadBadgeText;
      return typeof fn === 'function'
        ? fn(isConnected, transportLabel, tasks)
        : (isConnected ? '辦公室上班中' : '離線');
    }

    function buildDeviceHeadChipClass(isConnected, tasks) {
      const fn = deviceHeadBadgeHelpers().buildDeviceHeadChipClass;
      if (typeof fn === 'function') return fn(isConnected, tasks);
      return isConnected ? 'chip-online' : 'chip-offline';
    }

    function buildCancelAllButtonHtml(clientId, isConnected, tasks) {
      if (pendingCancelAllClients.has(clientId)) {
        return '<button class="btn btn-danger btn-pending" data-role="cancel-all" disabled title="已送出取消全部指令，等候桌面端心跳確認">取消中…</button>';
      }
      if ((tasks || []).length < 2) {
        return '';
      }
      const canCancel = isConnected && hasCancellableTasks(tasks);
      const disabled = canCancel ? '' : 'disabled';
      const title = canCancel ? '' : ' title="現在沒有在做的可以取消"';
      return '<button class="btn btn-danger" data-role="cancel-all" onclick="remoteCancelSchedule(\'' + clientId + '\')"' + title + ' ' + disabled + '>全部取消</button>';
    }

    function refreshTaskCancelUi(clientId, taskId) {
      const root = deviceEl(clientId);
      if (!root) return;
      const c = clientData.find((x) => x.clientId === clientId);
      if (!c) return;
      const t = (c.tasks || []).find((x) => x.id === taskId);
      if (!t) return;
      const card = root.querySelector('.task-card[data-task-id="' + taskId + '"]');
      if (card) patchTaskCard(card, c, t, c.status !== 'OFFLINE');
    }

    function refreshCancelAllUi(clientId) {
      const root = deviceEl(clientId);
      if (!root) return;
      const c = clientData.find((x) => x.clientId === clientId);
      if (!c) return;
      const tasks = Array.isArray(c.tasks) ? c.tasks : [];
      const actionRow = root.querySelector('.action-row');
      if (actionRow) {
        const isConnected = c.status !== 'OFFLINE';
        actionRow.innerHTML = buildCancelAllButtonHtml(clientId, isConnected, tasks);
      }
      const taskListHost = root.querySelector('[data-role="task-list"]');
      if (taskListHost) {
        patchTaskListHost(c, c.status !== 'OFFLINE', taskListHost);
      }
    }

    function isCancelAllAckMessage(message) {
      const text = String(message || '');
      return text.includes('遠端取消全部') || text.includes('取消全部任務');
    }

    function syncPendingCancelAllFromEventLog(c) {
      if (!c || !pendingCancelAllClients.has(c.clientId)) return;
      const events = Array.isArray(c.eventLog) ? c.eventLog : [];
      const tasks = Array.isArray(c.tasks) ? c.tasks : [];
      const allCancelled = tasks.length > 0 && tasks.every((t) => t.status === 'CANCELLED');
      const pendingAt = pendingSince.get(c.clientId + '|*');
      const logAck = events.some((e) => {
        const text = String(e.text || '');
        if (!text.includes('取消全部') || !(text.includes('→ 已取消') || text.includes('已停止'))) return false;
        if (pendingAt == null) return true;
        const eventMs = eventTimeMs(e);
        return !Number.isNaN(eventMs) && eventMs >= pendingAt;
      });
      if (!allCancelled && !logAck) return;
      clearCancelAllPending(c.clientId);
      for (let i = 0; i < tasks.length; i++) {
        clearTaskCancelPending(c.clientId, tasks[i].id);
      }
    }

    /** 取消全部等待中：任務仍顯示等待屬正常，等桌面端心跳回報後才解除「取消中…」 */
    function reconcileCancelAllClientState(c) {
      if (!c || !pendingCancelAllClients.has(c.clientId)) return;
      const tasks = Array.isArray(c.tasks) ? c.tasks : [];
      const hasActive = tasks.some((t) => t.status === 'SCHEDULED' || t.status === 'CHECKING_IN');
      if (hasActive) return;
      clearCancelAllPending(c.clientId);
      for (let i = 0; i < tasks.length; i++) {
        clearTaskCancelPending(c.clientId, tasks[i].id);
      }
    }

    /** 裝置 / 任務 id / app 版本變了才需要整頁重建 */
    function clientsStructureFingerprint(clients) {
      return JSON.stringify((clients || []).map((c) => ({
        id: c.clientId,
        offline: c.status === 'OFFLINE',
        appVersion: c.appVersion || '',
        appReleaseTime: c.appReleaseTime || '',
        tasks: (c.tasks || []).map((t) => t.id).sort()
      })));
    }

    /** 狀態 / 日誌等內容變了只做局部更新（不含 lastSeen，心跳秒數另處理） */
    function clientsContentFingerprint(clients) {
      return JSON.stringify((clients || []).map((c) => ({
        id: c.clientId,
        status: c.status,
        message: c.message,
        transport: c.transport,
        appVersion: c.appVersion,
        tasks: (c.tasks || []).map((t) => ({
          id: t.id,
          name: t.name,
          status: t.status,
          targetTime: t.targetTime,
          actualTime: t.actualTime,
          targetUrl: t.targetUrl,
          buttonId: t.buttonId,
          useRandomOffset: t.useRandomOffset,
          message: t.message
        })),
        eventLog: (c.eventLog || []).map((e) => (e.time || '') + '|' + (e.text || ''))
      })));
    }

    function deviceEl(clientId) {
      return document.querySelector('.device[data-client-id="' + clientId + '"]');
    }

    function formatHeartbeat(lastSeen) {
      const lastSeenDate = new Date(lastSeen);
      if (isNaN(lastSeenDate.getTime())) return '-';
      const diffSec = Math.max(0, Math.floor((Date.now() - lastSeenDate.getTime()) / 1000));
      return lastSeenDate.toLocaleTimeString('zh-TW') + ' · ' + diffSec + 's';
    }

    function parseTaskTime(value) {
      if (!value) return null;
      const m = String(value).match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})/);
      if (m) {
        return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]);
      }
      const parsed = new Date(value);
      return isNaN(parsed.getTime()) ? null : parsed;
    }

    function formatCountdown(seconds) {
      if (seconds <= 0) return '即將觸發';
      const days = Math.floor(seconds / 86400);
      const hours = Math.floor((seconds % 86400) / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      const secs = seconds % 60;
      const hms = String(hours).padStart(2, '0') + ':'
        + String(minutes).padStart(2, '0') + ':'
        + String(secs).padStart(2, '0');
      return days > 0 ? (days + '天 ' + hms) : hms;
    }

    function countdownLabel(status, actualTime) {
      if (status !== 'SCHEDULED') return '—';
      const t = parseTaskTime(actualTime);
      if (!t) return '—';
      return formatCountdown(Math.floor((t.getTime() - Date.now()) / 1000));
    }

    function updateCountdowns() {
      const nodes = document.querySelectorAll('.task-countdown');
      for (let i = 0; i < nodes.length; i++) {
        const status = nodes[i].getAttribute('data-status');
        const actual = nodes[i].getAttribute('data-actual-time');
        nodes[i].textContent = countdownLabel(status, actual);
      }
    }

    function updateHeartbeatMetrics() {
      const nodes = document.querySelectorAll('.heartbeat-value');
      for (let i = 0; i < nodes.length; i++) {
        const id = nodes[i].getAttribute('data-client-id');
        const c = clientData.find((x) => x.clientId === id);
        if (c) nodes[i].textContent = formatHeartbeat(c.lastSeen);
      }
    }

    function applyClientSnapshot(clients, forceRender) {
      clientData = clients || [];
      for (let i = 0; i < clientData.length; i++) {
        reconcileCancelAllClientState(clientData[i]);
        syncPendingFromEventLog(clientData[i]);
        syncPendingCancelAllFromEventLog(clientData[i]);
        syncCancelPendingFromTasks(clientData[i].clientId, clientData[i].tasks || []);
      }
      syncClientLogs(clientData);

      const structFp = clientsStructureFingerprint(clientData);
      const contentFp = clientsContentFingerprint(clientData);
      const needsFullRender = forceRender
        || structFp !== lastStructureFingerprint
        || clientData.length === 0
        || !document.querySelector('.device[data-client-id]');

      if (needsFullRender) {
        lastStructureFingerprint = structFp;
        lastContentFingerprint = contentFp;
        renderClients();
        return;
      }

      if (contentFp !== lastContentFingerprint) {
        lastContentFingerprint = contentFp;
        patchAllClients();
      }
      updateHeartbeatMetrics();
    }

    function patchAllClients() {
      for (let i = 0; i < clientData.length; i++) {
        patchClient(clientData[i]);
      }
    }

    function patchClient(c) {
      const root = deviceEl(c.clientId);
      if (!root) return;

      const isConnected = c.status !== 'OFFLINE';
      const tasks = Array.isArray(c.tasks) ? c.tasks.slice() : [];
      const transportLabel = c.transport === 'http' ? 'HTTP' : (c.transport || '-');
      const badgeText = buildDeviceHeadBadgeText(isConnected, transportLabel, tasks);

      const headChip = root.querySelector('.device-head-right .chip');
      if (headChip) {
        headChip.className = 'chip ' + buildDeviceHeadChipClass(isConnected, tasks);
        headChip.innerHTML = (isConnected ? '<span class="dot dot-live"></span>' : '<span class="dot"></span>') + badgeText;
      }

      syncDeviceFoldChrome(root, c.clientId);

      const taskCountEl = root.querySelector('.metric-value[data-role="task-count"]');
      if (taskCountEl) taskCountEl.textContent = tasks.length + ' 筆';

      const msgEl = root.querySelector('.metric-value[data-role="latest-message"]');
      if (msgEl) {
        msgEl.textContent = cleanMessage(c.message);
        msgEl.style.color = getStatusColor(c.status);
      }

      const taskListHost = root.querySelector('[data-role="task-list"]');
      syncPendingFromEventLog(c);
      syncPendingCancelAllFromEventLog(c);
      syncCancelPendingFromTasks(c.clientId, tasks);
      if (taskListHost) {
        patchTaskListHost(c, isConnected, taskListHost);
      }

      const actionRow = root.querySelector('.action-row');
      if (actionRow) {
        actionRow.innerHTML = buildCancelAllButtonHtml(c.clientId, isConnected, tasks);
      } else {
        const cancelAllBtn = root.querySelector('[data-role="cancel-all"]');
        if (cancelAllBtn) {
          cancelAllBtn.disabled = !isConnected || tasks.length === 0;
        }
      }

      updateClientLogBox(c);
    }

    function updateClientLogBox(c) {
      const box = document.getElementById('log-' + c.clientId);
      if (!box) return;
      const fp = visibleLogLines(c).join('\n');
      if (lastLogFingerprints[c.clientId] === fp) return;
      lastLogFingerprints[c.clientId] = fp;

      const lines = visibleLogLines(c);
      const html = lines.length > 0
        ? lines.map(escapeHtml).join('<br>')
        : ('[' + new Date().toLocaleTimeString('zh-TW') + '] 系統連線就緒');
      const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 32;
      box.innerHTML = html;
      if (atBottom) box.scrollTop = box.scrollHeight;
    }

    function formatEventClock(iso) {
      const d = new Date(iso);
      if (isNaN(d.getTime())) return '--';
      return d.toLocaleTimeString('zh-TW');
    }

    function visibleLogLines(c) {
      const skip = logSkipCount[c.clientId] || 0;
      const events = Array.isArray(c.eventLog) ? c.eventLog.slice(skip) : [];
      const serverLines = events.map((e) => '[' + formatEventClock(e.time) + '] ' + (e.text || ''));
      const localLines = clientLogs[c.clientId] || [];
      return serverLines.concat(localLines);
    }

    function taskStatusLabel(status) {
      if (status === 'SCHEDULED') return '等待中';
      if (status === 'CHECKING_IN') return '執行中';
      if (status === 'SUCCESS') return '成功';
      if (status === 'FAILED') return '失敗';
      if (status === 'CANCELLED') return '已取消';
      if (status === 'PENDING') return '待命中';
      return status || '未知';
    }

    function syncClientLogs(clients) {
      if (!clients || !Array.isArray(clients)) return;
      clients.forEach(c => {
        if (Array.isArray(c.eventLog) && c.eventLog.length > 0) {
          lastClientState[c.clientId] = (c.status || '') + '|' + (c.message || '');
          return;
        }

        const currentStateKey = (c.status || '') + '|' + (c.message || '') + '|'
          + (c.tasks || []).map((t) => (t.id || '') + ':' + (t.status || '')).join(',');
        const prevStateKey = lastClientState[c.clientId];

        if (prevStateKey !== undefined && prevStateKey !== currentStateKey) {
          const timeStr = new Date().toLocaleTimeString('zh-TW');
          if (c.status === 'CHECKING_IN') {
            appendLog(c.clientId, '[' + timeStr + '] 正在做…');
          } else if (c.message && c.message.trim() !== '') {
            appendLog(c.clientId, '[' + timeStr + '] ' + c.message);
          }
          (c.tasks || []).forEach((t) => {
            if (!t) return;
            appendLog(c.clientId, '[' + timeStr + '] 這筆任務【' + (t.name || t.id) + '】狀態：' + taskStatusLabel(t.status));
          });
        } else if (prevStateKey === undefined) {
          if (c.message && c.message.trim() !== '') {
            const timeStr = new Date().toLocaleTimeString('zh-TW');
            appendLog(c.clientId, '[' + timeStr + '] ' + c.message);
          }
        }
        lastClientState[c.clientId] = currentStateKey;
      });
    }

    function deviceFoldHelpers() {
      return (typeof window !== 'undefined' && window.PunchclockDeviceFold) || {};
    }

    function deviceChevronHtml(expanded) {
      const fn = deviceFoldHelpers().deviceChevronMarkup;
      return typeof fn === 'function' ? fn(expanded) : '';
    }

    function deviceFoldText(expanded) {
      const fn = deviceFoldHelpers().deviceFoldLabel;
      return typeof fn === 'function' ? fn(expanded) : (expanded ? '收合' : '展開');
    }

    function syncDeviceFoldChrome(root, clientId) {
      if (!root) return;
      const expanded = expandedClients.has(clientId);
      const collapsed = !expanded;
      const body = root.querySelector('.device-body');
      const head = root.querySelector('.device-head');
      if (body) body.classList.toggle('collapsed', collapsed);
      root.classList.toggle('device-is-collapsed', collapsed);
      if (head) {
        head.setAttribute('aria-expanded', expanded ? 'true' : 'false');
      }
      const foldHint = root.querySelector('[data-fold-label]');
      if (foldHint) foldHint.textContent = deviceFoldText(expanded);
      const chevronSlot = root.querySelector('.device-chevron-slot');
      if (chevronSlot) chevronSlot.innerHTML = deviceChevronHtml(expanded);
    }

    function toggleCollapse(clientId) {
      if (expandedClients.has(clientId)) {
        expandedClients.delete(clientId);
      } else {
        expandedClients.add(clientId);
      }
      const root = deviceEl(clientId);
      if (!root) {
        renderClients();
        return;
      }
      syncDeviceFoldChrome(root, clientId);
    }

    function connectWebSocket() {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      ws = new WebSocket(protocol + '//' + window.location.host + '/ws/dashboard');

      ws.onopen = () => console.log('Connected to Dashboard WebSocket');

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'STATUS_UPDATE') {
            applyClientSnapshot(data.clients || []);
          } else if (data.type === 'CHECKIN_RESULT') {
            appendLog(data.clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 收到回覆: ' + data.message);
          }
        } catch (e) {
          console.error('WS Parse Error', e);
        }
      };

      ws.onclose = () => setTimeout(connectWebSocket, 3000);
    }

    function formatChartTime(ms) {
      const d = new Date(ms);
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      const hh = String(d.getHours()).padStart(2, '0');
      const mi = String(d.getMinutes()).padStart(2, '0');
      return mm + '/' + dd + ' ' + hh + ':' + mi;
    }

    function buildAppVersionHintHtml(c) {
      if (!c.appVersion) return '';
      const releaseMs = Date.parse(c.appReleaseTime || '');
      if (!Number.isFinite(releaseMs)) {
        return '<span class="app-version-hint" title="舊版 app 未回報版本時間">v' + escapeHtml(c.appVersion) + '</span>';
      }
      const full = new Date(releaseMs).toLocaleString('zh-TW', { hour12: false });
      return '<span class="app-version-hint" title="app 版本時間（最後一次 commit）' + escapeHtml(full) + '">v'
        + escapeHtml(c.appVersion) + ' · ' + formatChartTime(releaseMs) + '</span>';
    }

    async function fetchStatus() {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        applyClientSnapshot(data.clients || []);
      } catch (e) {
        console.error('狀態更新失敗', e);
      }
    }

    function getStatusBadgeHtml(status) {
      if (status === 'SCHEDULED') return '<span class="chip">等待中</span>';
      if (status === 'CHECKING_IN') return '<span class="chip chip-admin">執行中</span>';
      if (status === 'SUCCESS') return '<span class="chip chip-online">成功</span>';
      if (status === 'FAILED') return '<span class="chip chip-offline">失敗</span>';
      if (status === 'CANCELLED') return '<span class="chip">已取消</span>';
      return '<span class="chip">' + (status || '在線') + '</span>';
    }

    function getStatusColor(status) {
      if (status === 'FAILED') return 'var(--bad)';
      if (status === 'SUCCESS') return 'var(--ok)';
      return 'var(--ink-soft)';
    }

    function cleanMessage(msg) {
      if (!msg) return '連線正常';
      const str = String(msg);
      const httpIdx = str.indexOf('http://');
      const httpsIdx = str.indexOf('https://');
      let idx = -1;
      if (httpIdx !== -1 && httpsIdx !== -1) idx = Math.min(httpIdx, httpsIdx);
      else if (httpIdx !== -1) idx = httpIdx;
      else if (httpsIdx !== -1) idx = httpsIdx;
      if (idx !== -1) return str.substring(0, idx) + '[網址已隱蔽]';
      return str;
    }

    function escapeHtml(s) {
      return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
    }

    function buildTaskCardHtml(c, t, isConnected) {
      const statusBadge = buildTaskStatusBadgeHtml(t.status, c.clientId, t.id);
      const cancelBtn = buildCancelButtonHtml(c.clientId, t.id, isConnected, t.status);
      const offsetLabel = t.useRandomOffset ? ' ±' : '';
      const actualTimeStr = (t.actualTime || t.targetTime || '-') + offsetLabel;
      const countdown = countdownLabel(t.status, t.actualTime || t.targetTime);
      const countdownAttr = escapeHtml(t.actualTime || t.targetTime || '');

      return '<article class="task-card" data-task-id="' + escapeHtml(t.id || '') + '">' +
        '<div class="task-card-top">' +
          '<strong class="task-card-name">' + escapeHtml(t.name || '未命名任務') + '</strong>' +
          '<div class="task-card-actions">' +
            '<span class="task-countdown" data-status="' + escapeHtml(t.status || '') + '" data-actual-time="' + countdownAttr + '">' + escapeHtml(countdown) + '</span>' +
            statusBadge + cancelBtn +
          '</div>' +
        '</div>' +
        '<dl class="task-card-fields">' +
          '<div class="task-field"><dt>設定時間</dt><dd class="time-set">' + escapeHtml(t.targetTime || '-') + '</dd></div>' +
          '<div class="task-field"><dt>實際觸發</dt><dd class="time-actual">' + escapeHtml(actualTimeStr) + '</dd></div>' +
          '<div class="task-field"><dt>倒數</dt><dd class="task-countdown" data-status="' + escapeHtml(t.status || '') + '" data-actual-time="' + countdownAttr + '">' + escapeHtml(countdown) + '</dd></div>' +
          '<div class="task-field"><dt>網址</dt><dd class="task-url" title="' + escapeHtml(t.targetUrl || '') + '">' + escapeHtml(t.targetUrl || '-') + '</dd></div>' +
          '<div class="task-field"><dt>Selector</dt><dd class="task-sel">' + escapeHtml(t.buttonId || '-') + '</dd></div>' +
        '</dl>' +
      '</article>';
    }

    function buildTaskListInnerHtml(c, isConnected) {
      const tasks = Array.isArray(c.tasks) ? c.tasks.slice() : [];
      if (tasks.length === 0) {
        return '<p class="empty-tasks">目前沒有排程任務</p>';
      }
      tasks.sort((a, b) => (a.actualTime || a.targetTime || '').localeCompare(b.actualTime || b.targetTime || ''));
      let cardsHtml = '';
      for (let j = 0; j < tasks.length; j++) {
        cardsHtml += buildTaskCardHtml(c, tasks[j], isConnected);
      }
      return cardsHtml;
    }

    function patchTaskCard(card, c, t, isConnected) {
      if (isTerminalTaskStatus(t.status) || isRemoteCancelMessage(t.message)) {
        clearTaskCancelPending(c.clientId, t.id);
      }

      const offsetLabel = t.useRandomOffset ? ' ±' : '';
      const actualTimeStr = (t.actualTime || t.targetTime || '-') + offsetLabel;
      const countdown = countdownLabel(t.status, t.actualTime || t.targetTime);
      const countdownAttr = t.actualTime || t.targetTime || '';

      const nameEl = card.querySelector('.task-card-name');
      if (nameEl) nameEl.textContent = t.name || '未命名任務';

      const actions = card.querySelector('.task-card-actions');
      if (actions) {
        actions.innerHTML =
          '<span class="task-countdown" data-status="' + escapeHtml(t.status || '') + '" data-actual-time="' + escapeHtml(countdownAttr) + '">' + escapeHtml(countdown) + '</span>' +
          buildTaskStatusBadgeHtml(t.status, c.clientId, t.id) +
          buildCancelButtonHtml(c.clientId, t.id, isConnected, t.status);
      }

      const timeSet = card.querySelector('.time-set');
      if (timeSet) timeSet.textContent = t.targetTime || '-';
      const timeActual = card.querySelector('.time-actual');
      if (timeActual) timeActual.textContent = actualTimeStr;
      const urlEl = card.querySelector('.task-url');
      if (urlEl) {
        urlEl.textContent = t.targetUrl || '-';
        urlEl.title = t.targetUrl || '';
      }
      const selEl = card.querySelector('.task-sel');
      if (selEl) selEl.textContent = t.buttonId || '-';

      const countdownField = card.querySelector('.task-card-fields .task-countdown');
      if (countdownField) {
        countdownField.setAttribute('data-status', t.status || '');
        countdownField.setAttribute('data-actual-time', countdownAttr);
        countdownField.textContent = countdown;
      }
    }

    function patchTaskListHost(c, isConnected, host) {
      const tasks = Array.isArray(c.tasks) ? c.tasks.slice() : [];
      if (tasks.length === 0) {
        host.removeAttribute('data-task-ids');
        host.innerHTML = '<p class="empty-tasks">目前沒有排程任務</p>';
        return;
      }
      tasks.sort((a, b) => (a.actualTime || a.targetTime || '').localeCompare(b.actualTime || b.targetTime || ''));
      const ids = tasks.map((t) => t.id).join('|');
      if (host.getAttribute('data-task-ids') === ids && host.querySelector('.task-card')) {
        for (let j = 0; j < tasks.length; j++) {
          const card = host.querySelector('.task-card[data-task-id="' + tasks[j].id + '"]');
          if (card) patchTaskCard(card, c, tasks[j], isConnected);
        }
        return;
      }
      host.setAttribute('data-task-ids', ids);
      host.innerHTML = buildTaskListInnerHtml(c, isConnected);
    }

    function renderClients() {
      const container = document.getElementById('clientContainer');
      if (!clientData || clientData.length === 0) {
        container.innerHTML = '<div class="empty-state"><p>還沒有設備連上來</p><p>請打開桌面端，並勾選雲端狀態回報</p></div>';
        return;
      }

      let html = '';
      for (let i = 0; i < clientData.length; i++) {
        const c = clientData[i];
        const isConnected = c.status !== 'OFFLINE';
        const isCollapsed = !expandedClients.has(c.clientId);
        const tasks = Array.isArray(c.tasks) ? c.tasks.slice() : [];

        const badgeClass = buildDeviceHeadChipClass(isConnected, tasks);
        const transportLabel = c.transport === 'http' ? 'HTTP' : (c.transport || '-');
        const badgeText = buildDeviceHeadBadgeText(isConnected, transportLabel, tasks);

        const logLines = visibleLogLines(c);
        const logContent = logLines.length > 0
          ? logLines.map(escapeHtml).join('<br>')
          : ('[' + new Date().toLocaleTimeString('zh-TW') + '] 系統連線就緒');

        const tasksInnerHtml = buildTaskListInnerHtml(c, isConnected);

        const deleteBtnHtml = isConnected ? '' : (
          '<button class="btn btn-ghost-danger" onclick="deleteClient(\'' + c.clientId + '\')" title="移除離線設備紀錄">移除</button>'
        );
        const cancelAllBtnHtml = buildCancelAllButtonHtml(c.clientId, isConnected, tasks);
        const foldText = deviceFoldText(!isCollapsed);
        const bodyClass = isCollapsed ? 'collapsed' : '';
        const bodyDomId = 'device-body-' + escapeHtml(c.clientId).replace(/[^a-zA-Z0-9_-]/g, '_');
        const headDomId = 'device-head-' + escapeHtml(c.clientId).replace(/[^a-zA-Z0-9_-]/g, '_');
        const ariaExpanded = isCollapsed ? 'false' : 'true';
        const collapsedSectionClass = isCollapsed ? ' device-is-collapsed' : '';
        const statusColor = getStatusColor(c.status);
        const safeMsg = escapeHtml(cleanMessage(c.message));
        const liveDot = isConnected ? '<span class="dot dot-live"></span>' : '<span class="dot"></span>';
        const chevronHtml = deviceChevronHtml(!isCollapsed);

        html += '<section class="device' + collapsedSectionClass + '" data-client-id="' + escapeHtml(c.clientId) + '">' +
          '<div class="device-head" id="' + headDomId + '" role="button" tabindex="0"' +
            ' aria-expanded="' + ariaExpanded + '" aria-controls="' + bodyDomId + '">' +
            '<div class="device-id">' +
              '<span class="device-chevron-slot" aria-hidden="true">' + chevronHtml + '</span>' +
              '<strong>' + escapeHtml(c.clientId) + '</strong>' +
              buildAppVersionHintHtml(c) +
              '<span class="fold-hint" data-fold-label>' + foldText + '</span>' +
            '</div>' +
            '<div class="device-head-right">' +
              '<span class="chip ' + badgeClass + '">' + liveDot + badgeText + '</span>' +
              deleteBtnHtml +
            '</div>' +
          '</div>' +
          '<div class="device-body ' + bodyClass + '" id="' + bodyDomId + '">' +
            '<div class="metrics">' +
              '<div class="metric"><span class="metric-label">最後心跳</span><span class="metric-value heartbeat-value" data-client-id="' + escapeHtml(c.clientId) + '">' + formatHeartbeat(c.lastSeen) + '</span></div>' +
              '<div class="metric"><span class="metric-label">待辦</span><span class="metric-value" data-role="task-count">' + tasks.length + ' 筆</span></div>' +
              '<div class="metric"><span class="metric-label">最新動態</span><span class="metric-value" data-role="latest-message" style="color:' + statusColor + ';font-size:0.88rem;">' + safeMsg + '</span></div>' +
            '</div>' +
            '<div>' +
              '<div class="section-label">任務</div>' +
              '<div class="task-list" data-role="task-list" data-task-ids="' + escapeHtml(tasks.map((t) => t.id).join('|')) + '">' + tasksInnerHtml + '</div>' +
              '<div class="action-row">' +
                cancelAllBtnHtml +
              '</div>' +
            '</div>' +
            '<div>' +
              '<div class="log-head">' +
                '<div class="section-label" style="margin:0">設備紀錄</div>' +
                '<button class="btn btn-ghost" onclick="clearClientLog(\'' + c.clientId + '\')">清空</button>' +
              '</div>' +
              '<div class="log-box" id="log-' + c.clientId + '">' + logContent + '</div>' +
            '</div>' +
          '</div>' +
        '</section>';
      }

      container.innerHTML = html;

      for (let i = 0; i < clientData.length; i++) {
        lastLogFingerprints[clientData[i].clientId] = visibleLogLines(clientData[i]).join('\n');
        const box = document.getElementById('log-' + clientData[i].clientId);
        if (box) box.scrollTop = box.scrollHeight;
      }
    }

    function clearClientLog(clientId) {
      clientLogs[clientId] = [];
      const c = clientData.find((x) => x.clientId === clientId);
      logSkipCount[clientId] = (c && Array.isArray(c.eventLog)) ? c.eventLog.length : 0;
      lastLogFingerprints[clientId] = '[' + new Date().toLocaleTimeString('zh-TW') + '] 紀錄已清空';
      const box = document.getElementById('log-' + clientId);
      if (box) {
        box.innerHTML = escapeHtml(lastLogFingerprints[clientId]);
      }
    }

    function deleteClient(clientId) {
      if (confirm('確定要把【' + clientId + '】從設備名單移除嗎？')) {
        fetch('/api/clients/' + encodeURIComponent(clientId), { method: 'DELETE' })
          .then(res => res.json())
          .then(data => { if (!data.success) alert(data.message || '刪除失敗'); })
          .catch(err => console.error(err));
      }
    }

    function remoteCancelTask(clientId, taskId) {
      if (isTaskCancelPending(clientId, taskId)) return;
      if (!confirm('確定要取消【' + clientId + '】的這筆任務【' + taskId + '】嗎？')) return;

      markTaskCancelPending(clientId, taskId);
      refreshTaskCancelUi(clientId, taskId);
      appendLog(clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 正在取消這筆任務 (' + taskId + ')…');
      fetch('/api/clients/' + encodeURIComponent(clientId) + '/cancel-task/' + encodeURIComponent(taskId), { method: 'POST' })
        .then(res => res.json())
        .then(data => {
          if (data.success) {
            appendLog(clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 已送出取消囉，等對方收到就會處理');
          } else {
            clearTaskCancelPending(clientId, taskId);
            refreshTaskCancelUi(clientId, taskId);
            alert(data.message || '這筆任務沒取消成');
          }
        }).catch(err => {
          clearTaskCancelPending(clientId, taskId);
          refreshTaskCancelUi(clientId, taskId);
          console.error(err);
        });
    }

    async function remoteCancelSchedule(clientId) {
      if (pendingCancelAllClients.has(clientId)) return;
      const c = clientData.find((x) => x.clientId === clientId);
      const tasks = c && Array.isArray(c.tasks) ? c.tasks : [];
      if (!hasCancellableTasks(tasks)) return;
      if (!confirm('確定要取消【' + clientId + '】的全部任務嗎？')) return;

      markCancelAllPending(clientId);
      refreshCancelAllUi(clientId);
      appendLog(clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 正在取消全部任務…');
      try {
        const res = await fetch('/api/clients/' + encodeURIComponent(clientId) + '/cancel-schedule', { method: 'POST' });
        const data = await res.json();
        if (data.success) {
          appendLog(clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 已送出取消囉，等對方收到就會全部取消');
        } else {
          clearCancelAllPending(clientId);
          refreshCancelAllUi(clientId);
          alert(data.message || '指令發送失敗');
        }
      } catch (e) {
        clearCancelAllPending(clientId);
        refreshCancelAllUi(clientId);
        console.error('Remote cancel error', e);
      }
    }

    function appendLog(clientId, text) {
      if (!clientLogs[clientId]) clientLogs[clientId] = [];
      clientLogs[clientId].push(text);
      const c = clientData.find((x) => x.clientId === clientId);
      if (c) {
        updateClientLogBox(c);
        return;
      }
      const box = document.getElementById('log-' + clientId);
      if (box) {
        box.innerHTML = clientLogs[clientId].map(escapeHtml).join('<br>');
        box.scrollTop = box.scrollHeight;
      }
    }

    const CONSOLE_ZONES = ['devices'];
    const isAuthenticated = document.body.classList.contains('is-authenticated')
      || window.PUNCHCLOCK_AUTHENTICATED === true;
    let suppressConsoleHashWrite = false;
    let currentConsoleTab = 'devices';

    function onConsolePage() {
      try {
        const path = window.location.pathname || '';
        return path === '/console' || path.endsWith('/console');
      } catch (e) {
        return false;
      }
    }

    function zoneLabel(tabId) {
      const panel = document.getElementById('panel-' + tabId);
      return panel ? (panel.getAttribute('data-label') || tabId) : tabId;
    }

    function setActiveConsoleChrome(next, opts) {
      const roadmapOpen = !!(opts && opts.roadmapOpen);
      currentConsoleTab = next || 'devices';
      document.querySelectorAll('.console-tab[data-tab]').forEach(btn => {
        const active = !roadmapOpen && btn.getAttribute('data-tab') === next;
        btn.classList.toggle('is-active', active);
        btn.setAttribute('aria-pressed', active ? 'true' : 'false');
      });
      document.querySelectorAll('.console-tab[data-open-roadmap]').forEach(btn => {
        btn.classList.toggle('is-active', roadmapOpen);
        btn.setAttribute('aria-pressed', roadmapOpen ? 'true' : 'false');
      });
      document.body.classList.toggle('console-sheet-open', !!next);
    }

    window.PunchclockConsoleChrome = {
      setRoadmapOpen: (open) => setActiveConsoleChrome(currentConsoleTab || 'devices', { roadmapOpen: !!open })
    };

    function writeConsoleUrlForTab(tabId) {
      if (!isAuthenticated || suppressConsoleHashWrite || !onConsolePage()) return;
      try {
        const path = window.location.pathname || '/console';
        const hash = tabId ? '#' + tabId : '';
        const search = window.location.search || '';
        const next = path + search + hash;
        if (window.location.pathname + window.location.search + window.location.hash !== next) {
          window.history.replaceState(null, '', next);
        }
      } catch (e) {}
    }

    function showGuestLoginGate(tabId) {
      const panels = document.querySelectorAll('.dash-panel');
      panels.forEach(panel => {
        const active = panel.id === 'panel-guest-gate';
        panel.classList.toggle('is-active', active);
        if (active) {
          panel.removeAttribute('hidden');
        } else {
          panel.setAttribute('hidden', '');
        }
      });
      const sheet = document.getElementById('consoleSheet');
      const title = document.getElementById('consoleSheetTitle');
      const hint = document.getElementById('consoleGuestHint');
      const label = zoneLabel(tabId);
      if (sheet) sheet.hidden = false;
      if (title) title.textContent = label;
      if (hint) hint.textContent = '登入後可操作「' + label + '」。';
      setActiveConsoleChrome(tabId);
    }

    function switchDashTab(tabId) {
      const panels = document.querySelectorAll('.dash-panel');
      if (!panels.length) return;
      const next = CONSOLE_ZONES.indexOf(tabId) >= 0 ? tabId : '';
      if (!isAuthenticated && next) {
        showGuestLoginGate(next);
        return;
      }
      panels.forEach(panel => {
        const active = next !== '' && panel.id === 'panel-' + next;
        panel.classList.toggle('is-active', active);
        if (active) {
          panel.removeAttribute('hidden');
        } else {
          panel.setAttribute('hidden', '');
        }
      });
      const sheet = document.getElementById('consoleSheet');
      const title = document.getElementById('consoleSheetTitle');
      if (sheet) {
        if (next) {
          sheet.hidden = false;
          if (title) title.textContent = zoneLabel(next);
        } else {
          sheet.hidden = true;
        }
      }
      try {
        if (isAuthenticated) {
          window.sessionStorage.setItem('punchclock.dashTab', next);
        }
      } catch (e) {}
      const roadmapApi = window.PunchclockConsoleRoadmap;
      const roadmapOpen = !!(roadmapApi && typeof roadmapApi.isOpen === 'function' && roadmapApi.isOpen());
      setActiveConsoleChrome(next, { roadmapOpen: roadmapOpen });
      if (isAuthenticated && next) {
        writeConsoleUrlForTab(next);
      }
    }

    function readSavedDashTab() {
      try {
        return window.sessionStorage.getItem('punchclock.dashTab');
      } catch (e) {
        return null;
      }
    }

    function readDashTabHash() {
      try {
        return (window.location && window.location.hash) || '';
      } catch (e) {
        return '';
      }
    }

    function forgetRemovedDashHash() {
      const api = window.PunchclockDashTab;
      if (!api || typeof api.isRemovedDashTab !== 'function' || !api.isRemovedDashTab(readDashTabHash())) {
        return;
      }
      try {
        window.history.replaceState(null, '', window.location.pathname + window.location.search);
      } catch (e) {}
    }

    function initialAuthenticatedDashTab() {
      const api = window.PunchclockDashTab;
      const resolve = api && api.resolveInitialDashTab;
      if (typeof resolve !== 'function') return 'devices';
      try {
        const next = resolve(readSavedDashTab(), readDashTabHash());
        return CONSOLE_ZONES.indexOf(next) >= 0 ? next : 'devices';
      } catch (e) {
        return 'devices';
      }
    }

    function bindDashTabs() {
      document.querySelectorAll('[data-tab]').forEach(el => {
        el.addEventListener('click', () => {
          if (window.PunchclockConsoleRoadmap && typeof window.PunchclockConsoleRoadmap.close === 'function') {
            window.PunchclockConsoleRoadmap.close();
          }
          switchDashTab(el.getAttribute('data-tab'));
        });
      });
      const initial = isAuthenticated ? initialAuthenticatedDashTab() : 'devices';
      if (isAuthenticated) forgetRemovedDashHash();
      switchDashTab(initial);
    }

    function bindClientContainerFold() {
      const container = document.getElementById('clientContainer');
      if (!container || container.getAttribute('data-fold-bound') === 'yes') return;
      container.setAttribute('data-fold-bound', 'yes');
      const shouldToggleKey = (key) => {
        const fn = deviceFoldHelpers().shouldToggleDeviceFold;
        if (typeof fn === 'function') return fn(key);
        return key === 'Enter' || key === ' ';
      };
      container.addEventListener('click', (ev) => {
        const device = ev.target.closest('.device');
        if (!device) return;
        if (ev.target.closest('.device-head-right button')) return;
        if (ev.target.closest('.device-body button, .device-body a, .device-body input, .log-box, .task-list')) return;
        const clientId = device.getAttribute('data-client-id');
        if (!clientId) return;
        toggleCollapse(clientId);
      });
      container.addEventListener('keydown', (ev) => {
        const head = ev.target.closest('.device-head');
        if (!head || !shouldToggleKey(ev.key)) return;
        ev.preventDefault();
        const device = head.closest('.device');
        const clientId = device && device.getAttribute('data-client-id');
        if (clientId) toggleCollapse(clientId);
      });
    }

    function bindConsoleHashNav() {
      if (!isAuthenticated || !onConsolePage()) return;
      window.addEventListener('hashchange', () => {
        const api = window.PunchclockDashTab;
        if (!api || typeof api.resolveInitialDashTab !== 'function') return;
        suppressConsoleHashWrite = true;
        try {
          const tab = api.resolveInitialDashTab(readSavedDashTab(), readDashTabHash());
          if (tab && tab !== currentConsoleTab) {
            switchDashTab(tab);
          }
        } finally {
          suppressConsoleHashWrite = false;
        }
      });
    }

    bindDashTabs();
    bindClientContainerFold();
    bindConsoleHashNav();
    if (isAuthenticated) {
      fetchStatus();
      connectWebSocket();
      setInterval(() => {
        updateHeartbeatMetrics();
        updateCountdowns();
      }, 1000);
      setInterval(async () => {
        if (!ws || ws.readyState !== WebSocket.OPEN) {
          try {
            const res = await fetch('/api/status');
            const data = await res.json();
            applyClientSnapshot(data.clients || []);
          } catch (e) {
            console.error('狀態更新失敗', e);
          }
        }
      }, 5000);
    }
