# État des tests de bv_desktop

Relevé du 2026-11-15, commande `mvn test -pl budgetview/bv_desktop` (JDK 8, ~15 min).

| Étape | Tests lancés | Passent |
|---|---|---|
| Premier lancement, réglages d'origine | 1 049 | 191 |
| Après correction de l'infrastructure de test (voir CLAUDE.md) | 1 049 | 986 |
| Après correction des attentes périmées et désactivation des obsolètes | 1 037 | 998 |
| Après le tri des 38 échecs restants (corrections de l'appli et des tests) | 1 038 | 1 021 |
| Après `ProjectTransferTest` (attentes alignées, question ouverte notée) | 1 038 | 1 023 |
| Après le contrôle des données (`DataCheckingServiceTest`, `DataCheckerTest`) | 1 038 | 1 029 |
| Après désactivation des 9 échecs restants, mis de côté (2026-11-15) | 1 029 | **1 029** |

Dernière ligne relevée sur la suite complète lancée avec les corrections de globs (voir « Tests de globs »).

Aucune version de référence : la 5.01 ne se reconstruit plus en l'état (casse d'un nom
de fichier dans `globs`, `pom` à dépendances cassées, look & feel Mac, `Gui.setLookAndFeel`).
On ne sait donc pas lesquels des échecs restants existaient déjà avant la 6.00.
Le tri ci-dessous s'appuie sur l'historique git et sur l'aide utilisateur du site
(`budgetview/bv_website/fr/content/support/`), qui décrit le comportement attendu. Par exemple :
- `faq/comptes/carte_debit_differe.html` : seul le prélèvement mensuel du compte courant se rattache
  à la carte (poste « Autres ») ; les opérations de la carte se classent dans les enveloppes ordinaires.
- `faq/comptes/solde_a_zero.html` : un fichier QIF ou CSV n'a pas de solde ; une fois le dernier solde
  saisi à la main, les imports suivants le font évoluer. Un fichier OFX, lui, donne le solde de chaque compte.
- `derniers_changements.html` : la 5.0 (2017-10-29) remplace les connexions directes aux banques par
  Sync (Budgea) ; la 5.01 (2017-11-12, jar 156) contient donc déjà les débranchements de 2016.

Pages utiles pour les tests encore en échec :
- `ProjectUpgradeTest` → `versions/v3_14b_maj.html` : à la migration vers la 3.14, chaque enveloppe et
  chaque élément de projet reçoit un compte d'après les opérations déjà affectées ; s'il y en a sur plusieurs
  comptes, l'enveloppe devient un groupe (une enveloppe par compte) et l'élément de projet est dupliqué.
  Aucune opération ne devrait donc finir rattachée au compte de synthèse « Main accounts ».
- `ProjectTransferTest` → rien de direct ; `faq/comptes/solde_a_zero.html` (solde saisi à la main = dernier
  solde connu) et `guides/import.html` (l'OFX donne le solde de chaque compte).
- `SingleInstanceTest` → `faq/donnees/deplacement_repertoire_donnees.html` : un seul BudgetView à la fois
  sur un dossier de données ; la 3.07 protège contre la corruption dans ce cas, déclaré non supporté.
- Le plugin La Poste servait le **téléchargement direct OFX** de La Banque Postale
  (`faq/general/pourquoi_connexion.html`), supprimé avec les connecteurs en 2016.

L'aide anglaise (`en/content/support/`) n'est pas une référence : `faq/categorization/internal_transfers.html`
et `savings_transfers.html`, absentes en français, décrivent l'avant-4.0 (bloc « Savings », deux enveloppes
créées d'office par compte d'épargne), en contradiction avec la FAQ française. La liste des banques en
connexion directe (`faq/import/banques_francaises_connexion_directe.html`) est elle aussi périmée.

## Corrigés : attentes en retard sur le code

