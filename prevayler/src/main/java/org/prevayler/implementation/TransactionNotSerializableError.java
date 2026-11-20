package org.prevayler.implementation;

import org.prevayler.PrevaylerError;

import java.io.Serial;

public class TransactionNotSerializableError extends PrevaylerError {

  @Serial
  private static final long serialVersionUID = 1L;

	public TransactionNotSerializableError() {
		super();
	}


	public TransactionNotSerializableError(String message, Throwable cause) {
		super(message, cause);
	}


}
