package org.prevayler.implementation;

import org.prevayler.PrevaylerError;

public class ErrorInEarlierTransactionError extends PrevaylerError {

	private static final long serialVersionUID = 1L;

	public ErrorInEarlierTransactionError() {
		super();
	}

	public ErrorInEarlierTransactionError(String message) {
		super(message);
	}



}
