// 已登入主控台的起始分頁。簡報牆移除後，舊的 photos 改開任務。
(function (root) {
  const ZONES = ['devices'];
  const DEFAULT_TAB = 'devices';
  const REMOVED = { photos: true, system: true };

  function normalizeDashTabToken(raw) {
    if (raw == null) return '';
    let token = String(raw).trim();
    if (!token) return '';
    if (token.charAt(0) === '#') token = token.slice(1).trim();
    while (token.charAt(0) === '/') token = token.slice(1);
    if (token.indexOf('panel-') === 0) token = token.slice('panel-'.length);
    let end = token.length;
    while (end > 0 && (token.charAt(end - 1) === '/' || token.charAt(end - 1) === '#')) end -= 1;
    token = token.slice(0, end).trim();
    const query = token.search(/[?&]/);
    if (query >= 0) token = token.slice(0, query);
    return token;
  }

  function interpretDashTab(raw) {
    const token = normalizeDashTabToken(raw);
    if (!token) return '';
    if (REMOVED[token]) return DEFAULT_TAB;
    if (ZONES.indexOf(token) >= 0) return token;
    return '';
  }

  function resolveInitialDashTab(saved, hash) {
    const hashToken = normalizeDashTabToken(hash);
    if (hashToken) {
      const fromHash = interpretDashTab(hash);
      if (fromHash) return fromHash;
    }
    const fromSaved = interpretDashTab(saved);
    if (fromSaved) return fromSaved;
    return DEFAULT_TAB;
  }

  function isRemovedDashTab(raw) {
    return !!REMOVED[normalizeDashTabToken(raw)];
  }

  root.PunchclockDashTab = {
    zones: ZONES,
    defaultTab: DEFAULT_TAB,
    normalizeDashTabToken: normalizeDashTabToken,
    resolveInitialDashTab: resolveInitialDashTab,
    isRemovedDashTab: isRemovedDashTab
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
