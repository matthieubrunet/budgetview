# Tri du rapport -printusage de ProGuard (chantier 1e, docs/audit-code.md). Lancé par usage.sh, depuis la racine.
#   python3 -I docs/audit/usage.py usage.txt triage.json
# Affiche le décompte par module, puis classe chaque membre isolé de l'application (classe englobante gardée)
# selon les occurrences de son nom (git grep -w) dans le code principal, les tests et bv_server.
# Le classement est indicatif : un nom fréquent (get, show…) passe pour « cité » même s'il est mort.
import sys, re, json, subprocess, collections

usage, dest = sys.argv[1], sys.argv[2]
classes, members, cur = [], collections.defaultdict(list), None
for line in open(usage):
    line = line.rstrip('\n')
    if not line.strip():
        continue
    if line.startswith('    '):
        members[cur].append(re.sub(r'^\d+:\d+:', '', line.strip()))
    elif line.endswith(':'):
        cur = line[:-1]
    else:
        classes.append(line)
        cur = None

def module(c):
    if c.startswith('com.budgetview.shared'): return 'bv_shared'
    if c.startswith(('com.budgetview', 'com.jgoodies')): return 'bv_desktop'
    if c.startswith('org.globsframework'): return 'globs'
    return '.'.join(c.split('.')[:2])

nc = collections.Counter(module(c) for c in classes)
nm = collections.Counter()
for c, ms in members.items():
    nm[module(c)] += len(ms)
for k in sorted(set(nc) | set(nm)):
    print(f'{k:15} classes entières {nc[k]:4}   membres isolés {nm[k]:5}')

app = lambda c: module(c) in ('bv_desktop', 'bv_shared')
dead = set(classes)
print('\nClasses de l\'application entièrement inutilisées :')
for c in sorted(c for c in classes if app(c)):
    print('  ', c)

MAIN = ['budgetview/bv_desktop/src/main', 'budgetview/bv_shared/src/main', 'globs/src/main']
TEST = ['budgetview/bv_desktop/src/test', 'budgetview/bv_shared/src/test']
SERVER = ['budgetview/bv_server/src']
# Groupes gardés par décision : cloud et Budgea (1b), client serveur, Windows et Linux (hors champ de 1d).
KEPT = ('shared.cloud', 'desktop.cloud', 'client.exceptions', 'shared.http', 'shared.json', 'budgea', 'io.budgea')
cache = {}
def count(name, paths):
    r = subprocess.run(['git', 'grep', '-w', '-c', '-F', name, '--'] + paths, capture_output=True, text=True)
    return sum(int(l.rsplit(':', 1)[1]) for l in r.stdout.splitlines())

out = collections.defaultdict(list)
for c, ms in members.items():
    if not app(c) or c.split('$')[0] in dead:
        continue
    for sig in ms:
        m = re.search(r'([A-Za-z0-9_$<>]+)\(', sig)
        name = m.group(1) if m else sig.split()[-1]
        if '$' in name or name.startswith(('access', 'lambda')):
            continue
        if c[len('com.budgetview.'):].startswith(KEPT) or any(w in c for w in ('Windows', 'Linux', 'Unix')):
            out['groupe gardé'].append((c, sig)); continue
        if name not in cache:
            cache[name] = (count(name, MAIN), count(name, TEST), count(name, SERVER))
        main, test, server = cache[name]
        if 'abstract' in sig: k = 'abstrait'
        elif name == c.split('.')[-1].split('$')[-1]: k = 'constructeur'
        elif main > 1: k = 'champ cité ailleurs (constante recopiée ?)' if '(' not in sig else 'nom cité ailleurs'
        elif test: k = 'servi par les seuls tests'
        elif server: k = 'servi par bv_server'
        else: k = 'aucune autre occurrence'
        out[k].append((c, sig))
print('\nMembres isolés de l\'application :')
for k, v in sorted(out.items(), key=lambda kv: -len(kv[1])):
    print(f'{len(v):5}  {k}')
json.dump(out, open(dest, 'w'), ensure_ascii=False, indent=0)
