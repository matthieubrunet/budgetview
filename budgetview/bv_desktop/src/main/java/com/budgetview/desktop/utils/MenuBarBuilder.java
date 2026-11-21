package com.budgetview.desktop.utils;


import com.budgetview.desktop.Application;
import com.budgetview.desktop.WindowManager;
import com.budgetview.desktop.about.AboutAction;
import com.budgetview.desktop.accounts.actions.CreateAccountAction;
import com.budgetview.desktop.actions.*;
import com.budgetview.desktop.addons.dev.ToggleAllAddOnsAction;
import com.budgetview.desktop.backup.BackupAction;
import com.budgetview.desktop.backup.RestoreFileAction;
import com.budgetview.desktop.backup.RestoreSnapshotMenuAction;
import com.budgetview.desktop.cloud.actions.CloudDemoAction;
import com.budgetview.desktop.components.PicsouFrame;
import com.budgetview.desktop.help.actions.GotoSupportAction;
import com.budgetview.desktop.help.actions.GotoWebsiteAction;
import com.budgetview.desktop.notes.ShowNotesAction;
import com.budgetview.desktop.preferences.PreferencesAction;
import com.budgetview.desktop.preferences.dev.DevOptionsAction;
import com.budgetview.desktop.printing.actions.PrintBudgetAction;
import com.budgetview.desktop.startup.components.LogoutService;
import com.budgetview.desktop.time.actions.*;
import com.budgetview.desktop.transactions.creation.ShowCreateTransactionAction;
import com.budgetview.desktop.transactions.reconciliation.annotations.ShowReconciliationAction;
import com.budgetview.desktop.undo.RedoAction;
import com.budgetview.desktop.undo.UndoAction;
import com.budgetview.desktop.utils.dev.*;
import com.budgetview.shared.model.AccountType;
import com.budgetview.utils.Lang;
import org.globsframework.gui.splits.utils.GuiUtils;
import org.globsframework.model.GlobRepository;
import org.globsframework.model.repository.ReplicationGlobRepository;
import org.globsframework.utils.directory.Directory;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class MenuBarBuilder {

  private final GlobRepository repository;
  private ReplicationGlobRepository replicationGlobRepository;
  private final Directory directory;

  private ImportFileAction importFileAction;
  private ExportFileAction exportFileAction;
  private SetPasswordAction setPasswordAction;
  private BackupAction backupAction;
  private RestoreFileAction restoreActionFileAction;
  private RestoreSnapshotMenuAction restoreSnapshotMenuAction;
  private PreferencesAction preferencesAction;
  private DeleteUserAction deleteUserAction;
  private Action threadsAction;
  private PrintBudgetAction printBudgetAction;
  private ExitAction exitAction;
  private LogoutAction logoutAction;
  private AboutAction aboutAction;

  public MenuBarBuilder(GlobRepository repository,
                        ReplicationGlobRepository replicationGlobRepository,
                        WindowManager windowManager,
                        LogoutService logoutService,
                        Directory directory, DeleteUserAction deleteUserAction) {
    this.repository = repository;
    this.replicationGlobRepository = replicationGlobRepository;
    this.directory = directory;

    this.importFileAction = ImportFileAction.initForMenu(Lang.get("import"), repository, directory);
    this.exportFileAction = new ExportFileAction(repository, directory);
    this.backupAction = new BackupAction(repository, directory);
    this.restoreActionFileAction = new RestoreFileAction(repository, directory);
    this.restoreSnapshotMenuAction = new RestoreSnapshotMenuAction(directory, repository);
    this.preferencesAction = new PreferencesAction(repository, directory);
    this.setPasswordAction = new SetPasswordAction(repository, directory);
    this.deleteUserAction = deleteUserAction;
    this.printBudgetAction = new PrintBudgetAction(replicationGlobRepository, directory);
    this.threadsAction = new DumpStackTracesAction();
    this.aboutAction = new AboutAction(directory);
    this.exitAction = new ExitAction(windowManager, repository, directory);
    this.logoutAction = new LogoutAction(logoutService);
  }

  public void createMenuBar(final PicsouFrame frame) {
    JMenuBar menuBar = new JMenuBar();

    menuBar.add(createFileMenu());
    menuBar.add(createEditMenu(frame));
    menuBar.add(createViewMenu());

    if (Application.isDevMode()) {
      menuBar.add(createDevMenu());
    }

    menuBar.add(createHelpMenu());

    frame.setJMenuBar(menuBar);
  }

  private JMenu createFileMenu() {

    final JMenu menu = new JMenu(Lang.get("menuBar.file"));
    menu.add(importFileAction);
    menu.add(exportFileAction);
    menu.addSeparator();
    menu.add(backupAction);
    menu.add(restoreActionFileAction);
    menu.add(restoreSnapshotMenuAction);

    if (!Gui.useMacOSMenu()) {
      menu.addSeparator();
      menu.add(preferencesAction);
    }

    menu.addSeparator();
//    menu.add(activateLicenseAction);

    menu.add(setPasswordAction);
    menu.add(logoutAction);
    menu.add(deleteUserAction);

    menu.addSeparator();
    menu.add(printBudgetAction);

    if (!Gui.useMacOSMenu()) {
      menu.addSeparator();
      menu.add(exitAction);
    }

    return menu;
  }

  private JMenu createEditMenu(PicsouFrame frame) {
    final UndoAction undoAction = new UndoAction(directory);
    final RedoAction redoAction = new RedoAction(directory);

    JMenu editMenu = new JMenu(Lang.get("menuBar.edit"));
    editMenu.add(undoAction);
    editMenu.add(redoAction);

    JRootPane rootPane = frame.getRootPane();
    GuiUtils.addShortcut(rootPane, "UNDO", undoAction, GuiUtils.ctrl(KeyEvent.VK_Z));
    GuiUtils.addShortcut(rootPane, "REDO", redoAction, GuiUtils.ctrl(KeyEvent.VK_Y));
    GuiUtils.addShortcut(rootPane, "Data check", new DataCheckerAction(repository, directory),
                         GuiUtils.ctrl(KeyEvent.VK_D));

    editMenu.addSeparator();
    CreateAccountAction createAccount =
      new CreateAccountAction("account.create.menu", AccountType.MAIN, repository, directory, frame);
    createAccount.setGotoAccountViewEnabled(true);
    editMenu.add(createAccount);
    editMenu.add(new ShowCreateTransactionAction(directory));

    return editMenu;
  }

  private JMenu createDevMenu() {
    JMenu devMenu = new JMenu("[Dev]");
    devMenu.add(new DevOptionsAction(repository, directory));
    devMenu.addSeparator();
    devMenu.add(new DumpRepositoryAction(replicationGlobRepository));
    devMenu.add(new DumpDataAction(repository));
    devMenu.add(new DataCheckerAction(repository, directory));
    devMenu.add(new DumpUndoStackAction(repository, directory));
    devMenu.add(new CheckMemoryAction());
    devMenu.addSeparator();
    devMenu.add(new ThrowExceptionAction());
    devMenu.add(new ThrowInRepoExceptionAction(repository));
    devMenu.add(new AddAccountErrorAction(repository));
    devMenu.addSeparator();
    devMenu.add(new Goto10OfNextMonthAction(repository));
    devMenu.add(new AddSixDaysAction(repository));
    devMenu.add(new ChangeDateAction(repository));
    devMenu.addSeparator();
    devMenu.add(new HideSignpostsAction(repository));
    devMenu.addSeparator();
    devMenu.addSeparator();
    devMenu.add(ToggleAllAddOnsAction.enableAll(repository));
    devMenu.add(ToggleAllAddOnsAction.disableAll(repository));
    devMenu.addSeparator();
    // Synchro bancaire (Budgea) désactivée en usage local (docs/perimetre-local.md)
    if (Application.isServerEnabled()) {
      devMenu.add(new ShowSynchroErrorAction(directory));
      devMenu.add(new CloudDemoAction(repository, directory));
      devMenu.addSeparator();
    }
    devMenu.add(threadsAction);
    return devMenu;
  }

  private JMenu createViewMenu() {
    JMenu showMenu = new JMenu(Lang.get("menuBar.view"));
    showMenu.add(new SelectCurrentMonthAction(repository, directory));
    showMenu.add(new SelectMonthAction(repository, directory));
    showMenu.add(new SelectCurrentYearAction(repository, directory));
    showMenu.add(new SelectLast12MonthsAction(repository, directory));
    showMenu.add(new SelectSinceLastJanuaryAction(repository, directory));
    showMenu.addSeparator();
    showMenu.add(new ShowNotesAction(repository, directory));
    showMenu.addSeparator();
    showMenu.add(new ShowReconciliationAction(repository, directory));
    return showMenu;
  }

  private JMenu createHelpMenu() {
    JMenu menu = new JMenu(Lang.get("menuBar.help"));
    menu.add(new GotoSupportAction(directory));

    menu.addSeparator();
    menu.add(new GotoWebsiteAction(directory));

    if (!Gui.useMacOSMenu()) {
      menu.addSeparator();
      menu.add(aboutAction);
    }

    return menu;
  }

  public ExitAction getExitAction() {
    return exitAction;
  }

  public ImportFileAction getImportFileAction() {
    return importFileAction;
  }

  public AboutAction getAboutAction() {
    return aboutAction;
  }

  public PreferencesAction getPreferencesAction() {
    return preferencesAction;
  }
}
