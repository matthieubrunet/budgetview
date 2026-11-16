# Migration de Java 8 vers un Java récent

État au 2026-11-16 : étapes 1 à 5 faites. **Le code est compilé en `--release 21` et la référence est
OpenJDK 21** (Homebrew). Java 8 ne peut plus ni construire ni exécuter l'application. Reste l'étape 6 (FlatLaf).

## Décision : rester sur la JVM

La question d'une réécriture dans un langage « plus moderne » (Rust/Flutter, par exemple) a été posée et
écartée. Une réécriture repartirait de zéro sur :

- ~105 000 lignes d'application (dont ~76 000 d'IHM) et 149 écrans `.splits` ;
- ~43 000 lignes de globs, le framework réactif sur lequel tout repose ;
- ~63 000 lignes de tests ;
- le format des données (Prevayler + sérialisation globs), à relire ou à exporter.

Ce qui a vieilli, c'est Swing, pas Java. Le plan retenu :

1. passer à un Java récent (cible **Java 21**, LTS ; 25 ensuite sans grande difficulté) ;
2. moderniser l'apparence sans réécrire les écrans (FlatLaf à la place de JGoodies Looks) ;
3. ne trancher qu'ensuite sur une IHM réécrite. Si on la réécrit, ce serait avec **Kotlin + Compose
   Multiplatform**, sur la même JVM, écran par écran, en gardant le modèle, les triggers et les imports.

Une réécriture complète ne se justifierait que pour une appli mobile qui partage le code, pour attirer des
contributeurs, ou pour repenser le produit.

## Pourquoi quitter Java 8

- **Distribution** : `jpackage` (Java 14 et plus) produit des installeurs `.dmg`/`.msi`/`.deb` qui embarquent
  leur Java. Avec une identité propre, l'application a aussi ses propres autorisations macOS (voir plus bas).
- **Durée de vie** de Java 8 sur les futurs macOS.
- **Écrans haute résolution** gérés sous Windows et Linux depuis Java 9.
- **Bibliothèques** récentes qui exigent Java 11 ou 17.

## Inventaire de ce qui bloque

| Point | Constat | Effort estimé |
|---|---|---|
| Code de l'application | Presque aucune API interne de Java. Aucune lambda : le code est de style Java 6. | faible |
| `MacOSXHooks` | `com.apple.eawt` a disparu en Java 9 : plus de menus « À propos », « Préférences » ni « Quitter », plus d'ouverture de fichier par double-clic. **Porté** (2026-11-16) vers `java.awt.Desktop`, voir plus bas. | — |
| Plugins Maven | **Fait** (2026-11-15) : tous en 3.0.x ; compiler en 3.13.0 avec `<release>21</release>` depuis le 2026-11-16. | — |
| Shrinker | Lit les `.class` avec ASM 3.1 (2007), qui ne connaît pas `invokedynamic`. Ça marche aujourd'hui faute de lambdas, mais une compilation pour Java 9+ en met dans chaque concaténation de chaînes. Passer à ASM 9 (l'API a changé : `MethodAdapter` n'existe plus) ou supprimer le shrinker, qui n'a plus d'intérêt avec `jpackage`. **Supprimé** (2026-11-15), voir plus bas. | — |
| JGoodies Looks 2.2.0 | Thème Windows/Linux de 2008, avec des classes du projet dans le paquet de JGoodies. Fonctionnera probablement, à vérifier à l'écran. | à tester |
| `SecurityManager` de `FunctionalTestCase` | Il fallait un drapeau en Java 17-23, et il est désactivé pour de bon en Java 24. **Remplacé** (2026-11-16) par un point de sortie unique, `AppExit`, voir plus bas. | — |
| **uispec4j** | Sous-classe `sun.awt.SunToolkit` et les peers AWT. **Porté** (2026-11-15) en une dizaine de lignes, variante `java-jdk17`, voir plus bas. L'instabilité de `PrintTest` est corrigée (2026-11-16). | — |

N'empêchent pas la migration : log4j 1.2 (fin de vie, sans conséquence en local), joda-time, les
`new Integer(...)` (avertissements).

## Ordre proposé

1. ~~Plugins Maven en 3.x, sous Java 8.~~ Fait.
2. ~~Sort du shrinker : le supprimer ou passer à ASM 9.~~ Supprimé.
3. ~~Essai sur uispec4j sous Java 17.~~ Fait : porté, ce qui décide pour l'**option B** (tout passer à un
   Java récent : compilation, exécution, tests), plutôt que l'option A (exécuter sur Java 21, mais
   compiler et tester sous Zulu 8).
