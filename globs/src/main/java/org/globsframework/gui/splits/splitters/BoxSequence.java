package org.globsframework.gui.splits.splitters;

import org.globsframework.gui.splits.SplitProperties;
import org.globsframework.gui.splits.Splitter;
import org.globsframework.gui.splits.layout.ComponentConstraints;
import org.globsframework.utils.exceptions.InvalidParameter;

import javax.swing.*;

public class BoxSequence extends Sequence {

  public BoxSequence(Splitter[] subSplitters, Direction direction, SplitProperties properties) {
    super(properties, subSplitters, direction);
  }

  protected SequenceBuilder getSequenceBuilder() {
    return new BoxSequenceBuilder();
  }

  public String getName() {
    return switch (direction) {
      case HORIZONTAL -> "horizontalBoxes";
      case VERTICAL -> "verticalBoxes";
    };
  }

  private static class BoxSequenceBuilder implements SequenceBuilder {
    private JPanel panel;

    public void init(JPanel panel, Direction direction) {
      this.panel = panel;
      panel.setOpaque(false);
      panel.setLayout(new BoxLayout(panel, getBoxDirection(direction)));
    }

    public void add(ComponentConstraints constraints, Direction direction, int position) {
      panel.add(constraints.getComponent(), position);
    }

    public int getBoxDirection(Direction direction) {
      return switch (direction) {
        case HORIZONTAL -> BoxLayout.X_AXIS;
        case VERTICAL -> BoxLayout.Y_AXIS;
      };
    }
  }
}