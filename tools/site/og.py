#!/usr/bin/env python3
"""홈페이지 공유 미리보기 그림(site/img/og-ko.png·og-en.png, 1200×630)과 아이콘(site/img/icon-*.png)을 만든다.

    python3 tools/site/og.py <Pebble Desk 아이콘 512px PNG>

기기 그림은 페이지와 같다: 기기 틀(frame-*.webp) 화면 자리에 캡처를 곱하기로 얹는다(자리는 site/index.html 의 .shot 과 같다).
아이콘은 에뮬에서 IconDump 로 com.woody.pebbledesk 를 그린 것.
"""
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

IMG = "site/img"
BG = (251, 251, 249)
INK = (17, 17, 17)
SUB = (107, 107, 104)
SANS = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
# 기기: 틀, 화면 자리(틀 대비 left·top·width·height), 화면 모서리(화면 폭 대비)
DEVICES = {"pebble": ("frame-pebble", (0.1020, 0.0854, 0.7912, 0.7594), 0), "bar": ("frame-palma", (0.1037, 0.0717, 0.7949, 0.8433), 0.046)}
TEXT = {
    "ko": ("이북리더기를 위한 런처", ["지금 읽는 책을", "홈 화면에."], ""),
    "en": ("A launcher for Android E Ink readers", ["The book you’re", "reading, on your", "home screen."], "en-"),
}


def device(name, prefix, height):
    frame_file, (l, t, w, h), radius = DEVICES[name]
    frame = Image.open(f"{IMG}/{frame_file}.webp").convert("RGBA")
    frame = frame.resize((round(frame.width * height / frame.height), height), Image.LANCZOS)
    box = (round(l * frame.width), round(t * frame.height), round((l + w) * frame.width), round((t + h) * frame.height))
    size = (box[2] - box[0], box[3] - box[1])
    shot = Image.open(f"{IMG}/{prefix}{name}-home.webp").convert("RGB").resize(size, Image.LANCZOS)
    under = frame.crop(box).convert("RGB")
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=round(radius * size[0]), fill=255)
    frame.paste(ImageChops.multiply(under, shot), box, mask)
    return frame


def og(lang):
    kicker, lines, prefix = TEXT[lang]
    im = Image.new("RGB", (1200, 630), BG)
    d = ImageDraw.Draw(im)
    x = 72
    d.text((x, 92), "Pebble Desk", font=ImageFont.truetype(SANS, 30, index=6), fill=INK)
    d.text((x, 146), kicker, font=ImageFont.truetype(SANS, 22, index=2), fill=SUB)
    big = ImageFont.truetype(SANS, 60 if lang == "ko" else 48, index=6)
    y = 230
    for line in lines:
        d.text((x, y), line, font=big, fill=INK)
        y += 78 if lang == "ko" else 62
    # 오른쪽: 기기 두 대(글자 칸 왼쪽 약 430px 을 비운다)
    peb, bar = device("pebble", prefix, 500), device("bar", prefix, 500)
    right = 1200 - 48
    bx = right - bar.width
    px = bx - 28 - peb.width
    im.paste(peb, (px, 65), peb)
    im.paste(bar, (bx, 65), bar)
    im.save(f"{IMG}/og-{lang}.png", optimize=True)


icon = Image.open(sys.argv[1]).convert("RGBA")
for px in (32, 180, 512):
    icon.resize((px, px), Image.LANCZOS).save(f"{IMG}/icon-{px}.png", optimize=True)
for lang in TEXT:
    og(lang)
print("ok")
