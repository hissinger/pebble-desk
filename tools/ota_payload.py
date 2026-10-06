"""A/B 전체 OTA(update.zip 안의 payload.bin)에서 파티션 이미지를 꺼낸다. 사용: ota_payload.py update.zip 출력폴더 [파티션...]

전체 OTA 의 REPLACE·REPLACE_BZ·REPLACE_XZ·ZERO 만 다룬다(증분 OTA 는 못 푼다). 파티션을 안 주면 모두 꺼낸다.
"""
import bz2, lzma, struct, sys, zipfile, os


def varint(b, i):
    r = s = 0
    while True:
        c = b[i]; i += 1
        r |= (c & 0x7F) << s; s += 7
        if c < 0x80: return r, i


def fields(b):
    i, out = 0, []
    while i < len(b):
        key, i = varint(b, i)
        f, t = key >> 3, key & 7
        if t == 0: v, i = varint(b, i)
        elif t == 2:
            n, i = varint(b, i); v = b[i:i + n]; i += n
        elif t == 1: v = b[i:i + 8]; i += 8
        elif t == 5: v = b[i:i + 4]; i += 4
        else: raise ValueError(t)
        out.append((f, v))
    return out


def main():
    src, out, want = sys.argv[1], sys.argv[2], set(sys.argv[3:])
    z = zipfile.ZipFile(src)
    p = z.open('payload.bin')
    assert p.read(4) == b'CrAU'
    ver, msize = struct.unpack('>QQ', p.read(16))
    ssize = struct.unpack('>I', p.read(4))[0] if ver >= 2 else 0
    manifest = p.read(msize)
    base = 4 + 16 + (4 if ver >= 2 else 0) + msize + ssize
    m = fields(manifest)
    bs = next((v for f, v in m if f == 3), 4096)
    os.makedirs(out, exist_ok=True)
    for f, v in m:
        if f != 13: continue
        part = fields(v)
        name = next(x for k, x in part if k == 1).decode()
        info = next((x for k, x in part if k == 7), b'')
        size = next((x for k, x in fields(info) if k == 1), 0)
        print(f'{name}: {size / 1e6:.0f}MB', flush=True)
        if want and name not in want: continue
        with open(os.path.join(out, name + '.img'), 'wb') as o:
            o.truncate(size)
            for k, op in part:
                if k != 8: continue
                of = dict(); dst = []
                for a, b in fields(op):
                    if a == 6: dst.append(dict(fields(b)))
                    else: of[a] = b
                typ, off, ln = of.get(1, 0), of.get(2, 0), of.get(3, 0)
                if typ in (6, 7):  # ZERO, DISCARD
                    continue
                p.seek(base + off)
                data = p.read(ln)
                if typ == 1: data = bz2.decompress(data)
                elif typ == 8: data = lzma.decompress(data)
                elif typ != 0: raise ValueError(f'{name}: op {typ}')
                data, pos = memoryview(data), 0
                for e in dst:
                    start, n = e.get(1, 0), e.get(2, 0)
                    o.seek(start * bs); o.write(data[pos:pos + n * bs]); pos += n * bs


main()
