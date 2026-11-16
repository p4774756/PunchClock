/** Camera / scene chrome shared by the character viewer (anatomy layers removed). */
export type View = 'three-quarter'|'front'|'back'|'side';
export interface SceneState {
 inspectorOpen?:boolean;
 explode:number;
 visible:string[];
 selected:string[];
 isolate:boolean;
 view:View;
 rotate:boolean;
 reset:number;
}
