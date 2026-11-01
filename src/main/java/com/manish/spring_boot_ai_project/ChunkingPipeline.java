package com.manish.spring_boot_ai_project;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChunkingPipeline {

  private static final Logger log = LoggerFactory.getLogger(ChunkingPipeline.class);

  private final ChunkingStrategy chunkingStrategy;
  private final ChunkRepository chunkRepository;
  private final EmbeddingProvider embeddingProvider;

  public ChunkingPipeline(
      ChunkRepository chunkRepository,
      ChunkingStrategy chunkingStrategy,
      EmbeddingProvider embeddingProvider) {
    this.chunkingStrategy = chunkingStrategy;
    this.chunkRepository = chunkRepository;
    this.embeddingProvider = embeddingProvider;
  }

  public void ingest(Long documentId, String text) {
    if (text == null || text.isBlank()) {
      return;
    }

    // 1. Slice text into chunks using the injected Strategy
    List<String> chunks = chunkingStrategy.chunk(text);

    // 2. Embed each chunk and save to pgvector
    for (String chunkText : chunks) {
      try {
        float[] embedding = embeddingProvider.embed(chunkText);
        chunkRepository.save(new Chunk(documentId, chunkText, embedding));
      } catch (Exception e) {
        throw new RuntimeException("Failed to embed and save chunk for document " + documentId, e);
      }
    }
  }

  public void remove(Long documentId) {
    chunkRepository.deleteByDocumentId(documentId);
  }
}
