package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class HelloAiController {

  private final ChatClient chatClient;
  private final EmbeddingProvider embeddingProvider;

  public HelloAiController(
      ChatClient.Builder chatClientBuilder, EmbeddingProvider embeddingProvider) {
    this.chatClient = chatClientBuilder.build();
    this.embeddingProvider = embeddingProvider;
  }

  // 1. LLM Chat Completion (Groq)
  @GetMapping("/hello-groq")
  public String helloGroq() {
    return chatClient.prompt("Say hello in one creative sentence.").call().content();
  }

  // 2. Vector Embedding (Hugging Face) -> Returns 384-dimensional vector
  @GetMapping("/hello-embedding")
  public float[] helloEmbedding() {
    return embeddingProvider.embed("hello world");
  }
}
