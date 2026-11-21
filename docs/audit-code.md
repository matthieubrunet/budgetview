# Audit du code : simplifier un projet de 17 ans

Le code date de 2008 et a été écrit pour Java 5 à 8. Il contient probablement des
fonctionnalités qui ne servent plus, des contournements de bugs disparus depuis, des tests
devenus inutiles et du code maison que Java 21 fournit maintenant. Ce document découpe
l'audit en **chantiers courts et indépendants**. On coche au fur et à mesure et on note les
chiffres relevés.

Point de départ, au 2026-11-16 (nombre de fichiers dans `bv_desktop`, `bv_shared`, `globs`) :

| Marqueur | Fichiers |
|---|---|
| `workaround`, `contournement`, `hack` | 0 |
| `TODO`, `FIXME`, `XXX` | 11 |
| `Date`, `SimpleDateFormat`, `Calendar.getInstance` | 35 |
| `Vector`, `Hashtable`, `StringBuffer` | 5 |
| `com.apple`, `eawt` | 2 |

Le code ne signale presque jamais ses contournements : chercher des mots-clés ne suffit pas.
Il faut mesurer (étapes 1 et 2), puis interroger l'historique git (étape 4).

## Règles de conduite

- **Une suppression ou un remplacement par commit**, avec la suite de tests verte avant et après.
- Relever à chaque chantier : nombre de classes, taille de `target/dist`, durée des tests.
- **Comportement attendu** : l'aide de `bv_website/fr/content/support/` et le journal
  `derniers_changements.html`. Une fonction absente des deux est un indice d'abandon, pas une preuve.
- **Données utilisateur** : ne jamais supprimer un `GlobType` ni un champ du modèle sans passer
  par `model/upgrade`. Les sauvegardes existantes doivent continuer à se charger, y compris
  celles venues de la 5.01, qui servent désormais avec la 6.00.
- Les suppressions qui risquent de toucher l'IHM se vérifient aussi dans l'appli (`./run-dev.sh`),
  pas seulement par les tests.
- **Tests unitaires** (décidé le 2026-11-18, raisons dans [tests-unitaires.md](tests-unitaires.md)) :
  ne pas en ajouter pendant l'étape 1, ils feraient passer du code mort pour vivant dans la mesure
  JaCoCo. Aux étapes 2 à 4, avant de réécrire une classe à comportement constant, écrire d'abord
  les tests qui figent son comportement actuel, défauts compris. Les corrections de comportement
  attendent le chantier des tests unitaires, après l'audit.
- **Lancer les tests propres d'un module avant d'y toucher** (`mvn test -pl <module>`). Le réacteur se construit en
  `-DskipTests` et les suites lancées d'habitude sont celles de `bv_desktop` et de `globs` : ceux des autres modules
  peuvent n'avoir jamais tourné (cas de `saxstack` en 2b, dont il manquait jusqu'au fournisseur JUnit 3 de surefire).
- **Un membre que ProGuard dit inutilisé peut avoir été inliné** dans son appelant (`XmlUtils.addInCDataValue` en 2b) :
  la compilation tranche, pas le seul rapport.
- **ProGuard ne voit pas le code mort par configuration** : ce qui n'est atteint que par un réglage que l'application
  ne fait jamais (réplication, censeur strict et `RWLock` de `prevayler` en 2c). Le trouver en lisant les fabriques avec
  les réglages réels de l'application, puis le confirmer à 0 % par JaCoCo.
- **Le commit d'un lot est ce qui a été testé** : ne pas préparer le lot suivant dans l'arbre de travail pendant que la
  suite du lot en cours tourne (en 2c, il a fallu mettre le lot C de côté et reconstruire l'état du lot B avant de le
  commiter). Attendre, ou préparer dans une copie hors de l'arbre.
- **Enveloppes et ternaires** : dans `c ? unDouble : 0.00`, le `double` nu fait déballer l'autre branche, qui lève
  alors une `NullPointerException` si elle vaut `null` (vu en 3a, lot A, sur `new Double(0.00)`). En remplaçant un
  constructeur `Integer`, `Long`, `Double`… dans un ternaire, garder un objet : `Double.valueOf(0.00)`. Vaut pour tout
  remplacement de l'étape 3 (OpenRewrite compris) : relire chaque ternaire qui mélange les deux.
- **Une recette automatique ne voit pas les méthodes maison homonymes du JDK** : `GlobList` (une `ArrayList`) a ses
  propres `getFirst()`/`getLast()`, qui rendent `null` sur liste vide ; OpenRewrite les a traitées comme celles de Java 21
  et a rendu `getFirst()` récursive (3b). Une recette se juge en appliquant son patch dans une copie et en y lançant les
  suites de tests, pas sur la seule relecture du diff.
- **Fins de ligne** : 48 fichiers source suivis sont en CRLF (aucun `.gitattributes`). Un script Python qui les réécrit
  avec `open(p)` les convertit en LF sans le dire (`TransactionView`, 737 lignes « modifiées » en 3d, lot A). Lire et écrire
  avec `newline=''`, et contrôler `git diff --stat` avant chaque commit.

## Étape 1 : le code mort, mesuré

Faire l'inventaire sans rien modifier, puis supprimer.