| Tests | Cause | Commit d'origine |
|---|---|---|
| `CaisseEpargneTest`, `CreditMutuelTest` | banques renommées (« Caisse d'Épargne », « Crédit Mutuel Sud-Ouest ») | 2016-2017 |
| `PlannedTransactionCreationTriggerTest` | libellé `picsou` → `budgetview` à moitié renommé | `e2a70a817` (2016) |
| `LoginTest`, `SignpostSequenceTest` | séries par défaut « Income 1/2 » devenues « Income / Interest » | `b69993967` (2016) |
| mode démo (`LoginTest` ×4, `SignpostSequenceTest`) | snapshots de démo non générés : `mvn -Pgen-demo install` | — |
| `ColorTest` | **bug de l'appli** : clé `dashboard.title` (titre de l'import cloud) absente des 6 thèmes ; ajoutée avec `404040` comme le thème par défaut, le fond étant clair partout | — |
| `TimeViewPanelUISpecTest` | largeur mini d'un mois passée de 40 à 46 px ; à 80 px le panneau tient moins de deux mois et ne défile pas, réduit à 100 px | `94db96c10` (2015) |
| `BudgetViewTest.testSeveralMonthsShowOrNotSeries` | passait seul, échouait dans sa classe : `testShowHideInactiveSeries` désactivait le filtrage par mois sans le rétablir (fenêtre partagée) | — |
| `ImportTest.testImportFromDifferentAccountsWithSameTransactionsInQif` | position plus saisissable dans l'aperçu pour un compte existant | 2016 |
| `LoginTest.testBanksAreCorrectlyReImported` | bouton « Skip file » remplacé par un choix de la liste des comptes cibles | `ec66b22fb` (2017) |
| `CATest` | code banque 13006 du fichier de test plus rattaché à aucune banque depuis la refonte des caisses régionales ; le test choisit la banque | `03bb7182f` (2016) |
| `MoneyExportTest.testStrictQifFile` | compte sans solde + fichier QIF sans solde : la fin de l'import demande la position, le test l'accepte | — |
| `SetPasswordTest` ×2 | la déconnexion recrée la session, ce qui vide une base en mémoire : le test passe sur disque | — |
| `CsvImportTest.testImportWithSeries` | attendait la boîte d'import des séries au réimport d'une série connue, effet du bug `TARGET_ACCOUNT` ci-dessous | — |
| `ProjectTransferTest` ×2 | soldes attendus décalés de +1 000 pour suivre le comportement actuel (position saisie = solde d'ouverture) ; **question ouverte**, voir plus bas | — |
| `DataCheckingServiceTest` ×6 | test unitaire sans les constantes (`DesktopModel.get().getConstants()`) ni les `MonthDay` que charge `PicsouInit`, exigées par le contrôle des liens `GlobCheck.linksAreAllConnected` ; libellés du rapport changés (« Creating months:[…] », « Missing series for seriesBudget … ») | `9b303cd8e` (2017) |
| `BNPTest` ×4 | **régression de l'appli** : les 22 règles de libellés (`transactionMatcher`) de `banks/fr/bnp.xml` avaient été supprimées en marge du branchement Budgea, sans raison donnée ; restaurées depuis le parent de ce commit, car l'import OFX/QIF est le seul qui reste (Budgea arrêté) | `501972923` (2017) |

Corrections de l'appli elle-même (le test passe sans modification) :

| Tests | Bug | Origine |
|---|---|---|
| `NotesViewTest` | infobulle d'un mois sans opération : total au minimum recopié du mois précédent, comptes de synthèse comptés (`BudgetStatTrigger`) | `823b36f20` (2015) |
| `RestartTest.testCorrupt` | journal corrompu signalé comme « Impossible de se connecter au serveur » + fenêtre de pile : l'erreur de déchiffrement était enveloppée dans une `RuntimeException` | 2016 |
| `AccountDeletionTest` | la suppression d'un compte laissait un `SeriesStat` orphelin, recréé par `ActualSeriesStatTrigger` (`findOrCreate` sur une suppression) | — |
| `SavingsTest` | après saisie manuelle de la position, un import QIF redemandait la position | — |
| `ImportTest` ×2 (alertes d'écart de position) | `BankPluginService` débranché en 2016 : le solde du fichier OFX n'était plus reporté sur un compte existant, plus aucune alerte d'écart. Rebranché, **sauf le plugin La Poste**. Confirmé en usage réel sur la 5.01 : plus aucune alerte d'écart de solde depuis longtemps. Si les données réelles passent un jour sur la 6.00, des alertes pourront signaler des écarts accumulés depuis 2016 | `03bb7182f`, `a05fe274b` (2016) |
| `DeferredTest` (partie catégorisation) | une dépense de carte à débit différé se voyait proposer d'être le prélèvement de sa carte : le filtre ignorait `ORIGINAL_ACCOUNT` (opérations déplacées dans le compte courant depuis 2015) | `d4ec09c6a` (2015) |
| `DataCheckerTest.testErrorDialog` | une exception levée dans l'IHM n'ouvrait plus le contrôle des données : `AwtExceptionHandler` passait par la propriété `sun.awt.exception.handler`, ignorée depuis Java 7. Relayé par `Thread.setDefaultUncaughtExceptionHandler`, limité au fil de l'IHM (EDT) ; suite complète sans régression | passage à Java 7+ |
| `MoneyExportTest` ×2 | séries créées à l'import sans compte cible (`TARGET_ACCOUNT`), donc non reconnues et reproposées au réimport | `fa611f7fe` (2014) |

## Désactivés (méthode renommée `disabled_*`, raison en commentaire)

Les tests héritent de JUnit 3 (`TestCase`) : `@Ignore` n'a aucun effet. Une méthode est donc
désactivée en la renommant, et une classe vide est exclue dans le `pom` (sinon « No tests found »).

Audit 5a, lot A (2026-11-21) : supprimés, car ils ne testaient plus rien, `DashboardTest` ×2 et `SeriesEditionTest` ×2
(pense-bêtes de l'auteur, notés dans [idees-ameliorations.md](idees-ameliorations.md)), `UndoRedoTest.testUndoRedoMaintainsSelection`
(fonction commencée en 2008, jamais finie), `UrlTest` (budgetview.fr), `JsonImporterTest` (synchro Budgea) et
`LaPosteTest.testDeferred` (plugin La Poste laissé débranché). Détail : [audit-code.md](audit-code.md), « 5a, inventaire ».

| Tests | Raison |
|---|---|
| `RestartTest.testChangeDayChangesPlannedTransactionsForSavingsSeries` | **bug connu** noté par l'auteur : des opérations prévues sont générées sur un mois passé après redémarrage |
| `ImportTest.testImportAndChangeDate` | **bug connu** noté par l'auteur dans un `fail()` final : `PositionTrigger` saute le contrôle si le solde importé égale le précédent |
| `DeferredTest.testCategorization` | test en chantier chez l'auteur (« en cours », 2015) : ses attentes de fin ne correspondent pas au fichier importé ; la partie catégorisation passe |

### Mis de côté durablement (2026-11-15), sans correction

Les derniers échecs, désactivés par décision et non parce qu'ils seraient obsolètes. Pistes conservées
pour une éventuelle reprise :

| Tests | Piste |
|---|---|
| `SingleInstanceTest` ×6 (classe exclue) | plusieurs instances lancées dans la même JVM, vrais ports 5454… ; fenêtres non interceptées |
| `ProjectUpgradeTest.testProjectsWithVariousTranferItemsFromJar125` | après migration d'une base de 2014, une opération finit sur le compte de synthèse « Main accounts », sans enveloppe : contraire à `v3_14b_maj.html`. **Seul des neuf à toucher les données réelles**, si elles passent un jour sur la 6.00 |
| `AnalysisBudgetViewStackChartTest.testPopupMenus` | « component must be showing on the screen » à l'ouverture d'un menu contextuel |
| `SeriesUpgradeTest.testNoMessagesDisplayedWhenConvertingASavingsSeriesWithNoTransactionsAndASingleMainAccount` | instable, passe ou échoue selon les passes (`expected:<MainA> but was:<SavingsA>`) |

Instable mais actif : `SignpostSequenceTest.testSwitchingToTheDemoAccountAndBackPreservesSignposts` a échoué
une fois sur la suite complète (2026-11-16, sous JaCoCo) : `homeCardToggle` introuvable alors qu'il figure dans
la liste des composants disponibles, juste après le passage au compte de démo. La classe seule passe 3 fois sur 3.
Le 2026-11-20, même erreur sur 2 des 4 suites complètes de l'audit 2b (sans JaCoCo), au hasard des lots ; la classe
seule passe 4 fois sur 4. L'échec devient fréquent : à regarder au chantier 5 (attente sur la fenêtre après la bascule).
Audit 3a (2026-11-20) : 1 échec sur 3 suites complètes (`budgetCardToggle`, même cas), et 1 sur 10 passages de la
classe seule, sans lien avec le lot testé (4 sur 4 verts avec et sans lui) : l'échec n'exige donc pas la suite complète.

## Questions ouvertes (arbitrage métier)

### Solde saisi à la création d'un compte, puis import OFX

Cas : on crée un compte à la main en saisissant son « Solde » (1 000), puis on importe un OFX
contenant des opérations passées (`ProjectTransferTest`, méthodes `createMainAccount` / `createSavingsAccount`).

- **Comportement actuel** : le solde saisi devient un solde **d'ouverture** — transaction « open account »
  placée avant la première opération (`AccountInitialPositionTrigger`) — et les opérations importées s'y
  ajoutent (compte courant du test : 1 000 + 2 100 = 3 100). Le solde du fichier OFX est ignoré : il ne
  compte que s'il est daté après `Account.POSITION_DATE` (`BankPluginService`), qui est ici la date de création.
- **Aide du site** : `faq/import/banque_sans_export.html` demande le « solde **actuel** » à la création ;
  `faq/comptes/solde_a_zero.html` parle du « dernier solde connu », que les imports suivants font évoluer.
  Le compte devrait alors finir à 1 000, les soldes passés recalculés à rebours.
- **Ancien test** : soldes partant de 0 (fin à 2 100), ce qui ne correspond à aucune des deux lectures
  ni au solde des fichiers OFX du test (1 000 et 10 000). Le test porte sur les virements de projets,
  pas sur les soldes : ses chiffres ont été alignés sur le comportement actuel, sans trancher.
- **À décider** : (1) solde saisi = solde actuel ou d'ouverture ? (2) le solde d'un OFX importé ensuite
  doit-il remplacer le solde saisi, même s'il est daté d'avant ?
- Non vérifié sur la 5.01 (logique de 2013-2015, donc probablement identique) : à essayer sur un dossier de
  données jetable — créer un compte avec un solde, puis importer un OFX d'opérations passées.

## Tests de globs

Le `pom` de `globs` fixe une JVM par classe (des classes de test de l'IHM laissent un état partagé :
`ColorServiceEditorTest` échoue derrière d'autres classes), une limite de 120 s (un blocage SQL reste possible) et
les `--add-opens` dont uispec4j a besoin sous Java 21 (sans eux, les 38 classes de test d'IHM échouent à
l'initialisation ; réglages ajoutés le 2026-11-19). Durée : ~1 min.

```sh
mvn -o clean test -pl globs
```

Toujours avec `clean` après un changement de signature dans `src/main` : le vieux `maven-compiler-plugin` (2.3.2)
ne recompile que les sources modifiées, et une classe de test non touchée reste liée à l'ancienne méthode
(constaté avec `MultiCreateBuilder.setValue`). bv_desktop prend globs dans `~/.m2` : `mvn -o install -pl globs
-DskipTests` après toute modification de globs, avant de lancer ses tests.

| Relevé (2026-11-15) | Lancés | En échec |
|---|---|---|
| Premier tri, sans correction | 874 + 1 classe bloquée | 42 |
| Après corrections | 873 | **0** |
| Après l'audit 2a (2026-11-20), tests partis avec le code supprimé ou déplacé dans `bv_server` | 787 | **0** |
| Après l'audit 2b (2026-11-20) : `saxstack` fondu dans `globs`, avec les 26 tests qui lui restaient après le lot A | 813 | **0** |

bv_desktop relancé en entier avec ces corrections (jar globs installé) : 1 029 tests, une seule erreur, due à l'essai
sur `GlobTableView` (voir plus bas), abandonné depuis ; la classe en cause repasse.

Corrections, par cause :

| Cause | Tests | Correction |
|---|---|---|
| `DummyObject` du test modifié en 2016 (`3a133bb2c`, rmedina) : champ `COUNT` renommé `COUNTER` (mot réservé SQL), index unique `NAME_INDEX` retiré | `GlobPrinterTest` ×4, `GlobComboEditorTest` ×6, `DefaultGlobRepositoryTest` ×2, `XmlGlobParserTest` (passait, mais ne testait plus une valeur invalide) | tests passés à `counter` ; `testResetUpdateIndex`, converti à moitié vers `DummyObjectIndex` en 2013, terminé (index sur `uniqueName`, `name` distincts pour l'index multi-champs) |
| `MultiCreateBuilder` n'avait que `setValue(Field, Object)` : un `Accessor` y était pris pour la valeur (`ClassCastException`, « On dummyObject.id ») | `SqlSelectQueryTest` ×10, `SqlUpdateBuilderTest` ×2, `SqlDeleteBuilderTest` ×2, `SqlGlobRepositoryBuilderTest`, `RemoteExecutorTest` | variante `setValue(Field, Accessor)` ajoutée. Code du seul `bv_server` |
| `<testResources>` du `pom` limité à `**/*.properties` : `.splits` et `.colors` non copiés | `ColorServiceEditorTest` ×5, `ColorServiceTest`, `SplitsStyleImportTest` | tout `src/test/resources` copié |
| **Bug de globs** : `SingleFieldKey` et `CompositeKey` égales (`equals`) mais de `hashCode` différents depuis le changement de formule de `CompositeKey` et des clés à 2, 3, 4 champs (`24c8bf275`, 2015). Sans effet visible dans l'appli : `KeyBuilder` ne crée jamais de `CompositeKey` pour un type à clé unique | `KeyBuilderTest` | `SingleFieldKey.hashCode` aligné (31 × hash du type + hash de la valeur). Le hash d'un `GlobType` est son hash d'identité : il changeait déjà à chaque lancement, l'ordre des `HashMap` n'était pas stable avant non plus |
| Le bouton écoute les `ActionEvent` depuis 2014 (`740f2e340`, correctif de perfs) : `setSelected()` par le code ne change plus la sélection, seul un clic | `GlobSelectionToggleTest` ×2 | le test clique (`doClick`) |
| Layouts de repeat personnalisés résolus par un `RepeatLayoutService` depuis 2014 (`d67e1dde5`), absent du contexte des tests de splits ; l'erreur était masquée en « Unknown layout type » | `SplitsRepeatTest.testCustomRepeatLayout` | service ajouté à `SplitsTestCase` |
| Polices Times et Courier absentes pour le JDK 8 de ce Mac (remplacées par Dialog et Monospaced) ; le test devinait d'après le système | `FontsTest` | attente déduite des polices installées |
| `getQuery()` referme la requête après le premier `getUnique()` (2008) ; le test la relisait. Jamais vu, la classe bloquant avant | `HsqlExceptionTest.testRollback` | `getNotAutoCloseQuery()` |

Désactivés :

| Tests | Raison |
|---|---|
| `SqlExceptionTest.testConcurrentModification` (pour HSQLDB et MySQL) | suppose une lecture non isolée, comme HSQLDB 1.8 ; depuis HSQLDB 2.3.4 (`fc89dcb0c`, 2016), la seconde connexion attend le verrou de la première, tenu par le même fil : **c'était le blocage sans fin** |
| `MySqlExceptionTest` | demande un serveur MySQL local ; parti dans `bv_server` avec `sqlstreams` (audit 2a), son exclusion du `pom` de `globs` a été retirée en 5a |
| `GlobTableViewTest.testRefreshUpdatesTheSortingAndKeepsSelection` | **comportement réel, gardé exprès** : `refresh()` coupe les notifications de sélection, mais la remise à zéro du modèle (`globListPreReset` / `globListReset`) les rétablit avant la fin, et la sélection réappliquée part en événements transitoires (vide, puis ligne par ligne) vers le `SelectionService`. Essai d'une coupure imbriquable (compteur, demandes `sendPending` gardées pour la sortie) : `AnalysisSeriesGroupViewTest.testDeletingAndAddingGroupElements` échoue dans bv_desktop (graphique des enveloppes vide après « ajouter à un nouveau groupe »), donc l'appli dépend de ces événements. `GlobTableView` sert dans 57 fichiers de l'appli : non retouché |
| `ReplacementInputStreamTest.testRegexp` | attente incohérente (le motif exige `</SCRIPT>\n</font>`, absent de l'entrée) ; `ReplacementInputStream` ne servait plus nulle part. Supprimé avec sa classe (audit 2a, lot C, 2026-11-20) |

Usage de globs par l'appli desktop (fichiers de `bv_desktop/src/main` qui importent le paquet) :
`gui.splits` 235, `model.format` 87, `utils.serialization` 80, `gui.views` 57, `gui.editors` 25, `json` 2,
`sqlstreams` 0, `remote` 0.
Depuis l'audit 2a, `remote` est supprimé (lot B) et `sqlstreams` est passé dans `bv_server` avec ses tests (lot D,
2026-11-20) : les corrections SQL ci-dessus y vivent désormais.

### Lien avec globsframework (projet actuel de MarcGuiot)

[globsframework/globsframework](https://github.com/globsframework/globsframework) (Apache 2.0, version 5.14.0
de 2026-11-11, Java 21) est **issu de ce même code**. Son README le dit (« version open source développée pour le
projet BudgetView »), et son premier import (`2eeac91`, 2017-09-26, « import after split ») le confirme : 399
fichiers Java, dont 289 portent le même chemin que dans `globs/`. Parmi ces 289, 129 sont identiques à l'espace près,
100 diffèrent de moins de 20 lignes, et 60 davantage.

- Il n'a gardé que le noyau (métamodèle, `model`, repository, `ChangeSet`) : aucun fichier `gui/` ni `splits`,
  et le SQL, le JSON, le XML, etc. sont passés dans des dépôts séparés (`globs-db`, `globs-gson`…).
- Depuis, l'API a changé : paquets `org.globsframework.core.*`, plus de `GlobTypeLoader` (types déclarés par
  `GlobTypeBuilderFactory` au lieu de champs statiques remplis par réflexion), Java 11 en 2020, 17, puis 21.
- Côté BudgetView, `globs/src` est quasi figé depuis la séparation (2 commits en 2018, 1 en 2025).

Il ne remplace donc pas `globs/` : les deux ont un ancêtre commun de 2017. Passer de l'un à l'autre voudrait dire
réécrire chaque `GlobType` de l'appli, garder `gui`/`splits` à part et quitter Java 8, que uispec4j impose.
Les échecs ci-dessus ne s'y retrouvent pas tels quels : ils viennent de tests du côté BudgetView (2014-2016) ou de
paquets que l'amont n'a pas repris. Le bug corrigé (`SingleFieldKey.hashCode`) et le comportement de `GlobTableView`
n'ont pas été comparés avec l'amont.
