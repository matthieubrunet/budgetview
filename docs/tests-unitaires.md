# Tests unitaires : sondage et chantier à ouvrir

**État : chantier non commencé.** À lancer une fois terminé le chantier de simplification
([audit-code.md](audit-code.md)), selon la stratégie ci-dessous. Ce document consigne le sondage du 2026-11-18 et ce qu'il reste
à faire pour passer d'un sondage à un diagnostic.

## Quand le faire : après l'audit, sauf exception ciblée

Décidé le 2026-11-18.

1. **Étape 1 de l'audit (code mort) : n'ajouter aucun test unitaire.**
   - Ce qui vérifie une suppression, c'est la compilation et les tests fonctionnels ; un test de
     cas limites ne détecte pas une suppression de trop.
   - Les étapes 1c et 1d repèrent le code jamais exécuté par la couverture JaCoCo : un test
     unitaire qui appelle une méthode que l'application n'utilise plus la ferait passer pour vivante.
   - On écrirait des tests pour du code que l'audit va peut-être supprimer.
2. **Étapes 2 à 4 (frameworks forkés, ce que Java 21 fournit, contournements) : tests de
   caractérisation, classe par classe.** Juste avant de réécrire une classe à comportement constant,
   écrire les tests qui figent son comportement actuel, **défauts compris**, sans rien corriger.
   Exemple : compléter `MonthTest` (lot E) avant de remplacer le `Calendar` de `Month` par `java.time`.
3. **Après l'audit : le chantier complet** ci-dessous (mesure de couverture, puis corrections), sur
   le code qui reste. Les corrections changent le comportement : les mêler à l'audit rendrait ses
   vérifications « à comportement constant » illisibles.

Rien ne presse côté QIF : Matthieu n'utilise pas ce format. La boucle infinie attend donc le lot B.

## Portée du sondage : étroite

Quatre classes de calcul pur, lues en entier et essayées sur des cas limites, soit environ
1 000 lignes sur ~1 100 classes dans `bv_desktop` :

| Classe | Lignes | Test unitaire existant |
|---|---|---|
| `model/Month` | 424 | `MonthTest` (109 lignes) |
| `io/importer/qif/QifParser` | 262 | aucun |
| `shared/utils/Amounts` (bv_shared) | 168 | `AmountsTest` (72 lignes, dans bv_desktop) |
| `io/importer/csv/CsvReader` | 133 | `CsvReaderTest` (64 lignes) |

Limites à garder en tête avant d'en tirer une conclusion générale :

- **Échantillon biaisé** : classes choisies parce qu'elles se testent isolément. Le résultat ne dit
  rien du code lié à l'IHM ou au repository globs.
- **Couverture non mesurée** : aucun rapport JaCoCo n'était présent, et il n'a pas été relancé.
  « Jamais testé unitairement » ne veut pas dire « jamais exécuté » : les tests fonctionnels
  (~118 classes) passent par beaucoup de ce code.
- **Liste de défauts non exhaustive**, même dans ces quatre classes : seuls les cas suspects à la
  lecture ont été essayés.
- **Non regardés** : les triggers (56 classes, 5 tests unitaires — budgets, soldes, opérations
  prévues), le reste de l'import (OFX, JSON, `ImportSession`, `TransactionFilter` pour les doublons,
  analyseurs de libellés), la persistance, l'IHM, et les modules `globs`, `saxstack`, `prevayler`.

Chiffres de cadrage : une quarantaine de classes de tests unitaires (hors `functests`), environ
5 400 lignes ; `bv_shared` n'a pas de dossier de tests.

## Défauts constatés par exécution

Tous reproduits le 2026-11-18 sur le code de `master` (JDK 21). Aucun n'est corrigé.

### Import QIF (`QifParser`)

| Fichier QIF | Résultat |
|---|---|
| Ventilation tronquée : ligne `S`, puis fin de fichier | **Boucle infinie.** `SplitedNextTransactionReader.readNext` renvoie `this` sur fin de fichier (`case -1`) |
| Ventilation avec une ligne `%` ou une ligne vide après `$` | **Le reste du fichier est abandonné sans message** (2 opérations lues sur 4) : le lecteur anonyme créé après `$` n'accepte que `^` ou `S` et renvoie `null` sinon |
| Dernière opération sans `^` final | Opération créée sans libellé, sans compte, sans type d'import : `completeDefault` n'est pas appelé sur fin de fichier |
| `S` avant `T` dans une opération | Opération créée sans date bancaire |
| Montant avec signe en fin (`T12,50-`) | `NumberFormatException` (voir `extractAmount`) |

Les ventilations bien formées sont couvertes par les tests fonctionnels (fichiers
`money_export_*.qif`). À noter, comportement voulu : le montant de l'opération mère est diminué de
chaque ventilation (il tombe à 0 quand elles la couvrent entièrement).

### Montants (`Amounts`)

