#!/usr/bin/env python3
"""홈페이지(site/) 화면 캡처용 mock 데이터: 표지(직접 그림)와 DB(버전 4).

    python3 tools/site/mock.py <출력 폴더> [표지 폴더]   # <폴더>/pebble.db, <폴더>/books/<id>.jpg

표지는 [표지 폴더](covers.py 가 받은 실제 표지)에 있으면 그것을, 없으면 글자로 그린다. 날짜는 SEED_TODAY(없으면 오늘) 기준.
SITE_LANG=en 이면 영어 페이지용(영어판 책, Kindle·Libby·KOReader·Moon+ Reader).
넣는 법은 tools/site/shoot.sh.
"""
import datetime as dt
import os
import random
import shutil
import sqlite3
import sys
import uuid

from PIL import Image, ImageDraw, ImageFont

TODAY = dt.date.fromisoformat(os.environ["SEED_TODAY"]) if os.environ.get("SEED_TODAY") else dt.date.today()
MIN = 60_000
SERIF = "/System/Library/Fonts/Supplemental/AppleMyungjo.ttf"
SANS = "/System/Library/Fonts/AppleSDGothicNeo.ttc"

KYOBO, MILLIE, RIDI, ALADIN = "kr.co.kyobobook.KEL", "kr.co.millie.eink", "com.initialcoms.ridi", "kr.co.aladin.ebook"
KINDLE, LIBBY, KOREADER, MOON = "com.amazon.kindle", "com.overdrive.mobile.android.libby", "org.koreader.launcher", "com.flyersoft.moonreader"
EN = os.environ.get("SITE_LANG") == "en"

# 읽고 있는 책(맨 앞이 홈의 큰 책): id, 제목, 저자, 앱, 진행률, 반납까지 남은 날(-1 없음), 읽기 시작한 날(며칠 전)
SHELF = [
    ("b-demian", "데미안", "헤르만 헤세", KYOBO, 64, 4, 16),
    ("b-walden", "월든", "헨리 데이비드 소로", MILLIE, 31, -1, 9),
    ("b-oldman", "노인과 바다", "어니스트 헤밍웨이", RIDI, 52, -1, 6),
    ("b-sky", "하늘과 바람과 별과 시", "윤동주", ALADIN, 80, -1, 3),
]

# 올해 완독한 책: id, 제목, 저자, 앱, 읽기 시작(월, 일), 완독(월, 일), 별점(반 개 단위 0~10), 감상
DONE = [
    ("p-prince", "어린 왕자", "앙투안 드 생텍쥐페리", MILLIE, (1, 2), (1, 9), 10, "어른이 되어 다시 읽으니 여우의 말이 다르게 들린다."),
    ("p-stranger", "이방인", "알베르 카뮈", KYOBO, (1, 11), (1, 30), 8, "햇빛 때문이었다는 말이 오래 남는다."),
    ("p-meta", "변신", "프란츠 카프카", RIDI, (2, 1), (2, 12), 7, "짧은데 하루 종일 무거웠다."),
    ("p-farm", "동물농장", "조지 오웰", KYOBO, (2, 14), (3, 6), 8, "모든 동물은 평등하다. 다만 어떤 동물은 더 평등하다."),
    ("p-gatsby", "위대한 개츠비", "F. 스콧 피츠제럴드", MILLIE, (3, 9), (4, 4), 9, "초록 불빛 장면을 밑줄 쳐 두었다."),
    ("p-sidd", "싯다르타", "헤르만 헤세", ALADIN, (4, 6), (5, 2), 9, "강물 소리를 듣는 대목에서 천천히 읽었다."),
    ("p-pride", "오만과 편견", "제인 오스틴", MILLIE, (5, 5), (6, 14), 8, "엘리자베스의 말솜씨가 즐겁다."),
    ("p-werther", "젊은 베르테르의 슬픔", "요한 볼프강 폰 괴테", KYOBO, (6, 17), (7, 11), 6, ""),
    ("p-luck", "운수 좋은 날", "현진건", ALADIN, (7, 14), (7, 22), 9, "설렁탕을 사 왔는데 왜 먹지를 못하니."),
    ("p-buck", "메밀꽃 필 무렵", "이효석", ALADIN, (7, 24), (8, 3), 8, "달밤 길 묘사가 소금을 뿌린 듯 환하다."),
    ("p-1984", "1984", "조지 오웰", RIDI, (8, 6), (9, 15), 9, "빅 브라더가 지금도 보고 있는 것 같다."),
    ("p-wheel", "수레바퀴 아래", "헤르만 헤세", KYOBO, (9, 18), (10, 3), 8, "한스가 낚시하던 여름이 가장 좋았다."),
]

