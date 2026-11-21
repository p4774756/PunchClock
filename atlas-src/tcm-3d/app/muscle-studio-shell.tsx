import {lazy, Suspense, useCallback, useState} from 'react';
import {Eye, EyeOff, Info, Layers3, RotateCcw, X} from 'lucide-react';
import type {View} from './anatomy';
import type {MuscleLayerStep, MusclePick} from './muscle-studio-scene';
import {acupointMuscleNote} from './acupoint-muscles';
import {LOCATION, POINT_BY_CODE} from './teaching';
import type {AtlasLoadProgress} from './load-progress';

const MuscleStudioScene = lazy(() => import('./muscle-studio-scene'));

const LAYER_OPTIONS: {id: MuscleLayerStep; label: string; hint: string}[] = [
 {id: 'superficial', label: '淺層肌', hint: '較表層肌群'},
 {id: 'deep', label: '深層肌', hint: '較深肌肉；淺層半透明'},
 {id: 'bone', label: '骨骼', hint: '肌層半透明，突顯骨面'},
];

interface Props {
 view: View;
 rotate: boolean;
 resetToken: number;
 onViewChange: (v: View) => void;
 onRotateToggle: () => void;
 onReset: () => void;
 onProgress: (p: AtlasLoadProgress | number) => void;
 onError: (msg: string) => void;
 progress: number;
 loadError: string;
}

export default function MuscleStudioShell({
 view,
 rotate,
 resetToken,
 onReset,
 onProgress,
 onError,
 progress,
 loadError,
}: Props) {
 const [layer, setLayer] = useState<MuscleLayerStep>('superficial');
 const [showPoints, setShowPoints] = useState(true);
 const [pointCode, setPointCode] = useState<string | null>('ST36');
 const [musclePick, setMusclePick] = useState<MusclePick | null>(null);

 const point = pointCode ? POINT_BY_CODE.get(pointCode) : null;
 const location = pointCode ? LOCATION[pointCode] : null;
 const muscleNear = pointCode ? acupointMuscleNote(pointCode) : null;

 const dismissPanels = useCallback(() => {
  setMusclePick(null);
 }, []);

 return (
  <>
   <Suspense fallback={null}>
    <MuscleStudioScene
     layer={layer}
     showPoints={showPoints}
     selectedPoint={pointCode}
     view={view}
     rotate={rotate}
     resetToken={resetToken}
     onMusclePick={setMusclePick}
     onPointPick={code => {
      setPointCode(code);
      setMusclePick(null);
     }}
     onProgress={onProgress}
     onError={onError}
    />
   </Suspense>
   <div className="muscle-layer-bar surface" role="group" aria-label="解剖分層">
    <span className="muscle-layer-eyebrow">
     <Layers3 size={14} aria-hidden="true" />
     層級
    </span>
    <p className="muscle-skin-note">第一期無開源全身皮膚模型，皮膚層省略。</p>
    {LAYER_OPTIONS.map(opt => (
     <button
      key={opt.id}
      type="button"
      className={layer === opt.id ? 'selected' : ''}
      aria-pressed={layer === opt.id}
      title={opt.hint}
      onClick={() => setLayer(opt.id)}
     >
      {opt.label}
     </button>
    ))}
    <button
     type="button"
     className="icon-button"
     aria-label={showPoints ? '隱藏穴位標記' : '顯示穴位標記'}
     aria-pressed={showPoints}
     onClick={() => setShowPoints(v => !v)}
    >
     {showPoints ? <Eye size={15} /> : <EyeOff size={15} />}
    </button>
   </div>
   {(progress < 100 || loadError) && (
    <div className={loadError ? 'loading-card woodcut-loading load-error' : 'loading-card woodcut-loading'} role="status">
     <span className="loading-symbol">
      <Layers3 size={23} />
     </span>
     <strong>{loadError ? '解剖模型暫未就緒' : '正在載入肌肉分層'}</strong>
     <p>{loadError || '僅在此模式下載骨骼與肌肉 GLB。'}</p>
     {!loadError && (
      <>
       <div className="load-track load-track-shimmer">
        <i style={{width: `${progress}%`}} />
       </div>
       <small>{progress}%</small>
      </>
     )}
    </div>
   )}
   <div className="muscle-attribution-strip" role="contentinfo">
    <span>Z-Anatomy · CC BY-SA 4.0</span>
    <span>BodyParts3D · CC BY 4.0</span>
    <a href={`${import.meta.env.BASE_URL}ATTRIBUTION.md`} target="_blank" rel="noreferrer">
     署名
    </a>
   </div>
   {musclePick && (
    <aside className="muscle-info-panel surface" aria-live="polite">
     <div className="muscle-info-head">
      <h2>{musclePick.info?.nameZh ?? '肌肉'}</h2>
      <button type="button" className="icon-button" aria-label="關閉肌肉說明" onClick={dismissPanels} autoFocus>
       <X size={17} />
      </button>
     </div>
     {musclePick.info ? (
      <>
       <p className="muscle-info-en">{musclePick.info.nameEn}</p>
       <dl>
        <div>
         <dt>起點</dt>
         <dd>{musclePick.info.origin}</dd>
        </div>
        <div>
         <dt>止點</dt>
         <dd>{musclePick.info.insertion}</dd>
        </div>
        <div>
         <dt>作用</dt>
         <dd>{musclePick.info.action}</dd>
        </div>
       </dl>
      </>
     ) : (
      <p className="muscle-info-fallback">此網格：{musclePick.meshName}。詳細肌解說明待補。</p>
     )}
    </aside>
   )}
   {point && !musclePick && (
    <aside className="muscle-point-panel surface" aria-live="polite">
     <div className="muscle-info-head">
      <h2>
         {point.name} {point.code}
      </h2>
      <button type="button" className="icon-button" aria-label="關閉穴位說明" onClick={() => setPointCode(null)}>
       <X size={17} />
      </button>
     </div>
     <p className="muscle-point-zhuyin">{point.zhuyin}</p>
     {location?.text && <p className="muscle-point-loc">{location.text}</p>}
     {muscleNear && (
      <p className="muscle-point-near">
         <Info size={13} aria-hidden="true" /> 鄰近肌肉：{muscleNear}
      </p>
     )}
     <button type="button" className="text-button" onClick={onReset}>
      <RotateCcw size={13} aria-hidden="true" /> 重設視角
     </button>
    </aside>
   )}
  </>
 );
}