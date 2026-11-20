package org.prevayler.implementation.journal;

import org.prevayler.PrevaylerIOException;

import java.io.Serial;

public class JournalException extends PrevaylerIOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public JournalException() {
		super();
	}

	public JournalException(String message) {
		super(message);
	}



}
