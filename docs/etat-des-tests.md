# État des tests de bv_desktop

Relevé du 2026-11-15, commande `mvn test -pl budgetview/bv_desktop` (JDK 8, ~15 min).

| Étape | Tests lancés | Passent |
|---|---|---|
| Premier lancement, réglages d'origine | 1 049 | 191 |
| Après correction de l'infrastructure de test (voir CLAUDE.md) | 1 049 | 986 |
| Après correction des attentes périmées et désactivation des obsolètes | 1 037 | **998** |

Aucune version de référence : la 5.01 ne se reconstruit plus en l'état (casse d'un nom
de fichier dans `globs`, `pom` à dépendances cassées, look & feel Mac, `Gui.setLookAndFeel`).
On ne sait donc pas lesquels des échecs restants existaient déjà avant la 6.00.
Le tri ci-dessous s'appuie sur l'historique git.

## Corrigés : attentes en retard sur le code

| Tests | Cause | Commit d'origine |
|---|---|---|
| `CaisseEpargneTest`, `CreditMutuelTest` | banques renommées (« Caisse d'Épargne », « Crédit Mutuel Sud-Ouest ») | 2016-2017 |
| `PlannedTransactionCreationTriggerTest` | libellé `picsou` → `budgetview` à moitié renommé | `e2a70a817` (2016) |
| `LoginTest`, `SignpostSequenceTest` | séries par défaut « Income 1/2 » devenues « Income / Interest » | `b69993967` (2016) |
| mode démo (`LoginTest` ×4, `SignpostSequenceTest`) | snapshots de démo non générés : `mvn -Pgen-demo install` | — |

## Désactivés (méthode renommée `disabled_*`, raison en commentaire)

Les tests héritent de JUnit 3 (`TestCase`) : `@Ignore` n'a aucun effet. Une méthode est donc
désactivée en la renommant, et une classe vide est exclue dans le `pom` (sinon « No tests found »).

| Tests | Raison |
|---|---|
| `DashboardTest` ×2, `SeriesEditionTest` ×2 | TODO de l'auteur : `fail("…")` explicite |
| `RestartTest.testChangeDayChangesPlannedTransactionsForSavingsSeries` | **bug connu** noté par l'auteur : des opérations prévues sont générées sur un mois passé après redémarrage |
| `UrlTest` ×2 (classe exclue) | vérifie en ligne les URL des textes : dépend du réseau et de budgetview.fr |
| `JsonImporterTest` (classe exclue) | import du service cloud Budgea, arrêté en 2018 |
| `BNPTest` ×4 (classe exclue) | règles de libellés BNP retirées de `banks/fr/bnp.xml` (`501972923`, 2017) |

**Décision à prendre :** faut-il restaurer les règles BNP pour les utilisateurs qui importent
des fichiers OFX/QIF de la BNP ? Elles sont dans le commit parent de `501972923`.

## Restent en échec : 39 tests, à investiguer

| Groupe | Tests | Piste |
|---|---|---|
| Instance unique | `SingleInstanceTest` ×6 | plusieurs instances lancées dans la même JVM, vrais ports 5454… ; fenêtres non interceptées |
| Contrôle des données | `DataCheckingServiceTest` ×6, `DataCheckerTest` ×1 | service refondu en 2017 (`9b303cd8e`, « Intro BackupClinic »), tests adaptés en partie seulement |
| Import | `ImportTest` ×4, `MoneyExportTest` ×3, `CATest`, `LaPosteTest.testDeferred`, `DeferredTest.testCategorization`, `LoginTest.testBanksAreCorrectlyReImported` | dont 3 « notificationsFlag is not visible » ; enchaînement des écrans d'import modifié ? |
| Comptes utilisateurs | `SetPasswordTest` ×2 | après déconnexion, l'utilisateur auto-connecté n'est plus proposé (« Create auto login user » au lieu de « Enter without password ») |
| Projets / épargne | `ProjectTransferTest` ×2, `ProjectUpgradeTest` ×1, `SeriesUpgradeTest` ×1, `SavingsTest` ×1, `AccountDeletionTest` ×1 | — |
| Divers | `RestartTest.testCorrupt`, `PrintTest` (NPE), `BudgetViewTest`, `NotesViewTest`, `AnalysisBudgetViewStackChartTest`, `TimeViewPanelUISpecTest` | — |
| **Bug de l'appli** | `ColorTest` | la clé de couleur `dashboard.title`, présente dans `colors/color.properties`, manque dans les 6 thèmes `color_*.properties` (black, blue, green, orange, pink, turquoise) |

`SeriesUpgradeTest` n'échouait pas à la passe précédente : il est peut-être instable.

## Tests de globs

~40 échecs (`GlobPrinterTest`, tests SQL) et un blocage sans fin sur HSQLDB dans
`HsqlExceptionTest`. Pas encore examinés.
