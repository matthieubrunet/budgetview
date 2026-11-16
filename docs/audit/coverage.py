# Classes jamais exécutées, d'après les CSV de JaCoCo (appelé par coverage.sh).
#
# Usage : python3 -I docs/audit/coverage.py <module>.csv …
#
# Une classe de premier niveau compte comme jamais exécutée quand ni elle ni aucune de ses classes
# internes n'a d'instruction couverte. Sortie : un résumé par module, puis la liste des classes à 0 %
# triées par paquet, avec leur nombre de lignes. Les classes sans ligne exécutable (interfaces, annotations)
# ne sont pas comptées : la couverture ne dit rien d'elles.
import sys, csv, os, collections

classes = collections.OrderedDict()   # (module, paquet, classe) -> [lignes manquées, lignes couvertes, instr. couvertes]
for path in sys.argv[1:]:
    module = os.path.splitext(os.path.basename(path))[0]
    for row in csv.DictReader(open(path, encoding='utf-8')):
        top = row['CLASS'].split('.')[0]
        key = (module, row['PACKAGE'], top)
        c = classes.setdefault(key, [0, 0, 0])
        c[0] += int(row['LINE_MISSED'])
        c[1] += int(row['LINE_COVERED'])
        c[2] += int(row['INSTRUCTION_COVERED'])

summary = collections.OrderedDict()
for (module, _, _), (missed, covered, instr) in classes.items():
    s = summary.setdefault(module, [0, 0, 0, 0])
    if missed + covered == 0:
        continue
    s[0] += 1
    s[2] += missed + covered
    if instr == 0:
        s[1] += 1
        s[3] += missed + covered

print('module         classes  à 0 %   lignes  à 0 %')
for module, (n, zero, lines, zlines) in summary.items():
    print(f'{module:<14} {n:7} {zero:6} {lines:8} {zlines:6}')
print()
for (module, package, cls), (missed, covered, instr) in sorted(classes.items()):
    if instr == 0 and missed > 0:
        print(f'{module}\t{package.replace("/", ".")}.{cls}\t{missed}')
