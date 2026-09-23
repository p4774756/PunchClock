(function () {
  const BEST_KEY = 'punchclock.flappyBest';
  const W = 480;
  const H = 640;
  const GROUND_H = 72;
  const BIRD_R = 16;
  const PIPE_W = 64;
  const GAP_MIN = 132;
  const GAP_MAX = 158;
  const GRAVITY = 0.42;
  const FLAP_V = -7.2;
  const BASE_SPEED = 2.35;

  const canvas = document.getElementById('flappyCanvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const scoreEl = document.getElementById('flappyScore');
  const bestEl = document.getElementById('flappyBest');
  const overlay = document.getElementById('flappyOverlay');
  const overlayTitle = document.getElementById('flappyOverlayTitle');
  const overlaySub = document.getElementById('flappyOverlaySub');
  const restartBtn = document.getElementById('flappyRestartBtn');

  let best = 0;
  try {
    best = Math.max(0, parseInt(localStorage.getItem(BEST_KEY) || '0', 10) || 0);
  } catch (e) {}
  if (bestEl) bestEl.textContent = String(best);

  let active = false;
  let running = false;
  let raf = 0;
  let state = 'ready'; // ready | playing | dead
  let birdY = H * 0.42;
  let birdV = 0;
  let birdTilt = 0;
  let pipes = [];
  let score = 0;
  let frame = 0;
  let groundX = 0;
  let steamPhase = 0;

  function setOverlay(show, title, sub) {
    if (!overlay) return;
    overlay.classList.toggle('is-hidden', !show);
    if (title != null && overlayTitle) overlayTitle.textContent = title;
    if (sub != null && overlaySub) overlaySub.textContent = sub;
  }

  function setScore(n) {
    score = n;
    if (scoreEl) scoreEl.textContent = String(score);
    if (score > best) {
      best = score;
      if (bestEl) bestEl.textContent = String(best);
      try { localStorage.setItem(BEST_KEY, String(best)); } catch (e) {}
    }
  }

  function resetRound(keepOverlay) {
    birdY = H * 0.42;
    birdV = 0;
    birdTilt = 0;
    pipes = [];
    frame = 0;
    groundX = 0;
    steamPhase = 0;
    setScore(0);
    state = 'ready';
    if (!keepOverlay) {
      setOverlay(true, '飛杯摩卡', '點擊或按空白鍵開始');
    }
    draw();
  }

  function spawnPipe(x) {
    const playH = H - GROUND_H;
    const gap = GAP_MIN + Math.random() * (GAP_MAX - GAP_MIN);
    const margin = 48;
    const top = margin + Math.random() * Math.max(8, playH - gap - margin * 2);
    pipes.push({ x: x, gapTop: top, gap: gap, scored: false });
  }

  function startPlaying() {
    if (state === 'dead') resetRound(true);
    state = 'playing';
    birdV = FLAP_V;
    setOverlay(false);
    if (pipes.length === 0) {
      spawnPipe(W + 40);
      spawnPipe(W + 40 + 210);
    }
  }

  function flap() {
    if (!active) return;
    if (state === 'ready') {
      startPlaying();
      return;
    }
    if (state === 'playing') {
      birdV = FLAP_V;
      return;
    }
    if (state === 'dead') {
      resetRound(false);
      startPlaying();
    }
  }

  function die() {
    if (state !== 'playing') return;
    state = 'dead';
    setOverlay(true, '濺出咖啡了', '分數 ' + score + ' · 點擊再飛');
  }

  function pipeSpeed() {
    return BASE_SPEED + Math.min(1.6, score * 0.045);
  }

  function update() {
    if (state !== 'playing') return;
    frame += 1;
    steamPhase += 0.08;
    birdV += GRAVITY;
    birdY += birdV;
    birdTilt = Math.max(-0.55, Math.min(1.1, birdV * 0.08));

    const speed = pipeSpeed();
    groundX = (groundX - speed) % 48;

    for (let i = 0; i < pipes.length; i++) {
      pipes[i].x -= speed;
    }
    while (pipes.length && pipes[0].x + PIPE_W < -10) pipes.shift();

    const last = pipes[pipes.length - 1];
    if (!last || last.x < W - 200) {
      spawnPipe((last ? last.x : W) + 200 + Math.random() * 30);
    }

    const bx = W * 0.28;
    const by = birdY;
    const playBottom = H - GROUND_H;

    if (by + BIRD_R >= playBottom || by - BIRD_R <= 0) {
      die();
      return;
    }

    for (let i = 0; i < pipes.length; i++) {
      const p = pipes[i];
      const left = p.x;
      const right = p.x + PIPE_W;
      const gapBot = p.gapTop + p.gap;
      if (bx + BIRD_R > left && bx - BIRD_R < right) {
        if (by - BIRD_R < p.gapTop || by + BIRD_R > gapBot) {
          die();
          return;
        }
      }
      if (!p.scored && right < bx - BIRD_R) {
        p.scored = true;
        setScore(score + 1);
      }
    }
  }

  function roundRect(x, y, w, h, r) {
    const rr = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + rr, y);
    ctx.arcTo(x + w, y, x + w, y + h, rr);
    ctx.arcTo(x + w, y + h, x, y + h, rr);
    ctx.arcTo(x, y + h, x, y, rr);
    ctx.arcTo(x, y, x + w, y, rr);
    ctx.closePath();
  }

  function drawBackground() {
    const sky = ctx.createLinearGradient(0, 0, 0, H);
    sky.addColorStop(0, '#b7d9ea');
    sky.addColorStop(0.55, '#d9ebe4');
    sky.addColorStop(1, '#f5e8b8');
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, W, H);

    ctx.fillStyle = 'rgba(255,255,255,0.55)';
    for (let i = 0; i < 4; i++) {
      const cx = ((i * 140 + frame * 0.15) % (W + 80)) - 40;
      const cy = 60 + i * 38;
      ctx.beginPath();
      ctx.ellipse(cx, cy, 42, 16, 0, 0, Math.PI * 2);
      ctx.ellipse(cx + 28, cy + 4, 30, 12, 0, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.fillStyle = '#7aa87a';
    ctx.beginPath();
    ctx.moveTo(0, H - GROUND_H - 28);
    ctx.quadraticCurveTo(120, H - GROUND_H - 58, 240, H - GROUND_H - 30);
    ctx.quadraticCurveTo(360, H - GROUND_H - 8, W, H - GROUND_H - 36);
    ctx.lineTo(W, H - GROUND_H);
    ctx.lineTo(0, H - GROUND_H);
    ctx.fill();
  }

  function drawPipe(p) {
    const gapBot = p.gapTop + p.gap;
    const body = '#6b3e2e';
    const lip = '#8b5a3c';
    const cream = '#fdf4d3';

    ctx.fillStyle = body;
    ctx.fillRect(p.x, 0, PIPE_W, p.gapTop);
    ctx.fillRect(p.x, gapBot, PIPE_W, H - GROUND_H - gapBot);

    ctx.fillStyle = lip;
    roundRect(p.x - 4, p.gapTop - 18, PIPE_W + 8, 18, 4);
    ctx.fill();
    roundRect(p.x - 4, gapBot, PIPE_W + 8, 18, 4);
    ctx.fill();

    ctx.fillStyle = cream;
    ctx.fillRect(p.x + 8, 8, 8, Math.max(0, p.gapTop - 28));
    ctx.fillRect(p.x + 8, gapBot + 22, 8, Math.max(0, H - GROUND_H - gapBot - 30));
  }

  function drawGround() {
    const y = H - GROUND_H;
    ctx.fillStyle = '#5c3a24';
    ctx.fillRect(0, y, W, GROUND_H);
    ctx.fillStyle = '#ea5420';
    ctx.fillRect(0, y, W, 6);
    ctx.fillStyle = '#7a4a2e';
    for (let x = groundX; x < W + 48; x += 48) {
      ctx.fillRect(x, y + 14, 28, 8);
      ctx.fillRect(x + 10, y + 34, 22, 8);
      ctx.fillRect(x + 4, y + 52, 26, 8);
    }
  }

  function drawBird() {
    const x = W * 0.28;
    const y = birdY;
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(birdTilt);

    // cup body
    ctx.fillStyle = '#ffffff';
    ctx.strokeStyle = '#096597';
    ctx.lineWidth = 2.5;
    roundRect(-14, -10, 28, 26, 5);
    ctx.fill();
    ctx.stroke();

    // coffee surface
    ctx.fillStyle = '#5c3a24';
    roundRect(-11, -7, 22, 8, 3);
    ctx.fill();

    // handle
    ctx.beginPath();
    ctx.arc(16, 2, 8, -Math.PI * 0.45, Math.PI * 0.45);
    ctx.strokeStyle = '#096597';
    ctx.lineWidth = 3;
    ctx.stroke();

    // MC mark
    ctx.fillStyle = '#ea5420';
    ctx.font = 'bold 9px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('MC', 0, 12);

    // steam
    ctx.strokeStyle = 'rgba(255,255,255,0.75)';
    ctx.lineWidth = 2;
    for (let i = 0; i < 3; i++) {
      const sx = -6 + i * 6;
      const sy = -14 - Math.sin(steamPhase + i) * 3;
      ctx.beginPath();
      ctx.moveTo(sx, sy);
      ctx.quadraticCurveTo(sx + 3, sy - 8, sx, sy - 14);
      ctx.stroke();
    }

    ctx.restore();
  }

  function drawScoreHud() {
    if (state === 'ready') return;
    ctx.fillStyle = 'rgba(63,52,52,0.55)';
    ctx.font = 'bold 36px "微軟正黑體", sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(String(score), W / 2 + 1, 54 + 1);
    ctx.fillStyle = '#fff';
    ctx.fillText(String(score), W / 2, 54);
  }

  function draw() {
    drawBackground();
    for (let i = 0; i < pipes.length; i++) drawPipe(pipes[i]);
    drawGround();
    drawBird();
    drawScoreHud();

    if (state === 'ready' && !overlay.classList.contains('is-hidden')) {
      // idle bob
      birdY = H * 0.42 + Math.sin(frame * 0.05) * 6;
      frame += 1;
      steamPhase += 0.06;
    }
  }

  function loop() {
    if (!active || !running) return;
    update();
    draw();
    raf = requestAnimationFrame(loop);
  }

  function startLoop() {
    if (running) return;
    running = true;
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(loop);
  }

  function stopLoop() {
    running = false;
    cancelAnimationFrame(raf);
    raf = 0;
  }

  function activate() {
    active = true;
    if (state === 'ready') {
      setOverlay(true, '飛杯摩卡', '點擊或按空白鍵開始');
    } else if (state === 'dead') {
      setOverlay(true, '濺出咖啡了', '分數 ' + score + ' · 點擊再飛');
    } else if (state === 'playing') {
      setOverlay(false);
    }
    canvas.focus({ preventScroll: true });
    startLoop();
    draw();
  }

  function deactivate() {
    active = false;
    stopLoop();
    if (state === 'playing') {
      // soft-pause: freeze mid-flight until tab returns
      setOverlay(true, '暫停中', '回到此分頁繼續');
    }
  }

  function onPointer(e) {
    e.preventDefault();
    flap();
  }

  function onKey(e) {
    if (!active) return;
    if (e.code === 'Space' || e.code === 'ArrowUp' || e.key === ' ') {
      e.preventDefault();
      flap();
    }
  }

  canvas.addEventListener('pointerdown', onPointer);
  canvas.addEventListener('keydown', onKey);
  window.addEventListener('keydown', function (e) {
    if (!active) return;
    if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) return;
    if (e.code === 'Space' || e.code === 'ArrowUp') {
      e.preventDefault();
      flap();
    }
  });

  if (restartBtn) {
    restartBtn.addEventListener('click', function () {
      if (!active) return;
      resetRound(false);
      canvas.focus({ preventScroll: true });
    });
  }

  document.addEventListener('visibilitychange', function () {
    if (document.hidden && active && state === 'playing') {
      setOverlay(true, '暫停中', '回到頁面繼續');
      stopLoop();
    } else if (!document.hidden && active) {
      if (state === 'playing') setOverlay(false);
      startLoop();
    }
  });

  resetRound(false);
  draw();

  window.PunchClockFlappy = {
    activate: activate,
    deactivate: deactivate
  };

  // dashboard.js may switch tabs before this script loads
  const panel = document.getElementById('panel-flappy');
  if (panel && panel.classList.contains('is-active')) {
    activate();
  }
})();