# 영어 페이지: 같은 모양(id 는 covers.py 의 QUERY_EN 과 같다)
SHELF_EN = [
    ("e-oldman", "The Old Man and the Sea", "Ernest Hemingway", LIBBY, 64, 4, 16),
    ("e-walden", "Walden", "Henry David Thoreau", KINDLE, 31, -1, 9),
    ("e-sidd", "Siddhartha", "Hermann Hesse", KOREADER, 52, -1, 6),
    ("e-prince", "The Little Prince", "Antoine de Saint-Exupéry", MOON, 80, -1, 3),
]
DONE_EN = [
    ("e-frank", "Frankenstein", "Mary Shelley", KINDLE, (1, 2), (1, 9), 9, "The creature's voice stays with you longer than Victor's."),
    ("e-meta", "The Metamorphosis", "Franz Kafka", KOREADER, (1, 11), (1, 20), 7, "Short, strange, and heavy all day."),
    ("e-farm", "Animal Farm", "George Orwell", LIBBY, (1, 22), (2, 8), 8, "Some animals are more equal than others."),
    ("e-gatsby", "The Great Gatsby", "F. Scott Fitzgerald", KINDLE, (2, 10), (3, 6), 9, "Underlined the green light again."),
    ("e-pride", "Pride and Prejudice", "Jane Austen", KINDLE, (3, 9), (4, 14), 8, "Elizabeth wins every argument."),
    ("e-jane", "Jane Eyre", "Charlotte Brontë", LIBBY, (4, 16), (5, 20), 8, ""),
    ("e-stranger", "The Stranger", "Albert Camus", KOREADER, (5, 23), (6, 5), 7, "It was because of the sun."),
    ("e-women", "Little Women", "Louisa May Alcott", MOON, (6, 8), (7, 10), 8, "Jo is still my favorite."),
    ("e-dracula", "Dracula", "Bram Stoker", KINDLE, (7, 13), (8, 9), 7, "Best read at midnight."),
    ("e-moby", "Moby-Dick", "Herman Melville", KOREADER, (8, 11), (9, 12), 6, "Skipped a few whaling chapters. No regrets."),
    ("e-1984", "Nineteen Eighty-Four", "George Orwell", LIBBY, (9, 14), (9, 30), 9, "Read it in one long week."),
    ("e-demian", "Demian", "Hermann Hesse", KINDLE, (10, 1), (10, 3), 8, "The bird fights its way out of the egg."),
]
REVIEW_KO = "두 번째로 읽으니 싱클레어가 훨씬 가깝게 느껴진다.\n밑줄을 긋다 그만두었다. 거의 다 그어서."
REVIEW_EN = "Second time through, and the last pages hit harder.\nRead them slowly on the bus home."
if EN:
    SHELF, DONE = SHELF_EN, DONE_EN

