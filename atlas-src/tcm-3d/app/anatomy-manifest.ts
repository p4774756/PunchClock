import manifest from '../data/anatomy-manifest.json';
import {publicAssetUrl} from './public-assets';

export type AnatomyLayer = 'skeleton' | 'muscle-superficial' | 'muscle-deep';
export type AnatomyRegion = 'head-neck' | 'trunk' | 'arm' | 'leg';

export interface AnatomyPart {
 id: string;
 url: string;
 region: string;
 layer: string;
 bytes: number;
 digest?: string;
}

export interface AnatomyManifest {
 source: string;
 initialLoad: string[];
 lazyRegions: string[];
 parts: AnatomyPart[];
 totalGzipBytes: number;
 initialGzipBytes: number;
}

export const ANATOMY_MANIFEST = manifest as AnatomyManifest;

export function anatomyPartUrl(part: AnatomyPart): string {
 return publicAssetUrl(`/${part.url}`);
}

export function anatomyPartsForLayer(layer: AnatomyLayer): AnatomyPart[] {
 return ANATOMY_MANIFEST.parts.filter(p => p.layer === layer);
}

export function anatomyInitialPartIds(): Set<string> {
 return new Set(ANATOMY_MANIFEST.initialLoad);
}