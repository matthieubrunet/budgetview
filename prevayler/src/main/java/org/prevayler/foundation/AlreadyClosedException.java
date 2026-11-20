package org.prevayler.foundation;

import org.prevayler.PrevaylerIOException;

import java.io.Serial;

public class AlreadyClosedException extends PrevaylerIOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public AlreadyClosedException() {
		super();
	}

	public AlreadyClosedException(String message) {
		super(message);
	}



}
