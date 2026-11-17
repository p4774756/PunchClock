// 島上泡泡聊天的純邏輯與角落 UI。不存檔；內容只用 textContent 顯示。

export const CHAT_MAX = 60;
export const CHAT_LOG_MAX = 20;
export const BUBBLE_MS = 5000;
export const SLOW_HINT = '說太快了，等一下';

export function cleanChatText(raw) {
  const cleaned = String(raw == null ? '' : raw)
    .replace(/[\u0000-\u001F\u007F-\u009F\u2028\u2029]/g, '')
    .trim();
  if (!cleaned) return '';
  const chars = Array.from(cleaned);
  if (chars.length <= CHAT_MAX) return cleaned;
  return chars.slice(0, CHAT_MAX).join('');
}

/** 最近 max 句，只放記憶體。 */
export function createChatLog(max) {
  const limit = max > 0 ? Math.floor(max) : CHAT_LOG_MAX;
  const lines = [];
  return {
    push(entry) {
      if (!entry) return lines.slice();
      lines.push({
        name: entry.name == null ? '' : String(entry.name),
        text: entry.text == null ? '' : String(entry.text),
        system: !!entry.system,
        ts: entry.ts || 0
      });
      while (lines.length > limit) lines.shift();
      return lines.slice();
    },
    list() {
      return lines.slice();
    },
    clear() {
      lines.length = 0;
      return lines.slice();
    }
  };
}

export function formatChatLine(entry) {
  if (!entry) return '';
  if (entry.system) return String(entry.text || '');
  return String(entry.name || '') + '：' + String(entry.text || '');
}

/**
 * 桌面 T／（無入口提示時）Enter 開聊天。
 * 不搶改名彈窗、輸入框、大廳與清單。
 */
export function shouldOpenChatKey(ctx) {
  if (!ctx || !ctx.exploring || ctx.uiOpen || ctx.typing || ctx.nickOpen || ctx.chatOpen) return false;
  if (ctx.altKey || ctx.ctrlKey || ctx.metaKey) return false;
  const key = ctx.key || '';
  const code = ctx.code || '';
  if (key === 't' || key === 'T' || code === 'KeyT') return true;
  if (key === 'Enter' || code === 'Enter') return !ctx.promptVisible;
  return false;
}

/**
 * 角落紀錄、輸入框、頭上泡泡、手機 FAB。
 * presence.sendSay／onSaid／onSlow 由呼叫端接上。
 */
