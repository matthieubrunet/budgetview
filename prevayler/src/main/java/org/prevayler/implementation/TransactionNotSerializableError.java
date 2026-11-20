package org.prevayler.implementation;

import org.prevayler.PrevaylerError;

public class TransactionNotSerializableError extends PrevaylerError {

	private static final long serialVersionUID = 1L;

	public TransactionNotSerializableError() {
		super();
	}


	public TransactionNotSerializableError(String message, Throwable cause) {
		super(message, cause);
	}


}
