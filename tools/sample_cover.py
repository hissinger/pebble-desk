"""시작하기(온보딩)의 예시 책 표지(app/src/main/res/drawable-nodpi/sample_cover.jpg)를 만든다. 사용: sample_cover.py 원본.jpg 출력.jpg

원본: 윤동주 『하늘과 바람과 별과 시』(정음사, 1948) 초판 표지. 국립한글박물관 아카이브의 스캔을 Wikimedia Commons 에 올린
`윤동주 하늘과 바람과 별과 시 (초판본, 1948).pdf`(퍼블릭 도메인) 첫 쪽의 500px 미리 보기
(https://commons.wikimedia.org/wiki/File:윤동주_하늘과_바람과_별과_시_(초판본,_1948).pdf, page1-500px).
흑백으로 바꾸고 바랜 종이가 너무 어둡지 않게 명암을 넓힌 뒤 2:3 으로 맞춰 200×300 으로 줄인다(화면에서는 52dp, 페블 98px).
"""
import sys
from PIL import Image, ImageOps

W, H = 200, 300

src, dst = sys.argv[1], sys.argv[2]
im = ImageOps.autocontrast(Image.open(src).convert("L"), cutoff=1)
im = ImageOps.fit(im, (W, H), Image.LANCZOS)
im.save(dst, quality=88, optimize=True)
