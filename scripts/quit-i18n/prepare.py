"""Rebuild the translator inputs in build/quit-tr from the English resources.
Run from the repo root:  python3 scripts/quit-i18n/prepare.py
Creates build/quit-tr/thoughts/en_p1..10.txt (about 100 lines each), quit_strings_en.xml and tr/ (output dir)."""
import os, xml.etree.ElementTree as ET
B = 'build/quit-tr'
os.makedirs(f'{B}/thoughts', exist_ok=True); os.makedirs(f'{B}/tr', exist_ok=True)
items = [i.text.replace("\\'", "'").replace('\\"', '"').replace('\\@', '@').replace('\\?', '?')
         for i in ET.parse('app/src/main/res/values/quit_thoughts.xml').getroot().iter('item')]
for n in range(10):
    open(f'{B}/thoughts/en_p{n+1}.txt', 'w').write('\n'.join(items[n*101:(n+1)*101]) + '\n')
s = open('app/src/main/res/values/strings.xml').read()
open(f'{B}/quit_strings_en.xml', 'w').write(s[s.index('    <!-- Quit tracker'):s.rindex('</resources>')])
print(len(items), 'thoughts')
