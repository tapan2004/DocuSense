# DocuSense — Enterprise Secure RAG Platform

> **Role-Based, Department-Scoped Retrieval-Augmented Generation** — an enterprise AI document intelligence system where security is enforced at the vector retrieval layer, not the application layer.

---

## What is DocuSense?

DocuSense is a full-stack RAG (Retrieval-Augmented Generation) platform built for enterprise environments where **different users must only see answers derived from documents they are authorized to access**.

Most RAG systems retrieve documents first and apply access control after. DocuSense enforces RBAC + department scoping **inside the vector similarity search query itself**, making it impossible for a user to receive an answer grounded in unauthorized content — even through prompt injection.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         DocuSense RAG Pipeline                       │
└─────────────────────────────────────────────────────────────────────┘

  User Query
      │
      ▼
  ┌─────────────┐
  │ PII Redact  │  ── Strips emails, SSNs, phone numbers, IPs
  └──────┬──────┘
         │
         ▼
  ┌─────────────┐
  │ Redis Cache │  ── Exact-match MD5 hash lookup (10-min TTL)
  │  (Exact)    │     Cache HIT → return instantly, log query
  └──────┬──────┘
         │ MISS
         ▼
  ┌─────────────────┐
  │ Query Rewriting │  ── LLM rewrites follow-up questions into
  │  (Multi-Turn)   │     standalone queries using chat history
  └────────┬────────┘
           │
           ▼
  ┌─────────────┐
  │    HyDE     │  ── LLM generates a hypothetical answer;
  │  Expansion  │     embed that for higher-precision retrieval
  └──────┬──────┘
         │
         ▼
  ┌──────────────────┐
  │  Semantic Cache  │  ── pgvector cosine distance < 0.05
  │  (pgvector)      │     Cache HIT → return, skip LLM call
  └────────┬─────────┘
           │ MISS
           ▼
  ┌──────────────────────────────────┐
  │         Hybrid Search            │
  │  ┌─────────────┐ ┌────────────┐ │
  │  │  pgvector   │ │  SQL LIKE  │ │  ── Semantic + Lexical, both
  │  │  (Semantic) │ │ (Lexical)  │ │     scoped by RBAC filter
  │  └──────┬──────┘ └─────┬──────┘ │
  └─────────┼──────────────┼────────┘
            └──────┬───────┘
                   │ Merged & Deduplicated
                   ▼
  ┌─────────────────┐
  │  LLM Re-Ranking │  ── Top 8 candidates → ranked by LLM → Top 4
  └────────┬────────┘
           │
           ▼
  ┌──────────────────┐
  │ Context Pruning  │  ── Jaccard similarity removes irrelevant
  │   (Jaccard)      │     sentences from parent chunks
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │   LLM Answer     │  ── Gemini generates answer from pruned context
  │   Generation     │     + conversation history
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │  Groundedness    │  ── Second LLM call evaluates if answer is
  │   Guardrail      │     supported by context → flags hallucinations
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │  Cache Write &   │  ── Store in Redis + pgvector semantic cache
  │  Query Logging   │     Log tokens, cost, latency, cache status
  └──────────────────┘
```

---

## Tech Stack

### Backend — `DocuSenseApi`

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.1, Java 21 |
| AI / LLM | Spring AI 2.0, Google Gemini (`gemini-2.0-flash`) |
| Embeddings | Google Gemini Embedding API (768 dimensions) |
| Vector Store | pgvector (HNSW index, Cosine distance) |
| Document Parsing | Apache Tika (PDF, DOCX, TXT) |
| Relational DB | PostgreSQL + Spring Data JPA / Hibernate |
| Caching | Redis (exact-match) + pgvector (semantic) |
| Security | Spring Security, JWT (JJWT 0.12.6), BCrypt |
| Concurrency | Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor`) |
| Resilience | Spring Retry (exponential backoff for LLM rate-limits) |
| Config | springboot3-dotenv (.env file support) |

### Frontend — `DocuSenseUi`

| Layer | Technology |
|---|---|
| Framework | React 19 + Vite 8 |
| Styling | TailwindCSS 4 |
| State | React Hooks (useState, useEffect, useRef) |
| HTTP | Fetch API with JWT Bearer token injection |

---

## Key Features

### 🔐 Security-First Vector Retrieval
Access control is enforced **inside** the `VectorStore.similaritySearch()` call via metadata filter expressions. A user from the `HR` department with `ROLE_USER` clearance **cannot** receive results from `Finance` or `ROLE_ADMIN` documents — at the database level, not the application level.

```
Filter: (department_owner == 'General' OR department_owner == 'HR')
        AND required_role IN ['ROLE_USER']
```

### 📄 Parent-Child Chunking
Documents are split into **parent chunks (1200 tokens)** for context richness and **child chunks (300 tokens)** for retrieval precision. Child chunks are embedded and searched; parent chunks are passed to the LLM for answer generation.

### 🧠 Contextual Chunk Prefixing
Before embedding, each child chunk is prefixed with a 1-sentence LLM-generated context summary situating it within its parent section. This dramatically improves retrieval quality for ambiguous or short chunks. Generated concurrently using Java 21 Virtual Threads.

### 💰 Two-Layer Cost Optimization
- **Layer 1 — Redis**: Exact query hash match → zero LLM calls
- **Layer 2 — pgvector**: Semantic cache → catches paraphrased versions of cached queries
- Result: LLM API costs approach zero for frequently asked questions

