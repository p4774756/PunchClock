import type {StageProgress} from './types';

/** Compare run against progress *before* saveStage merges this attempt. */
export function questNewRecordFlags(
 prev: StageProgress | undefined,
 stars: 0|1|2|3,
 elapsedMs: number,
 score: number,
): {newTime: boolean; newScore: boolean; show: boolean}{
 const newScore=score>(prev?.bestScore??0);
 const newTime=stars>=1&&(prev?.bestTimeMs==null||elapsedMs<prev.bestTimeMs);
 const firstClear=stars>=1&&(prev?.stars??0)<1;
 return {newTime,newScore,show:newScore||newTime||firstClear};
}