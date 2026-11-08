import { useEffect, useMemo, useRef, useState } from "react";
import gsap from "gsap";
import {
  BookOpen,
  Compass,
  Search,
  Sparkles,
  X,
  LibraryBig,
  ArrowRight,
  Activity,
} from "lucide-react";
import {
  getPointDetail,
  meridianById,
  meridians,
  searchPoints,
  type Acupoint,
  type MeridianId,
} from "../lib/acupoint-data";
import { BodyViewer } from "./BodyViewer";

export function AcupointApp() {
  const [meridianId, setMeridianId] = useState<MeridianId>("li");
  const [autoRotate, setAutoRotate] = useState(true);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Acupoint | null>(null);
  const [showBilateral, setShowBilateral] = useState(true);
  const [mobileLibrary, setMobileLibrary] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);

  const meridian = meridianById[meridianId];
  const detail = selected ? getPointDetail(selected) : null;

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return meridians;
    return meridians.filter(
      (m) =>
        m.name.includes(q) ||
        m.shortName.includes(q) ||
        m.element.includes(q) ||
        m.points.some(
          (p) =>
            p.name.includes(q) ||
            p.pinyin.toLowerCase().includes(q) ||
            p.code.toLowerCase().includes(q),
        ),
    );
  }, [query]);

  const searchHits = useMemo(() => (query.trim().length >= 1 ? searchPoints(query).slice(0, 12) : []), [query]);

  useEffect(() => {
    if (!contentRef.current) return;
    gsap.fromTo(
      contentRef.current.querySelectorAll("[data-reveal]"),
      { opacity: 0, y: 8 },
      { opacity: 1, y: 0, duration: 0.42, stagger: 0.03, ease: "power2.out", overwrite: true },
    );
  }, [meridianId, selected?.id]);

  const selectMeridian = (id: MeridianId) => {
    setMeridianId(id);
    setSelected(null);
    setMobileLibrary(false);
  };

  const selectPoint = (point: Acupoint | null) => {
    if (point && point.meridianId !== meridianId) {
      setMeridianId(point.meridianId);
    }
    setSelected(point);
  };

  return (
    <main className="app-shell">
      <header className="topbar">
        <button className="brand" type="button" onClick={() => selectMeridian("li")}>
          <strong>
            經絡穴位圖<sup>✦</sup>
          </strong>
          <em>循經取穴 · 立體明堂</em>
        </button>
        <nav className="main-nav" aria-label="主導航">
          <button type="button" className="active">
            <Compass size={17} /> 探索
          </button>
          <button type="button">
            <Activity size={17} /> 十四經
          </button>
          <button type="button">
            <BookOpen size={17} /> 取穴法
          </button>
        </nav>
        <label className="search-box">
          <Search size={17} />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="搜索穴位、經絡、拼音…"
          />
          {query && (
            <button type="button" className="clear-q" onClick={() => setQuery("")} aria-label="清除">
              <X size={14} />
            </button>
          )}
        </label>
        <button
          className="mobile-library-trigger"
          onClick={() => setMobileLibrary(true)}
          aria-label="打開經絡列表"
          type="button"
        >
          <LibraryBig size={20} />
        </button>
      </header>

      {searchHits.length > 0 && (
        <div className="search-results">
          {searchHits.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => {
                selectPoint(p);
                setQuery("");
              }}
            >
              <b>{p.name}</b>
              <span>
                {p.code} · {meridianById[p.meridianId].shortName}
              </span>
            </button>
          ))}
        </div>
      )}

      <div className="workspace">
        <aside className={`organ-library ${mobileLibrary ? "open" : ""}`}>
          <div className="panel-heading">
            <span>十四經脈</span>
            <button
              aria-label="關閉"
              className="mobile-close"
              onClick={() => setMobileLibrary(false)}
              type="button"
            >
              <X size={17} />
            </button>
          </div>
          <div className="organ-list">
            {filtered.map((item) => (
              <button
                type="button"
                key={item.id}
                className={`organ-item ${meridianId === item.id ? "active" : ""}`}
                onClick={() => selectMeridian(item.id)}
                style={{ "--item-accent": item.accent } as React.CSSProperties}
              >
                <span className="organ-glyph" style={{ background: `${item.accent}22`, color: item.accent }}>
                  {item.nature}
                </span>
                <span>
                  <b>{item.shortName}</b>
                  <small>
                    {item.name} · {item.element}
                  </small>
                </span>
              </button>
            ))}
          </div>
          <label className="bilateral-toggle">
            <input
              type="checkbox"
              checked={showBilateral}
              onChange={(e) => setShowBilateral(e.target.checked)}
            />
            顯示雙側穴位
          </label>
          <blockquote>
            <Sparkles size={18} />
            <p>
              經脈者，
              <br />
              所以能決死生…
            </p>
            <em>— 《靈樞·經脈》</em>
          </blockquote>
        </aside>

        <BodyViewer
          meridian={meridian}
          autoRotate={autoRotate}
          onAutoRotate={setAutoRotate}
          selected={selected}
          onSelect={selectPoint}
          showBilateral={showBilateral}
        />

        <aside className="info-panel" ref={contentRef}>
          {detail ? (
            <>
              <div className="info-kicker" data-reveal>
                <span className="kicker-dot" style={{ background: meridian.accent }} />
                {meridian.name}
              </div>
              <div className="info-title-row" data-reveal>
                <div>
                  <h1>{detail.displayName}</h1>
                  <em>
                    {detail.pinyin} · {detail.code}
                  </em>
                </div>
                <span className="specimen-stamp" style={{ borderColor: meridian.accent }}>
                  {detail.side === "left" ? "左" : detail.side === "right" ? "右" : "中"}
                </span>
              </div>
              <p className="description" data-reveal>
                {detail.overview}
              </p>
              <div className="rule" />
              <h2 data-reveal>定位取穴</h2>
              <p className="body-text" data-reveal>
                {detail.location}
              </p>
              <h2 data-reveal>主治概要</h2>
              <p className="body-text" data-reveal>
                {detail.indications}
              </p>
              {detail.url && (
                <a className="lesson-button" data-reveal href={detail.url} target="_blank" rel="noreferrer">
                  查閱醫學百科 <ArrowRight size={16} />
                </a>
              )}
              <button className="ghost-btn" data-reveal type="button" onClick={() => setSelected(null)}>
                返回經絡概覽
              </button>
            </>
          ) : (
            <>
              <div className="info-kicker" data-reveal>
                <span className="kicker-dot" style={{ background: meridian.accent }} />
                {meridian.nature}經 · {meridian.element}
              </div>
              <div className="info-title-row" data-reveal>
                <div>
                  <h1>{meridian.shortName}</h1>
                  <em>{meridian.poetic}</em>
                </div>
                <span className="specimen-stamp" style={{ borderColor: meridian.accent }}>
                  {meridian.points.filter((p) => p.side !== "left").length}
                  <small>穴</small>
                </span>
              </div>
              <p className="description" data-reveal>
                {meridian.description}
              </p>
              <div className="rule" />
              <h2 data-reveal>循行提要</h2>
              <p className="body-text" data-reveal>
                {meridian.pathHint}
              </p>
              <h2 data-reveal>本經穴位</h2>
              <div className="point-grid" data-reveal>
                {meridian.points
                  .filter((p) => p.side === "right" || p.side === "mid")
                  .map((p) => (
                    <button key={p.id} type="button" className="point-chip" onClick={() => selectPoint(p)}>
                      <b>{p.name}</b>
                      <small>{p.code}</small>
                    </button>
                  ))}
              </div>
              <div className="fun-note" data-reveal>
                <Sparkles size={15} />
                <p>
                  <b>操作提示</b>
                  拖拽旋轉人體，滾輪縮放；點擊光點或下方穴位名稱即可查看詳解。
                </p>
              </div>
            </>
          )}
        </aside>
      </div>
    </main>
  );
}
