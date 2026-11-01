package com.manish.spring_boot_ai_project;

public class TransientEmbeddingException extends RuntimeException {

  public TransientEmbeddingException(String message) {
    super(message);
  }

  public TransientEmbeddingException(String message, Throwable cause) {
    super(message, cause);
  }
}
