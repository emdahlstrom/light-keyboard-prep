"""Convert `Light Phone III Modding Guide.pdf` to the Markdown in README.md.

Usage:  pip install pymupdf pillow  &&  python convert.py <out-dir>
Expects the source PDF next to this script as `guide.pdf`.

Structure comes from the PDF outline (heading levels), Courier spans become fenced
code blocks, Symbol/Wingdings bullets become list items, and both external and
internal PDF links are rewritten as Markdown links / heading anchors.
"""

import fitz, re, os, sys, io, collections, unicodedata
from PIL import Image

SRC, OUT_DIR = 'guide.pdf', (sys.argv[1] if len(sys.argv) > 1 else 'out2')
IMG_DIR = os.path.join(OUT_DIR, 'images')
os.makedirs(IMG_DIR, exist_ok=True)

doc = fitz.open(SRC)
MAX_IMG_W = 480
MONO = ('Courier', 'Lucida', 'Consol')
BULLET_FONTS = ('Symbol', 'Wingdings')

def norm(s):
    s = unicodedata.normalize('NFKC', s).replace(' ', ' ')
    return re.sub(r'\s+', ' ', s).strip()

toc = doc.get_toc()
head_by_page = collections.defaultdict(list)
for lvl, title, page in toc:
    if page and page > 0 and lvl > 1:
        head_by_page[page - 1].append([lvl, norm(title), False])

line_counts = collections.Counter()
for p in doc:
    for b in p.get_text('dict')['blocks']:
        if b['type'] == 0:
            for l in b['lines']:
                t = norm(''.join(s['text'] for s in l['spans']))
                if t: line_counts[t] += 1
BOILER = {t for t, c in line_counts.items() if c > len(doc) * 0.3 and len(t) < 80}

def esc(t):
    t = re.sub(r'([*_`])', r'\\\1', t)
    return re.sub(r'\[(?=[^\]]*\]\()', r'\\[', t)

def span_in_link(s, r):
    x0, x1 = s['bbox'][0], s['bbox'][2]
    y = s['origin'][1] - s['size'] * 0.35
    if not (r.y0 - 1 <= y <= r.y1 + 1):
        return False
    ov = min(x1, r.x1) - max(x0, r.x0)
    return ov > 0.5 * max(1.0, min(x1 - x0, r.x1 - r.x0))

def center_in(r, box):
    cx, cy = (r[0] + r[2]) / 2, (r[1] + r[3]) / 2
    return box[0] - 2 <= cx <= box[2] + 2 and box[1] - 2 <= cy <= box[3] + 2

def md_table(tbl):
    rows = [[norm(c or '').replace('|', r'\|').replace('\n', ' ') for c in row] for row in tbl]
    rows = [r for r in rows if any(c for c in r)]
    if not rows: return None
    w = max(len(r) for r in rows)
    rows = [r + [''] * (w - len(r)) for r in rows]
    head = rows[0] if any(rows[0]) else ['' ] * w
    body = rows[1:]
    out = ['| ' + ' | '.join(head) + ' |', '| ' + ' | '.join(['---'] * w) + ' |']
    out += ['| ' + ' | '.join(r) + ' |' for r in body]
    return '\n'.join(out)

items = []            # ('h',lvl,text) ('p',blockid,text) ('li',text) ('code',[lines]) ('img',name) ('tbl',md) ('num',text)
img_seq, seen = 0, {}
head_pos, goto_targets = [], []
unmatched_heads = []

for pno, page in enumerate(doc):
    heads = head_by_page.get(pno, [])
    links = [(fitz.Rect(l['from']), l['uri']) for l in page.get_links() if l.get('uri')]
    gotos = [(fitz.Rect(l['from']), l.get('page'), (l.get('to').y if l.get('to') else 0))
             for l in page.get_links() if l['kind'] == fitz.LINK_GOTO and l.get('page', -1) >= 0]

    code_buf = []
    def flush_code():
        while code_buf and not code_buf[-1].strip(): code_buf.pop()
        while code_buf and not code_buf[0].strip(): code_buf.pop(0)
        if code_buf:
            items.append(('code', list(code_buf)))
        code_buf.clear()

    blocks = page.get_text('dict', flags=fitz.TEXTFLAGS_DICT | fitz.TEXT_PRESERVE_IMAGES)['blocks']
    blocks.sort(key=lambda b: (round(b['bbox'][1], 1), round(b['bbox'][0], 1)))
    for bi, b in enumerate(blocks):
        if b['type'] == 1:
            flush_code()
            data = b['image']
            key = (len(data), data[:64])
            if key not in seen:
                img_seq += 1
                seen[key] = f'img-{img_seq:03d}.png'
                im = Image.open(io.BytesIO(data))
                if im.width > MAX_IMG_W:
                    im = im.resize((MAX_IMG_W, round(im.height * MAX_IMG_W / im.width)), Image.LANCZOS)
                im = im.convert('RGB').quantize(colors=96, dither=Image.FLOYDSTEINBERG)
                im.save(os.path.join(IMG_DIR, seen[key]), optimize=True)
            items.append(('img', seen[key], pno + 1))
            continue

        for line in b['lines']:
            spans = line['spans']
            raw = norm(''.join(s['text'] for s in spans))
            if not raw or raw in BOILER or re.fullmatch(r'\d{1,3}', raw):
                continue
            lb = line['bbox']

            textlen = sum(len(s['text'].strip()) for s in spans) or 1
            monolen = sum(len(s['text'].strip()) for s in spans if any(m in s['font'] for m in MONO))
            is_code = monolen / textlen > 0.6

            lvl = None
            for h in heads:
                if h[2]: continue
                if raw.upper() == h[1].upper() or (len(h[1]) > 12 and raw.upper().startswith(h[1].upper())):
                    lvl, h[2] = h[0], True
                    title = h[1]
                    break
            if lvl is not None:
                flush_code()
                head_pos.append((pno, lb[1]))
                items.append(('h', lvl, title))
                continue

            if is_code:
                code_buf.append(''.join(s['text'] for s in spans).rstrip())
                continue
            flush_code()

            bullet = any(bf in spans[0]['font'] for bf in BULLET_FONTS)
            parts = []
            for s in spans:
                if any(bf in s['font'] for bf in BULLET_FONTS): continue
                t = s['text'].replace(' ', ' ')
                if not t.strip():
                    parts.append(' '); continue
                uri = next((u for r, u in links if span_in_link(s, r)), None)
                if uri is None:
                    g = next((g for g in gotos if span_in_link(s, g[0])), None)
                    if g:
                        goto_targets.append((g[1], g[2]))
                        uri = f'@@GOTO{len(goto_targets)-1}@@'
                bold = bool(s['flags'] & 16) or 'Bold' in s['font']
                ital = bool(s['flags'] & 2) or 'Italic' in s['font']
                lead = t[:len(t) - len(t.lstrip())]
                trail = t[len(t.rstrip()):]
                mono = any(m in s['font'] for m in MONO)
                core = t.strip() if mono else esc(t.strip())
                if mono: core = f'`{core}`'
                if bold and ital: core = f'***{core}***'
                elif bold: core = f'**{core}**'
                elif ital: core = f'*{core}*'
                if uri: core = f'[{core}]({uri})'
                parts.append(lead + core + trail)
            body = re.sub(r'\s+', ' ', ''.join(parts)).strip()
            body = re.sub(r'\*\*\s*\*\*|(?<!\*)\*\s*\*(?!\*)|``', '', body).strip()
            if not body: continue
            if bullet: items.append(('li', body))
            elif re.match(r'^\d+[\.\)]\s', body): items.append(('num', body))
            else: items.append(('p', (pno, bi), body))
    flush_code()

