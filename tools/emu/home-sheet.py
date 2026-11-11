#!/usr/bin/env python3
"""home-sizes.sh 결과를 설정마다 한 장으로 모은다: <결과 폴더>/sheet-<설정>.png (화면마다 높이 600px, 이름과 PASS/FAIL)."""
import os
import sys

from PIL import Image, ImageDraw

out = sys.argv[1]
results = {}
for line in open(os.path.join(out, 'summary.txt'), encoding='utf-8'):
    parts = line.split()
    if len(parts) >= 2:
        results[parts[1]] = parts[0]
by_pref = {}
for shot in results:
    for pref in ('grid-short-bar', 'list-short-bar', 'grid-bar', 'grid-short', 'list-short', 'grid'):
        if shot.endswith('-' + pref):
            by_pref.setdefault(pref, []).append(shot)
            break
H = 600
for pref, shots in by_pref.items():
    tiles = []
    for shot in shots:
        im = Image.open(os.path.join(out, shot + '.png')).convert('RGB')
        im = im.resize((round(im.width * H / im.height), H))
        tile = Image.new('RGB', (im.width, H + 28), 'white')
        tile.paste(im, (0, 28))
        ok = results[shot] == 'PASS'
        ImageDraw.Draw(tile).text((4, 6), f"{shot[:-len(pref) - 1]} {results[shot]}", fill=(0, 120, 0) if ok else (200, 0, 0))
        tiles.append(tile)
    sheet = Image.new('RGB', (sum(t.width for t in tiles) + 8 * (len(tiles) + 1), H + 28 + 16), (200, 200, 200))
    x = 8
    for t in tiles:
        sheet.paste(t, (x, 8))
        x += t.width + 8
    sheet.save(os.path.join(out, f'sheet-{pref}.png'))