# 표지 모양: (바탕, 글자색, 무늬, 글꼴)
STYLE = {
    "b-demian": ("#1d1d1b", "#efe9dc", "bird", SERIF),
    "b-walden": ("#e9e4d6", "#2b2a26", "lines", SERIF),
    "b-oldman": ("#d7dde0", "#1f2a33", "sun", SANS),
    "b-sky": ("#f2efe8", "#22201c", "stars", SERIF),
    "p-prince": ("#f4efe4", "#2f3b4c", "dot", SANS),
    "p-stranger": ("#e8d9b8", "#2a2116", "sun", SERIF),
    "p-meta": ("#2a2a2a", "#e6e2d8", "box", SANS),
    "p-farm": ("#b8412f", "#f6efe2", "bar", SANS),
    "p-gatsby": ("#14324a", "#e9d9a8", "lines", SERIF),
    "p-sidd": ("#efe7d5", "#4a3b22", "wave", SERIF),
    "p-pride": ("#ece6dd", "#5a2f35", "frame", SERIF),
    "p-werther": ("#c9d3c4", "#28322a", "dot", SERIF),
    "p-luck": ("#f1ece2", "#1f1f1f", "rain", SERIF),
    "p-buck": ("#1e2433", "#f1ede2", "moon", SERIF),
    "p-1984": ("#e1e1df", "#111111", "eye", SANS),
    "p-wheel": ("#d9cdb5", "#2e2618", "circle", SERIF),
}

W, H = 600, 900


def font(path, size, bold=False):
    if path == SANS:
        return ImageFont.truetype(SANS, size, index=6 if bold else 2)  # SemiBold / Regular
    return ImageFont.truetype(path, size)


def wrap(title, f, width, draw):
    """제목을 폭에 맞게 어절로 나눈다"""
    lines, cur = [], ""
    for word in title.split(" "):
        t = (cur + " " + word).strip()
        if draw.textlength(t, font=f) <= width or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = word
    lines.append(cur)
    return lines


REAL = None  # 실제 표지 폴더


def cover(book_id, title, author, path):
    real = REAL and os.path.join(REAL, f"{book_id}.jpg")
    if real and os.path.exists(real):
        shutil.copyfile(real, path)
        return
    bg, fg, motif, face = STYLE.get(book_id, ("#e9e4d6", "#2b2a26", "lines", SERIF))
    im = Image.new("RGB", (W, H), bg)
    d = ImageDraw.Draw(im)
    rnd = random.Random(book_id)
    m = 56
    if motif == "sun":
        d.ellipse((W / 2 - 130, 420, W / 2 + 130, 680), fill=fg)
        d.rectangle((0, 610, W, H), fill=bg)
        for k in range(5):
            y = 640 + k * 26
            d.line((m + k * 30, y, W - m - k * 30, y), fill=fg, width=4)
    elif motif == "lines":
        for k in range(14):
            y = 420 + k * 22
            d.line((m, y, W - m, y), fill=fg, width=2 if k % 3 else 4)
    elif motif == "stars":
        for _ in range(26):
            x, y, r = rnd.randint(m, W - m), rnd.randint(430, 780), rnd.choice([3, 4, 6])
            d.ellipse((x - r, y - r, x + r, y + r), fill=fg)
    elif motif == "bird":
        d.arc((150, 430, 330, 610), 200, 340, fill=fg, width=6)
        d.arc((300, 430, 480, 610), 200, 340, fill=fg, width=6)
        d.ellipse((290, 600, 340, 650), outline=fg, width=4)
    elif motif == "dot":
        d.ellipse((W / 2 - 60, 520, W / 2 + 60, 640), fill=fg)
    elif motif == "box":
        d.rectangle((m + 40, 440, W - m - 40, 760), outline=fg, width=5)
        d.rectangle((W / 2 - 30, 560, W / 2 + 30, 640), fill=fg)
    elif motif == "bar":
        d.rectangle((0, 560, W, 600), fill=fg)
    elif motif == "wave":
        for k in range(7):
            y = 470 + k * 40
            pts = [(x, y + 12 * __import__("math").sin(x / 38 + k)) for x in range(m, W - m + 1, 6)]
            d.line(pts, fill=fg, width=3)
    elif motif == "frame":
        d.rectangle((28, 28, W - 28, H - 28), outline=fg, width=3)
        d.rectangle((40, 40, W - 40, H - 40), outline=fg, width=1)
    elif motif == "rain":
        for _ in range(60):
            x, y = rnd.randint(m, W - m), rnd.randint(420, 790)
            d.line((x, y, x - 8, y + 30), fill=fg, width=2)
    elif motif == "moon":
        d.ellipse((W - 220, 430, W - 80, 570), fill=fg)
        for _ in range(40):
            x, y = rnd.randint(m, W - m), rnd.randint(650, 800)
            d.ellipse((x - 3, y - 3, x + 3, y + 3), fill=fg)
    elif motif == "eye":
        d.ellipse((W / 2 - 160, 520, W / 2 + 160, 660), outline=fg, width=6)
        d.ellipse((W / 2 - 45, 545, W / 2 + 45, 635), fill=fg)
    elif motif == "circle":
        d.ellipse((W / 2 - 140, 440, W / 2 + 140, 720), outline=fg, width=5)
        d.line((W / 2, 440, W / 2, 720), fill=fg, width=3)
        d.line((W / 2 - 140, 580, W / 2 + 140, 580), fill=fg, width=3)

    size = 92 if len(title) <= 6 else 76 if len(title) <= 10 else 64
    tf = font(face, size, bold=True)
    y = 120
    for line in wrap(title, tf, W - 2 * m, d):
        d.text((m, y), line, font=tf, fill=fg)
        y += int(size * 1.25)
    d.line((m, y + 18, m + 60, y + 18), fill=fg, width=3)
    af = font(face, 30)
    d.text((m, H - m - 36), author, font=af, fill=fg)
    im.save(path, quality=90)


