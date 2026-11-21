/** Q 版私人秘書方塊頭像（HUD 用，2D 扁平上色）。 */

export function drawSecretaryAvatar(ctx, size) {
  const s = size || 64;
  ctx.clearRect(0, 0, s, s);
  ctx.fillStyle = '#e8f2fc';
  ctx.fillRect(0, 0, s, s);

  const hair = '#f4a8c8';
  const skin = '#ffe8dc';
  const shirt = '#8a96a8';
  const navy = '#163a70';

  ctx.fillStyle = hair;
  ctx.beginPath();
  ctx.ellipse(s * 0.5, s * 0.38, s * 0.32, s * 0.36, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillRect(s * 0.18, s * 0.34, s * 0.64, s * 0.42);

  ctx.fillStyle = skin;
  ctx.beginPath();
  ctx.arc(s * 0.5, s * 0.4, s * 0.22, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = navy;
  ctx.fillRect(s * 0.42, s * 0.36, s * 0.05, s * 0.06);
  ctx.fillRect(s * 0.53, s * 0.36, s * 0.05, s * 0.06);
  ctx.fillRect(s * 0.44, s * 0.46, s * 0.12, s * 0.03);

  ctx.fillStyle = shirt;
  ctx.fillRect(s * 0.3, s * 0.58, s * 0.4, s * 0.3);
  ctx.strokeStyle = navy;
  ctx.lineWidth = Math.max(1, s * 0.03);
  ctx.strokeRect(s * 0.3, s * 0.58, s * 0.4, s * 0.3);
}