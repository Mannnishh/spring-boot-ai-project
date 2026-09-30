package com.manish.spring_boot_ai_project;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChunkingConfig {

  @Bean
  public ChunkingStrategy chunkingStrategy() {
    return new FixedSizeChunkingStrategy(1000, 100);
  }
}
