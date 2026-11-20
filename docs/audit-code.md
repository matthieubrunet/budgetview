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
- [ ] **2c. `prevayler`** : quelles parties du fork servent ? Ne rien changer au format sur
  disque.
  Inventaire fait le 2026-11-20 (ProGuard, JaCoCo, lecture de la configuration), voir [2c, inventaire](#2c-inventaire)
  plus bas : environ la moitié du code est morte, dont une bonne part par configuration, que ProGuard ne voit pas.
- [ ] **2d. `splits`** : balises et composants jamais utilisés dans les `.splits`.

## Étape 3 : ce que Java fait maintenant tout seul

Des remplacements mécaniques et relisibles, pas une réécriture.

- [ ] **3a. Alertes du compilateur** : compiler avec `-Xlint:all` et classer ce qui ressort
  (dépréciations, types bruts, `finalize`…).
- [ ] **3b. OpenRewrite** : passer les recettes de migration Java 8 → 21 en mode `dryRun`,
  relire le diff et n'appliquer que par familles.
- [ ] **3c. Intégration macOS** : `com.apple.eawt` (2 fichiers) → `java.awt.Desktop` (Java 9 et +).
- [ ] **3d. Utilitaires maison** (`Utils`, `Strings`, `Files`…) comparés à `java.util`,
  `java.nio.file`, `Objects`, `String` (`isBlank`, `repeat`, `strip`…).
- [ ] **3e. Collections synchronisées** (`Vector`, `Hashtable`, `StringBuffer`, 5 fichiers).
- [ ] **3f. Dates** (35 fichiers) → `java.time`. C'est le chantier le plus délicat : le modèle
  stocke des mois et des jours sous forme d'entiers. Ne toucher qu'au code de calcul et
  d'affichage, et le faire en dernier.

## Étape 4 : les contournements, par l'historique

- [ ] **4a. Repérer les motifs suspects** : tests sur `os.name` ou `java.version`, `invokeLater`
  imbriqués, `Thread.sleep` et minuteries dans l'IHM, `catch` vides, réglages forcés de Swing
  (`UIManager.put`, propriétés `apple.*`, `sun.*`).
- [ ] **4b. Retrouver leur raison** : `git log -L <début>,<fin>:<fichier>` ou `git blame`, puis le
  message du commit d'origine. Chercher aussi directement dans les messages :
  `git log -i -E --grep='bug|mac|swing|jdk|java'`.
- [ ] **4c. Tester sans le contournement** sous Java 21, sur macOS, et le retirer s'il n'est plus utile.

## Étape 5 : les tests

À faire après les étapes 1 et 2, qui en suppriment déjà une partie.

- [ ] **5a. Tests désactivés** (`disabled_test…`, voir [etat-des-tests.md](etat-des-tests.md)) :
  pour chacun, réparer ou supprimer.
- [ ] **5b. Tests en double** : avec JaCoCo par test, repérer ceux qui couvrent exactement les
  mêmes lignes.
- [ ] **5c. Durée** : repérer les classes de test les plus lentes (`target/surefire-reports`) et
  voir si leur coût est justifié.
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
`src/test` (3 905 lignes). `prevayler-1.0.jar` : 204 Ko, 144 entrées. Sur 447 fichiers suivis par git, les 300 autres
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
| 2026-11-20 | 2c, inventaire | Rien de modifié. `prevayler` : 103 classes, 3 579 lignes de code, `prevayler-1.0.jar` 204 Ko ; ~1 700 lignes mortes, dont 32 classes selon ProGuard et ~15 par configuration (réplication, censeur strict, `RWLock`), confirmées à 0 % par JaCoCo (65 classes sur 99) ; ~300 fichiers annexes (~2,7 Mo). Tests du module jamais lancés (JUnit 3.8.1 refusé par surefire) : 63 à la main, 1 échec (gzip, mort). Format sur disque non concerné. `bv_desktop` sous JaCoCo : 1 026 verts, 12 min. Cinq lots proposés (0, A à D) |
| 2026-11-20 | 2c, lot 0 (tests du module) | JUnit 4.12, surefire 3.0.0 : tests de `prevayler` lancés par Maven pour la première fois, 62 verts (1 désactivé, gzip), 5 s. Rien de changé hors des tests |
