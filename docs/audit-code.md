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

- [ ] **1a. Code derrière `Application.isServerEnabled()`** : licences, cloud, mobile,
  vérification de version ([perimetre-local.md](perimetre-local.md)). Il est mort par
  construction, puisque le serveur ne répond plus. Lister les classes qui ne servent que là
  (`client/`, `mobile/`, `session/`…). C'est le plus gros gisement et le moins risqué.
  Inventaire fait le 2026-11-16 (script [audit/reach.py](audit/reach.py)), voir [1a, inventaire](#1a-inventaire) plus bas.
  Lots 1 à 4a supprimés le 2026-11-16 ; reste la licence (lot 5), à valider avant d'y toucher.
- [ ] **1b. Synchro bancaire Budgea** (`budgea/`, `bank/`, `io/budgea`) : vérifier si elle
  marche encore sans le serveur. Sinon, décider de la couper, voire de la supprimer.
- [ ] **1c. Couverture JaCoCo de la suite fonctionnelle** : ajouter un profil Maven, lancer
  `mvn test`, lister les classes à 0 %.
- [ ] **1d. Couverture JaCoCo en usage réel** : agent JaCoCo dans `run-dev.sh` (option), quelques
  sessions de travail habituelles. Une classe à 0 % dans 1c et dans 1d est candidate.
- [ ] **1e. Code inaccessible depuis `DesktopApp`** : ProGuard en `-dontobfuscate -printusage`
  (ou un outil équivalent), à recouper avec 1c et 1d. Attention aux classes chargées par
  réflexion (globs, splits, i18n), que ces outils signalent à tort comme inutilisées.
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
- [ ] 5. licence et téléchargement du jar : ce n'est pas une simple suppression. `UserConfigService`, la machine
  d'états `userconfig/states` (7 classes) et `LicenseRegistrationTrigger` écrivent `IS_REGISTERED_USER` et
  `LICENSE_ACTIVATION_STATE` dans le modèle, et `ImportFileAction` ouvre `LicenseActivationDialog` quand
  l'import n'est pas disponible. Approche proposée : « toujours enregistré », `UserConfigService` réduit au
  chargement local de la config des banques, champs du modèle conservés. Le changement touche la
  connexion : il faut le vérifier aussi dans l'appli (`./run-dev.sh`). Reste après ce lot la synchro Budgea (1b).

Pendant ces suppressions, compiler dans un worktree séparé (`git worktree add`, puis
`mvn -o test-compile -pl budgetview/bv_shared,budgetview/bv_desktop`). Cela permet de vérifier pendant
qu'une suite tourne, sans toucher à son `target/` ni au `bv_shared` de `~/.m2`. Chercher les usages d'un
checker de test par le nom de sa classe, et non par le nom du composant : `LoggedInFunctionalTestCase`
crée plusieurs checkers pour tous les tests.

## Journal

| Date | Chantier | Résultat (classes, taille du jar, tests) |
|---|---|---|
| 2026-11-16 | Point de départ | 1 112 classes de premier niveau (`bv_desktop` et `bv_shared`, dont 1 069 accessibles depuis `DesktopApp`) ; 2 374 `.class` dans `bv_desktop/target/classes` ; `target/dist` 12,3 Mo, `budgetview.jar` 5,2 Mo |
| 2026-11-16 | 1a, inventaire | 51 classes et 5 734 lignes mortes par construction ; 10 classes du deuxième cercle à examiner |
| 2026-11-16 | Suite de référence | 1 034 tests, 0 échec, 165 classes de test, 11 min |
| 2026-11-16 | 1a, lots 1 à 4a | 1 070 classes de premier niveau (−42), 2 302 `.class` (−72) ; `target/dist` 12,2 Mo, `budgetview.jar` 5,07 Mo (−110 Ko) ; 1 028 tests verts (−6, supprimés avec leur code), 163 classes, 14 min ; −4 056 lignes de Java net, −348 lignes de langue (3 fichiers). Reste 13 classes mortes par construction (licence : 9, cloud : 4) |
