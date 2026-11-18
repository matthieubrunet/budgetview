package com.budgetview.persistence.prevayler;

import com.budgetview.session.states.Persistence;
import org.globsframework.model.Glob;
import org.globsframework.model.GlobList;

public interface RootDataManager {

  Glob getHiddenUser(String linkInfo);

  Glob getUser(String name);

  Persistence.UserInfo createUserAndHiddenUser(String name, boolean autoLog, boolean isRegisteredUser, byte[] cryptedPassword,
                                               byte[] linkInfo, byte[] cryptedLinkInfo, Integer userId);

  void deleteUser(String name, byte[] cryptedLinkInfo);

  void close();

  GlobList getLocalUsers();

  Integer allocateNewUserId(String name);

  void replaceUserAndHiddenUser(boolean autoLog, boolean isRegisteredUser,
                                String newName, byte[] newCryptedPassword, byte[] newLinkInfo, byte[] newCryptedLinkInfo,
                                String name, byte[] linkInfo, byte[] cryptedLinkInfo,
                                Integer userId);

  void setLang(String lang);

  static class RepoInfo {
    private String lang;
    private long version;

    public RepoInfo(String lang, long version) {
      this.lang = lang;
      this.version = version;
    }

    public String getLang() {
      return lang;
    }

    public long getVersion() {
      return version;
    }
  }

  RepoInfo getAndUpdateAccountInfo(long version);
}
