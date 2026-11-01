package com.manish.spring_boot_ai_project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingProviderConfig {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderConfig.class);

  @Bean
  public EmbeddingProvider embeddingProvider(
      @Value("${embeddings.provider:jina}") String provider,
      @Value("${JINA_API_KEY:}") String jinaKey,
      @Value("${HF_API_KEY:}") String hfKey,
      @Value("${embeddings.jina.url:" + JinaEmbeddingProvider.DEFAULT_URL + "}") String jinaUrl,
      @Value("${embeddings.huggingface.url:" + HuggingFaceEmbeddingProvider.DEFAULT_URL + "}")
          String hfUrl) {
    log.info("Configuring EmbeddingProvider with active provider: '{}'", provider);

    if ("huggingface".equalsIgnoreCase(provider)) {
      if (hfKey == null || hfKey.isBlank()) {
        throw new IllegalStateException(
            "HF_API_KEY must be set when embeddings.provider=huggingface");
      }
      return new HuggingFaceEmbeddingProvider(hfKey, hfUrl);
    }

    if ("jina".equalsIgnoreCase(provider)) {
      if (jinaKey == null || jinaKey.isBlank()) {
        throw new IllegalStateException("JINA_API_KEY must be set when embedding.provider=jina");
      }
      return new JinaEmbeddingProvider(jinaKey, jinaUrl);
    }
    throw new IllegalArgumentException(
        "Unknown embeddings.provider: '"
            + provider
            + "'. Supported values are: 'jina', 'huggingface'");
  }
}
