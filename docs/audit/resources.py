#!/usr/bin/env python3
"""Audit 1f : ressources de bv_desktop qui ne sont plus référencées.

Lecture seule. Usage : python3 docs/audit/resources.py [--detail]

Cherche, pour chaque ressource de bv_desktop/src/main/resources, une citation dans le code principal
(bv_desktop, bv_shared, globs) et dans les autres ressources (.splits, .xml, .properties, .html).
Les tests ne comptent pas : une ressource que seuls les tests citent est signalée à part.

Catégories traitées : images, fichiers .splits, clés de langue, définitions de banques, divers.
Les références dynamiques connues sont décrites dans DYNAMIC_IMAGES ; pour les clés de langue,
une clé dont un préfixe (à une frontière « . ») figure en littéral dans le code est classée
« dynamique possible » plutôt qu'orpheline.
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DESKTOP = os.path.join(ROOT, "budgetview", "bv_desktop")
RES = os.path.join(DESKTOP, "src", "main", "resources")
MAIN_SRC = [
    os.path.join(DESKTOP, "src", "main", "java"),
    os.path.join(ROOT, "budgetview", "bv_shared", "src", "main", "java"),
    os.path.join(ROOT, "globs", "src", "main", "java"),
]
TEST_SRC = [
    os.path.join(DESKTOP, "src", "test"),
    os.path.join(ROOT, "budgetview", "bv_shared", "src", "test"),
]
TEXT_EXT = (".java", ".splits", ".xml", ".properties", ".html", ".txt", ".js", ".css")
LANG_FILES = ("lang.properties", "lang_en.properties", "lang_fr.properties")

# Images désignées par un chemin construit : (préfixe, noms, suffixe), d'après le code cité.
DYNAMIC_IMAGES = {
    # NavigationIcons.get : "cards/" + Card.getName().toLowerCase() + ".png"
    "cards": ["cards/%s.png" % n for n in
              ("home", "categorization", "budget", "analysis", "data", "projects")],
    # ColorTheme.getImagePath : "color_themes/" + imagePath
    "color_themes": ["color_themes/%s.png" % n for n in
                     ("standard", "blue", "pink", "black", "turquoise", "orange", "green")],
    # WelcomeView : Lang.getFilePath("welcome", "welcome.png") → welcome/<lang>/welcome.png
    "welcome": ["welcome/fr/welcome.png", "welcome/en/welcome.png"],
}


def read_tree(dirs, exclude_lang=True):
    """Texte concaténé de tous les fichiers texte, par fichier."""
    out = {}
    for d in dirs:
        for base, _, files in os.walk(d):
            for f in files:
                if not f.endswith(TEXT_EXT):
                    continue
                if exclude_lang and f in LANG_FILES:
                    continue
                p = os.path.join(base, f)
                with open(p, encoding="utf-8", errors="replace") as fh:
                    out[p] = fh.read()
    return out


def rel(p):
    return os.path.relpath(p, ROOT)


def load_keys(path):
    keys = []
    with open(path, encoding="iso-8859-1") as fh:
        cont = False
        for line in fh:
            s = line.rstrip("\n")
            if cont:
                cont = s.endswith("\\")
                continue
            t = s.strip()
            if not t or t[0] in "#!":
                continue
            m = re.match(r"((?:[^=:\s\\]|\\.)+)\s*[=:\s]", t + " ")
            if m:
                keys.append(m.group(1))
            cont = s.endswith("\\")
    return keys


def main():
    detail = "--detail" in sys.argv
    main_files = read_tree(MAIN_SRC)
    main_files.update(read_tree([RES]))
    test_files = read_tree(TEST_SRC)
    main_text = "\n".join(main_files.values())
    test_text = "\n".join(test_files.values())

    report = defaultdict(list)

    # --- Images ---------------------------------------------------------------------------------
    dynamic = {p for names in DYNAMIC_IMAGES.values() for p in names}
    images_dir = os.path.join(RES, "images")
    n_images = 0
    for base, _, files in os.walk(images_dir):
        for f in sorted(files):
            if not f.endswith((".png", ".gif", ".jpg")):
                continue
            n_images += 1
            path = os.path.relpath(os.path.join(base, f), images_dir)
            if path in dynamic:
                continue
            pattern = re.compile(r'["/=\s]' + re.escape(path) + r'["\s<]')
            if pattern.search(main_text):
                continue
            if pattern.search(test_text):
                report["images (tests seulement)"].append(path)
            else:
                report["images orphelines"].append(path)
    for d, names in DYNAMIC_IMAGES.items():
        for p in names:
            if not os.path.exists(os.path.join(images_dir, p)):
                report["images dynamiques manquantes"].append(p)

    # --- Fichiers .splits -----------------------------------------------------------------------
    layout_dir = os.path.join(RES, "layout")
    n_splits = 0
    for base, _, files in os.walk(layout_dir):
        for f in sorted(files):
            if not f.endswith(".splits"):
                continue
            n_splits += 1
            path = "/" + os.path.relpath(os.path.join(base, f), RES)
            if '"' + path + '"' in main_text:
                continue
            if '"' + path + '"' in test_text:
                report["splits (tests seulement)"].append(path)
            else:
                report["splits orphelins"].append(path)

    # --- Clés de langue -------------------------------------------------------------------------
    i18n = os.path.join(RES, "i18n")
    keys_by_file = {f: load_keys(os.path.join(i18n, f)) for f in LANG_FILES}
    all_keys = sorted(set().union(*keys_by_file.values()))
    java_literals = set()
    for p, t in main_files.items():
        if p.endswith(".java"):
            java_literals.update(re.findall(r'"((?:[^"\\\n]|\\.)*)"', t))
    splits_refs = set()
    for p, t in main_files.items():
        if p.endswith(".splits"):
            splits_refs.update(re.findall(r'\$([A-Za-z][\w.\-]*)', t))
    # Littéraux d'attributs xml/properties (banques, couleurs) : citations directes possibles.
    other_text = "\n".join(t for p, t in main_files.items() if not p.endswith((".java", ".splits")))
    test_literals = set(re.findall(r'"((?:[^"\\\n]|\\.)*)"', test_text))
    prefixes = {l for l in java_literals if l.endswith(".") or l.endswith("_")}
    suffixes = {l for l in java_literals if l.startswith(".")}
    for k in all_keys:
        if k in java_literals or k in splits_refs or re.search(r'["\s=$]' + re.escape(k) + r'["\s<]', other_text):
            continue
        parts = k.split(".")
        dyn = [p for p in (".".join(parts[:i]) + "." for i in range(1, len(parts))) if p in prefixes]
        dyn += [s for s in ("." + ".".join(parts[i:]) for i in range(1, len(parts))) if s in suffixes]
        test_dyn = [p for p in (".".join(parts[:i]) + "." for i in range(1, len(parts)))
                    if p in test_literals]
        if dyn:
            report["clés à préfixe dynamique"].append("%s  [%s]" % (k, ", ".join(dyn)))
        elif k in test_literals or test_dyn:
            report["clés (tests seulement)"].append(k)
        else:
            report["clés orphelines"].append(k)
    for f, ks in keys_by_file.items():
        missing = sorted(set(all_keys) - set(ks))
        report["clés absentes de " + f] = missing

    # --- Banques --------------------------------------------------------------------------------
    banks_dir = os.path.join(RES, "banks")
    with open(os.path.join(banks_dir, "bankList.txt")) as fh:
        listed = {l.strip() for l in fh if l.strip()}
    n_banks = 0
    for base, _, files in os.walk(banks_dir):
        for f in sorted(files):
            if not f.endswith(".xml"):
                continue
            n_banks += 1
            path = os.path.relpath(os.path.join(base, f), banks_dir)
            if path not in listed:
                report["banques hors de bankList.txt"].append(path)
    for p in sorted(listed):
        if not os.path.exists(os.path.join(banks_dir, p)):
            report["bankList.txt : fichiers manquants"].append(p)

    # --- Divers ---------------------------------------------------------------------------------
    for d in ("connectors", "css", "docs", "fonts", "demo"):
        for base, _, files in os.walk(os.path.join(RES, d)):
            for f in sorted(files):
                path = os.path.relpath(os.path.join(base, f), RES)
                if f in main_text.replace(path, "") or path in main_text:
                    continue
                report["divers sans citation"].append(path)
    for f in os.listdir(RES):
        if os.path.isfile(os.path.join(RES, f)) and f not in main_text:
            report["divers sans citation"].append(f)

    print("Ressources : %d images, %d .splits, %d clés de langue, %d banques"
          % (n_images, n_splits, len(all_keys), n_banks))
    for cat in sorted(report):
        items = report[cat]
        print("\n== %s : %d" % (cat, len(items)))
        for it in (items if detail or len(items) <= 40 else items[:40] + ["…"]):
            print("  " + it)


if __name__ == "__main__":
    main()
