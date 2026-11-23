import type {AvatarId} from './avatars';
import {ADULT_OUTFITS,DEFAULT_ADULT_OUTFIT_ID,type AdultOutfitId} from './adult-outfits';
import {ANIME_OUTFITS,DEFAULT_OUTFIT_ID,type AssistantOutfitId,type AnimeOutfit} from './outfits';

export type OutfitId=AssistantOutfitId|AdultOutfitId;

export function outfitsForAvatar(avatarId:AvatarId):AnimeOutfit[]{
 return avatarId==='adult'?ADULT_OUTFITS:ANIME_OUTFITS;
}

export function defaultOutfitForAvatar(avatarId:AvatarId):OutfitId{
 return avatarId==='adult'?DEFAULT_ADULT_OUTFIT_ID:DEFAULT_OUTFIT_ID;
}

export function outfitBelongsToAvatar(avatarId:AvatarId,id:OutfitId):boolean{
 return outfitsForAvatar(avatarId).some(o=>o.id===id);
}

export function animeOutfitForAvatar(avatarId:AvatarId,id:OutfitId|string|null|undefined):AnimeOutfit{
 const list=outfitsForAvatar(avatarId);
 return list.find(o=>o.id===id)??list[0];
}

export function isAdultOutfitId(id:OutfitId):id is AdultOutfitId{
 return id==='suit'||id==='casual'||id==='practice'||id==='sheer';
}

export function isAssistantOutfitId(id:OutfitId):id is AssistantOutfitId{
 return id==='school'||id==='study-body';
}

/** Alternate rig must be built lazily (skin paint or sheer layer). */
export function outfitNeedsAltBuild(avatarId:AvatarId,id:OutfitId):boolean{
 if(avatarId==='assistant')return id==='study-body';
 if(avatarId==='adult')return id==='practice'||id==='sheer';
 return false;
}