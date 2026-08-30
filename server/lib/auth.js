const crypto = require('crypto');

// 管理員登入密碼（Render 環境變數 ADMIN_PASSWORD）
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'secret';
// 登入後 Cookie 值：由密碼衍生，改密碼後舊 Cookie 自動失效
const AUTH_COOKIE_VALUE = crypto
  .createHash('sha256')
  .update('clickclick-admin:' + ADMIN_PASSWORD)
  .digest('hex')
  .slice(0, 32);

// 心跳 API 共用密鑰（桌面端 Authorization: Bearer <token>）
const HEARTBEAT_SECRET = process.env.HEARTBEAT_SECRET || 'clickclick-dev-secret';

function isAuth(req) {
  if (req.cookies && req.cookies.auth === AUTH_COOKIE_VALUE) return true;
  return isAuthFromCookieHeader(req.headers && req.headers.cookie);
}

/** WebSocket upgrade 沒有 Express cookie parser，改從原始 Cookie 標頭判斷。 */
function isAuthFromCookieHeader(cookieHeader) {
  return parseCookieHeader(cookieHeader).auth === AUTH_COOKIE_VALUE;
}

function parseCookieHeader(cookieHeader) {
  const out = {};
  if (!cookieHeader || typeof cookieHeader !== 'string') return out;
  for (const part of cookieHeader.split(';')) {
    const idx = part.indexOf('=');
    if (idx === -1) continue;
    const key = part.slice(0, idx).trim();
    let value = part.slice(idx + 1).trim();
    try {
      value = decodeURIComponent(value);
    } catch (_) {
      // keep raw value
    }
    if (key) out[key] = value;
  }
  return out;
}

function isHeartbeatAuthorized(req) {
  const authHeader = req.headers.authorization || '';
  if (!authHeader.startsWith('Bearer ')) return false;
  return authHeader.slice(7) === HEARTBEAT_SECRET;
}

// 紀錄登入失敗次數與鎖定時間 (key: ip, value: { attempts: number, lockUntil: number })
const loginAttempts = new Map();

function getLoginLockStatus(ip) {
  const record = loginAttempts.get(ip);
  if (!record) return { isLocked: false, remainingSec: 0, attempts: 0 };
  if (record.lockUntil && record.lockUntil > Date.now()) {
    const remainingSec = Math.ceil((record.lockUntil - Date.now()) / 1000);
    return { isLocked: true, remainingSec, attempts: record.attempts };
  }
  if (record.lockUntil && record.lockUntil <= Date.now()) {
    loginAttempts.delete(ip);
    return { isLocked: false, remainingSec: 0, attempts: 0 };
  }
  return { isLocked: false, remainingSec: 0, attempts: record.attempts };
}

function recordLoginFailure(ip) {
  let record = loginAttempts.get(ip) || { attempts: 0, lockUntil: 0 };
  record.attempts += 1;
  if (record.attempts >= 5) {
    record.lockUntil = Date.now() + 15 * 60 * 1000; // 鎖定 15 分鐘
  }
  loginAttempts.set(ip, record);
  return record;
}

function clearLoginAttempt(ip) {
  loginAttempts.delete(ip);
}

function checkAdminPassword(password) {
  return password === ADMIN_PASSWORD;
}

function isHttpsRequest(req) {
  if (!req) return false;
  if (req.secure) return true;
  const proto = req.headers && req.headers['x-forwarded-proto'];
  if (typeof proto === 'string') {
    return proto.split(',')[0].trim() === 'https';
  }
  return false;
}

function authCookieOptions(req) {
  return {
    httpOnly: true,
    maxAge: 24 * 60 * 60 * 1000,
    sameSite: 'lax',
    secure: isHttpsRequest(req)
  };
}

function authCookieClearOptions(req) {
  return {
    httpOnly: true,
    sameSite: 'lax',
    secure: isHttpsRequest(req)
  };
}

module.exports = {
  ADMIN_PASSWORD,
  AUTH_COOKIE_VALUE,
  HEARTBEAT_SECRET,
  isAuth,
  isAuthFromCookieHeader,
  isHeartbeatAuthorized,
  getLoginLockStatus,
  recordLoginFailure,
  clearLoginAttempt,
  checkAdminPassword,
  isHttpsRequest,
  authCookieOptions,
  authCookieClearOptions
};
