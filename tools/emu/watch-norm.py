#!/usr/bin/env python3
"""읽는 책 자동 추가 시험 결과(tools/emu/watch-snap.sh 가 가져온 폴더)를 때 없이 요약한다.

  watch-norm.py <폴더> [<앞 폴더>]

앞 폴더를 주면 그 뒤로 바뀐 것만: 새로 생긴·바뀐 읽고 있는 책, 새 편 책 기록·열림 표시·자동 추가 기록 줄.
책 id 는 매번 달라 제목으로 바꿔 적는다. 같은 시험 순서의 두 결과(옮기기 전·뒤)의 요약이 같으면 동작이 같다.
"""
import os
import shutil
import sqlite3
import tempfile
import sys
import xml.etree.ElementTree as ET


def load(folder):
    # 가져온 것을 바꾸지 않게(WAL 을 읽으면 체크포인트로 합쳐진다) 사본에서 읽는다
    tmp = tempfile.mkdtemp()
    for f in os.listdir(os.path.join(folder, "db")):
        shutil.copy(os.path.join(folder, "db", f), tmp)
    con = sqlite3.connect(os.path.join(tmp, "pebble.db"))
    q = lambda s: con.execute(s).fetchall()
    titles = {i: t for i, t in q("SELECT id, title FROM shelf UNION ALL SELECT id, title FROM past")}
    files = dict(q("SELECT book_id, path FROM book_file"))
    covers = set()
    cv = os.path.join(folder, "covers.txt")
    if os.path.exists(cv):
        covers = {l.strip()[:-4] for l in open(cv) if l.strip().endswith(".jpg")}
    # 읽는 화(`chapter`)는 DB 버전 5 부터(그 전 판의 결과와도 비교하게 없으면 빈 값)
    chapter = "chapter" if any(c[1] == "chapter" for c in q("PRAGMA table_info(shelf)")) else "''"
    shelf = [
        dict(id=i, title=t, author=a or "", app=(app or "").split("/")[0], progress=p, due=d, seen=s or "",
             file=os.path.basename(files.get(i, "")), cover=i in covers, chapter=ch or "")
        for (i, t, a, app, p, d, s, ch) in q(f"SELECT id, title, author, app, progress, due, seen_title, {chapter} FROM shelf ORDER BY pos")
    ]
    past = [dict(id=i, title=t, hidden=h) for (i, t, h) in q("SELECT id, title, hidden FROM past ORDER BY pos")]
    opens = q("SELECT at, pkg, title, book_id FROM book_open ORDER BY at, rowid")
    marks = q("SELECT at, pkg, shown FROM viewer_mark ORDER BY at, rowid")
    log = []
    lf = os.path.join(folder, "watch_log.txt")
    if os.path.exists(lf):
        for line in open(lf, encoding="utf-8"):
            f = line.rstrip("\n").split("\t")
            if len(f) >= 6:
                log.append((int(f[0] or 0), f[1], f[2], f[4], f[5]))
    prefs = {}
    pf = os.path.join(folder, "reader_watch.xml")
    if os.path.exists(pf) and os.path.getsize(pf) > 0:
        for el in ET.parse(pf).getroot():
            prefs[el.get("name")] = el.text if el.text is not None else el.get("value")
    return dict(titles=titles, shelf=shelf, past=past, opens=opens, marks=marks, log=log, prefs=prefs)


def name(s, i):
    return s["titles"].get(i, "?" if i else "-")


def short(pkg):
    for p in ("kr.co.", "com."):
        if pkg.startswith(p):
            return pkg[len(p):]
    return pkg


def book_line(s, b):
    # 읽는 화는 있을 때만 붙인다(읽는 화가 없는 앱의 줄은 그 전 결과와 같게)
    return (f"{b['title']} | {b['author']} | {short(b['app'])} | {b['progress']}% | due {b['due']} | seen {b['seen']} | "
            f"file {b['file'] or '-'} | cover {'O' if b['cover'] else '-'}" + (f" | chapter {b['chapter']}" if b['chapter'] else ""))


# 시험마다 달라 비교에서 빼는 기록(연결, 앱을 띄울 때 순서가 섞이는 대화상자 창)
SKIP = {"FIRST_EVENT", "CONNECTED", "UNBOUND", "DESTROYED", "OTHER_WINDOW"}


def main():
    cur = load(sys.argv[1])
    before = load(sys.argv[2]) if len(sys.argv) > 2 else None
    since = max([r[0] for r in before["log"]] + [0]) if before else 0
    print("# 읽고 있는 책(앞에서부터)" + (" — 앞 결과에서 바뀐 것" if before else ""))
    old = {b["id"]: book_line(before, b) for b in before["shelf"]} if before else {}
    old_order = [b["id"] for b in before["shelf"]] if before else []
    for n, b in enumerate(cur["shelf"]):
        line = book_line(cur, b)
        if before and old.get(b["id"]) == line:
            continue
        mark = "새 책 " if before and b["id"] not in old else ""
        print(f"{n}. {mark}{line}")
    if before:
        front = [b["id"] for b in cur["shelf"][:3]]
        if front != old_order[:3]:
            print("맨 앞 셋: " + " / ".join(name(cur, i) for i in front))
    print("# 나간 책" + (" — 새로" if before else ""))
    old_past = {p["id"] for p in before["past"]} if before else set()
    for p in cur["past"]:
        if p["id"] not in old_past:
            print(f"- {p['title']}{' (지움)' if p['hidden'] else ''}")
    # 편 책 기록·열림 표시는 읽는 화면이 열린 때로 거슬러 적기도 해 때가 아니라 앞 결과에 없던 줄로 고른다
    old_opens = set(before["opens"]) if before else set()
    old_marks = set(before["marks"]) if before else set()
    print("# 편 책 기록")
    for row in cur["opens"]:
        if row not in old_opens:
            _, pkg, title, bid = row
            print(f"- {short(pkg)} | {title or '(책 모름)'} | {name(cur, bid)}")
    print("# 열림 표시")
    for row in cur["marks"]:
        if row not in old_marks:
            print(f"- {short(row[1])} | {'열림' if row[2] else '닫힘'}")
    print("# 자동 추가 기록")
    for at, kind, pkg, arg, detail in cur["log"]:
        if at > since and kind not in SKIP:
            print(f"- {kind} | {short(pkg)} | {arg} | {detail}")
    print("# 서비스 prefs" + (" — 바뀐 것" if before else ""))
    for k in sorted(cur["prefs"]):
        v = cur["prefs"][k]
        # 데이터로 맞춘 때(KOReader)는 홈에 올 때마다 바뀌어 빼고 바뀐 것만 본다
        if k.startswith("sync_") or before and before["prefs"].get(k) == v:
            continue
        elif k.startswith(("tab_book_", "book_key_", "shelf_book_", "key_")) and v in cur["titles"]:
            v = "책 " + cur["titles"][v]
        print(f"- {k} = {v}")


if __name__ == "__main__":
    main()
