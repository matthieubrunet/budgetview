package com.budgetview.persistence.prevayler.users;

import com.budgetview.persistence.prevayler.CustomSerializable;
import com.budgetview.persistence.prevayler.CustomSerializableFactory;
import org.globsframework.utils.directory.Directory;
import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedOutput;
import org.prevayler.Transaction;

import java.security.SecureRandom;
import java.util.Date;

public class InitialRepoIdTransaction implements Transaction, CustomSerializable {
  private static final int V1 = 1;
  private static String TRANSACTION_NAME = "InitialRepoId";
  private byte[] repoId;

  public InitialRepoIdTransaction() {
  }

  private InitialRepoIdTransaction(byte[] repoId) {
    this.repoId = repoId;
  }

  public void executeOn(Object prevalentSystem, Date executionTime) {
    ((PRootData)prevalentSystem).setRepoId(repoId);
  }

  public String getSerializationName() {
    return TRANSACTION_NAME;
  }

  public void read(SerializedInput input, Directory directory) {
    int version = input.readNotNullInt();
    repoId = input.readBytes();
  }

  public void write(SerializedOutput output, Directory directory) {
    output.write(V1);
    output.writeBytes(repoId);
  }

  public static CustomSerializableFactory getFactory() {
    return new Factory();
  }

  public static Transaction create() {
    byte[] repoId = new byte[50];
    new SecureRandom().nextBytes(repoId);
    return new InitialRepoIdTransaction(repoId);
  }

  private static class Factory implements CustomSerializableFactory {

    public String getSerializationName() {
      return TRANSACTION_NAME;
    }

    public CustomSerializable create() {
      return new InitialRepoIdTransaction();
    }
  }
}
