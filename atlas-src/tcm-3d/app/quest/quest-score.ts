export const QUEST_BASE_SCORE=100;
export const QUEST_HINT_SCORE=50;
export const QUEST_COMBO_STEP=20;
export const QUEST_COMBO_CAP=100;
export const QUEST_BONUS_TODAY=200;

export function comboBonus(combo:number):number{
 if(combo<=1)return 0;
 return Math.min(QUEST_COMBO_CAP,(combo-1)*QUEST_COMBO_STEP);
}

export function scoreForAnswer(correct:boolean,combo:number,usedHint:boolean):number{
 if(!correct)return 0;
 if(usedHint)return QUEST_HINT_SCORE;
 return QUEST_BASE_SCORE+comboBonus(combo);
}

export function starsFromRun(correct:number,total:number,elapsedMs:number):0|1|2|3{
 if(total<=0)return 0;
 const rate=correct/total;
 if(rate<.6)return 0;
 if(rate<.8)return 1;
 if(rate<.9)return 2;
 if(elapsedMs<=120_000)return 3;
 return 2;
}