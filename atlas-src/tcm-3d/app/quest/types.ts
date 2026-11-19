export type MeridianQuestId='LU'|'LI'|'ST'|'SP'|'HT'|'SI'|'BL'|'KI'|'PC'|'TE'|'GB'|'LR';

export type QuestQuestionKind='locate'|'indication'|'location';

export interface QuestPointRecord{
 code:string;
 name:string;
 nameVariant?:string;
 meridianId:MeridianQuestId;
 order:number;
 location:string;
 indications:string[];
 category?:string;
 source:string;
 uncertain?:string;
 reviewed:false;
}

export interface LocateQuestion{
 kind:'locate';
 code:string;
 prompt:string;
 answerCode:string;
}

export interface ChoiceQuestion{
 kind:'indication'|'location';
 code:string;
 prompt:string;
 answerCode:string;
 choices:string[];
 symptom?:string;
 locationText?:string;
}

export type QuestQuestion=LocateQuestion|ChoiceQuestion;

export interface StageQuestionPlan{
 meridianId:MeridianQuestId;
 seed:number;
 questions:QuestQuestion[];
 locateCount:number;
 bonusToday?:QuestQuestion|null;
}

export interface StageRuntime{
 meridianId:MeridianQuestId;
 questions:QuestQuestion[];
 index:number;
 combo:number;
 score:number;
 correct:number;
 hintsLeft:number;
 usedHint:boolean;
 elapsedMs:number;
 paused:boolean;
 pauseStartedAt:number|null;
 wrongCodes:string[];
 maxCombo:number;
 bonusScore:number;
 startedAt:number;
}

export interface StageProgress{
 stars:0|1|2|3;
 bestTimeMs:number|null;
 bestScore:number;
 clearedAt:string|null;
}

export interface QuestProgressV1{
 version:1;
 stages:Partial<Record<MeridianQuestId,StageProgress>>;
}