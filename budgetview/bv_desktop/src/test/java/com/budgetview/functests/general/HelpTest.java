package com.budgetview.functests.general;

import com.budgetview.desktop.Application;
import com.budgetview.functests.utils.LoggedInFunctionalTestCase;
import com.budgetview.desktop.startup.components.AppLogger;
import com.budgetview.utils.Lang;
import org.junit.Test;

import java.io.PrintStream;
import java.util.Date;

public class HelpTest extends LoggedInFunctionalTestCase {

  // Les envois au support passent par le serveur d'origine : menu masqué sauf si on réactive le serveur.
  // La fenêtre est partagée par les tests de la classe : le réglage vaut pour toute la classe.
  protected void setUp() throws Exception {
    System.setProperty(Application.SERVER_ENABLED_PROPERTY, "true");
    super.setUp();
  }

  protected void tearDown() throws Exception {
    System.clearProperty(Application.SERVER_ENABLED_PROPERTY);
    super.tearDown();
  }

  @Test
  public void testHelpMenu() throws Exception {
    operations.checkGotoSupport(Lang.get("site.support.url"));
  }

  @Test
  public void testDefaultLinks() throws Exception {
    operations.checkGotoSupport("http://www.budgetview.fr/support");
  }

  @Test
  public void testSendLogs() throws Exception {

    String text = new Date().toString();

    PrintStream stream = new PrintStream(AppLogger.getLogFile());
    stream.append("Date: " + text);
    stream.close();

    operations.openSendLogs()
      .checkTitle("Send logs")
      .checkMessageContains("support@budgetview.fr")
      .checkDetailsContain(text)
      .checkCopy()
      .close();
  }
}