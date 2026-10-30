package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A grounded answer plus the filenames of the documents it was drawn from.")
public record AskResponse(
    @Schema(
            description = "The model's answer, based only on the retrieved context.",
            example = "The equipment stipend is $500. Source: remote-work-policy.txt")
        String answer,
    @Schema(description = "Distinct filenames of the documents the retrieved chunks came from.")
        List<String> citedDocuments) {}
