package org.prevayler;

import java.io.Serial;

public class PrevaylerException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

	public PrevaylerException() {
		super();
	}

	public PrevaylerException(String message) {
		super(message);
	}

	public PrevaylerException(String message, Throwable cause) {
		super(message, cause);
	}

	public PrevaylerException(Throwable cause) {
		super(cause);
	}

}
