package com.manish.spring_boot_ai_project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JinaEmbeddingProvider implements EmbeddingProvider {

  public static final String DEFAULT_URL = "https://api.jina.ai/v1/embeddings";

  public static final Logger log = LoggerFactory.getLogger(JinaEmbeddingProvider.class);
  private static final String MODEL = "jina-embeddings-v3";
  private static final int DIMENSIONS = 384;

  private final String apiKey;
  private final String url;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public JinaEmbeddingProvider(String apiKey, String url) {
    this.apiKey = apiKey;
    this.url = url;
  }

  @Override
  @Retryable(
      retryFor = {TransientEmbeddingException.class},
      maxAttempts = 4,
      backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 4000))
  public float[] embed(String text) {
    try {
      Map<String, Object> payload =
          Map.of(
              "model",
              MODEL,
              "task",
              "text-matching",
              "dimensions",
              DIMENSIONS,
              "input",
              List.of(text));

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
              .build();

      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

      int status = response.statusCode();
      if (status == 429 || status >= 500) {
        log.warn("Jina AI failed with retryable status HTTP {} - {}", status, response.body());
        throw new TransientEmbeddingException(
            "Jina AI embedding failed HTTP " + status + " " + response.body());
      }

      if (status != 200) {
        throw new NonTransientEmbeddingException(
            "Jina AI embedding failed: HTTP " + status + " " + response.body());
      }
      JsonNode vector =
          objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
      if (!vector.isArray()) {
        throw new NonTransientEmbeddingException(
            "Unexpected response shape from Jina AI: " + response.body());
      }

      float[] embedding = new float[vector.size()];
      for (int i = 0; i < vector.size(); i++) {
        embedding[i] = (float) vector.get(i).asDouble();
      }

      if (embedding.length != DIMENSIONS) {
        throw new NonTransientEmbeddingException(
            modelId() + " returned " + embedding.length + " dimensions, expected " + DIMENSIONS);
      }
      return embedding;
    } catch (TransientEmbeddingException | NonTransientEmbeddingException e) {
      // Re-throw our custom domain exceptions as-is (do not re-wrap or retry non-transient errors)
      throw e;
    } catch (Exception e) {
      log.warn("Jina AI network failure, treating as retryable: {}", e.toString());
      throw new TransientEmbeddingException("Jina AO embedding failed due to network error", e);
    }
  }

  @Override
  public int dimensions() {
    return DIMENSIONS;
  }

  @Override
  public String modelId() {
    return "jina:" + MODEL;
  }
}
