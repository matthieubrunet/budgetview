#!/bin/bash
# Rapport de couverture JaCoCo (audit 1c et 1d, voir docs/audit-code.md).
#
# Usage (depuis la racine du dépôt, après un build) :
#   mvn -Pcoverage test -pl budgetview/bv_desktop      # → budgetview/bv_desktop/target/jacoco.exec
#   docs/audit/coverage.sh [fichier.exec] [dossier de sortie]
#
# Produit dans le dossier de sortie (défaut : budgetview/bv_desktop/target/coverage) :
#   <module>/index.html   rapport HTML par module
#   <module>.csv          couverture par classe
#   zero.txt              classes de premier niveau jamais exécutées (docs/audit/coverage.py)
# Les classes sont lues dans target/classes de chaque module : elles doivent correspondre à celles qui ont
# tourné (sinon JaCoCo signale « Execution data … does not match » et compte la classe à 0 %).
set -e
cd "$(dirname "$0")/../.."
EXEC=${1:-budgetview/bv_desktop/target/jacoco.exec}
OUT=${2:-budgetview/bv_desktop/target/coverage}
VERSION=0.8.13
CLI=$HOME/.m2/repository/org/jacoco/org.jacoco.cli/$VERSION/org.jacoco.cli-$VERSION-nodeps.jar
[ -f "$CLI" ] || mvn -q dependency:get -Dartifact=org.jacoco:org.jacoco.cli:$VERSION:jar:nodeps
mkdir -p "$OUT"
for m in budgetview/bv_desktop budgetview/bv_shared globs saxstack prevayler; do
  name=$(basename $m)
  java -jar "$CLI" report "$EXEC" --name "$name" \
    --classfiles $m/target/classes --sourcefiles $m/src/main/java \
    --csv "$OUT/$name.csv" --html "$OUT/$name" --quiet
done
python3 -I docs/audit/coverage.py "$OUT"/*.csv > "$OUT/zero.txt"
head -20 "$OUT/zero.txt"
echo "… détail : $OUT/zero.txt"
