package com.budgetview.desktop.browsing;

import org.globsframework.utils.Log;

import java.awt.*;
import java.net.URI;

/**
 * Ouvre une adresse dans le navigateur du système, par {@link Desktop#browse}.
 * <p/>
 * Un échec (adresse mal formée ou sans schéma, comme « www.exemple.fr » saisi par l'utilisateur) est seulement
 * journalisé, comme avec les commandes {@code open}, {@code rundll32} et {@code firefox} d'avant : une exception
 * dans l'IHM lancerait le contrôle des données.
 */
public class BrowsingService {

  private static boolean useDummy = false;

  public static void setDummyBrowser(boolean useDummy) {
    BrowsingService.useDummy = useDummy;
  }

  public static BrowsingService createService() {
    if (useDummy) {
      return new DummyBrowsingService();
    }
    return new BrowsingService();
  }

  public void launchBrowser(String url) {
    try {
      if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Log.write("[Browsing] java.awt.Desktop cannot browse, url not opened: " + url);
        return;
      }
      Desktop.getDesktop().browse(new URI(url));
    }
    catch (Exception e) {
      Log.write("[Browsing] Could not open url: " + url, e);
    }
  }
}
