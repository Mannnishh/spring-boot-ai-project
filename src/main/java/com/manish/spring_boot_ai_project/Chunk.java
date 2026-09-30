package com.manish.spring_boot_ai_project;

import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class Chunk {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long documentId;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(columnDefinition = "vector(384)")
  @JdbcTypeCode(SqlTypes.VECTOR)
  @Array(length = 384)
  private float[] embedding;

  public Chunk() {}

  public Chunk(Long documentId, String content, float[] embedding) {
    this.documentId = documentId;
    this.content = content;
    this.embedding = embedding;
  }

  public Long getId() {
    return id;
  }

  public Long getDocumentId() {
    return documentId;
  }

  public void setDocumentId(Long documentId) {
    this.documentId = documentId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public float[] getEmbedding() {
    return embedding;
  }

  public void setEmbedding(float[] embedding) {
    this.embedding = embedding;
  }
}
