package com.manish.spring_boot_ai_project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

  @Query(
      value =
          "SELECT * FROM chunk "
              + "ORDER BY embedding <=> CAST(:queryVector as vector) "
              + "LIMIT :k",
      nativeQuery = true)
  List<Chunk> findNearest(@Param("queryVector") String queryVector, @Param("k") int k);

  List<Chunk> findByDocumentId(Long documentId);

  @Transactional
  void deleteByDocumentId(Long documentId);
}
