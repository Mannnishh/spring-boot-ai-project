package com.manish.spring_boot_ai_project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChunkingPipeline {

  private static final Logger log = LoggerFactory.getLogger(ChunkingPipeline.class);

  private final ChunkingStrategy chunkingStrategy;
  private final ChunkRepository chunkRepository;
  private final String jinaApiKey;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ChunkingPipeline(
      ChunkRepository chunkRepository,
      ChunkingStrategy chunkingStrategy,
      @Value("${JINA_API_KEY}") String jinaApiKey) {
    this.chunkingStrategy = chunkingStrategy;
    this.chunkRepository = chunkRepository;
    this.jinaApiKey = jinaApiKey;
  }

  public void ingest(Long documentId, String text) {
    if (text == null || text.isBlank()) {
      return;
    }

    // 1. Slice text into chunks using the injected Strategy
    List<String> chunks = chunkingStrategy.chunk(text);

    // 2. Embed each chunk and save to pgvecto
    for (String chunkText : chunks) {
      try {
        float[] embedding = embed(chunkText);
        chunkRepository.save(new Chunk(documentId, chunkText, embedding));
      } catch (Exception e) {
        throw new RuntimeException("Failed to embed and save chunk for document " + documentId, e);
      }
    }
  }

  public void remove(Long documentId) {
    chunkRepository.deleteByDocumentId(documentId);
  }

  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2.0))

  // Hugging Face 384-dimensional embedding call
  public float[] embed(String text) throws Exception {
    String url = "https://api.jina.ai/v1/embeddings";

    // Build the Jina AI request payload requesting 384 dimensions
    java.util.Map<String, Object> payload =
        java.util.Map.of(
            "model",
            "jina-embeddings-v3",
            "task",
            "text-matching",
            "dimensions",
            384,
            "input",
            java.util.List.of(text));
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + jinaApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Jina embedding failed: HTTP " + response.statusCode() + " " + response.body());
    }

    // Parse: {"data": [{"embedding": [...]}]}
    tools.jackson.databind.JsonNode root = objectMapper.readTree(response.body());
    tools.jackson.databind.JsonNode vectorNode = root.path("data").get(0).path("embedding");

    float[] embedding = new float[vectorNode.size()];
    for (int i = 0; i < vectorNode.size(); i++) {
      embedding[i] = (float) vectorNode.get(i).asDouble();
    }
    return embedding;
  }

  // Fallback called if all 3 attempts fail
  @Recover
  public float[] recoverEmbed(Exception e, String text) {
    log.error("Embedding API permanently failed after 3 attempts: {}", e.getMessage());
    throw new RuntimeException(
        "Embedding service is currently unavailable. Please try again later.", e);
  }
}
