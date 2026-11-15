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
| `budgetview/bv_website` | Contenu du site (fr/en). **Référence du comportement attendu** : aide utilisateur dans `fr/content/support/` (`faq/`, `guides/`) et journal des versions `support/derniers_changements.html` (5.0 du 2017-10-29 = Sync/Budgea ; 5.01 du 2017-11-12 = jar 156, la version officielle). À consulter avant de trancher entre le code et un test | hors Maven |
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

**Il faut un JDK 8.** uispec4j (dont tout le build dépend, voir plus bas)
sous-classe `sun.awt.SunToolkit` et des classes AWT internes de Java 8 :
il ne compile pas sous Java 17, et les tests d'IHM en ont besoin à l'exécution.

Sur ce poste (Mac ARM), installé le 2026-11-15 :
- JDK 8 : Zulu 8 (archive Azul, sans sudo) dans `~/Library/Java/JavaVirtualMachines/zulu-8.jdk`.
  Les casks Homebrew de JDK exigent sudo ; Temurin n'a pas de JDK 8 pour aarch64.
- Maven 3.10 (`brew install maven`) — fonctionne sous Java 8.
- `openjdk@17` (Homebrew) aussi présent, inutile tant qu'on reste en Java 8.

```sh
export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home

# 1. Une fois : installer uispec4j dans ~/.m2 (retiré du réacteur racine,
#    mais requis : même -DskipTests résout les dépendances de test de globs et bv_desktop).
#    Les modules d'exemple addressbook/calculator échouent : sans importance.
mvn -f uispec4j/pom.xml install -DskipTests

# 2. Build complet (~30 s)
mvn clean install -DskipTests

# 3. Une fois (et après tout changement du modèle) : générer les comptes de démo
#    src/main/resources/demo/demo-{fr,en}.snapshot. Fichiers non versionnés ; sans eux le
#    bouton « Compte de démo » ne marche pas et les tests du mode démo échouent.
mvn -Pgen-demo install -pl budgetview/bv_desktop -DskipTests

# 4. Jar livrable : budgetview/bv_desktop/budgetview.jar (~9 Mo, shrinké) + config.jar
mvn -Pgen-version clean install -DskipTests
```

Lancer l'application de dev : **`./run-dev.sh`** (`--build` pour reconstruire le jar avant).
Le script fixe le JDK 8, met `-DSINGLE_INSTANCE_DISABLED=true` et utilise les données de
`~/.budgetview-dev` (modifiable avec `BV_DEV_DATA`). Il refuse les dossiers de données de
l'application officielle, y compris celui désigné par son `redirect.txt`, qui est dans Dropbox.

- Le serveur d'origine `register.budgetview.fr` ne répond plus (constaté le 2026-11-15) :
  vérification de version, licences et cloud sont inopérants. L'appli démarre quand même.

- Le log est écrit dans `<data.path>/logs/log.txt`.
- **Cohabitation avec l'appli officielle** (BudgetView 5.01 installée dans
  `/Applications`, utilisée en parallèle par Matthieu) :
  - Instance unique : chaque instance écoute sur 127.0.0.1, ports 5454, 3474,
    14457 et 9381. Si l'officielle tourne, la version de dev s'y connecte et sort aussitôt avec
    le code 0, sans rien afficher. Toujours lancer la version de dev avec `-DSINGLE_INSTANCE_DISABLED=true`.
    Elle n'écoute alors pas, et l'officielle peut démarrer après elle.
  - Données : **ne jamais** faire pointer la version de dev sur les données réelles
    (`~/Library/Application Support/BudgetView`, ou le dossier désigné par son
    `redirect.txt`). La version de dev est en 6.00 : ses triggers d'upgrade migreraient le
    format, peut-être de façon illisible pour la 5.01. Toujours passer `-Dbudgetview.data.path`.
- Cible Java **1.8** (dernier commit amont : passage en 1.8, désactivation
  du look & feel Mac qui ne compilait plus faute de `apple.laf.AquaLookAndFeel`).
- Packaging natif via `jpackage` (commandes dans `README.md`) — `jpackage`
  n'existe qu'à partir de Java 14 : il faudra un second JDK pour cette étape.
- Tests : JUnit 4, surefire inclut `**/*Test.java` et `**/*Tests.java`.
  Les tests fonctionnels (`src/test/java/com/budgetview/functests`, classes de base
  `FunctionalTestCase` → `LoggedInFunctionalTestCase`) pilotent l'IHM via uispec4j.

## Tests de bv_desktop

```sh
mvn test -pl budgetview/bv_desktop                     # ~15 min, 1 049 tests, 166 classes
mvn test -pl budgetview/bv_desktop -Dtest=LoginTest    # une classe (liste séparée par des virgules)
```

