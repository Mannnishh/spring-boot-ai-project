package com.manish.spring_boot_ai_project;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {
  @Autowired private MockMvc mvc;

  @MockitoBean private DocumentRepository repository;

  @MockitoBean private Tika tika;

  @MockitoBean private ChunkingPipeline chunkingPipeline;

  @Test
  void unknownIdReturns404() throws Exception {
    when(repository.findById(42L)).thenReturn(Optional.empty());
    mvc.perform(get("/documents/42")).andExpect(status().isNotFound());
  }
}
