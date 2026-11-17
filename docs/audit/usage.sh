#!/bin/bash
# Chantier 1e (docs/audit-code.md) : membres et classes que rien n'atteint depuis DesktopApp, selon ProGuard.
# Usage, depuis la racine du dépôt, après `mvn -Pgen-version clean install -DskipTests` :
#   docs/audit/usage.sh [dossier de sortie, par défaut target/usage]
# Produit usage.txt (rapport brut de ProGuard) et triage.json (membres de l'application classés).
set -euo pipefail
JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}
ROOT=$(pwd)
OUT=${1:-target/usage}
DIST=$ROOT/budgetview/bv_desktop/target/dist
M2=~/.m2/repository
PG=7.6.1
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

[ -f "$M2/com/guardsquare/proguard-base/$PG/proguard-base-$PG.jar" ] \
  || mvn -q dependency:get -Dartifact=com.guardsquare:proguard-base:$PG
CP=$(find "$M2/com/guardsquare/proguard-base/$PG" "$M2/com/guardsquare/proguard-core" \
  "$M2/org/jetbrains/kotlin/kotlin-stdlib" "$M2/com/google/code/gson/gson" \
  "$M2/org/apache/logging/log4j/log4j-api" "$M2/org/apache/logging/log4j/log4j-core" \
  -name '*.jar' ! -name '*sources*' | tr '\n' ':')

{
  # Les modules du dépôt sont analysés ; les bibliothèques tierces ne servent qu'à résoudre.
  echo "-injars $DIST/budgetview.jar(!META-INF/**)"
  for l in "$DIST"/lib/*.jar; do
    case $(basename "$l") in
      bv_shared-*|globs-*|saxstack-*|prevayler-*) echo "-injars $l(!META-INF/**)" ;;
      *) echo "-libraryjars $l" ;;
    esac
  done
  echo "-libraryjars $JAVA_HOME/jmods(!**.jar;!module-info.class)"
  echo "-outjars $OUT/out.jar"
  grep -rhoE 'class="[a-zA-Z0-9_.$]+"' budgetview/bv_desktop/src/main/resources | sort -u \
    | sed -E 's/class="(.*)"/-keep class \1 { public <init>(); }/'
  cat "$ROOT/docs/audit/proguard.pro"
} > "$OUT/bv.pro"

(cd "$OUT" && "$JAVA_HOME/bin/java" -cp "$CP" proguard.ProGuard @bv.pro > proguard.log 2>&1)
rm -f "$OUT/out.jar"
python3 -I "$ROOT/docs/audit/usage.py" "$OUT/usage.txt" "$OUT/triage.json"
