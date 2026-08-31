(el) => {
  const doc = el.ownerDocument;
  const win = doc.defaultView || window;
  if (!doc.getElementById('punchclock-highlight-style')) {
    const style = doc.createElement('style');
    style.id = 'punchclock-highlight-style';
    style.textContent = `
    #punchclock-highlight-root {
      position: fixed; inset: 0; pointer-events: none;
      z-index: 2147483647; overflow: visible;
    }
    @keyframes punchclock-manga-pulse {
      0%, 100% { opacity: .92; transform: scale(1); }
      50% { opacity: .48; transform: scale(1.015); }
    }
    @keyframes punchclock-blink {
      0%, 100% { border-color: #ff2d2d; transform: scale(1);
        box-shadow: 0 0 0 2px rgba(255,45,45,.55), 0 0 16px 6px rgba(255,45,45,.35); }
      50% { border-color: rgba(255,45,45,.35); transform: scale(1.01);
        box-shadow: 0 0 0 1px rgba(255,45,45,.12), 0 0 6px 3px rgba(255,45,45,.08); }
    }
    #punchclock-manga-focus {
      position: fixed; inset: 0; pointer-events: none;
      animation: punchclock-manga-pulse 1.3s ease-in-out infinite;
    }
    #punchclock-manga-focus svg line { stroke: #141414; stroke-linecap: round; }
    #punchclock-highlight-overlay {
      position: fixed; pointer-events: none; border: 2px solid #ff2d2d;
      border-radius: 10px; animation: punchclock-blink 1.4s ease-in-out infinite;
      box-sizing: border-box; transform-origin: center;
    }
    #punchclock-poke-arrow {
      position: fixed; pointer-events: none; width: 64px; height: 120px;
      transform-origin: 50% 100%; z-index: 1;
      filter: drop-shadow(0 3px 10px rgba(255,45,45,.5));
      animation: punchclock-poke 1.6s ease-in-out infinite;
    }
    @keyframes punchclock-poke {
      0%, 22%, 100% { transform: translate(-50%, 0); }
      48% { transform: translate(-50%, 30px); }
      58% { transform: translate(-50%, 30px); }
    }
    `;
    (doc.head || doc.documentElement).appendChild(style);
  }
  let root = doc.getElementById('punchclock-highlight-root');
  if (!root) {
    root = doc.createElement('div');
    root.id = 'punchclock-highlight-root';
    (doc.documentElement || doc.body).appendChild(root);
  }
  root.innerHTML = '';
  const rect = el.getBoundingClientRect();
  const cx = rect.left + rect.width / 2;
  const cy = rect.top + rect.height / 2;
  const vw = win.innerWidth;
  const vh = win.innerHeight;
  const outerR = Math.hypot(vw, vh);
  const baseInner = Math.max(rect.width, rect.height) * 0.55 + 36;
  const mangaWrap = doc.createElement('div');
  mangaWrap.id = 'punchclock-manga-focus';
  mangaWrap.style.transformOrigin = cx + 'px ' + cy + 'px';
  const mangaSvg = doc.createElementNS('http://www.w3.org/2000/svg', 'svg');
  mangaSvg.setAttribute('width', String(vw));
  mangaSvg.setAttribute('height', String(vh));
  mangaSvg.setAttribute('viewBox', '0 0 ' + vw + ' ' + vh);
  for (let i = 0; i < 96; i++) {
    const angle = (i / 96) * Math.PI * 2 + (i % 5) * 0.006;
    const innerR = baseInner + ((i * 13) % 44) - 22;
    const line = doc.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', String(cx + Math.cos(angle) * outerR));
    line.setAttribute('y1', String(cy + Math.sin(angle) * outerR));
    line.setAttribute('x2', String(cx + Math.cos(angle) * innerR));
    line.setAttribute('y2', String(cy + Math.sin(angle) * innerR));
    const strokeW = (i % 6 === 0) ? 3.8 : ((i % 3 === 0) ? 2.4 : 1.5);
    line.setAttribute('stroke-width', String(strokeW));
    line.setAttribute('opacity', String(0.5 + (i % 5) * 0.1));
    mangaSvg.appendChild(line);
  }
  mangaWrap.appendChild(mangaSvg);
  root.appendChild(mangaWrap);
  const boxPad = Math.max(14, Math.max(rect.width, rect.height) * 0.16);
  const overlay = doc.createElement('div');
  overlay.id = 'punchclock-highlight-overlay';
  overlay.style.top = (rect.top - boxPad) + 'px';
  overlay.style.left = (rect.left - boxPad) + 'px';
  overlay.style.width = (rect.width + boxPad * 2) + 'px';
  overlay.style.height = (rect.height + boxPad * 2) + 'px';
  root.appendChild(overlay);
  const pokeArrow = doc.createElement('div');
  pokeArrow.id = 'punchclock-poke-arrow';
  pokeArrow.style.left = cx + 'px';
  pokeArrow.style.top = (rect.top - boxPad - 128) + 'px';
  pokeArrow.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 100" width="64" height="120" aria-hidden="true">'
      + '<path fill="#ff2d2d" d="M18 0 h12 v58 L44 58 L24 100 L4 58 h14 Z"/>'
      + '<path fill="#ff5c5c" d="M21 6 h6 v48 L38 58 L24 90 L10 58 h11 Z"/>'
      + '</svg>';
  root.appendChild(pokeArrow);
}
