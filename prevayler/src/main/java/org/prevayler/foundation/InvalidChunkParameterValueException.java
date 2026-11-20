package org.prevayler.foundation;

import org.prevayler.PrevaylerIOException;

import java.io.Serial;

public class InvalidChunkParameterValueException extends PrevaylerIOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public InvalidChunkParameterValueException() {
		super();
	}

	public InvalidChunkParameterValueException(String message) {
		super(message);
	}



}
