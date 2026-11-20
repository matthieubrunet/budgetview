package org.prevayler;

import java.io.Serial;

public class PrevaylerError extends Error {

  @Serial
  private static final long serialVersionUID = 1L;

	public PrevaylerError() {
		super();
	}

	public PrevaylerError(String message) {
		super(message);
	}

	public PrevaylerError(String message, Throwable cause) {
		super(message, cause);
	}


}
