package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A natural-language question to answer from the uploaded documents")
public record AskRequest(
    @Schema(
            description = "the question. Retrieval embeds this exact text.",
            example = "What is the equipment stipend for remote work?")
        String question) {}
