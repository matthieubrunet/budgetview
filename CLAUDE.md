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
- Remote `upstream` → `https://github.com/MarcGuiot/budgetview.git` (dépôt de l'auteur, figé depuis avril 2025).
- **Pas de licence** sur l'application tant que la PR #2 (Apache 2.0) n'est pas acceptée chez l'auteur :
  ne rien redistribuer d'ici là. Droits, démarches et licences des sous-projets : [docs/licence.md](docs/licence.md).
- Historiquement versionné sous Mercurial (`budgetview/howto/livraison.txt`
  parle encore de `hg`), puis migré vers git.

## Modules (réacteur Maven racine `pom.xml`)

| Module | Rôle | Dans le build ? |
|---|---|---|
| `globs/` | GlobsFramework, **version privée et ancienne** du framework de MarcGuiot : modèle de données réactif (`GlobType`, champs statiques annotés `@Key`, `@Target`…), repository, changesets. Contient aussi `splits` (`org.globsframework.gui.splits`), le moteur de mise en page Swing déclaratif des fichiers `.splits`, dont le dossier séparé a été supprimé le 2026-11-20. Contient depuis le 2026-11-20 l'ancien module `saxstack` (parseur SAX à pile, écriture XML, paquets `org.saxstack`) | oui |
| `prevayler/` | Prevayler (persistance par journal de transactions + snapshots), forké | oui |
| `siteweaver/` | Générateur du site web statique | oui |
| `budgetview/bv_shared` | Code commun client/serveur | oui |
| `budgetview/bv_desktop` | **L'application** (~1 100 classes, ~350 tests) | oui |
| `budgetview/bv_server` | Serveur (licences, cloud, mobile ; Jetty, MySQL, Stripe). Porte depuis le 2026-11-20 la couche SQL `sqlstreams` sortie de `globs` | **commenté** |
| `budgetview/bv_analytics` | Analyse de logs | **commenté** |
| `budgetview/bv_website` | Contenu du site (fr/en). **Référence du comportement attendu** : aide utilisateur dans `fr/content/support/` (`faq/`, `guides/`) et journal des versions `support/derniers_changements.html` (5.0 du 2017-10-29 = Sync/Budgea ; 5.01 du 2017-11-12 = jar 156, la version officielle). À consulter avant de trancher entre le code et un test | hors Maven |
| `uispec4j/` | Framework de tests d'IHM Swing (fork) | **commenté** |
| `budgetview-android/` | Appli mobile (Gradle, à part) | hors Maven |
| `functests/` | Ancien projet de tests IntelliJ (fichiers `.ipr` retirés le 2026-11-15) | hors Maven |
| `ssl/` | Ne garde que le README de génération du certificat serveur (voir Points d'attention) | — |

## Application desktop (`budgetview/bv_desktop`)

- Point d'entrée : `com.budgetview.desktop.DesktopApp` → `Application.main`.
  Version courante : `Application.APPLICATION_VERSION = "6.0.1"` (6.00 jusqu'au 2026-11-16), `JAR_VERSION = 156`.
- Variantes utiles au dev : `InMemoryDesktopApp` (données en mémoire),
  `src/test/java/.../desktop/DevDesktopApp.java`.
- Données utilisateur : `~/.budgetview` (Linux/Windows),
  `~/Library/Application Support/BudgetView` (macOS) — voir `desktop/startup/AppPaths.java`.
  Surcharges par propriétés système préfixées `budgetview.` (`budgetview.prevayler.path`,
  `budgetview.prevayler.delete`, date forcée…).
- Utilisateurs et dossiers de données (vérifié le 2026-11-16) :
  - Un dossier peut contenir **plusieurs utilisateurs**, chacun avec son propre jeu de données (identifiant
    distinct, données chiffrées par son mot de passe). On passe de l'un à l'autre par l'écran de connexion
    (« Fichier → Ouvrir un autre compte utilisateur »), sans relancer. Création : y cocher « Créer un nouveau
    compte » ; suppression : « Fichier → Supprimer » une fois connecté. C'est l'usage que décrit la FAQ
    (`faq/comptes/utiliser_plusieurs_comptes.html`, qui cite encore l'ancien nom du menu).
  - L'application n'utilise **qu'un dossier à la fois** : le défaut, ou celui de `redirect.txt`. « Préférences →
    Stockage » en change au lancement suivant (copie de la session, ou reprise des données déjà présentes).
    L'aide le présente pour déplacer les données, pas pour tenir deux jeux en parallèle.
  - Sur ce poste, la séparation dev / réel tient aux dossiers : `run-dev.sh` force le sien (`~/.budgetview-dev`
    ou `BV_DEV_DATA`), l'app empaquetée suit `redirect.txt` (Dropbox). Pour essayer un premier démarrage :
    `BV_DEV_DATA=/tmp/bv-essai ./run-dev.sh` (dossier vide → compte sans mot de passe créé d'office).
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

**JDK de référence : 21** depuis le 2026-11-16. Le réacteur est compilé en `--release 21` : Java 8 ne peut
plus ni le construire ni l'exécuter ([docs/migration-java.md](docs/migration-java.md)). Les profils `jdk17`
(variante `java-jdk17` d'uispec4j, `--add-opens` des tests) s'activent d'office à partir de Java 9.
- uispec4j sous-classe `sun.awt.SunToolkit` : le jar installé dans `~/.m2` n'est utilisable qu'avec le JDK
  qui l'a construit. **Changer de JDK pour les tests = réinstaller uispec4j avec ce JDK** (étape 1), puis
  `clean install` du réacteur. uispec4j reste compilé en `-source 8 -target 8` : `--release` interdirait
  ses accès à `sun.awt`.

Sur ce poste (Mac ARM) :
- `openjdk@21` (Homebrew, 2026-11-16) : `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Maven 3.10 (`brew install maven`).
- Anciens JDK, plus nécessaires au build : Zulu 8 (`~/Library/Java/JavaVirtualMachines/zulu-8.jdk`),
  `openjdk@17` (Homebrew).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# 1. Une fois : installer uispec4j dans ~/.m2 (retiré du réacteur racine,
#    mais requis : même -DskipTests résout les dépendances de test de globs et bv_desktop).
#    Les modules d'exemple addressbook/calculator échouent : sans importance.
mvn -f uispec4j/pom.xml install -DskipTests

# 2. Build complet (~30 s)
#    clean efface target/dist/budgetview.jar, que run-dev.sh utilise : fermer l'appli de dev avant.
#    « Failed to delete …/target » : une fenêtre du Finder ouverte dessus y recrée des .DS_Store ;
#    la fermer, ou rm -rf target puis relancer sans clean.
mvn clean install -DskipTests
#    Doit rester sans aucun [WARNING] : les pom affichent les alertes javac utiles (removal, static, varargs…,
#    choisies en audit 3a). Une alerte qui apparaît se corrige, ou se justifie par un @SuppressWarnings commenté.

# 3. Une fois (et après tout changement du modèle) : générer les comptes de démo
#    src/main/resources/demo/demo-{fr,en}.snapshot. Fichiers non versionnés ; sans eux le
#    bouton « Compte de démo » ne marche pas et les tests du mode démo échouent.
mvn -Pgen-demo install -pl budgetview/bv_desktop -DskipTests

# 4. Livrable dans budgetview/bv_desktop/target/dist : budgetview.jar (classes de bv_desktop, Class-Path
#    vers lib/) + lib/ (18 jars) + config.jar, ~12 Mo. C'est l'entrée de jpackage (voir README.md).
#    Le shrinker qui réduisait le jar à 9 Mo a été supprimé le 2026-11-15 (docs/migration-java.md).
#    Outil de dump : java -cp target/dist/budgetview.jar com.budgetview.persistence.direct.Dump
#    N'embarque les comptes de démo que s'ils existent déjà (étape 3) : sinon, au clic sur
#    « Compte de démo » : Could not find '/demo/demo-fr.snapshot' in classpath.
mvn -Pgen-version clean install -DskipTests
```

Lancer l'application de dev : **`./run-dev.sh`** (`--build` pour reconstruire le jar avant).
Le script fixe le JDK 21, met `-DSINGLE_INSTANCE_DISABLED=true`, `-Dbudgetview.dev=true` et `-ea`, et utilise les données de
`~/.budgetview-dev` (modifiable avec `BV_DEV_DATA`).
- **Mode dev** : `Application.isDevMode()` (propriété `budgetview.dev`, fausse par défaut) affiche le menu `[Dev]`, propose
  1 à 6 mois futurs et met Préférences et Quitter dans le menu Fichier sur Mac. `FunctionalTestCase` l'active : les tests
  s'en servent. Les contrôles de développement de `globs` sont des `assert` (actifs sous surefire et `run-dev.sh`).
  Jusqu'à la 5.01, ce code était retiré du jar par le shrinker (balises `Utils.beginRemove`/`endRemove`, supprimées en
  audit 3d) : ne pas réintroduire de code réservé au dev sans passer par l'un de ces deux moyens. Il refuse les dossiers de données de
l'application officielle, y compris celui désigné par son `redirect.txt`, qui est dans Dropbox.

- Le serveur d'origine `register.budgetview.fr` ne répond plus (constaté le 2026-11-15) :
  vérification de version, licences et cloud sont inopérants. Périmètre retenu : **usage local seulement**,
  tout ce qui contacte un serveur est coupé par `Application.isServerEnabled()` (propriété
  `budgetview.server.enabled`, fausse par défaut). Liste et raisons : [docs/perimetre-local.md](docs/perimetre-local.md).
  Toute nouvelle fonction qui contacte un serveur doit passer par cet interrupteur.

- Le log est écrit dans `<data.path>/logs/log.txt`.
- **Cohabitation avec l'appli officielle** (BudgetView 5.01 installée dans
  `/Applications`, utilisée en parallèle par Matthieu) :
  - Instance unique : chaque instance écoute sur 127.0.0.1, ports 5454, 3474,
    14457 et 9381. Si l'officielle tourne, la version de dev s'y connecte et sort aussitôt avec
    le code 0, sans rien afficher. Toujours lancer la version de dev avec `-DSINGLE_INSTANCE_DISABLED=true`.
    Elle n'écoute alors pas, et l'officielle peut démarrer après elle.
  - Données : **ne jamais** faire pointer la version de dev sur les données réelles
    (`~/Library/Application Support/BudgetView`, ou le dossier désigné par son
    `redirect.txt`). La version de dev est en 6.0.1 : ses triggers d'upgrade migreraient le
    format, peut-être de façon illisible pour la 5.01. Toujours passer `-Dbudgetview.data.path`.
  - Autorisations macOS : la version de dev hérite de celles de l'application qui la lance (VS Code
    ou Terminal), alors que la 5.01 a les siennes. Sans accès à Dropbox (`~/Library/CloudStorage`),
    Java reçoit `null` et le dialogue de fichiers affiche une liste vide, sans message ; `ls` répond
    `Operation not permitted`. VS Code a reçu cet accès le 2026-11-15.
- Diagnostic de l'IHM depuis Claude Code :
  - `screencapture -l <id>` capture une fenêtre Java même cachée. L'identifiant s'obtient par
    `CGWindowListCopyWindowInfo` (petit script Swift). Il faut l'autorisation d'enregistrement de l'écran.
  - macOS empêche de passer l'appli Java au premier plan depuis un script (ni `AXRaise`, ni
    `NSRunningApplication.activate`) : un vrai clic doit être fait par Matthieu.
  - L'application redirige `System.out` vers son log : un harnais lancé dans sa JVM y écrit aussi.
  - Une fenêtre ne se dessine dans `screencapture -l` qu'après un premier clic de Matthieu dedans : avant, capture blanche.
  - Ouvrir un fichier dans l'appli qui tourne (`OpenRequestManager.openFiles`) : ni l'icône Java de `run-dev.sh` ni
    `BudgetView 6.app` (sans association de fichiers) n'acceptent un fichier du Finder. Lancer le jar sans
    `-DSINGLE_INSTANCE_DISABLED`, ports libres et BudgetView 6 fermée, puis un second `java -jar … fichier.ofx` avec les
    mêmes propriétés : l'instance unique transmet le fichier à la première (vérifié en audit 4c).
- Consulter les données d'un dossier (`<data.path>/data`) sans passer par l'IHM (vérifié le 2026-11-18) :
  - Toujours travailler sur une **copie** (`cp -R`) : l'outil ci-dessous ouvre Prevayler en écriture, et
    l'appli qui tourne écrit dans les mêmes journaux.
  - Disposition : `users/` (liste des utilisateurs) et un dossier par utilisateur, nommé par son identifiant
    numérique, avec des paires `N.snapshot` / `N.journal`. Le journal le plus récemment modifié désigne le
    compte où l'on vient de travailler.
  - Utilisateurs : `java -cp target/dist/budgetview.jar com.budgetview.persistence.direct.Dump <copie>/data`
    donne noms et `autoLog`. Le compte sans mot de passe s'appelle `autologUser`, mot de passe identique au nom.
    Le lien nom → identifiant de dossier est chiffré : le déduire des dates de création des dossiers.
  - Contenu : chiffré par le mot de passe de l'utilisateur, illisible sans lui. Seuls les **noms de types
    globs** sont en clair dans journaux et snapshots :
    `LC_ALL=C grep -a -o -E "[a-zA-Z]{4,30}" N.journal | sort | uniq -c` montre quels types ont été écrits
    (ex. `accountPositionError` après un import dont le solde diffère). Le décodeur de journal de `Dump`
    (`dumpJournal`, code mort) ne correspond pas au format de ces fichiers.
  - Pour voir des valeurs : capture de la fenêtre (ci-dessus), ou demander le mot de passe du compte de test.
  - Non-régression du format après un changement de la persistance (`prevayler`, `persistence/`) : copier
    `~/BudgetView-sauvegardes/5.01-avant-6.00-2026-11-16/dropbox-BudgetView` (retirer `data/app.lock`), lancer `Dump`
    dessus (il ouvre `users/` par `PRootDataManager`, donc relit snapshot et journal par Prevayler), vérifier qu'il
    retrouve l'utilisateur, puis `diff -rq` entre le `users/` d'origine et celui de la copie (fait en audit 2c).
  - Le jar de la 5.01 (`/Applications/BudgetView.app/Contents/Resources/app/budgetview_bundle.jar`) est
    obfusqué, avec des noms qui ne diffèrent que par la casse : ne pas l'extraire sur le disque du Mac,
    qui ignore la casse. Le lire avec `zipfile` (Python).
- Cible Java **21** depuis le 2026-11-16 (l'amont s'était arrêté en 1.8). Migration, étapes et
  mesures : [docs/migration-java.md](docs/migration-java.md).
- Packaging natif via `jpackage` (commandes dans `README.md`), fourni par le JDK 21.
  Sur Mac : `./package-mac.sh` → `~/Applications/BudgetView 6.app` (~160 Mo, runtime Java compris, signature
  ad hoc). Contrairement à `run-dev.sh`, l'app empaquetée garde l'instance unique et **les données par défaut,
  donc celles de la 5.01** : Matthieu l'a choisie le 2026-11-16 pour l'usage quotidien à la place de la 5.01
  (sauvegarde préalable des données dans `~/BudgetView-sauvegardes/5.01-avant-6.00-2026-11-16`).
  Pour essayer le paquet sans toucher à ces données : `JAVA_TOOL_OPTIONS="-Dbudgetview.data.path=<dossier>
  -DSINGLE_INSTANCE_DISABLED=true" "~/Applications/BudgetView 6.app/Contents/MacOS/BudgetView 6"`.
- Tests : JUnit 4, surefire inclut `**/*Test.java` et `**/*Tests.java`.
  Les tests fonctionnels (`src/test/java/com/budgetview/functests`, classes de base
  `FunctionalTestCase` → `LoggedInFunctionalTestCase`) pilotent l'IHM via uispec4j.

## Tests de bv_desktop

```sh
caffeinate -i mvn test -pl budgetview/bv_desktop       # ~5 min, 1 026 tests, 162 classes, 3 JVM (2026-11-21)
mvn test -pl budgetview/bv_desktop -Dtest=LoginTest    # une classe (liste séparée par des virgules)
mvn -Pcoverage test -pl budgetview/bv_desktop && docs/audit/coverage.sh   # couverture JaCoCo → target/coverage/
```

Tri des échecs (corrigés, désactivés, restants) : [docs/etat-des-tests.md](docs/etat-des-tests.md).
Pour l'état actuel de la suite, la relancer : les chiffres changent à chaque correction.
Désactiver un test : JUnit 3, donc renommer la méthode `disabled_test…` avec un commentaire
(`@Ignore` est sans effet) ; exclure la classe dans le `pom` si plus aucun test n'y reste.
Réglages du `pom.xml` (surefire) et leurs raisons :
- `-Xmx1024m` : à 256 Mo, les tests s'enlisaient en `OutOfMemoryError`.
- `reuseForks=false` : une JVM par classe. Les tests fonctionnels laissent fuir
  fenêtres et threads (700+) : en JVM partagée, l'`OutOfMemoryError` arrivait vers la 80e classe
  et la JVM mourait (« forked VM terminated without saying goodbye »), emportant la suite.
- `forkCount=${test.forks}`, 3 par défaut : trois de ces JVM en même temps (audit 5c, mesures dans le `pom`).
  À 4, la machine sature et uispec4j échoue par délai dépassé. `-Dtest.forks=1` pour revenir en série.
- `workingDirectory` = `target/forks/<n>` : chaque JVM a son `tmp/`, qu'elle vide au démarrage
  (`FunctionalTestCase`). Sans cela, des JVM parallèles s'effaceraient leurs fichiers.
- **`caffeinate -i`** devant `mvn test` : sur batterie, le Mac se met en veille quand personne ne touche le
  clavier, et la JVM gelée fait « durer » la classe en cours le temps de la sieste (de 80 s à 8 min relevés,
  d'où des suites de 9 à 21 min). Capot fermé, la veille a lieu malgré `caffeinate` : le laisser ouvert.
- `-Dapple.awt.UIElement=true` : sans cela, chaque JVM de test passe au premier plan sur macOS
  (icône Dock, vol du focus toutes les 2 s) et certaines fenêtres (« choix du compte ») s'affichent.

Adaptations des tests faites pour la 6.00 :
- Licence : la 6.00 active toutes les extensions et supprime le menu « Activer les extensions… »
  (`Register` en anglais). `LicenseChecker.register()` ne fait plus rien ; ne plus appeler
  `LicenseActivationChecker.enterLicense`.
- Langue : la 6.00 force le français au démarrage. `Application.LANG_PROPERTY`
  (`budgetview.lang` = `fr` par défaut, `en`, ou `keep`) ; `FunctionalTestCase` met `keep` pour
  que les tests restent dans la langue qu'ils fixent (anglais, `Lang.ROOT`).
- Sorties de l'application : toujours `AppExit.exit(code)`, jamais `System.exit`. `FunctionalTestCase` le
  remplace par une exception, pour que la JVM de surefire survive (ex. `ShowDialogAndExitExceptionHandler`).
  Il remplace le `SecurityManager`, désactivé en Java 24 ([docs/migration-java.md](docs/migration-java.md)).
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
  `-DtrimStackTrace=false`. Les tests écrivent dans `target/forks/<n>/tmp/`.
- La commande `timeout` n'existe pas sur ce Mac (pas de coreutils). Une commande
  `timeout … mvn test | grep …` échoue sans message et sort avec le code 0 : on croit les tests passés
  alors qu'ils n'ont pas tourné. Pour limiter la durée, utiliser le délai de l'outil qui lance la commande,
  et vérifier la ligne `Tests run:` finale.

Tests de `globs` : `mvn -o clean test -pl globs` (~1 min ; le `pom` fixe JVM par classe, limite de temps et `--add-opens`),
813 verts au 2026-11-20, `saxstack` compris (détail dans [docs/etat-des-tests.md](docs/etat-des-tests.md)). `clean` après un changement de signature (le vieux
compilateur ne recompile pas les tests non touchés) ; `mvn -o install -pl globs -DskipTests` avant de tester bv_desktop,
qui prend globs dans `~/.m2`.

Modules hors build (`bv_server`, `bv_analytics`) : après `mvn install` du réacteur, `mvn -f budgetview/bv_server/pom.xml compile`
(la première fois en ligne, pour télécharger ses dépendances) vérifie le `main`. Ses tests ne compilent plus en bloc
(`functests` de licence, qui dépendent de checkers supprimés de bv_desktop). Pour vérifier des tests précis :
classpath par `mvn -o dependency:build-classpath -Dmdep.outputFile=<fichier>`, `javac -d <dossier>` sur ces seuls
fichiers, puis `java -cp … org.junit.runner.JUnitCore <classes>`. JUnitCore compte comme échec (« Cannot instantiate
test case ») une classe de test abstraite que surefire ignorerait.
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
- Simplification du code ancien (code mort, frameworks forkés, ce que Java 21 fournit, contournements,
  tests) : plan découpé en petits chantiers à cocher dans [docs/audit-code.md](docs/audit-code.md).
  Y cocher chaque chantier terminé et en noter les chiffres dans son journal.
- Pistes d'amélioration relevées à l'usage (ergonomie, fonctions) : les noter dans
  [docs/idees-ameliorations.md](docs/idees-ameliorations.md), sans les traiter avant la fin de l'audit.

## Points d'attention

- Les clés privées du serveur (`ssl/bv.key`, `bv.jks`, `bv.keystore`) ont été retirées de l'arbre
  le 2026-11-15 mais restent dans l'historique git, public : elles sont compromises, ne jamais les
  réutiliser. Le `.gitignore` bloque désormais `*.key`, `*.jks`, `*.keystore`, `*.p12`, `*.pem`.
- La clé privée DSA qui signait les licences est écrite en dur dans
  `bv_server/.../license/generator/LicenseGenerator.java`, public : n'importe qui peut fabriquer une licence
  valide pour la 5.01. Le système de licence a été supprimé de l'application en 6.0.1 (audit 1a, lot 5) ;
  une future offre payante passerait par un compte et un abonnement vérifiés côté serveur, pas par une licence.
- Les `howto/` (livraison, serveur) décrivent l'infrastructure d'origine
  (IP, comptes SSH) : documentation historique, pas à rejouer telle quelle.
- Fichiers d'IDE (IntelliJ `.ipr`/`.iws`/`.iml`, Eclipse `.project`/`.classpath`/`.settings/`,
  `bin/`, `.vscode/`) : retirés et ignorés depuis le 2026-11-15, le `pom.xml` fait foi. L'extension
  Java de VS Code (Red Hat), qui les créait, a été désinstallée ; s'ils réapparaissent, les supprimer.
