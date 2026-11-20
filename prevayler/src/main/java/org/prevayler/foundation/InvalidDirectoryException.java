package org.prevayler.foundation;

import org.prevayler.PrevaylerIOException;

import java.io.Serial;

public class InvalidDirectoryException extends PrevaylerIOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public InvalidDirectoryException() {
		super();
	}

	public InvalidDirectoryException(String message) {
		super(message);
	}



}