Tri des échecs (corrigés, désactivés, restants) : [docs/etat-des-tests.md](docs/etat-des-tests.md).
Pour l'état actuel de la suite, la relancer : les chiffres changent à chaque correction.
Désactiver un test : JUnit 3, donc renommer la méthode `disabled_test…` avec un commentaire
(`@Ignore` est sans effet) ; exclure la classe dans le `pom` si plus aucun test n'y reste.
Réglages du `pom.xml` (surefire) et leurs raisons :
- `-Xmx1024m` : à 256 Mo, les tests s'enlisaient en `OutOfMemoryError`.
- `forkCount=1` + `reuseForks=false` : une JVM par classe. Les tests fonctionnels laissent fuir
  fenêtres et threads (700+) : en JVM partagée, l'`OutOfMemoryError` arrivait vers la 80e classe
  et la JVM mourait (« forked VM terminated without saying goodbye »), emportant la suite.
- `-Dapple.awt.UIElement=true` : sans cela, chaque JVM de test passe au premier plan sur macOS
  (icône Dock, vol du focus toutes les 2 s) et certaines fenêtres (« choix du compte ») s'affichent.

Adaptations des tests faites pour la 6.00 :
- Licence : la 6.00 active toutes les extensions et supprime le menu « Activer les extensions… »
  (`Register` en anglais). `LicenseChecker.register()` ne fait plus rien ; ne plus appeler
  `LicenseActivationChecker.enterLicense`.
- Langue : la 6.00 force le français au démarrage. `Application.LANG_PROPERTY`
  (`budgetview.lang` = `fr` par défaut, `en`, ou `keep`) ; `FunctionalTestCase` met `keep` pour
  que les tests restent dans la langue qu'ils fixent (anglais, `Lang.ROOT`).
- `FunctionalTestCase` installe un `SecurityManager` qui transforme en exception tout
  `Runtime.exit()` venant de `com.budgetview.desktop` (ex. `ShowDialogAndExitExceptionHandler`).
  Il ne doit pas bloquer les simples `checkExit()` de Swing (`setDefaultCloseOperation(EXIT_ON_CLOSE)`),
  sinon plus aucune fenêtre principale ne se crée.
- `LoggedInFunctionalTestCase.setUp` referme la fenêtre partagée (`static mainWindow`) s'il échoue :
  sinon tous les tests suivants de la classe échouent en cascade (« textBox 'name' not found »).
- Fenêtre partagée, suite : un test qui passe seul mais échoue dans sa classe vient presque toujours
  d'un réglage d'IHM laissé modifié par un test précédent (ex. filtrage par mois de la vue Budget).
  Tout test qui change un tel réglage doit le rétablir avant de finir.
- Un test qui se déconnecte puis se reconnecte doit travailler sur disque (`setInMemory(false)` dans
  son `setUp`, voir `PreferencesTest`) : la déconnexion recrée la session (`MainWindow.initServerAccess`),
  ce qui vide une base en mémoire.
- Ne jamais lancer deux `mvn test` en même temps : ils écrivent dans le même `target/`. Pendant une suite
  complète, lire ou éditer les sources reste possible (les classes sont déjà compilées).
- Contrôle des données en échec : `OperationChecker.checkDataIsOk` affiche le détail des erreurs trouvées.
- Test unitaire sur un repository globs construit à la main : y charger les constantes et les `MonthDay`
  comme `PicsouInit` (`GlobRepositoryBuilder.init(…).add(DesktopModel.get().getConstants())`, puis
  `PicsouInit.createTransientDataForNewUser`), sinon le contrôle des liens (`GlobCheck.linksAreAllConnected`)
  signale de faux liens cassés (voir `DataCheckingServiceTest`).
- Pour diagnostiquer : rapports dans `target/surefire-reports/*.txt`, pile complète avec
  `-DtrimStackTrace=false`. Les tests écrivent dans `tmp/` (ignoré par git).

Tests de `globs` : ~40 échecs (GlobPrinterTest, SQL) et blocage infini sur HSQLDB dans
`HsqlExceptionTest`. Toujours poser une limite de temps quand on les lance.
- Le dépôt `http://download.java.net/maven/2/` déclaré dans `bv_desktop/pom.xml`
  est mort mais ne gêne pas : tout se résout depuis Maven Central.

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
- L'extension Java de VS Code (Red Hat) écrit dans le dépôt : `bin/` (classes compilées),
  `.settings/`, modifications de `.project`. Ni le build ni les tests ne s'en servent ; elle a été
  désinstallée le 2026-11-15. Si ces fichiers réapparaissent, ce n'est pas du travail en cours :
  restaurer les fichiers versionnés (`git checkout`) et supprimer les autres.