def ep(d):
    return (d - dt.date(1970, 1, 1)).days


def ms(d, h=21):
    return int(dt.datetime.combine(d, dt.time(h)).timestamp() * 1000)


def schema(x):
    x("CREATE TABLE reading_day (day INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', book_id TEXT NOT NULL DEFAULT '', "
      "ms INTEGER NOT NULL, PRIMARY KEY (day, pkg, title, book_id))")
    x("CREATE INDEX reading_day_book ON reading_day (book_id)")
    x("CREATE TABLE reading_meta (name TEXT PRIMARY KEY, value TEXT)")
    x("CREATE TABLE book_open (at INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL, book_id TEXT NOT NULL DEFAULT '')")
    x("CREATE TABLE shelf (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, due INTEGER, seen_title TEXT)")
    x("CREATE TABLE past (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, seen_title TEXT, left_at INTEGER, hidden INTEGER)")
    x("CREATE TABLE finish (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL UNIQUE, book_id TEXT NOT NULL, day INTEGER NOT NULL, at INTEGER, started INTEGER, stars INTEGER, text TEXT)")
    x("CREATE INDEX finish_book ON finish (book_id)")
    x("CREATE TABLE review (book_id TEXT PRIMARY KEY, stars INTEGER NOT NULL, text TEXT NOT NULL, updated_at INTEGER NOT NULL)")
    x("CREATE TABLE round_start (book_id TEXT PRIMARY KEY, day INTEGER)")
    x("CREATE TABLE viewer_mark (at INTEGER NOT NULL, pkg TEXT NOT NULL, shown INTEGER NOT NULL)")
    x("CREATE TABLE book_file (book_id TEXT PRIMARY KEY, path TEXT NOT NULL)")
    x("PRAGMA user_version=4")


