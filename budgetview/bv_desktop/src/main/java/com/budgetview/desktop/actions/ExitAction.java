package com.budgetview.desktop.actions;

import com.budgetview.desktop.WindowManager;
import com.budgetview.desktop.utils.AppExit;
import com.budgetview.utils.Lang;
import org.globsframework.model.GlobRepository;
import org.globsframework.utils.directory.Directory;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ExitAction extends AbstractAction {
  private JFrame frame;
  private WindowManager windowManager;
  private boolean enabled = true;
  private boolean exitRequested = false;

  public ExitAction(WindowManager windowManager, GlobRepository repository, Directory directory) {
    super(Lang.get("exit"));
    this.windowManager = windowManager;
    this.frame = directory.get(JFrame.class);
  }

  public void actionPerformed(ActionEvent e) {
    if (exitRequested) {
      return;
    }
    exitRequested = true;

    frame.setVisible(false);
    frame.dispose();
    windowManager.shutdown();
    if (System.getProperty("realExit", "true").equalsIgnoreCase("true")){
      AppExit.exit(0);
    }
  }
}
