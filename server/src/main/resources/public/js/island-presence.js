// 探索模式多人在線的純邏輯：訊息、暱稱、內插、重連。不依賴 Three.js／DOM。
// 連線本身由 createIslandPresence 包起來，進入探索才開，離開或分頁藏太久就關。

export const NICK_KEY = 'punchclock.island.nick';
export const AVATAR_KEY = 'punchclock.island.avatar';
export const NAME_MAX = 12;
export const WORLD_RADIUS = 36;
export const SEND_MOVE_MS = 100;
export const SEND_IDLE_MS = 5000;
export const HIDDEN_DISCONNECT_MS = 30000;
export const INTERP_MS = 130;
export const RENAME_MS = 10000;

const SCENE_SET = {
  outdoor: true,
  shop: true,
  casino: true,
  'tcm-site': true,
  'computer-site': true
};

export function cleanNick(raw) {
  const cleaned = String(raw == null ? '' : raw)
    .replace(/[\u0000-\u001F\u007F-\u009F\u2028\u2029]/g, '')
    .trim();
  if (!cleaned) return '';
  if (Array.from(cleaned).length > NAME_MAX) return '';
  return cleaned;
}

/** n 取 0–999，補成「旅人 042」這種三位數。 */
export function autoNick(n) {
  const value = Math.abs(Math.floor(Number(n) || 0)) % 1000;
  return '旅人 ' + String(value).padStart(3, '0');
}

export function presenceUrl(loc) {
  const place = loc || {};
  const protocol = place.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = place.host || 'localhost';
  return protocol + '//' + host + '/ws/island';
}

/** 第 0 次等 1 秒，之後 2、4、8…，最多 30 秒。 */
export function reconnectDelayMs(attempt) {
  const step = attempt > 0 ? Math.floor(attempt) : 0;
  return Math.min(30, 2 ** step) * 1000;
}

export function lerpAngle(from, to, t) {
  let delta = to - from;
  const turn = Math.PI * 2;
  while (delta > Math.PI) delta -= turn;
  while (delta < -Math.PI) delta += turn;
  return from + delta * (t || 0);
}

export function createMotion() {
  return {
    x: 0,
    z: 0,
    yaw: 0,
    fromX: 0,
    fromZ: 0,
    fromYaw: 0,
    toX: 0,
    toZ: 0,
    toYaw: 0,
    start: 0,
    have: false
  };
}

export function sampleMotion(motion, now, reduced) {
  if (!motion || !motion.have) return { x: 0, z: 0, yaw: 0 };
  if (reduced) return { x: motion.toX, z: motion.toZ, yaw: motion.toYaw };
  const elapsed = (now || 0) - motion.start;
  const t = Math.max(0, Math.min(1, elapsed / INTERP_MS));
  return {
    x: motion.fromX + (motion.toX - motion.fromX) * t,
    z: motion.fromZ + (motion.toZ - motion.fromZ) * t,
    yaw: lerpAngle(motion.fromYaw, motion.toYaw, t)
  };
}

/** 第一個點直接出現，之後在大約 130ms 內滑過去。 */
export function pushTarget(motion, sample, now) {
  const shown = motion && motion.have ? sampleMotion(motion, now, false) : null;
  const target = sample || { x: 0, z: 0, yaw: 0 };
  motion.fromX = shown ? shown.x : target.x;
  motion.fromZ = shown ? shown.z : target.z;
  motion.fromYaw = shown ? shown.yaw : target.yaw;
  motion.toX = target.x;
  motion.toZ = target.z;
  motion.toYaw = target.yaw;
  motion.start = now || 0;
  motion.have = true;
  const at = sampleMotion(motion, now, false);
  motion.x = at.x;
  motion.z = at.z;
  motion.yaw = at.yaw;
  return motion;
}

function round3(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 0;
  return Math.round(n * 1000) / 1000;
}

export function encodeHello(name) {
  return JSON.stringify({ t: 'hello', name: name == null ? '' : String(name) });
}

export function encodeRename(name) {
  return JSON.stringify({ t: 'rename', name: name == null ? '' : String(name) });
}

export function normalizeAvatar(id) {
  return id === 'berry' ? 'berry' : 'milu';
}

export function loadAvatar(storage) {
  if (!storage || typeof storage.getItem !== 'function') return 'milu';
  try {
    return normalizeAvatar(storage.getItem(AVATAR_KEY));
  } catch (err) {
    return 'milu';
  }
}

export function saveAvatar(storage, id) {
  const avatar = normalizeAvatar(id);
  if (!storage || typeof storage.setItem !== 'function') return avatar;
  try {
    storage.setItem(AVATAR_KEY, avatar);
  } catch (err) {
    // localStorage 滿了仍回傳已選角色
  }
  return avatar;
}

