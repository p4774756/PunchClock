"""Bake pedestal plaque text PNG (macOS CJK fonts). Usage: python3 gen_plaque_texture.py <out.png>"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 640, 160
TEXT = '人體針灸銅人'
BG = (58, 42, 24, 255)
FG = (232, 200, 120, 255)


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('plaque_text.png')
    pil = Image.new('RGBA', (W, H), BG)
    draw = ImageDraw.Draw(pil)
    font = None
    for fp in (
        '/System/Library/Fonts/STHeiti Medium.ttc',
        '/System/Library/Fonts/Hiragino Sans GB.ttc',
        '/System/Library/Fonts/PingFang.ttc',
    ):
        p = Path(fp)
        if not p.is_file():
            continue
        try:
            font = ImageFont.truetype(str(p), 96, index=0)
            break
        except OSError:
            try:
                font = ImageFont.truetype(str(p), 96)
                break
            except OSError:
                continue
    if font is None:
        raise SystemExit('no CJK font')
    bbox = draw.textbbox((0, 0), TEXT, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = (W - tw) // 2 - bbox[0]
    ty = int((H - th) * 0.5) - bbox[1]
    draw.text((tx, ty), TEXT, font=font, fill=FG)
    bright = sum(
        1
        for y in range(H)
        for x in range(W)
        if pil.getpixel((x, y))[0] > 140 and pil.getpixel((x, y))[1] > 110
    )
    print(f'plaque bright_pixels={bright} size={tw}x{th}')
    out.parent.mkdir(parents=True, exist_ok=True)
    pil.save(out)
    print(f'WROTE {out}')


if __name__ == '__main__':
    main()