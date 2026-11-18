package com.budgetview.desktop.accounts.position;

import com.budgetview.desktop.description.Formatting;
import com.budgetview.model.Account;
import com.budgetview.model.UserPreferences;
import com.budgetview.utils.Lang;
import org.globsframework.gui.GlobsPanelBuilder;
import org.globsframework.model.Glob;
import org.globsframework.model.GlobList;
import org.globsframework.model.GlobRepository;
import org.globsframework.model.Key;
import org.globsframework.model.format.GlobListStringifier;
import org.globsframework.model.utils.ChangeSetMatchers;

public class AccountPositionLabels {

  private AccountPositionLabels() {
  }

  public static void registerReferencePositionLabels(GlobsPanelBuilder builder,
                                                     Integer accountId,
                                                     String positionLabelName,
                                                     String titleLabelName,
                                                     String titleLabelKey) {
    Key summaryAccount = Key.create(Account.TYPE, accountId);
    builder.addLabel(positionLabelName, Account.POSITION_WITH_PENDING)
      .setAutoHideIfEmpty(true)
      .forceSelection(summaryAccount);
    builder.addLabel(titleLabelName, Account.TYPE, new ReferenceAmountStringifier(titleLabelKey))
      .setUpdateMatcher(ChangeSetMatchers.changesForKey(UserPreferences.KEY))
      .setAutoHideIfEmpty(true)
      .forceSelection(summaryAccount);
  }

  private static class ReferenceAmountStringifier implements GlobListStringifier {
    private String key;

    private ReferenceAmountStringifier(String key) {
      this.key = key;
    }

    public String toString(GlobList list, GlobRepository repository) {
      if (list.isEmpty() || list.get(0).get(Account.POSITION_DATE) == null) {
        return "";
      }
      Glob account = list.get(0);
      return Lang.get(key, getAccountPositionDate(account));
    }
  }

  public static String getAccountPositionDate(Glob account) {
    return Formatting.toString(account.get(Account.POSITION_DATE));
  }
}
