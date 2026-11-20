package org.prevayler.implementation;

import java.io.Serial;

public class Bomb extends Error {

  @Serial
  private static final long serialVersionUID = 1L;

	public Bomb() {
		super();
	}

	public Bomb(String message) {
		super(message);
	}

	public Bomb(String message, Throwable cause) {
		super(message, cause);
	}

	public Bomb(Throwable cause) {
		super(cause);
	}

}
