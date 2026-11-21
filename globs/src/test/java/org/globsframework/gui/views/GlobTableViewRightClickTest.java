package org.globsframework.gui.views;

import junit.framework.TestCase;
import org.globsframework.gui.splits.utils.MouseModifiersTest;

import java.awt.event.MouseEvent;

/** Fige la condition de sélection au clic droit, écrite à l'origine avec les anciens masques (audit 4c, lot B). */
public class GlobTableViewRightClickTest extends TestCase {

  @SuppressWarnings("deprecation")
  public void testIsRowSelectingClickIsSameAsLegacyMasks() throws Exception {
    MouseModifiersTest.checkSameAsLegacy(
      "isRowSelectingClick", GlobTableView::isRowSelectingClick,
      e -> ((MouseModifiersTest.legacyModifiers(e) & MouseEvent.BUTTON3_MASK) != 0) &&
           (MouseModifiersTest.legacyModifiers(e) & MouseEvent.CTRL_MASK) == 0);
  }
}
