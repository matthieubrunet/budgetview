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
| `BNPTest` ×4 | **régression de l'appli** : les 22 règles de libellés (`transactionMatcher`) de `banks/fr/bnp.xml` avaient été supprimées en marge du branchement Budgea, sans raison donnée ; restaurées depuis le parent de ce commit, car l'import OFX/QIF est le seul qui reste (Budgea arrêté) | `501972923` (2017) |

## Désactivés (méthode renommée `disabled_*`, raison en commentaire)

Les tests héritent de JUnit 3 (`TestCase`) : `@Ignore` n'a aucun effet. Une méthode est donc
désactivée en la renommant, et une classe vide est exclue dans le `pom` (sinon « No tests found »).

| Tests | Raison |
|---|---|
| `DashboardTest` ×2, `SeriesEditionTest` ×2 | TODO de l'auteur : `fail("…")` explicite |
| `RestartTest.testChangeDayChangesPlannedTransactionsForSavingsSeries` | **bug connu** noté par l'auteur : des opérations prévues sont générées sur un mois passé après redémarrage |
| `UrlTest` ×2 (classe exclue) | vérifie en ligne les URL des textes : dépend du réseau et de budgetview.fr |
| `JsonImporterTest` (classe exclue) | import du service cloud Budgea, arrêté en 2018 |

## Restent en échec : 38 tests, à investiguer

| Groupe | Tests | Piste |
|---|---|---|
| Instance unique | `SingleInstanceTest` ×6 | plusieurs instances lancées dans la même JVM, vrais ports 5454… ; fenêtres non interceptées |
| Contrôle des données | `DataCheckingServiceTest` ×6, `DataCheckerTest` ×1 | service refondu en 2017 (`9b303cd8e`, « Intro BackupClinic »), tests adaptés en partie seulement |
| Import | `ImportTest` ×4, `MoneyExportTest` ×3, `CATest`, `LaPosteTest.testDeferred`, `DeferredTest.testCategorization`, `LoginTest.testBanksAreCorrectlyReImported` | dont 3 « notificationsFlag is not visible » ; enchaînement des écrans d'import modifié ? |
| Comptes utilisateurs | `SetPasswordTest` ×2 | après déconnexion, l'utilisateur auto-connecté n'est plus proposé (« Create auto login user » au lieu de « Enter without password ») |
| Projets / épargne | `ProjectTransferTest` ×2, `ProjectUpgradeTest` ×1, `SeriesUpgradeTest` ×1, `SavingsTest` ×1, `AccountDeletionTest` ×1 | — |
| Divers | `RestartTest.testCorrupt`, `PrintTest` (NPE), `BudgetViewTest`, `NotesViewTest`, `AnalysisBudgetViewStackChartTest`, `TimeViewPanelUISpecTest` | — |

`SeriesUpgradeTest` n'échouait pas à la passe précédente : il est peut-être instable.

## Tests de globs

~40 échecs (`GlobPrinterTest`, tests SQL) et un blocage sans fin sur HSQLDB dans
`HsqlExceptionTest`. Pas encore examinés.
