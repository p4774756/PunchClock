import { buildingById } from './island-layout.js';
import {
  CAREER_EXHIBIT_LAYOUT,
  CAREER_WALK_SAMPLES,
  CAREER_ZONE_SIGNS,
  CAREER_PROMPT_LABEL
} from './island-career-corridor-data.js';

export const CAREER_TIMELINE_URL = '/island/career-timeline.json';
export const CARD_FIELD_WHITELIST = Object.freeze(['years', 'org', 'role', 'summary', 'prop', 'phase', 'title']);

const PII_PATTERNS = [
  /\bTEL\b/i,
  /@[a-z0-9._-]+\.[a-z]{2,}/i,
  /薪資|薪水|月薪|年薪/,
  /\d+\s*歲/
];

export function pickWhitelistFields(entry) {
  const out = {};
  for (let i = 0; i < CARD_FIELD_WHITELIST.length; i++) {
    const key = CARD_FIELD_WHITELIST[i];
    if (entry && entry[key] != null) out[key] = String(entry[key]);
  }
  return out;
}

export function buildCardTextModel(entry) {
  const fields = pickWhitelistFields(entry);
  return {
    years: fields.years || '',
    org: fields.org || '',
    role: fields.role || '',
    summary: fields.summary || '',
    prop: fields.prop || '',
    phase: fields.phase || '',
    title: fields.title || '',
    isPunchClock: entry && entry.id === 'punchclock'
  };
}

export function careerCardHeading(model) {
  if (model.org) return model.org;
  if (model.phase) return model.phase;
  if (model.title) return model.title;
  return '';
}

export function fillCareerCard(root, model, doc) {
  while (root.firstChild) root.removeChild(root.firstChild);
  const title = doc.createElement('h2');
  title.textContent = careerCardHeading(model);
  root.appendChild(title);
  const meta = doc.createElement('p');
  meta.className = 'career-card-meta';
  meta.textContent = model.role ? model.years + ' · ' + model.role : model.years;
  root.appendChild(meta);
  const summary = doc.createElement('p');
  summary.className = 'career-card-summary';
  summary.textContent = model.summary;
  root.appendChild(summary);
  if (model.prop) {
    const prop = doc.createElement('p');
    prop.className = 'career-card-prop';
    prop.textContent = '展件：' + model.prop;
    root.appendChild(prop);
  }
}

export function scanTimelinePrivacy(text) {
  const hits = [];
  if (!text) return hits;
  for (let i = 0; i < PII_PATTERNS.length; i++) {
    const m = text.match(PII_PATTERNS[i]);
    if (m) hits.push(m[0]);
  }
  return hits;
}

const FORBIDDEN_EMPLOYER_SNIPPETS = Object.freeze(['弘真科技', 'hongzhen']);

export function validateTimelineData(data) {
  const entries = data && Array.isArray(data.entries) ? data.entries : [];
  if (entries.length !== CAREER_EXHIBIT_LAYOUT.length) {
    return { ok: false, reason: 'count=' + entries.length };
  }
  const firmware = entries.find((e) => e.id === 'firmware-2013-2022');
  const software = entries.find((e) => e.id === 'software-2022-now');
  const punch = entries.find((e) => e.id === 'punchclock');
  const edu = entries.find((e) => e.id === 'thu-2007');
  if (!firmware || !software || !punch || !edu) return { ok: false, reason: 'phase-ids' };
  if (firmware.phase !== '韌體時期' || !String(firmware.years).includes('2013')) {
    return { ok: false, reason: 'firmware-years' };
  }
  if (software.phase !== '純軟體時期' || !String(software.years).includes('2022')) {
    return { ok: false, reason: 'software-years' };
  }
  if (firmware.org != null && String(firmware.org).trim() !== '') {
    return { ok: false, reason: 'firmware-org' };
  }
  const blob = JSON.stringify(entries);
  for (let f = 0; f < FORBIDDEN_EMPLOYER_SNIPPETS.length; f++) {
    if (blob.includes(FORBIDDEN_EMPLOYER_SNIPPETS[f])) {
      return { ok: false, reason: 'forbidden-employer' };
    }
  }
  if (!software.summary.includes('虛擬貨幣交易所') || !software.summary.includes('運維')) {
    return { ok: false, reason: 'software-twilight-split' };
  }
  if (software.summary.includes('2018') && software.summary.includes('從缺')) {
    return { ok: false, reason: 'blank-gap' };
  }
  for (let i = 0; i < entries.length; i++) {
    const keys = Object.keys(entries[i]);
    for (let j = 0; j < keys.length; j++) {
      if (keys[j] !== 'id' && CARD_FIELD_WHITELIST.indexOf(keys[j]) < 0) {
        return { ok: false, reason: 'extra-field-' + keys[j] };
      }
    }
  }
  const layoutIds = new Set(CAREER_EXHIBIT_LAYOUT.map((row) => row.id));
  for (let k = 0; k < entries.length; k++) {
    if (!layoutIds.has(entries[k].id)) return { ok: false, reason: 'layout-miss-' + entries[k].id };
  }
  return { ok: true };
}

