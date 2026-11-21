package org.globsframework.gui.splits.utils;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fige le comportement des tests de boutons et de touches, écrits à l'origine avec les anciens masques
 * (MouseEvent.getModifiers(), BUTTON3_MASK…, dépréciés depuis Java 9). Dans ces masques, META_MASK = BUTTON3_MASK et
 * ALT_MASK = BUTTON2_MASK : un clic avec Meta (Cmd) compte comme un clic droit. Ce défaut est volontairement gardé
 * (audit 4c, lot B) ; sa correction attend le chantier des tests unitaires.
 */
public class MouseModifiersTest extends TestCase {

  private static final int[] IDS = {
    MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED,
    MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_EXITED
  };
  private static final int[] KEYS_EX = {
    InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.META_DOWN_MASK
  };
  private static final int[] BUTTONS_EX = {
    InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK
  };
  @SuppressWarnings("deprecation") // anciens masques : c'est la référence du test
  private static final int[] LEGACY = {
    InputEvent.SHIFT_MASK, InputEvent.CTRL_MASK, InputEvent.ALT_MASK, InputEvent.META_MASK, InputEvent.BUTTON1_MASK
  };

  /** Tous les événements que la souris peut produire, construits comme AWT (masques étendus) et à l'ancienne. */
  public static List<MouseEvent> allMouseEvents() {
    Component source = new JPanel();
    List<MouseEvent> events = new ArrayList<>();
    for (int id : IDS) {
      for (int button = MouseEvent.NOBUTTON; button <= MouseEvent.BUTTON3; button++) {
        for (int keys = 0; keys < (1 << KEYS_EX.length); keys++) {
          for (int held = 0; held < (1 << BUTTONS_EX.length); held++) {
            int ex = combine(KEYS_EX, keys) | combine(BUTTONS_EX, held);
            events.add(new MouseEvent(source, id, 0, ex, 0, 0, 1, false, button));
          }
        }
      }
      for (int legacy = 1; legacy < (1 << LEGACY.length); legacy++) {
        events.add(new MouseEvent(source, id, 0, combine(LEGACY, legacy), 0, 0, 1, false));
      }
    }
    return events;
  }

  private static int combine(int[] masks, int bits) {
    int result = 0;
    for (int i = 0; i < masks.length; i++) {
      if ((bits & (1 << i)) != 0) {
        result |= masks[i];
      }
    }
    return result;
  }

  @SuppressWarnings("deprecation")
  public static int legacyModifiers(MouseEvent event) {
    return event.getModifiers();
  }

  public static void checkSameAsLegacy(String name, Predicate<MouseEvent> actual, Predicate<MouseEvent> legacy) {
    int count = 0;
    for (MouseEvent event : allMouseEvents()) {
      assertEquals(name + " : " + event.paramString(), legacy.test(event), actual.test(event));
      count++;
    }
    assertTrue(count > 1000);
  }

  @SuppressWarnings("deprecation")
  public void testIsRightClickIsSameAsLegacyButton3Mask() throws Exception {
    checkSameAsLegacy("isRightClick", GuiUtils::isRightClick,
                      e -> (legacyModifiers(e) & MouseEvent.BUTTON3_MASK) != 0);
  }

  public void testMetaClickCountsAsRightClick() throws Exception {
    Component source = new JPanel();
    assertTrue(GuiUtils.isRightClick(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false,
                                                    MouseEvent.BUTTON3)));
    assertTrue(GuiUtils.isRightClick(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0,
                                                    InputEvent.META_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK,
                                                    0, 0, 1, false, MouseEvent.BUTTON1)));
    assertFalse(GuiUtils.isRightClick(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0,
                                                     InputEvent.BUTTON1_DOWN_MASK, 0, 0, 1, false,
                                                     MouseEvent.BUTTON1)));
  }
}
