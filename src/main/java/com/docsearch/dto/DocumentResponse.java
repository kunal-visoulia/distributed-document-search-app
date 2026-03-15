package com.docsearch.dto;

import com.docsearch.document.DocumentEntity;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {
    private String id;
    private String tenantId;
    private String title;
    private String content;
    private String docType;
    private List<String> tags;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public static DocumentResponse from(DocumentEntity doc, String tenantId) {
        return DocumentResponse.builder()
                .id(doc.getId()).tenantId(tenantId)
                .title(doc.getTitle()).content(doc.getContent())
                .docType(doc.getDocType()).tags(doc.getTags())
                .metadata(doc.getMetadata())
                .createdAt(doc.getCreatedAt()).updatedAt(doc.getUpdatedAt())
                .build();
    }
}