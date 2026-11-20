package com.budgetview.desktop.components.tips;

import net.java.balloontip.positioners.*;

public enum TipPosition {
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  CENTER;

  BalloonTipPositioner getPositioner() {
    return switch (this) {
      case TOP_LEFT -> new LeftAbovePositioner(10, 20);
      case TOP_RIGHT -> new RightAbovePositioner(10, 20);
      case BOTTOM_LEFT -> new LeftBelowPositioner(10, 20);
      case BOTTOM_RIGHT -> new RightBelowPositioner(10, 20);
      case CENTER -> new CenteredPositioner(10);
    };
  }
}
