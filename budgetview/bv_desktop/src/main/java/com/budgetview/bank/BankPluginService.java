package com.budgetview.bank;

import com.budgetview.model.Account;
import com.budgetview.model.Bank;
import com.budgetview.model.RealAccount;
import com.budgetview.shared.utils.Amounts;
import org.globsframework.model.Glob;
import org.globsframework.model.GlobRepository;
import org.globsframework.model.Key;
import org.globsframework.utils.Strings;

import java.util.Date;

import static org.globsframework.model.FieldValue.value;

/**
 * Report du solde d'un fichier importé sur le compte (LAST_IMPORT_POSITION). Seul vestige des règles propres à
 * chaque banque, chargées autrefois comme plugins.
 */
public class BankPluginService {

  public void apply(Glob account, Glob importedAccount, GlobRepository localRepository) {
    Glob bank = localRepository.find(Key.create(Bank.TYPE, account.get(Account.BANK)));
    if (bank.get(Bank.INVALID_POSITION)) {
      return;
    }
    String amount = importedAccount.get(RealAccount.POSITION);
    if (Strings.isNotEmpty(amount)) {
      Date currentDate = account.get(Account.POSITION_DATE);
      Date importDate = importedAccount.get(RealAccount.POSITION_DATE);
      // ce doit etre la derniere date.
      if (importDate != null && (currentDate == null || importDate.after(currentDate))) {
        localRepository.update(account.getKey(), value(Account.LAST_IMPORT_POSITION, Amounts.extractAmount(amount)));
      }
    }
  }
}
