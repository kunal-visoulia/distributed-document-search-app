#!/bin/bash
BASE="http://localhost:8080/api/v1"
T1="tenant-acme"
T2="tenant-globex"

echo "═══════════════════════════════════════"
echo "  Document Search Service — API Tests"
echo "═══════════════════════════════════════"

echo -e "\n[1] Health Check"
curl -s "$BASE/health"
echo ""

echo -e "\n[2a] Create: Financial Report (tenant-acme)"
DOC1=$(curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T1" \
  -d '{"title":"Q4 2025 Revenue Report","content":"The company achieved record revenue of 2.4 billion in Q4 2025 representing 23 percent year-over-year growth. Cloud services drove most of the growth.","docType":"report","tags":["finance","quarterly","revenue"],"metadata":{"author":"Jane Smith"}}')
echo "$DOC1"
DOC1_ID=$(echo "$DOC1" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "  → Created: $DOC1_ID"

echo -e "\n[2b] Create: Technical Spec (tenant-acme)"
DOC2=$(curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T1" \
  -d '{"title":"Microservices Migration Plan","content":"Plan to migrate monolithic application to microservices using Kubernetes and circuit breakers with Resilience4j.","docType":"technical","tags":["architecture","microservices"]}')
echo "$DOC2"
DOC2_ID=$(echo "$DOC2" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "  → Created: $DOC2_ID"

echo -e "\n[2c] Create: Policy (tenant-acme)"
curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T1" \
  -d '{"title":"Remote Work Policy 2026","content":"Employees may work remotely up to 3 days per week with minimum 50 Mbps internet.","docType":"policy","tags":["hr","remote-work"]}'
echo ""

echo -e "\n[2d] Create: Document for tenant-globex"
curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T2" \
  -d '{"title":"Globex Sales Strategy","content":"Globex plans to expand into Asian markets targeting 500M revenue.","docType":"strategy","tags":["sales","expansion"]}'
echo ""

echo -e "\nWaiting 2s for OpenSearch refresh..."
sleep 2

echo -e "\n[3a] Search: 'revenue growth' (tenant-acme)"
curl -s "$BASE/search?q=revenue+growth&tenant=$T1" -H "X-Tenant-Id: $T1"
echo ""

echo -e "\n[3b] Search: 'microservices' (tenant-acme)"
curl -s "$BASE/search?q=microservices&tenant=$T1" -H "X-Tenant-Id: $T1"
echo ""

echo -e "\n[3c] Search: fuzzy typo 'remte work' (tenant-acme)"
curl -s "$BASE/search?q=remte+work&tenant=$T1" -H "X-Tenant-Id: $T1"
echo ""

echo -e "\n[4a] Tenant Isolation: globex searching for acme docs"
curl -s "$BASE/search?q=revenue&tenant=$T2" -H "X-Tenant-Id: $T2"
echo ""

echo -e "\n[4b] Tenant mismatch (should return 403)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/search?q=revenue&tenant=$T2" -H "X-Tenant-Id: $T1")
echo "  → HTTP Status: $HTTP_CODE"

echo -e "\n[5] Get Document by ID: $DOC1_ID"
curl -s "$BASE/documents/$DOC1_ID" -H "X-Tenant-Id: $T1"
echo ""

echo -e "\n[6] Cross-tenant access (should return 404)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/documents/$DOC1_ID" -H "X-Tenant-Id: $T2")
echo "  → HTTP Status: $HTTP_CODE"

echo -e "\n[7a] Delete Document: $DOC2_ID"
curl -s -X DELETE "$BASE/documents/$DOC2_ID" -H "X-Tenant-Id: $T1"
echo ""

echo -e "\n[7b] Verify deleted (should return 404)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/documents/$DOC2_ID" -H "X-Tenant-Id: $T1")
echo "  → HTTP Status: $HTTP_CODE"

echo -e "\n[8a] Missing tenant header (should return 400)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/documents" -H "Content-Type: application/json" -d '{"title":"test","content":"test"}')
echo "  → HTTP Status: $HTTP_CODE"

echo -e "\n[8b] Invalid body (should return 400)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/documents" -H "Content-Type: application/json" -H "X-Tenant-Id: $T1" -d '{"title":""}')
echo "  → HTTP Status: $HTTP_CODE"

echo -e "\n[8c] Not found (should return 404)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/documents/nonexistent" -H "X-Tenant-Id: $T1")
echo "  → HTTP Status: $HTTP_CODE"

echo -e "\n═══════════════════════════════════════"
echo "  All tests complete!"
echo "  Swagger UI: http://localhost:8080/swagger-ui.html"
echo "═══════════════════════════════════════"