# Règles ProGuard du chantier 1e (docs/audit-code.md) : analyse seule, rien n'est réduit ni renommé.
# usage.sh y ajoute les -injars, -libraryjars et un -keep par classe nommée dans un .splits.
-dontoptimize
-dontobfuscate
-dontpreverify
-ignorewarnings
-dontnote
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-printusage usage.txt

-keep class com.budgetview.desktop.DesktopApp { public static void main(java.lang.String[]); }

# globs : GlobTypeLoader lit les champs publics statiques des types et leurs annotations,
# DefaultFieldFactory appelle value() sur les annotations.
-keepclassmembers class * { public static org.globsframework.metamodel.** *; }
-keep @interface * { *; }

# splits : PropertySetter pose les attributs des .splits par les setters publics, sur les composants,
# les UI et les layouts. UIService et LayoutService instancient UI et layouts par leur nom
# (ApplicationLAF les enregistre par Class.getName()). Les look & feel appellent createUI.
-keepclassmembers class * extends java.awt.Component { public void set*(***); }
-keepclassmembers class * extends javax.swing.plaf.ComponentUI {
  public <init>();
  public void set*(***);
  public static javax.swing.plaf.ComponentUI createUI(javax.swing.JComponent);
}
-keepclassmembers class * implements java.awt.LayoutManager { public <init>(); public void set*(***); }

# JDK : énumérations et sérialisation
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers class * implements java.io.Serializable {
  static final long serialVersionUID;
  private void writeObject(java.io.ObjectOutputStream);
  private void readObject(java.io.ObjectInputStream);
  java.lang.Object writeReplace();
  java.lang.Object readResolve();
}