4. ~~Hooks Mac vers `java.awt.Desktop`, avec remise de `apple.laf.useScreenMenuBar`.~~ Fait.
5. ~~Passage effectif : stabiliser `PrintTest`, remplacer le `SecurityManager`, décider des formats CLDR,
   installer un JDK 21, compiler en `--release 21`, en faire la référence.~~ Fait (2026-11-16), voir plus bas.
6. Ensuite seulement : FlatLaf.

## Passage à Java 21 (2026-11-16)

- **JDK** : `brew install openjdk@21` (keg-only, sans sudo), dans
  `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. Version installée : 21.0.13.
- **Compilation** : `maven-compiler-plugin` 3.13.0 et `<release>21</release>` dans les 8 `pom.xml` du
  réacteur. Aucun module n'hérite de la racine : chacun porte son bloc, et saxstack n'en avait pas (défaut de
  Maven). Bytecode en version 65.
- **uispec4j garde `-source 8 -target 8`** : `--release` interdit les `--add-exports` vers les modules du JDK,
  alors qu'uispec4j sous-classe `sun.awt.SunToolkit`. Il se construit sous JDK 21 comme avant. Le réacteur, lui,
  n'importe aucune API interne (`sun.*`, `com.apple.*` : vérifié par recherche, tout passe par réflexion).
- **Avertissements** « marked for removal » dans l'application : 4 `finalize()` (`TextFilterPanel`,
  `HyperlinkTableColumn`, et dans globs `FillPainter`, `GradientPainter`, `ReplicationGlobRepository`), des
  `new Integer/Long/Double(...)`. Sans effet en Java 21 ; `finalize` est à remplacer avant qu'il ne disparaisse.
- **Formats CLDR acceptés** : pas de `-Djava.locale.providers=COMPAT` (supprimé en Java 23). Aucun test n'y réagit.
  Relecture du code (2026-11-16), rien ne dépend des formats régionaux de Java :
  - montants **affichés** sans séparateur de milliers, en `Locale.US` (`AmountFormat`, `Formats` de globs) ;
  - montants **saisis** (`GlobNumericEditor`, `SplitTransactionDialog`) lus par `Double.parseDouble`, après
    remplacement de `,` par `.` ;
  - montants **importés** (CSV, QIF, OFX, synchro) lus par `Amounts.extractAmount`, qui retire tout ce qui
    n'est ni chiffre, ni signe, ni `,`/`.` : une espace de milliers, insécable ou fine, disparaît ;
  - dates : motifs fixés, chiffres seulement à l'import ; les deux formats à mois en lettres
    (`yearMonthFormat`, `LOCAL_TIME_STAMP`) servent à l'affichage, et CLDR n'y change rien en français.
- **Tests** :

  | Configuration | Résultat |
  |---|---|
  | JDK 21, bytecode 1.8 | 1 034 tests, 2 échecs intermittents (`TimeViewPanelUISpecTest…_3`, déjà connu sous 17 ; `SignpostSequenceTest.testSwitchingToTheDemoAccountAndBackPreservesSignposts`, `budgetCardToggle` introuvable alors qu'il est dans l'arbre) ; tous deux verts 3 fois sur 3 à la relance |
  | JDK 21, `--release 21` | 1 034 tests, tous verts, en ~9 min 30 |

- **Application** : `run-dev.sh` exige désormais un JDK 21 (défaut : celui de Homebrew). Le livrable
  `target/dist` démarre sous 21, sans exception dans le log.

## Hooks Mac sur `java.awt.Desktop` (2026-11-16)

`MacOSXHooks` passe d'abord par `java.awt.Desktop` (Java 9+), sinon par `com.apple.eawt` (Java 8). Les
deux API ont les mêmes noms de méthodes (`handleAbout`, `handlePreferences`, `handleQuitRequestWith`,
`openFiles`) : un seul `InvocationHandler` sert aux deux. Tout reste en réflexion, pour compiler en cible 1.8.

- **Plein écran** : sous Java 9+, propriété `apple.awt.fullscreenable` du `JRootPane`.
  `com.apple.eawt.FullScreenUtilities` existe encore en Java 17, mais `java.desktop` ne l'exporte plus
  (`IllegalAccessException`) : il n'est appelé que sous Java 8.
- **Méthodes des événements** (`getFiles`, `cancelQuit`) lues sur le type déclaré du paramètre, public,
  et non sur la classe réelle de l'objet, qui peut être interne au JDK.
- **Quitter** : `ExitAction` termine la JVM ; si elle revient (sortie déjà en cours, `realExit=false`),
  la demande est annulée (`cancelQuit`) pour que macOS n'attende pas de réponse.
- **Barre de menus en haut de l'écran** : `apple.laf.useScreenMenuBar`, posée dans `Application.main`
  seulement. Les tests n'y passent pas : uispec4j remplace le toolkit, sans barre de menus native.
- **Nom de l'application** : `apple.awt.application.name` (l'ancienne propriété `com.apple.mrj…` est ignorée).
- **Vérifié à l'écran** sous Zulu 8 et OpenJDK 17, comportement identique : barre en haut, À propos, Préférences, plein écran, ⌘Q.
  Les entrées du menu application (« About », « Preferences… », « Quit ») sont en anglais : macOS les
  nomme d'après la langue que déclare le bundle, et le lanceur `java` n'en déclare pas. À régler dans
  l'`Info.plist` du `.app` produit par `jpackage` (`CFBundleDevelopmentRegion` = `fr`).
- **Pas vérifié** : l'ouverture d'un fichier par le Finder, qui demande un `.app` déclarant ses types
  de fichiers (étape `jpackage`).

## Sortie de l'application : `AppExit` (2026-11-16)

Les tests ne doivent pas laisser l'application tuer la JVM de surefire. `FunctionalTestCase` installait
pour cela un `SecurityManager` dont `checkExit` levait une exception, à condition de trouver
`Runtime.exit` puis une classe `com.budgetview.desktop` dans la pile : Swing appelle aussi `checkExit()`
sans quitter (`setDefaultCloseOperation(EXIT_ON_CLOSE)`).

Il est remplacé par `com.budgetview.desktop.utils.AppExit` : toutes les sorties de l'application
(`Application`, `ExitAction`, `ShowDialogAndExitExceptionHandler`) appellent `AppExit.exit(code)`, qui fait
`System.exit` par défaut. `FunctionalTestCase` installe un `Handler` qui lève une `IllegalStateException`.
Le drapeau `-Djava.security.manager=allow` a disparu du profil `jdk17`.

- **Vérifié** : suite complète sous Zulu 8 (1 034 tests, tous verts) ; sous OpenJDK 17 sans le drapeau,
  `DataCheckerTest` et `PrintTest` (7 tests, verts, plus d'avertissement sur `setSecurityManager`).
  Les piles `RuntimeException: Exception test` du log viennent de `DataCheckerTest`, qui les provoque exprès.

- **Règle** : une nouvelle sortie dans `com.budgetview.desktop` passe par `AppExit.exit`, jamais par `System.exit`.
- Restent sur `System.exit`, hors du périmètre de l'ancien `SecurityManager` : la fermeture de la fenêtre
  par `EXIT_ON_CLOSE` (`PicsouFrame`, appel venant de `JFrame`), les outils en ligne de commande
  (`tools/BackupClinic`) et les `main` de test (`DevDesktopApp`, `DemoGenerationTest`).

## Portage d'uispec4j et tests sous Java 17 (2026-11-15)

**Compilation.** Sous javac 17 avec `--add-exports`, 8 erreurs seulement, toutes mécaniques :

| Erreur | Correction |
|---|---|
| `sun.awt.CausedFocusEvent` n'est plus public | `java.awt.event.FocusEvent.Cause`, public depuis Java 9 |
| `createComponent`, `getMouseInfoPeer`, `createDesktopPeer` sont publics dans `ComponentFactory` | passés en `public` (sans effet en Java 8) |
| `Toolkit.createDragSourceContextPeer` a disparu | appel via `ComponentFactory` |
| `SunToolkit.isTaskbarSupported()` abstraite | ajoutée, `false` |

Les classes propres à Java 9+ sont dans `src/main/java-jdk17`, copie de `java-jdk18`, choisie par le profil
`jdk17` (`<jdk>[9,)</jdk>`). Le reste est dans le code commun, compatible Java 8. Point utile : avec
`-source 8 -target 8`, javac 17 ne contrôle pas les modules et compile les `sun.*` **sans aucun
`--add-exports`** (il les refuse même : « not allowed with target 8 »). Seule l'exécution demande des
`--add-opens` (liste dans le profil `jdk17` de `bv_desktop/pom.xml`, propriété `jdk.test.args`).

**Exécution.** Un seul blocage : Java 9+ ne renseigne plus la propriété `awt.toolkit`, et uispec4j ne
connaissait en repli que `WToolkit` (Windows) et `MToolkit` (Motif, disparu). Ajout de
`sun.lwawt.macosx.LWCToolkit` et `sun.awt.X11.XToolkit`. Il fallait aussi `-Djava.security.manager=allow`
pour le `SecurityManager` de `FunctionalTestCase`, retiré depuis (section suivante).

**Résultats.**

| Suite | Java 8 | Java 17 |
|---|---|---|
| tests propres d'uispec4j (894, lancés hors Maven : TestNG 5.0 trop vieux pour surefire) | 31 échecs | les mêmes 31 + 5 |
| bv_desktop (1 030) | tout passe | 1 028 ; les 2 échecs ne se reproduisent pas tels quels |

- Les 5 échecs d'uispec4j propres à Java 17 : 4 descriptions de `JSpinner` (Swing nomme ses flèches
  `Spinner.nextButton` depuis Java 9) et un format de date (CLDR, ci-dessous). Rien dans l'interception.
- `TimeViewPanelUISpecTest.testScrollToVisibleDoNotScrollTooMuch_3` : échoue une fois, passe à la relance.
- **`PrintTest` est instable sous 17** : en trois lancements, un échec sur un test, un autre sur un autre
  test, puis tout vert ; 3 fois sur 3 vert sous Java 8. Premier échec : `NullPointerException` dans Swing
  (`this.invokerRootPane` est null) à l'ouverture d'un menu déroulant par `PopupMenuInterceptor` ; second :
  exception dans un trigger de catégorisation, `System.exit(10)` par `ShowDialogAndExitExceptionHandler`.
  **Corrigé le 2026-11-16** : deux défauts distincts, aucun propre à Java 17 (détail ci-dessous).

### Stabilisation de `PrintTest` (2026-11-16)

Mesures sous JDK 17 : 1 échec sur 6 passages avant correction, 3 sur 12 après le premier correctif,
0 sur 15 après les deux.

1. **Comparateur incohérent, bogue de l'application.** L'« exception dans un trigger » était un
   `IllegalArgumentException: Comparison method violates its general contract!` levé par TimSort dans le tri
   du panneau de rapprochement (`ReferenceTransactionComparator`). Le départage par libellé lisait deux fois
   le libellé de la première opération et répondait -1 dès qu'elle portait le libellé de référence, sans
   regarder la seconde. Deux opérations au même libellé donnaient donc `compare(a,b) = compare(b,a) = -1`.
   TimSort ne le détecte que pour certains ordres d'entrée, d'où l'échec aléatoire. Java 8 a la même
   vérification : il passait par chance. En usage réel, l'erreur ferme l'application (`System.exit(10)`).
   Couvert par `ReferenceTransactionComparatorTest`, qui contrôle le contrat sur toutes les paires et tous
   les triplets.
2. **Menu fermé hors de l'EDT, défaut des tests.** `PopupChecker.close` faisait `setVisible(false)` sur le
   menu depuis le fil du test, ce qui modifie le `MenuSelectionManager` de Swing. Après un `click()` qui
   ouvre une fenêtre modale, ce fil ne reprend qu'à la fermeture de la fenêtre, alors que le test ouvre
   déjà le menu suivant dans l'EDT. Les deux modifient la même liste en même temps :
   `ArrayIndexOutOfBoundsException: 0 >= 0` dans `MenuSelectionManager.setSelectedPath` (vu dans
   `SavingsSetup`, deux éditions de compte de suite), ou la `NullPointerException` sur `invokerRootPane`.
   La fermeture (et le `doClick` de `select`) passe maintenant par `TriggerRunner.runInSwingThread`.
   À retenir : dans un checker, **toute action Swing passe par l'EDT**. Les `setVisible(false)` de la
   fenêtre principale dans `LoggedInFunctionalTestCase` ont encore ce défaut.

**Formats régionaux (CLDR).** Depuis Java 9, les formats viennent des données CLDR. Relevé en français :

| | Java 8 | Java 17 |
|---|---|---|
| séparateur de milliers de `NumberFormat` | U+00A0 (espace insécable) | **U+202F** (espace fine insécable) |
| date courte | `15/11/26` | `15/11/2026` |
| date moyenne, mois, jours | `15 nov. 2026`, `novembre 2026`, `dim. 15 nov.` | identiques |

BudgetView fixe presque partout ses motifs (`SimpleDateFormat("…")`) et formate les montants avec
`Locale.US` : aucun test de bv_desktop n'y a réagi. `-Djava.locale.providers=COMPAT` rétablit les formats
de Java 8, mais cette option disparaît en Java 23 : mieux vaut accepter CLDR.

**Changer de JDK** : le jar d'uispec4j dans `~/.m2` ne vaut que pour le JDK qui l'a construit (la variante
Java 17 référence `FocusEvent.Cause`, absente de Java 8). Réinstaller uispec4j, puis `clean install` du
réacteur, à chaque changement de JDK pour les tests.

## Suppression du shrinker (2026-11-15)

Le profil `gen-version` produit maintenant `bv_desktop/target/dist` : `budgetview.jar` (les classes de
bv_desktop, avec un `Class-Path` vers `lib/`), `lib/` (les bibliothèques, copiées par
`maven-dependency-plugin`) et `config.jar`. C'est directement l'`--input` de `jpackage`.

- **Taille** : 12 Mo au lieu de 9 Mo. Sans importance : un installeur `jpackage` embarque de toute façon un
  Java de plusieurs dizaines de Mo.
- **Bibliothèques écartées** du livrable : hsqldb, mysql-connector, joda-time, xercesImpl, xml-apis,
  slf4j (×2), servlet-api. Le jar réduit par le shrinker n'en contenait aucune classe (sauf 3 classes de
  `xml-apis` déjà présentes dans le JDK) : elles sont tirées par globs (`sqlstreams`) ou servent pour
  des constantes (`HttpServletResponse` dans `MobileService`). Elles restent dans le classpath des tests.
- **Contrôle fait** : les 5 044 classes du dernier jar réduit se retrouvent toutes dans `target/dist`,
  sauf ces 3 classes de `xml-apis`. L'application démarre depuis `target/dist` sous Zulu 8 et OpenJDK 17.
- **Disparus avec lui** : les listes `dev/ant/inputClient.txt` (classes chargées par réflexion, à tenir à
  la main) et `ignoreClient.txt`, ainsi que `dump.jar`. Pour l'outil de dump :
  `java -cp target/dist/budgetview.jar com.budgetview.persistence.direct.Dump`.
- **ASM reste** une dépendance de l'application : `KeyService` s'en sert pour fabriquer à la volée la
  classe qui vérifie les signatures de licence. Elle ne fait qu'écrire du bytecode ancien, ce qui marche
  sur tout Java ; elle partira avec le code des licences, devenu inutile en usage local.
- `generate_version.sh` et `bv_server/generate_release.sh` (chaîne de livraison d'origine, obfuscation
  ZKM, `target/obfuscated`) étaient déjà hors service : ils n'ont pas été adaptés.

## Essai d'exécution sous Java 17 (2026-11-15)

Le `budgetview.jar` actuel (compilé en 1.8) a tourné sous OpenJDK 17.0.21 (Homebrew), sur une copie
des données de dev, et a été comparé au même jar sous Zulu 8.

| Point | Résultat |
|---|---|
| Démarrage, écrans, rendu, compte de démo, fermeture | identique à Java 8, aucune exception ni accès interdit |
| Hooks Mac | **seule régression** : `ClassNotFoundException: com.apple.eawt.QuitHandler` |
| Menus dans la fenêtre au lieu de la barre système | pareil sous Java 8 : vient de la 6.00, pas de Java (voir plus bas) |
| Dialogue d'import qui affiche une liste vide | pareil sous Java 8 : autorisation macOS sur Dropbox (voir `CLAUDE.md`) |

L'exécution sous Java 17 ne coûte donc qu'environ une demi-journée. Le chantier, c'est la compilation
et les tests.

### Menus dans la fenêtre depuis la 6.00

En 5.01, `PicsouMacLookAndFeel` étendait `apple.laf.AquaLookAndFeel` : une sous-classe vide du thème Aqua
standard, embarquée dans le jar de la 5.01. Elle positionnait `apple.laf.useScreenMenuBar=true`. Le dernier
commit amont a commenté son installation ([Gui.java:71](../budgetview/bv_desktop/src/main/java/com/budgetview/desktop/utils/Gui.java#L71)),
parce que la classe ne compilait plus. Java retombe de toute façon sur le thème Aqua. Il suffit donc de
remettre la propriété système avant de créer la première fenêtre, sans le look & feel. **Fait** le 2026-11-16.

La 5.01 utilisait elle aussi le `JFileChooser` de Swing, et non le sélecteur natif `java.awt.FileDialog`.
