"""완독 도장(app/src/main/res/drawable-nodpi/stamp_completed.png)을 만든다. 사용: stamp_completed.py 원본.png 출력.png

원본: Pixabay TheDigitalArtist 'Approved Stamp'(https://pixabay.com/illustrations/approved-stamp-approval-business-5254258/,
1280px PNG, Pixabay Content License). 둥근 테·질감은 그대로 두고 가운데 `APPROVED` 를 지우고 `COMPLETED` 를 넣은 뒤
잉크 얼룩(작은 구멍)을 입히고, 검정 잉크(투명도만)로 −10° 기울여 420px 로 줄인다(화면에서 가장 큰 220dp = 413px). macOS 의 DIN Condensed Bold 를 쓴다.
"""
import sys
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

FONT = "/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf"
SIZE = 420

src, dst = sys.argv[1], sys.argv[2]
a = Image.open(src).getchannel("A")
s = a.width / 640  # 아래 좌표는 640px 기준
top, bottom = round(280 * s), round(366 * s)
band = a.crop((0, top, a.width, bottom))
x0, _, x1, _ = band.getbbox()  # 원래 글자 폭
ImageDraw.Draw(a).rectangle([x0 - round(4 * s), top, x1 + round(4 * s), bottom], fill=0)

h = round(60 * s)
font = ImageFont.truetype(FONT, round(80 * s))
text = "COMPLETED"
d = ImageDraw.Draw(Image.new("L", (1, 1)))
big = Image.new("L", (int(d.textlength(text, font=font)) + 40, round(140 * s)), 0)
ImageDraw.Draw(big).text((20, 10), text, font=font, fill=255)
big = big.crop(big.getbbox()).resize((x1 - x0, h), Image.LANCZOS)
word = Image.new("L", (a.width, bottom - top), 0)
word.paste(big, (x0, (bottom - top - h) // 2))
# 잉크가 덜 묻은 자리: 흐린 잡음을 잘라 작은 구멍으로(잡음이라 만들 때마다 조금씩 다르다)
noise = Image.effect_noise(word.size, 90).filter(ImageFilter.GaussianBlur(1.3 * s))
word = ImageChops.multiply(word, noise.point(lambda v: 0 if v < 108 else 255))
a.paste(ImageChops.lighter(a.crop((0, top, a.width, bottom)), word), (0, top))

a = a.crop(a.getbbox()).rotate(-10, expand=True, resample=Image.BICUBIC)
a = a.crop(a.getbbox()).resize((SIZE, SIZE), Image.LANCZOS)
out = Image.new("LA", a.size, 0)
out.putalpha(a)
out.save(dst, optimize=True)
