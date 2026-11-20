# Licence et droits

État au 2026-11-15 : **aucune licence explicite sur l'application.** Le dépôt amont
[MarcGuiot/budgetview](https://github.com/MarcGuiot/budgetview), public depuis avril 2025, n'en
déclare pas. Sans licence, le code reste « tous droits réservés ». Le fork ne doit donc ni
redistribuer l'application ni ajouter de licence de sa propre initiative.

## Qui détient les droits

- Le site (`bv_website/fr/content/apropos/mentions_legales.html`) et le contrat d'utilisation
  attribuent le logiciel à **BudgetView SARL** (SIREN 525 104 543, Paris).
- D'après l'annuaire public des entreprises, la société a été créée le 2010-09-20 et **fermée le
  2019-11-30**. Marc Guiot en était le gérant et Régis Medina le liquidateur.
- Auteurs (écran « À propos ») : Marc Guiot et Régis Medina. Commits : `rmedina` 2 463,
  `mguiot`/`guiot`/`MarcGuiot` environ 1 440 ; premier commit du 2008-05-28.
- À la liquidation, les droits ont normalement été attribués aux associés. L'accord des deux auteurs
  est donc souhaitable : celui de Marc seul pourrait ne pas suffire.

## Démarches

- Tentative de contact directe de Marc Guiot par Matthieu : sans réponse à ce jour.
- [Issue #1](https://github.com/MarcGuiot/budgetview/issues/1) (novembre 2025, un utilisateur) :
  Marc y répond en quelques heures. Il a mis à jour les liens de téléchargement du site, et un
  jar 6.00 tourne chez lui sous Java 21. GitHub est donc le canal qui marche.
- [PR #2](https://github.com/MarcGuiot/budgetview/pull/2), ouverte le 2026-11-15 : propose
  **Apache 2.0** (`LICENSE`, `NOTICE` « Copyright 2008-2019 Marc Guiot et Régis Medina (BudgetView
  SARL) », section README). Elle part de la branche `licence-apache-2.0` du fork, créée sur
  `upstream/master` (remote `upstream`) pour ne contenir que ces trois fichiers.

Pourquoi Apache 2.0 : c'est déjà la licence de `globs` (qui contient l'ancien `saxstack`), `splits` et de
[globsframework](https://github.com/globsframework/globsframework). Elle est permissive, compatible
avec les dépendances, et protège les auteurs (copyright conservé, aucune garantie).

## Licences des sous-projets (déjà présentes)

| Dossier | Licence |
|---|---|
| `globs/` (dont `org.saxstack`, fondu le 2026-11-20 depuis le module `saxstack/`, sous la même licence, et `splits`, dont le dossier séparé a été supprimé en audit 2d) | Apache 2.0 |
| `prevayler/` | BSD (Klaus Wuestefeld), texte dans `prevayler/LICENSE.txt` (les `contrib/`, dont `transactionsFacade` sous BSD de Jay Sachs, ont été supprimés en audit 2c) |
| `uispec4j/` | Common Public License 1.0, tests seulement |
| bibliothèques embarquées | liste dans `bv_desktop/src/main/resources/docs/licenses.html` |
| `bv_desktop/dev/Zelix/` | licence commerciale de l'obfuscateur ZKM, outil plus utilisé |

## Une fois la PR acceptée

Fusionner `upstream/master` dans le fork pour y récupérer `LICENSE` et `NOTICE`. Mettre ensuite à jour
cette page, puis la ligne de CLAUDE.md sur le dépôt.
