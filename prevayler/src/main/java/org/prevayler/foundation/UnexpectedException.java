package org.prevayler.foundation;

import org.prevayler.PrevaylerException;

import java.io.Serial;

public class UnexpectedException extends PrevaylerException {

  @Serial
  private static final long serialVersionUID = 1L;

	public UnexpectedException() {
		super();
	}


	public UnexpectedException(String message, Throwable cause) {
		super(message, cause);
	}

	public UnexpectedException(Throwable cause) {
		super(cause);
	}

}
