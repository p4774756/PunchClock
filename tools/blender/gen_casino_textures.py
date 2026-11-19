#!/usr/bin/env python3
"""產生賭場 v3 低多邊形貼圖（512／1024，PNG）。不需 Blender。"""
from __future__ import annotations

import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

_HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(_HERE, 'textures', 'casino')


def _font(size: int):
    for name in (
        'PingFang TC', 'Heiti TC', 'STHeiti', 'Arial Unicode MS',
        'Hiragino Sans GB', 'Noto Sans CJK TC', 'Apple SD Gothic Neo',
    ):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def _noise_stone(w=512, h=512, seed=193):
    rnd = random.Random(seed)
    img = Image.new('RGB', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            n = rnd.randint(0, 28)
            v = 42 + n + int(12 * math.sin(x * 0.08) * math.cos(y * 0.06))
            px[x, y] = (v, v - 2, v + 4)
    draw = ImageDraw.Draw(img)
    for _ in range(120):
        x0, y0 = rnd.randint(0, w - 1), rnd.randint(0, h - 1)
        c = rnd.randint(28, 52)
        draw.line((x0, y0, x0 + rnd.randint(-30, 30), y0 + rnd.randint(-8, 8)), fill=(c, c - 3, c + 2), width=1)
    return img


def _poker_card(suit: str, rank: str, color_rgb, w=256, h=352):
    img = Image.new('RGB', (w, h), (0xf8, 0xf4, 0xec))
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, w - 1, h - 1), outline=(0x20, 0x18, 0x14), width=4)
    draw.rectangle((8, 8, w - 9, h - 9), outline=(0xd4, 0xa8, 0x4b), width=2)
    f_big = _font(112)
    f_sm = _font(52)
    draw.text((18, 10), rank, fill=color_rgb, font=f_sm)
    draw.text((18, 58), suit, fill=color_rgb, font=f_sm)
    draw.text((w // 2 - 28, h // 2 - 58), suit, fill=color_rgb, font=f_big)
    draw.text((w - 52, h - 62), rank, fill=color_rgb, font=f_sm)
    draw.text((w - 52, h - 118), suit, fill=color_rgb, font=f_sm)
    return img


def _tex_777(w=512, h=256):
    img = Image.new('RGB', (w, h), (0x0c, 0x08, 0x10))
    draw = ImageDraw.Draw(img)
    draw.rectangle((4, 4, w - 5, h - 5), outline=(0xd4, 0xa8, 0x4b), width=6)
    for i in range(16):
        bx = 12 + i * 31
        draw.ellipse((bx, 10, bx + 18, 28), fill=(0xff, 0xe8, 0x9a))
        draw.ellipse((bx, h - 28, bx + 18, h - 10), fill=(0xff, 0xe8, 0x9a))
    f = _font(128)
    draw.text((w // 2 - 158, h // 2 - 72), '777', fill=(0xff, 0x55, 0x44), font=f, stroke_width=6, stroke_fill=(0xff, 0xdd, 0x66))
    # cherries
    for cx in (w // 2 - 70, w // 2 + 30):
        draw.ellipse((cx, h - 72, cx + 36, h - 36), fill=(0xcc, 0x22, 0x33))
        draw.arc((cx - 8, h - 92, cx + 44, h - 48), 200, 340, fill=(0x3d, 0x8a, 0x40), width=4)
    return img


def _tex_dice(w=256, h=256):
    img = Image.new('RGB', (w, h), (0x18, 0x10, 0x22))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((16, 16, w - 17, h - 17), radius=24, fill=(0xf2, 0xee, 0xe6), outline=(0xd4, 0xa8, 0x4b), width=4)
    dots = [(64, 64), (192, 64), (128, 128), (64, 192), (192, 192)]
    for x, y in dots:
        draw.ellipse((x - 14, y - 14, x + 14, y + 14), fill=(0x22, 0x18, 0x20))
    return img


def _tex_roulette(w=512, h=512):
    img = Image.new('RGB', (w, h), (0x1a, 0x14, 0x1c))
    draw = ImageDraw.Draw(img)
    cx, cy, r = w // 2, h // 2, 220
    colors = [(0x9c, 0x2b, 0x3a), (0x1a, 0x1a, 0x22), (0x4a, 0x8a, 0x44)]
    n = 24
    for i in range(n):
        a0, a1 = 2 * math.pi * i / n, 2 * math.pi * (i + 1) / n
        poly = [(cx, cy)]
        for a in (a0, a1):
            poly.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        draw.polygon(poly, fill=colors[i % 3])
    draw.ellipse((cx - 48, cy - 48, cx + 48, cy + 48), fill=(0xd4, 0xa8, 0x4b))
    draw.ellipse((cx - 22, cy - 22, cx + 22, cy + 22), fill=(0xff, 0xee, 0xcc))
    return img


def _tex_arch(w=512, h=200):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, h // 2 - 8, w, h - 1), fill=(0x9c, 0x2b, 0x3a, 255))
    f = _font(88)
    draw.text((w // 2 - 132, 24), '神之島', fill=(0xff, 0xee, 0xdd), font=f,
              stroke_width=5, stroke_fill=(0x6a, 0x18, 0x28))
    return img


def _tex_bulb(w=64, h=64):
    img = Image.new('RGB', (w, h), (0x22, 0x18, 0x10))
    draw = ImageDraw.Draw(img)
    draw.ellipse((8, 8, w - 9, h - 9), fill=(0xff, 0xeb, 0xb0))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    _noise_stone().save(os.path.join(OUT, 'cas_stone.png'))
    _poker_card('♥', 'A', (0xcc, 0x22, 0x33)).save(os.path.join(OUT, 'cas_poker_ah.png'))
    _poker_card('♠', 'A', (0x22, 0x18, 0x20)).save(os.path.join(OUT, 'cas_poker_as.png'))
    _tex_777().save(os.path.join(OUT, 'cas_777.png'))
    _tex_dice().save(os.path.join(OUT, 'cas_dice.png'))
    _tex_roulette().save(os.path.join(OUT, 'cas_roulette.png'))
    _tex_arch().save(os.path.join(OUT, 'cas_arch_gate.png'))
    _tex_bulb().save(os.path.join(OUT, 'cas_bulb.png'))
    print('Wrote textures to', OUT)


if __name__ == '__main__':
    main()