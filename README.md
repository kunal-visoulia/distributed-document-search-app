# Distributed Document Search Service — Prototype

Multi-tenant document search service built with **Java 21**, **Spring Boot 3.2**, **OpenSearch 2.11**, and **Redis 7**.

## Architecture

```
Client → [TenantFilter → RateLimitFilter] → Controller → Service → OpenSearch / Redis
```

| Component | Role |
|-----------|------|
| OpenSearch | Primary data store + full-text search (index-per-tenant) |
| Redis | Query cache (60s TTL) + document cache (5min TTL) + generation-based invalidation |
| Spring Boot | REST API, tenant isolation, rate limiting, caching |

### Prototype Simplifications vs Production

| Prototype | Production |
|-----------|------------|
| Single service (read + write) | Separate Search Service + Document Service |
| Sync write to OpenSearch | Kafka → Flink → OpenSearch (async) |
| In-memory rate limiting (Bucket4j) | Kong rate-limiting plugin + Redis |
| Client provides X-Tenant-Id header | Kong extracts tenant_id from JWT |
| No auth | Cognito + Kong OIDC plugin |

## Quick Start

```bash
docker-compose up --build

# Wait ~30s for OpenSearch to start, then:
chmod +x scripts/test-api.sh
./scripts/test-api.sh
```

API available at `http://localhost:8080`. OpenSearch Dashboards at `http://localhost:5601`.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/documents` | Index a new document |
| GET | `/api/v1/search?q={query}&tenant={tenantId}` | Full-text search |
| GET | `/api/v1/documents/{id}` | Retrieve document by ID |
| DELETE | `/api/v1/documents/{id}` | Remove a document |
| GET | `/api/v1/health` | Health check with dependencies |

All endpoints (except health) require `X-Tenant-Id` header.

## Search Features

- **BM25 relevance ranking** — title matches boosted 3x over content
- **Fuzzy search** — handles typos (`remte` → `remote`)
- **Highlighting** — matched terms wrapped in `<em>` tags
- **Faceted aggregations** — counts by docType and tags

## Multi-Tenancy

Index-per-tenant (`docs_tenant-acme`, `docs_tenant-globex`). Complete physical isolation. Indices created lazily on first write. TenantIndexService is the single place that constructs index names.

## Caching

Generation-based cache invalidation. On write/delete, `tenant_gen:{tenant}` counter increments. Old search cache keys become orphaned and expire via TTL. O(1) invalidation.

## AI Tool Usage

Developed with Claude (Anthropic) for architecture design, tradeoff analysis, and boilerplate generation. All code reviewed and adapted based on production experience with Kafka → Flink → OpenSearch pipelines and Kong API gateway.
