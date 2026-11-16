# Accessibilité des classes depuis DesktopApp, avec et sans les chemins coupés en mode local.
# Voir docs/audit-code.md, section « 1a, inventaire ».
#
# Usage (depuis budgetview/, après un build) :
#   jdeps -verbose:class -filter:none -e 'com\.budgetview\..*' \
#     bv_desktop/target/classes bv_shared/target/classes 2>/dev/null \
#     | grep -- '->' | awk '{print $1, $3}' | grep '^com.budgetview' | sort -u > deps.txt
#   python3 -I ../docs/audit/reach.py deps.txt mortes.txt inaccessibles.txt [Classe …]
#
# mortes.txt : classes accessibles seulement par les arêtes de `cuts` (code mort en local).
# inaccessibles.txt : classes qu'aucun chemin ne relie à DesktopApp (chantier 1e).
# Classes en argument : affiche un chemin qui les atteint encore malgré les coupures.
# Une coupure qui vise une classe disparue est signalée « INCONNU » : la retirer de `cuts`.
import sys, collections
P='com.budgetview.'
edges=collections.defaultdict(set)
for line in open(sys.argv[1]):
    a,b=line.split()
    a=a.split('$')[0][len(P):]; b=b.split('$')[0][len(P):]
    if a!=b: edges[a].add(b)
cuts={
 # lots 1 à 6 supprimés le 2026-11-16 ; reste la synchro Budgea (1b).
 'desktop.utils.MenuBarBuilder':{'CloudDemoAction'},
 'desktop.importer.steps.ImportedFileSelectionPanel':{'desktop.cloud.CloudIntroPanel'},
 'desktop.cloud.AutoCloudImportView':{'desktop.cloud.AutoImporter'},
 'desktop.Application':{'desktop.cloud.CloudService'},
}
# résoudre les noms courts des actions
allc=set(edges)|{b for s in edges.values() for b in s}
def fix(n):
    if n in allc: return n
    m=[c for c in allc if c.endswith('.'+n.split('.')[-1])]
    return m[0] if len(m)==1 else n
cuts={k:{fix(x) for x in v} for k,v in cuts.items()}
for k,v in cuts.items():
    for x in v:
        if x not in allc: print('INCONNU',x,file=sys.stderr)
def reach(cut):
    seen=set(); st=['desktop.DesktopApp']
    while st:
        c=st.pop()
        if c in seen: continue
        seen.add(c)
        for d in edges.get(c,()):
            if cut and d in cuts.get(c,()): continue
            st.append(d)
    return seen
full=reach(False); local=reach(True)
dead=sorted(full-local)
print('accessibles:',len(full),'en local:',len(local),'morts par interrupteur:',len(dead))
open(sys.argv[2],'w').write('\n'.join(dead)+'\n')
unreach=sorted(allc-full)
open(sys.argv[3],'w').write('\n'.join(unreach)+'\n')
print('jamais accessibles depuis DesktopApp:',len(unreach),'sur',len(allc))
# chemin depuis DesktopApp (avec coupures) vers une cible
def path(target):
    from collections import deque
    prev={'desktop.DesktopApp':None}; q=deque(['desktop.DesktopApp'])
    while q:
        c=q.popleft()
        if c==target: break
        for d in sorted(edges.get(c,())):
            if d in cuts.get(c,()): continue
            if d not in prev: prev[d]=c; q.append(d)
    if target not in prev: return None
    p=[];c=target
    while c: p.append(c); c=prev[c]
    return ' -> '.join(reversed(p))
for t in sys.argv[4:]:
    print(path(fix(t)))
