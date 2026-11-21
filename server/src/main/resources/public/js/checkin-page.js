// 打卡鐘頁：訪客看公開摘要，管理員可載入詳情。

const lead = document.querySelector('[data-checkin-lead]');
const summary = document.querySelector('[data-checkin-summary]');
const detailHint = document.querySelector('[data-checkin-detail-hint]');

function statusClass(label) {
  const s = String(label || '');
  if (s === '成功') return 'is-ok';
  if (s === '失敗') return 'is-bad';
  if (s === '執行中') return 'is-run';
  return '';
}

function renderTasks(tasks) {
  if (!summary) return;
  summary.replaceChildren();
  const list = Array.isArray(tasks) ? tasks : [];
  if (!list.length) {
    const p = document.createElement('p');
    p.className = 'checkin-empty';
    p.textContent = '目前沒有回報中的排程任務。';
    summary.appendChild(p);
    summary.hidden = false;
    return;
  }
  const table = document.createElement('table');
  table.className = 'checkin-table';
  const thead = document.createElement('thead');
  thead.innerHTML = '<tr><th scope="col">任務</th><th scope="col">狀態</th><th scope="col">時間</th></tr>';
  table.appendChild(thead);
  const tbody = document.createElement('tbody');
  for (let i = 0; i < list.length; i++) {
    const row = list[i] || {};
    const tr = document.createElement('tr');
    const name = document.createElement('td');
    name.textContent = row.name || '未命名任務';
    const st = document.createElement('td');
    const span = document.createElement('span');
    span.className = 'checkin-status ' + statusClass(row.status);
    span.textContent = row.status || '未知';
    st.appendChild(span);
    const tm = document.createElement('td');
    tm.textContent = row.time || '—';
    tr.appendChild(name);
    tr.appendChild(st);
    tr.appendChild(tm);
    tbody.appendChild(tr);
  }
  table.appendChild(tbody);
  summary.appendChild(table);
  summary.hidden = false;
}

function loadPublic() {
  return fetch('/api/office/checkin/public')
    .then((r) => (r.ok ? r.json() : null))
    .catch(() => null);
}

function loadDetail() {
  return fetch('/api/office/checkin')
    .then((r) => (r.ok ? r.json() : null))
    .catch(() => null);
}

function flattenDetail(clients) {
  const byName = new Map();
  const list = Array.isArray(clients) ? clients : [];
  for (let c = 0; c < list.length; c++) {
    const client = list[c];
    const tasks = client && Array.isArray(client.tasks) ? client.tasks : [];
    for (let t = 0; t < tasks.length; t++) {
      const task = tasks[t] || {};
      const name = task.name || '未命名任務';
      let status = task.status || '';
      const last = task.lastResultStatus;
      if (last === 'SUCCESS' || last === 'FAILED') {
        status = last;
      }
      const labels = {
        PENDING: '待命中',
        SCHEDULED: '等待中',
        CHECKING_IN: '執行中',
        SUCCESS: '成功',
        FAILED: '失敗',
        CANCELLED: '已取消'
      };
      const label = labels[status] || status || '未知';
      if (!byName.has(name)) {
        byName.set(name, {
          name,
          status: label,
          time: task.actualTime ? String(task.actualTime).slice(11, 16) : (task.targetTime ? String(task.targetTime).slice(11, 16) : '')
        });
      }
    }
  }
  return Array.from(byName.values()).sort((a, b) => a.name.localeCompare(b.name, 'zh-Hant'));
}

async function boot() {
  const authed = window.PUNCHCLOCK_AUTHENTICATED === true;
  const pub = await loadPublic();
  if (!pub || pub.success === false) {
    if (lead) lead.textContent = '暫時無法讀取打卡狀態，請稍後再試。';
    return;
  }
  const date = pub.date || '';
  const online = Number(pub.onlineDevices) || 0;
  if (lead) {
    lead.textContent = date
      ? date + ' · 目前有 ' + online + ' 台桌面端在線'
      : '目前有 ' + online + ' 台桌面端在線';
  }
  renderTasks(pub.tasks);
  if (authed) {
  const detail = await loadDetail();
    if (detail && detail.success && detailHint) {
      const flat = flattenDetail(detail.clients);
      const n = flat.length;
      detailHint.hidden = false;
      detailHint.textContent = n
        ? '已登入：共彙整 ' + n + ' 項任務狀態（網址與按鈕已遮罩）。'
        : '已登入：尚無設備回報任務。';
      if (n > (pub.tasks || []).length) {
        renderTasks(flat.map((row) => ({
          name: row.name,
          status: row.status,
          time: row.time || '—'
        })));
      }
    }
  }
}

boot();