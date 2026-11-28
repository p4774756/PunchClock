export const SECRETARY_OUTFIT_STORAGE_KEY = 'punchclock.office.secretaryOutfit';

const ASSET_BASE = '/assets/private-team';

const SPRITE_FILES = {
  default: {
    typing: 'secretary-typing-a.webp',
    typingB: 'secretary-typing-b.webp',
    wave: 'secretary-wave.webp',
    yeah: 'secretary-yeah.webp',
    idle: 'secretary-idle-tea.webp',
    read: 'secretary-read.webp',
    phone: 'secretary-phone.webp',
    sleep: 'secretary-sleep.webp',
    sofa: 'secretary-sofa.webp',
    clapA: 'secretary-clap-a.webp',
    clapB: 'secretary-clap-b.webp',
    avatar: 'secretary-avatar.webp'
  },
  stage: {
    typing: 'secretary-stage-typing-a.webp',
    typingB: 'secretary-stage-typing-b.webp',
    wave: 'secretary-stage-wave.webp',
    yeah: 'secretary-stage-yeah.webp',
    idle: 'secretary-stage-idle-tea.webp',
    read: 'secretary-stage-read.webp',
    phone: 'secretary-stage-phone.webp',
    sleep: 'secretary-stage-sleep.webp',
    sofa: 'secretary-stage-sofa.webp',
    clapA: 'secretary-stage-clap-a.webp',
    clapB: 'secretary-stage-clap-b.webp',
    avatar: 'secretary-stage-avatar.webp'
  }
};

export const SECRETARY_SPRITE_PACK_KEYS = [
  'typing',
  'typingB',
  'wave',
  'yeah',
  'idle',
  'read',
  'phone',
  'sleep',
  'sofa',
  'clapA',
  'clapB',
  'avatar'
];

export function parseSecretaryOutfitId(value) {
  const s = value == null ? '' : String(value).trim();
  if (s === 'stage') return 'stage';
  return 'default';
}

function spriteUrl(fileName) {
  return ASSET_BASE + '/' + fileName;
}

export function secretarySpritePackForOutfit(outfitId) {
  const id = parseSecretaryOutfitId(outfitId);
  const files = SPRITE_FILES[id] || SPRITE_FILES.default;
  const pack = {};
  for (let i = 0; i < SECRETARY_SPRITE_PACK_KEYS.length; i++) {
    const key = SECRETARY_SPRITE_PACK_KEYS[i];
    pack[key] = spriteUrl(files[key]);
  }
  return pack;
}

export function secretaryStandbyIdleUrlsForOutfit(outfitId) {
  const pack = secretarySpritePackForOutfit(outfitId);
  return [pack.idle, pack.read, pack.phone];
}

export function secretaryCelebrateStepsForOutfit(outfitId) {
  const pack = secretarySpritePackForOutfit(outfitId);
  return [
    { kind: 'frame', url: pack.clapA, ms: 250 },
    { kind: 'frame', url: pack.clapB, ms: 250 },
    { kind: 'frame', url: pack.clapA, ms: 250 },
    { kind: 'frame', url: pack.clapB, ms: 250 },
    { kind: 'frame', url: pack.yeah, ms: 700 },
    { kind: 'jump', ms: 450 }
  ];
}

export function secretaryDanceDurationMsForSteps(steps) {
  if (!Array.isArray(steps)) return 0;
  let sum = 0;
  for (let i = 0; i < steps.length; i++) {
    sum += Number(steps[i].ms) || 0;
  }
  return sum;
}

/** 依角色鍵把現有貼圖 URL 對應到另一套服裝（不切姿勢／位置）。 */
export function secretarySpriteUrlRoleMap(url) {
  const u = url == null ? '' : String(url).trim();
  if (!u) return null;
  const defaultPack = secretarySpritePackForOutfit('default');
  const stagePack = secretarySpritePackForOutfit('stage');
  for (let i = 0; i < SECRETARY_SPRITE_PACK_KEYS.length; i++) {
    const key = SECRETARY_SPRITE_PACK_KEYS[i];
    if (u === defaultPack[key] || u === stagePack[key]) return key;
  }
  return null;
}

export function secretaryEquivalentSpriteUrl(url, targetOutfitId) {
  const role = secretarySpriteUrlRoleMap(url);
  const pack = secretarySpritePackForOutfit(targetOutfitId);
  if (role && pack[role]) return pack[role];
  return pack.idle;
}

export function secretaryAvatarUrlForOutfit(outfitId) {
  return secretarySpritePackForOutfit(outfitId).avatar;
}

export function secretaryOutfitPreloadUrls(outfitId) {
  const pack = secretarySpritePackForOutfit(outfitId);
  const seen = new Set();
  const urls = [];
  for (let i = 0; i < SECRETARY_SPRITE_PACK_KEYS.length; i++) {
    const u = pack[SECRETARY_SPRITE_PACK_KEYS[i]];
    if (u && !seen.has(u)) {
      seen.add(u);
      urls.push(u);
    }
  }
  return urls;
}

export function readSecretaryOutfitFromStorage(storage) {
  try {
    if (!storage || typeof storage.getItem !== 'function') return 'default';
    const raw = storage.getItem(SECRETARY_OUTFIT_STORAGE_KEY);
    const parsed = parseSecretaryOutfitId(raw);
    if (raw != null && String(raw) !== parsed && typeof storage.setItem === 'function') {
      try {
        storage.setItem(SECRETARY_OUTFIT_STORAGE_KEY, parsed);
      } catch {
        /* 清理失效值失敗時仍回傳正規結果 */
      }
    }
    return parsed;
  } catch {
    return 'default';
  }
}

export function writeSecretaryOutfitToStorage(storage, outfitId) {
  try {
    if (!storage || typeof storage.setItem !== 'function') return;
    storage.setItem(SECRETARY_OUTFIT_STORAGE_KEY, parseSecretaryOutfitId(outfitId));
  } catch {
    /* 訪客本機偏好；讀寫失敗時靜默略過 */
  }
}