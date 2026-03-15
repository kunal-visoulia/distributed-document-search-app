package com.docsearch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkCreateRequest {

    @NotEmpty(message = "Documents list cannot be empty")
    @Valid
    private List<CreateDocumentRequest> documents;
}