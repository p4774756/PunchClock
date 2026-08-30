/**
 * Pure unit tests for lib/clientStore — no HTTP server required.
 */
const {
  clients,
  queueClientAction,
  queuePeerMessage,
  queuePeerPoke,
  peerSnapshot,
  drainPendingActions,
  sanitizeClientForApi,
  maskTargetUrl,
  logTaskTransitions,
  appendClientEvent,
  PENDING_ACTION_TTL_MS
} = require('./lib/clientStore');

let failed = 0;

function assert(condition, message) {
  if (!condition) {
    console.error(`❌ ${message}`);
    failed += 1;
  } else {
    console.log(`✅ ${message}`);
  }
}

function assertEqual(actual, expected, message) {
  const ok = JSON.stringify(actual) === JSON.stringify(expected);
  if (!ok) {
    console.error(`❌ ${message}`);
    console.error(`   expected: ${JSON.stringify(expected)}`);
    console.error(`   actual:   ${JSON.stringify(actual)}`);
    failed += 1;
  } else {
    console.log(`✅ ${message}`);
  }
}

console.log('🔍 [unit] lib/clientStore — sanitize + queue/drain');

assertEqual(
  maskTargetUrl('https://secret.example/checkin?token=abc'),
  'https://se***.example/***',
  'maskTargetUrl masks host labels and path'
);
assertEqual(
  maskTargetUrl('https://tw.yahoo.com/'),
  'https://t*.ya***.com/***',
  'maskTargetUrl masks domain not just trailing stars'
);
assert(maskTargetUrl('not a url').includes('***'), 'maskTargetUrl still masks invalid URL');

// --- sanitize masks targetUrl, keeps buttonId ---
const dirty = {
  clientId: 'worker-a',
  status: 'ONLINE',
  targetUrl: 'https://secret.example/checkin',
  buttonId: '#btn-checkin',
  tasks: [
    { id: 't1', name: '上班', targetUrl: 'https://secret.example/a', buttonId: '#a' },
    { id: 't2', name: '下班', targetUrl: 'https://secret.example/b', buttonId: '#b' }
  ]
};
const clean = sanitizeClientForApi(dirty);
assertEqual(clean.targetUrl, 'https://se***.example/***', 'sanitize masks client.targetUrl');
assertEqual(clean.buttonId, '#btn-checkin', 'sanitize keeps client.buttonId');
assertEqual(clean.tasks[0].targetUrl, 'https://se***.example/***', 'sanitize masks task.targetUrl');
assertEqual(clean.tasks[0].buttonId, '#a', 'sanitize keeps task.buttonId');
assertEqual(clean.tasks.map((t) => t.id), ['t1', 't2'], 'sanitize keeps other task fields');
assert(dirty.targetUrl === 'https://secret.example/checkin', 'sanitize does not mutate original');

// --- queue + drain fresh actions ---
clients.clear();
const clientId = 'unit-test-worker';
queueClientAction(clientId, 'CANCEL_SCHEDULE');
queueClientAction(clientId, 'CANCEL_TASK:abc');
const stored = clients.get(clientId);
assert(Array.isArray(stored.pendingActions) && stored.pendingActions.length === 2,
  'queue stores two pending actions');
const drained = drainPendingActions(stored);
assertEqual(drained, ['CANCEL_SCHEDULE', 'CANCEL_TASK:abc'], 'drain returns queued actions in order');
assert(stored.pendingActions === undefined, 'drain clears pendingActions');
assert(stored.pendingAction === undefined, 'drain clears legacy pendingAction');

// --- drain respects TTL ---
clients.clear();
const expiredClient = {
  clientId: 'ttl-worker',
  status: 'ONLINE',
  tasks: [],
  lastSeen: new Date(),
  message: '',
  transport: 'unknown',
  pendingActions: [
    { action: 'CANCEL_SCHEDULE', time: Date.now() - PENDING_ACTION_TTL_MS - 1000 },
    { action: 'CANCEL_TASK:keep', time: Date.now() }
  ]
};
clients.set('ttl-worker', expiredClient);
const ttlDrained = drainPendingActions(expiredClient);
assertEqual(ttlDrained, ['CANCEL_TASK:keep'], 'drain drops actions older than PENDING_ACTION_TTL_MS');

// --- event log: queue cancel + task status transitions ---
clients.clear();
const logClientId = 'log-worker';
queueClientAction(logClientId, 'CANCEL_TASK:work-out');
const queued = clients.get(logClientId);
assert(Array.isArray(queued.eventLog) && queued.eventLog.length === 1, 'queue cancel writes eventLog');
assert(queued.eventLog[0].text.includes('取消任務'), 'cancel event text mentions 取消任務');

queued.tasks = [{ id: 'work-out', name: '下班打卡', status: 'SCHEDULED', message: '' }];
logTaskTransitions(queued, [
  { id: 'work-out', name: '下班打卡', status: 'CANCELLED', message: '網頁後台遠端取消' }
]);
assert(queued.eventLog.length === 2, 'status change appends another event');
assert(queued.eventLog[1].text.includes('等待中'), 'transition log includes previous status label');
assert(queued.eventLog[1].text.includes('已取消'), 'transition log includes new status label');
assert(queued.eventLog[1].text.includes('網頁後台遠端取消'), 'transition log includes cancel reason');

appendClientEvent(queued, 'extra');
assert(queued.eventLog[queued.eventLog.length - 1].text === 'extra', 'appendClientEvent pushes text');

// --- peer message / poke ---
clients.clear();
const msgResult = queuePeerMessage('worker-b', 'worker-a', '記得打卡');
assert(msgResult.ok, 'queuePeerMessage succeeds');
const msgDrained = drainPendingActions(clients.get('worker-b'));
assert(msgDrained.length === 1, 'peer message queued once');
assert(msgDrained[0].startsWith('MSG|worker-a|'), 'peer message action format');

const pokeResult = queuePeerPoke('worker-b', 'worker-a');
assert(pokeResult.ok, 'queuePeerPoke succeeds');
const pokeDrained = drainPendingActions(clients.get('worker-b'));
assertEqual(pokeDrained, ['POKE|worker-a'], 'peer poke action format');

assert(!queuePeerMessage('worker-a', 'worker-a', 'hi').ok, 'cannot message self');
assert(!queuePeerPoke('worker-a', 'worker-a').ok, 'cannot poke self');

clients.set('worker-a', {
  clientId: 'worker-a',
  status: 'ONLINE',
  tasks: [{ id: 't1', status: 'SCHEDULED' }],
  appVersion: '1.0',
  lastSeen: new Date()
});
clients.set('worker-b', {
  clientId: 'worker-b',
  status: 'OFFLINE',
  tasks: [],
  appVersion: '1.0',
  lastSeen: new Date(Date.now() - 10 * 60 * 1000)
});
const peers = peerSnapshot('worker-a');
assertEqual(peers.length, 1, 'peerSnapshot excludes self');
assertEqual(peers[0].clientId, 'worker-b', 'peerSnapshot returns other client');
assertEqual(peers[0].status, 'OFFLINE', 'peerSnapshot marks stale client offline');

if (failed > 0) {
  console.error(`\n❌ test_client_store failed: ${failed} assertion(s)`);
  process.exit(1);
}
console.log('\n✅ test_client_store passed');
