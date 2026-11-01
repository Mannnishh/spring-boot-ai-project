package com.manish.spring_boot_ai_project;

public interface EmbeddingProvider {

  /** Embeds one piece of text into a vector of exactly dimensions() floats. */
  float[] embed(String text);

  /** Length of every vector this provider returns; must match pgvector column (384). */
  int dimensions();

  /** Human-readable "provider:model" string, for logs. */
  String modelId();
}
