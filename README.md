# Distributed Document Search Service

A multi-tenant document search service built with **Java 17**, **Spring Boot 3.2**, **OpenSearch 2.11**, and **Redis 7**. Supports full-text search with BM25 relevance ranking, fuzzy matching, highlighting, and faceted aggregations across physically isolated tenant indices.

## Quick Start

### Prerequisites

- Docker Desktop installed and running

### Run
```bash
docker compose up --build
```

Wait ~60 seconds for OpenSearch to initialize. App is ready when you see:
```
app-1 | Started DocSearchApplication in X.XXX seconds
```

API available at `http://localhost:8080`
OpenSearch Dashboards at `http://localhost:5601`
Swagger UI at `http://localhost:8080/swagger-ui.html`

---

## API Endpoints

All endpoints (except health and swagger) require `X-Tenant-Id` header.

| Method   | Endpoint                                     | Description                         |
|----------|----------------------------------------------|-------------------------------------|
| `POST`   | `/api/v1/documents`                          | Index a new document                |
| `POST`   | `/api/v1/documents/bulk`                     | Bulk index multiple documents       |
| `GET`    | `/api/v1/search?q={query}&tenant={tenantId}` | Full-text search                    |
| `GET`    | `/api/v1/documents/{id}`                     | Retrieve document by ID             |
| `DELETE` | `/api/v1/documents/{id}`                     | Remove a document                   |
| `GET`    | `/api/v1/health`                             | Health check with dependency status |

---

## Performance (Prototype — Single Node)

| Operation | Cold (no cache) | Warm (cache hit) | Improvement |
|-----------|----------------|-----------------|-------------|
| Search "revenue growth" | ~47ms | ~21ms | 2.2x |
| Search "the" (broad, 14 hits) | ~47ms | ~9ms | 5.2x |

On the prototype with 16 documents, cold searches take 30-50ms and cache hits take 9-21ms. The absolute numbers aren't meaningful at this scale — what matters is that cache hits bypass OpenSearch entirely. At production scale with millions of documents, cold search might take 200-400ms but cache hits stay under 10ms. That's where caching really pays off.

Run `api-performance-benchmarking/benchmark.sh` to reproduce.

---

## Sample API Requests

A Postman collection with all requests and test assertions is available under `postman-collection/`. Import it into Postman and run requests top-to-bottom for a complete demo.

### 1. Health Check
```bash
curl http://localhost:8080/api/v1/health
```
```json
{
  "status": "success",
  "message": "All dependencies healthy",
  "timestamp": "2026-03-15T14:00:00Z",
  "data": {
    "serviceStatus": "healthy",
    "uptimeSeconds": 120,
    "dependencies": {
      "opensearch": { "status": "healthy", "latencyMs": 8, "cluster": "green" },
      "redis": { "status": "healthy", "latencyMs": 1 }
    }
  }
}
```

### 2. Create a Document
```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-acme" \
  -d '{
    "title": "Q4 2025 Revenue Report",
    "content": "The company achieved record revenue of $2.4 billion in Q4 2025.",
    "docType": "report",
    "tags": ["finance", "quarterly", "revenue"],
    "metadata": {"author": "Jane Smith", "department": "Finance"}
  }'
```
```json
{
  "status": "success",
  "message": "Document indexed successfully",
  "timestamp": "2026-03-15T14:00:01Z",
  "data": {
    "id": "doc_a1b2c3d4e5f67890",
    "tenantId": "tenant-acme",
    "title": "Q4 2025 Revenue Report",
    "createdAt": "2026-03-15T14:00:01Z"
  }
}
```

### 3. Search Documents
```bash
curl "http://localhost:8080/api/v1/search?q=revenue+growth&tenant=tenant-acme" \
  -H "X-Tenant-Id: tenant-acme"
```
```json
{
  "status": "success",
  "message": "Search completed",
  "timestamp": "2026-03-15T14:00:05Z",
  "data": {
    "query": "revenue growth",
    "totalHits": 1,
    "tookMs": 47,
    "results": [
      {
        "id": "doc_a1b2c3d4e5f67890",
        "title": "Q4 2025 Revenue Report",
        "score": 3.67,
        "highlights": {
          "title": ["Q4 2025 <em>Revenue</em> Report"],
          "content": ["record <em>revenue</em>...year-over-year <em>growth</em>"]
        },
        "tags": ["finance", "quarterly", "revenue"],
        "docType": "report"
      }
    ],
    "facets": {
      "docType": { "report": 1 },
      "tags": { "finance": 1, "quarterly": 1, "revenue": 1 }
    }
  }
}
```

### 4. Search with Typo (Fuzzy Matching)
```bash
curl "http://localhost:8080/api/v1/search?q=revnue&tenant=tenant-acme" \
  -H "X-Tenant-Id: tenant-acme"
```

Returns the same result — fuzzy matching corrects "revnue" → "revenue".

### 5. Tenant Isolation
```bash
curl "http://localhost:8080/api/v1/search?q=revenue&tenant=tenant-globex" \
  -H "X-Tenant-Id: tenant-globex"
```
```json
{
  "status": "success",
  "message": "Search completed",
  "data": { "query": "revenue", "totalHits": 0, "results": [], "facets": {} }
}
```

