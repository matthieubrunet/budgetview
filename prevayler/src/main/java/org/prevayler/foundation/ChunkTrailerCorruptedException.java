package org.prevayler.foundation;

import org.prevayler.PrevaylerIOException;

import java.io.Serial;

public class ChunkTrailerCorruptedException extends PrevaylerIOException {

  @Serial
  private static final long serialVersionUID = 1L;

	public ChunkTrailerCorruptedException() {
		super();
	}

	public ChunkTrailerCorruptedException(String message) {
		super(message);
	}



}
