#!/bin/bash
# Fabrique « BudgetView 6.app » (Java 21 embarqué) avec jpackage et l'installe
# dans ~/Applications.
#
#   ./package-mac.sh            empaquette le livrable existant (le construit s'il manque)
#   ./package-mac.sh --build    reconstruit le livrable avant d'empaqueter
#
# L'application produite utilise les données par défaut : celles de BudgetView 5.01
# (~/Library/Application Support/BudgetView, suivi de son redirect.txt). Ses triggers
# d'upgrade les migrent en 6.00 au premier lancement, sans retour pour la 5.01.
# Ne pas lancer la 5.01 et la 6.00 en même temps : l'instance unique est active, la
# seconde lancée se connecte à la première et se ferme sans rien afficher.
#
# Variables :
#   JAVA_HOME   JDK 21 (défaut : openjdk@21 de Homebrew), qui fournit jpackage
#   DEST        dossier d'installation (défaut : ~/Applications)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DESKTOP="$ROOT/budgetview/bv_desktop"
DIST="$DESKTOP/target/dist"
OUT="$DESKTOP/target/mac"
NAME="BudgetView 6"
DEST="${DEST:-$HOME/Applications}"
ICON_SRC="$ROOT/budgetview/bv_website/resources/files/images/press/budgetview_icon_512.png"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"

if ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21\.'; then
  echo "JDK 21 introuvable dans JAVA_HOME=$JAVA_HOME (voir CLAUDE.md, section Build)." >&2
  exit 1
fi

if [ "${1:-}" = "--build" ] || [ ! -f "$DIST/budgetview.jar" ]; then
  echo "Construction du livrable…"
  (cd "$ROOT" && mvn -q -B -Pgen-version clean install -DskipTests)
fi
if [ ! -f "$DESKTOP/src/main/resources/demo/demo-fr.snapshot" ]; then
  echo "Attention : comptes de démo absents, le bouton « Compte de démo » ne marchera pas (CLAUDE.md, étape 3)." >&2
fi

rm -rf "$OUT"
mkdir -p "$OUT"

# Icône : .icns construit depuis le PNG 512 px du site.
ICONSET="$OUT/BudgetView.iconset"
mkdir -p "$ICONSET"
for s in 16 32 128 256 512; do
  sips -z $s $s "$ICON_SRC" --out "$ICONSET/icon_${s}x${s}.png" >/dev/null
  d=$((s * 2)); [ $d -gt 512 ] && d=512
  sips -z $d $d "$ICON_SRC" --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
done
iconutil -c icns "$ICONSET" -o "$OUT/BudgetView.icns"

"$JAVA_HOME/bin/jpackage" \
  --type app-image \
  --input "$DIST" \
  --main-jar budgetview.jar \
  --main-class com.budgetview.desktop.DesktopApp \
  --name "$NAME" \
  --app-version 6.0 \
  --icon "$OUT/BudgetView.icns" \
  --mac-package-identifier fr.budgetview.desktop6 \
  --mac-package-name "BudgetView" \
  --java-options "-Dfile.encoding=UTF-8 -Xmx1024m" \
  --dest "$OUT"

mkdir -p "$DEST"
rm -rf "$DEST/$NAME.app"
cp -R "$OUT/$NAME.app" "$DEST/"
echo "Installé : $DEST/$NAME.app"
