package com.manish.spring_boot_ai_project;

import java.util.ArrayList;
import java.util.List;

public class FixedSizeChunkingStrategy implements ChunkingStrategy {

  private final int chunkSize;
  private final int overlap;

  public FixedSizeChunkingStrategy(int chunkSize, int overlap) {
    if (chunkSize <= 0) {
      throw new IllegalArgumentException("chunkSize must be positive");
    }
    if (overlap < 0 || overlap >= chunkSize) {
      throw new IllegalArgumentException("overlap must be in [0, chunkSize)");
    }
    this.chunkSize = chunkSize;
    this.overlap = overlap;
  }

  @Override
  public List<String> chunk(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(start + chunkSize, text.length());
      chunks.add(text.substring(start, end));
      if (end == text.length()) {
        break;
      }
      start = end - overlap;
    }
    return chunks;
  }
}
