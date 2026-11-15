# BudgetView — notes pour Claude

Logiciel de gestion de finances personnelles, client lourd Java/Swing (code
derrière budgetview.fr). Ce dépôt est le fork `matthieubrunet/budgetview`
du projet de MarcGuiot, rendu open source en avril 2025 après ~17 ans de
développement privé (premier commit 2008, ~3 900 commits). L'ancien nom
de code du projet est **Picsou** : on le retrouve dans les noms de classes
(`PicsouModel`, `PicsouInit`, `org.designup.picsou`).

## Dépôt

- Remote : `budgetview` (pas `origin`) → `https://github.com/matthieubrunet/budgetview.git`.
- Branche : `master` (celle du fork, suivie depuis `budgetview/master`).
- Historiquement versionné sous Mercurial (`budgetview/howto/livraison.txt`
  parle encore de `hg`), puis migré vers git.

## Modules (réacteur Maven racine `pom.xml`)

| Module | Rôle | Dans le build ? |
|---|---|---|
| `globs/` | GlobsFramework, **version privée et ancienne** du framework de MarcGuiot : modèle de données réactif (`GlobType`, champs statiques annotés `@Key`, `@Target`…), repository, changesets | oui |
| `splits/` | Moteur de mise en page Swing déclaratif (fichiers `.splits` en XML) — son code est dans `globs` | via globs |
| `saxstack/` | Parsing XML SAX | oui |
| `prevayler/` | Prevayler (persistance par journal de transactions + snapshots), forké | oui |
| `shrinker/` | `JarShrinker` : réduit le jar livré à partir de listes de classes | oui |
| `siteweaver/` | Générateur du site web statique | oui |
| `budgetview/bv_shared` | Code commun client/serveur | oui |
| `budgetview/bv_desktop` | **L'application** (~1 100 classes, ~350 tests) | oui |
| `budgetview/bv_server` | Serveur (licences, cloud, mobile ; Jetty, MySQL, Stripe) | **commenté** |
| `budgetview/bv_analytics` | Analyse de logs | **commenté** |
| `budgetview/bv_website` | Contenu du site (fr/en) | hors Maven |
| `uispec4j/` | Framework de tests d'IHM Swing (fork) | **commenté** |
| `budgetview-android/` | Appli mobile (Gradle, à part) | hors Maven |
| `functests/` | Ancien projet de tests IntelliJ (`.ipr`) | hors Maven |
| `ssl/` | Certificats **et clés privées** du serveur (voir Points d'attention) | — |

## Application desktop (`budgetview/bv_desktop`)

- Point d'entrée : `com.budgetview.desktop.DesktopApp` → `Application.main`.
  Version courante : `Application.APPLICATION_VERSION = "6.00"`, `JAR_VERSION = 156`.
- Variantes utiles au dev : `InMemoryDesktopApp` (données en mémoire),
  `src/test/java/.../desktop/DevDesktopApp.java`.
- Données utilisateur : `~/.budgetview` (Linux/Windows),
  `~/Library/Application Support/BudgetView` (macOS) — voir `desktop/startup/AppPaths.java`.
  Surcharges par propriétés système préfixées `budgetview.` (`budgetview.prevayler.path`,
  `budgetview.prevayler.delete`, date forcée…).
- Paquets principaux sous `com.budgetview` :
  - `model/` : les `GlobType` métier (Account, Transaction, Series, SeriesBudget,
    Project, Month…) ; `model/upgrade` pour la migration des données.
  - `desktop/` : l'IHM, un paquet par écran/fonction (budget, categorization,
    accounts, projects, analysis, dashboard…).
  - `triggers/` : logique réactive recalculée sur changement du repository globs.
  - `io/importer` (OFX, QIF, CSV, JSON) et `io/exporter`.
  - `persistence/` : Prevayler + `direct/` (dont `Dump`, outil de dump de sauvegarde).
  - `bank/`, `budgea/`, `io/budgea` : synchro bancaire via l'agrégateur Budgea.
  - `client/`, `mobile/`, `session/` : dialogue avec le serveur.
- Ressources (`src/main/resources`) : écrans en `layout/**/*.splits`,
  textes en `i18n/lang_fr.properties` / `lang_en.properties`,
  définitions de banques par pays dans `banks/`, thèmes de couleurs dans `colors/`.

## Build et tests

```sh
mvn -Pgen-version clean install -DskipTests=true   # produit budgetview.jar (shrinké) + dump.jar + config.jar
```

- Cible Java **1.8** (dernier commit amont : passage en 1.8, désactivation
  du look & feel Mac qui ne compilait plus faute de `apple.laf.AquaLookAndFeel`).
- Packaging natif via `jpackage` (commandes dans `README.md`).
- Tests : JUnit 4, surefire inclut `**/*Test.java` et `**/*Tests.java`.
  Les tests fonctionnels (`src/test/java/com/budgetview/functests`, classes de base
  `LoggedInFunctionalTestCase`…) pilotent l'IHM via **uispec4j 2.3-SNAPSHOT** :
  ce module étant retiré du réacteur, il faut l'installer à part
  (`mvn -f uispec4j/pom.xml install`) avant de lancer les tests — hypothèse non vérifiée.
- Le `pom` de `bv_desktop` déclare un dépôt `http://download.java.net/maven/2/`
  (HTTP, mort) : Maven récent le bloquera ; à retirer si le build échoue dessus.
- Sur ce poste, au 2026-11-15 : **ni JDK ni Maven installés** — aucun build n'a encore été tenté.

## Conventions repérées

- Modèle globs : chaque type métier est une classe à champs `public static`
  (`TYPE`, `ID`, …) initialisés par réflexion ; ne pas les instancier.
- IHM décrite en `.splits` et liée au code par nom de composant ; tout texte
  visible passe par les fichiers `lang_*.properties` (fr et en à tenir à jour ensemble).
- Checklist d'une nouvelle fonctionnalité : `budgetview/howto/checklist_nouvelle_feature.txt`
  (upgrade des données utilisateur, couleurs pour tous les thèmes, traduction EN, focus…).
- Annotation `@NoObfuscation` : vestige de l'obfuscation Zelix (ZKM), désormais commentée.

## Points d'attention

- `ssl/` contient `bv.key`, `bv.jks`, `bv.keystore` : clés privées versionnées
  publiquement — à considérer comme compromises, ne pas les réutiliser.
- Les `howto/` (livraison, serveur) décrivent l'infrastructure d'origine
  (IP, comptes SSH) : documentation historique, pas à rejouer telle quelle.
- Fichiers IntelliJ anciens (`.ipr`, `.iws`, `.iml`) versionnés.
