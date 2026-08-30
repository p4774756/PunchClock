/**
 * Unit tests for lib/auth cookie parsing (WebSocket upgrade path).
 */
const {
  AUTH_COOKIE_VALUE,
  isAuthFromCookieHeader,
  isHttpsRequest,
  authCookieOptions
} = require('./lib/auth');

let failed = 0;

function assert(condition, message) {
  if (!condition) {
    console.error(`❌ ${message}`);
    failed += 1;
  } else {
    console.log(`✅ ${message}`);
  }
}

console.log('🔍 [unit] lib/auth — cookie header');

assert(isAuthFromCookieHeader() === false, 'missing header is not auth');
assert(isAuthFromCookieHeader('') === false, 'empty header is not auth');
assert(isAuthFromCookieHeader('auth=wrong') === false, 'wrong cookie is not auth');
assert(isAuthFromCookieHeader(`auth=${AUTH_COOKIE_VALUE}`) === true, 'matching auth cookie is auth');
assert(
  isAuthFromCookieHeader(`other=1; auth=${AUTH_COOKIE_VALUE}; extra=2`) === true,
  'auth cookie among others is auth'
);

assert(isHttpsRequest({ secure: true, headers: {} }) === true, 'req.secure is https');
assert(isHttpsRequest({ headers: { 'x-forwarded-proto': 'https' } }) === true, 'x-forwarded-proto https');
assert(isHttpsRequest({ headers: { 'x-forwarded-proto': 'https, http' } }) === true, 'x-forwarded-proto first hop');
assert(isHttpsRequest({ headers: {} }) === false, 'plain http is not https');
assert(authCookieOptions({ headers: {} }).secure === false, 'localhost cookie is not Secure');
assert(authCookieOptions({ headers: { 'x-forwarded-proto': 'https' } }).secure === true, 'https cookie is Secure');

if (failed > 0) {
  console.error(`\n❌ test_auth failed: ${failed} assertion(s)`);
  process.exit(1);
}
console.log('\n✅ test_auth passed');
