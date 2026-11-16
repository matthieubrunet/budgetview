package com.budgetview.functests.general;

import com.budgetview.functests.utils.LoggedInFunctionalTestCase;
import com.budgetview.utils.Lang;
import org.junit.Test;

public class HelpTest extends LoggedInFunctionalTestCase {

  @Test
  public void testHelpMenu() throws Exception {
    operations.checkGotoSupport(Lang.get("site.support.url"));
  }

  @Test
  public void testDefaultLinks() throws Exception {
    operations.checkGotoSupport("http://www.budgetview.fr/support");
  }
}
