#!/usr/bin/env python3
"""실제 기기 사진으로 홈페이지 기기 틀(site/img/frame-<기기>.webp)을 만든다. 화면 자리는 비워 두고(밝은 회색), 그 자리를 % 로 알려 준다.
흑백으로 바꾸고 사진의 흰 바탕은 투명하게 뺀다(흰 기기가 페이지 바탕에 묻히지 않게 페이지에서 그림자를 준다).

    python3 tools/site/frames.py <크레마 페블 사진> <팔마 사진> site/img

사진: 크레마 페블 화이트 = YES24 상품 126954992 (https://image.yes24.com/goods/126954992/XL),
      BOOX Palma 블랙 = BOOX 스토어 Palma 첫 사진(https://shop.boox.com/products/palma). 사진은 저장소에 넣지 않는다.
화면 자리(사진 좌표)는 사진마다 잰 값. 출력의 % 를 site/index.html 의 .shot 자리에 쓴다.
"""
import sys

from PIL import Image, ImageDraw, ImageFilter

PAPER = 236  # 비운 화면 색(그 위에 캡처를 곱하기로 얹는다)
# 기기: 사진, 자를 곳(기기 둘레 + 여백), 화면 자리, 화면 모서리 반지름
DEVICES = {
    "pebble": (1, (283, 150, 920, 1052), (348, 227, 852, 912), 0),
    "palma": (2, (284, 88, 718, 911), (329, 147, 674, 841), 16),
}

out = sys.argv[3]
for name, (arg, crop, screen, radius) in DEVICES.items():
    im = Image.open(sys.argv[arg]).convert("L")
    ImageDraw.Draw(im).rounded_rectangle(screen, radius=radius, fill=PAPER)
    im = im.crop(crop)
    # 바탕: 네 귀에서 이어진 거의 흰 칸(기기 흰 테두리는 236~245 라 남는다)
    bg = im.point(lambda v: 255 if v >= 249 else 0)
    w, h = im.size
    for xy in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        ImageDraw.floodfill(bg, xy, 128)
    alpha = bg.point(lambda v: 0 if v == 128 else 255).filter(ImageFilter.GaussianBlur(0.8))
    rgba = Image.merge("RGBA", (im, im, im, alpha))
    rgba.save(f"{out}/frame-{name}.webp", quality=92, method=6)
    w, h = im.size
    l, t, r, b = screen[0] - crop[0], screen[1] - crop[1], screen[2] - crop[0], screen[3] - crop[1]
    print(f"{name}: {w}x{h}  left {l / w:.2%} top {t / h:.2%} width {(r - l) / w:.2%} height {(b - t) / h:.2%}  radius {radius / (r - l):.2%}")
