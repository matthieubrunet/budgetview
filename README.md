# budgetview
It is a app to manage personnal finance.

The code behind https://budgetview.fr

It use a a private old version of globsFramework (https://github.com/MarcGuiot/globsframework)

mvn -Pgen-version clean install -DskipTests=true
Le livrable est dans budgetview/bv_desktop/target/dist (budgetview.jar + lib/ + config.jar).

generate an exe ; under windows do 
jpackage --input budgetview/bv_desktop/target/dist --main-jar budgetview.jar --name budgetview --app-version 6.0 --win-menu  --win-dir-chooser

Pour linux :
jpackage --input budgetview/bv_desktop/target/dist --dest version --main-jar budgetview.jar --name budgetview --app-version 6.0 --type rpm
