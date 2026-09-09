package com.manish.spring_boot_ai_project;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/documents")
public class DocumentController {

  private final DocumentRepository repository;

  public DocumentController(DocumentRepository repository) {
    this.repository = repository;
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
