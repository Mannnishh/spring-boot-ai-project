package com.manish.spring_boot_ai_project;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  private String filename;

  @Column(columnDefinition = "TEXT")
  private String content;

  private Instant uploadedAt;

  public Document() {}
  ;

  public Document(String filename, String content, Instant uploadedAt) {
    this.filename = filename;
    this.content = content;
    this.uploadedAt = uploadedAt;
  }

  public Long getId() {
    return id;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public void setUploadedAt(Instant uploadedAt) {
    this.uploadedAt = uploadedAt;
  }
}
