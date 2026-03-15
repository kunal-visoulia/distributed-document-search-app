package com.docsearch.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Document entity stored in OpenSearch (primary data store).
 * One index per tenant: docs_{tenantId}
 *
 * The @Document annotation sets a default index for Spring Data.
 * Actual index is resolved dynamically by TenantIndexService.
 */
@org.springframework.data.elasticsearch.annotations.Document(indexName = "documents")
@Setting(shards = 1, replicas = 0)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentEntity {

    @Id
    private String id;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "standard"),
        otherFields = @InnerField(suffix = "raw", type = FieldType.Keyword)
    )
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(type = FieldType.Keyword)
    private String docType;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Object, enabled = false)
    private Map<String, Object> metadata;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;
}
