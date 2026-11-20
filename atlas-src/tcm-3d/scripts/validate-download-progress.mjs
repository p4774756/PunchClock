#!/usr/bin/env node
import assert from 'node:assert/strict';
import {
  createDownloadProgressState,
  materialsSlicePercent,
  nextModelDownloadProgress,
  nextMonotonicPercent,
  PARSE_PERCENT_START,
  DOWNLOAD_PERCENT_MAX,
} from '../app/load-progress.ts';

const GZIP = 2620159;
const RAW = 5681108;

function runCases() {
  // Chrome: loaded tracks gzip Content-Length
  let state = createDownloadProgressState();
  let r = nextModelDownloadProgress(GZIP / 2, GZIP, GZIP, RAW, state);
  state = r.state;
  assert(r.step === 'download' && r.percent !== null && r.percent > 12);
  r = nextModelDownloadProgress(GZIP, GZIP, GZIP, RAW, state);
  state = r.state;
  assert.equal(r.step, 'parse');
  assert.equal(r.percent, PARSE_PERCENT_START);
  r = nextModelDownloadProgress(GZIP + 1000, GZIP, GZIP, RAW, state);
  assert.equal(r.step, null, 'parse only once');

  // Safari: loaded is decoded bytes — mid-flight can stay below gzip total; completion inflates past it
  state = createDownloadProgressState();
  const safariLoaded = 5681108;
  r = nextModelDownloadProgress(1_500_000, GZIP, GZIP, RAW, state);
  state = r.state;
  assert(r.step === 'download' && r.percent !== null && r.percent <= DOWNLOAD_PERCENT_MAX);
  r = nextModelDownloadProgress(safariLoaded, GZIP, GZIP, RAW, state);
  assert.equal(r.step, 'parse');
  assert.equal(r.percent, PARSE_PERCENT_START);

  // No total: fall back to gzipBytes then rawBytes
  state = createDownloadProgressState();
  r = nextModelDownloadProgress(GZIP / 3, 0, GZIP, RAW, state);
  assert.equal(r.step, 'download');
  r = nextModelDownloadProgress(RAW, 0, GZIP, RAW, state);
  assert.equal(r.step, 'parse');

  // Never exceeds 100% display range for download phase
  state = createDownloadProgressState();
  r = nextModelDownloadProgress(RAW * 2, GZIP, GZIP, RAW, state);
  if (r.step === 'download') assert(r.percent <= DOWNLOAD_PERCENT_MAX);
}

runCases();

assert.equal(materialsSlicePercent(0, 10), 92);
assert.equal(materialsSlicePercent(10, 10), 99);
assert(materialsSlicePercent(5, 10) > materialsSlicePercent(4, 10));
assert.equal(nextMonotonicPercent(95, 92), 95);
assert.equal(nextMonotonicPercent(92, 96), 96);

console.log('download progress mapping passed.');