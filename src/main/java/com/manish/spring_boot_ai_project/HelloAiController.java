package com.manish.spring_boot_ai_project;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class HelloAiController {

  private final ChatClient chatClient;
  private final String hfApiKey;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public HelloAiController(
      ChatClient.Builder chatClientBuilder, @Value("${HF_API_KEY:}") String hfApiKey) {
    this.chatClient = chatClientBuilder.build();
    this.hfApiKey = hfApiKey;
  }

  // 1. LLM Chat Completion (Groq)
  @GetMapping("/hello-groq")
  public String helloGroq() {
    return chatClient.prompt("Say hello in one creative sentence.").call().content();
  }

  // 2. Vector Embedding (Hugging Face) -> Returns 384-dimensional vector
  @GetMapping("/hello-embedding")
  public float[] helloEmbedding() throws Exception {
    String url =
        "https://router.huggingface.co/hf-inference/models/"
            + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + hfApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"inputs\":\"hello world\"}"))
            .build();

    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

    // Parse array-of-arrays returned by Hugging Face: [[0.012, -0.045, ...]]
    try {
      return objectMapper.readValue(response.body(), float[].class);
    } catch (Exception e) {
      float[][] nested = objectMapper.readValue(response.body(), float[][].class);
      return nested[0];
    }
  }
}
