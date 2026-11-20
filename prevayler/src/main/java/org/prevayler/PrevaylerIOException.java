package org.prevayler;

import java.io.IOException;
import java.io.Serial;

public class PrevaylerIOException extends IOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public PrevaylerIOException() {
		super();
	}

	public PrevaylerIOException(String message) {
		super(message);
	}



}
