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
- [ ] **1f. Ressources orphelines** : fichiers `.splits`, clés `lang_*.properties`, images,
  définitions de `banks/` qui ne sont plus référencés.

## Étape 2 : les frameworks forkés

`globs`, `splits`, `saxstack` et `prevayler` sont des versions privées et figées. BudgetView
n'utilise sans doute qu'une partie de leurs API.

- [ ] **2a. API de `globs` réellement appelées** par `bv_desktop` et `bv_shared` (`jdeps`, ou
  analyse des imports et des appels). Le reste est candidat à la suppression, avec ses tests
  (873 aujourd'hui).
- [ ] **2b. `saxstack`** : ce qu'il apporte par rapport au SAX ou au StAX du JDK. Peut-il être
  remplacé, ou au moins réduit ?
- [ ] **2c. `prevayler`** : quelles parties du fork servent ? Ne rien changer au format sur
  disque.
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
