package com.budgetview.functests.general;

import com.budgetview.functests.utils.LoggedInFunctionalTestCase;
import org.junit.Test;

/** Usage local seulement (défaut depuis la 6.00) : rien ne doit proposer de contacter les serveurs d'origine. */
public class LocalModeTest extends LoggedInFunctionalTestCase {

  @Test
  public void testHelpMenuHasNoServerActions() throws Exception {
    operations.checkHelpMenuWithoutServerActions();
    operations.checkGotoSupport("http://www.budgetview.fr/support");
  }
}
