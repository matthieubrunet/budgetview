package org.prevayler.implementation.snapshot;

import org.prevayler.PrevaylerIOException;

import java.io.Serial;

public class SnapshotException extends PrevaylerIOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public SnapshotException() {
		super();
	}

	public SnapshotException(String message) {
		super(message);
	}



}
