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
| Synchro bancaire, section de l'import | `ImportedFileSelectionPanel.prepareForDisplay` | section masquée, titre compris |
| Synchro bancaire, import automatique au démarrage | `AutoCloudImportView.shouldDisplay` | jamais affiché |
| Synchro bancaire, mise à jour des comptes après un import | `CloudService.updateAccounts` | aucun appel |
| Synchro bancaire, tout autre appel réseau | `CloudService.isDisabled`, en tête de chaque méthode réseau | erreur « synchro désactivée » renvoyée au rappel, ligne `[Cloud]` dans le log |
| Synchro bancaire, menu de dev | `MenuBarBuilder.createDevMenu` | « Run cloud demo » et « Show synchro error » absents |

Déjà débranchés avant cette décision : le menu « Activer les extensions » (supprimé en 6.00) et le
menu mobile (commenté dans `MenuBarBuilder`).

**Synchro bancaire : désactivée, pas supprimée** (décision de Matthieu, 2026-11-16). Un agrégateur pourrait
resservir un jour, peut-être dans une offre payante. Le code (`desktop/cloud`, les écrans `ImportCloud*`,
`budgea/`, `shared.cloud`) et le modèle (`CloudDesktopUser`, `CloudProviderConnection`, champs `PROVIDER_*` de
`RealAccount`) restent en place. Une reprise demanderait aussi un nouveau serveur : le client envoyait les identifiants
bancaires directement à Budgea (`BudgeaAPI`, `budgetview.biapi.pro`), mais obtenait son jeton, ses connexions et
ses relevés par `register.budgetview.fr` (`CloudAPI`), donc par `bv_server`.

**Supprimé depuis** (audit du code, chantier 1a, [audit-code.md](audit-code.md)) : l'accès mobile, le
stockage des données sur le serveur (propriété `budgetview.server.url`), et les envois au support (avis,
logs, fichier importé anonymisé, demande d'évaluation, statistiques d'usage, envoi par mail du relevé des
threads du menu de dev, avec `ServerMailingService`), le bandeau « nouvelle
version », et **toute la licence** : statut, activation, écran des extensions, téléchargement du jar et
de la configuration des banques (qui chargeait des classes depuis le dossier `configs/` des données).
La configuration des banques vient toujours de celle intégrée au programme. `User.CONNECTED` reste faux.

Le téléchargement automatique du jar, qui aurait pu exécuter le code d'un serveur tiers si le domaine
`budgetview.fr` changeait de mains, n'existe plus depuis la 6.0.1.

## Tests

- `LocalModeTest` vérifie que le menu Aide ne propose plus les anciens envois au support.
- Les tests fonctionnels vident l'URL de serveur (`LicenseConstants.LICENSE_URL_PROPERTY`) : même avec
  l'interrupteur actif, ils ne sortent pas sur le réseau.