- [x] **1a. Code derrière `Application.isServerEnabled()`** : licences, cloud, mobile,
  vérification de version ([perimetre-local.md](perimetre-local.md)). Il est mort par
  construction, puisque le serveur ne répond plus. Lister les classes qui ne servent que là
  (`client/`, `mobile/`, `session/`…). C'est le plus gros gisement et le moins risqué.
  Inventaire fait le 2026-11-16 (script [audit/reach.py](audit/reach.py)), voir [1a, inventaire](#1a-inventaire) plus bas.
  Lots 1 à 6 supprimés le 2026-11-16.
- [x] **1b. Synchro bancaire Budgea** (`budgea/`, `bank/`, `io/budgea`) : vérifier si elle
  marche encore sans le serveur. Sinon, décider de la couper, voire de la supprimer.
  Ne peut pas marcher : jeton et relevés passaient par `register.budgetview.fr`, muet, et Budgea est arrêté
  depuis 2018.
  Décision de Matthieu (2026-11-16) : **désactivée, code gardé**, car un agrégateur pourra peut-être resservir.
  Toutes les méthodes réseau de `CloudService` passent désormais par l'interrupteur (`isDisabled`), et les
  deux actions cloud du menu de dev n'apparaissent qu'avec lui ([perimetre-local.md](perimetre-local.md)).
  `bank/` n'en fait pas partie : ce sont les plugins de banque de l'import de fichiers (`BankPluginService`).
- [x] **1c. Couverture JaCoCo de la suite fonctionnelle** : ajouter un profil Maven, lancer
  `mvn test`, lister les classes à 0 %.
  Fait le 2026-11-16 (profil `coverage`, script [audit/coverage.sh](audit/coverage.sh)), voir
  [1c, résultats](#1c-résultats) plus bas.
  Les 17 classes candidates sont supprimées (2026-11-16).
- [x] **1d. Couvrir ou supprimer le code jamais exécuté** : pour chaque classe accessible depuis
  `DesktopApp` mais à 0 % en 1c, retrouver la fonction visible qui s'en sert et la chercher dans l'aide.
  Documentée : écrire le test fonctionnel qui manque. Ni documentée ni atteignable : candidate à la
  suppression, sur arbitrage de Matthieu. Inventaire d'abord (tableau fonction / documentée / décision),
  tests ensuite.
  Remplace le projet initial (agent JaCoCo pendant l'usage réel, 2026-11-16) : l'usage de Matthieu ne couvre
  qu'une petite partie des fonctions, une classe non exécutée n'y prouverait rien, alors qu'un test protège
  aussi les étapes suivantes.
  **Hors champ** (décision de Matthieu, 2026-11-16) : le code propre à Windows et à Linux, qui ne tourne
  pas sur le Mac. Ces cibles seront reprises si un jour elles deviennent nécessaires. Le cloud et Budgea
  restent gardés sans test (1b).
  Fait le 2026-11-16, voir [1d, inventaire](#1d-inventaire) plus bas : 24 classes supprimées, le dessin du
  graphe journalier et de l'impression désormais exécuté par les tests. Les 59 classes encore à 0 % sont
  toutes dans les groupes gardés.
- [x] **1e. Code inaccessible depuis `DesktopApp`** : ProGuard en `-dontobfuscate -printusage`
  (ou un outil équivalent), à recouper avec 1c et 1d. Attention aux classes chargées par
  réflexion (globs, splits, i18n), que ces outils signalent à tort comme inutilisées.
  Inventaire fait le 2026-11-17 (ProGuard 7.6.1, script [audit/usage.sh](audit/usage.sh)), voir
  [1e, inventaire](#1e-inventaire) plus bas. Les six lots supprimés le 2026-11-18 : 12 classes de premier niveau
  et ~1 150 lignes de Java en moins, 1 026 tests verts.
- [x] **1f. Ressources orphelines** : fichiers `.splits`, clés `lang_*.properties`, images,
  définitions de `banks/` qui ne sont plus référencés.
  Inventaire fait le 2026-11-18 (script [audit/resources.py](audit/resources.py)), voir
  [1f, inventaire](#1f-inventaire) plus bas. Les quatre lots supprimés le 2026-11-18 : 63 images,
  12 `.splits`, 142 clés de langue, 5 fichiers divers.

## Étape 2 : les frameworks forkés

`globs`, `splits`, `saxstack` et `prevayler` sont des versions privées et figées. BudgetView
n'utilise sans doute qu'une partie de leurs API.

- [x] **2a. API de `globs` réellement appelées** par `bv_desktop` et `bv_shared` (`jdeps`, ou
  analyse des imports et des appels). Le reste est candidat à la suppression, avec ses tests
  (873 aujourd'hui).
  Inventaire fait le 2026-11-19 (script [audit/globs.py](audit/globs.py)), voir [2a, inventaire](#2a-inventaire) plus bas.
  Lots A à E faits du 2026-11-19 au 2026-11-20 : `sqlstreams` déplacé dans `bv_server`, 46 classes et ~3 300 lignes de code
  supprimées, tests de `globs` 873 → 787.
- [x] **2b. `saxstack`** : ce qu'il apporte par rapport au SAX ou au StAX du JDK. Peut-il être
  remplacé, ou au moins réduit ?
  Inventaire fait le 2026-11-20 (rapport ProGuard de `usage.sh`), voir [2b, inventaire](#2b-inventaire) plus bas :
  le remplacer n'apporterait rien, le réduire de moitié, oui.
  Lots A à D faits le 2026-11-20 : 7 classes supprimées, 13 passées dans les tests de `globs`, Xerces retiré, et ce qui
  restait (17 classes, 608 lignes) **fondu dans `globs`** : le module `saxstack` n'existe plus.
- [x] **2c. `prevayler`** : quelles parties du fork servent ? Ne rien changer au format sur
  disque.
  Inventaire fait le 2026-11-20 (ProGuard, JaCoCo, lecture de la configuration), voir [2c, inventaire](#2c-inventaire)
  plus bas : environ la moitié du code est morte, dont une bonne part par configuration, que ProGuard ne voit pas.
  Lots 0 à C faits le 2026-11-20 : tests du module remis en marche, 299 fichiers annexes et 47 classes supprimés ;
  `prevayler` 56 classes, 1 686 lignes de code, jar 121 → 60 Ko. Lot D (journal en mémoire) gardé, fusion écartée.
- [x] **2d. `splits`** : balises et composants jamais utilisés dans les `.splits`.
  Inventaire fait le 2026-11-20, voir [2d, inventaire](#2d-inventaire) plus bas : peu de chose à retirer (~350 lignes
  sur 7 800), le gros du tri ayant été fait par 2a ; s'y ajoutent la coquille du dossier `splits/` et 9 démos.
  Lots A à C faits le 2026-11-20 : dossier `splits/` et démos supprimés, 5 balises, 2 bordures, 3 icônes et 2 attributs
  retirés ; `splits` 132 classes, 7 361 lignes de code ; tests de `globs` 813 → 796.

## Étape 3 : ce que Java fait maintenant tout seul

Des remplacements mécaniques et relisibles, pas une réécriture.

- [x] **3a. Alertes du compilateur** : compiler avec `-Xlint:all` et classer ce qui ressort
  (dépréciations, types bruts, `finalize`…).
  Inventaire fait le 2026-11-20, voir [3a, inventaire](#3a-inventaire) plus bas : ~2 000 alertes, dont ~1 900 de
  bruit ou hors sujet. Lots A à C faits le 2026-11-20 : ~70 corrections mécaniques, et le build affiche désormais les
  catégories utiles, à 0 alerte. Lot D (`junit.framework.Assert`) renvoyé à l'étape 5 ; `Runtime.exec` à 3c, masques
  `InputEvent` à 4a.
- [x] **3b. OpenRewrite** : passer les recettes de migration Java 8 → 21 en mode `dryRun`,
  relire le diff et n'appliquer que par familles.
  Inventaire fait le 2026-11-20, voir [3b, inventaire](#3b-inventaire) plus bas : 231 fichiers dans le build, 13 familles.
  Deux familles sont fautives : `ListFirstAndLast` rend `GlobList.getFirst()` récursive, et `UseTextBlocks` efface des
  blancs de fin de ligne. Lots A (recettes sûres, 102 fichiers) et C (plugins) faits le 2026-11-20 ; B (blocs de texte)
  non retenu, D rejeté.
- [x] **3c. Intégration macOS** : `com.apple.eawt` (2 fichiers) → `java.awt.Desktop` (Java 9 et +).
  Inventaire fait le 2026-11-20, voir [3c, inventaire](#3c-inventaire) plus bas : `java.awt.Desktop` déjà en place
  depuis le passage à Java 17, mais par réflexion et avec un repli `com.apple.eawt` mort ; navigateur lancé par trois
  `Runtime.exec(String)` selon le système. Deux lots proposés (A et B), faits le 2026-11-20 : plus aucun `com.apple.eawt` ni
  `Runtime.exec` dans le code livré, −189 lignes.
- [x] **3d. Utilitaires maison** (`Utils`, `Strings`, `Files`…) comparés à `java.util`,
  `java.nio.file`, `Objects`, `String` (`isBlank`, `repeat`, `strip`…).
  Inventaire fait le 2026-11-21, voir [3d, inventaire](#3d-inventaire) plus bas : peu de remplacements exacts
  (`Objects.equals`, `Arrays.copyOf`, `String.repeat`, comparateurs), le reste a une sémantique propre. **Découverte** :
  les balises `Utils.beginRemove`/`endRemove`, que le shrinker supprimé exploitait, laissent depuis la 6.0.1 du code de
  développement dans l'application livrée (menu `[Dev]`…). Quatre lots proposés (0, A à C), tous faits le 2026-11-21 :
  balises remplacées par `Application.isDevMode()`, 4 classes et ~25 méthodes supprimées, `Utils.equal` → `Objects.equals`.
- [x] **3e. Collections synchronisées** (`Vector`, `Hashtable`, `StringBuffer`, 5 fichiers).
  Fait le 2026-11-21 en un seul lot, voir [3e](#3e-collections-synchronisées) plus bas : 41 `StringBuffer` → `StringBuilder`
  (17 fichiers), 1 `Vector` → tableau, aucun `Hashtable` ; tous locaux à un thread.
- [x] **3f. Dates** (35 fichiers) → `java.time`. C'est le chantier le plus délicat : le modèle
  stocke des mois et des jours sous forme d'entiers. Ne toucher qu'au code de calcul et
  d'affichage, et le faire en dernier.
  Inventaire fait le 2026-11-21, voir [3f, inventaire](#3f-inventaire) plus bas : 97 fichiers et non 35, dont les deux
  tiers ne font que transmettre le type (API de globs et de Prevayler, champs `DateField` du modèle) et restent. Trois lots
  proposés (A à C) ; l'analyse des dates à l'import reste en `SimpleDateFormat`, sauf décision contraire.
  Clos le 2026-11-21 sur le seul plus évident (choix de Matthieu : meilleur rapport gain/risque), voir
  [3f, ce qui a été fait](#3f-ce-qui-a-été-fait) : `Month` en `java.time`, `hh` → `HH`, `OfxImporter.DATE_FORMAT`
  non partagé. Lots B et C abandonnés.

## Étape 4 : les contournements, par l'historique

- [x] **4a. Repérer les motifs suspects** : tests sur `os.name` ou `java.version`, `invokeLater`
  imbriqués, `Thread.sleep` et minuteries dans l'IHM, `catch` vides, réglages forcés de Swing
  (`UIManager.put`, propriétés `apple.*`, `sun.*`).
  Fait le 2026-11-21 avec 4b, voir [4a et 4b, inventaire](#4a-et-4b-inventaire) plus bas : peu de contournements
  vivants ; trois lots proposés (A code mort sous Java 21, B masques d'événements dépréciés, C cinq essais dans l'appli).
- [x] **4b. Retrouver leur raison** : `git log -L <début>,<fin>:<fichier>` ou `git blame`, puis le
  message du commit d'origine. Chercher aussi directement dans les messages :
  `git log -i -E --grep='bug|mac|swing|jdk|java'`.
- [x] **4c. Tester sans le contournement** sous Java 21, sur macOS, et le retirer s'il n'est plus utile.
  Lot A fait le 2026-11-21 : versions de Windows de `GuiUtils` et `AppPaths`, `sun.awt.exception.handler`,
  `PicsouLinuxLookAndFeel` (gardée en 1e comme « Linux, hors champ », mais aucun système ne la chargeait).
  Lot B fait le 2026-11-21 (choix de Matthieu : les quatre méthodes, tests d'abord) : masques `InputEvent` dépréciés
  remplacés par `getModifiersEx`, `getButton` et `ActionEvent.*_MASK`, en reproduisant le calcul des anciens masques par AWT
  (bouton qui change d'état pour appui, relâchement et clic ; boutons tenus sinon). Le défaut « Meta = clic droit,
  Alt = bouton du milieu » est gardé et documenté. Garde : `MouseModifiersTest`, `GlobTableViewRightClickTest` (`globs`)
  et `GuiModifiersTest` (`bv_desktop`) comparent aux anciens masques sur ~3 000 événements, construits comme AWT
  (masques étendus) et à l'ancienne.
  Lot C fait le 2026-11-21, dans l'appli avec Matthieu (version sans contournements, données de test) ; les cinq retirés :
  C1 faux dialogue modal d'`ImportDialog` fenêtre réduite (le dialogue s'ouvre, macOS restaure la fenêtre), C2 pause de
  50 ms d'`ImportController` (fichier pris en compte à l'étape 1), C3 coupure des bulles à la perte du focus (pas de bulle
  fantôme après Cmd-Tab), C4 hauteur réduite des boutons maison (elle décentrait le texte : marges mesurées 20/16 pixels
  avec, 20/20 sans), C5 marge des boutons hors OpenJDK (jamais posée avec les JDK actuels, non essayable ici).
  **Essais.** Une icône Java lancée par `run-dev.sh` n'accepte pas de fichier glissé, et `BudgetView 6.app` non plus (aucune
  association de fichiers, noté dans [idees-ameliorations.md](idees-ameliorations.md)). Seul chemin vers
  `OpenRequestManager.openFiles` : lancer l'appli **sans** `-DSINGLE_INSTANCE_DISABLED` (ports libres, BudgetView 6 fermée),
  puis un second `java -jar … fichier.ofx` avec les mêmes propriétés, que l'instance unique transmet à la première.
  Une fenêtre Java ne se dessine dans `screencapture -l` qu'après un premier clic de Matthieu dedans. **Étape 4 terminée.**

## Étape 5 : les tests

À faire après les étapes 1 et 2, qui en suppriment déjà une partie.

- [ ] **5a. Tests désactivés** (`disabled_test…`, voir [etat-des-tests.md](etat-des-tests.md)) :
  pour chacun, réparer ou supprimer.
- [ ] **5b. Tests en double** : avec JaCoCo par test, repérer ceux qui couvrent exactement les
  mêmes lignes.
- [x] **5c. Durée** : repérer les classes de test les plus lentes (`target/surefire-reports`) et
  voir si leur coût est justifié.
  Fait le 2026-11-21 (avancé avant 3d à la demande de Matthieu), voir [5c, durée](#5c-durée) plus bas : suite de
  `bv_desktop` de 9 à 21 min ramenée à 5 min (3 JVM en parallèle, `caffeinate -i` contre la veille du Mac).
- [ ] **5d. Tests de `siteweaver` sous Maven** : comme `prevayler` en 2c (lot 0), ils sont en JUnit 3.8.1, que surefire
  refuse ; ils n'ont jamais tourné par Maven. Passer à JUnit 4.12+ dans son `pom`. Lancés à la main en 3a (JUnitCore) :
  72, dont 10 échecs anciens à trier (7 de `SiteUploaderTest`, 3 classes sans test, qu'un `TestSuite` signale).
- Chantier suivant, à part : compléter les tests unitaires et corriger les défauts d'import relevés
  par le sondage du 2026-11-18, voir [tests-unitaires.md](tests-unitaires.md).
- Ne pas toucher aux tests fonctionnels qui couvrent des fonctions encore utilisées : ce sont
  eux qui garantissent les autres chantiers.

## 1a, inventaire

Fait le 2026-11-16, sans rien modifier.

**Méthode.** On construit le graphe des dépendances entre classes à partir du bytecode
(`jdeps -verbose:class -filter:none -e 'com\.budgetview\..*' bv_desktop/target/classes bv_shared/target/classes`),
en ramenant les classes internes à leur classe englobante. On calcule ensuite les classes accessibles
depuis `DesktopApp`, une fois dans le graphe complet, une fois sans les arêtes que le mode local rend
inopérantes. La différence entre les deux donne le code mort par construction. La méthode est grossière,
puisqu'elle travaille à la classe et non à la méthode. Chaque coupure ci-dessous a donc été vérifiée
dans le source.

`isServerEnabled()` ne suffit pas à repérer ce code. Une bonne part est coupée autrement :

| Coupure | Garde réelle |
|---|---|
| `MainWindow` → `LicenseCheckerThread` | `isServerEnabled()` |
| `MainWindow` → `HttpsDataTransport`, `AutoRetryDataAccess` (données stockées sur le serveur) | adresse `DEFAULT_ADDRESS_PROPERTY` vide par défaut, donc jamais `http…` |
| `MenuBarBuilder` → envoi d'avis, des logs, du fichier importé | `isServerEnabled()` |
| `MenuBarBuilder` → `EditMobileAccountAction` | construite, mais menu Mobile commenté |
| `MenuBarBuilder`, menu de dev → licence, mobile, évaluation, statistiques d'usage, `CloudDemoAction` | sans objet en local |
| `UserConfigService` → `DownloadThread`, `KeyService`, `Http` | `isServerEnabled()` |
| `NewVersionService` → `LicenseAPI` | `isServerEnabled()` |
| `ImportedFileSelectionPanel` → `CloudIntroPanel` | construit, puis masqué par `isServerEnabled()` |
| `AutoCloudImportView` → `AutoImporter` | `isServerEnabled()` |
| `PicsouFrame`, `ExitAction` → `UsageDataSender`, `UserEvaluationDialog` | `User.CONNECTED`, toujours faux |
| `MainPanel` → `SendMobileDataAction` | `User.CONNECTED` |
| `MainPanel`, `HyperlinkHandler` → `FeedbackService` | plus aucun lien `contact:` dans les ressources |
| `PicsouInit` → `LicenseRegistrationTrigger` | personne ne s'enregistre en local (**trigger : à retirer avec soin**) |
| `Application` → `MobileService`, `ServerMailingService`, `CloudService` | services enregistrés d'office ; leurs vrais appelants sont coupés ci-dessus |

**Résultat : 51 classes, 5 734 lignes**, sur 1 112 classes de premier niveau (`bv_desktop` et `bv_shared`) :

- **Mobile** (22 classes) : `desktop.mobile` (6) et `desktop.mobile.utils` (4), `mobile.BudgetValuesUpdater`,
  `shared.mobile.model` (10), `shared.encryption.PasswordEncryption`.
- **Retours utilisateur** (9) : `FeedbackDialog`, `FeedbackService`, `UsageDataSender`, `UserEvaluationDialog`,
  `SendFeedbackAction`, `SendLogsAction`, `SendImportedFileAction` et `SendImportedFileDialog`,
  `io.importer.Obfuscator` (anonymise le fichier envoyé).
- **Licence et mises à jour** (9) : `LicenseCheckerThread`, `LicenseRegistrationTrigger`, `KeyService`
  (1 894 lignes, surtout une clé publique), `LicenseAPI`, `userconfig.download` (4 classes, dont le
  téléchargement du jar), 2 actions du menu de dev.
- **Données sur le serveur** (4) : `HttpsDataTransport`, `AutoRetryDataAccess`, `ExceptionMapping`, `DisconnectionTip`.
- **Cloud, partie déjà coupée** (4) : `AutoImporter`, `SilentImportDisplay`, `CloudIntroPanel`, `CloudDemoAction`.
  Le reste de la synchro (`CloudService`, appelé par `ImportController`) relève de 1b.
- Menu de dev (2) : `DumpUsageDataAction`, `ShowUserEvaluationDialogAction`.

Trois questions restent ouvertes :

- **Deuxième cercle**, encore accessible mais dont l'affichage dépend d'un état de licence ou de version
  qui ne se produit plus. À lire un par un : `LicenseInfoView` et `LicenseExpirationDialog` (`MainPanel`),
  `LicenseActivationDialog` et `LicenseConfirmationFeedbackPanel` (ouverts depuis `ImportFileAction`),
  `NewVersionView`, `SendStackTracesAction` → `ServerMailingService` → `shared.http.Http`,
  `LicenseConstants` et `MobileConstants` (lus par `UserConfigService`).
- **Modèle** : `User`, `UserPreferences` (`MAIL_FOR_MOBILE`…), `CloudDesktopUser`, `LicenseActivationState`
  gardent leurs champs serveur. Ils restent tant qu'on ne passe pas par `model/upgrade`.
- **Android** : `budgetview-android` importe `com.budgetview.shared.model.*`, un paquet qui n'existe plus
  dans `bv_shared` (devenu `shared.mobile.model`). L'appli ne compile donc déjà plus contre ce dépôt, et
  supprimer `shared.mobile.model` ne la casse pas davantage.

Tests à retirer avec ce code : `HelpTest`, en partie (dialogues d'envoi), `SendImportedFileTest`, `ObfuscatorTest`,
et les checkers `FeedbackDialogChecker`, `SendImportedFileChecker`, `UserEvaluationDialogChecker`,
`checkers/mobile/*` et `checkers/license/LicenseChecker`. Ce dernier n'est qu'un champ de
`LoggedInFunctionalTestCase` (`register()` ne fait plus rien en 6.00), qu'on peut retirer avec lui.

Hors périmètre 1a, mais relevé au passage pour 1e : **43 classes ne sont accessibles depuis `DesktopApp`
par aucun chemin**. Une partie sont d'autres points d'entrée légitimes (`InMemoryDesktopApp`, `Dump`,
`BackupClinic`, `utils.generator.*`). D'autres sont des démos (`experiment.*Demo`, `BudgeaDemo`), ou des
classes que plus rien n'appelle (`AddOnsView`, `AddOnsSelector`, `BankEntityEditionDialog`, `PicsouButtonUI`,
`LaPoste`…), à recouper avec la réflexion.

**Ordre de suppression** (un commit par lot, suite verte avant et après) :

- [x] 1. mobile (`aa9740caf`). `PasswordEncryption` et `MobileConstants` restent : `bv_server` s'en sert.
- [x] 2. données sur le serveur (`6a2b98561`).
- [x] 3. retours utilisateur et envois (`37f48de6c`). `DisconnectionTip` y est passé, seul `FeedbackDialog` s'en servait.
- [x] 4a. bandeau « nouvelle version » (`69a9a539f`), avec `LicenseAPI`, qui fait partie du deuxième cercle.
  Lots 1 à 4a vérifiés aussi dans l'appli (`./run-dev.sh`) par Matthieu le 2026-11-16 : rien d'anormal.
- [x] 5. licence et téléchargement du jar (2026-11-16). Décision de Matthieu : supprimer **tout** le système, pas
  seulement le figer à « toujours enregistré ». Que l'appli reste personnelle ou soit distribuée sous Apache 2,
  l'ancien système ne servirait plus. Sa clé privée de signature est publique (`bv_server`). Un verrou dans un
  logiciel libre ne protège rien. Une offre payante future (accès à un agrégateur, par exemple) passerait par
  un compte et un abonnement vérifiés côté serveur. Supprimés : `desktop/license` (8 classes),
  `desktop/userconfig` (14, dont `UserConfigService`), `KeyService`, `LicenseRegistrationTrigger`, l'écran des
  extensions (`AddOnsView`, `AddOnsSelector`, déjà débranché), 58 clés de langue. Effets de bord :
  - la configuration des banques ne se charge plus depuis `configs/` du dossier de données (classes exécutées
    depuis un jar téléchargé). Vide chez Matthieu : elle vient toujours de celle intégrée au programme ;
  - le refus d'ouvrir des données écrites par un jar plus récent est gardé, avec un message réécrit ;
  - les préférences changent la langue par `DataAccess`, enregistré dans le `Directory` par `PicsouInit` ;
  - champs de licence de `User` conservés dans le modèle (données 5.01), plus lus ni écrits. Reste mort dans la
    couche de persistance : `DataAccess.register` et `LocalInfo.getMail/getSignature/getActivationCode`
    (à traiter en 1e, sans changer le format sur disque).
  Vérifié dans l'appli (`./run-dev.sh`) par Matthieu le 2026-11-16 : connexion, création d'utilisateur, import,
  compte de démo, changement de langue, rien d'anormal.
  Reste après ce lot la synchro Budgea (1b) et le deuxième cercle : `SendStackTracesAction` →
  `ServerMailingService`.
- [x] 6. `ServerMailingService` (2026-11-16), avec `ConnectionStatus`, qu'il était seul à appeler. Son unique
  client, l'action « surveillance des threads » du menu de dev, écrivait déjà son relevé dans le log avant de
  l'envoyer : elle est gardée sous le nom `DumpStackTracesAction`, sans l'envoi. Les constantes mail de
  `LicenseConstants` restent, `bv_server` s'en sert.

Pendant ces suppressions, compiler dans un worktree séparé (`git worktree add`, puis
`mvn -o test-compile -pl budgetview/bv_shared,budgetview/bv_desktop`). Cela permet de vérifier pendant
qu'une suite tourne, sans toucher à son `target/` ni au `bv_shared` de `~/.m2`. Chercher les usages d'un
checker de test par le nom de sa classe, et non par le nom du composant : `LoggedInFunctionalTestCase`
crée plusieurs checkers pour tous les tests.

## 1c, résultats

Fait le 2026-11-16, sans rien modifier.

**Méthode.** `mvn -Pcoverage test -pl budgetview/bv_desktop` pose l'agent JaCoCo 0.8.13 sur chaque JVM de test
(propriété `jacoco.args`, lue par l'`argLine` de surefire en `@{…}`, donc vide hors profil). Il mesure
`com.budgetview`, `globs`, `saxstack` et `prevayler`, et cumule tout dans `target/jacoco.exec` (16 Mo).
`docs/audit/coverage.sh` en tire un rapport HTML et CSV par module, et `zero.txt` : les classes de premier
niveau dont aucune instruction, classes internes comprises, n'a tourné. Les classes sans code exécutable
(interfaces) ne sont pas comptées. La suite dure 11 min sous l'agent, comme sans lui. Une erreur sur 1 028
tests, `SignpostSequenceTest.testSwitchingToTheDemoAccountAndBackPreservesSignposts`, instable : elle passe
3 fois sur 3 quand on relance la classe seule, avec ou sans l'agent.

| Module | Classes | À 0 % | Lignes | À 0 % |
|---|---|---|---|---|
| bv_desktop | 972 | 89 | 45 873 | 2 317 |
| bv_shared | 32 | 14 | 1 380 | 415 |
| globs | 468 | 164 | 18 150 | 4 324 |
| prevayler | 80 | 52 | 1 838 | 897 |
| saxstack | 32 | 22 | 750 | 415 |

Les chiffres de `globs`, `prevayler` et `saxstack` sont l'entrée de l'étape 2 : ils ne mesurent que ce que
l'application utilise, pas les tests propres à ces modules.

**Recoupement avec l'accessibilité statique** (`reach.py`, même méthode qu'en 1a). Les 103 classes à 0 % de
`bv_desktop` et `bv_shared` se répartissent en deux groupes.

*Jamais exécutées et inaccessibles depuis `DesktopApp` : 35 classes.*
- Outils et points d'entrée hors application, à garder : `InMemoryDesktopApp`, `AppCore` (servi par
  `DevDesktopApp` et `BackupClinic`), `persistence.direct.Dump`, `tools.BackupClinic` et
  `DataCheckExceptionHandler`, `utils.generator.*` (5 classes, dont `PicsouSampleGenerator`).
- Démos : `experiment.DeltaGaugeDemo`, `GaugeDemo`, `HistoButtonChartDemo`, `HistoChartDemo`, `io.budgea.BudgeaDemo`.
- Budgea, gardé par décision (1b) : `BudgeaCategory`, `BudgeaSeriesConverter`.
- Faux positif : `plaf.PicsouButtonUI`, appelé par les look & feel Windows et Linux du paquet `com.jgoodies`,
  que le graphe de `reach.py` ignore.
- **Candidates à la suppression, 17 classes, 373 lignes** : `bank.plugins.LaPoste` (le chargement des plugins
  de banque, `Loader.loadBank`, ne fait plus rien depuis le lot 5 : `BankPluginService` n'applique que son
  plugin par défaut), `accounts.position.SavingsAccountPositionLabels`, `budget.AccountThresholdMessage`,
  `budget.BudgetToggle`, `charts.stack.StackChartSelection`, `filtering.FilterSet` et `FilterSetBuilder`,
  `stringifiers.ColoredAmountStringifier`, `SeriesPeriodicityAndScopeStringifier`,
  `importer.components.OfxSecurityInfoButton`, `importer.edition.BankEntityEditionDialog`,
  `BankEntityEditionPanel`, `CardTypeChooserDialog`, `projects.utils.ProjectFilter`,
  `signpost.actions.SetSignpostStatusAction`, `utils.dev.GotoPastTrialExpirationAction` (période d'essai,
  reste de la licence ; son appel dans `OperationChecker.gotoPastTrialExpiration` n'a aucun appelant),
  `triggers.savings.UpdateMirrorSeriesBudgetChangeSetVisitor`.
  **Supprimées le 2026-11-16**, avec leurs trois `.splits` (`bankEntityEdition*`, `cardTypeChooserDialog`) et
  leurs clés de langue (`cardTypeChooser.*`, `synchro.security.info.*`, `bankEntityEdition.title`). Les clés se
  retirent des **trois** fichiers, `lang.properties` compris : `LangTest` vérifie que `lang_fr` et `lang_en`
  contiennent toutes les clés de cette racine.

*Jamais exécutées mais accessibles : 68 classes*, à confronter à l'usage réel (1d) :
- propres à Windows ou Linux : `UnixBrowsingService`, `WindowsBrowsingService`, `PicsouWindows*`,
  `PicsouLinuxLookAndFeel`, `PicsouWindowsLookAndFeel`, `LafUtils`, `PicsouRootPaneUI`, `PicsouOptionPaneUI` ;
- Budgea et cloud, gardés (1b) : `desktop.cloud.*`, `CloudConnection*FieldEditor`, `SynchroErrorDialog`,
  `ShowSynchroErrorAction`, `shared.cloud.*` ;
- client serveur et constantes que `bv_server` garde : `client.exceptions.*`, `shared.http.Http`,
  `shared.json.Json`, `PasswordEncryption`, `PasswordBasedEncryptor`, `LicenseConstants`, `MobileConstants` ;
- impression, jamais testée : `PrintStyle` et les `*Metrics` de `printing` ;
- le reste, petites classes d'IHM ou utilitaires sans test : `JRoundedPanel`, `RoundButtonUI`,
  `SelectionToggleUI`, `PieChartPainter`, `HistoDailyBlockPainter`, `SplitPaneDotsPainter`, `WavePanelUI`,
  `AddOnPanelLayout`, `ReadOnlyGlobTextFieldView`, `BlankAction`, `JsonImporter` (tests désactivés),
  `DataExchangeService`, `FloatingAverage`, `Chronometer`… La liste complète est dans `zero.txt`.

Quatre classes sont inaccessibles mais exécutées, donc servies par les seuls tests : `ComponentTextDisplay`,
`HistoLinePainter`, `OfxBeautifier`, `model.LicenseActivationState` (type du modèle, à garder pour les
données 5.01).

## 1d, inventaire

Fait le 2026-11-16 : nouvelle mesure de couverture (1 028 tests verts, sans erreur instable), recoupée avec
`reach.py` et une recherche de chaque nom dans les sources, les `.splits` et `bv_server`. 67 classes de
`bv_desktop` et `bv_shared` sont à 0 % sans être signalées inaccessibles.

**Faille de `reach.py`** : il ne connaît que les classes présentes dans au moins une arête de `jdeps`. Une
classe sans aucune dépendance vers `com.budgetview` ni aucun appelant n'apparaît ni dans les accessibles ni
dans les inaccessibles. Ainsi 15 des « 68 accessibles » de 1c, sans aucune référence, ne l’étaient pas (et le code Windows et Linux, que seul `com.jgoodies` appelle, ne l’est pas davantage).

| Groupe | Classes | Proposition |
|---|---|---|
| Aucune référence nulle part | `AccountAnalysisView`, `ReadOnlyGlobTextFieldView`, `PieChartPainter`, `BlankAction`, `experiment.AutoCompleteDemo`, `ButtonPopupDemo`, `SplitPaneDotsPainter`, `SeriesRecurrenceEditionPanel`, `FloatingAverage`, `utils.Html`, `ofx.InvalidPasswordOrBadUrl`, `ofx.TimeoutError`, `Chronometer`, `DataExchangeService` ; `JRoundedPanel` (seul un `main` de démo dans `src/test` s'en sert) | supprimer |
| UI enregistrées dans `ApplicationLAF` mais nommées par aucun `.splits` (un `.splits` désigne une UI par le nom complet de sa classe : `<ui name=… class="com.budgetview…">`) | `WavePanelUI`, `SelectionToggleUI`, `RoundButtonUI`, `AddOnPanelLayout` | supprimer, avec leur enregistrement. `RoundButtonUI` est gardé : les look & feel Windows et Linux (`com.jgoodies.looks.plastic`, hors du graphe de `reach.py`) l'installent directement |
| Stringifier d'un type que plus rien n'affiche | `AccountCardTypeStringifier` (le choix du type de carte, `CardTypeChooserDialog`, est parti en 1c) | supprimer, après vérification |
| Stringifier des projets | `ProjectStringifier`, `ProjectComparator` | chercher où une description de projet est demandée, sinon supprimer |
| Dessin : les tests uispec4j ne peignent jamais | graphe journalier des comptes (`HistoDailyBlockPainter`, `HistoDailyMetrics`) ; impression (`PrintStyle`, `BudgetGaugeBlockMetrics`, `SeriesTableMetrics`, `TransactionBlockMetrics`), fonction documentée dans `derniers_changements.html` et déjà testée jusqu'à `DummyPrinterService` | tester : rendu dans une `BufferedImage` ; `DummyPrinterService` qui dessine les pages du rapport |
| Point d'entrée | `DesktopApp` (seul son `main` n'a pas tourné) | garder |
| Hors champ : Windows et Linux | `UnixBrowsingService`, `WindowsBrowsingService`, `LafUtils`, `PicsouOptionPaneUI`, `PicsouRootPaneUI`, `PicsouWindowsFileChooserUI`, `PicsouWindowsLabelUI`, `PicsouLinuxLookAndFeel`, `PicsouWindowsLookAndFeel` | garder sans test |
| Cloud et Budgea (1b) | `desktop.cloud.*` (4), `CloudConnection*` (6), `SynchroErrorDialog`, `ShowSynchroErrorAction`, `JsonImporter` (format des relevés Budgea), `RealAccountComparator` (import des comptes du cloud), `shared.cloud.*` (5) | garder sans test |
| Client serveur et constantes de `bv_server` | `client.exceptions.*` (5), `shared.http.Http`, `shared.json.Json`, `PasswordEncryption`, `PasswordBasedEncryptor`, `LicenseConstants`, `MobileConstants` | garder |

**Supprimées le 2026-11-16** : les 21 classes des quatre premiers groupes (sans `RoundButtonUI`) et la démo
`JRoundedPanelTest`, avec les branches `AccountCardType` et `Project` de `PicsouDescriptionService`. Aucune
ressource ne leur était attachée, et `reach.py` corrigé ne signale aucun orphelin derrière elles.
`reach.py` corrigé signale aussi deux classes qui ne servent qu'aux tests : `desktop.utils.TimeUtils` (et son
`TimeUtilsTest`) et l'annotation `utils.Inline`, appliquée nulle part. Supprimées aussi.

**Tests du dessin** (2026-11-16). Les tests uispec4j n'affichent rien : sans dessin explicite, aucun code de
`paint` ne tourne.
- Impression : `DummyPrinterService` prépare le rapport (`init`, sur le format de page des tests, zone
  imprimable de 1000 x 900) puis dessine chaque page dans une `BufferedImage`, comme `DefaultPrinterService` et
  `PrinterJob`. **`PrintableReport.init` n'est pas idempotent** : un second appel ajoute de nouveau les pages.
  Les vérificateurs (`BudgetReportChecker`, `TransactionPrintChecker`) ne l'appellent donc plus.
  `SeriesGroupPrintingTest` repliait le groupe après l'impression et ne passait que parce que le rapport était
  préparé à la vérification : il le replie maintenant avant d'imprimer.
- Graphiques : `AbstractHistoChecker.getDataset` dessine le `HistoChart` dans une image à chaque lecture. Les
  fenêtres de test n'étant pas mises en page (largeur 0), il lui donne une taille provisoire de 400 x 200, puis
  rétablit la sienne (`HistoChart.setBounds` réinitialise ses métriques).

## 1e, inventaire

Fait le 2026-11-17, sans rien modifier.

**Méthode.** `docs/audit/usage.sh` passe le livrable (`target/dist`) à ProGuard 7.6.1 en `-printusage`, sans
réduire, optimiser ni renommer. Le point d'entrée est `DesktopApp.main`, les règles sont dans
[audit/proguard.pro](audit/proguard.pro). Les modules du dépôt sont analysés (`budgetview.jar`, `bv_shared`,
`globs`, `saxstack`, `prevayler`) ; les bibliothèques tierces servent seulement à résoudre les références.
Règles contre les faux positifs de la réflexion :
- globs : `GlobTypeLoader` lit les champs `public static` des types et leurs annotations ;
- splits : chaque classe nommée par un `.splits` (`class="…"`) est gardée avec son constructeur, et
  `PropertySetter` pose les attributs par les setters publics des composants, des UI et des layouts ;
- `UIService` et `LayoutService` instancient les classes qu'`ApplicationLAF` enregistre par leur nom ;
- look & feel (`createUI`), énumérations, sérialisation.

`usage.py` classe ensuite chaque membre isolé selon les occurrences de son nom dans le code principal, les
tests et `bv_server`. Ce tri est indicatif : un nom courant (`show`, `getKey`) compte comme cité même quand le
membre est mort. Durée : 15 s.

| Module | Classes entières | Membres isolés |
|---|---|---|
| bv_desktop | 56 | 618 |
| bv_shared | 8 | 106 |
| globs | 293 | 640 |
| prevayler | 32 | 86 |
| saxstack | 34 | 56 |

**Classes.** ProGuard confirme `reach.py` : 31 classes de premier niveau inaccessibles des deux côtés,
toutes déjà classées en 1c et 1d (outils, démos, Budgea). Il ne s'en écarte que sur 8 classes :
- 4 sont signalées par ProGuard seul : `client.exceptions.UnableToWrite` (client serveur, gardé),
  `PicsouLinuxLookAndFeel` (Linux, hors champ), et deux interfaces que `reach.py` voit seulement parce qu'elles
  sont implémentées, `RealAccountImporter` (son unique méthode, `ImportDialog.importAccounts`, n'a aucun
  appelant) et `time.selectable.Transformation` ;
- 4 sont signalées par `reach.py` seul et sont gardées à raison : `RoundButtonUI`, `PicsouButtonUI` et
  `PicsouWindowsLabelUI`, que les look & feel installent par `createUI`, et `model.LicenseActivationState`,
  conservé pour les données 5.01.

Les 17 classes internes inutilisées dont la classe englobante sert sont l'apport propre de ProGuard. On y
trouve les deux stringifiers d'`AccountPositionLabels`, `Account$UserAccountMatcher` et
`Account$UserSavingAccountMatcher`, les deux écouteurs de survol de `Gui`, et `Http$Post` (gardé).

**Membres isolés de `bv_desktop` et `bv_shared` (724).**

| Groupe | Membres | Lecture |
|---|---|---|
| Constantes `static final` cités dans le source | 211 | Faux positifs : `javac` recopie leur valeur, le bytecode ne les cite plus. ProGuard ne peut pas en juger |
| Groupes gardés (cloud, Budgea, client serveur, Windows, Linux) | 93 | Garder (1b, 1d) |
| Constructeurs | 59 | Surtout le constructeur implicite de classes à méthodes statiques : rien à gagner. Exceptions dans les lots |
| Servis par les seuls tests | 29 | Garder : points d'observation des tests (`getSelectionManager`, `waitRepaint`, `setDummyBrowser`…) |
| Servis par `bv_server` | 2 | Garder (`AppLogger.getLogFile`, `SerializedGlob.setId`) |
| **Aucune autre occurrence** | 58 | Morts, sauf erreur de lecture : à supprimer par lots |
| **Nom cité ailleurs** (homonyme ou redéfinition) et **abstraits** | 263 + 8 champs | À lire un par un. Les gisements identifiés sont regroupés ci-dessous |

**Lots proposés.**

| Lot | Contenu | Taille |
|---|---|---|
| A. Enregistrement local et version téléchargée | Vestige de la licence (1a, lot 5). La chaîne `localRegister` / `register` / `downloadedVersion` / `setDownloadedVersion` / `localDownload` / `removeLocalUser` / `canRead` / `getUserId` traverse `DataAccess` (et `LocalInfo`), `DataAccessDecorator`, `EncryptToTransportDataAccess`, `DataTransport`, `LocalSessionDataTransport`, `SessionService`, les `SessionState`, `Persistence`, `RootDataManager` et leurs implémentations (`Dummy`, `InMemory`, `Prevayler`, `PRootDataManager`). Ne rien appeler ne veut pas dire ne rien relire : les transactions Prevayler `Register` et `SetDownloadedVersion` restent, avec leur lecture et `executeOn`, car un ancien journal peut en contenir. Seuls partent leurs constructeurs publics et ce qui les émet | ~70 membres |
| B. Extensions (`AddOn`) | La 6.00 active toutes les extensions : `getName`, `getIcon`, `getDescription`, `activate`, `isEnabled`, `getField` et les 5 `processPostActivation` ne sont plus appelés. Voir ce qui reste utile d'`AddOn` et d'`AddOnService` (`getAddOns` ne sert qu'aux tests) | ~16 membres, peut-être 7 classes |
| C. Plugins de banque | `BankPlugin.postApply` et `getVersion`, `BankPluginService.add`, `TransactionAnalyzerFactory.Loader.loadBank` : suite de 1c (le chargement des plugins ne fait plus rien) | ~6 membres |
| D. Partie instance d'`AccountPositionLabels` | La classe sert par ses méthodes statiques ; son constructeur, ses méthodes abstraites, ses 5 champs et ses deux stringifiers internes n'ont plus d'utilisateur depuis la suppression de `SavingsAccountPositionLabels` (1c) | ~15 membres, 2 classes internes |
| E. Méthodes sans aucune autre occurrence | Les 58 du tableau, dont `TimeViewPanel.goto*`, `OfxWriter.writeLoadOp` et `writeQuery`, les `HistoDiffColors.get*`, `Gui.initHtmlEditor` et `createInvisibleLabel`, `RandomStrings.random*` (seule `randomAlphanumeric` sert), `PRootData.writeV1Info` | 58 membres |
| F. Divers repérés parmi les homonymes | `main` de démo dans `NotificationFlagUI`, `OnOffToggleUI`, `PlusMinusToggleUI` et `PRootDataManager` (avec son `dump`) ; écouteurs clavier vides de `TimeViewPanel` ; interfaces `RealAccountImporter` et `Transformation` | ~10 membres, 2 classes |

**Bilan des lots** (2026-11-18, un commit par lot, de `62924fc87` à `f250cc45b`).

| Lot | Fait | Écarts par rapport à la proposition |
|---|---|---|
| E | 57 membres sur 58, puis ce qu'ils étaient seuls à utiliser : `HelpSource` et les fichiers d'aide embarqués (`help/*/manualDownload.html`), le drapeau `filterOnCurrentMonth` d'`Uncategorized`, les actions de fermeture de `PicsouDialog`, des couleurs lues mais jamais rendues. Ensuite, morts par ricochet : `Month.getLastDay(int)` (lot D) et `TimeGraph.getYearWeigth` (`goto*`) | Gardé : `HistoChartMetrics.labelZoneHeightWithMargin`, appelé par l'appli Android, qui partage `bv_shared` |
| D | Classe réduite à ses deux méthodes statiques | — |
| F | Mains de démo des trois UI, `RealAccountImporter`, `Transformation`, écouteurs clavier de `TimeViewPanel` | Gardés : `PRootDataManager.main` et `dump`, que l'outil `persistence.direct.Dump` appelle |
| C | Seul le plugin par défaut servait : son code passe dans `BankPluginService.apply`, `BankPlugin` et `AbstractBankPlugin` disparaissent | Plus large que prévu (2 classes) |
| B | `AddOn`, `AddOnService` et les 5 extensions (7 classes), leurs icônes et leurs messages d'activation | Restent le `GlobType` `AddOns` (données), `AddOnListener` et l'action de dev `ToggleAllAddOnsAction` |
| A | Toute la chaîne. `LocalInfo` et `RepoInfo` ne portent plus que la langue et la version du jar ; le message de connexion, échangé en mémoire, est réduit d'autant | `PRootData` garde et écrit tous ses champs ; `Register` et `SetDownloadedVersion` restent pour la relecture |

**Angle mort de ProGuard.** Il part de `DesktopApp.main` : le code que seuls des outils lancés à part appellent
(`persistence.direct.Dump`, par exemple) passe pour mort. Seule la compilation l'a révélé. Avant de supprimer un
`main` ou ce qu'il appelle, chercher ses appelants dans tout le dépôt.

Après les lots, ProGuard ne signale plus qu'un membre sans aucune autre occurrence, gardé pour Android. Les
autres groupes (homonymes, abstraits, constructeurs) restent à lire au cas par cas ; rien n'y a été repéré
d'aussi gros que les six lots.

Les chiffres de `globs`, `prevayler` et `saxstack` sont l'entrée des chantiers 2a à 2c, avec ceux de 1c.

## 1f, inventaire

Fait le 2026-11-18, sans rien modifier.

**Méthode.** `python3 docs/audit/resources.py [--detail]` (8 s) cherche chaque ressource de
`bv_desktop/src/main/resources` dans le code principal (`bv_desktop`, `bv_shared`, `globs`) et dans les autres
ressources (`.splits`, `.xml`, `.properties`, `.html`). Les tests ne comptent pas, mais une ressource qu'ils
sont seuls à citer est signalée à part. Les orphelins ont ensuite été recherchés par `git grep` dans tout le
dépôt (`bv_server`, site, scripts) : seuls des homonymes ou des pages du site les citent.
- Images : chemin complet relatif à `images/` (`"cards/budget.png"`, `icon="…"`). Trois chemins sont construits,
  décrits dans le script : `cards/` + nom de `Card`, `color_themes/` + image de `ColorTheme`,
  `welcome/<langue>/welcome.png`.
- `.splits` : chemin `"/layout/…"` cité en Java ou dans un `file="…"` d'inclusion.
- Clés de langue : littéral Java, `$clé` dans un `.splits`, ou valeur d'un `.xml`. Une clé dont un préfixe
  (`"categorization.noseries."`) ou un suffixe à frontière de point est un littéral Java est classée
  « dynamique possible », pas orpheline.
- Banques : chaque `.xml` de `banks/` doit figurer dans `bankList.txt`, seule porte d'entrée
  (`TransactionAnalyzerFactory`).

| Ressource | Total | Orphelins | Taille des orphelins |
|---|---|---|---|
| Images | 164 | 61 | 332 Ko |
| `.splits` | 133 | 12 | 314 lignes |
| Clés de langue (union des 3 fichiers) | 1 486 | 131 | ~130 lignes par fichier |
| Banques (`.xml`) | 25 | 1 | — |
| Divers | — | 4 fichiers | 124 Ko avec la banque |

**Ce qui reste, et pourquoi.**
- Les 80 clés `demo.*` ne sont citées que par `DemoGenerationTest`, qui génère les comptes de démo
  (profil `gen-demo`) : gardées. Idem pour 8 autres clés que seuls les tests citent, à revoir avec l'étape 5.
- 330 clés « dynamique possible » : le préfixe existe dans le code, la clé n'est pas forcément atteinte
  (ex. `categorization.noseries.noaccount` alors que le suffixe vient de `BudgetArea.getName()`). À lire famille par
  famille si l'on veut aller plus loin ; le gain est faible.
- `fonts/OpenSans-Apache-License.txt` : jamais lu, mais la licence Apache impose de la livrer avec la police.
- `demo/demo-*.snapshot` : générés, chargés par un chemin construit.

**Défauts relevés** (corrections hors audit, voir les règles de conduite) :
- `lang_en.properties`, `categorization.noseries.variable` : la deuxième ligne de continuation n'a pas de `\`.
  En anglais, le message perd son `</html>` final et `</html>` devient une clé à part.
- Clés présentes dans une langue et absentes d'une autre : 13 manquent à `lang.properties` (langue par défaut,
  celle des tests), 8 à `lang_en`, 2 à `lang_fr`. Toutes orphelines ou dynamiques, sauf erreur.

**Lots proposés.**

| Lot | Contenu | Taille |
|---|---|---|
| A. Images | Tout le dossier `home/` (16, l'ancienne page d'accueil), `cards/addons.png` (extensions, 1e lot B), les variantes `_rollover`, `_disabled`, `_selected` que plus aucun `.splits` ne nomme, les icônes de synchro (`button_synchro*`, `import_synchro`), `activation_key` (licence), `googleplay` (mobile), `smiley_*`, `help_logo`, `open_website*`… | 61 fichiers, 332 Ko |
| B. `.splits` | Écrans des connecteurs bancaires (`bank/connection/` : BNP, Banque Postale, SG, OFX, identifiant et mot de passe, autre), `home/actionView`, `analysis/accountAnalysisView`, `accounts/accountEvolutionView`, `accountTypeSelectionPanel`, `chooseOrCreateAccountDialog`, `general/restoreChangePassword` | 12 fichiers, 314 lignes |
| C. Clés de langue | Les 131 orphelines, dans les 3 fichiers : familles `synchro.*` (connecteurs), `import.*`, `wizard.*`, `monthRange.*`, `savingsView.*`, `navigationView.*`, anciennes séries par défaut (`series.electricity`…, remplacées par `defaultSeries.*`)… Un oubli serait visible : `Lang.get` lève `ItemNotFound` | ~390 lignes |
| D. Divers | `connectors/bnp/*.js` (scripts du connecteur BNP), `css/jeditorpane.css` (plus cité depuis l'abandon de l'aide intégrée, `8285707c5`, 2015), `banks/uk/uk_banks.xml` (jamais listé dans `bankList.txt` depuis 2016) | 5 fichiers, 124 Ko |

Les lots A, B et D ne touchent pas le Java ; C non plus, mais chaque clé retirée sera recherchée encore une
fois avant suppression. Vérification : build, suite complète, et ouverture des écrans principaux dans l'appli.

**Suppression** (2026-11-18), dans l'ordre A, B, D, C, un commit par lot :
- Relancer `resources.py` après le lot B : les `.splits` retirés étaient seuls à citer 11 clés
  (`synchro.ofx.*`, `synchro.userAndPassword.*`…) et 2 icônes (`security_lock*`), versées au lot C.
- Chaque clé a été recherchée une dernière fois sous les formes `"clé` et `$clé` dans tout le dépôt
  (`bv_server` compris). Seul résultat : `account.updateMode`, qui ne sert que de préfixe
  (`"account.updateMode." + name`). Retrait par un petit script qui suit les lignes de continuation,
  puis contrôle par `java.util.Properties` : seules les clés visées ont disparu, aucune autre valeur n'a changé.
- Piège : construire avec `clean` après une suppression de ressource. Sinon le fichier reste dans
  `target/classes` et la suite passe sans rien vérifier.

## 2a, inventaire

Fait le 2026-11-19, sans rien modifier.

**Méthode.** Le rapport ProGuard de 1e (`docs/audit/usage.sh`, qui analyse déjà `globs` depuis `DesktopApp.main`),
trié par `python3 -I docs/audit/globs.py target/usage/usage.txt` (22 s) : classes de premier niveau inutilisées par
paquet, avec les autres sources qui citent leur nom (`bv_server`, `bv_analytics`, Android, tests de `bv_desktop`, tests
de `globs`), puis membres isolés des classes vivantes, classés comme dans `usage.py`.

`globs` compte 608 fichiers et 43 000 lignes ; ses tests, 796 méthodes. `bv_server` (hors build) en est l'autre grand
client : 114 fichiers, dont 113 imports de `sqlstreams`. Android cite `model`, `utils` et `xml` par un jar absent du
dépôt (`app/libs`).

| | Classes de premier niveau | Lignes | Poids dans `globs-1.0.jar` |
|---|---|---|---|
| Inutilisées, `sqlstreams` | 70 | ~3 500 | 81 Ko |
| Inutilisées, autres paquets | 93 | ~4 950 | 150 Ko |
| Vivantes | 445 | ~34 500 | 748 Ko |

`sqlstreams` (accès SQL, JDBC, HSQLDB, MySQL) est mort **en entier** côté bureau, annotation `AutoIncrement` comprise :
rien dans `bv_desktop` ni `bv_shared` ne l'importe. Il ne sert qu'à `bv_server`.

Membres isolés des classes vivantes : 638, dont 53 sans aucune autre occurrence dans le dépôt, 36 cités par les seuls
tests, 11 par le seul serveur ; les 538 autres partagent leur nom avec un membre vivant (interfaces, homonymes) et ne se
trancheraient qu'à la lecture.

**Dépendances entre lots.** `remote.ConstraintSerializer` et les tests de `remote` utilisent `sqlstreams.constraints` :
`remote` part avant ou avec `sqlstreams`. Trois classes inutilisées sont des **outils de test** : `XmlChangeSetParser`
(`DefaultChangeSetTest`, `ChangeSetSequencerTest`), `XmlGlobWriter` (`GlobTestUtils`), `XmlGlobStreamReader` (tests de
`sqlstreams` et `remote` seulement). Les deux premières se déplacent dans `globs/src/test` au lieu d'être supprimées.

**Lots proposés.**

| Lot | Contenu | Classes, lignes | Tests retirés |
|---|---|---|---|
| A. IHM de `globs` | Actions `CreateGlobAction`, `DeleteGlobAction`, `AbstractGlobSelectionAction` ; éditeurs `GlobLinkListEditor`, `GlobSlider*` ; vues `GlobRepeatView`, `GlobToggleView`, `GlobMultiLineTextView` ; `HyperlinkButtonUIDemo`, `GlobSelectionButton`, `GlobKeyListTransfer`, `FixedCellPainter`, `FixedFillPainter`, `DefaultColorChangeListener`, `ResourceLoadingFailed` ; les méthodes de `GlobsPanelBuilder` qui les créent (`addCreateAction`, `addDeleteAction`, `addSlider`…) | 16, ~880 | 7 classes, 21 tests |
| B. `remote` | `RemoteExecutor`, `SerializedRemoteAccess`, `ConstraintSerializer`, `DataAccess`, `remote.impl` (le `DataAccess` cité par 3 tests de `bv_desktop` est un homonyme, à vérifier) | 7, ~600 | 1 classe, 1 test |
| C. Divers sans serveur | `model` : `ChangeSetSequencer`, `DefaultFieldValuesWithPrevious`, `FieldValuesWithPreviousBuilder`, `GlobArrayFieldValues`, `FieldChangeListener`, `GlobLinkComparator`, `CachedGlobIdGenerator`, `ChangeSetPrinter` ; `utils.logging.Html*` (5) ; `utils.stream` (`ReplacementInputStream` et ses 8 classes de nœuds) ; `metamodel.links.LinkBuilder`, `LinkVisitor`, `MultifieldVisitor` ; `XmlSingleGlobParser`, `streams.xml.XmlGlobStreamReader` ; `Millis`, `T3uples`, `DefaultComparator`, `InvalidReference`, `Rethrowable`, `Timeout`. `XmlChangeSetParser` et `XmlGlobWriter` passent dans les tests. `GlobRepositoryValidator` et `IntSet` sont cités par les tests fonctionnels : à lire | ~40, ~2 700 | ~5 classes, ~20 tests |
| D. `sqlstreams` et ce que seul le serveur cite | Tout `sqlstreams`, puis `streams.accessors` (23), `GlobStream`, `JsonGlobWriter`, `GlobTreePrinter`, `StringComparator`, `Debug`, 4 exceptions | ~100, ~4 800 | 9 classes `sqlstreams` (17 tests) et 2 autres |
| E. Membres | Les 53 membres sans autre occurrence (liste du script), puis les 36 cités par les seuls tests, au cas par cas | — | selon le cas |

**Décision à prendre pour le lot D** : jusqu'ici, tout ce que `bv_server` utilise a été gardé (1a, 1d, 1e). Le
supprimer de `globs` laisse `bv_server`, déjà hors build, sans sa couche SQL : il ne compilerait plus sans une copie de
`sqlstreams`. Deux voies : garder `sqlstreams` tel quel, ou le déplacer dans `bv_server` (ce qui le sort du livrable
et des tests du bureau sans perdre le code).

**Décision de Matthieu (2026-11-19)** : déplacer `sqlstreams` dans `bv_server`.

**Suppression**, un commit par lot :
- Lot A (2026-11-19) : 16 classes et leurs 7 classes de test, les 5 méthodes de `GlobsPanelBuilder` qui les créaient,
  et les exemples `globs/samples` (`addressbook`, `blog`, `swingdemo`, 9 fichiers de 2008, hors build), qui se
  servaient de `addCreateAction` et `addDeleteAction`. Tests de `globs` : 873 → 833 ; `GlobMultiLineTextViewTest`
  héritait des 19 tests de `GlobTextViewTestCase`, qui tournent toujours pour les vues gardées.
- Lot B (2026-11-19) : le paquet `remote` (7 classes) et `RemoteExecutorTest`. Le `DataAccess` des tests de
  `bv_desktop` est bien un homonyme (`com.budgetview.client.DataAccess`).
- Lot C (2026-11-20) : 23 classes supprimées (`ChangeSetSequencer`, `FieldChangeListener`, `GlobLinkComparator`,
  `CachedGlobIdGenerator`, `ChangeSetPrinter`, `LinkVisitor`, `MultifieldVisitor`, `GlobRepositoryValidator`, `IntSet`,
  `Millis`, `DefaultComparator`, `InvalidReference`, `Rethrowable`, `Timeout`, les 9 de `utils.stream`) et leurs 3 classes
  de test ; avec elles, 4 méthodes qui ne servaient qu'à les citer (`Log.write(ChangeSet)`, `visit(LinkVisitor)`, deux
  `visitIndex(MultifieldVisitor)`). `XmlChangeSetParser` et `XmlGlobWriter` passent dans `globs/src/test`. `IntSet` ne
  servait qu'à `TransactionChecker` (tests de `bv_desktop`) : remplacé par un `Set<Integer>`. Tests de `globs` : 832 → 821.
  Reportés, parce que du code gardé les cite encore :
  - `LinkBuilder` (et `GlobTypeLoader.defineLink`) : seule façon de déclarer un lien à plusieurs champs, et 13 fichiers de
    test de `globs` s'en servent (`DummyObjectWithLinks`…). BudgetView déclare les siens par `LinkField` + `@Target`.
    Le sortir du jar voudrait réécrire ces modèles de test : **gardé**. La recherche par nom de classe ne le voyait pas,
    les tests n'appelant que `defineLink(…)`.
  - `FieldValuesWithPreviousBuilder`, `DefaultFieldValuesWithPrevious`, `GlobArrayFieldValues` : tenus par
    `DefaultSerializationInput.readChangeSet`, que seul `SerializationTest` appelle, et par `XmlChangeSetParser`.
    Au lot E, avec `readChangeSet` et `writeChangeSet`.
  - `utils.logging.Html*` (5) : seul `Debug` les atteint, et `Debug` est cité par un test de `bv_server` → lot D.
  - `T3uples`, `XmlGlobStreamReader` et `XmlSingleGlobParser` : servent à `sqlstreams` ou à ses tests → lot D.
- Lot D (2026-11-20) : rien de supprimé, **111 classes déplacées** hors de `globs` (5 426 lignes), sous leurs paquets
  d'origine pour que `bv_server` compile sans changer un import :
  - vers `bv_server/src/main` (102) : tout `sqlstreams` sauf `annotations.AutoIncrement`, `streams` (`GlobStream`,
    `GlobStreamBuilder`, les 23 `accessors`), `JsonGlobWriter`, `GlobTreePrinter`, `InvalidConfiguration`,
    `ItemAlreadyUsed`, `TimeExpired`, `TooManyItems`, `T3uples` ;
  - vers `bv_server/src/test` (8) : `XmlGlobStreamReader`, `XmlSingleGlobParser`, `Debug` et ses 5 `Html*`, que seuls
    des tests citent (ceux de `sqlstreams`, et `UserEvaluationTest` pour `Debug`) ;
  - vers `bv_analytics` : `StringComparator`, son seul client ;
  - avec eux 11 fichiers de test (681 lignes) : les 10 de `sqlstreams`, `DebugTest`, `StringComparatorTest`.
  `AutoIncrement` reste dans `globs` : annotation de modèle de 10 lignes, posée sur les `DummyObject` des tests de
  `globs`, que les tests SQL déplacés réutilisent par le `test-jar` de `globs` (le `pom` de `bv_server` le déclarait
  déjà, ainsi que `hsqldb` et `mysql-connector-java`, retirés du `pom` de `globs`). Ces deux pilotes étaient déjà
  exclus de `target/dist` : pas de gain de ce côté. `bv_desktop` déclare encore `hsqldb` pour un seul test
  (`JsonImporterTest`, `ReaderInputStream`).
  Vérifié : `bv_server` compile en `main` (ses tests ne compilaient déjà plus : `functests` de licence) ; les tests
  déplacés compilés à part et lancés par `JUnitCore` : 18 verts, MySQL compris (traduction de codes d'erreur).
  Tests de `globs` : 821 → 794.
- Lot E (2026-11-20), sur un inventaire relancé après le lot D (631 membres isolés : 50 sans autre occurrence, 40 cités
  par les seuls tests, 12 par le seul serveur), trois commits :
  - E1 : les 50 membres sans autre occurrence (−445 lignes). Les noms cités ailleurs (`getRenderedComponent`, `toList`,
    `setDateFormat`, `average`) ne le sont que par des homonymes d'uispec4j, de siteweaver ou des commentaires.
  - E2 : `readChangeSet` et `writeChangeSet` de la sérialisation binaire, que seul `SerializationTest` appelait, avec la
    partie `ChangeSetVisitor` de `DefaultSerializationOutput` (−302 lignes). Les trois classes reportées du lot C ne
    servent plus qu'à l'outil de test `XmlChangeSetParser` : elles passent dans `globs/src/test`. `LinkBuilder` reste.
  - E3 : sept utilitaires que seul leur propre test exerçait (`Dates.getStandardDate`, `extractDateDDMMYYYY`,
    `Strings.removeSpaces`, `trimLines`, `Utils.minInt`, `Encoder.stringToByte`, `GlobTypeUtils.getNamingField`), avec
    leurs 6 tests. Tests de `globs` : 794 → 787.
  Gardés parmi les membres servis par les seuls tests :
  - **`GlobTypeDependencies`** : ses quatre accesseurs ne servent qu'aux tests, mais sa construction (par
    `DefaultGlobModel`, à chaque démarrage) refuse les cycles de liens obligatoires. C'est un contrôle du modèle ;
  - les outils des tests de `bv_desktop` (`GuiUtils.pressKey`, `Files.copyResourceToTmpFile`, `loadPropertyKeys`,
    `Log.enableDebug`, `TablePrinter.addRow`, `Utils.randomInt`, `box`) ;
  - les points d'observation des tests du parsing des `.splits` (accesseurs des icônes, des UI, du `ColorService`) ;
  - des options des vues et du parseur (`setIconifier`, `setAutoDisableIfEmpty`, `parseIgnoreError`,
    `ChangeSetMatchers.changesForType`) et les `add*Field` de `GlobTypeBuilder` : les retirer voudrait réécrire des
    classes de test de base partagées (`GlobTextViewTestCase`…) pour quelques lignes.
  Les 12 membres que seul le serveur cite (`Dates`, `Strings`, `GlobMatchers`…) restent : ce sont des méthodes de
  classes vivantes, qu'on ne déplace pas seules.
  Les 527 homonymes ne sont pas lus : rien n'y a été repéré d'aussi gros que les lots précédents.
- Piège : des sources ont des fins de ligne CRLF (`GuiUtils`, `TableUtils` de `globs`, par exemple). Un script Python
  qui les lit en mode texte les convertit en LF et le diff réécrit tout le fichier. Lire et écrire avec `newline=''`,
  et vérifier qu'une suppression ne montre aucune insertion (`git diff --stat`).
- Piège : les tests d'IHM de `globs` (38 classes) échouent sous Java 21 sans les `--add-opens` du profil `jdk17` de
  `bv_desktop`, que le `pom` de `globs` n'avait pas. Ajoutés à son surefire avec une JVM par classe et une limite de
  temps : `mvn -o clean test -pl globs` suffit ([etat-des-tests.md](etat-des-tests.md)).

Les lots A, B, C ne touchent ni le format des données ni l'IHM de BudgetView (rien de ces classes n'est atteint) ;
vérification par build `clean`, tests de `globs` puis suite complète de `bv_desktop`. Rappel de 1e : l'angle mort de
ProGuard est le code lancé à part (`Dump`, génération de la démo), que seule la compilation révèle.

## 2b, inventaire

Fait le 2026-11-20, sans rien modifier.

**Méthode.** Le rapport ProGuard de `docs/audit/usage.sh` (qui analyse `saxstack` depuis `DesktopApp.main`), puis, pour
chaque classe morte, les autres sources qui la citent, et la fermeture de ce que ces clients entraînent.

`saxstack` compte 37 classes : 2 047 lignes de code, 1 330 de tests (44 tests). `saxstack-2.0.jar` : 87 Ko, 64 entrées.
Clients : `globs` (le parseur des `.splits`, `XmlGlobParser`, `XmlChangeSetVisitor`), `bv_desktop` (`BankFormatExporter`,
`TransactionAnalyzerFactory`) et des outils de test de `globs` et de `bv_server`. Android ne le cite pas.

**Remplacer par le JDK : non.** `SaxStackParser` est déjà un adaptateur de 99 lignes au-dessus du SAX du JDK
(`XmlUtils.getXmlReader` passe par `SAXParserFactory`) : il tient une pile de `XmlNode` et confie chaque balise au nœud
courant. Ses clients sont une dizaine de classes de nœuds, presque toutes dans `splits`. Passer à SAX nu ou à StAX
voudrait réécrire ce parseur des `.splits` pour retrouver la même pile. Ce qui vaut d'être fait, c'est réduire.

| | Classes | Lignes |
|---|---|---|
| Vivantes : parseur (`SaxStackParser`, `XmlNode`, `DefaultXmlNode`, `SilentXmlNode`, `XmlBootstrapNode`, 4 exceptions), écriture (`XmlTag`, `WriterXmlTag`, `PrettyPrintXmlTag`, `AbstractRootXmlTag`, `PrettyPrintRootXmlTag`, `XmlWriter`, `XmlWriterException`), `XmlUtils` | 16 | 735 |
| Mortes, tenues par des outils de test : `XmlComparator` (`XmlTestUtils` de `globs`, dont se sert aussi `BankFormatExportTest`) et ce qu'il entraîne (`DomXmlNode`, `BootstrapDomXmlNode`, `NodeType`, `NodeTypeFilter`, `SaxStackWriter`, `SaxStackBuilder`, `XmlRootBuilder`, `XmlNodeBuilder`, `Filter`, `FilterNone`), `XmlNodeToBuilder` (`XmlUtils.format`, pour afficher l'écart quand une comparaison échoue) | 12 | 792 |
| Mortes sans client : `PathFilter`, `XmlPrettyPrinter`, `FixedXmlNodeBuilder`, `IteratorBasedXmlNodeBuilder`, `NullXmlTag`, `NullAttributes`, `AttributeOrderProvider`, `UnexpectedTagException` | 8 | 392 |
| `RootXmlTag` : sert à `XmlChangeSetWriter.write`, que seuls des tests de `globs` appellent | 1 | 19 |

Membres morts des classes vivantes (une trentaine, sans compter les constantes que le compilateur recopie) :
`SaxStackParser.parse(File)` et `parse(…, EntityResolver)`, la classe interne `SAXExceptionHolder` (jamais construite, donc
la branche `catch` qui la traite non plus), `XmlUtils.format` à filtre, `build`, `write`, `get*AttrValue` à valeur par
défaut, `addInCDataValue`, les deux `getCurrentTagName`, `getAttrName`, `XmlParsingException(Throwable)`,
`XmlTag.addAttribute` en `double`, `long`, `float`. Les `addValue`, `addCDataValue`, `addXmlSubtree` et `getTagName` de la
hiérarchie `XmlTag` ne servent pas non plus à l'application, mais des tests les citent : à lire au lot A.

**Constats en marge.**
- Les tests de `saxstack` **n'avaient jamais tourné** sur ce poste : le réacteur se construit en `-DskipTests` et les suites
  lancées sont celles de `bv_desktop` et de `globs`. Il manquait même le fournisseur JUnit 3 de surefire, téléchargé ce jour.
  `mvn test -pl saxstack` : 44 verts, moins d'une seconde.
- `xercesImpl` 2.11 (et `xml-apis`) est une dépendance **de compilation** de `globs`, alors que seul `XmlTestUtils` (test)
  s'en sert, par `new org.apache.xerces.parsers.SAXParser()`. Le `pom` de `bv_desktop` l'exclut à la main de `target/dist`.
  `siteweaver` a sa propre dépendance à Xerces, pour son `XmlDomParser`.
- Le `pom` de `saxstack` déclare la *Common Public License* alors que son `LICENSE.txt` est l'Apache 2.0, comme le dit
  [licence.md](licence.md). Il garde aussi un dépôt Maven mort (`startupxp.dynalias.org`), l'adresse CVS de java.net et
  JUnit 3.8.1 ; `www/index.html` est la page d'accueil par défaut d'un projet java.net, jamais remplie.
- `SaxStackParser` utilise `Stack` et `StringBuffer` : pour 3e.

**Lots proposés.**

| Lot | Contenu | Classes, lignes | Tests |
|---|---|---|---|
| A. Code mort sans client | Les 8 classes mortes sans client et les membres morts ci-dessus | 8 + membres, ~500 | `XmlUtilsTest` (`PathFilter`), `FixedXmlNodeBuilderTest`, `XmlPrettyPrinterTest`, à lire |
| B. Outils de test hors du livrable | Les 12 classes tenues par `XmlTestUtils` passent dans `globs/src/test`, sous leurs paquets, avec leurs tests (`XmlComparatorTest`, `SaxStackWriter*Test`, `XmlNodeToBuilderTest`) ; `XmlUtils.format` va dans `XmlTestUtils`. `RootXmlTag` reste (19 lignes, pendant de `PrettyPrintRootXmlTag`) | 12, ~800 déplacées | suivent leur code |
| C. Xerces | `XmlTestUtils` passe par `XmlUtils.getXmlReader()` ; `xercesImpl` sort du `pom` de `globs`, et son exclusion de celui de `bv_desktop` | — | `globs`, `BankFormatExportTest` |
| D. Fondre `saxstack` dans `globs` (à décider) | Les 16 classes vivantes (~700 lignes après A) rejoignent `globs` sous `org.saxstack`, sans changer un import ; le module, son `pom` et `www/` disparaissent, un jar de moins dans `target/dist` | 1 module | les tests restants passent dans `globs` |

**Décision à prendre pour le lot D.** Après A et B, `saxstack` n'est plus qu'un parseur et un écrivain XML de ~700 lignes,
dont `globs` est le principal client et que `bv_desktop` appelle à peine (2 classes). Le garder comme module coûte un
`pom` à entretenir et un jar à livrer ; le fondre dans `globs` éloigne le code de sa forme publiée en 2008, sans autre
perte, la licence étant la même. Recommandation : le fondre.

**Décision de Matthieu (2026-11-20)** : faire les lots A à D, donc fondre `saxstack` dans `globs`.

**Suppression**, un commit par lot :
- Lot A (2026-11-20) : 7 classes sans client (`PathFilter`, `XmlPrettyPrinter`, `FixedXmlNodeBuilder`,
  `IteratorBasedXmlNodeBuilder`, `NullAttributes`, `AttributeOrderProvider`, `UnexpectedTagException`) et les membres
  morts listés plus haut (−450 lignes), avec 7 fichiers de test et `testWithExternalDTD`, qui ne servait que l'appel à
  `EntityResolver` (−919 lignes). Tests de `saxstack` : 44 → 26. Deux corrections de l'inventaire à la lecture :
  - `NullXmlTag`, `XmlUtils.build` et `write` servent à `SaxStackWriter`, donc à l'outil de test `XmlComparator` :
    ils passent au lot B. Les tests d'écriture (`SaxStackWriter*Test`) sont partis avec les constructeurs d'arbre
    (`FixedXmlNodeBuilder`, `IteratorBasedXmlNodeBuilder`) et le `PathFilter` qu'ils utilisaient ; `SaxStackWriter`
    reste exercé par `XmlComparatorTest`.
  - `XmlUtils.addInCDataValue` est appelé par `addCDataValue` : ProGuard le signalait parce qu'il l'avait inliné. Gardé.
- Lot B (2026-11-20) : 13 classes (les 12 de l'inventaire et `NullXmlTag`) passent dans `globs/src/test`, sous leurs
  paquets, avec `XmlComparatorTest` et `XmlNodeToBuilderTest`. `XmlUtils.build` et `write` deviennent des méthodes
  privées de `SaxStackWriter`, leur seul appelant ; `XmlUtils.format` passe dans `XmlTestUtils` de `globs`. Reste dans
  `saxstack/src/main` : 17 classes, 608 lignes. Tests de `saxstack` 5, de `globs` 808 (+21, venus de `saxstack`).
- Lot C (2026-11-20) : `XmlTestUtils` prend son lecteur par `XmlUtils.getXmlReader()` (JDK, avec espaces de noms, comme
  le `SAXParser` de Xerces par défaut) ; `xercesImpl` sort du `pom` de `globs`, et `xercesImpl`, `xml-apis` de la liste
  d'exclusions de `bv_desktop`, que plus rien ne lui amène (`dependency:tree`). Xerces ne reste qu'à `siteweaver`.
- Lot D (2026-11-20) : les 17 classes restantes passent dans `globs/src/main` et les 2 classes de test dans
  `globs/src/test`, sous `org.saxstack` (aucun import changé). Le module disparaît : `pom.xml`, `LICENSE.txt` (Apache 2.0,
  comme `globs`), `www/` et `src/site/` (pages java.net de 2008). Dépendance retirée des `pom` racine, `globs`,
  `bv_desktop` et `splits` (hors build) ; `coverage.sh` et `usage.sh` n'ont plus de module `saxstack` à lire, ses classes
  étant dans le jar de `globs`. Les tests venus de `saxstack` tournent désormais avec ceux de `globs`, ce qui règle le
  constat « jamais lancés ». L'ancien `org/saxstack/2.0` reste dans `~/.m2`, sans plus aucun client (`dependency:tree`).

## 2c, inventaire

Fait le 2026-11-20, sans rien modifier.

**Méthode.** Le rapport ProGuard de `docs/audit/usage.sh`, la couverture JaCoCo de la suite `bv_desktop` (1 026 tests
verts, 12 min), puis la lecture de `PrevaylerFactory` avec les réglages que fait l'application : ProGuard ne voit pas le
code qui n'est atteint que par un réglage jamais fait.

`prevayler` compte 103 fichiers dans `src/main` (5 671 lignes, 3 579 hors commentaires et lignes vides) et 44 dans
`src/test` (3 905 lignes). `prevayler-1.0.jar` : 121 Ko (204 Ko décompressés), 144 entrées. Sur 447 fichiers suivis par git, les 300 autres
sont des annexes (plus bas). Licence BSD (Klaus Wuestefeld), texte dans `root/docs/License.txt`.

**Ce que l'application en utilise.** Seul `bv_desktop` s'en sert (16 fichiers ; ni `bv_shared`, ni `bv_server`, ni
`globs`), à deux niveaux :
- le moteur complet pour la liste des utilisateurs, `<data>/users`, par `PRootDataManager` : `PrevaylerFactory` avec
  son propre sérialiseur (`DefaultSerializer`), filtrage des transactions coupé, ni réplication ni moniteur réglés ;
- pour les données de chaque utilisateur, rien que le nommage des fichiers (`PrevaylerDirectory` : `N.snapshot`,
  `N.journal`). Lecture et écriture sont faites par `bv_desktop` (`DirectAccountDataManager`, son propre
  `DurableOutputStream`).

**Format sur disque.** Le journal de `users/` est une suite de blocs `Chunking` (en-têtes texte `systemVersion`,
`executionTime`, `withQuery`) dont le contenu est produit par `DefaultSerializer`. Les classes de Prevayler n'y sont pas
sérialisées (`Capsule.toChunk` n'écrit que ses octets) : les retirer ne change pas le format. À ne pas toucher :
`Chunk`, `Chunking`, `DurableInputStream`/`DurableOutputStream`, `PersistentJournal`, `GenericSnapshotManager`,
`TransactionTimestamp`, les capsules, `PrevaylerDirectory`.

**Morts selon ProGuard** (32 classes entières) : tout `foundation/network` sauf ce que tient la réplication (20 classes),
`foundation/gzip` (4), `Log4jMonitor`, `NullMonitor`, `FileLocker`, `AlreadyLockedException`, `SkaringaException`,
`TransactionNotDeserializableError`, `SureTransactionWithQuery`, et une cinquantaine de membres : les fabriques
statiques `createPrevayler`, `createTransientPrevayler`, `createCheckpointPrevayler`, les `configure*` jamais appelés
(moniteur, horloge, réplication, seuils du journal, réseau, sérialiseurs Java), les constructeurs `(Throwable)` des
exceptions, `Cool.sleep`, `DeepCopier.deepCopy`.

**Morts par configuration**, confirmés à 0 % par JaCoCo :

| Partie | Pourquoi elle ne sert pas | Classes | Lignes |
|---|---|---|---|
| Réplication : `ClientPublisher`, `ServerConnection`, `ServerListener`, `POBox`, et ce qui reste de `network` (`OldNetwork`, `OldNetworkImpl`, `ObjectSocket`, `ObjectSocketImpl`, `ObjectServerSocket`, `ObjectServerSocketImpl`) | aucun `configureReplicationClient` ni `configureReplicationServer` | 10 | ~330 |
| Censeur strict : `StrictTransactionCensor`, `FoodTasterException`, `DeepCopier`, `DeepCopyFailedException`, `PrevalentSystemGuard.deepCopy` et `DeepCopyAction` | `configureTransactionFiltering(false)` ; la copie profonde ne sert qu'au censeur | 4 + membres | ~180 |
| Verrou `RWLock` et ses branches (`PrevalentSystemGuard`, `Capsule.Execute`, trois `Action`) | activé par la propriété `USE_RWLOCK`, que seul l'ancien `build.xml` (Ant) pose | 1 + 4 internes | ~200 |
| Journal en mémoire : `TransientJournal`, `configureTransientMode` | en mode mémoire, `SessionDirectory` prend `InMemoryRootDataManager` : `PRootDataManager` reçoit toujours `inMemory = false` | 1 | ~50 |

Au total, environ 1 700 des 3 579 lignes de code sont mortes. JaCoCo compte 65 classes sur 99 à 0 % (940 lignes) ; les
exceptions de la partie vivante (`PrevaylerIOException`, `ChunkHeaderCorruptedException`…) y figurent parce que la
suite ne provoque pas d'erreur d'écriture : elles restent.

**Tests du module.** Ils n'avaient **jamais tourné** : surefire 3.6 refuse JUnit 3.8.1 (« not supported anymore »).
Lancés à la main (`JUnitCore` de JUnit 4.12, classes compilées par `mvn test-compile`) : 63 tests, 2,5 s, un échec,
`MultiMemberGZIPTest` (depuis Java 16, le JDK écrit 255 au lieu de 0 dans l'octet « système » de l'en-tête gzip ; les
classes gzip sont mortes). `MainTest` et `FileIOTest` n'ont aucun test, `NetworkSuite` regroupe des tests déjà comptés.
Beaucoup de tests passent par `createTransientPrevayler` et le journal en mémoire : à lire avant de retirer celui-ci.

**Annexes** (~300 fichiers, ~2,7 Mo), dont rien n'est construit par Maven : builds Ant et Maven 1 (`build.xml`,
`maven.xml`, `project.xml`, `project.properties`, `*.incl`, `ant2html.xsl`, `continuous-integration.sh`, `sample.*`,
`log4j.xml`), notes de 2005 (`TODO`, `changelog-2.1.txt`, `ReleaseFileDescription.txt`), restes CVS et d'éditeur
(`.cvsignore`, `.init`, `.swp`), `contrib/` (121 fichiers : cluster, socketServer, facade…), `src/demo` (58),
`src/tutorial` (8), `xdocs/`, `dependencies/` (javadoc et `ant-contrib`), `root/lib` (14 jars et leurs licences, dont
`junit-3.8.1`, `log4j-1.2.13`, `xstream`). À garder : `root/docs/License.txt`, à remonter en `prevayler/LICENSE.txt`.

**Constats en marge.**
- `log4j` n'est dépendance de `prevayler` que pour `Log4jMonitor`, mort ; le jar reste dans `target/dist`, `globs`
  l'amène aussi et `bv_desktop`, `bv_shared` s'en servent.
- Le `pom` garde un bloc `<scm>` vide et l'adresse du site de 2005.
- `bv_desktop` a recopié `DurableOutputStream` dans `persistence/direct` : deux classes de même nom, l'une de
  Prevayler (pour `users/`), l'autre pour les données des comptes.
- `Stack`, `Vector`, `StringBuffer`, `Date` dans le code vivant : pour 3e et 3f.

**Lots proposés.**

| Lot | Contenu | Classes, lignes | Tests |
|---|---|---|---|
| 0. Faire tourner les tests | JUnit 3.8.1 → 4.12 dans le `pom` (comme `globs`) ; `MultiMemberGZIPTest` renommé `disabled_…` en attendant le lot B ; `log4j` reste jusqu'au lot B | — | `mvn test -pl prevayler` vert |
| A. Annexes | Les ~300 fichiers ci-dessus ; `License.txt` → `LICENSE.txt` | ~300 fichiers, ~2,7 Mo | aucun |
| B. Mort selon ProGuard | Les 32 classes et les membres morts ; la dépendance `log4j` sort du `pom` | 32 + membres, ~1 000 | tests de `network` (14 fichiers), `MultiMemberGZIPTest`, à lire |
| C. Mort par configuration | Réplication, censeur strict, `RWLock` ; l'appel `configureTransactionFiltering(false)` de `PRootDataManager` disparaît avec le réglage, le censeur libéral devenant le seul | 15 + membres, ~700 | `ReplicationTest`, `ConfusedFoodTasterStressTest`, `DeadKingDeepCopyTest`, `DeepCopierTest`, `RollbackTest`, à lire |
| D. Journal en mémoire (à décider) | `TransientJournal` et `configureTransientMode` : morts pour l'application, mais support de nombreux tests du module | 1, ~50 | tests en mode mémoire à basculer sur un dossier temporaire |

Chaque lot se vérifie aussi sur une copie d'un vrai dossier de données (`users/` de la 5.01) : connexion, création et
suppression d'un utilisateur, relance.

**Décisions à prendre.**
- **Lot D** : le garder coûte 50 lignes ; le retirer oblige à réécrire les tests du module sur disque. Recommandation :
  le garder.
- **Fondre `prevayler` dans `bv_desktop`**, comme `saxstack` dans `globs` ? Après B et C, il resterait ~40 classes et
  ~1 900 lignes, dont un seul client. Mais la licence est la BSD, pas l'Apache 2.0 de `globs` et (si la PR #2 est
  acceptée) de l'application : la fusion mélangerait deux licences dans un module. Recommandation : garder le module.

**Décision de Matthieu (2026-11-20)** : faire les lots 0 à C ; D et la fusion restent en suspens.

**Réalisation**, un commit par lot :
- Lot 0 (2026-11-20) : JUnit 3.8.1 → 4.12 et surefire fixé en 3.0.0, comme `globs` (le 3.6 hérité réclame le fournisseur
  JUnit Platform). `MultiMemberGZIPTest` désactivé et exclu dans le `pom` : une classe JUnit 3 sans test échoue
  (« No tests found »). `mvn test -pl prevayler` : 62 verts, 5 s.
- Lot A (2026-11-20) : 299 fichiers annexes supprimés (2,9 Mo sur disque) ; `root/docs/License.txt` devient
  `prevayler/LICENSE.txt`, [licence.md](licence.md) mis à jour (`contrib/transactionsFacade`, sous une autre BSD, est
  parti avec `contrib/`). Restent `pom.xml`, `LICENSE.txt`, `src/main`, `src/test`. Jar inchangé ; tests 62 verts.
- Lot B (2026-11-20) : 32 classes supprimées (20 de `network`, 4 de `gzip`, `Log4jMonitor`, `NullMonitor`, `FileLocker`,
  `AlreadyLockedException`, `SkaringaException`, `TransactionNotDeserializableError`, `SureTransactionWithQuery`,
  `NullSnapshotManager`) et une cinquantaine de membres : fabriques statiques, réglages de moniteur et de seuils du
  journal, sérialiseurs Java en argument, constructeurs d'exception, trois des quatre `Monitor.notify`. Dépendance `log4j`
  retirée du `pom` (le jar reste dans `target/dist`, amené par `globs`). Reste 71 classes, 2 508 lignes de code. Gardés
  pour les tests, qui vérifient du code vivant : `configureClock` (temps fixé), `DurableOutputStream.fileSyncCount` et
  `reallyClosed` (passés en visibilité paquet), `Cool.sleep` (jusqu'au lot C). Tests : les fabriques statiques remplacées
  par `TestPrevaylers` (tests) ; partis avec leur code les tests de `network` et de gzip, `PrevaylerFactoryTest`,
  `JournalFileRollingTest` (découpage du journal par taille, jamais réglé) et le refus de snapshot du mode transitoire.
  Tests de `prevayler` 62 → 34. Copie de la sauvegarde 5.01 ouverte par `Dump` (`PRootDataManager`) : utilisateur relu,
  `users/` inchangé.
- Lot C (2026-11-20) : réplication (`replication/`, `POBox`, les 6 dernières classes de `network`), censeur strict
  (`StrictTransactionCensor`, `FoodTasterException`, `DeepCopier`, `DeepCopyFailedException`, `PrevalentSystemGuard.deepCopy`)
  et `RWLock` (avec ses branches dans `PrevalentSystemGuard` et `Capsule`) supprimés : 15 classes, −1 223 lignes. Le
  censeur libéral est désormais le seul ; l'appel `configureTransactionFiltering(false)` de `PRootDataManager` disparaît
  avec le réglage, sans changer le comportement de l'application. `Cool.sleep` part avec ses derniers clients (tests).
  Tests partis avec leur code : `ReplicationTest`, `ConfusedFoodTasterStressTest`, `DeadKingDeepCopyTest`,
  `DeepCopierTest`, `RollbackTest` (le retour arrière d'une transaction en échec est l'œuvre du censeur strict), et les
  bouchons réseau. Reste **56 classes, 1 686 lignes de code** (3 579 au départ). Tests de `prevayler` 34 → 26. Données
  5.01 relues, `users/` inchangé.
- Restent en suspens : le lot D (journal en mémoire, gardé) et la fusion dans `bv_desktop` (écartée pour la licence).

## 2d, inventaire

Fait le 2026-11-20, sans rien modifier.

**Méthode.** ProGuard ne voit pas ce que `splits` interprète par nom : balises (`DefaultSplitterFactory`), attributs lus
explicitement (`getExcludedParameters`, `DEFAULT_EXCLUDES`, `properties.get…("…")`), formes des bordures
(`BorderParser`) et des icônes (`IconParser`). Chaque nom est cherché dans les 122 fichiers `.splits` (121 de
`bv_desktop`, 1 de `bv_analytics`), dans les fichiers de thème (`themes/*.properties`, d'où viennent les valeurs `${…}`)
et dans les chaînes Java de l'application. Le reste de l'API, appelé depuis Java, a été trié en 2a ; le rapport
ProGuard du jour n'y montre que les restes déjà examinés alors (accesseurs servant aux tests).

Le moteur est dans `globs`, sous `org.globsframework.gui.splits` : 138 classes, 7 790 lignes de code, 41 fichiers de
test. Tests de `globs` : 813 verts, 49 s.

**Le dossier `splits/`** n'est plus qu'une coquille : `pom.xml` (hors réacteur, et dépendant de `saxstack`, supprimé en
2b), `build.xml` (Ant), `LICENSE.txt` (Apache 2.0, comme `globs`) et deux pages de site (`www/`). Rien ne s'y construit.

**Balises jamais écrites** (34 balises connues, 29 utilisées) :

| Balise | Classe | Lignes | Tests qui s'en servent |
|---|---|---|---|
| `borderLayout` | `BorderLayoutComponent` | 58 | `SplitsComponentsTest` (3), `SplitsBuilderTest` (1, comme conteneur) |
| `footer` | `Footer`, et sa branche dans `RepeatSplitter` | 45 | `SplitsRepeatTest` (3) |
| `frame` | `FrameComponent` | 26 | `SplitsComponentsTest` (2), et comme racine dans `SplitsBuilderTest` (2, dont `testSizes`) et `SplitsLayoutTest` (1) ; démos |
| `editorPane` | (`JEditorPane` générique) | 3 | `SplitsComponentsTest` (1) |
| `progressBar` | (`JProgressBar` générique) | 3 | `SplitsComponentsTest` (1) |

`header`, sa jumelle, sert une fois : elle reste.

**Formes et attributs jamais écrits** :
- bordures `bevel(lowered)` et `bevel(raised)` (`BorderParserTest`, 1 test) ;
- icônes `circledArrow(…)`, `rect(…)`, `roundedRect(…)`, avec `CircledArrowIcon`, `RectIcon`, `RoundedRectIcon`
  (132 lignes), que seuls `IconParser`, `IconParserTest` (5 tests) et une démo citent ;
- attributs `horizontalUnitIncrement` (`ScrollPaneComponent`) et `useImageLocator` (`HtmlEditorPane`) : 2 sur 36
  attributs explicites. Les 120 autres attributs des `.splits` vont aux accesseurs Swing par réflexion
  (`PropertySetter`) : rien à retirer côté code.

**Démos** rangées dans `globs/src/test`, jamais lancées par la suite (des `main`) : `SplitsDemo`, `SplitsRepeatDemo`,
`ColorServiceEditorDemo`, `FontDemo`, `RoundedRectIconDemo`, `JStyledPanelDemo`, `ArrowButtonIconDemo`,
`ArrowIconDemo`, `DownloadIconDemo` (297 lignes), et `resources/demo/` (`login.xml`, `editor.xml`, lus par `SplitsDemo`).
1d avait déjà supprimé une démo de ce genre (`JRoundedPanelTest`).

**Lots proposés.**

| Lot | Contenu | Taille | Tests |
|---|---|---|---|
| A. Fichiers morts | Le dossier `splits/` (5 fichiers ; sa ligne disparaît de `CLAUDE.md` et de [licence.md](licence.md)) et les 9 démos avec `resources/demo/` | 5 fichiers, 9 classes, ~330 lignes | aucun |
| B. Balises | `borderLayout`, `footer`, `frame`, `editorPane`, `progressBar` sortent de `DefaultSplitterFactory`, avec leurs 3 classes et la branche `Footer` de `RepeatSplitter` | 3 classes, ~140 lignes | les tests de ces balises partent ; ceux qui prennent `frame` ou `borderLayout` comme simple conteneur passent sur `panel` |
| C. Formes et attributs | Les deux `bevel`, les trois icônes et leurs classes, les deux attributs | 3 classes, ~170 lignes | 6 tests de `IconParserTest` et `BorderParserTest` partent |

Chaque lot se vérifie par les tests de `globs` et la suite `bv_desktop` ; une balise ou une forme absente de tous les
`.splits` et des thèmes ne peut pas casser le chargement d'un écran, mais un coup d'œil à l'appli (`./run-dev.sh`)
reste de règle pour B et C.

**Décision à prendre : `frame`.** Elle ne sert pas à l'application, qui construit ses fenêtres en Java, mais trois tests
de `globs` l'utilisent comme racine (dont `testSizes`, qui vérifie les attributs de taille). Recommandation : la retirer
avec les autres et réécrire ces tests sur `panel`, qui a les mêmes attributs de taille.

**Décision de Matthieu (2026-11-20)** : faire les lots A à C, `frame` compris.

**Réalisation**, un commit par lot :
- Lot A (2026-11-20) : dossier `splits/` supprimé (5 fichiers ; `CLAUDE.md` et [licence.md](licence.md) disent
  désormais que `splits` vit dans `globs`), ainsi que les 9 démos et `resources/demo/` des tests de `globs`. Code livré
  inchangé ; tests de `globs` 813 verts.
- Lot B (2026-11-20) : balises `borderLayout`, `footer`, `frame`, `editorPane`, `progressBar` retirées de
  `DefaultSplitterFactory` et de `splits.xsd` (schéma livré dans le jar de `globs`, qui ne sert qu'à valider les extraits
  des tests), avec `BorderLayoutComponent`, `Footer`, `FrameComponent` et le pied de liste de `RepeatSplitter` et
  `RepeatPanel`. L'attribut `borderPos`, qui ne servait qu'à `borderLayout`, sort de `DEFAULT_EXCLUDES` et du schéma ; son
  unique occurrence, sans effet (`transactionView.splits`, un `scrollPane` dans une `column`), est retirée, sans quoi
  `PropertySetter` chercherait un `setBorderPos`. Tests : 8 partis avec leur balise ; `testCreatingAComponentWithAName`,
  `testSizes`, `testComplexContainment` et `testCanUseReferencedComponentsForColumnsAndRows` passent de `frame` ou
  `borderLayout` à `panel` ou `column` ; les deux tests d'en-tête et de pied en disposition `verticalGrid` gardent
  l'en-tête. Tests de `globs` 805 verts.
- Lot C (2026-11-20) : bordures `bevel(lowered)` et `bevel(raised)` (et leur mention dans le message d'erreur de
  `BorderParser`), icônes `circledArrow`, `rect`, `roundedRect` avec `CircledArrowIcon`, `RectIcon`, `RoundedRectIcon`,
  attributs `horizontalUnitIncrement` et `useImageLocator` supprimés, aussi dans `splits.xsd`. `useImageLocator` emportait
  un kit HTML qui allait chercher les images par `ImageLocator` (~90 lignes de `HtmlEditorPane`). −348 lignes de code,
  −101 de tests (9 tests ; `testScrollPane` ne vérifie plus que le pas vertical). `splits` : **132 classes, 7 361 lignes
  de code** (138 et 7 790 au départ). Tests de `globs` 796 verts.

## 3a, inventaire

Fait le 2026-11-20, sans rien modifier.

**Méthode.** Les `pom` ne demandent aucune alerte. Chaque module du réacteur est recompilé à part, sources principales
puis tests, par `javac --release 21 -Xlint:all -Xmaxwarns 1000000 -proc:none` sur son classpath Maven
(`dependency:build-classpath`), dans un dossier temporaire. Tout compile.

| Catégorie | globs | prevayler | siteweaver | bv_shared | bv_desktop | Total |
|---|---|---|---|---|---|---|
| `serial` | 138 / 2 | 1 / 0 | 2 / 0 | 2 / 0 | 502 / 1 | 648 |
| `rawtypes` | 185 / 93 | 25 / 4 | 2 / 26 | 0 | 57 / 15 | 407 |
| `deprecation` | 7 / 40 | 0 | 0 | 13 / 0 | 54 / 247 | 361 |
| `unchecked` | 124 / 57 | 9 / 1 | 0 / 13 | 0 | 22 / 88 | 314 |
| `this-escape` | 38 / 0 | 1 / 0 | 0 | 1 / 0 | 145 / 13 | 198 |
| `removal` | 6 / 6 | 4 / 1 | 0 / 1 | 0 | 6 / 0 | 24 |
| varargs (sans catégorie) | 0 | 0 | 0 | 0 | 11 / 9 | 20 |
| `dep-ann` | 0 | 0 | 0 | 1 / 0 | 9 / 1 | 11 |
| `static` | 0 / 1 | 0 | 0 | 0 | 1 / 4 | 6 |
| `fallthrough`, `cast`, `overloads` | 0 | 0 / 0 / 1 | 0 / 1 / 0 | 0 | 1 / 0 / 0 | 3 |

(sources principales / tests ; `bv_shared` n'a pas de tests.)

**À corriger, mécanique et sans effet sur le comportement.**
- `finalize` (5 méthodes : `FillPainter`, `GradientPainter`, `ReplicationGlobRepository` dans `globs`,
  `HyperlinkTableColumn`, `TextFilterPanel` dans `bv_desktop`) : chacune désinscrit l'objet d'un service
  (`ColorService`, repository) qui le tient par une référence forte (`HashSet`, `ArrayList`). L'objet ne devient donc
  récupérable qu'avec le service, et se désinscrire d'un service mort ne sert à rien : ces méthodes ne font rien
  d'utile. À supprimer, ce qui n'ouvre pas de fuite qui n'existe déjà.
- Constructeurs de types enveloppes promis à la suppression : `new Long(…)` (`PrevaylerDirectory`, 4, dans des
  `compareTo`), `new Double(0.00)` (`PlannedSeriesStatTrigger`, `SeriesChartsPanel`), `new Boolean` (`ArrayUtils`),
  `new Integer` (`XmlComparator` et 5 tests). Aucun n'est comparé par `==` : `valueOf`, ou `Long.compare`.
- Petites alertes : 1 transtypage redondant (`siteweaver`), 6 appels statiques faits sur une instance, 11 éléments
  marqués `@deprecated` en javadoc sans l'annotation (les champs anciens du modèle et `model/deprecated`, gardés pour
  l'upgrade : l'annotation ne change rien d'autre), `Class.newInstance()` (2, `globs`) →
  `getDeclaredConstructor().newInstance()` (les exceptions changent de forme : vérifier que les appelants attrapent
  large), `new Locale(…)` → `Locale.of` (2), `new URL` → `URI.toURL` (1 test).
- Varargs (20) : un `String...` repassé tel quel à `Lang.get(String, Object...)`. Java le prend bien comme tableau
  d'arguments, ce que veut le code ; un transtypage `(Object[])` fait taire l'alerte. Valeur faible.

**À traiter ailleurs.**
- `Runtime.exec(String)` (3 : `MacBrowsingService`, `UnixBrowsingService`, `WindowsBrowsingService`) : ces trois
  classes ouvrent le navigateur par la ligne de commande ; `java.awt.Desktop.browse` les remplace. Rattaché à **3c**.
- Masques `InputEvent` (`CTRL_MASK`… et `getModifiers()`, 17 alertes dans `Gui`, `GuiUtils`, `GlobTableView`,
  `StackChart`) : **pas mécanique**. Dans l'ancien système, `BUTTON3_MASK` vaut `META_MASK` (4) : sur Mac, un clic avec
  Cmd passe pour un clic droit dans `GlobTableView` et `GuiUtils`. Passer aux masques `*_DOWN_MASK` et à
  `getModifiersEx()` séparerait les deux, et uispec4j simule les clics avec ses propres modificateurs. Comportement à
  figer par un test avant tout changement : rattaché à **4a**. Au passage, `Gui` contient deux fois le même test de
  modificateurs (l. 188 et 235).
- Types bruts et conversions non vérifiées (~720, dont ~510 dans `globs`) : les supprimer revient à généraliser l'API
  de `globs`, une réécriture et non un remplacement. À revoir en **3b** si OpenRewrite en traite une partie sûre.
- `junit.framework.Assert` (268 alertes, tests de `globs` et `bv_desktop`) : déprécié par JUnit 4, toujours là en
  JUnit 4.13. Remplacer l'import par `org.junit.Assert` est mécanique, mais ne gagne rien tant que les tests restent en
  JUnit 3 (`TestCase`) ; à voir avec l'étape 5.
- Apache HttpClient 4 (13 alertes, `bv_shared/.../http/Http.java`) : utilisé seulement par le cloud et Budgea, coupés par
  `isServerEnabled()` et gardés par décision en 1b. À migrer vers `java.net.http` le jour où ils reviendraient.

**Bruit, à ne pas traiter.**
- `serial` (648) : sous-classes Swing et exceptions sans `serialVersionUID`. Rien ne les sérialise. Les seules classes
  `Serializable` qui comptent sont les transactions des utilisateurs (`persistence/prevayler/users`, 11) et `Capsule`
  de `prevayler` : leur ajouter un `serialVersionUID` changerait l'identifiant calculé aujourd'hui, donc risquerait la
  relecture des journaux de la 5.01. Ne jamais le faire sans le test de non-régression des données.
- `this-escape` (198) : constructeurs Swing qui appellent leurs propres méthodes ; alerte apparue en Java 21, sans
  défaut connu derrière.
- Champs `@deprecated` du modèle (`Project.SERIES`, `Transaction.CREATED_BY_SERIES`, `model/deprecated`…) : voulus, lus
  par `model/upgrade`.
- `fallthrough` de `SeriesWrapperStringifier` (le `case SUMMARY` tombe sur un `throw`, voulu) ; `overloads` de
  `Prevayler.execute` (API d'origine).

**Lots proposés.**

| Lot | Contenu | Taille | Risque |
|---|---|---|---|
| A. `removal` | 5 `finalize` supprimés, constructeurs enveloppes remplacés | 24 alertes, ~10 fichiers | nul : tests de `globs`, `prevayler` et `bv_desktop` |
| B. Petites alertes | `cast`, `static`, `dep-ann`, `Class.newInstance`, `Locale`, `URL`, varargs | ~45 alertes, ~30 fichiers | faible ; varargs facultatif |
| C. Garde-fou | Faire afficher par Maven les catégories utiles (`-Xlint:removal,cast,static,dep-ann,fallthrough,overloads,varargs`), les autres restant muettes | 5 `pom` | nul ; une nouvelle alerte se verrait au build |
| D. `junit.framework.Assert` | Import remplacé par `org.junit.Assert` dans les tests | 268 alertes, ~60 fichiers | nul, mais sans gain tant que les tests restent en JUnit 3 |

Recommandation : A, B et C ; D attend l'étape 5.

**Décision de Matthieu (2026-11-20)** : faire A, B et C.

**Réalisation**, un commit par lot :
- Lot A (2026-11-20) : 5 `finalize` supprimés ; `new Long` de `PrevaylerDirectory` → `Long.compare`, `new Double(0.00)`
  → `Double.valueOf(0.00)` (et non `0.00` : dans un ternaire avec un `Double`, un `double` nu ferait déballer l'autre
  branche, d'où une `NullPointerException` sur un montant nul), `new Integer`, `new Long`, `new Boolean` des tests →
  `valueOf`. `IntegerPool` de `XmlComparator`, cache d'`Integer` d'avant l'autoboxing, remplacé par `Integer.valueOf`
  (il sortait de son tableau dès 11 nœuds identiques sous un même parent). 0 alerte `removal`. Tests de `globs` 796,
  `prevayler` 26, `bv_desktop` 1 026 verts (12 min) ; données 5.01 relues. Tests de `siteweaver` lancés à la main
  (JUnitCore : JUnit 3.8.1, refusé par surefire, ils n'ont jamais tourné sous Maven) : 72, dont 10 échecs identiques
  avant et après (7 de `SiteUploaderTest`, 3 classes sans test).
- Lot B (2026-11-20) : 21 appels varargs reçoivent `(Object[])` (le même tableau qu'avant, sans l'alerte) ; 4 appels
  statiques faits sur une instance passent par la classe (les instances de `SerializableDeltaGlobSerializer`, classe
  sans état, disparaissent) ; 1 transtypage redondant retiré (`PageHandler`, qui garde son `instanceof`, vrai aussi
  contre `null`) ; `@Deprecated` ajouté aux 11 éléments marqués en javadoc (sans effet sur `GlobTypeLoader` : les
  annotations de classe ne sont pas lues, celles des champs sont cherchées par type) ; `Class.newInstance()` →
  `getDeclaredConstructor().newInstance()` (les 2 appelants attrapent `Exception`), `new Locale` → `Locale.of`,
  `new URL` → `URI.toURL`. Alertes : 0 `cast`, `static`, `dep-ann`, varargs ; restent 1 `fallthrough` et 1
  `overloads`, voulus. Tests de `globs` 796, `prevayler` 26, `bv_desktop` 1 026 verts (29 min, machine chargée par
  une appli de dev ouverte) ; `siteweaver` à la main inchangé.
- Lot C (2026-11-20) : les 5 `pom` du réacteur passent `<showWarnings>true</showWarnings>` et
  `-Xlint:cast,dep-ann,divzero,empty,fallthrough,finally,overloads,overrides,removal,static,varargs` (les quatre
  catégories hors inventaire étaient déjà à 0). Les deux alertes voulues sont rendues explicites : `throw` écrit dans le
  `case SUMMARY` de `SeriesWrapperStringifier` (même comportement), `@SuppressWarnings("overloads")` sur l'interface
  `Prevayler` (javac n'y signalait qu'une des deux paires ambiguës à la fois). `siteweaver` reçoit `<proc>none</proc>` :
  `jetty-maven-plugin`, curieusement en dépendance de compilation, amène par `sisu-inject-bean` un processeur
  d'annotations inutile qui se plaignait de `-source 21`. `mvn clean install` : **0 `[WARNING]`**. Tests de `globs` 796,
  `prevayler` 26 verts ; `bv_desktop` 1 026, 1 erreur de `SignpostSequenceTest` (instable ; 4 passages verts seul, avec
  et sans le lot), 9 min.

## 3b, inventaire

Fait le 2026-11-20, sans rien modifier dans l'arbre.

**Méthode.** `mvn org.openrewrite.maven:rewrite-maven-plugin:6.18.0:dryRun` avec
`-Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-migrate-java:3.20.0` et
`-Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava21`, à la racine (1 min). Le patch tombe dans
`target/rewrite/rewrite.patch` ; la liste des recettes par fichier n'est que dans la sortie de Maven. Le module racine
balaie tout le dépôt, modules hors build compris (`uispec4j`, `bv_server`, Android : 13 fichiers, laissés de côté).
Le patch a ensuite été appliqué dans une copie (`git worktree` hors de l'arbre) : il compile, mais les tests de `globs`
donnent 62 erreurs sur 796 et `ExportTest` échoue.

| Famille | Fichiers (code / tests) | Ce qu'elle fait | Verdict |
|---|---|---|---|
| `ListFirstAndLast` | 50 / 16 | `get(0)` → `getFirst()`, `get(size() - 1)` → `getLast()`, `remove(size() - 1)` → `removeLast()`, `add(0, x)` → `addFirst(x)` | **à rejeter** |
| `UseTextBlocks` | 2 / 61 | chaînes concaténées sur plusieurs lignes → blocs `"""` | à appliquer après correction de 2 fichiers |
| `InstanceOfPatternMatch` | 26 / 5 | `instanceof` suivi d'un transtypage → variable de motif | sûr |
| `SortedSet.first()`/`last()` → `getFirst()`/`getLast()` | 31 / 0 | même méthode sous le nom de Java 21 | sûr, gain nul |
| `@Serial` sur `serialVersionUID` | 16 / 6 (`prevayler`) | annotation de compilation, l'identifiant ne change pas | sûr |
| `SwitchCaseReturnsToSwitchExpression`, `…Assignments…` | 14 / 0 | `switch` à `return` → `return switch (…) { case X -> … }` | sûr |
| `IteratorNext` | 3 / 0 | `set.iterator().next()` → `set.getFirst()` sur des `SortedSet<Integer>` | sûr |
| `RemoveExtraSemicolons` | 2 / 1 | `;;` et `;` après une méthode | sûr |
| `ReplaceMathRandomWithThreadLocalRandom` | 4 / 0 | `Math.random()` → `ThreadLocalRandom` (générateurs de données, `Utils.randomInt`) | inutile |
| Version des plugins | 6 `pom` | `maven-compiler-plugin` 3.13.0 → 3.16.0, `jacoco` 0.8.13 → 0.8.16 | à part, facultatif |
| `maven.compiler.release` dans `budgetview/pom.xml` | 1 `pom` | propriété redondante avec la configuration des modules | inutile |
| `ReplaceDeprecatedRuntimeExecMethods` | 1 (`WindowsBrowsingService`) | `exec(cmd)` → `exec(cmd.split(" "))`, découpage plus pauvre que celui du JDK | non : le code part en **3c** |

**Les deux familles fautives.**
- `ListFirstAndLast` : `GlobList` hérite d'`ArrayList` et définit depuis toujours `getFirst()` et `getLast()`, qui
  **rendent `null`** sur une liste vide. Depuis Java 21, ces méthodes redéfinissent celles de `List`, qui lèvent
  `NoSuchElementException`. La recette ne le sait pas : 1) dans `GlobList.getFirst()` même, `return get(0)` devient
  `return getFirst()`, qui s'appelle sans fin (62 `StackOverflowError` dans les tests de `globs`) ; 2) sur toute
  `GlobList`, un `get(0)` qui levait une exception sur liste vide rendrait `null`. Gain purement cosmétique : rejet.
  À retenir hors de cet audit : tout appel à `getFirst()` sur une `List<Glob>` qui est en fait une `GlobList` suit le
  contrat de `GlobList`, pas celui du JDK.
- `UseTextBlocks` : le compilateur efface les blancs de fin de ligne d'un bloc de texte. La recette les protège par
  `\s` quand ce sont des espaces suivis d'autre chose, mais pas une **tabulation finale** (`ExportTest.testTSVExport`
  échoue : `"To categorize\t\n"` devient `"To categorize\n"`) ni une **ligne faite de seuls espaces**
  (`"    \n"` de `HtmlUtilsTest` devient `"\n"` : le test passe toujours, avec une entrée changée). Fichiers concernés :
  `ExportTest`, `HtmlUtilsTest` ; `ColorServiceEditorTest` et `CsvImportTest` signalés par `git apply` mais justes
  (lignes vides à l'origine). À corriger à la main (`\t`, `\s`) avant de commiter. Les `\t` du milieu de ligne
  deviennent de vraies tabulations, invisibles à la relecture : les garder en `\t`.

**Ce que la recette ne fait pas.** Les types bruts et conversions non vérifiées (~720 alertes en 3a) : aucune recette
de `UpgradeToJava21` n'y touche. Pas de piste sûre ici ; ils restent hors audit.

**Lots proposés.** Chacun s'applique par `rewrite:run` avec ses seules recettes (fichier `rewrite.yml` hors de l'arbre),
pas en découpant le patch : 13 fichiers relèvent de plusieurs familles.

| Lot | Contenu | Taille | Risque |
|---|---|---|---|
| A. Code livré | `instanceof` à motif, `switch` en expression, `IteratorNext`, `SortedSet.getFirst/getLast`, `@Serial`, points-virgules | ~75 fichiers | faible : chaque cas relu, tests de `globs`, `prevayler`, `bv_desktop`, données 5.01 relues (`@Serial`) |
| B. Blocs de texte | `UseTextBlocks`, 2 fichiers corrigés à la main, `\t` gardés | 63 fichiers, ~2 500 lignes, presque tout des tests | faible : les tests se vérifient eux-mêmes, sauf ce qui passe quel que soit le texte (cas `HtmlUtilsTest`) |
| C. Plugins | `maven-compiler-plugin` 3.16.0, `jacoco` 0.8.16 | 6 `pom` | nul ; garder 0 `[WARNING]` |
| D. Rejeté | `ListFirstAndLast`, `Math.random`, propriété de `budgetview/pom.xml`, `Runtime.exec` (→ 3c) | — | — |

Recommandation : A et C ; B si la lisibilité des tests vaut un diff de 2 500 lignes (à mon sens oui pour les tableaux
attendus des tests fonctionnels, mais c'est le gain le plus faible des trois).

**Décision de Matthieu (2026-11-20)** : faire A et C.

**Réalisation**, un commit par lot :
- Lot A (2026-11-20) : `rewrite:run` avec un `rewrite.yml` hors de l'arbre, qui reprend les 8 recettes du lot (dont deux
  `ChangeMethodName` pour `SortedSet.first()`/`last()`). `-Drewrite.exclusions` n'arrête pas le module racine : 4 fichiers
  hors build (Android, `bv_server`, `uispec4j`) ont été modifiés quand même, puis remis en état à la main. 102 fichiers,
  +441 −568 lignes : 52 `getFirst()`/`getLast()`, tous sur des `SortedSet` (aucune `GlobList`), 14 `switch` en
  expression, ~45 `instanceof` à motif, 22 `@Serial`, 5 points-virgules. Tests de `globs` 796, `prevayler` 26,
  `bv_desktop` 1 026 verts, 9 min ; données 5.01 relues, `users/` identique. `mvn -o clean install` : 0 `[WARNING]`.
- Lot C (2026-11-20) : `maven-compiler-plugin` 3.13.0 → 3.16.0 (6 `pom`), `jacoco-maven-plugin` 0.8.13 → 0.8.16, et
  `docs/audit/coverage.sh` aligné (il fixe sa propre version de l'outil `jacococli`). `mvn -o clean install` : 0
  `[WARNING]`. Profil `coverage` vérifié sur `LoginTest` : l'agent écrit `jacoco.exec`, le rapport se génère. Tests de
  `globs` 796, `prevayler` 26 verts ; `bv_desktop` 1 026, 1 erreur de `SignpostSequenceTest` (instable, verte relancée
  seule, deux fois), 9 min.

## 3c, inventaire

Fait le 2026-11-20, sans rien modifier dans l'arbre.

**Menus et fichiers macOS.** `MacOSXHooks` (222 lignes) a été réécrit au passage à Java 17 (`befc644d7`) : en Java 9 et
plus, il passe déjà par `java.awt.Desktop` (`setAboutHandler`, `setPreferencesHandler`, `setQuitHandler`,
`setOpenFileHandler`). Mais tout est fait **par réflexion**, à travers un `InvocationHandler` commun, pour compiler en
cible Java 8, et un repli `com.apple.eawt` (gestionnaires, `FullScreenUtilities`) reste derrière un test
`isJava9OrLater()`. Depuis le passage en `--release 21`, la réflexion ne sert plus et le repli ne peut plus s'exécuter
(`com.apple.eawt` n'est plus exporté par `java.desktop`). Réécriture directe : quatre lambdas, plus de proxy ni de
`equals`/`hashCode` à la main, ~100 lignes de moins. Comportement inchangé, dont l'annulation de la demande de sortie
si `ExitAction` revient. La propriété `apple.awt.fullscreenable` (bouton vert) reste : savoir si elle sert encore
relève de 4a.

`PicsouMacLookAndFeel` (39 lignes) : seule `initApplicationName()` est appelée (`Application`, `DevDesktopApp`). Le
reste est mort depuis que `Gui.init` a commenté `UIManager.setLookAndFeel(new PicsouMacLookAndFeel())` (la classe
n'étend même plus `AquaLookAndFeel`). La propriété `com.apple.mrj.application.apple.menu.about.name` date du Java 6
d'Apple ; seule `apple.awt.application.name` est lue par le JDK actuel. Proposition : passer `initApplicationName` dans
`MacOSXHooks`, à côté de `useScreenMenuBar`, et supprimer la classe et la ligne commentée de `Gui`.

**Navigateur.** `BrowsingService` choisit parmi `MacBrowsingService` (`open <url>`), `WindowsBrowsingService`
(`rundll32 url.dll,FileProtocolHandler <url>`) et `UnixBrowsingService` (`firefox <url>`), tous par
`Runtime.exec(String)`, déprécié en Java 18 (relevé en 3a et 3b). `DummyBrowsingService` sert aux tests
(`BrowsingChecker`). `Desktop.browse(URI)` couvre les trois systèmes. Appelants : aide (13 URL `https://`), site de la
banque (122 URL `http(s)://` dans `banks/`, 11 vides, désactivées), lien d'un élément de projet et banques créées par
l'utilisateur (saisie libre), licences de l'écran « À propos ». Différences :
- une adresse saisie sans schéma (`www.exemple.fr`) : `open` la prend pour un fichier et échoue sans rien dire ;
  `Desktop.browse` échoue aussi. Ajouter `https://` serait une correction de comportement : notée pour après l'audit ;
- une adresse qui n'est pas une URI (espace…) : `exec` la coupait en morceaux, `new URI` la refuse. Dans les deux cas,
  rien ne s'ouvre. L'échec sera seulement journalisé : une exception levée dans l'IHM lance le contrôle des données
  (`AwtExceptionHandler`), et `open` échouait déjà sans rien dire. La clé `browsing.error` devient inutile ;
- sous Linux, `Desktop` demande les bibliothèques GTK, présentes sur un bureau courant, là où l'ancien code exigeait
  Firefox.

**Lots proposés.**

| Lot | Contenu | Taille | Risque |
|---|---|---|---|
| A. Hooks macOS | `MacOSXHooks` sans réflexion ni `com.apple.eawt`, `PicsouMacLookAndFeel` supprimé | 2 classes, ~−130 lignes | faible : à vérifier dans l'appli (À propos, Préférences, Quitter par ⌘Q, plein écran, ouverture d'un fichier par le Finder sur le `.app`) |
| B. Navigateur | `BrowsingService` concret sur `Desktop.browse`, 3 classes supprimées, `DummyBrowsingService` gardé | 4 classes, ~−40 lignes | faible : tests du navigateur, puis un lien d'aide ouvert dans l'appli |

**Décision de Matthieu (2026-11-20)** : faire A et B.

**Réalisation**, un commit par lot :
- Lot A (2026-11-20) : `MacOSXHooks` appelle directement `Desktop` (quatre lambdas), sans proxy ni repli
  `com.apple.eawt` ; `initApplicationName` y est passée et `PicsouMacLookAndFeel` est supprimé, avec la propriété
  `com.apple.mrj…` et la ligne commentée de `Gui.init`. 5 fichiers, +56 −202 lignes. Tests de `bv_desktop` 1 026 verts,
  11 min ; vérifié par Matthieu dans l'appli de dev : À propos, Préférences, plein écran, ⌘Q.
- Lot B (2026-11-20) : `BrowsingService` devient une classe concrète sur `Desktop.browse` ; `Mac`, `Windows` et
  `UnixBrowsingService` supprimés, avec la clé `browsing.error` (3 fichiers de langue). Un échec est journalisé
  (`[Browsing]` dans le log), sans exception. 7 fichiers, +24 −67 lignes. Tests de `bv_desktop` 1 026 verts, 19 min
  (11 pour le lot A : même variabilité qu'en 1f) ; liens d'aide et de « À propos » vérifiés par Matthieu dans l'appli
  de dev. Le lien vers le site d'une banque n'a pas pu l'être (pas de banque à site dans les données de dev) : même
  appel que l'aide.

## 3d, inventaire

Fait le 2026-11-21, sans rien modifier dans l'arbre. Comptes d'appels qualifiés (`Utils.x(`) et imports statiques, dans le
code livré (`globs`, `prevayler`, `siteweaver`, `bv_shared`, `bv_desktop`), les tests, et `bv_server`/`bv_analytics`
(hors build, mais qui doivent compiler).

**Découverte hors sujet, à traiter en premier : les balises `Utils.beginRemove()` / `Utils.endRemove()`.** Ce ne sont pas
des utilitaires : ces deux méthodes vides marquent du code réservé au développement, que le `JarShrinker` retirait du jar
livré (`FilterWriter`, commit `3f076953a` de 2008 : il ne recopiait pas les instructions situées entre les deux appels). Le
shrinker a été supprimé le 2026-11-15 (`4ee7db9b8`) sans que ce rôle soit relevé : **depuis la 6.0.1, ce code s'exécute
dans l'application livrée**, dont `BudgetView 6.app`. Treize blocs (sept relevés d'abord, six de plus en faisant le lot 0) :

| Fichier | Code « dev » qui tourne désormais en production | Effet pour l'utilisateur |
|---|---|---|
| `MenuBarBuilder` | `menuBar.add(createDevMenu())` | menu **`[Dev]`** visible : lever une exception, injecter une erreur de compte, changer la date, dump, activer/désactiver les extensions… |
| `Gui.useMacOSMenu` | `result = false` | sur Mac, Préférences et Quitter apparaissent aussi dans le menu Fichier (en plus du menu de l'application) |
| `ParametersPane` | choix de 0, 1, 2, 3, 4, 6 mois futurs | la 5.01 ne proposait que 12, 18, 24, 36, 48, 60 |
| `InitialRepoIdTransaction` | `random = new Random()` au lieu de `SecureRandom` | identifiant de dépôt tiré par un générateur non cryptographique (sans usage local connu, mais à rétablir) |
| `Application.main` | `changeDate()` | lit la propriété `budgetview.today` (date forcée) : sans effet si elle n'est pas fixée |
| `ImportCloudBankSelectionPanel` | banque de test Budgea visible | synchro coupée en usage local (`isServerEnabled`) : sans effet |
| `LicenseConstants` (`bv_shared`) | `RETRY_PERIOD = 500` | licence supprimée en 1a : sans effet ; les tests de `bv_server` fixent eux-mêmes cette valeur |
| `DefaultGlobRepository`, `ReplicationGlobRepository` (`globs`) | `removeChangeListener` lève une `RuntimeException` si le listener est inconnu | une erreur de programmation ignorée par la 5.01 devient une exception dans l'IHM |
| `LicenseConstants` (×2), `CloudConstants`, `BudgeaConstants`, `MobileConstants` (`bv_shared`) | adresse du serveur surchargeable par propriété système | serveurs coupés en usage local : sans effet |

Les tests reposent sur ces comportements : 91 appels à `setFutureMonthsCount(2)`, 45 à 6, 38 à 1, 3 ou 4 ; menu `[Dev]`
par `OperationChecker` (`DevOptionsChecker`, `LoggedInFunctionalTestCase`, `SeriesShapeTest`) ; Préférences cherchées
dans le menu Fichier. On ne peut donc pas simplement supprimer les blocs : il faut un interrupteur lu à l'exécution,
comme `isServerEnabled()`, faux par défaut et mis par `run-dev.sh` et `FunctionalTestCase`. Point à vérifier en le
faisant : un utilisateur qui a choisi 1 à 6 mois futurs en 6.0.1 garde cette valeur, absente de la liste de la 5.01.

**`Utils` (416 lignes), `Strings` (261), `Files` (264), dans `org.globsframework.utils`.**

Remplaçables par le JDK à comportement égal :

| Maison | JDK | Appels (livré / tests / serveur) |
|---|---|---|
| `Utils.copyOf` (« code from java 1.6 ») | `Arrays.copyOf` | 2 / 0 / 0 (`Transaction`) |
| `Strings.repeat` | `String.repeat` (Java 11) | 1 / 1 / 3 |
| `Strings.LINE_SEPARATOR` | `System.lineSeparator()` | 84 usages : garder la constante, l'initialiser par le JDK |
| `Utils.equal` | `Objects.equals` | **133 / 12 / 10**, dont 3 imports statiques ; seule différence théorique : un objet dont `equals` répond faux à lui-même |
| `InvertedComparator` | `Comparator.reversed()` | 3 appels (`SeriesOrderManager`, `TransactionView`) |
| `CompositeComparator` | `thenComparing` | 3 appels (`GlobTableView`) |
| `Runnables.NO_OP` | `() -> {}` | 2 fichiers |
| `Files.copyStream`, `copyFile`, `loadStreamToString` (corps) | `transferTo` (Java 9), `java.nio.file.Files.copy` avec `REPLACE_EXISTING` | appels inchangés, seul le corps change |

`Files` laisse aussi des flux ouverts (`loadProperties` ×2, `loadPropertyKeys`, `copyFile` en cas d'erreur) : un
`try`-avec-ressources les ferme sans changer ce que rendent les méthodes. `Files` n'a aucun test : il faut d'abord
figer son comportement (règle des étapes 2 à 4).

Doublons internes : `Utils.box` = `Utils.toObjectIntegers` ; `Utils.joinedList` = `Utils.list(first, other…)` ;
`Utils.toString(Throwable)` = `Strings.toString(Throwable)` ; `Utils.capitalize` et `Utils.toNiceUpperCase` = ceux de
`Strings` (le second ne sert qu'à `Utils.toEnum`).

Morts partout, `bv_server` compris : `Utils.capitalize`, `defaultIfNull`, `dumpStack`, `remove`, `set` (×2), `sum`.
Réservés aux tests : `Utils.append`, `box`, `join` (×3, plus 1 appel serveur), `max`, `randomInt`, `toArray` ;
`Strings.toUpperCase(String[])`, `leftAlign` et `toUpperCaseLabel` (serveur aussi) ; `Files.copyResourceToTmpFile`
(16), `copyStreamTofile`, `dumpStringToFile` (20, serveur 3), `loadPropertyKeys`. À laisser en place : ce sont des aides
de test d'un framework, les déplacer ne simplifie rien.

À garder, sans équivalent exact dans le JDK :
- `Strings.isNullOrEmpty` / `isNotEmpty` (89 et 66 appels) : aucun test de vide sûr pour `null` dans le JDK ;
  `isBlank` ne compte pas les blancs comme vides ici.
- `Strings.join(String…)` saute les éléments vides, `String.join` non.
- `Utils.compare` (nuls en premier) : `Comparator.nullsFirst(naturalOrder())` est plus long à l'appel.
- `Utils.compareIgnoreCase` passe par `toLowerCase()` (locale par défaut), pas `CASE_INSENSITIVE_ORDER` : résultats
  différents sur quelques caractères.
- `Files.loadFileToString(File)` réécrit les fins de ligne en `LINE_SEPARATOR` et ajoute un saut final : ce n'est pas
  `Files.readString`.
- `Strings.unaccent` ne traite que les voyelles françaises (ni `ç`, ni `ñ`, ni `œ`) ; `Normalizer` changerait le
  filtrage des listes (`GlobMatchers`, `GlobListViewFilter`) et l'import CSV. Correction de comportement : après l'audit.
- `Functor` (`run() throws Exception`), `Ref`, `Pair`, `MultiMap`, `MapOfMaps`, `Range`, `Unset` : pas d'équivalent.

**Reste du chantier 1a.** `RandomStrings` (105 lignes, copie de `commons-lang3`) ne sert qu'à tirer
`UserPreferences.PASSWORD_FOR_MOBILE` (`PicsouInit`, `UpgradeTrigger`), pour l'appli mobile supprimée. Le champ reste
(modèle), le tirage et la classe peuvent partir.

**Lots proposés.**

| Lot | Contenu | Taille | Risque |
|---|---|---|---|
| 0. Balises « dev » | `beginRemove`/`endRemove` remplacés par `if (Application.isDevMode())` (propriété `budgetview.dev`, fausse par défaut, mise par `run-dev.sh` et `FunctionalTestCase`) ; `SecureRandom` rétabli sans condition ; bloc de `LicenseConstants` supprimé ; les deux méthodes vides retirées de `Utils` | 7 fichiers, ~40 lignes (13 en fin de compte) | moyen : comportement visible changé (retour à celui de la 5.01) ; à vérifier dans l'appli empaquetée |
| A. JDK direct | `copyOf`, `repeat`, `LINE_SEPARATOR`, comparateurs, `NO_OP` ; tests de `Files`, puis son corps modernisé | ~10 fichiers, 3 classes en moins | faible |
| B. Doublons et morts | doublons fusionnés, 7 méthodes mortes, `RandomStrings` et tirage du mot de passe mobile | ~8 fichiers, ~−200 lignes | faible |
| C. `Utils.equal` → `Objects.equals` | remplacement mécanique, `bv_server` compris | ~90 fichiers, 155 appels | faible, mais diff volumineux (`git blame`) |

**Décision de Matthieu (2026-11-21)** : faire les quatre lots, le lot 0 avec l'interrupteur `budgetview.dev` (retour au
comportement de la 5.01 dans l'application livrée).

**Réalisation**, un commit par lot :
- Lot 0 (2026-11-21) : `Application.isDevMode()` (propriété `budgetview.dev`) commande le menu `[Dev]`, les 1 à 6 mois
  futurs, les menus Mac et la date forcée de `Application.main` ; banque de test Budgea aussi. `run-dev.sh`,
  `FunctionalTestCase` et `DevDesktopApp` l'activent. `SecureRandom` rétabli sans condition. Les deux contrôles de
  `removeChangeListener` de `globs`, qui ne peut pas lire `isDevMode`, deviennent des `assert` (actifs sous surefire ;
  `-ea` ajouté à `run-dev.sh`). Les cinq surcharges d'adresse de serveur de `bv_shared` restent, sans condition (serveurs
  coupés). Méthodes `beginRemove`/`endRemove` supprimées. Un utilisateur resté sur 1 à 6 mois futurs voit la liste des
  Préférences sans sélection, sa valeur est gardée (`GlobComboEditor`). 18 fichiers, +53 −86 lignes. Tests de `globs`
  796, `bv_desktop` 1 026 verts, 4 min 51 s ; 0 `[WARNING]`, `bv_server` compile. Vérifié par Matthieu dans le jar
  livré lancé sans mode dev : ni menu `[Dev]`, ni Préférences/Quitter dans Fichier, mois futurs de 12 à 60.
- Lot A (2026-11-21) : `Arrays.copyOf`, `String.repeat`, `System.lineSeparator()`, `Comparator.reversed()` et
  `thenComparing` (aucun comparateur nul possible : `GlobViewModel` trie déjà avec le comparateur initial), lambda vide ;
  `Runnables`, `InvertedComparator`, `CompositeComparator` et `Utils.copyOf` supprimés. `FilesTest` (12 tests) fige
  d'abord le comportement de `Files`, défauts compris, puis ses méthodes passent en `try`-avec-ressources et `transferTo` :
  quatre flux qui restaient ouverts sont fermés. Tests de `globs` 808 verts (+12), `bv_desktop` 1 026 verts, 4 min 43 s ;
  0 `[WARNING]`, `bv_server` compile.
- Lot B (2026-11-21) : `Utils.box`, `joinedList`, `toString(Throwable)`, `capitalize`, `toNiceUpperCase` fusionnés dans
  leurs doubles (`toObjectIntegers`, `list`, `Strings`), 7 méthodes mortes supprimées ; `RandomStrings` et
  `initMobilePassword` aussi. Ce dernier créait au passage les préférences de l'utilisateur (`findOrCreate`) : ses deux
  appels sont remplacés par ce seul `findOrCreate`. Les nouveaux utilisateurs n'ont plus de mot de passe mobile (`null`,
  que la sérialisation accepte) ; le champ reste dans le modèle. 10 fichiers, +11 −217 lignes. Tests de `globs` 808 verts ;
  `bv_desktop` 1 026, 1 erreur « No such child » de `TransactionFilteringTest` (course d'uispec4j, verte relancée seule
  deux fois), 4 min 56 s ; 0 `[WARNING]`, `bv_server` compile.
- Lot C (2026-11-21) : `Utils.equal` → `Objects.equals`, 174 appels dans 79 fichiers (dont 3 imports statiques, et 6
  fichiers de `bv_server`), par script ; la méthode est supprimée. Les fichiers en CRLF le restent (`newline=''`). Un
  import statique de `Objects.equals` ne marcherait pas : la méthode d'instance `equals(Object)` le masque, d'où
  `Objects.equals(` en toutes lettres. Tests de `globs` 808 verts ; `bv_desktop` 1 026, 1 erreur « No such child » de
  `SavingsCategorizationTest` (verte relancée seule deux fois), 5 min 02 s ; 0 `[WARNING]`, `bv_server` compile.

**Bilan.** −3 classes et ~25 méthodes de `globs`, −1 classe de `bv_desktop` (`RandomStrings`) ; `FilesTest` ajouté. La
course d'uispec4j « No such child » est sortie à deux suites sur quatre (lots B et C, deux classes différentes), toujours
verte seule : elle reste à traiter avec les tests instables (voir 5c).

## 3e, collections synchronisées

Fait le 2026-11-21. Les 5 fichiers du point de départ étaient ceux du code livré de `bv_desktop`, `bv_shared` et `globs` ;
compté sur tous les modules, sources de test comprises : 19 fichiers, 41 `StringBuffer`, 1 `Vector`, 0 `Hashtable`.
Aucun de ces objets n'est partagé entre threads : variables locales, champ d'un parseur SAX (`SaxStackParser`), ou
système de test des tests de `prevayler`, que Prevayler sérialise par sa propre stratégie (`StringBuilder` est lui aussi
`Serializable`). `Matcher.appendReplacement` (`Utils.replace`) accepte un `StringBuilder` depuis Java 9. Le seul `Vector`
alimentait un `DefaultComboBoxModel` de `ColorServiceEditor` (éditeur de couleurs de développement) : un tableau suffit.
Laissé : `SmtpMessage` de `bv_server`, copie de la bibliothèque Dumbster (Apache 2.0), à garder telle quelle.

Un seul lot, 18 fichiers, +25 −26 lignes. Tests de `globs` 808, `prevayler` 26 verts ; `siteweaver` à la main (JUnitCore,
JUnit 4.12) 69, dont les 7 échecs anciens de `SiteUploaderTest` (5d) ; `bv_desktop` 1 026 verts, 4 min 50 s ; 0 `[WARNING]`,
`bv_server` compile.

## 3f, inventaire

Fait le 2026-11-21. Le chiffre de départ (35) ne comptait que les imports explicites : avec les `import java.util.*`, le
code livré compte **97 fichiers** qui citent `Date`, `Calendar`, `SimpleDateFormat`, `DateFormat` ou `TimeZone` :
`bv_desktop` 49, `globs` 36, `prevayler` 12, `bv_shared` 0.

**Représentation des dates.** Le modèle ne stocke presque que des entiers : mois `yyyymm`, jour, date complète `yyyymmdd`
(`Month.toFullDate`), calculés à la main dans `Month`. Seuls 10 champs sont des `DateField` (`java.util.Date`) :
`Account.POSITION_DATE`, `OPEN_DATE`, `CLOSED_DATE`, `RealAccount.POSITION_DATE`, `TransactionImport.IMPORT_DATE`,
`AccountPositionError.UPDATE_DATE`, `StandardMessage.DATE`, `CloudDesktopUser.SUBSCRIPTION_END_DATE`, et
`DashboardStat.TOTAL_*_DATE` (calculés). globs les sérialise en millisecondes (`DefaultSerializationOutput.writeDate`) dans
les snapshots et journaux. Aucun `TimeStampField` dans le modèle.

**Ce qui reste tel quel (~65 fichiers).**
- `globs`, API des champs (28 fichiers : `FieldValue`, `Key`, `GlobBuilder`, `*FieldKey`, visiteurs, sérialisation) : ils
  transmettent `Date` sans calcul. Passer `DateField` à `LocalDate` changerait le format des données sur disque.
- `prevayler` (12) et les transactions `persistence/prevayler/users` (11) : `executeOn(Object, Date)` est l'API de Prevayler.
- Les lectures de `DateField` dans les triggers, les comparateurs et les contrôles (`AccountInitialPositionTrigger`,
  `SavingsBudgetStatTrigger`, `DeferredCardDayTrigger`, `UpgradeTrigger`, `Account`, `AccountCheck`, `TransactionMatchers`,
  `AutomaticAccountComparator`, `BankPluginService`…) : ils comparent des `Date` ou les convertissent par `Month.getMonthId`
  et `getDay`. Ils suivront `Month` sans changer.
- `DatePicker` : `JXDatePicker` (SwingX) exige un `java.text.DateFormat` et des `java.util.Date`. `Formatting.getDateFormat`
  reste donc un `SimpleDateFormat`.

**Pièges relevés.**
- *Fuseau horaire.* Une `Date` est un instant : `Month.toDate` la crée à minuit, heure locale, et `getMonthId`/`getDay` la
  relisent dans le fuseau courant. Toute conversion vers `LocalDate` doit passer par `ZoneId.systemDefault()` pour garder ce
  comportement (y compris le décalage d'un jour si le fuseau a changé entre l'écriture et la lecture).
- *Analyse souple.* Aucun `setLenient(false)` : `SimpleDateFormat` accepte « 31/02 » (→ 3 mars) et ignore ce qui suit une
  date valide. `DateTimeFormatter` en mode par défaut (`SMART`) ramène « 31/02 » au 28 ou 29 février, refuse « 32/01 » et le
  texte en trop. Trois comportements différents pour un fichier de banque mal formé.
- *Années à deux chiffres.* `SimpleDateFormat` place `yy` entre 80 ans avant et 20 ans après la création du format ;
  `DateTimeFormatter` dans 2000-2099. Les formats d'import `yy/MM/dd` et `dd/MM/yy` (`DateFormatAnalyzer`) en dépendent.
- *Dates sans année.* `AbstractTransactionTypeFinalizer.getDate` reconnaît une date du libellé sans année par l'année 1970
  que lui donne `SimpleDateFormat`, puis la remplace par celle de l'opération bancaire. Avec `java.time`, il faudrait un
  `parseDefaulting` explicite.
- *Partage entre threads.* `SimpleDateFormat` n'est pas sûr entre threads. Formats statiques partagés :
  `OfxImporter.DATE_FORMAT` (réutilisé par `OfxExporter`), `Dates.DEFAULT_*`, `Formats.DEFAULT_*`, `FieldConverter`,
  `JsonGlobFormat`, `BackupAction`, `PicsouDescriptionService.LOCAL_TIME_STAMP`, et le `Calendar` statique de `Month`
  (protégé par `synchronized`).
- *`hh` au lieu de `HH`* dans `Formats.DEFAULT_TIMESTAMP_FORMAT` et `Formatting.dateAndTimeFormat` (heures de 1 à 12, sans
  AM/PM). Défaut latent : ces formats ne servent qu'aux `TimeStampField`, absents du modèle.

**Lots proposés.**
- **A. Calcul interne** : `Month` (`Calendar` statique → `LocalDate`/`YearMonth` : `toDate`, `getMonthId`, `getDay`,
  `addDays`, `getLastDayNumber`, `createCalendar`), `DayGenerator`, `Formatting.getFullMonthLabel` (`GregorianCalendar`),
  `DateFormatAnalyzer` (année de référence), `CurrentMonth`. Même API d'entiers et de `Date` ; tests de `MonthTest` en garde.
- **B. Formats d'affichage et de sérialisation texte** : `Dates`, `Formats`, `FieldConverter`, `JsonGlobFormat`,
  `BackupAction`, `RestoreSnapshotAction`, `PicsouDescriptionService.LOCAL_TIME_STAMP`, `OfxExporter`,
  `DirectAccountDataManager`, `DumpStackTracesAction` → `DateTimeFormatter` (immuable, sûr entre threads), `hh` → `HH`.
  `Formats` prend des `SimpleDateFormat` dans son constructeur (API de globs) : à garder, ou à changer avec ses appelants.
  Les formats texte de `FieldConverter` et `JsonGlobFormat` (XML et JSON) doivent rester identiques au caractère près.
- **C. Analyse des imports** (`OfxImporter`, `ImportSession`, `*TransactionFinalizer`, `ImportedTransactionDateRenderer`) :
  **ne pas faire** sans décision. Le gain est faible (des formats locaux, non partagés, sauf `OfxImporter.DATE_FORMAT`, réglé
  en B) et les trois pièges ci-dessus changent ce qu'accepte l'import. Seule mesure proposée : rendre
  `OfxImporter.DATE_FORMAT` privé à chaque analyse.
- `TimeService` (`Date` statique, date forcée des tests et du menu `[Dev]`) : son API reste en `Date`, ses appelants (15 fichiers)
  aussi ; un passage à `LocalDate` serait un chantier à part, sans gain fonctionnel.

## 3f, ce qui a été fait

Le 2026-11-21, en un lot, limité à ce dont le gain est net et le risque mesurable :
- **`Month`** : le `Calendar` statique et le `DecimalFormat("00")` partagés sous `synchronized` sont remplacés par
  `java.time` (`toDate`, `getMonthId`, `getDay`, `addDays`, `getLastDayNumber`) et une petite fonction `twoDigits`.
  Même API (entiers et `Date`). Le comportement souple du `Calendar` est reproduit par addition (`1er janvier + (mois − 1)
  mois + (jour − 1) jours`) : un jour 0 ou 31 avril déborde sur le mois voisin au lieu de lever une exception. Fuseau :
  `ZoneId.systemDefault()` (l'ancien `Calendar` figeait celui du chargement de la classe ; différence seulement si le
  fuseau change en cours d'exécution). Le cache `monthIdToLastDay` et `createCalendar` (seul appelant : `DayGenerator`)
  sont supprimés. Garde : `MonthTest.testSameResultsAsLenientCalendar` compare au `Calendar` d'origine 1995-2040, jours
  −1 à 33, à Paris, São Paulo (heure d'été à minuit jusqu'en 2019) et UTC.
- **`hh` → `HH`** dans `Formats.DEFAULT_TIMESTAMP_FORMAT` (globs) et `Formatting.dateAndTimeFormat` : défaut latent, ces
  formats ne servent qu'aux `TimeStampField`, absents du modèle.
- **`OfxImporter.DATE_FORMAT`** (statique partagé avec `OfxExporter`) devient le motif `DATE_PATTERN`, et chaque usage crée
  son `SimpleDateFormat` : ce qu'accepte l'import ne change pas.
- `Formatting.toString(year, month)` : `GregorianCalendar` → `Month.toDate`.

Laissés, faute de gain à la hauteur du risque : lot B (formats d'affichage et de sérialisation, dont l'API `Formats` de
globs ; les formats statiques ne servent en pratique que sur le fil de Swing), lot C (analyse des imports, trois pièges de
comportement), `TimeService`, `DateFormatAnalyzer`, et les `DateField` (format sur disque).

## 4a et 4b, inventaire

Fait le 2026-11-21, sur le code livré (`bv_desktop`, `bv_shared`, `globs`, `prevayler`), avec l'origine de chaque site par
`git blame -w -C -C` et par les messages de commit (`git log -i -E --grep='mac|jdk|java ?[0-9]|swing|linux|windows|vista|
contourn|workaround|hack|blocage'`, 109 commits, dont une vingtaine touchent encore du code présent).

**Relevé brut.** Tests de plateforme : 21 sites dans 11 fichiers, tous par `GuiUtils.isMacOSX()` & co (aucun `os.name` ni
`java.version` lu ailleurs, hors `AboutDialog` qui les affiche). `UIManager.put` : 3, tous légitimes. `System.setProperty` :
`apple.*` (2, voulus), `sun.awt.exception.handler` (1). `Thread.sleep` : 5. `invokeLater`/`invokeAndWait` : 34, dont 12 dans
`MainWindow`. `catch` vides : 25. Minuteries Swing : 3 (animations de `TimeViewPanel`, `ProgressPanel`, panneau cloud).

**Morts sous Java 21, sans essai à faire (lot A).**
- *Versions de Windows* (`f4a2d5648`, 2013, « fix App dir for vista, win 7/8 » ; `bbe9eb355`, 2009) : `GuiUtils.IS_XP`,
  `IS_VISTA`, `IS_WIN7`, `IS_WIN8` et leurs branches dans `AppPaths.getDefaultDataPath`. Java 21 ne tourne que sur Windows 10
  et plus ; sur ces versions `AppData\Local` existe toujours, donc la troisième vérification d'existence répond avant elles,
  et la dernière branche rend de toute façon le même chemin que celle de Vista. Les vérifications d'existence (anciennes
  installations dans `Application Data`) restent.
- *`sun.awt.exception.handler`* (`47410cc95`, 2009) : la propriété n'est plus lue depuis Java 7, le relais par
  `setDefaultUncaughtExceptionHandler` posé en 6.00 la remplace. Le `setProperty` et le commentaire sur `jsmooth` (fichier
  absent du dépôt) sont à retirer.
- *`PicsouLinuxLookAndFeel`* : aucune référence hors d'elle-même (`Gui.init` prend `PicsouWindowsLookAndFeel` pour tout ce
  qui n'est pas macOS). Gardée jusqu'ici par les règles `keep` des look and feel.

**Dépréciations à corriger sans changer le comportement (lot B, renvoyé ici par 3a).** `InputEvent.CTRL_MASK` & co dans
`Gui.isAddModifier`, `Gui.hasModifiers`, `GuiUtils.isRightClick`, `GlobTableView.selectRowOnRightClickIfNeeded`.
- `Gui.hasModifiers(ActionEvent)` : `ActionEvent.CTRL_MASK`, `SHIFT_MASK`… (mêmes valeurs, non dépréciées). Sans risque.
- Les trois autres lisent `MouseEvent.getModifiers()`, dont les anciens masques se recouvrent : `META_MASK` = `BUTTON3_MASK`,
  `ALT_MASK` = `BUTTON2_MASK`. Un clic droit compte donc comme « Meta » dans `isAddModifier` (sélection additive de
  `StackChart`). Passer à `getModifiersEx()` et `*_DOWN_MASK` changerait cela, et aussi le relâchement (le bouton n'est plus
  dans `getModifiersEx()` au `mouseReleased`). **Défaut latent** : figer d'abord le comportement par des tests, ou laisser
  ces trois-là en l'état avec un `@SuppressWarnings("deprecation")` commenté.

**Contournements à essayer dans l'appli (lot C, sur macOS, avec Matthieu pour les gestes).**
- *Dialogue d'import quand la fenêtre est réduite* (`e421a6066`, 2008, « blocage à l'ouverture de boîte de dialogue
  lorsque l'application est iconifiée ») : `ImportDialog.show` ouvre un `JDialog` modal vide qu'un `invokeLater` qui se
  relance (avec `Thread.sleep(200)` sur le fil de l'IHM) referme aussitôt, puis montre le vrai dialogue à la restauration.
  Essai : réduire la fenêtre, ouvrir un `.ofx` depuis le Finder (« Ouvrir avec » ou dépôt sur l'icône du Dock).
- *`Thread.sleep(50)` avant `openFiles`* dans `ImportController` (`c86763fc3`, 2008, « fin open with ») : même essai, fenêtre
  d'import ouverte à l'étape 1.
- *Bulles d'aide fantômes après Cmd-Tab* (`f0ec4602a`, 2011, « Correction tooltips phantomes sur alt-tab sous Mac ») :
  `PicsouFrame` et `PicsouDialog` coupent le `ToolTipManager` à la perte du focus. Essai : survoler un bouton à bulle, Cmd-Tab
  vers une autre appli, regarder si la bulle reste à l'écran.
- *Hauteur réduite de 1 ou 2 pixels sous macOS* dans `CustomButtonUI`, `FlatButtonUI`, `FlatIconButtonUI` (`height -= 2`) et
  `SlidingToggleUI` (`-= 1`), venus avec ces classes (2013-2014), sans raison donnée. Probablement l'anneau de focus ou la
  ligne de base d'Aqua. Essai : captures avant / après des boutons de l'accueil, des projets et des bascules.
- *`GuiUtils.IS_OPEN_JDK`* (`0c001940f`, 2009) : `PicsouWindowsLookAndFeel` (Windows et Linux seulement) pose
  `Button.margin` sauf sous OpenJDK. Le JDK 21 de Homebrew et le runtime de jpackage s'annoncent « OpenJDK 64-Bit Server VM »,
  comme Temurin : la marge n'est plus jamais posée. Impossible à essayer ici (pas de Windows ni de Linux). Proposé : retirer
  le test et la marge, ce qui fige le comportement actuel des JDK courants.

**Conventions de plateforme, à garder.** Ordre Annuler / OK (`PicsouDialog`, 2008) et marge des boutons, Cmd au lieu de Ctrl
(`getCtrlModifier`, `6408375a9`), dossier de données macOS, barre de menus en haut de l'écran, Aqua sous macOS, taille de
police des tableaux par système (`Gui`, 2008).

**Légitimes, à laisser.**
- `invokeLater` : presque tous passent du fil de connexion à celui de l'IHM (`MainWindow.LoginFunctor`), avec une vraie
  contrainte notée dans le code (« il faut attendre que le main soit terminé », `f87edde7c`, 2009). Un seul imbriqué (pile
  d'erreur affichée après le message), sans effet.
- `Thread.sleep(2000)` d'`Application` avant de sortir quand une autre instance a reçu les fichiers ; `Thread.sleep(100)` de
  `SingleApplicationInstanceListener` après des `accept` en échec rapprochés ; `Thread.sleep(5000)` de `PersistentJournal`
  entre deux essais d'écriture : temporisations voulues.
- `catch` vides : `InterruptedException` des attentes ci-dessus, port libre (`SingleApplicationInstanceListener`), fichiers
  absents (`DirectAccountDataManager`), ressource de langue manquante (`DefaultDescriptionService`), fin de flux
  (`DurableInputStream`). Un seul masque un défaut possible : `SplitTransactionDialog.EnterKeyValidator` avale toute exception
  de l'action « Ajouter » (`927c6cf87`, 2008). À relever pour le chantier des tests unitaires, pas à changer dans l'audit.
- Déjà retirés par des chantiers précédents : la « protection exit MAC » (`33e86e14d`, 2011, `MRJAdapter`, remplacé en 3c),
  le contournement `AquaLabelUI` de `GlobSliderEditor` (`90d6d53fa`, classe supprimée en 2a).

**Lots proposés.** A (code mort, ~60 lignes), B (`hasModifiers` seul, ou les quatre avec tests qui figent le comportement),
C (cinq essais dans l'appli, un commit par contournement retiré).

## 5a, inventaire

Fait le 2026-11-21. Rien de modifié. Chaque test désactivé a été réactivé à part (renommé, lancé par
`-Dtest=Classe#méthode`, puis remis), sauf `SingleInstanceTest`, qui ouvre les vrais ports 5454… et enverrait ses requêtes à
BudgetView 6 si elle tourne.

**Périmètre** : 22 méthodes dans `bv_desktop` (dont 2 préfixées `DISABLED_`, de l'auteur, absentes de
[etat-des-tests.md](etat-des-tests.md) : `TimeViewPanelUISpecTest.testMouseMove`, `UndoRedoTest.testUndoRedoMaintainsSelection`,
ce dernier de 2008), 1 dans `globs`, 2 tests commentés dans `globs` (`SerializationTest.testBigWrite` / `testBigRead`, mesures
de débit sans assertion). Une exclusion périmée : `MySqlExceptionTest` dans le `pom` de `globs`, parti dans `bv_server` en 2a.
Hors périmètre : `bv_server` (`SqlExceptionTest`, hors build) et le fork `uispec4j`.

**Réactivés, résultat** : 14 lancés, 1 passe (`SeriesUpgradeTest`, déjà connu comme instable), 11 échecs, 2 erreurs.

| Tests | Résultat réactivé | Nature | Proposition |
|---|---|---|---|
| `DashboardTest` ×2, `SeriesEditionTest.testClosedAccount` | `fail("…")` seul | pense-bête de l'auteur, aucun test | **supprimer** ; les 3 idées dans [idees-ameliorations.md](idees-ameliorations.md) |
| `SeriesEditionTest.testUnknownPeriodicity` | `fail` en tête | l'auteur conteste le comportement testé | **supprimer** |
| `UndoRedoTest.testUndoRedoMaintainsSelection` | mois sélectionné non rétabli par l'annulation | fonction commencée en 2008 (« début prise en compte sélections »), jamais finie | **supprimer** |
| `UrlTest` ×2 (classe exclue) | non lancé | vérifie en ligne des URL de budgetview.fr, mort | **supprimer** la classe et l'exclusion |
| `JsonImporterTest` (classe exclue) | erreur de lecture du JSON ; la dernière attente est vide | `JsonImporter` ne sert qu'à la synchro Budgea : sans compte venu de la synchro, il rejette tout fichier (`OperationCancelled`) | **supprimer** la classe et l'exclusion ; `JsonImporter` lui-même, mort par configuration, à traiter à part |
| `LaPosteTest.testDeferred` | montants de signe inversé | repose sur le plugin La Poste, laissé débranché exprès | **supprimer**, avec `laposteCBDiffere.ofx` (seul usage) |
| `DeferredTest.testCategorization` | la partie catégorisation passe, les attentes suivantes non | test en chantier chez l'auteur (2015) | **réparer** : garder la partie qui passe, qui couvre la correction du filtre `ORIGINAL_ACCOUNT` |
| `TimeViewPanelUISpecTest.testMouseMove` | « Invalid number of items » | sélection de mois au glisser | **examiner** : attente périmée → réparer ; sinon supprimer |
| `RestartTest.testChangeDay…`, `ImportTest.testImportAndChangeDate`, `ProjectUpgradeTest.…FromJar125` | échecs attendus | **bugs connus de l'appli** | **garder désactivés** : les corriger change le comportement, ce qui attend le chantier des tests unitaires |
| `AnalysisBudgetViewStackChartTest.testPopupMenus`, `SeriesUpgradeTest.…`, `SingleInstanceTest` ×6 | erreur d'uispec4j ; instable (vert ce jour) ; non lancé | limites de l'outil de test | **garder désactivés**, avec les courses d'uispec4j |
| `GlobTableViewTest.testRefreshUpdatesTheSortingAndKeepsSelection` | — | comportement réel gardé exprès (voir [etat-des-tests.md](etat-des-tests.md)) | **garder** |
| `SerializationTest` (2 commentés), exclusion `MySqlExceptionTest` | — | restes | **supprimer** |

Trois lots proposés : **A** suppressions (9 méthodes, 2 classes, 1 fichier de test, 2 exclusions, 2 tests commentés) ;
**B** `DeferredTest` et `TimeViewPanelUISpecTest` ; **C** mise à jour d'[etat-des-tests.md](etat-des-tests.md) (les 12 gardés,
avec leur raison).

## 5c, durée

Fait les 2026-11-20 et 21.

**Variabilité (9 à 21 min).** Les classes ordinaires durent la même chose d'une suite à l'autre (±1 s), mais chaque
suite lente compte une ou deux classes qui « calent » : `SetPasswordTest` 80 s au lieu de 2 s, `AnalysisSeriesGroupViewTest`
476 s au lieu de 2. Cause : la **veille du Mac**, sur batterie, quand personne ne touche le clavier (`pmset -g log`,
« Entering Sleep state due to 'Idle Sleep' ») ; la JVM gelée fait durer la classe en cours le temps de la sieste. Veilles
de 77 s et de 580 s, pour des calages de 78 s et de 555 s. Remède : `caffeinate -i mvn test …`. Il ne protège pas d'une
veille capot fermé (« Clamshell Sleep » : une suite lancée à 22 h 23 a fini 8 h 57 plus tard).

**Parallélisme.** Une JVM par classe (`reuseForks=false`) reste indispensable (fuites, voir `CLAUDE.md`), mais rien
n'empêchait d'en lancer plusieurs à la fois, hormis le dossier `tmp/` partagé : `FunctionalTestCase` le vide au démarrage
et les tests y gardent `tmp/test_prevayler`. Chaque JVM a désormais le sien (`workingDirectory` =
`target/forks/${surefire.forkNumber}`) ; aucun test ne lit de fichier par un chemin relatif au module, JaCoCo écrit par
chemin absolu. `forkCount` passe par la propriété `test.forks` : un `<forkCount>` écrit en dur dans le `pom` l'emporte
sur `-DforkCount` en ligne de commande (premier essai faussé ainsi).

| JVM | Durée | Somme des durées par classe | Résultat |
|---|---|---|---|
| 1 | 9 min 16 s (sans veille) | 560 s | vert ; 1 course « No such child » passagère sur un autre essai |
| 3 | **5 min 08 s** | 800 s | vert |
| 4 | 5 min 48 s | 1 239 s | 3 échecs, verts relancés seuls : 2 « No window was shown » (délai de 30 s), 1 « No such child » |

Machine : 8 cœurs dont 4 de performance, 16 Go, batterie à 6 % en charge pendant les essais à 3 et 4 JVM. Retenu : 3.

**Classes les plus lentes.** En série, sans veille : `SavingsTest` 14 s (38 tests), `SeriesEditionTest` 12 s (51),
`CategorizationTest` 11 s (42), `LoginTest` 12 s (20), `RestartTest` 11 s (15) : 0,3 à 0,8 s par test, comme le reste de
la suite. Rien d'injustifié ; leur seule réduction passerait par moins de tests (5b).

**Restant, hors 5c.** Les courses d'uispec4j (« No such child », fenêtre non interceptée) se voient plus souvent sous la
charge : à traiter avec les tests instables (`SignpostSequenceTest`), au chantier des tests unitaires.

## Journal

| Date | Chantier | Résultat (classes, taille du jar, tests) |
|---|---|---|
| 2026-11-16 | Point de départ | 1 112 classes de premier niveau (`bv_desktop` et `bv_shared`, dont 1 069 accessibles depuis `DesktopApp`) ; 2 374 `.class` dans `bv_desktop/target/classes` ; `target/dist` 12,3 Mo, `budgetview.jar` 5,2 Mo |
| 2026-11-16 | 1a, inventaire | 51 classes et 5 734 lignes mortes par construction ; 10 classes du deuxième cercle à examiner |
| 2026-11-16 | Suite de référence | 1 034 tests, 0 échec, 165 classes de test, 11 min |
| 2026-11-16 | 1a, lots 1 à 4a | 1 070 classes de premier niveau (−42), 2 302 `.class` (−72) ; `target/dist` 12,2 Mo, `budgetview.jar` 5,07 Mo (−110 Ko) ; 1 028 tests verts (−6, supprimés avec leur code), 163 classes, 14 min ; −4 056 lignes de Java net, −348 lignes de langue (3 fichiers). Reste 13 classes mortes par construction (licence : 9, cloud : 4) |
| 2026-11-16 | 1a, lot 5 (licence) | 1 045 classes de premier niveau (−25), 2 226 `.class` (−76) ; `target/dist` 12,1 Mo, `budgetview.jar` 4,95 Mo (−117 Ko) ; 1 028 tests verts, 163 classes, 9 min ; −4 492 lignes de Java net, −299 lignes de langue. Reste 4 classes mortes par construction, toutes du cloud (1b) |
| 2026-11-16 | 1c, couverture de la suite | bv_desktop + bv_shared : 103 classes de premier niveau à 0 % sur 1 004 (2 732 lignes sur 47 253) ; 17 candidates à la suppression (373 lignes). globs 164/468, prevayler 52/80, saxstack 22/32 à 0 %. 1 028 tests, 1 erreur instable, 11 min sous JaCoCo |
| 2026-11-16 | 1c, 17 classes supprimées | 1 028 classes de premier niveau (−17), 2 186 `.class` (−40) ; `target/dist` 12,0 Mo, `budgetview.jar` 4,90 Mo (−50 Ko) ; 1 028 tests verts, 163 classes, 9 min ; −917 lignes de Java net, −71 lignes de `.splits`, −37 lignes de langue (3 fichiers) |
| 2026-11-16 | 1d, 21 classes supprimées | `reach.py` corrigé (classes sans arête). 1 007 classes de premier niveau (−21), 2 162 `.class` (−24) ; `target/dist` 12,0 Mo, `budgetview.jar` 4,87 Mo (−33 Ko) ; 1 028 tests verts, 163 classes, 9 min ; −1 150 lignes de Java (dont la démo `JRoundedPanelTest`) |
| 2026-11-16 | 1d, tests du dessin | `TimeUtils` et `@Inline` supprimés : 1 004 classes de premier niveau (−3), 2 160 `.class` ; `target/dist` 12,0 Mo, `budgetview.jar` 4,87 Mo ; 1 026 tests verts (−2, ceux de `TimeUtils`), 162 classes, 11 min sous JaCoCo. Classes à 0 % : bv_desktop 46 sur 933, bv_shared 13 sur 32, toutes gardées par décision |
| 2026-11-17 | 1e, inventaire ProGuard | Rien de modifié. bv_desktop + bv_shared : 64 classes entières inutilisées (31 de premier niveau, 33 internes), dont 4 que `reach.py` ne voyait pas ; 724 membres isolés, dont 58 sans aucune autre occurrence et 211 faux positifs (constantes recopiées). globs 293 classes et 640 membres, prevayler 32 et 86, saxstack 34 et 56. Six lots proposés (A à F), ~175 membres |
| 2026-11-18 | 1e, lots A à F | 992 classes de premier niveau (−12), 2 140 `.class` (−20) ; `target/dist` 12,0 Mo, `budgetview.jar` 4,84 Mo (−34 Ko) ; 1 026 tests verts, 162 classes, 9 min ; −1 148 lignes de Java net, −12 lignes de langue (3 fichiers), 2 fichiers d'aide et 5 icônes |
| 2026-11-18 | 1f, inventaire | Rien de modifié. Orphelins : 61 images sur 164 (332 Ko), 12 `.splits` sur 133 (314 lignes), 131 clés de langue sur 1 486, 1 définition de banque, 4 fichiers divers (124 Ko avec la banque). 330 clés à préfixe dynamique non tranchées. Un défaut de continuation dans `lang_en.properties`. Quatre lots proposés (A à D) |
| 2026-11-18 | 1f, lots A à D | Pas de Java touché : 992 classes de premier niveau, 2 140 `.class` ; `target/dist` 12 Mo, `budgetview.jar` 4,55 Mo (−290 Ko) ; 1 026 tests verts à chaque lot, 162 classes, 9 à 21 min (durée très variable d'une suite à l'autre, sans échec). 63 images (338 Ko), 12 `.splits` (314 lignes), 142 clés de langue (−428 lignes, 3 fichiers), 5 fichiers divers (124 Ko) |
| 2026-11-19 | 2a, inventaire | Rien de modifié. `globs` : 608 fichiers, 43 000 lignes ; 163 classes de premier niveau inutilisées (8 465 lignes, 231 Ko du jar), dont tout `sqlstreams` (70 classes, ~3 500 lignes), que seul `bv_server` utilise ; 638 membres isolés, dont 53 sans aucune autre occurrence. Cinq lots proposés (A à E), décision attendue sur `sqlstreams` |
| 2026-11-19 | 2a, lot A (IHM de `globs`) | `globs` : −16 classes, −854 lignes de code, −728 de tests, −709 d'exemples (dont 9 fichiers de `samples`) ; `globs-1.0.jar` 1 209 Ko (−27 Ko), `target/dist` 12 Mo. Tests de `globs` 833 verts (−40), 54 s ; `bv_desktop` 1 026 verts, 16 min |
| 2026-11-19 | 2a, lot B (`remote`) | `globs` : −7 classes, −667 lignes avec le test ; `globs-1.0.jar` 1 187 Ko (−22 Ko). Tests de `globs` 832 verts (−1) ; `bv_desktop` 1 026 verts, 14 min |
| 2026-11-20 | 2a, lot C (divers) | `globs` : −23 classes, −1 048 lignes de code (dont 4 méthodes), −467 de tests ; 2 outils XML (270 lignes) passés dans les tests ; `globs-1.0.jar` 1 142 Ko (−45 Ko). Tests de `globs` 821 verts (−11) ; `bv_desktop` 1 026 verts, 10 min |
| 2026-11-20 | 2a, lot D (`sqlstreams`) | Rien de supprimé : 111 classes (5 426 lignes) et 11 fichiers de test (681 lignes) sortis de `globs`, 110 vers `bv_server`, 1 vers `bv_analytics` ; `hsqldb` et `mysql-connector-java` retirés du `pom` de `globs`. `globs-1.0.jar` 982 Ko (−160 Ko), `target/dist` 11 Mo. Tests de `globs` 794 verts (−27, partis avec leur code ; 18 relancés à part dans `bv_server`, verts) ; `bv_desktop` 1 026 verts, 9 min |
| 2026-11-20 | 2a, lot E (membres) | `globs` : 50 membres morts, la sérialisation des changesets et 7 utilitaires de test supprimés (−804 lignes de code, −68 de tests) ; 3 classes passées dans les tests. `globs` 446 fichiers, 33 800 lignes ; `globs-1.0.jar` 952 Ko (−30 Ko), `target/dist` 11 Mo. Tests de `globs` 787 verts (−7) ; `bv_desktop` 1 026 verts après E1 et après E3, 9 min. **2a terminé** |
| 2026-11-20 | 2b, inventaire | Rien de modifié. `saxstack` : 37 classes, 2 047 lignes de code, 44 tests (jamais lancés jusque-là, verts) ; `saxstack-2.0.jar` 87 Ko. Vivantes 16 classes (735 lignes) ; mortes 21 (1 203 lignes), dont 12 tenues par les outils de test de `globs` (`XmlComparator`) et 8 sans client. `xercesImpl` en dépendance de compilation de `globs` pour un seul outil de test. Remplacement par le JDK écarté ; quatre lots proposés (A à D), décision attendue sur la fusion dans `globs` |
| 2026-11-20 | 2b, lot A (code mort) | `saxstack` : −7 classes, −450 lignes de code, −919 de tests ; `saxstack-2.0.jar` 41 Ko (−10 Ko). Tests de `saxstack` 26 verts (−18), `globs` 787 verts, `bv_desktop` 1 026 verts, 11 min |
| 2026-11-20 | 2b, lot B (outils de test) | 13 classes (~810 lignes) et 2 classes de test sorties de `saxstack` vers `globs/src/test` ; `saxstack` 17 classes, 608 lignes ; `saxstack-2.0.jar` 17 Ko (−24 Ko). Tests de `saxstack` 5, `globs` 808 verts ; `bv_desktop` 1 026, 1 erreur de `SignpostSequenceTest` déjà connue comme instable (verte relancée seule, deux fois), 13 min |
| 2026-11-20 | 2b, lot C (Xerces) | `xercesImpl` 2.11 et `xml-apis` retirés des dépendances de `globs` et de `bv_desktop` ; `target/dist` inchangé (11 Mo, 18 jars : ils en étaient déjà exclus). Tests de `globs` 808 verts, `bv_desktop` 1 026 verts, 14 min |
| 2026-11-20 | 2b, lot D (fusion dans `globs`) | Module `saxstack` supprimé : 17 classes (608 lignes) et 2 classes de test passées dans `globs`, 13 fichiers de site et de `pom` retirés. `globs-1.0.jar` 967 Ko (+15 Ko), `target/dist` 11 Mo, 17 jars (−1). Tests de `globs` 813 verts ; `bv_desktop` 1 026, 1 erreur de `SignpostSequenceTest` (instable, verte relancée seule, deux fois), 13 min ; `bv_server` compile. **2b terminé** |
| 2026-11-20 | 2c, inventaire | Rien de modifié. `prevayler` : 103 classes, 3 579 lignes de code, `prevayler-1.0.jar` 121 Ko ; ~1 700 lignes mortes, dont 32 classes selon ProGuard et ~15 par configuration (réplication, censeur strict, `RWLock`), confirmées à 0 % par JaCoCo (65 classes sur 99) ; ~300 fichiers annexes (~2,7 Mo). Tests du module jamais lancés (JUnit 3.8.1 refusé par surefire) : 63 à la main, 1 échec (gzip, mort). Format sur disque non concerné. `bv_desktop` sous JaCoCo : 1 026 verts, 12 min. Cinq lots proposés (0, A à D) |
| 2026-11-20 | 2c, lot 0 (tests du module) | JUnit 4.12, surefire 3.0.0 : tests de `prevayler` lancés par Maven pour la première fois, 62 verts (1 désactivé, gzip), 5 s. Rien de changé hors des tests |
| 2026-11-20 | 2c, lot A (annexes) | 299 fichiers hors build supprimés (2,9 Mo : builds Ant et Maven 1, `contrib/`, démos, jars et javadoc embarqués) ; licence BSD gardée en `LICENSE.txt`. `prevayler-1.0.jar` inchangé (121 Ko), tests de `prevayler` 62 verts |
| 2026-11-20 | 2c, lot B (mort selon ProGuard) | `prevayler` : −32 classes, ~50 membres, −1 761 lignes de code, −1 926 de tests ; 71 classes, 2 508 lignes de code ; `prevayler-1.0.jar` 90 Ko (−31 Ko), `target/dist` 11 Mo ; `log4j` retiré du `pom`. Tests de `prevayler` 34 verts (−28, partis avec leur code), `bv_desktop` 1 026 verts, 9 min ; données 5.01 relues |
| 2026-11-20 | 2c, lot C (mort par configuration) | `prevayler` : −15 classes (réplication, censeur strict, `RWLock`), −1 223 lignes de code, −662 de tests ; 56 classes, 1 686 lignes de code ; `prevayler-1.0.jar` 60 Ko (−30 Ko), `target/dist` 11 Mo. Tests de `prevayler` 26 verts (−8), `bv_desktop` 1 026 verts, 9 min ; données 5.01 relues. **2c terminé** (lot D gardé) |
| 2026-11-20 | 2d, inventaire | Rien de modifié. `splits` (dans `globs`) : 138 classes, 7 790 lignes de code ; 5 balises sur 34 jamais écrites, 2 bordures, 3 icônes et 2 attributs explicites sur 36 jamais utilisés (~310 lignes, 6 classes) ; dossier `splits/` réduit à une coquille (5 fichiers) ; 9 démos (297 lignes) dans les tests de `globs`. Tests de `globs` 813 verts, 49 s. Trois lots proposés (A à C), décision attendue sur `frame` |
| 2026-11-20 | 2d, lot A (fichiers morts) | Dossier `splits/` (5 fichiers) et 9 démos de `globs/src/test` (297 lignes, plus 2 fichiers XML) supprimés ; code livré inchangé. Tests de `globs` 813 verts |
| 2026-11-20 | 2d, lot B (balises) | 5 balises, 3 classes et le pied de liste des répétitions supprimés (−184 lignes de Java, −195 de tests), schéma `splits.xsd` −58 lignes ; `globs-1.0.jar` 962 Ko (−5 Ko). Tests de `globs` 805 verts (−8) ; `bv_desktop` 1 026 verts, 9 min |
| 2026-11-20 | 2d, lot C (formes et attributs) | 2 bordures, 3 icônes (3 classes), 2 attributs supprimés (−348 lignes de code, −101 de tests) ; `splits` 132 classes, 7 361 lignes ; `globs-1.0.jar` 952 Ko (−10 Ko), `target/dist` 11 Mo. Tests de `globs` 796 verts (−9) ; `bv_desktop` 1 026 verts, 9 min. **2d terminé, étape 2 terminée** |
| 2026-11-20 | 3a, inventaire | Rien de modifié. `-Xlint:all` sur les 5 modules du réacteur : ~2 000 alertes (sources principales 1 369, tests 623), dont `serial` 648, types bruts et `unchecked` 721, dépréciations 361 (268 pour `junit.framework.Assert`), `this-escape` 198, `removal` 24 (5 `finalize` inopérants). ~70 corrections mécaniques ; `Runtime.exec` renvoyé à 3c, masques `InputEvent` à 4a. Quatre lots proposés (A à D) |
| 2026-11-20 | 3a, lot A (`removal`) | 5 `finalize` et 1 classe de test supprimés, 14 constructeurs enveloppes remplacés (−37 lignes) ; 0 alerte `removal` (24 avant). Tests de `globs` 796, `prevayler` 26, `bv_desktop` 1 026 verts, 12 min ; données 5.01 relues |
| 2026-11-20 | 3a, lot B (petites alertes) | ~40 corrections dans 36 fichiers, sans changement de comportement ; 0 alerte `cast`, `static`, `dep-ann`, varargs (38 avant). Tests de `globs` 796, `prevayler` 26, `bv_desktop` 1 026 verts, 29 min (appli de dev ouverte en parallèle) |
| 2026-11-20 | 3a, lot C (garde-fou) | 5 `pom` : alertes utiles affichées au build ; 2 alertes voulues rendues explicites, processeur d'annotations coupé dans `siteweaver`. `mvn clean install` sans aucun `[WARNING]` ; `target/dist` 11 Mo, 17 jars. Tests de `globs` 796, `prevayler` 26 verts ; `bv_desktop` 1 026, 1 erreur de `SignpostSequenceTest` (instable), 9 min. **3a terminé** |
| 2026-11-20 | 3b, inventaire | Rien de modifié. OpenRewrite `UpgradeToJava21` (plugin 6.18.0, `rewrite-migrate-java` 3.20.0) en `dryRun` : 231 fichiers du build, 13 familles. Patch appliqué dans une copie : compile, mais 62 `StackOverflowError` dans les tests de `globs` (`ListFirstAndLast` rend `GlobList.getFirst()` récursive) et `ExportTest` en échec (`UseTextBlocks` efface une tabulation finale). Quatre lots proposés (A à D) |
| 2026-11-20 | 3b, lot A (recettes sûres) | 102 fichiers, +441 −568 lignes, sans changement de comportement ; `target/dist` 11 Mo, 17 jars, `budgetview.jar` 4,55 Mo. Tests de `globs` 796, `prevayler` 26, `bv_desktop` 1 026 verts, 9 min ; données 5.01 relues |
| 2026-11-20 | 3b, lot C (plugins) | `maven-compiler-plugin` 3.16.0, `jacoco` 0.8.16 (6 `pom`, `coverage.sh`) ; 0 `[WARNING]`, `target/dist` 11 Mo. Tests de `globs` 796, `prevayler` 26 verts ; `bv_desktop` 1 026, 1 erreur de `SignpostSequenceTest` (instable), 9 min. **3b terminé** |
| 2026-11-20 | 3c, inventaire | Rien de modifié. `MacOSXHooks` déjà sur `java.awt.Desktop`, mais par réflexion avec un repli `com.apple.eawt` mort (~130 lignes à retirer avec `PicsouMacLookAndFeel`) ; navigateur par trois `Runtime.exec(String)` (dépréciés), remplaçables par `Desktop.browse`. Deux lots proposés (A et B) |
| 2026-11-20 | 3c, lot A (hooks macOS) | `MacOSXHooks` sans réflexion ni `com.apple.eawt`, `PicsouMacLookAndFeel` supprimé (+56 −202 lignes) ; 2 139 `.class`, `target/dist` 11 Mo, `budgetview.jar` 4,55 Mo. Tests de `bv_desktop` 1 026 verts, 11 min ; menus vérifiés dans l'appli |
| 2026-11-20 | 3c, lot B (navigateur) | 3 services à `Runtime.exec` remplacés par `Desktop.browse` (+24 −67 lignes) ; 2 136 `.class` (−3), `target/dist` 11 Mo, `budgetview.jar` 4,54 Mo. Tests de `bv_desktop` 1 026 verts, 19 min ; liens d'aide vérifiés dans l'appli. **3c terminé** |
| 2026-11-21 | 5c, durée | Calages de 80 s à 8 min dus à la veille du Mac : `caffeinate -i`. 3 JVM de test en parallèle, chacune dans `target/forks/<n>` (`pom` de `bv_desktop`) : suite de `bv_desktop` 1 026 verts en 5 min 08 s (9 à 21 min avant). **5c terminé** |
| 2026-11-21 | 3d, inventaire | Rien de modifié. `Utils`, `Strings`, `Files` (941 lignes) : 8 remplacements exacts par le JDK, dont `Utils.equal` (155 appels) ; 4 doublons internes, 7 méthodes mortes, `RandomStrings` reste du mobile. Balises `beginRemove`/`endRemove` inopérantes depuis la suppression du shrinker : 7 blocs de développement actifs en production, dont le menu `[Dev]`. Quatre lots proposés (0, A à C) |
| 2026-11-21 | 3d, lot 0 (balises « dev ») | 13 blocs `beginRemove`/`endRemove` remplacés par `Application.isDevMode()`, des `assert` ou supprimés (+53 −86 lignes, 18 fichiers) ; application livrée revenue au comportement de la 5.01. Tests de `globs` 796, `bv_desktop` 1 026 verts, 4 min 51 s |
| 2026-11-21 | 3d, lot A (JDK direct) | 3 classes et `Utils.copyOf` supprimés, `Files` modernisé (4 flux fermés) ; `FilesTest` ajouté (12 tests). Tests de `globs` 808 verts, `bv_desktop` 1 026 verts, 4 min 43 s |
| 2026-11-21 | 3d, lot B (doublons et morts) | 5 doublons fusionnés, 7 méthodes mortes, `RandomStrings` et le tirage du mot de passe mobile supprimés (+11 −217 lignes). Tests de `globs` 808 verts, `bv_desktop` 1 026 (1 course d'uispec4j, verte relancée), 4 min 56 s |
| 2026-11-21 | 3d, lot C (`Objects.equals`) | 174 appels dans 79 fichiers, `Utils.equal` supprimé (+238 −243 lignes). Tests de `globs` 808 verts, `bv_desktop` 1 026 (1 course d'uispec4j, verte relancée), 5 min 02 s. **3d terminé** |
| 2026-11-21 | 3e, collections synchronisées | 41 `StringBuffer` → `StringBuilder`, 1 `Vector` → tableau (18 fichiers, +25 −26 lignes). Tests de `globs` 808, `prevayler` 26, `bv_desktop` 1 026 verts, 4 min 50 s. **3e terminé** |
| 2026-11-21 | 3f, inventaire | 97 fichiers (et non 35) : ~65 transmettent le type et restent ; 3 lots proposés (A calcul de `Month`, B formats, C imports à ne pas faire sans décision) ; défaut latent `hh` → `HH` relevé |
| 2026-11-21 | 3f, `Month` en `java.time` | `Month` sans `Calendar` ni `synchronized`, `hh` → `HH`, `OfxImporter.DATE_FORMAT` non partagé (7 fichiers, +84 −66 lignes, dont un test de 42 lignes comparant au `Calendar`). Tests de `globs` 808, `bv_desktop` 1 027 verts. Lots B et C abandonnés. **3f terminé** |
| 2026-11-21 | 4a et 4b, inventaire | Rien de modifié. 21 tests de plateforme (11 fichiers), 34 `invokeLater`, 5 `Thread.sleep`, 25 `catch` vides, origine retrouvée par `git blame`. Morts sous Java 21 : versions de Windows d'`AppPaths`, `sun.awt.exception.handler`, `PicsouLinuxLookAndFeel` ; masques `InputEvent` dépréciés, dont un défaut latent (clic droit pris pour « Meta ») ; 5 contournements à essayer dans l'appli. Trois lots proposés (A à C) |
| 2026-11-21 | 4c, lot A (morts sous Java 21) | Détection de Windows XP à 8 (`GuiUtils`, `AppPaths`), `setProperty("sun.awt.exception.handler")` et `PicsouLinuxLookAndFeel` supprimés (−87 lignes, 4 fichiers) ; build sans `[WARNING]`. Tests de `globs` 808, `bv_desktop` 1 027 verts, 5 min 11 s |
| 2026-11-21 | 4c, lot B (masques dépréciés) | `Gui.isAddModifier` (prend désormais le `MouseEvent`), `Gui.hasModifiers`, `GuiUtils.isRightClick`, condition de `GlobTableView` sans API dépréciée, comportement identique défaut compris ; nouveau `GuiUtils.isButton` (4 fichiers, +37 −15 lignes ; 3 classes de test, 5 tests). Tests de `globs` 811, `bv_desktop` 1 029 (1 course d'uispec4j dans `DashboardTest`, verte relancée seule deux fois), 5 min 11 s |
| 2026-11-21 | 4c, lot C (essais dans l'appli) | 5 contournements retirés, un commit chacun (+3 −106 lignes, 10 fichiers), après essai sur macOS pour C1 à C4. Tests de `globs` 811, `bv_desktop` 1 029 (1 délai d'uispec4j dans `DeferredTest`, vert relancé seul deux fois), 5 min 07 s. Association de fichiers absente du paquet 6.0.1 relevée. **4c terminé, étape 4 terminée** |
| 2026-11-21 | 5a, inventaire | Rien de modifié. 25 tests désactivés ou commentés (22 dans `bv_desktop`, 3 dans `globs`) ; 14 réactivés à part : 1 passe (instable), 13 échouent. 9 méthodes à supprimer (dont 2 classes entières), 2 à réparer, 12 à garder désactivés (bugs connus, limites d'uispec4j) ; 1 exclusion périmée dans `globs`. Trois lots proposés (A à C) |
