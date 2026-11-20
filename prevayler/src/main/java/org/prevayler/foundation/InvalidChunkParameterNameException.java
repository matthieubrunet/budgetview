package org.prevayler.foundation;

import org.prevayler.PrevaylerIOException;

import java.io.Serial;

public class InvalidChunkParameterNameException extends PrevaylerIOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public InvalidChunkParameterNameException() {
		super();
	}

	public InvalidChunkParameterNameException(String message) {
		super(message);
	}



}
