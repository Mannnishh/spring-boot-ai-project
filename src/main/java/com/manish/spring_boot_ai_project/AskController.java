package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ask", description = "Query uploaded documents via RAG")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class AskController {

  private static final Logger log = LoggerFactory.getLogger(AskController.class);

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
  private final ChunkingPipeline chunkingPipeline;

  public AskController(
      ChatClient.Builder chatClientBuilder,
      ChunkRepository chunkRepository,
      DocumentRepository documentRepository,
      ChunkingPipeline chunkingPipeline) {
    this.chatClient = chatClientBuilder.build();
    this.documentRepository = documentRepository;
    this.chunkRepository = chunkRepository;
    this.chunkingPipeline = chunkingPipeline;
  }

  @Operation(
      summary = "Ask a question",
      description =
          "Embeds the question, retrieves matching chunks from pgvector, and generates a grounded answer via Groq LLM.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Grounded answer with cited documents",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AskResponse.class),
                    examples = {
                      @ExampleObject(
                          name = "Normal Answer",
                          value =
                              """
                                                            {
                                                              "answer": "The equipment stipend for remote work is a one-time annual amount of $500.",
                                                              "citedDocuments": ["remote-work-policy.txt"]
                                                            }
                                                            """),
                      @ExampleObject(
                          name = "Not in the documents",
                          value =
                              """
                                                            {
                                                              "answer": "I don't have enough information to answer that.",
                                                              "citedDocuments": ["remote-work-policy.txt"]
                                                            }
                                                            """)
                    }))
      })
  @Retryable(
      retryFor = {Exception.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2.0))
  @PostMapping("/ask")
  public AskResponse ask(@RequestBody AskRequest request) throws Exception {
    // Step 1: Embed the user's question (384 dimensions)
    float[] queryEmbedding = chunkingPipeline.embed(request.question());

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

  @Recover
  public AskResponse recoverAsk(Exception e, AskRequest request) {
    log.error(
        "All retry attempts failed for question '{}': {}", request.question(), e.getMessage());
    return new AskResponse(
        "The AI service is currently busy or unavailable. Please try again in a moment.",
        List.of());
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
}
