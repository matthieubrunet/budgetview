package org.saxstack.parser;

public class ExceptionHolder extends RuntimeException {
  public ExceptionHolder(Exception cause) {
    super(cause);
  }
}