export function createIslandChat(options) {
  const opts = options || {};
  const doc = opts.document || (typeof document !== 'undefined' ? document : null);
  const log = createChatLog(CHAT_LOG_MAX);
  const bubbles = new Map();
  let open = false;
  let hintTimer = 0;
  let selfBubble = null;

  const panel = doc ? doc.getElementById('islandChatPanel') : null;
  const btn = doc ? doc.getElementById('islandChatBtn') : null;
  const fab = doc ? doc.getElementById('islandChatFab') : null;
  const logEl = doc ? doc.getElementById('islandChatLog') : null;
  const input = doc ? doc.getElementById('islandChatInput') : null;
  const sendBtn = doc ? doc.getElementById('islandChatSend') : null;
  const hint = doc ? doc.getElementById('islandChatHint') : null;
  const labels = doc ? doc.getElementById('islandLabels') : null;

  function ensureSelfBubble() {
    if (!labels) return null;
    if (selfBubble && selfBubble.parentNode) return selfBubble;
    selfBubble = doc.createElement('div');
    selfBubble.className = 'island-chat-bubble island-chat-bubble-self';
    selfBubble.hidden = true;
    labels.appendChild(selfBubble);
    return selfBubble;
  }

  function paintLog() {
    if (!logEl) return;
    logEl.textContent = '';
    const rows = log.list();
    for (let i = 0; i < rows.length; i++) {
      const row = doc.createElement('div');
      row.className = rows[i].system ? 'island-chat-line island-chat-line-system' : 'island-chat-line';
      row.textContent = formatChatLine(rows[i]);
      logEl.appendChild(row);
    }
    logEl.scrollTop = logEl.scrollHeight;
  }

  function setOpen(next) {
    open = !!next;
    if (panel) panel.hidden = !open;
    if (btn) btn.setAttribute('aria-expanded', open ? 'true' : 'false');
    if (open && input) {
      try {
        input.focus();
      } catch (err) {
        // 測試環境可能沒有 focus
      }
    } else if (!open && input && doc.activeElement === input) {
      try {
        input.blur();
      } catch (err) {
        // ignore
      }
    }
  }

  function showHint(text) {
    if (!hint) return;
    hint.textContent = text || SLOW_HINT;
    hint.hidden = false;
    const clear = opts.clearTimeout || clearTimeout;
    const wait = opts.setTimeout || setTimeout;
    if (hintTimer) clear(hintTimer);
    hintTimer = wait(() => {
      hintTimer = 0;
      hint.hidden = true;
    }, 2200);
  }

  function bubbleForPeer(id) {
    const getLabel = opts.getPeerLabel;
    const label = typeof getLabel === 'function' ? getLabel(id) : null;
    if (!label) return null;
    let bubble = label.querySelector('.island-chat-bubble');
    if (!bubble) {
      bubble = doc.createElement('div');
      bubble.className = 'island-chat-bubble';
      label.insertBefore(bubble, label.firstChild);
    }
    return bubble;
  }

  function showBubble(id, text, self) {
    const now = typeof opts.now === 'function' ? opts.now() : Date.now();
    const until = now + BUBBLE_MS;
    bubbles.set(id, { text: text, until: until, self: !!self });
    if (self) {
      const el = ensureSelfBubble();
      if (el) {
        el.textContent = text;
        el.hidden = false;
        el.classList.remove('is-fading');
      }
      return;
    }
    const el = bubbleForPeer(id);
    if (el) {
      el.textContent = text;
      el.hidden = false;
      el.classList.remove('is-fading');
    }
  }

  function expireBubbles(now) {
    const t = now || (typeof opts.now === 'function' ? opts.now() : Date.now());
    bubbles.forEach((row, id) => {
      if (t < row.until) return;
      bubbles.delete(id);
      if (row.self) {
        if (selfBubble) {
          selfBubble.hidden = true;
          selfBubble.textContent = '';
        }
        return;
      }
      const el = bubbleForPeer(id);
      if (el) {
        el.hidden = true;
        el.textContent = '';
      }
    });
  }

  function placeSelfBubble(ctx) {
    const now = typeof opts.now === 'function' ? opts.now() : Date.now();
    expireBubbles(now);
    const row = bubbles.get('__self__');
    const el = ensureSelfBubble();
    if (!el || !row || !ctx || !ctx.visible) {
      if (el) el.style.visibility = 'hidden';
      return;
    }
    const width = ctx.width || 1;
    const height = ctx.height || 1;
    const projected = ctx.projected;
    const camera = ctx.camera;
    if (!projected || !camera || typeof projected.set !== 'function' || typeof projected.project !== 'function') {
      return;
    }
    const worldY = ctx.y == null ? 1.18 : ctx.y;
    projected.set(ctx.x, worldY, ctx.z);
    projected.project(camera);
    if (projected.z > 1 || projected.x < -1.08 || projected.x > 1.08 || projected.y < -1.08 || projected.y > 1.08) {
      el.style.visibility = 'hidden';
      return;
    }
    const x = (projected.x * 0.5 + 0.5) * width;
    const screenY = (-projected.y * 0.5 + 0.5) * height;
    el.style.visibility = 'visible';
    el.style.transform = 'translate(' + x + 'px, ' + screenY + 'px) translate(-50%, -120%)';
  }

  function submit() {
    if (!input) return false;
    const cleaned = cleanChatText(input.value);
    if (!cleaned) return false;
    const presence = opts.presence;
    if (!presence || typeof presence.sendSay !== 'function') return false;
    const ok = presence.sendSay(cleaned);
    if (ok) input.value = '';
    return ok;
  }

  function onSaid(msg) {
    if (!msg || msg.t !== 'said') return;
    const selfId = typeof opts.selfId === 'function' ? opts.selfId() : '';
    const isSelf = !!(selfId && msg.id === selfId);
    log.push({ name: msg.name, text: msg.text, ts: msg.ts });
    paintLog();
    showBubble(isSelf ? '__self__' : msg.id, msg.text, isSelf);
  }

  function onSlow() {
    showHint(SLOW_HINT);
  }

  function onOutside(event) {
    if (!open) return;
    const target = event && event.target;
    if (!target || !target.closest) return;
    if (target.closest('#islandChatPanel, #islandChatBtn, #islandChatFab')) return;
    setOpen(false);
  }

  if (btn) {
    btn.addEventListener('click', () => setOpen(!open));
  }
  if (fab) {
    fab.addEventListener('click', () => setOpen(true));
  }
  if (sendBtn) {
    sendBtn.addEventListener('click', () => submit());
  }
  if (input) {
    input.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== 'Escape') return;
      event.preventDefault();
      event.stopPropagation();
      if (event.key === 'Enter') submit();
      else setOpen(false);
    });
  }
  if (doc) {
    doc.addEventListener('pointerdown', onOutside);
  }

  function setExploring(exploring) {
    if (fab) fab.hidden = !exploring;
    if (!exploring) setOpen(false);
  }

  function clearAll() {
    log.clear();
    paintLog();
    bubbles.clear();
    if (selfBubble) {
      selfBubble.hidden = true;
      selfBubble.textContent = '';
    }
    if (hint) hint.hidden = true;
  }

  return {
    isOpen: () => open,
    open: () => setOpen(true),
    close: () => setOpen(false),
    toggle: () => setOpen(!open),
    submit: submit,
    onSaid: onSaid,
    onSlow: onSlow,
    placeSelfBubble: placeSelfBubble,
    setExploring: setExploring,
    clear: clearAll,
    log: log,
    SLOW_HINT: SLOW_HINT
  };
}
