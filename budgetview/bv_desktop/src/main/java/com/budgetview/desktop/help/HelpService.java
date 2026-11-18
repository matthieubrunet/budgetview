package com.budgetview.desktop.help;

import com.budgetview.desktop.browsing.BrowsingService;
import com.budgetview.utils.Lang;
import org.globsframework.model.GlobRepository;
import org.globsframework.utils.directory.Directory;

public class HelpService {

  private final BrowsingService browsingService;

  public HelpService(GlobRepository repository, Directory directory) {
    this.browsingService = directory.get(BrowsingService.class);
  }

  public void show(String helpRef) {
    browsingService.launchBrowser(Lang.get("help.url." + helpRef));
  }
}
