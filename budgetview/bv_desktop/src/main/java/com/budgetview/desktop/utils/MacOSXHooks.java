package com.budgetview.desktop.utils;

import com.budgetview.desktop.startup.components.OpenFilesHandler;
import com.budgetview.utils.Lang;
import org.globsframework.gui.splits.utils.GuiUtils;
import org.globsframework.utils.directory.Directory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Intégration macOS : nom et barre de menus de l'application, menus « À propos », « Préférences », « Quitter »,
 * ouverture de fichiers par le Finder (par {@link java.awt.Desktop}) et plein écran.
 */
public class MacOSXHooks {

  private static final List<OpenFilesHandler> openFilesHandlers = new ArrayList<OpenFilesHandler>();

  private MacOSXHooks() {
  }

  /** Nom du menu application quand on lance un jar (un .app jpackage prend celui de son Info.plist). */
  public static void initApplicationName() {
    System.setProperty("apple.awt.application.name", Lang.get("application"));
  }

  /**
   * Barre de menus en haut de l'écran, comme en 5.01. À appeler avant la création de la première fenêtre.
   * Pas dans les tests : uispec4j remplace le toolkit et n'a pas de barre de menus native.
   */
  public static void useScreenMenuBar() {
    if (GuiUtils.isMacOSX()) {
      System.setProperty("apple.laf.useScreenMenuBar", "true");
    }
  }

  public static void install(Action aboutAction, Action preferencesAction, Action exitAction, Directory directory) {
    if (!GuiUtils.isMacOSX()) {
      return;
    }
    try {
      installDesktopHandlers(aboutAction, preferencesAction, exitAction);
    }
    catch (Exception ex) {
      System.out.println("Failed to register with OSX: " + ex);
    }
    enableOSXFullscreen(directory.get(JFrame.class));
  }

  private static void installDesktopHandlers(Action aboutAction, Action preferencesAction, Action exitAction) {
    if (!Desktop.isDesktopSupported()) {
      System.out.println("OSX: java.awt.Desktop not supported");
      return;
    }
    Desktop desktop = Desktop.getDesktop();
    if (isSupported(desktop, Desktop.Action.APP_ABOUT)) {
      desktop.setAboutHandler(event -> aboutAction.actionPerformed(null));
    }
    if (isSupported(desktop, Desktop.Action.APP_PREFERENCES)) {
      desktop.setPreferencesHandler(event -> preferencesAction.actionPerformed(null));
    }
    if (isSupported(desktop, Desktop.Action.APP_QUIT_HANDLER)) {
      desktop.setQuitHandler((event, response) -> {
        exitAction.actionPerformed(null);
        // ExitAction termine la JVM. Si elle revient (sortie déjà en cours, ou realExit=false dans les tests),
        // on annule la demande pour que macOS ne reste pas en attente d'une réponse.
        response.cancelQuit();
      });
    }
    if (isSupported(desktop, Desktop.Action.APP_OPEN_FILE)) {
      desktop.setOpenFileHandler(event -> openFiles(event.getFiles()));
    }
  }

  private static boolean isSupported(Desktop desktop, Desktop.Action action) {
    if (!desktop.isSupported(action)) {
      System.out.println("OSX: unsupported desktop action " + action);
      return false;
    }
    return true;
  }

  /**
   * Bouton vert de la fenêtre en plein écran. Propriété lue par sun.lwawt.macosx.CPlatformWindow :
   * com.apple.eawt.FullScreenUtilities existe encore, mais java.desktop ne l'exporte plus.
   */
  private static void enableOSXFullscreen(Window window) {
    if (window instanceof RootPaneContainer container) {
      container.getRootPane().putClientProperty("apple.awt.fullscreenable", Boolean.TRUE);
    }
  }

  private static void openFiles(List<File> files) {
    if (files == null) {
      return;
    }
    synchronized (openFilesHandlers) {
      for (OpenFilesHandler handler : openFilesHandlers) {
        handler.run(files);
      }
    }
  }

  public static void addOpenDocumentListener(OpenFilesHandler handler) {
    synchronized (openFilesHandlers) {
      openFilesHandlers.add(handler);
    }
  }

  public static void removeOpenDocumentListener(OpenFilesHandler handler) {
    synchronized (openFilesHandlers) {
      openFilesHandlers.remove(handler);
    }
  }
}
