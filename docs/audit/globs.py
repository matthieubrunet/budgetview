# Chantier 2a (docs/audit-code.md) : ce que BudgetView n'appelle pas dans globs, selon ProGuard.
# Usage, depuis la racine, après docs/audit/usage.sh (qui produit usage.txt) :
#   python3 -I docs/audit/globs.py target/usage/usage.txt
# 1. Classes de premier niveau inutilisées, par paquet, avec les autres sources qui citent leur nom :
#    S = bv_server, bv_analytics ou Android (hors build) ; T = tests de bv_desktop ; g = tests de globs.
# 2. Membres isolés des classes vivantes, classés par occurrences de leur nom (indicatif, comme usage.py).
import sys, re, os, subprocess, collections

usage = sys.argv[1]
classes, members, cur = set(), collections.defaultdict(list), None
for line in open(usage):
    line = line.rstrip('\n')
    if not line.strip():
        continue
    if line.startswith('    '):
        members[cur].append(re.sub(r'^\d+:\d+:', '', line.strip()))
    elif line.endswith(':'):
        cur = line[:-1]
    else:
        classes.add(line)
        cur = None

MAIN = ['globs/src/main', 'budgetview/bv_desktop/src/main', 'budgetview/bv_shared/src/main']
SRV = ['budgetview/bv_server', 'budgetview/bv_analytics', 'budgetview-android']
BTEST = ['budgetview/bv_desktop/src/test', 'budgetview/bv_shared/src/test']
GTEST = ['globs/src/test']
cache = {}
def count(name, paths):
    key = (name, tuple(paths))
    if key not in cache:
        r = subprocess.run(['git', 'grep', '-w', '-c', '-F', name, '--'] + paths, capture_output=True, text=True)
        cache[key] = sum(int(l.rsplit(':', 1)[1]) for l in r.stdout.splitlines())
    return cache[key]

print('1. Classes de premier niveau inutilisées\n')
agg = collections.defaultdict(lambda: [0, 0])
for c in sorted(c for c in classes if c.startswith('org.globsframework') and '$' not in c):
    path = 'globs/src/main/java/' + c.replace('.', '/') + '.java'
    if not os.path.exists(path):
        continue
    simple, n = c.split('.')[-1], sum(1 for _ in open(path))
    flags = ('S' if count(simple, SRV) else ' ') + ('T' if count(simple, BTEST) else ' ') \
        + ('g' if count(simple, GTEST) else ' ')
    print(f'{flags} {n:5} {c[19:]}')
    pkg = '.'.join(c.split('.')[2:-1])
    agg[pkg][0] += 1
    agg[pkg][1] += n
print(f"\n{'paquet':34}classes lignes")
for k in sorted(agg):
    print(f'{k:34}{agg[k][0]:7}{agg[k][1]:7}')
print(f"{'total':34}{sum(a[0] for a in agg.values()):7}{sum(a[1] for a in agg.values()):7}")

print('\n2. Membres isolés des classes vivantes\n')
cat, lst = collections.Counter(), collections.defaultdict(list)
for c, ms in members.items():
    if not c.startswith('org.globsframework') or c in classes or c.split('$')[0] in classes:
        continue
    for sig in ms:
        m = re.search(r'([A-Za-z0-9_$<>]+)\(', sig)
        name = m.group(1) if m else sig.split()[-1]
        if '$' in name or name.startswith(('access', 'lambda')):
            continue
        if name == '<init>':
            k = 'constructeur'
        elif count(name, MAIN) > 1:
            k = 'cité ailleurs (homonymes, interfaces)'
        elif count(name, SRV):
            k = 'serveur seul'
        elif count(name, BTEST + GTEST):
            k = 'tests seuls'
        else:
            k = 'aucune autre occurrence'
        cat[k] += 1
        lst[k].append(f'{c[19:]}  {sig}')
for k in sorted(cat):
    print(f'{k:40}{cat[k]:5}')
for k in ('aucune autre occurrence', 'tests seuls', 'serveur seul'):
    print(f'\n{k} :')
    for s in sorted(lst[k]):
        print('  ', s)
