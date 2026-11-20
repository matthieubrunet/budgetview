package org.prevayler.implementation;

import org.prevayler.PrevaylerError;

import java.io.Serial;

public class ErrorInEarlierTransactionError extends PrevaylerError {

  @Serial
  private static final long serialVersionUID = 1L;

	public ErrorInEarlierTransactionError() {
		super();
	}

	public ErrorInEarlierTransactionError(String message) {
		super(message);
	}



}
