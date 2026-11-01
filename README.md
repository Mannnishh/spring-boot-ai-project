# Spring Boot AI RAG Project

A production-grade Retrieval-Augmented Generation (RAG) backend service built with Spring Boot 4, Spring AI 2, and PostgreSQL pgvector. It ingests multi-format documents, extracts and chunks text, computes semantic embeddings, and generates grounded, cited answers using Groq LLM without hallucination.

---

## What It Does
* **Document Ingestion**: Uploads `.txt` and `.pdf` files, extracts raw text via Apache Tika, and splits content into overlapping semantic chunks.
* **Vector Embeddings & Storage**: Computes 384-dimensional embeddings using Jina AI (`jina-embeddings-v3`) and indexes them in PostgreSQL using the `pgvector` extension with cosine similarity (`<=>`).
* **Semantic Retrieval & Grounded Q&A**: Given a natural-language question, the system retrieves the nearest document chunks, constructs an augmented prompt, and calls Groq LLM (`openai/gpt-oss-120b`) to answer strictly from the context with cited sources.
* **Resilience & Security**: Secured with stateless JWT authentication (Spring Security OAuth2 Resource Server) and decorated with exponential-backoff retries (`@Retryable` and `@Recover`) to survive external rate limits.

---

## How It Works (Pipeline)

```
[User Upload] ──► Apache Tika (Text Extraction) ──► ChunkingStrategy (Sliding Window)
                                                            │
                                                            ▼
[pgvector DB] ◄── Jina AI Embeddings (384-dim) ◄────────────┘
     │
     ▼ (Cosine Similarity Search: <=>)
[Nearest Chunks] ──► PromptBuilder (Grounding) ──► Groq LLM ──► [Grounded Answer + Citations]
```

---

## Prerequisites
* **Docker & Docker Compose** installed and running.
* **Git** and **Java 17+**.
* **Groq API Key**: Free LLM key from [Groq Console](https://console.groq.com/keys).
* **Jina AI API Key**: Free embeddings key (10M free tokens) from [Jina AI](https://jina.ai).

---

## Setup & Quickstart

### 1. Clone the Repository
```bash
git clone https://github.com/tanfaizs/spring-boot-ai-project.git
cd spring-boot-ai-project
```

### 2. Configure Environment Variables
Copy the example environment file:
```bash
cp .env.example .env
```
Open `.env` and fill in your keys:
```properties
POSTGRES_DB=ragdb
POSTGRES_USER=raguser
POSTGRES_PASSWORD=ragpassword123
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ragdb
SPRING_DATASOURCE_USERNAME=raguser
SPRING_DATASOURCE_PASSWORD=ragpassword123
GROQ_API_KEY=your_groq_api_key_here
JINA_API_KEY=your_jina_api_key_here
EMBEDDINGS_PROVIDER=jina
```

### 3. Start the Stack with Docker Compose
```bash
docker compose up --build
```
> The PostgreSQL container automatically initializes the `vector` extension on first boot via `docker/initdb/01-vector.sql`.

Wait until the logs display:
```text
Started SpringBootAiProjectApplication in ... seconds
```

---

## Try It

Because the application is secured with Spring Security (JWT), all requests require a Bearer token.

### 1. Obtain a JWT Token
```bash
curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username": "demo", "password": "demopassword"}'
```
Copy the token string from the JSON response and set it as an environment variable:
```bash
export TOKEN="your_copied_token_here"
```

### 2. Upload Sample Documents
Ingest the sample corporate documents:
```bash
curl -X POST http://localhost:8080/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@docs/curriculum/sample-data/remote-work-policy.txt"
```

### 3. Ask a Question
```bash
curl -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question": "What is the equipment stipend for remote work?"}'
```
Response:
```json
{
  "answer": "The equipment stipend for remote work is a one-time annual amount of $500.\n\nSource: [remote-work-policy.txt]",
  "citedDocuments": [
    "remote-work-policy.txt"
  ]
}
```

---

## Interactive Demo Surface: Swagger UI

You can test every endpoint interactively via browser:
👉 **`http://localhost:8080/swagger-ui.html`**

1. Expand `POST /login`, click **Try it out**, and log in with `demo` / `demopassword`.
2. Copy the token string.
3. Click the green **Authorize 🔓** button at the top right, paste the token, and click Authorize.
4. Try `POST /ask` or `POST /documents/upload` directly from the UI!

---

## Sample Questions & Grounding Cache
A pre-validated cache of 7 verification questions across the sample corpus is documented in [`sample-qa.md`](sample-qa.md).

---

## Known Limitations
* **No OCR for Scanned Documents**: `vendor-contract-summary-scanned.pdf` is an image-only PDF. Apache Tika extracts 0 text characters, so the model correctly reports: *"I don't have enough information to answer that."* OCR (e.g., Tesseract) would be the next architectural iteration.
* **Rate Limits & Resilience**: Uses Groq and Jina AI / Hugging Face endpoints behind an EmbeddingProvider adapter. To handle transient failures, @Retryable with exponential backoff retries 4 times before failing cleanly with HTTP 503 Service Unavailable.
* **Synchronous Ingestion**: Documents are chunked and embedded synchronously on the HTTP upload thread. For high-volume production loads, ingestion should be offloaded to an asynchronous message queue (e.g., Kafka / RabbitMQ).

---

## Tech Stack
* **Framework**: Spring Boot 4.1.1, Spring Framework 7
* **AI & LLM Integration**: Spring AI 2.0.1, Groq Cloud API (`openai/gpt-oss-120b`)
* **Vector Embeddings**: Jina AI (`jina-embeddings-v3`, 384 dimensions)
* **Vector Database**: PostgreSQL 17 + `pgvector`
* **Text Extraction**: Apache Tika 4.0.0
* **Security**: Spring Security 7 (OAuth2 Resource Server, stateless JWT via JJWT)
* **Resilience**: Spring Retry with Exponential Backoff
* **Documentation**: SpringDoc OpenAPI 3.1.1 (Swagger UI)
* **Build & Style**: Maven Wrapper, Spotless (Google Java Format)
