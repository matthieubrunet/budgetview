# État des tests de bv_desktop

Relevé du 2026-11-15, commande `mvn test -pl budgetview/bv_desktop` (JDK 8, ~15 min).

| Étape | Tests lancés | Passent |
|---|---|---|
| Premier lancement, réglages d'origine | 1 049 | 191 |
| Après correction de l'infrastructure de test (voir CLAUDE.md) | 1 049 | 986 |
| Après correction des attentes périmées et désactivation des obsolètes | 1 037 | 998 |
| Après le tri des 38 échecs restants (corrections de l'appli et des tests) | 1 038 | **1 021** |

Le dernier relevé (1 039 lancés, 18 échecs) a été pris avant deux commits : `LaPosteTest.testDeferred`
désactivé et `CsvImportTest` corrigé, chacun relancé seul depuis. D'où 1 038 / 1 021.

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
| `MoneyExportTest` ×2 | séries créées à l'import sans compte cible (`TARGET_ACCOUNT`), donc non reconnues et reproposées au réimport | `fa611f7fe` (2014) |

## Désactivés (méthode renommée `disabled_*`, raison en commentaire)

Les tests héritent de JUnit 3 (`TestCase`) : `@Ignore` n'a aucun effet. Une méthode est donc
désactivée en la renommant, et une classe vide est exclue dans le `pom` (sinon « No tests found »).

| Tests | Raison |
|---|---|
| `DashboardTest` ×2, `SeriesEditionTest` ×2 | TODO de l'auteur : `fail("…")` explicite |
| `RestartTest.testChangeDayChangesPlannedTransactionsForSavingsSeries` | **bug connu** noté par l'auteur : des opérations prévues sont générées sur un mois passé après redémarrage |
| `UrlTest` ×2 (classe exclue) | vérifie en ligne les URL des textes : dépend du réseau et de budgetview.fr |
| `JsonImporterTest` (classe exclue) | import du service cloud Budgea, arrêté en 2018 |
| `ImportTest.testImportAndChangeDate` | **bug connu** noté par l'auteur dans un `fail()` final : `PositionTrigger` saute le contrôle si le solde importé égale le précédent |
| `DeferredTest.testCategorization` | test en chantier chez l'auteur (« en cours », 2015) : ses attentes de fin ne correspondent pas au fichier importé ; la partie catégorisation passe |
| `LaPosteTest.testDeferred` | repose sur le plugin La Poste (inversion de signe des cartes à débit différé, relevé de 2011), volontairement laissé débranché : format actuel de La Poste inconnu, risque d'inverser des montants corrects |

## Restent en échec : 17 tests

| Groupe | Tests | Piste |
|---|---|---|
| Instance unique | `SingleInstanceTest` ×6 | plusieurs instances lancées dans la même JVM, vrais ports 5454… ; fenêtres non interceptées |
| Contrôle des données | `DataCheckingServiceTest` ×6, `DataCheckerTest` ×1 | service refondu en 2017 (`9b303cd8e`, « Intro BackupClinic »), tests adaptés en partie seulement |
| Projets | `ProjectTransferTest` ×2 | toutes les positions décalées de +1 000 : le test attend des soldes partant de 0, l'appli part de la position saisie à la création du compte (1 000) avant l'import OFX. **Arbitrage métier** à faire : comment se combinent position saisie et solde du fichier |
| Migration | `ProjectUpgradeTest.testProjectsWithVariousTranferItemsFromJar125` | après migration d'une base de 2014, une opération finit sur le compte de synthèse « Main accounts », sans enveloppe : contraire à `v3_14b_maj.html` |
| Divers | `AnalysisBudgetViewStackChartTest.testPopupMenus` | « component must be showing on the screen » à l'ouverture d'un menu contextuel |

`SeriesUpgradeTest` passe ou échoue selon les passes : instable.

## Tests de globs

~40 échecs (`GlobPrinterTest`, tests SQL) et un blocage sans fin sur HSQLDB dans
`HsqlExceptionTest`. Pas encore examinés.
