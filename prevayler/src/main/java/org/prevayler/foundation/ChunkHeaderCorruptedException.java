package org.prevayler.foundation;

import org.prevayler.PrevaylerIOException;

import java.io.Serial;

public class ChunkHeaderCorruptedException extends PrevaylerIOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public ChunkHeaderCorruptedException() {
		super();
	}

	public ChunkHeaderCorruptedException(String message) {
		super(message);
	}



}