export function encodePos(pose) {
  const body = pose || {};
  const scene = SCENE_SET[body.scene] ? body.scene : 'outdoor';
  const avatar = normalizeAvatar(body.avatar);
  const payload = {
    t: 'pos',
    x: round3(body.x),
    z: round3(body.z),
    yaw: round3(body.yaw),
    moving: !!body.moving,
    scene: scene
  };
  if (avatar === 'berry') payload.avatar = 'berry';
  return JSON.stringify(payload);
}

export function encodeSay(text) {
  return JSON.stringify({ t: 'say', text: text == null ? '' : String(text) });
}

function finite(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

function readPlayer(raw) {
  if (!raw || typeof raw !== 'object') return null;
  if (typeof raw.id !== 'string' || !raw.id) return null;
  if (typeof raw.name !== 'string') return null;
  if (!finite(raw.x) || !finite(raw.z) || !finite(raw.yaw)) return null;
  if (typeof raw.moving !== 'boolean') return null;
  return {
    id: raw.id,
    name: raw.name,
    x: raw.x,
    z: raw.z,
    yaw: raw.yaw,
    moving: raw.moving,
    avatar: normalizeAvatar(raw.avatar)
  };
}

function readPlayers(list) {
  if (!Array.isArray(list)) return null;
  const players = [];
  for (let i = 0; i < list.length; i++) {
    const player = readPlayer(list[i]);
    if (player) players.push(player);
  }
  return players;
}

/** 壞掉、型別不對的訊息回 null。玩家陣列裡壞掉的單筆略過。 */
export function decodeMessage(text) {
  if (typeof text !== 'string' || !text) return null;
  // 客戶端送出的單則上限是 512 bytes；伺服器的快照會把同場景的人都列進來，所以這裡放寬。
  if (text.length > 65536) return null;
  let data;
  try {
    data = JSON.parse(text);
  } catch (err) {
    return null;
  }
  if (!data || typeof data !== 'object' || typeof data.t !== 'string') return null;
  const rev = finite(data.rev) ? data.rev : 0;
  if (data.t === 'welcome') {
    if (typeof data.id !== 'string' || !data.id || typeof data.name !== 'string') return null;
    const players = readPlayers(data.players);
    if (!players) return null;
    return { t: 'welcome', id: data.id, name: data.name, rev: rev, players: players };
  }
  if (data.t === 'snap') {
    const players = readPlayers(data.players);
    if (!players) return null;
    return {
      t: 'snap',
      scene: typeof data.scene === 'string' ? data.scene : '',
      rev: rev,
      players: players
    };
  }
  if (data.t === 'leave') {
    if (typeof data.id !== 'string' || !data.id) return null;
    return { t: 'leave', id: data.id, rev: rev };
  }
  if (data.t === 'said') {
    if (typeof data.id !== 'string' || !data.id) return null;
    if (typeof data.name !== 'string') return null;
    if (typeof data.text !== 'string') return null;
    return {
      t: 'said',
      id: data.id,
      name: data.name,
      text: data.text,
      ts: finite(data.ts) ? data.ts : 0
    };
  }
  if (data.t === 'slow') {
    return { t: 'slow' };
  }
  return null;
}

export function shouldSendPos(prev, next, now) {
  if (!next || !(now >= 0)) return false;
  if (!prev || !(prev.sentAt >= 0)) return true;
  if (next.scene !== prev.scene) return true;
  if (normalizeAvatar(next.avatar) !== normalizeAvatar(prev.avatar)) return true;
  const elapsed = now - prev.sentAt;
  const turned = Math.abs(lerpAngle(prev.yaw, next.yaw, 1) - prev.yaw);
  const moved = Math.abs(next.x - prev.x) > 0.008
    || Math.abs(next.z - prev.z) > 0.008
    || turned > 0.02
    || !!next.moving !== !!prev.moving;
  if (next.moving || moved) return elapsed >= SEND_MOVE_MS;
  return elapsed >= SEND_IDLE_MS;
}

export function loadNick(storage) {
  if (!storage || typeof storage.getItem !== 'function') return '';
  try {
    return cleanNick(storage.getItem(NICK_KEY));
  } catch (err) {
    return '';
  }
}

/**
 * 進入探索才 connect。隱藏分頁要由呼叫端在約 30 秒後 disconnect。
 * onPlayers({ selfId, selfName, connected, players })。
 */
export function createIslandPresence(options) {
  const opts = options || {};
  const setTimer = opts.setTimeout || setTimeout;
  const clearTimer = opts.clearTimeout || clearTimeout;
  const WebSocketImpl = opts.WebSocket || null;
  const loc = opts.location || null;
  let socket = null;
  let want = false;
  let attempt = 0;
  let retryTimer = 0;
  let selfId = '';
  let selfName = '';
  let seenRev = 0;
  let peers = [];
  let renameAt = 0;

  function emit() {
    if (typeof opts.onPlayers !== 'function') return;
    const list = [];
    for (let i = 0; i < peers.length; i++) {
      if (peers[i].id !== selfId) list.push(peers[i]);
    }
    opts.onPlayers({
      selfId: selfId,
      selfName: selfName,
      connected: !!(socket && socket.readyState === 1),
      players: list
    });
  }

  function accept(msg) {
    if (!want || !msg) return;
    if (msg.t !== 'welcome' && msg.rev && msg.rev < seenRev) return;
    if (msg.rev) seenRev = msg.rev;
    if (msg.t === 'welcome') {
      selfId = msg.id;
      selfName = msg.name;
      peers = msg.players || [];
      emit();
      if (typeof opts.onWelcome === 'function') opts.onWelcome(msg);
      return;
    }
    if (msg.t === 'snap') {
      const scene = typeof opts.scene === 'function' ? opts.scene() : '';
      if (msg.scene && scene && msg.scene !== scene) return;
      peers = msg.players || [];
      emit();
      return;
    }
    if (msg.t === 'leave') {
      const next = [];
      for (let i = 0; i < peers.length; i++) {
        if (peers[i].id !== msg.id) next.push(peers[i]);
      }
      peers = next;
      emit();
      return;
    }
    if (msg.t === 'said') {
      if (typeof opts.onSaid === 'function') opts.onSaid(msg);
      return;
    }
    if (msg.t === 'slow') {
      if (typeof opts.onSlow === 'function') opts.onSlow(msg);
    }
  }

  function schedule() {
    if (!want) return;
    const delay = reconnectDelayMs(attempt);
    attempt += 1;
    retryTimer = setTimer(() => {
      retryTimer = 0;
      open();
    }, delay);
  }

  function open() {
    if (!want || !WebSocketImpl || !loc) return;
    let ws;
    try {
      ws = new WebSocketImpl(presenceUrl(loc));
    } catch (err) {
      schedule();
      return;
    }
    socket = ws;
    ws.onopen = () => {
      attempt = 0;
      const nick = typeof opts.nick === 'function' ? opts.nick() : '';
      try {
        ws.send(encodeHello(nick));
      } catch (err) {
        // 送不出去就等 onclose 重連
      }
    };
    ws.onmessage = (event) => {
      if (socket !== ws || !want) return;
      accept(decodeMessage(event && event.data));
    };
    ws.onclose = () => {
      if (socket !== ws) return;
      socket = null;
      if (!want) return;
      schedule();
    };
    ws.onerror = () => {};
  }

  function connect() {
    want = true;
    if (socket && (socket.readyState === 0 || socket.readyState === 1)) return;
    if (retryTimer) return;
    open();
  }

  function disconnect() {
    want = false;
    attempt = 0;
    seenRev = 0;
    selfId = '';
    selfName = '';
    peers = [];
    if (retryTimer) {
      clearTimer(retryTimer);
      retryTimer = 0;
    }
    if (socket) {
      const current = socket;
      socket = null;
      current.onclose = null;
      try {
        current.close();
      } catch (err) {
        // 已經關了
      }
    }
    emit();
  }

  function sendPos(pose) {
    if (!socket || socket.readyState !== 1 || !pose) return false;
    try {
      socket.send(encodePos(pose));
      return true;
    } catch (err) {
      return false;
    }
  }

  function rename(name) {
    const cleaned = cleanNick(name);
    if (!cleaned) return false;
    if (opts.storage && typeof opts.storage.setItem === 'function') {
      try {
        opts.storage.setItem(NICK_KEY, cleaned);
      } catch (err) {
        // 寫不進 localStorage 仍把名字送給伺服器
      }
    }
    const now = typeof opts.now === 'function' ? opts.now() : Date.now();
    if (!socket || socket.readyState !== 1) return true;
    if (now - renameAt < RENAME_MS) return true;
    try {
      socket.send(encodeRename(cleaned));
      renameAt = now;
    } catch (err) {
      return true;
    }
    return true;
  }

  function sendSay(text) {
    if (!socket || socket.readyState !== 1) return false;
    try {
      socket.send(encodeSay(text));
      return true;
    } catch (err) {
      return false;
    }
  }

  return {
    connect: connect,
    disconnect: disconnect,
    sendPos: sendPos,
    sendSay: sendSay,
    rename: rename,
    accept: accept
  };
}