for pno, hs in head_by_page.items():
    for h in hs:
        if not h[2]: unmatched_heads.append((pno + 1, h[1]))

# assemble
md, para, para_key = [], [], None
def flush_para():
    global para, para_key
    if para:
        md.append(' '.join(para).strip()); para = []; para_key = None

for it in items:
    kind = it[0]
    if kind == 'p':
        if para_key == it[1] or (para and it[2][:1].islower()):
            para.append(it[2])
        else:
            flush_para(); para.append(it[2])
        para_key = it[1]
    else:
        flush_para()
        if kind == 'h': md.append('#' * min(it[1], 6) + ' ' + it[2])
        elif kind == 'li': md.append('- ' + it[1])
        elif kind == 'num': md.append(it[1])
        elif kind == 'code': md.append('```\n' + '\n'.join(it[1]) + '\n```')
        elif kind == 'img': md.append(f'![Screenshot from page {it[2]} of the original PDF](images/{it[1]})')
        elif kind == 'tbl': md.append(it[1])
flush_para()

text = '\n\n'.join(md)
prev = None
while prev != text:
    prev = text
    text = re.sub(r'\[([^\]]*)\]\(([^)]+)\)(\s*)\[([^\]]*)\]\(\2\)', r'[\1\3\4](\2)', text)
text = re.sub(r'\[([^\]]*?)([.,;:!?)]+)\]\((https?[^)]+)\)', r'[\1](\3)\2', text)
text = re.sub(r'(?m)^(- .+)\n\n(?=- )', r'\1\n', text)
text = re.sub(r'(?m)^(\d+[\.\)] .+)\n\n(?=\d+[\.\)] )', r'\1\n', text)
text = re.sub(r'\n{3,}', '\n\n', text)

def slug(t):
    t = re.sub(r'[^\w\s-]', '', t.lower())
    return re.sub(r'\s+', '-', t).strip('-')

toc_lines, used, anchors = [], collections.Counter(), []
for line in text.split('\n'):
    mh = re.match(r'^(#{2,6}) (.+)$', line)
    if not mh: continue
    lvl, title = len(mh.group(1)), mh.group(2)
    a = slug(title)
    used[a] += 1
    if used[a] > 1: a = f'{a}-{used[a]-1}'
    anchors.append(a)
    if lvl <= 4:
        toc_lines.append('  ' * (lvl - 2) + f'- [{title}](#{a})')
toc = '## Contents\n\n' + '\n'.join(toc_lines) + '\n'

def resolve(idx):
    tp, ty = goto_targets[idx]
    best, bestd = None, None
    for i, (hp, hy) in enumerate(head_pos):
        if hp != tp: continue
        dy = abs(hy - (792 - ty))
        if bestd is None or dy < bestd: best, bestd = i, dy
    if best is None:
        cands = [i for i, (hp, _) in enumerate(head_pos) if hp <= tp]
        best = cands[-1] if cands else None
    return anchors[best] if best is not None and best < len(anchors) else None

def sub_goto(mo):
    a = resolve(int(mo.group(1)))
    return f'(#{a})' if a else '(#contents)'

text = re.sub(r'\(@@GOTO(\d+)@@\)', sub_goto, text)

m = doc.metadata
header = (f"# {m.get('title')}\n\n"
          f"*Converted from `Light Phone III Modding Guide.pdf` — {len(doc)} pages, "
          f"revision `{m.get('subject')}`, by {m.get('author')}. "
          f"Screenshots extracted to [`images/`](images/).*\n")
open(os.path.join(OUT_DIR, 'README.md'), 'w', encoding='utf-8').write(header + '\n' + toc + '\n' + text + '\n')
print('chars:', len(text), '| images:', img_seq, '| unmatched headings:', len(unmatched_heads))
for u in unmatched_heads[:20]: print('  MISS', u)
