package com.manish.spring_boot_ai_project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
public class AskController {

  private static final int TOP_K = 5;

  private static final String SYSTEM_INSTRUCTION =
      """
            You are an internal Q&A assistant. Answer the question using ONLY
                           the context below. Each context entry is labeled with its source
                           document's filename in square brackets.

                           Rules:
                           - Do not use any knowledge beyond what appears in the context.
                           - If the context does not contain the answer, respond exactly:
                             "I don't have enough information to answer that." Do not guess.
                           - After your answer, name the filename(s) of the context entries
                             you actually used.
                             """;

  private final ChatClient chatClient;
  private final ChunkRepository chunkRepository;
  private final DocumentRepository documentRepository;
  private final String hfApiKey;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AskController(
      ChatClient.Builder chatClientBuilder,
      ChunkRepository chunkRepository,
      DocumentRepository documentRepository,
      @Value("${HF_API_KEY}") String hfApiKey) {
    this.chatClient = chatClientBuilder.build();
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.hfApiKey = hfApiKey;
  }

  @PostMapping("/ask")
  public AskResponse ask(@RequestBody AskRequest request) {
    // Step 1: Embed the user's question (384 dimensions)
    float[] queryEmbedding = embed(request.question());

    // Step 2: Retrieve Top-K nearest chunks from pgvector
    List<Chunk> nearest = chunkRepository.findNearest(toVectorLiteral(queryEmbedding), TOP_K);

    // Step 3: Resolve each chunk's source document filename
    List<ContextChunk> context =
        nearest.stream()
            .map(chunk -> new ContextChunk(filenameFor(chunk), chunk.getContent()))
            .toList();

    // Step 4: Assemble prompt using Builder Pattern
    String prompt =
        new PromptBuilder()
            .withSystemInstruction(SYSTEM_INSTRUCTION)
            .withContext(context)
            .withQuestion(request.question())
            .build();

    // Step 5: Call Groq LLM via Spring AI ChatClient
    String answer = chatClient.prompt(prompt).call().content();

    // Step 6: Extract distinct cited document filenames
    List<String> citedDocuments =
        context.stream().map(ContextChunk::sourceFilename).distinct().toList();

    return new AskResponse(answer, citedDocuments);
  }

  // Helper: Safely look up filename from DocumentRepository
  private String filenameFor(Chunk chunk) {
    return documentRepository
        .findById(chunk.getDocumentId())
        .map(Document::getFilename)
        .orElse("unknown");
  }

  // Helper: Formats float[] -> "[v1,v2,...]".
  // %.8f avoids scientific notation (e.g. "1.0E-4") which pgvector rejects.
  private String toVectorLiteral(float[] embedding) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(String.format(Locale.ROOT, "%.8f", embedding[i]));
    }

    return sb.append("]").toString();
  }

  // Helper: Calls Hugging Face sentence-transformers/all-MiniLM-L6-v2
  private float[] embed(String text) {
    try {
      String url =
          "https://router.huggingface.co/hf-inference/models/"
              + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Authorization", "Bearer " + hfApiKey)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      objectMapper.writeValueAsString(Map.of("inputs", text))))
              .build();

      HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Embedding call failed: HTTP " + response.statusCode() + " " + response.body());
      }
      // Parse response array (handles both [0.1, 0.2] and [[0.1, 0.2]])
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode vector =
          root.isArray() && root.size() > 0 && root.get(0).isArray() ? root.get(0) : root;

      float[] embedding = new float[vector.size()];
      for (int i = 0; i < embedding.length; i++) {
        embedding[i] = (float) vector.get(i).asDouble();
      }
      return embedding;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Embedding call failed", e);
    }
  }
}