### 📊 Full Observability
Admin/Manager role users have access to a real-time dashboard showing:
- Total LLM cost (USD, calculated from token usage)
- Cache hit rate (%)
- RLHF satisfaction rate (thumbs up/down ratio)
- Average end-to-end latency (ms)
- Paginated query logs with per-user filtering
- Feedback audit trail

---

## Project Structure

```
DocuSense/
├── DocuSenseApi/                  # Spring Boot Backend
│   ├── src/main/java/com/docusense/backend/
│   │   ├── config/                # Redis, Security, Spring AI config
│   │   ├── controller/            # REST API endpoints
│   │   ├── dto/                   # Request/Response DTOs
│   │   ├── model/                 # JPA Entities
│   │   ├── repository/            # Spring Data JPA Repositories
│   │   ├── security/              # JWT filter, token util, UserDetails
│   │   └── service/               # Core business logic
│   │       ├── SearchService.java          # 7-stage RAG pipeline (443 lines)
│   │       ├── DocumentIngestionService.java  # Chunking + embedding pipeline
│   │       ├── ContextPruningService.java  # Jaccard-based pruning
│   │       ├── PiiRedactorService.java     # Regex PII redaction
│   │       └── SecurityFilterService.java  # RBAC filter expression builder
│   └── src/main/resources/
│       └── application.properties
│
└── DocuSenseUi/                   # React Frontend
    └── src/
        ├── components/
        │   ├── Auth.jsx                    # Login / Register
        │   ├── Dashboard.jsx               # Role-gated sidebar nav
        │   ├── SearchRag.jsx               # Conversational chat UI
        │   ├── UploadDoc.jsx               # Drag & drop ingestion
        │   ├── ManageDocs.jsx              # Document catalog + delete
        │   └── ObservabilityDashboard.jsx  # Metrics + logs
        └── utils/api.js                    # Centralized API client
```

---

## API Endpoints

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a new user (always ROLE_USER) |
| `POST` | `/api/auth/login` | Public | Login, receive JWT |
| `POST` | `/api/documents/upload` | Manager/Admin | Upload + ingest a document |
| `GET` | `/api/documents` | Any | List authorized documents |
| `DELETE` | `/api/documents/{id}` | Manager/Admin | Delete document + vector embeddings |
| `POST` | `/api/search` | Any | One-shot semantic search |
| `POST` | `/api/search/chat` | Any | Conversational RAG with history |
| `POST` | `/api/feedback` | Any | Submit thumbs up/down RLHF feedback |
| `GET` | `/api/observability/stats` | Manager/Admin | Aggregate metrics |
| `GET` | `/api/observability/logs` | Manager/Admin | Paginated query logs |
| `GET` | `/api/observability/feedbacks` | Manager/Admin | Paginated feedback audit |

---

## Running Locally

### Prerequisites
- Java 21+
- Maven 3.9+
- PostgreSQL 15+ with the **pgvector** extension enabled
- Redis (any recent version)
- Node.js 20+
- A Google Gemini API key

### 1. Database Setup

```sql
-- In PostgreSQL
CREATE DATABASE docusense;
\c docusense
CREATE EXTENSION IF NOT EXISTS vector;
```

### 2. Backend Setup

```bash
cd DocuSenseApi

# Copy and fill in your credentials
cp .env.example .env
```

Edit `.env`:
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=docusense
DB_USERNAME=postgres
DB_PASSWORD=your_password

GEMINI_API_KEY=your_gemini_api_key

JWT_SECRET=your_secret_key_at_least_32_chars
```

```bash
# Run the Spring Boot application
./mvnw spring-boot:run
# API starts at http://localhost:8080
```

### 3. Frontend Setup

```bash
cd DocuSenseUi

npm install
npm run dev
# UI starts at http://localhost:5173
```

### 4. First Use
1. Open `http://localhost:5173`
2. Register an account (select your department)
3. Login — you'll receive a JWT scoped to your department and role
4. Ask questions — only documents authorized for your role/department will be retrieved

> **Note:** To upload documents and access the Observability dashboard, a user must have `ROLE_MANAGER` or `ROLE_ADMIN`. These roles must be assigned directly in the database — registration always creates `ROLE_USER` to prevent privilege escalation.

---

## Roles & Access Control

| Role | Search | Upload Docs | Delete Docs | Observability |
|---|---|---|---|---|
| `ROLE_USER` | ✅ Own dept + General | ❌ | ❌ | ❌ |
| `ROLE_MANAGER` | ✅ Own dept + General | ✅ | ✅ | ✅ |
| `ROLE_ADMIN` | ✅ All depts | ✅ | ✅ | ✅ |

**Departments:** `Engineering`, `HR`, `Finance`, `General`

Documents tagged `General` are accessible to all authenticated users regardless of department.

---

## Environment Variables Reference

| Variable | Description |
|---|---|
| `DB_HOST` | PostgreSQL host |
| `DB_PORT` | PostgreSQL port (default: 5432) |
| `DB_NAME` | Database name |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `GEMINI_API_KEY` | Google Gemini API key |
| `JWT_SECRET` | JWT signing secret (min 32 characters) |

---

Built by **Tapan Manna** — a demonstration of production-grade RAG engineering with enterprise security, multi-layer caching, and full observability.
