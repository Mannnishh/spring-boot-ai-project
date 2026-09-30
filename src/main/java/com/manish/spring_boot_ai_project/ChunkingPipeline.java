package com.manish.spring_boot_ai_project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChunkingPipeline {

  private final ChunkingStrategy chunkingStrategy;
  private final ChunkRepository chunkRepository;
  private final String hfApiKey;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ChunkingPipeline(
      ChunkRepository chunkRepository,
      ChunkingStrategy chunkingStrategy,
      @Value("${HF_API_KEY}") String hfApiKey) {
    this.chunkingStrategy = chunkingStrategy;
    this.chunkRepository = chunkRepository;
    this.hfApiKey = hfApiKey;
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

  // Hugging Face 384-dimensional embedding call
  public float[] embed(String text) throws Exception {
    String url =
        "https://router.huggingface.co/hf-inference/models/sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + hfApiKey)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"inputs\":" + objectMapper.writeValueAsString(text) + "}"))
            .build();

    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Hugging Face embedding failed: HTTP " + response.statusCode() + " " + response.body());
    }

    // Parse JSON into float[] (with fallback for nested arrays)
    try {
      return objectMapper.readValue(response.body(), float[].class);
    } catch (Exception e) {
      float[][] nested = objectMapper.readValue(response.body(), float[][].class);
      return nested[0];
    }
  }
}
