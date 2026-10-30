package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Document", description = "Upload, list, and delete source documents")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/documents")
public class DocumentController {

  private final DocumentRepository repository;
  private final Tika tika;
  private final ChunkingPipeline chunkingPipeline;

  public DocumentController(
      DocumentRepository repository, Tika tika, ChunkingPipeline chunkingPipeline) {
    this.repository = repository;
    this.tika = tika;
    this.chunkingPipeline = chunkingPipeline;
  }

  @Operation(
      summary = "Upload a document",
      description =
          "Extracts text with Apache Tika, stores the document, then chunks, embeds, and indexes it in pgvector.",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "Document stored and indexed",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Document.class),
                    examples =
                        @ExampleObject(
                            name = "remote-work-policy.txt",
                            value =
                                """
                                                    {
                                                      "id": 1,
                                                      "filename": "remote-work-policy.txt",
                                                      "content": "Remote Work Policy ...",
                                                      "uploadedAt": "2026-09-01T10:15:30Z"
                                                    }
                                                    """))),
        @ApiResponse(
            responseCode = "400",
            description = "Empty file upload rejected",
            content = @Content),
        @ApiResponse(
            responseCode = "500",
            description = "Text extraction or embedding failed",
            content = @Content)
      })
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Document> upload(@RequestParam("file") MultipartFile file)
      throws Exception {
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    // Extract plain text using Apache Tika
    String extractedText = tika.parseToString(file.getInputStream());

    // Create and save the new Document
    Document document =
        new Document(file.getOriginalFilename(), extractedText.trim(), Instant.now());
    Document saved = repository.save(document);

    // 2. Automatically chunk, embed, and store in pgvector!
    chunkingPipeline.ingest(saved.getId(), saved.getContent());

    return ResponseEntity.created(URI.create("/documents/" + saved.getId())).body(saved);
  }

  @GetMapping
  public List<Document> findAll() {
    return repository.findAll();
  }

  @GetMapping("/{id}")
  public ResponseEntity<Document> findById(@PathVariable Long id) {
    return repository
        .findById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping
  public ResponseEntity<Document> create(@RequestBody CreateDocumentRequest request) {
    Document document = new Document(request.filename(), request.content(), Instant.now());
    Document saved = repository.save(document);
    // Automatically chunk and vectorize raw text creations as well
    chunkingPipeline.ingest(saved.getId(), saved.getContent());
    return ResponseEntity.created(URI.create("/documents/" + saved.getId())).body(saved);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (repository.existsById(id)) {
      chunkingPipeline.remove(id);
      repository.deleteById(id);
      return ResponseEntity.noContent().build();
    }

    return ResponseEntity.notFound().build();
  }
}
