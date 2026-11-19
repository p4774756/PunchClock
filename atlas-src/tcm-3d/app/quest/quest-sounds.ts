const MUTE_KEY='punchclock.meridianQuest.mute';

let ctx:AudioContext|null=null;

function audio(){
 if(typeof window==='undefined')return null;
 if(!ctx)ctx=new AudioContext();
 return ctx;
}

export function questMuted():boolean{
 try{return localStorage.getItem(MUTE_KEY)==='1';}catch{return false;}
}

export function setQuestMuted(mute:boolean){
 try{localStorage.setItem(MUTE_KEY,mute?'1':'0');}catch{/* ignore */}
}

function tone(freq:number,duration:number,type:OscillatorType='sine',gain=.12){
 const ac=audio();
 if(!ac||questMuted())return;
 const o=ac.createOscillator();
 const g=ac.createGain();
 o.type=type;o.frequency.value=freq;
 g.gain.value=gain;
 o.connect(g);g.connect(ac.destination);
 const t=ac.currentTime;
 g.gain.setValueAtTime(gain,t);
 g.gain.exponentialRampToValueAtTime(.001,t+duration);
 o.start(t);o.stop(t+duration+.02);
}

export function playQuestCorrect(){tone(880,.12,'triangle',.1);setTimeout(()=>tone(1175,.08,'triangle',.08),60);}
export function playQuestWrong(){tone(220,.18,'sawtooth',.07);}
export function playQuestStar(){tone(660,.1,'square',.08);setTimeout(()=>tone(990,.12,'square',.09),90);}