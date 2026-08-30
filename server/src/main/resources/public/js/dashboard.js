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

    function cancelPendingKey(clientId, taskId) {
      return clientId + '|' + taskId;
    }

    function isTaskCancelPending(clientId, taskId) {
      return pendingTaskCancels.has(cancelPendingKey(clientId, taskId))
        || pendingCancelAllClients.has(clientId);
    }

    function markTaskCancelPending(clientId, taskId) {
      pendingTaskCancels.add(cancelPendingKey(clientId, taskId));
    }

    function clearTaskCancelPending(clientId, taskId) {
      pendingTaskCancels.delete(cancelPendingKey(clientId, taskId));
    }

    function markCancelAllPending(clientId) {
      pendingCancelAllClients.add(clientId);
    }

    function clearCancelAllPending(clientId) {
      pendingCancelAllClients.delete(clientId);
    }

    function syncCancelPendingFromTasks(clientId, tasks) {
      for (let i = 0; i < (tasks || []).length; i++) {
        const t = tasks[i];
        if (t.status !== 'SCHEDULED') {
          clearTaskCancelPending(clientId, t.id);
        }
      }
      const hasActive = (tasks || []).some((t) => t.status === 'SCHEDULED' || t.status === 'CHECKING_IN');
      if (!hasActive) {
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
      if (isTaskCancelPending(clientId, taskId)) {
        return '<button class="btn btn-ghost-danger btn-pending" disabled title="已送出取消指令，等候桌面端心跳確認">取消中…</button>';
      }
      return '<button class="btn btn-ghost-danger" onclick="remoteCancelTask(\'' + clientId + '\', \'' + taskId + '\')">取消</button>';
    }

    function hasCancellableTasks(tasks) {
      return (tasks || []).some((t) => t.status === 'SCHEDULED' || t.status === 'CHECKING_IN');
    }

    function buildCancelAllButtonHtml(clientId, isConnected, tasks) {
      if (pendingCancelAllClients.has(clientId)) {
        return '<button class="btn btn-danger btn-pending" data-role="cancel-all" disabled title="已送出取消全部指令，等候桌面端心跳確認">取消中…</button>';
      }
      const canCancel = isConnected && hasCancellableTasks(tasks);
      const disabled = canCancel ? '' : 'disabled';
      const title = canCancel ? '' : ' title="目前沒有等待中或執行中的任務可取消"';
      return '<button class="btn btn-danger" data-role="cancel-all" onclick="remoteCancelSchedule(\'' + clientId + '\')"' + title + ' ' + disabled + '>取消全部任務</button>';
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
        actionRow.innerHTML = buildCancelAllButtonHtml(clientId, c.status !== 'OFFLINE', tasks);
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

    /** 取消全部等待中：若桌面端已重新排程則解除；不再把 SCHEDULED 強制改為已取消 */
    function reconcileCancelAllClientState(c) {
      if (!c || !pendingCancelAllClients.has(c.clientId)) return;
      const tasks = Array.isArray(c.tasks) ? c.tasks : [];
      const hasActive = tasks.some((t) => t.status === 'SCHEDULED' || t.status === 'CHECKING_IN');
      if (hasActive) {
        clearCancelAllPending(c.clientId);
        for (let i = 0; i < tasks.length; i++) {
          clearTaskCancelPending(c.clientId, tasks[i].id);
        }
        return;
      }
      const cancelAllAck = tasks.some((t) => t.status === 'CANCELLED' && isCancelAllAckMessage(t.message));
      if (!cancelAllAck) return;
      clearCancelAllPending(c.clientId);
      for (let i = 0; i < tasks.length; i++) {
        clearTaskCancelPending(c.clientId, tasks[i].id);
      }
    }

    /** 裝置 / 任務 id 變了才需要整頁重建 */
    function clientsStructureFingerprint(clients) {
      return JSON.stringify((clients || []).map((c) => ({
        id: c.clientId,
        offline: c.status === 'OFFLINE',
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
      const badgeText = isConnected
        ? ('在線 · ' + transportLabel + ' · ' + tasks.length + ' 任務')
        : '離線';

      const headChip = root.querySelector('.device-head-right .chip');
      if (headChip) {
        headChip.className = 'chip ' + (isConnected ? 'chip-online' : 'chip-offline');
        headChip.innerHTML = (isConnected ? '<span class="dot dot-live"></span>' : '<span class="dot"></span>') + badgeText;
      }

      const foldHints = root.querySelectorAll('.device-id .fold-hint');
      const foldHint = foldHints.length ? foldHints[foldHints.length - 1] : null;
      if (foldHint) {
        foldHint.textContent = expandedClients.has(c.clientId) ? '收合' : '展開';
      }

      const taskCountEl = root.querySelector('.metric-value[data-role="task-count"]');
      if (taskCountEl) taskCountEl.textContent = tasks.length + ' 筆';

      const msgEl = root.querySelector('.metric-value[data-role="latest-message"]');
      if (msgEl) {
        msgEl.textContent = cleanMessage(c.message);
        msgEl.style.color = getStatusColor(c.status);
      }

      const taskListHost = root.querySelector('[data-role="task-list"]');
      if (taskListHost) {
        patchTaskListHost(c, isConnected, taskListHost);
      }

      const cancelAllBtn = root.querySelector('[data-role="cancel-all"]');
      const actionRow = root.querySelector('.action-row');
      if (actionRow) {
        actionRow.innerHTML = buildCancelAllButtonHtml(c.clientId, isConnected, tasks);
      } else if (cancelAllBtn) {
        cancelAllBtn.disabled = !isConnected || tasks.length === 0;
      }

      syncCancelPendingFromTasks(c.clientId, tasks);

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
            appendLog(c.clientId, '[' + timeStr + '] 正在執行自動打卡…');
          } else if (c.message && c.message.trim() !== '') {
            appendLog(c.clientId, '[' + timeStr + '] ' + c.message);
          }
          (c.tasks || []).forEach((t) => {
            if (!t) return;
            appendLog(c.clientId, '[' + timeStr + '] 任務【' + (t.name || t.id) + '】目前：' + taskStatusLabel(t.status));
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
      const body = root.querySelector('.device-body');
      const hints = root.querySelectorAll('.device-id .fold-hint');
      const foldHint = hints.length ? hints[hints.length - 1] : null;
      const collapsed = !expandedClients.has(clientId);
      if (body) body.classList.toggle('collapsed', collapsed);
      if (foldHint) foldHint.textContent = collapsed ? '展開' : '收合';
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

    async function fetchStatus() {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        applyClientSnapshot(data.clients || []);
      } catch (e) {}
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
          '<strong class="task-card-name">' + escapeHtml(t.name || '打卡任務') + '</strong>' +
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
        return '<p class="empty-tasks">目前無排定任務，請在本機桌面端設定</p>';
      }
      tasks.sort((a, b) => (a.actualTime || a.targetTime || '').localeCompare(b.actualTime || b.targetTime || ''));
      let cardsHtml = '';
      for (let j = 0; j < tasks.length; j++) {
        cardsHtml += buildTaskCardHtml(c, tasks[j], isConnected);
      }
      return cardsHtml;
    }

    function patchTaskCard(card, c, t, isConnected) {
      if (t.status !== 'SCHEDULED') {
        clearTaskCancelPending(c.clientId, t.id);
      }

      const offsetLabel = t.useRandomOffset ? ' ±' : '';
      const actualTimeStr = (t.actualTime || t.targetTime || '-') + offsetLabel;
      const countdown = countdownLabel(t.status, t.actualTime || t.targetTime);
      const countdownAttr = t.actualTime || t.targetTime || '';

      const nameEl = card.querySelector('.task-card-name');
      if (nameEl) nameEl.textContent = t.name || '打卡任務';

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
        host.innerHTML = '<p class="empty-tasks">目前無排定任務，請在本機桌面端設定</p>';
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
        container.innerHTML = '<div class="empty-state"><p>尚無打卡裝置連線</p><p>請啟動桌面端並啟用雲端狀態回報</p></div>';
        return;
      }

      let html = '';
      for (let i = 0; i < clientData.length; i++) {
        const c = clientData[i];
        const isConnected = c.status !== 'OFFLINE';
        const isCollapsed = !expandedClients.has(c.clientId);
        const tasks = Array.isArray(c.tasks) ? c.tasks.slice() : [];

        const badgeClass = isConnected ? 'chip-online' : 'chip-offline';
        const transportLabel = c.transport === 'http' ? 'HTTP' : (c.transport || '-');
        const badgeText = isConnected
          ? ('在線 · ' + transportLabel + ' · ' + tasks.length + ' 任務')
          : '離線';

        const logLines = visibleLogLines(c);
        const logContent = logLines.length > 0
          ? logLines.map(escapeHtml).join('<br>')
          : ('[' + new Date().toLocaleTimeString('zh-TW') + '] 系統連線就緒');

        const tasksInnerHtml = buildTaskListInnerHtml(c, isConnected);

        const deleteBtnHtml = isConnected ? '' : (
          '<button class="btn btn-ghost-danger" onclick="deleteClient(\'' + c.clientId + '\')" title="移除離線設備紀錄">移除</button>'
        );
        const cancelAllBtnHtml = buildCancelAllButtonHtml(c.clientId, isConnected, tasks);
        const foldText = isCollapsed ? '展開' : '收合';
        const bodyClass = isCollapsed ? 'collapsed' : '';
        const statusColor = getStatusColor(c.status);
        const safeMsg = escapeHtml(cleanMessage(c.message));
        const liveDot = isConnected ? '<span class="dot dot-live"></span>' : '<span class="dot"></span>';

        html += '<section class="device" data-client-id="' + escapeHtml(c.clientId) + '">' +
          '<div class="device-head" onclick="toggleCollapse(\'' + c.clientId + '\')">' +
            '<div class="device-id">' +
              '<strong>' + escapeHtml(c.clientId) + '</strong>' +
              (c.appVersion ? '<span class="fold-hint">v' + escapeHtml(c.appVersion) + '</span>' : '') +
              '<span class="fold-hint">' + foldText + '</span>' +
            '</div>' +
            '<div class="device-head-right" onclick="event.stopPropagation()">' +
              '<span class="chip ' + badgeClass + '">' + liveDot + badgeText + '</span>' +
              deleteBtnHtml +
            '</div>' +
          '</div>' +
          '<div class="device-body ' + bodyClass + '">' +
            '<div class="metrics">' +
              '<div class="metric"><span class="metric-label">最後心跳</span><span class="metric-value heartbeat-value" data-client-id="' + escapeHtml(c.clientId) + '">' + formatHeartbeat(c.lastSeen) + '</span></div>' +
              '<div class="metric"><span class="metric-label">排定任務</span><span class="metric-value" data-role="task-count">' + tasks.length + ' 筆</span></div>' +
              '<div class="metric"><span class="metric-label">最新訊息</span><span class="metric-value" data-role="latest-message" style="color:' + statusColor + ';font-size:0.88rem;">' + safeMsg + '</span></div>' +
            '</div>' +
            '<div>' +
              '<div class="section-label">任務清單</div>' +
              '<div class="task-list" data-role="task-list" data-task-ids="' + escapeHtml(tasks.map((t) => t.id).join('|')) + '">' + tasksInnerHtml + '</div>' +
              '<div class="action-row">' +
                cancelAllBtnHtml +
              '</div>' +
            '</div>' +
            '<div>' +
              '<div class="log-head">' +
                '<div class="section-label" style="margin:0">系統日誌</div>' +
                '<button class="btn btn-ghost" onclick="clearClientLog(\'' + c.clientId + '\')">清除</button>' +
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
      lastLogFingerprints[clientId] = '[' + new Date().toLocaleTimeString('zh-TW') + '] 日誌已清除';
      const box = document.getElementById('log-' + clientId);
      if (box) {
        box.innerHTML = escapeHtml(lastLogFingerprints[clientId]);
      }
    }

    function deleteClient(clientId) {
      if (confirm('確定要移除設備【' + clientId + '】的紀錄嗎？')) {
        fetch('/api/clients/' + encodeURIComponent(clientId), { method: 'DELETE' })
          .then(res => res.json())
          .then(data => { if (!data.success) alert(data.message || '刪除失敗'); })
          .catch(err => console.error(err));
      }
    }

    function remoteCancelTask(clientId, taskId) {
      if (!confirm('確定要取消設備【' + clientId + '】的任務【' + taskId + '】嗎？')) return;
      if (isTaskCancelPending(clientId, taskId)) return;

      markTaskCancelPending(clientId, taskId);
      refreshTaskCancelUi(clientId, taskId);
      appendLog(clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 正在發送取消任務指令 (' + taskId + ')…');
      fetch('/api/clients/' + encodeURIComponent(clientId) + '/cancel-task/' + encodeURIComponent(taskId), { method: 'POST' })
        .then(res => res.json())
        .then(data => {
          if (data.success) {
            appendLog(clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 已送出取消請求，等候桌面端心跳確認（按鈕已顯示「取消中…」）');
          } else {
            clearTaskCancelPending(clientId, taskId);
            refreshTaskCancelUi(clientId, taskId);
            alert(data.message || '取消任務失敗');
          }
        }).catch(err => {
          clearTaskCancelPending(clientId, taskId);
          refreshTaskCancelUi(clientId, taskId);
          console.error(err);
        });
    }

    async function remoteCancelSchedule(clientId) {
      const c = clientData.find((x) => x.clientId === clientId);
      const tasks = c && Array.isArray(c.tasks) ? c.tasks : [];
      if (!hasCancellableTasks(tasks)) return;
      if (!confirm('確定要對設備【' + clientId + '】發送【取消全部排程】嗎？')) return;
      if (pendingCancelAllClients.has(clientId)) return;

      markCancelAllPending(clientId);
      refreshCancelAllUi(clientId);
      appendLog(clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 正在發送取消全部排程…');
      try {
        const res = await fetch('/api/clients/' + encodeURIComponent(clientId) + '/cancel-schedule', { method: 'POST' });
        const data = await res.json();
        if (data.success) {
          appendLog(clientId, '[' + new Date().toLocaleTimeString('zh-TW') + '] 已送出取消全部請求，等候桌面端心跳確認（按鈕已顯示「取消中…」）');
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

    function toSpeakableEnglish(line) {
      if (!line) return '';
      let text = String(line).trim();
      const colon = text.indexOf(':');
      if (colon >= 0 && colon < text.length - 1) {
        text = text.slice(colon + 1).trim();
      }
      if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) {
        text = text.slice(1, -1);
      }
      return text.trim();
    }

    function speakDailyProverb() {
      const quote = document.querySelector('.proverb-en');
      const text = toSpeakableEnglish(quote ? quote.textContent : '');
      if (!text) return;
      if (!('speechSynthesis' in window)) {
        window.alert('此瀏覽器不支援語音合成');
        return;
      }
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'en-US';
      utterance.rate = 0.95;
      window.speechSynthesis.speak(utterance);
    }

    const speakBtn = document.getElementById('speakProverbBtn');
    if (speakBtn) {
      speakBtn.addEventListener('click', speakDailyProverb);
    }

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
        } catch (e) {}
      }
    }, 5000);
