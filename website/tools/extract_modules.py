import re, json, os, sys
root = sys.argv[1]
mm = re.sub(r'//[^\n]*', '', open(os.path.join(root, 'module/ModuleManager.java')).read())  # ignore commented-out modules
regs = re.findall(r'register\(new\s+([\w.]+)\(', mm)
idx = {}
for dp, _, fs in os.walk(os.path.join(root, 'modules')):
    for f in fs:
        if f.endswith('.java'): idx[f[:-5]] = os.path.join(dp, f)
def jstr(s): return bytes(s, 'utf-8').decode('unicode_escape') if '\\u' in s else s.replace('\\"', '"')
mods = []
for r in regs:
    cls = r.split('.')[-1]
    p = idx.get(cls)
    if not p or '/anticheatoff/' in p: continue   # gated test-only modules: not shown on the website
    src = open(p).read()
    m = re.search(r'super\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*Category\.(\w+)', src)
    if not m:  # HUD elements: super("Name", "Desc", x, y, enabled)
        m2 = re.search(r'super\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"', src)
        if not m2: continue
        if 'extends HudElement' not in src and '/modules/hud/' not in p: continue
        name, desc, cat = m2.group(1), m2.group(2), 'HUD'
    else:
        name, desc, cat = m.group(1), m.group(2), m.group(3)
    if cat in ('ANTI_CHEAT_OFF',) or name == 'Test Environment': continue
    on = bool(re.search(r'super\([^;]*?,\s*true\s*\)\s*;', src.split('super(',1)[1][:400] if 'super(' in src else ''))
    settings = []
    for sm in re.finditer(r'new (Bool|Number|Mode|Color)Setting\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,([^;]*?)\)\)?(?:\.visibleWhen|\.onChange|\.suffix|\)|;)', src):
        kind, sname, sdesc, rest = sm.group(1), sm.group(2), sm.group(3), sm.group(4)
        try:
            if kind == 'Bool':
                v = rest.strip().split(',')[0].strip()
                if v not in ('true', 'false'): continue
                settings.append({'t': 'bool', 'n': sname, 'd': sdesc, 'v': v == 'true'})
            elif kind == 'Number':
                nums = [x.strip() for x in rest.split(',')][:4]
                vals = [float(x.rstrip('fFdD')) for x in nums]
                if len(vals) < 4: continue
                settings.append({'t': 'num', 'n': sname, 'd': sdesc, 'v': vals[0], 'min': vals[1], 'max': vals[2], 'step': vals[3]})
            elif kind == 'Mode':
                opts = re.findall(r'"((?:[^"\\]|\\.)*)"', rest)
                if len(opts) < 2: continue
                settings.append({'t': 'mode', 'n': sname, 'd': sdesc, 'v': opts[0], 'o': opts[1:] if opts[0] in opts[1:] else opts})
            elif kind == 'Color':
                hx = re.search(r'0x([0-9A-Fa-f]{8})', rest)
                if not hx: continue
                settings.append({'t': 'color', 'n': sname, 'd': sdesc, 'v': '#' + hx.group(1)[2:]})
        except ValueError:
            continue
        if len(settings) >= 8: break
    mods.append({'n': name, 'd': desc.replace('\\"', '"'), 'c': cat, 'on': on, 's': settings})
cats = {'DONUTSMP': 'DonutSMP', 'COMBAT': 'Combat', 'MISC': 'Misc', 'VISUALS': 'Visuals', 'CLIENT': 'Client', 'WORLD': 'World', 'HUD': 'HUD', 'COSMETICS': 'Cosmetics', 'SETTINGS': 'Settings'}
for m in mods: m['c'] = cats.get(m['c'], m['c'])
out = os.path.join(sys.argv[2])
with open(out, 'w') as f:
    f.write('/* Generated from the Stella Client source (module names, descriptions and default settings). */\n')
    f.write('"use strict";\nwindow.STELLA_MODULES = ' + json.dumps(mods, ensure_ascii=False, separators=(',', ':')) + ';\n')
from collections import Counter
print(len(mods), Counter(m['c'] for m in mods), sum(len(m['s']) for m in mods), 'settings')
