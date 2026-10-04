"""merge.py <dir>... : validate build/quit-tr translations for values-<dir>, write them into the app."""
import sys, re, os, subprocess, textwrap, collections
import xml.etree.ElementTree as ET
B = os.path.abspath('build/quit-tr')  # run from repo root
RES = os.path.abspath('app/src/main/res')
PARTS = 10
NONLATIN = set('hi ar ru ja ko th zh-rCN zh-rTW bn ta te mr gu kn ml pa ur'.split())
ARG = re.compile(r'%(\d+\$)?0?\d*[ds%]')
def args(t): return sorted(m.group(0) for m in ARG.finditer(t or ''))
def parse(text): return ET.fromstring('<resources>' + text + '</resources>')
en = parse(open(f'{B}/quit_strings_en.xml').read())
en_parts = [[l.strip() for l in open(f'{B}/thoughts/en_p{n}.txt') if l.strip()] for n in range(1, PARTS + 1)]
ok_all = True
for d in sys.argv[1:]:
    errs, lines = [], []
    for n in range(1, PARTS + 1):
        p = f'{B}/tr/{d}_p{n}.txt'
        if not os.path.exists(p): errs.append(f'missing part {n}'); continue
        part = [l.strip() for l in open(p, encoding='utf-8') if l.strip()]
        src = en_parts[n - 1]
        if abs(len(part) - len(src)) > 2: errs.append(f'part {n}: {len(part)} lines vs {len(src)}')
        # Quality gate: padding, untranslated lines, Latin-only lines in a non-Latin script.
        dup = len(part) - len(set(part))
        same = sum(1 for a, b in zip(src, part) if a == b)
        asc = sum(1 for b in part if b.isascii()) if d in NONLATIN else 0
        if dup > 2 or same > 2 or asc > 2: errs.append(f'part {n}: dup={dup} untranslated={same} latin={asc}')
        lines += part
    sp = f'{B}/tr/{d}_strings.xml'
    tr = None
    if not os.path.exists(sp): errs.append('missing strings')
    else:
        try:
            raw = open(sp, encoding='utf-8').read()
            raw = re.sub(r'<\?xml[^>]*\?>', '', raw).replace('<resources>', '').replace('</resources>', '')
            tr = parse(raw)
        except Exception as e:
            errs.append(f'strings: {e}')
    if tr is not None:
        existing = open(f'{RES}/values-{d}/strings.xml', encoding='utf-8').read()
        quants = re.findall(r'quantity="(\w+)"', re.search(r'<plurals name="data_import_habits">(.*?)</plurals>', existing, re.S).group(1))
        for pl in tr.findall('plurals'):  # fill missing quantities from 'other', drop ones the language lacks
            items = {i.get('quantity'): i for i in pl}
            if 'other' in items:
                for q in quants:
                    if q not in items:
                        x = ET.SubElement(pl, 'item', quantity=q); x.text = items['other'].text
                for q, i in items.items():
                    if q not in quants: pl.remove(i)
                pl[:] = sorted(pl, key=lambda i: quants.index(i.get('quantity')))
        ET.indent(tr, space='    ')
        raw = ET.tostring(tr, encoding='unicode').replace('<resources>', '').replace('</resources>', '')
        tmap = {(e.tag, e.get('name')): e for e in tr}
        bad_apos = lambda t: "'" in re.sub(r"\\'", '', t or '')
        for e in en:
            t = tmap.get((e.tag, e.get('name')))
            if t is None: errs.append(f'missing {e.get("name")}'); continue
            if e.tag == 'string':
                if args(e.text) != args(t.text): errs.append(f'args {e.get("name")}: {t.text!r}')
                if bad_apos(t.text): errs.append(f'unescaped apostrophe {e.get("name")}')
            elif e.tag == 'plurals':
                if sorted(i.get('quantity') for i in t) != sorted(quants): errs.append(f'quantities {e.get("name")}')
                for i in t:
                    has = '%d' in (i.text or '')
                    if e.get('name') == 'quit_days_word' and has: errs.append('quit_days_word has number')
                    if e.get('name') != 'quit_days_word' and not has and not (i.get('quantity') == 'zero' or (d == 'ar' and i.get('quantity') in ('one', 'two'))): errs.append(f'{e.get("name")}/{i.get("quantity")} lacks %d')
                    if bad_apos(i.text): errs.append(f'unescaped apostrophe {e.get("name")}')
            else:
                if len(list(t)) < 6: errs.append(f'{e.get("name")} only {len(list(t))} items')
                if any(bad_apos(i.text) for i in t): errs.append(f'unescaped apostrophe in {e.get("name")}')
    if errs:
        ok_all = False
        print(d, 'FAIL', *errs[:12], sep='\n  ')
        continue
    tmp = f'{B}/tr/{d}_all.txt'
    open(tmp, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    subprocess.run(['python3', 'scripts/quit-i18n/txt2xml.py', tmp, f'{RES}/values-{d}/quit_thoughts.xml', 'quit_thoughts'], check=True)
    p = f'{RES}/values-{d}/strings.xml'
    s = open(p, encoding='utf-8').read()
    s = re.sub(r'\n+    <!-- Quit tracker -->.*?<!-- /Quit tracker -->\n', '\n', s, flags=re.S)
    body = re.sub(r'^\s*<!--.*?-->\s*\n', '', textwrap.dedent(raw.strip('\n')) + '\n', flags=re.M).strip('\n')
    s = s.rstrip()[:-len('</resources>')].rstrip() + '\n\n    <!-- Quit tracker -->\n' + textwrap.indent(body, '    ') + '\n    <!-- /Quit tracker -->\n</resources>\n'
    # Word-only plural is right here (the number is drawn separately), lint can't know that.
    s = s.replace('<plurals name="quit_days_word">', '<plurals name="quit_days_word" xmlns:tools="http://schemas.android.com/tools" tools:ignore="ImpliedQuantity">')
    open(p, 'w', encoding='utf-8').write(s)
    print(d, 'OK', len(lines), 'thoughts')
sys.exit(0 if ok_all else 1)
