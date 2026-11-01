package com.manish.spring_boot_ai_project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class HuggingFaceEmbeddingProvider implements EmbeddingProvider {

  public static final String DEFAULT_URL =
      "https://router.huggingface.co/hf-inference/models/"
          + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

  private static final Logger log = LoggerFactory.getLogger(HuggingFaceEmbeddingProvider.class);
  private static final int DIMENSIONS = 384;

  private final String apiKey;
  private final String url;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public HuggingFaceEmbeddingProvider(String apiKey, String url) {
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
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      objectMapper.writeValueAsString(Map.of("inputs", text))))
              .build();

      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

      int status = response.statusCode();
      if (status == 429 || status >= 500) {
        log.warn(
            "Hugging face failed with retryable status: HTTP {} - {}", status, response.body());
        throw new TransientEmbeddingException(
            "Hugging Face embedding failed: HTTP " + status + " " + response.body());
      }

      if (status != 200) {
        throw new NonTransientEmbeddingException(
            "Hugging Face embedding failed: HTTP " + status + " " + response.body());
      }

      JsonNode root = objectMapper.readTree(response.body());
      JsonNode vector = root.get(0).isArray() ? root.get(0) : root;
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
      throw e;
    } catch (Exception e) {
      log.warn("Hugging Face network failure, treating as retryable: {}", e.toString());
      throw new TransientEmbeddingException(
          "Hugging Face embedding failed due to network error", e);
    }
  }

  @Override
  public int dimensions() {
    return DIMENSIONS;
  }

  @Override
  public String modelId() {
    return "huggingface:sentence-transformers/all-MiniLM-L6-v2";
  }
}