- `extractAmount` lève une `NumberFormatException` sur `"-"`, `"12,50-"`, `"12.50 -"`, `"12-34"`,
  et une `NullPointerException` sur `null`. Or `CsvReader.getTextType` classe `"-"` et `"12,50-"`
  comme `NUMBER`. Appelants : `QifParser`, `OfxImporter`, `CsvType`, `ImportController`,
  `BankPluginService`, `RealAccount`. **Non vérifié** : si l'exception est rattrapée plus haut
  (pas de `catch` correspondant vu dans `ImportSession`, chaîne d'appel non suivie en entier).
- `"(12,50)"` donne +12,50 : la notation comptable du négatif perd son signe.
- `"1e3"` donne 13.
- Jamais testées : `equal`, `max`, `sameSign`, `isSameSign`, `add`, `diff`, `isNearZero` et
  voisines — alors que `BudgetStatTrigger`, `SeriesStatSummaryTrigger`, `PositionTrigger`,
  `PlannedTransactionCreationTrigger` et `TransactionFilter` s'en servent.
- `equal(null, x)`, `max(null, null, …)`, `split(null, n)` et `adjustTotal` avec un élément `null`
  lèvent une `NullPointerException`.
- `sameSign` et `isSameSign` sont deux quasi-doublons qui diffèrent sur zéro
  (`sameSign(0, 1)` vrai, `isSameSign(0, 1)` faux).
- `adjustTotal({33.33, 33.33, 33.34}, 10)` renvoie `3.3339999999999996` en dernier élément :
  pas d'arrondi au centime.

### Dates (`Month`)

Aucun défaut sur les usages normaux (années bissextiles, passage d'année, changement d'heure
essayés). Mais :

- `next(200601, -1)` renvoie `200600` et `previous(200612, -1)` renvoie `200613`, des mois
  invalides. Seul `offset` gère les négatifs.
- `isContinuousSequence` lève `ArrayIndexOutOfBoundsException` sur un tableau vide
  (appelant : `MonthListStringifier`).
- `toDate(202402, 31)` déborde en silence sur le 2 mars.
- Non testés : `getLastDayNumber`, `getDay(jour, mois)`, `toDate`, `getMonthId`, `offset`,
  `isContinuousSequence`, `yearRange`, `toString`, et tous les libellés.

### Lecteur CSV (`CsvReader`)

- `findSeparator` n'a aucun test. Il exige au moins deux occurrences du séparateur : un fichier à
  deux colonnes (`date;amount`) est refusé comme « format invalide ».
- Guillemets doublés du CSV standard mal découpés :
  `a;"dit ""bonjour"" ici";b` donne `[a, dit "bonjour"" ici", b]`, et un `;` placé entre
  guillemets doublés coupe le champ.
- Un espace avant le guillemet ouvrant (`a; "k;F";b`) désactive la protection du séparateur.
- `getTextType` : `1/2/2024` et `01.02.24 10:30` ne sont pas des dates (`TEXT`) ; `20240102`,
  `"-"`, `","` et `1.2.3` sont des nombres ; `99/99/9999` est une date.

## Chantier proposé

À découper en petits lots, comme l'audit. Avant de corriger un comportement, consulter l'aide
utilisateur de `bv_website` (formats d'import acceptés) pour trancher ce qui est voulu.

- [ ] **A. Mesurer** : `mvn -Pcoverage test -pl budgetview/bv_desktop` (~14 min) puis
  `docs/audit/coverage.sh`. Lister les classes de calcul (hors `desktop/`) les moins couvertes en
  branches : c'est cette liste, et non le sondage, qui doit fixer les priorités.
- [ ] **B. Import QIF** : écrire `QifParserTest`, puis corriger la boucle infinie, l'abandon
  silencieux et l'opération finale sans `^`.
- [ ] **C. Montants** : décider du comportement voulu pour le signe en fin, les parenthèses et
  `"-"` ; compléter `AmountsTest` ; trancher entre `sameSign` et `isSameSign`.
- [ ] **D. CSV** : tests de `findSeparator`, guillemets doublés, dates sans zéro initial.
- [ ] **E. Dates** : compléter `MonthTest` (filet de sécurité, pas de défaut connu en usage normal).
- [ ] **F. Sonder ce qui ne l'a pas été**, dans l'ordre : triggers, `TransactionFilter`
  (doublons), import OFX/JSON, migration des données (`model/upgrade`).

## Refaire la sonde

La sonde était un simple `main` Java, non conservé, appelant les méthodes sur des cas limites et
affichant résultat ou exception. Pour la refaire sans passer par Maven, depuis
`budgetview/bv_desktop`, après un build :

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
CP="$PWD/target/classes:$PWD/target/dist/lib/*"     # chemins absolus : en relatif, javac ne trouve rien
$JAVA_HOME/bin/javac -cp "$CP" -d /tmp/probe Probe.java
$JAVA_HOME/bin/java -Djava.awt.headless=true -cp "/tmp/probe:$CP" Probe
```

- `QifParser.read` se nourrit d'un `TypedInputStream(new ByteArrayInputStream(…), "a.qif")` et d'un
  `GlobRepositoryBuilder.createEmpty()` passé deux fois (dépôt initial et dépôt cible).
- Lancer chaque appel du parseur dans un thread démon avec un délai (`Future.get(5, SECONDS)`) :
  sans cela, la boucle infinie bloque la sonde.
