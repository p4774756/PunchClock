import { useCallback, useEffect, useRef, useState } from "react";
import { RotateCcw, Scan, ZoomIn } from "lucide-react";
import type { Acupoint, Meridian } from "../lib/acupoint-data";
import type { AcupointViewer } from "../lib/three/viewer";

type Props = {
  meridian: Meridian;
  autoRotate: boolean;
  onAutoRotate: (v: boolean) => void;
  selected: Acupoint | null;
  onSelect: (p: Acupoint | null) => void;
  showBilateral: boolean;
};

export function BodyViewer({
  meridian,
  autoRotate,
  onAutoRotate,
  selected,
  onSelect,
  showBilateral,
}: Props) {
  const mountRef = useRef<HTMLDivElement>(null);
  const viewerRef = useRef<AcupointViewer | null>(null);
  const [hovered, setHovered] = useState<Acupoint | null>(null);
  const [loading, setLoading] = useState(true);
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let viewer: AcupointViewer | null = null;

    void import("../lib/three/viewer").then(({ AcupointViewer: Viewer }) => {
      if (cancelled || !mountRef.current) return;
      viewer = new Viewer(mountRef.current, {
        onSelect,
        onHover: setHovered,
        onLoading: (isLoading, value) => {
          setLoading(isLoading);
          setProgress(value);
        },
      });
      viewerRef.current = viewer;
      viewer.setAutoRotate(autoRotate);
      viewer.setMeridian(meridian, showBilateral);
    });

    return () => {
      cancelled = true;
      viewerRef.current = null;
      viewer?.dispose();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount once
  }, []);

  useEffect(() => {
    viewerRef.current?.setMeridian(meridian, showBilateral);
  }, [meridian, showBilateral]);

  useEffect(() => {
    viewerRef.current?.setAutoRotate(autoRotate);
  }, [autoRotate]);

  useEffect(() => {
    if (selected) viewerRef.current?.focusPoint(selected);
  }, [selected]);

  const calloutRef = useCallback((node: HTMLDivElement | null) => {
    viewerRef.current?.attachCallout(node);
  }, []);

  const label = selected ?? hovered;
  const showLoader = loading && progress < 0.98;

  return (
    <section className="viewport">
      <div className="viewport-stage" ref={mountRef} />

      {showLoader && (
        <div className="viewport-loader" role="status">
          <div className="loader-ring" />
          <p>加載人體模型…</p>
          <small>{Math.round(progress * 100)}%</small>
        </div>
      )}

      <div
        className="callout"
        ref={calloutRef}
        style={{ opacity: selected ? 1 : 0 }}
        aria-hidden={!selected}
      >
        {selected && (
          <>
            <strong>{selected.name}</strong>
            <span>{selected.code}</span>
          </>
        )}
      </div>

      <div className="viewport-tools">
        <button
          type="button"
          className={autoRotate ? "active" : ""}
          onClick={() => onAutoRotate(!autoRotate)}
          title="自動旋轉"
        >
          <Scan size={16} />
          旋轉
        </button>
        <button type="button" onClick={() => viewerRef.current?.resetView()} title="復位視角">
          <RotateCcw size={16} />
          復位
        </button>
        <button type="button" title="滾輪縮放" disabled>
          <ZoomIn size={16} />
          縮放
        </button>
      </div>

      <div className="viewport-hint">
        <span className="hint-dot" style={{ background: meridian.accent }} />
        三維經絡標本 · 穴位已吸附體表
        {label && (
          <em>
            {label.name} · {label.pinyin}
          </em>
        )}
      </div>

      <div className="viewport-stamp">
        <b>{meridian.shortName}</b>
        <small>{meridian.element}</small>
      </div>
    </section>
  );
}
