Contenu du site budgetview.fr : pages (fr/content, en/content), gabarits (templates), fichiers a copier
(files, resources) et description de l'arborescence (fr/siteweaver.xml, en/siteweaver.xml).

Ces pages sont des fragments HTML. Elles etaient assemblees avec les gabarits, previsualisees
(http://localhost:8080) et envoyees par FTP par le generateur maison SiteWeaver, module siteweaver/ du depot.

Ce module a ete supprime le 2026-11-21 : rien d'autre ne l'utilisait, et l'hebergement d'origine du site
n'est plus accessible. Il reste dans l'historique git. Pour le retrouver dans son dernier etat (67 tests verts) :

   git show 7fc3ff608:siteweaver/README.txt
   git checkout 7fc3ff608 -- siteweaver      (puis remettre <module>siteweaver</module> dans le pom.xml racine)

Il se lancait par la classe com.designup.siteweaver.SiteWeaver, avec comme parametres :

   <fichier siteweaver.xml> [<@IP serveur ftp> <path ftp> <user ftp> <pwd ftp>]

soit :

   budgetview/bv_website/fr/siteweaver.xml <ftp> / <user ftp> <pwd ftp>
