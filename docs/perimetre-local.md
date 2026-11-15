# Périmètre : usage local seulement

Décision du 2026-11-15 : la 6.00 fonctionne **en local seulement**. On importe des fichiers
(OFX, QIF, CSV) ou on saisit à la main. Tout ce qui passait par les serveurs d'origine est désactivé.

Pourquoi : `register.budgetview.fr` (licences, mises à jour, mails, cloud, mobile) ne répond plus.
L'agrégateur Budgea (`budgetview.biapi.pro`) servait la synchro bancaire, et le service a été
arrêté en 2018. Le site `www.budgetview.fr` répond encore : les liens « Support » et « Site web »
du menu Aide, qui ouvrent simplement le navigateur, sont conservés.

## Interrupteur

`Application.isServerEnabled()`, piloté par la propriété système `budgetview.server.enabled`.
Elle est fausse par défaut. Avec `-Dbudgetview.server.enabled=true`, on retrouve le comportement
de la 5.01, sans garantie, puisque les serveurs sont morts.

| Fonction | Où c'est coupé | Effet en local |
|---|---|---|
| Statut de licence, config banques et **téléchargement de jar** depuis le serveur | `UserConfigService.retrieveUserStatus` (même branche que les données en mémoire) | aucun thread réseau ; l'utilisateur est considéré enregistré |
| Contrôle de licence après la connexion | `MainWindow` : `LicenseCheckerThread` non lancé | `User.CONNECTED` reste faux, ce qui coupe aussi l'envoi des statistiques d'usage (`UsageDataSender`), la demande d'évaluation (`UserEvaluationDialog`) et l'envoi vers le mobile |
| Bandeau « nouvelle version » | `NewVersionService.update` | jamais affiché |
| Menu Aide : avis, envoi des logs, envoi du fichier importé | `MenuBarBuilder.createHelpMenu` | entrées absentes |
| Synchro bancaire, section de l'import | `ImportedFileSelectionPanel.prepareForDisplay` | section masquée, titre compris |
| Synchro bancaire, import automatique au démarrage | `AutoCloudImportView.shouldDisplay` | jamais affiché |
| Synchro bancaire, mise à jour des comptes après un import | `CloudService.updateAccounts` | aucun appel |

Déjà débranchés avant cette décision : le menu « Activer les extensions » (supprimé en 6.00) et le
menu mobile (commenté dans `MenuBarBuilder`).

Le téléchargement automatique du jar mérite une attention particulière. Si le domaine
`budgetview.fr` changeait de mains, un serveur tiers pourrait livrer un jar exécuté au démarrage
suivant. Ne jamais réactiver l'interrupteur par défaut sans avoir remplacé ce mécanisme.

## Tests

- `LocalModeTest` vérifie que le menu Aide ne propose aucun envoi en mode local.
- `HelpTest` et `SendImportedFileTest` testent les dialogues d'envoi : ils réactivent l'interrupteur
  pour toute leur classe, puisque la fenêtre est partagée entre les tests d'une classe.
- Les tests fonctionnels vident déjà les URL de serveur (`LicenseConstants.LICENSE_URL_PROPERTY`,
  `FTP_URL_PROPERTY`) : même avec l'interrupteur actif, ils ne sortent pas sur le réseau.
