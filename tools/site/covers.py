#!/usr/bin/env python3
"""mock 책의 실제 표지를 YES24 검색에서 받는다(앱의 표지 검색 `BookSearch` 와 같은 방법).

    python3 tools/site/covers.py <표지 폴더>     # <폴더>/<id>.jpg, 있는 것은 건너뛴다

받은 표지는 저장소에 넣지 않는다(캡처 화면에만 쓴다). mock.py 는 이 폴더에 표지가 있으면 그것을, 없으면 글자 표지를 쓴다.
SITE_LANG=en 이면 영어 페이지 책(원서)의 표지.
"""
import os
import re
import sys
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
EXCLUDED = ("[중고도서]", "[Blu-ray]", "[DVD]", "[음반]", "[LP]", "[GIFT]")

# 책 id → YES24 검색어(판을 고르려고 출판사를 붙인다). 숫자만이면 그 상품 번호
QUERY = {
    "b-demian": "데미안 민음사 세계문학전집",
    "b-walden": "월든 은행나무",
    "b-oldman": "노인과 바다 민음사",
    "b-sky": "하늘과 바람과 별과 시 초판본",
    "p-prince": "어린 왕자 열린책들",
    "p-stranger": "이방인 민음사",
    "p-meta": "변신 시골의사 민음사",
    "p-farm": "동물농장 민음사",
    "p-gatsby": "위대한 개츠비 민음사",
    "p-sidd": "싯다르타 민음사",
    "p-pride": "오만과 편견 민음사",
    "p-werther": "젊은 베르테르의 슬픔 민음사",
    "p-luck": "운수 좋은 날 현진건",
    "p-buck": "메밀꽃 필 무렵 이효석",
    "p-1984": "1984 민음사 조지 오웰",
    "p-wheel": "수레바퀴 아래서 민음사",
}


# 영어 페이지: 원서(외서)
QUERY_EN = {
    "e-oldman": "Old Man and the Sea Scribner",
    "e-walden": "Walden Henry David Thoreau",
    "e-sidd": "Siddhartha Hermann Hesse Penguin",
    "e-prince": "The Little Prince Antoine de Saint-Exupery",
    "e-frank": "Frankenstein Penguin Classics",
    "e-meta": "The Metamorphosis Kafka",
    "e-farm": "Animal Farm George Orwell Penguin",
    "e-gatsby": "The Great Gatsby Fitzgerald",
    "e-pride": "224618",  # 검색 첫 결과는 표지가 없어 판을 정해 둔다(Penguin Classics)
    "e-jane": "Jane Eyre Penguin Classics",
    "e-stranger": "The Stranger Albert Camus Vintage",
    "e-women": "Little Women Louisa May Alcott",
    "e-dracula": "Dracula Bram Stoker Penguin",
    "e-moby": "7991701",  # Penguin Clothbound
    "e-1984": "Nineteen Eighty-Four George Orwell Penguin",
    "e-demian": "8630021",  # Penguin Classics(검색 첫 결과는 논문)
}
DOMAIN = "BOOK"  # 국내도서
if os.environ.get("SITE_LANG") == "en":
    QUERY, DOMAIN = QUERY_EN, "FOREIGN"


def get(url, referer=None):
    req = urllib.request.Request(url, headers={"User-Agent": UA, **({"Referer": referer} if referer else {})})
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read()


def first(query):
    html = get(f"https://www.yes24.com/Product/Search?domain={DOMAIN}&query=" + urllib.parse.quote(query)).decode("utf-8", "replace")
    for item in html.split('class="itemUnit"')[1:]:
        t = re.search(r'<span class="gd_res">(.*?)</span>', item, re.S)
        if t and t.group(1).strip() in EXCLUDED:
            continue
        name = re.search(r'<a\b[^>]*\bclass="gd_name"[^>]*href="[^"]*/goods/(\d+)[^"]*"[^>]*>(.*?)</a>', item, re.S) or \
            re.search(r'<a\b[^>]*href="[^"]*/goods/(\d+)[^"]*"[^>]*\bclass="gd_name"[^>]*>(.*?)</a>', item, re.S)
        if name:
            return name.group(1), re.sub(r"<[^>]+>", "", name.group(2)).strip()
    return None


def main(out):
    os.makedirs(out, exist_ok=True)
    for i, q in QUERY.items():
        path = os.path.join(out, f"{i}.jpg")
        if os.path.exists(path):
            continue
        hit = (q, q) if q.isdigit() else first(q)
        if not hit:
            print(f"{i}: 못 찾음 ({q})")
            continue
        gid, title = hit
        data = get(f"https://image.yes24.com/goods/{gid}/XL", referer="https://www.yes24.com/")
        with open(path, "wb") as f:
            f.write(data)
        print(f"{i}: {title} ({gid}, {len(data) // 1024}KB)")


if __name__ == "__main__":
    main(sys.argv[1])