def main(out):
    os.makedirs(os.path.join(out, "books"), exist_ok=True)
    db_path = os.path.join(out, "pebble.db")
    if os.path.exists(db_path):
        os.remove(db_path)
    con = sqlite3.connect(db_path)
    x = con.execute
    schema(x)
    rnd = random.Random(7)
    year = TODAY.year

    def read(d, pkg, title, book, minutes):
        x("INSERT OR REPLACE INTO reading_day VALUES (?,?,?,?,?)", (ep(d), pkg, title, book, int(minutes * MIN)))

    # 완독한 책: 읽은 동안 하루 20~70분, 가끔 쉰다
    for pos, (i, title, author, app, a, b, stars, text) in enumerate(reversed(DONE)):
        start, end = dt.date(year, *a), dt.date(year, *b)
        d = start
        while d <= end:
            if rnd.random() > 0.12:
                read(d, app, title, i, rnd.randint(20, 70))
            d += dt.timedelta(days=1)
        x("INSERT INTO past VALUES (?,?,?,?,?,?,100,?,?,0)", (pos, i, title, author, app, ms(end), title, ms(end)))
        x("INSERT INTO finish (uid, book_id, day, at, started, stars, text) VALUES (?,?,?,?,NULL,?,?)",
          (uuid.uuid4().hex, i, ep(end), ms(end, 22), stars, text or None))
        cover(i, title, author, os.path.join(out, "books", f"{i}.jpg"))
    # 싯다르타는 재작년에도 한 번
    if not EN:
        x("INSERT INTO finish (uid, book_id, day, at, started, stars, text) VALUES (?,?,?,?,NULL,?,?)",
          (uuid.uuid4().hex, "p-sidd", ep(dt.date(year - 2, 3, 21)), ms(dt.date(year - 2, 3, 21)), 8, "처음 읽었을 때"))

    # 읽고 있는 책: 맨 앞 책은 매일, 나머지는 번갈아. 마지막 13일은 빠짐없이(연속일)
    for pos, (i, title, author, app, progress, due, since) in enumerate(SHELF):
        x("INSERT INTO shelf VALUES (?,?,?,?,?,?,?,?,?)",
          (pos, i, title, author, app, ms(TODAY - dt.timedelta(days=pos)), progress, ep(TODAY) + due if due >= 0 else -1, title))
        cover(i, title, author, os.path.join(out, "books", f"{i}.jpg"))
    for k in range(17, 0, -1):
        d = TODAY - dt.timedelta(days=k)
        busy = k <= 13
        for pos, (i, title, author, app, _, _, since) in enumerate(SHELF):
            if k > since:
                continue
            if pos == 0 and (busy or rnd.random() > 0.3):
                read(d, app, title, i, rnd.randint(22, 48))
            elif pos > 0 and rnd.random() > 0.55:
                read(d, app, title, i, rnd.randint(8, 25))
    # 오늘(에뮬에서 이북 앱을 열지 않으면 그대로 보인다)
    (b0, t0, _, a0, *_), (b1, t1, _, a1, *_), _, (b3, t3, _, a3, *_) = SHELF
    read(TODAY, a0, t0, b0, 27)
    read(TODAY, a1, t1, b1, 11)
    read(TODAY - dt.timedelta(days=1), a3, t3, b3, 14)

    x("INSERT INTO book_open VALUES (?,?,?,?)", (ms(TODAY, 8), a0, t0, b0))
    # 맨 앞 책에 지금 회차의 별점·감상(완독 메뉴를 열면 채워져 뜬다, 홈페이지 별점·감상 화면)
    x("INSERT INTO review VALUES (?,?,?,?)", (b0, 9, REVIEW_EN if EN else REVIEW_KO, ms(TODAY)))
    x("INSERT INTO reading_meta VALUES ('through', ?)", ((TODAY - dt.timedelta(days=1)).isoformat(),))
    x("INSERT INTO reading_meta VALUES ('since', ?)", (dt.date(year - 1, 12, 1).isoformat(),))
    con.commit()
    con.close()


if __name__ == "__main__":
    REAL = sys.argv[2] if len(sys.argv) > 2 else None
    main(sys.argv[1])
