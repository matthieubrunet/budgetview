# Idées d'améliorations

Pistes relevées à l'usage, **à reprendre une fois l'audit du code terminé** ([audit-code.md](audit-code.md)).
Rien ici n'est décidé ni planifié : on note pour ne pas perdre, on triera ensuite.

Pour chaque piste : ce qui a été constaté (avec la date), pourquoi c'est gênant, et où regarder dans le code.
Un défaut avéré qui bloque l'usage ne va pas ici : il se corrige tout de suite.

## Import

### Écart de solde après un import : signalement trop discret

- **Constaté le 2026-11-18** (6.0.1, compte de test) : compte contenant des opérations jusqu'en juin, puis
  import d'un fichier d'octobre. Il manque donc des opérations entre les deux, et le solde calculé diffère
  du solde du fichier. L'import se termine sans aucun message ; seule une bulle jaune « 1 » apparaît dans la
  barre latérale gauche. Elle est passée inaperçue.
- **Comportement actuel, conforme au code** :
  - le solde affiché d'un compte existant est toujours le solde calculé (ouverture + opérations) ; le solde
    du fichier n'est appliqué qu'à la création du compte ;
  - l'écart crée un `AccountPositionError` (`PositionTrigger.AccountUpdate.updateAccount`), affiché comme
    notification (`AccountPositionNotification`, texte `messages.account.position.error.msg`) ;
  - aucune comparaison si le fichier n'a pas de solde (QIF, CSV), si la banque est marquée
    `invalidPosition` (Crédit Mutuel, Barclays), ou si le solde du fichier n'est pas daté après la dernière
    opération du compte (`BankPluginService`).
- **Piste** : avertir à la dernière étape de l'import, avec les deux soldes et la période sans opérations,
  et proposer d'importer le fichier manquant. À voir aussi : rappeler l'écart à côté du solde du compte
  tant qu'il n'est pas résolu.

### Ouvrir un relevé depuis le Finder : impossible avec le paquet 6.0.1

- **Constaté le 2026-11-21** (audit 4c) : `package-mac.sh` ne passe pas `--file-associations` à jpackage, donc
  `BudgetView 6.app` ne déclare aucun type de document. Le Finder refuse de lui glisser un `.ofx`, `.qif` ou `.csv`
  sur l'icône du Dock et ne la propose pas dans « Ouvrir avec ». La 5.01 le faisait (types déclarés dans ses
  installeurs depuis 2009, `b6125220a`).
- **Le code est prêt** : `MacOSXHooks` reçoit les fichiers par `Desktop.setOpenFileHandler`, puis
  `OpenRequestManager.openFiles` les passe à l'import, y compris fenêtre réduite ou import déjà ouvert (vérifié en 4c,
  par un second lancement avec le fichier en argument, que l'instance unique transmet).
- **Piste** : un fichier de propriétés par extension (`extension=ofx`, `mime-type`, `description`) passé par
  `--file-associations`, puis essayer glisser-déposer et « Ouvrir avec », appli ouverte et fermée.

## Pense-bêtes de l'auteur

Trouvés en audit 5a (2026-11-21) sous forme de tests vides (`fail("…")` seul), supprimés avec eux. Rien n'a été
essayé dans l'appli.

### Tableau de bord avant le premier compte

- **Note de l'auteur** (`DashboardTest`) : « faire disparaître les signposts » tant qu'aucun compte n'existe.
- **Piste** : vérifier sur un dossier vide (`BV_DEV_DATA=/tmp/bv-essai ./run-dev.sh`) ce que montrent le tableau de bord
  et les bulles de guidage (`signpost`) avant la création du premier compte.

### Tableau de bord sans opération prévue

- **Note de l'auteur** (`DashboardTest`) : « cas où il n'y a pas d'opérations prévues - sunny ? ». L'icône météo
  (`sunny`, pas de découvert prévu) s'affiche-t-elle à raison quand aucune opération n'est prévue ?

### Date de fin des enveloppes d'un compte clos

- **Échange entre auteurs** (`SeriesEditionTest`) : quand un compte est clos, faut-il mettre à jour la date de fin des
  enveloppes qui s'y rattachent, ou au moins ne plus y créer d'opérations prévues ? Proposition laissée :
  renseigner `Series.END_DATE` d'office.

## À vérifier

### Interface en anglais sur le compte de test

- **Constaté le 2026-11-18** : la session `run-dev.sh` du compte `Test` s'affiche en anglais (« Budget »,
  « Income », « Add an account »), alors que la 6.00 force le français au démarrage
  (`Application.LANG_PROPERTY`, `fr` par défaut). Cause non cherchée : préférence de l'utilisateur,
  propriété passée au lancement, ou défaut du forçage.
