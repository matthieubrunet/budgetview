package com.budgetview.desktop.utils;

import junit.framework.TestCase;
import org.globsframework.gui.splits.utils.MouseModifiersTest;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;

/**
 * Fige Gui.isAddModifier et Gui.hasModifiers, écrites à l'origine avec les anciens masques (audit 4c, lot B).
 * Dans ces masques, META_MASK = BUTTON3_MASK et ALT_MASK = BUTTON2_MASK : un clic droit ou du milieu compte comme
 * une touche de sélection additive. Ce défaut est volontairement gardé.
 */
public class GuiModifiersTest extends TestCase {

  @SuppressWarnings("deprecation")
  public void testIsAddModifierIsSameAsLegacyMasks() throws Exception {
    int mask = InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK | InputEvent.ALT_MASK | InputEvent.META_MASK;
    MouseModifiersTest.checkSameAsLegacy("isAddModifier", Gui::isAddModifier,
                                         e -> (MouseModifiersTest.legacyModifiers(e) & mask) != 0);
  }

  @SuppressWarnings("deprecation")
  public void testHasModifiersIsSameAsLegacyMasks() throws Exception {
    int mask = InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK | InputEvent.ALT_MASK | InputEvent.META_MASK;
    for (int modifiers = 0; modifiers < 64; modifiers++) {
      ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "", modifiers);
      assertEquals("modifiers=" + modifiers, (modifiers & mask) != 0, Gui.hasModifiers(event));
    }
  }
}
