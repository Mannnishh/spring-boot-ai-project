package com.manish.spring_boot_ai_project;

public class NonTransientEmbeddingException extends RuntimeException {

  public NonTransientEmbeddingException(String message) {
    super(message);
  }
}