### 6. Retrieve Document by ID
```bash
curl http://localhost:8080/api/v1/documents/{id} \
  -H "X-Tenant-Id: tenant-acme"
```

Returns full document including content (unlike search which returns highlights only).

### 7. Delete Document
```bash
curl -X DELETE http://localhost:8080/api/v1/documents/{id} \
  -H "X-Tenant-Id: tenant-acme"
```
```json
{
  "status": "success",
  "message": "Document deleted",
  "data": { "id": "doc_a1b2c3d4e5f67890", "tenantId": "tenant-acme" }
}
```

### 8. Tenant Mismatch — 403
```bash
curl "http://localhost:8080/api/v1/search?q=revenue&tenant=tenant-globex" \
  -H "X-Tenant-Id: tenant-acme"
```
```json
{
  "status": "failure",
  "message": "X-Tenant-Id header must match tenant query parameter",
  "errors": [{ "code": "DS006", "description": "X-Tenant-Id header must match tenant query parameter" }]
}
```

### 9. Missing Tenant Header — 400
```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/json" \
  -d '{"title": "test", "content": "test"}'
```
```json
{
  "status": "failure",
  "message": "Missing required header: X-Tenant-Id",
  "errors": [{ "code": "DS002", "description": "Invalid or missing tenant identifier" }]
}
```

---

## Key Design Decisions

**OpenSearch as sole data store.** No relational database. Documents are search-first data — OpenSearch handles CRUD and full-text search. Eliminates dual-write complexity.

**Index-per-tenant.** Each tenant gets a physically isolated OpenSearch index (`docs_{tenantId}`). No noisy neighbor. Clean tenant deletion (drop index). `TenantIndexService` is the single place in the codebase that constructs index names.

**Generation-based cache invalidation.** On write/delete, `tenant_gen:{tenant}` counter increments in Redis. Old search cache keys become orphaned and expire via TTL. O(1) invalidation — no KEYS/SCAN.

**Filter chain ordering.** `RequestLoggingFilter` (correlation ID) → `TenantFilter` (tenant extraction) → `RateLimitFilter` (needs tenant context). Each filter depends on the previous one's output.

**Tenant mismatch check.** Search endpoint validates `X-Tenant-Id` header matches the `tenant` query parameter. Returns 403 on mismatch. Defense-in-depth against parameter tampering.

## Search Features

- **BM25 relevance ranking** — title matches boosted 3x over content
- **Fuzzy search** — `fuzziness: AUTO` handles typos (edit distance 1-2 based on term length)
- **Highlighting** — matched terms wrapped in `<em>` tags on title and content
- **Faceted aggregations** — document counts by docType and tags returned alongside results
- **Source filtering** — content field excluded from search results (only highlights returned)

## Caching Strategy
```
Redis Keys:
  search:{tenant}:{generation}:{hash(query)}   TTL: 60s    Search results
  doc:{tenant}:{docId}                          TTL: 5min   Full document
  tenant_gen:{tenant}                           No TTL      Generation counter
```

Graceful degradation: if Redis is down, all requests bypass cache and query OpenSearch directly.

## Tech Stack

| Component      | Version | Purpose                            |
|----------------|---------|------------------------------------|
| Java           | 17      | Language                           |
| Spring Boot    | 3.2.3   | Framework                          |
| OpenSearch     | 2.11.1  | Primary data store + search engine |
| Redis          | 7       | Cache layer                        |
| Bucket4j       | 8.7.0   | Per-tenant rate limiting           |
| Docker Compose | —       | Multi-service orchestration        |
| Lombok         | —       | Boilerplate reduction              |

## Prototype vs Production

| Prototype                             | Production                                              |
|---------------------------------------|---------------------------------------------------------|
| Single service (read + write)         | Separate Search Service + Document Service              |
| Sync write to OpenSearch (201)        | Kafka → Flink → OpenSearch (202 Accepted)               |
| In-memory rate limiting (Bucket4j)    | Kong rate-limiting plugin + Redis                       |
| Client provides X-Tenant-Id           | Kong extracts tenant_id from JWT (Cognito)              |
| Single OS node, 0 replicas           | Multi-AZ cluster, 1+ replicas                           |

## Testing

### Postman Collection

Import `postman-collection/Document_Search_Service_Demo.postman_collection.json` into Postman. Run requests top-to-bottom — each request has test assertions that validate the response automatically.

The collection covers: health check, document CRUD, relevance ranking, fuzzy search, faceted aggregations, tenant isolation, tenant mismatch, error handling, and rate limiting headers.

### Swagger UI

Open http://localhost:8080/swagger-ui.html for interactive API documentation and testing.

## AI Tool Usage

Developed with assistance from Claude (Anthropic) for architecture design, tradeoff analysis, edge case identification, and boilerplate code generation. All code was reviewed and adapted based on production experience building event-driven pipelines (Kafka → Flink → OpenSearch) with Kong API gateway.