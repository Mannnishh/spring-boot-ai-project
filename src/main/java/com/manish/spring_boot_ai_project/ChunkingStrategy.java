package com.manish.spring_boot_ai_project;

import java.util.List;

public interface ChunkingStrategy {

  List<String> chunk(String text);
}
