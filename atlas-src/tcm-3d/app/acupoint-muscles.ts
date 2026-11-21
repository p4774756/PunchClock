import raw from '../data/acupoint-muscles.json';

export type AcupointMuscleNote = { nearby: string };

const table = raw as Record<string, AcupointMuscleNote>;

export function acupointMuscleNote(code: string): string | null {
 return table[code]?.nearby ?? null;
}

export function acupointMuscleCodes(): string[] {
 return Object.keys(table);
}