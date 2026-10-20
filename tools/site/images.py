#!/usr/bin/env python3
"""shoot.sh 가 찍은 화면을 홈페이지 그림(site/img/<이름>.webp)으로 만든다.

    python3 tools/site/images.py <캡처 폴더> site/img [앞말]     # 영어 페이지는 앞말 en- (site/img/en-pebble-home.webp …)

하위 화면(독서 기록·올해 읽은 책)의 시스템 상단 바는 흰색으로 지운다(홈 앱으로 쓰면 홈처럼 숨는 자리라).
폭은 2배 화면에 맞춰 줄이고 흑백 WebP 로.
"""
import sys

from PIL import Image

STATUS_BAR = 48  # 300dpi 24dp(45px) + 여유
WIDTH = {"pebble": 760, "bar": 560}

src, out = sys.argv[1], sys.argv[2]
prefix = sys.argv[3] if len(sys.argv) > 3 else ""
for dev in ("pebble", "bar"):
    for screen in ("home", "reading", "year"):
        im = Image.open(f"{src}/{dev}-{screen}.png").convert("L")
        if screen != "home":
            im.paste(255, (0, 0, im.width, STATUS_BAR))
        w = WIDTH[dev]
        im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
        im.save(f"{out}/{prefix}{dev}-{screen}.webp", quality=88, method=6)
        print(dev, screen, im.size)