export function careerInteractZones() {
  return CAREER_EXHIBIT_LAYOUT.map((row) =>
    Object.freeze({
      id: 'career:' + row.id,
      entryId: row.id,
      x: row.x,
      z: row.z,
      r: row.r,
      label: CAREER_PROMPT_LABEL,
      action: 'openCareerCard'
    })
  );
}

export function resolveCareerPickHit(hit) {
  if (!hit || !hit.object) return '';
  const ud = hit.object.userData;
  if (ud.careerExhibitId) {
    return { type: 'career-exhibit', entryId: ud.careerExhibitId };
  }
  return '';
}

export function mergeInteractZones(base, extra) {
  return (base || []).concat(extra || []);
}

export function corridorWalkSamples() {
  return CAREER_WALK_SAMPLES;
}

export function labDoorStandPose() {
  const hall = buildingById('computer-site');
  if (!hall || !hall.door) return null;
  return { x: hall.door.x, z: hall.door.z };
}

export function assertCorridorNavigable(walkable, blocked, radius) {
  const r = radius == null ? 0.29 : radius;
  const door = labDoorStandPose();
  if (!door) return { ok: false, reason: 'no-door' };
  if (!walkable(door.x, door.z, r) || blocked(door.x, door.z, r)) {
    return { ok: false, reason: 'door-blocked' };
  }
  const samples = corridorWalkSamples();
  for (let i = 0; i < samples.length; i++) {
    const s = samples[i];
    if (!walkable(s.x, s.z, r) || blocked(s.x, s.z, r)) {
      return { ok: false, reason: 'walk-' + i, x: s.x, z: s.z };
    }
  }
  for (let z = 15.2; z <= 16.85 + 1e-6; z += 0.25) {
    const x = 0.45;
    if (!walkable(x, z, r) || blocked(x, z, r)) {
      return { ok: false, reason: 'door-approach', x, z };
    }
  }
  return { ok: true };
}

export const LAB_DOOR_STAND = Object.freeze({ x: 0.45, z: 16.17 });
export const LAB_FRONT_WALL_Z = 16.95;
export const MAX_ZONE_SIGN_Z = 16.7;
export const ZONE_SIGN_SOUTH_YAW = Math.PI;
export const MIN_EXHIBIT_DOOR_GAP = 1.35;
export const MIN_EXHIBIT_CENTER_GAP = 0.85;
export function careerCardEscapeShouldClose(cardVisible, key) {
  return !!cardVisible && key === 'Escape';
}

export function assertExhibitLayout(door) {
  const d = door || LAB_DOOR_STAND;
  for (let i = 0; i < CAREER_EXHIBIT_LAYOUT.length; i++) {
    const row = CAREER_EXHIBIT_LAYOUT[i];
    const gap = Math.hypot(row.x - d.x, row.z - d.z);
    if (gap < MIN_EXHIBIT_DOOR_GAP - 1e-6) {
      return { ok: false, reason: 'door-gap', id: row.id, gap };
    }
  }
  for (let a = 0; a < CAREER_EXHIBIT_LAYOUT.length; a++) {
    for (let b = a + 1; b < CAREER_EXHIBIT_LAYOUT.length; b++) {
      const ra = CAREER_EXHIBIT_LAYOUT[a];
      const rb = CAREER_EXHIBIT_LAYOUT[b];
      const g = Math.hypot(ra.x - rb.x, ra.z - rb.z);
      const need = MIN_EXHIBIT_CENTER_GAP;
      if (g < need - 1e-6) {
        return { ok: false, reason: 'pair-gap', a: ra.id, b: rb.id, gap: g };
      }
    }
  }
  return { ok: true };
}

export function assertZoneSignLayout() {
  const door = LAB_DOOR_STAND;
  for (let i = 0; i < CAREER_ZONE_SIGNS.length; i++) {
    const s = CAREER_ZONE_SIGNS[i];
    if (s.z > MAX_ZONE_SIGN_Z + 1e-6) {
      return { ok: false, reason: 'sign-z', i, z: s.z };
    }
    if (s.z >= LAB_FRONT_WALL_Z - 0.2) {
      return { ok: false, reason: 'sign-past-wall', i, z: s.z };
    }
    const gap = Math.hypot(s.x - door.x, s.z - door.z);
    if (gap < MIN_EXHIBIT_DOOR_GAP - 1e-6) {
      return { ok: false, reason: 'sign-door-gap', i, gap };
    }
    if (Math.abs(s.yaw - ZONE_SIGN_SOUTH_YAW) > 0.15) {
      return { ok: false, reason: 'sign-yaw', i, yaw: s.yaw };
    }
  }
  const swExhibit = CAREER_EXHIBIT_LAYOUT.find((row) => row.id === 'software-2022-now');
  const swSign = CAREER_ZONE_SIGNS.find((row) => row.phase === '純軟體時期');
  if (swExhibit && swSign && swSign.z >= swExhibit.z - 0.08) {
    return { ok: false, reason: 'sign-behind-software-exhibit', z: swSign.z, exhibitZ: swExhibit.z };
  }
  const hall = buildingById('computer-site');
  if (hall && swSign) {
    const eastEdge = hall.x + hall.w / 2;
    if (swSign.x + 0.29 > eastEdge + 1e-6) {
      return { ok: false, reason: 'sign-past-east-wall', x: swSign.x, eastEdge };
    }
  }
  return { ok: true };
}