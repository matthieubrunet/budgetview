package com.budgetview.desktop.components;

import com.budgetview.desktop.utils.Gui;
import org.globsframework.model.GlobRepository;
import org.globsframework.utils.directory.Directory;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class PicsouFrame extends JFrame {
  private GlobRepository repository;
  private boolean isIconified;

  public PicsouFrame(String title, final Directory directory) {
    super(title);
    addWindowListener(new WindowAdapter() {
      public void windowIconified(WindowEvent e) {
        isIconified = true;
      }

      public void windowDeiconified(WindowEvent e) {
        isIconified = false;
      }
    });

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setIconImage(Gui.IMAGE_LOCATOR.get("app_icon_128.png").getImage());
  }

  public boolean isIconified() {
    return isIconified;
  }

  public GlobRepository getRepository() {
    return repository;
  }

  public void setRepository(GlobRepository repository) {
    this.repository = repository;
  }
}
