"""txt2xml.py <in.txt> <out.xml> <array-name>: one item per line -> escaped Android string-array."""
import sys, re
src, dst, name = sys.argv[1:4]
lines = [l.strip() for l in open(src, encoding='utf-8') if l.strip()]
def esc(t):
    t = re.sub(r'^\s*\d+[\.\)]\s+', '', t)
    t = t.replace('\\', '\\\\').replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    t = t.replace("'", "\\'").replace('"', '\\"')
    if t[:1] in '@?': t = '\\' + t
    return t
items = '\n'.join(f'        <item>{esc(l)}</item>' for l in lines)
open(dst, 'w', encoding='utf-8').write(
    '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated: one line per item. Calm, neutral; never names a specific habit. -->\n'
    f'<resources>\n    <string-array name="{name}">\n{items}\n    </string-array>\n</resources>\n')
