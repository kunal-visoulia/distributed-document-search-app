#!/bin/bash
# ══════════════════════════════════════════════════════════════
# Document Search Service — API Test Script
# Run after: docker-compose up --build
# ══════════════════════════════════════════════════════════════

BASE="http://localhost:8080/api/v1"
T1="tenant-acme"
T2="tenant-globex"

echo "═══════════════════════════════════════"
echo "  Document Search Service — API Tests"
echo "═══════════════════════════════════════"

# ─── 1. Health Check ─────────────────────────────────────────
echo -e "\n[1] Health Check"
curl -s "$BASE/health" | python3 -m json.tool

# ─── 2. Create Documents (tenant-acme) ───────────────────────
echo -e "\n[2a] Create: Financial Report (tenant-acme)"
DOC1=$(curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T1" \
  -d '{
    "title": "Q4 2025 Revenue Report",
    "content": "The company achieved record revenue of $2.4 billion in Q4 2025, representing a 23% year-over-year growth. Cloud services drove most of the growth with a 45% increase in annual recurring revenue. Operating margins expanded to 28%.",
    "docType": "report",
    "tags": ["finance", "quarterly", "revenue"],
    "metadata": {"author": "Jane Smith", "department": "Finance"}
  }')
echo "$DOC1" | python3 -m json.tool
DOC1_ID=$(echo "$DOC1" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo -e "\n[2b] Create: Technical Spec (tenant-acme)"
DOC2=$(curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T1" \
  -d '{
    "title": "Microservices Architecture Migration Plan",
    "content": "This document outlines the plan to migrate our monolithic application to a microservices architecture. Phase 1 involves decomposing the authentication and user management modules. We will use Kubernetes for orchestration and implement circuit breakers using Resilience4j.",
    "docType": "technical",
    "tags": ["architecture", "microservices", "migration"],
    "metadata": {"author": "Bob Johnson", "department": "Engineering"}
  }')
echo "$DOC2" | python3 -m json.tool
DOC2_ID=$(echo "$DOC2" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo -e "\n[2c] Create: Policy (tenant-acme)"
curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T1" \
  -d '{
    "title": "Remote Work Policy 2026",
    "content": "Employees may work remotely up to 3 days per week. All remote workers must maintain a dedicated workspace and reliable internet connection. Core collaboration hours are 10 AM to 3 PM local timezone.",
    "docType": "policy",
    "tags": ["hr", "remote-work"],
    "metadata": {"author": "HR Team"}
  }' | python3 -m json.tool

echo -e "\n[2d] Create: Document for tenant-globex"
curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T2" \
  -d '{
    "title": "Globex Sales Strategy",
    "content": "Globex plans to expand into Asian markets in 2026 with focus on Japan and South Korea. Revenue targets are set at $500M.",
    "docType": "strategy",
    "tags": ["sales", "expansion"],
    "metadata": {"author": "Sales VP"}
  }' | python3 -m json.tool

# Wait for OpenSearch refresh
echo -e "\nWaiting 2s for OpenSearch refresh..."
sleep 2

# ─── 3. Search ───────────────────────────────────────────────
echo -e "\n[3a] Search: 'revenue growth' (tenant-acme)"
curl -s "$BASE/search?q=revenue+growth&tenant=$T1" \
  -H "X-Tenant-Id: $T1" | python3 -m json.tool

echo -e "\n[3b] Search: 'microservices kubernetes' (tenant-acme)"
curl -s "$BASE/search?q=microservices+kubernetes&tenant=$T1" \
  -H "X-Tenant-Id: $T1" | python3 -m json.tool

echo -e "\n[3c] Search: fuzzy typo 'remte work' (tenant-acme)"
curl -s "$BASE/search?q=remte+work&tenant=$T1" \
  -H "X-Tenant-Id: $T1" | python3 -m json.tool

# ─── 4. Tenant Isolation ─────────────────────────────────────
echo -e "\n[4] Tenant Isolation: globex searching for acme docs"
curl -s "$BASE/search?q=revenue&tenant=$T2" \
  -H "X-Tenant-Id: $T2" | python3 -m json.tool

echo -e "\n[4b] Tenant mismatch: header=acme, param=globex → 403"
curl -s -o /dev/null -w "HTTP Status: %{http_code}" \
  "$BASE/search?q=revenue&tenant=$T2" \
  -H "X-Tenant-Id: $T1"
echo ""

# ─── 5. Get Document by ID ───────────────────────────────────
echo -e "\n[5] Get Document by ID: $DOC1_ID"
curl -s "$BASE/documents/$DOC1_ID" \
  -H "X-Tenant-Id: $T1" | python3 -m json.tool

# ─── 6. Get Document — wrong tenant → 404 ────────────────────
echo -e "\n[6] Get acme doc with globex tenant → 404"
curl -s "$BASE/documents/$DOC1_ID" \
  -H "X-Tenant-Id: $T2" | python3 -m json.tool

# ─── 7. Delete Document ──────────────────────────────────────
echo -e "\n[7] Delete Document: $DOC2_ID"
curl -s -X DELETE "$BASE/documents/$DOC2_ID" \
  -H "X-Tenant-Id: $T1" | python3 -m json.tool

echo -e "\n[7b] Verify deleted — GET returns 404"
curl -s "$BASE/documents/$DOC2_ID" \
  -H "X-Tenant-Id: $T1" | python3 -m json.tool

# ─── 8. Error Cases ──────────────────────────────────────────
echo -e "\n[8a] Missing tenant header → 400"
curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -d '{"title":"test","content":"test"}' | python3 -m json.tool

echo -e "\n[8b] Invalid request body → 400"
curl -s -X POST "$BASE/documents" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $T1" \
  -d '{"title":""}' | python3 -m json.tool

echo -e "\n[8c] Document not found → 404"
curl -s "$BASE/documents/nonexistent" \
  -H "X-Tenant-Id: $T1" | python3 -m json.tool

echo -e "\n═══════════════════════════════════════"
echo "  All tests complete!"
echo "═══════════════════════════════════════"
