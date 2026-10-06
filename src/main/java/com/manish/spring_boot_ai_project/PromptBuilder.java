package com.manish.spring_boot_ai_project;

import java.util.List;

public class PromptBuilder {

  private String systemInstruction = "";
  private List<ContextChunk> context = List.of();
  private String question = "";

  public PromptBuilder withSystemInstruction(String systemInstruction) {
    this.systemInstruction = systemInstruction;
    return this;
  }

  public PromptBuilder withContext(List<ContextChunk> context) {
    this.context = context;
    return this;
  }

  public PromptBuilder withQuestion(String question) {
    this.question = question;
    return this;
  }

  public String build() {
    StringBuilder sb = new StringBuilder();
    sb.append(systemInstruction).append("\n\n");
    sb.append("Context:\n");
    for (ContextChunk chunk : context) {
      sb.append("[").append(chunk.sourceFilename()).append("]\n");
      sb.append(chunk.content()).append("\n\n");
    }
    sb.append("Question: ").append(question);
    return sb.toString();
  }
}
