package com.manish.spring_boot_ai_project;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Document {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  private String filename;
  private Instant uploadedAt;

  public Document() {}
  ;

  public Document(String filename, Instant uploadedAt) {
    this.filename = filename;
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

  public void setUploadedAt(Instant uploadedAt) {
    this.uploadedAt = uploadedAt;
  }
}
