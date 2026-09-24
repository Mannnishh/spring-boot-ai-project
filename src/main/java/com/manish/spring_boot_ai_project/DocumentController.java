package com.manish.spring_boot_ai_project;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
public class DocumentController {

  private final DocumentRepository repository;
  private final Tika tika;

  public DocumentController(DocumentRepository repository, Tika tika) {
    this.repository = repository;
    this.tika = tika;
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Document> upload(@RequestParam("file") MultipartFile file)
      throws Exception {
    // Extract plain text using Apache Tika
    String extractedText = tika.parseToString(file.getInputStream());

    // Create and save the new Document
    Document document =
        new Document(file.getOriginalFilename(), extractedText.trim(), Instant.now());
    Document saved = repository.save(document);

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
  public ResponseEntity<Document> create(@RequestBody Document document) {
    Document saved = repository.save(document);
    return ResponseEntity.created(URI.create("/documents/" + saved.getId())).body(saved);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (repository.existsById(id)) {
      repository.deleteById(id);
      return ResponseEntity.noContent().build();
    }

    return ResponseEntity.notFound().build();
  }
}
