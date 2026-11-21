import raw from '../data/muscle-catalog.json';

export interface MuscleInfo {
 nameZh: string;
 nameEn: string;
 origin: string;
 insertion: string;
 action: string;
}

type RawCatalog = Record<string, MuscleInfo>;

const catalog = raw as RawCatalog;

/** Match mesh / material name fragments to catalog entries (Z-Anatomy English TA names). */
export function muscleInfoForMesh(meshName: string): MuscleInfo | null {
 const n = meshName.replace(/[._]/g, ' ').trim();
 for (const key of Object.keys(catalog)) {
  if (n.toLowerCase().includes(key.toLowerCase())) return catalog[key];
 }
 return null;
}

export function muscleCatalogKeys(): string[] {
 return Object.keys(catalog);
}