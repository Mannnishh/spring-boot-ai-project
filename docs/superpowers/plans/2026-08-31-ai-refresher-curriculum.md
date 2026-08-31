# Spring Boot + AI Refresher Curriculum — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Author the full 18-day, self-guided curriculum document set that teaches a Spring Boot + AI (RAG) refresher project, per `docs/superpowers/specs/2026-08-31-ai-refresher-curriculum-design.md`.

**Architecture:** One markdown file per day (or day-range, matching the spec's grouping) under `docs/curriculum/`, plus an overview/index file. Each file is self-contained: concept primer, verified reference links, a runnable checkpoint, an exercise (worked-example-with-variation or guided-TODO-with-hints per topic novelty), an interview-angle note, and — for guided-TODO days — a clearly separated answer key at the bottom.

**Tech Stack:** Markdown only. No application code is written in this plan — only curriculum content. Research is done live via WebSearch/WebFetch at authoring time (never recalled from memory) to satisfy the spec's citation policy.

**Spec:** `docs/superpowers/specs/2026-08-31-ai-refresher-curriculum-design.md`

## Global Constraints

- Every technical claim, tool, or API mentioned MUST link to a real, official source, verified via WebFetch (HTTP 200, content matches the claim) at authoring time. Never fabricate or recall-from-memory a URL. If no real source can be found, say so in the text instead of inventing a link.
- Each day file has one concrete, runnable checkpoint (a command + expected result) — not "read and understand."
- Exercise style: **worked example + variation** for genuinely new AI/RAG concepts (embeddings, chunking, prompt construction, similarity search, provider connectivity). **Guided TODO + hints + separated answer key** for familiar Spring Boot/CRUD/tooling territory (controllers, entities, Docker Compose, JWT wiring, git/style setup).
- Answer-key content lives in a section titled `## Answer Key (attempt it yourself first!)` at the very bottom of the file, never interleaved with the exercise prompt.
- Deliverable directory: `docs/curriculum/`.
- Target pacing is ~6 hours per day of content — a guide for depth, not a hard word count.

---

## File Structure

- Create: `docs/curriculum/00-overview.md` — goal, prerequisites, how to use the curriculum, full schedule table, link index to every day file.
- Create: `docs/curriculum/01-day1-git-and-style.md`
- Create: `docs/curriculum/02-day2-crud-docker.md`
- Create: `docs/curriculum/03-day3-groq-embeddings-hello-world.md`
- Create: `docs/curriculum/04-day4-rag-fundamentals.md`
- Create: `docs/curriculum/05-06-file-upload-tika.md`
- Create: `docs/curriculum/07-09-chunking-embedding-pipeline.md`
- Create: `docs/curriculum/10-13-ask-endpoint-rag-pipeline.md`
- Create: `docs/curriculum/14-rate-limit-resilience.md`
- Create: `docs/curriculum/15-jwt-security.md`
- Create: `docs/curriculum/16-frontend-api-docs-polish.md`
- Create: `docs/curriculum/17-readme-demo-prep.md`
- Create: `docs/curriculum/18-buffer-mock-interview.md`

Each day file is independent (no shared code/imports), so tasks can run in any order except Task 1 (overview), which should be written first since its schedule table and link index are fixed by this plan regardless of day-file content, and revisited last for a consistency check.

---

### Task 1: Overview / index file

**Files:**
- Create: `docs/curriculum/00-overview.md`

**Interfaces:**
- Consumes: nothing.
- Produces: links to all 12 day files below (paths are fixed by this plan, so they can be written now even though the files don't exist yet).

- [ ] **Step 1: Draft the overview content**

Write `docs/curriculum/00-overview.md` containing:
- One-paragraph goal statement: build a document-Q&A (RAG) app with Spring Boot, ending demo-ready for interviews.
- Prerequisites section: rusty-but-present Java/Spring familiarity assumed; no prior AI/ML experience assumed; Docker installed; a Groq API key and a Gemini or Hugging Face API key (link both signup pages — verify via WebFetch in Step 2).
- "How to use this curriculum" section explaining: one file per day, each with a primer/links/checkpoint/exercise/interview-angle, guided-TODO days have a hidden-below answer key — attempt before reading it.
- Full schedule table (reproduce the 18-day table from the spec's "18-day schedule" section verbatim).
- Link index: a markdown list linking to each of the 12 day files by their exact relative paths (`01-day1-git-and-style.md`, `02-day2-crud-docker.md`, `03-day3-groq-embeddings-hello-world.md`, `04-day4-rag-fundamentals.md`, `05-06-file-upload-tika.md`, `07-09-chunking-embedding-pipeline.md`, `10-13-ask-endpoint-rag-pipeline.md`, `14-rate-limit-resilience.md`, `15-jwt-security.md`, `16-frontend-api-docs-polish.md`, `17-readme-demo-prep.md`, `18-buffer-mock-interview.md`).

- [ ] **Step 2: Verify prerequisite links**

Use WebFetch on the Groq console signup/API-keys page and the Gemini API (or Hugging Face inference API) signup/docs page. Confirm both resolve and are the correct official pages before linking them.

- [ ] **Step 3: Commit**

```bash
git add docs/curriculum/00-overview.md
git commit -m "docs: add curriculum overview and schedule index"
```

---

### Task 2: Day 1 — Git/branching/style + CRUD skeleton start

**Files:**
- Create: `docs/curriculum/01-day1-git-and-style.md`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing consumed by other files (conceptually precedes Task 3).

- [ ] **Step 1: Research and verify reference links**

Use WebSearch + WebFetch to find and confirm real, official sources for:
- Git branching workflow (e.g. the official Git SCM book chapter on branching, or GitHub's own docs on feature branch workflow).
- Conventional commit message conventions (the official conventionalcommits.org spec).
- `.gitignore` templates / GitHub's own `.gitignore` guidance, specifically for Java/Spring/`.env` patterns.
- Spring Initializr (`start.spring.io`) official docs/usage.
- A Java code formatter: Spotless (`github.com/diffplug/spotless`) or Google Java Format — pick one, verify its official repo/docs, and confirm Maven/Gradle plugin setup instructions from that same source.

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/01-day1-git-and-style.md` with:
- **Concept primer:** why feature branches over trunk-direct commits for a solo project (still a real interview talking point), what a `.gitignore` protects against, why automated formatting removes a whole class of PR bikeshedding.
- **Reference links:** every tool/concept above, using the verified URLs from Step 1.
- **Exercise (guided TODO + hints, familiar territory):**
  1. `git init` (if not already) and create a `.gitignore` covering `.env`, `target/`/`build/`, `.idea/`/`.vscode/`.
  2. Create a `feature/project-skeleton` branch.
  3. Use Spring Initializr (web UI or `curl start.spring.io`) to generate a skeleton with Web, JPA, PostgreSQL driver, and pgvector-compatible dependencies (note: exact pgvector dependency is finalized on Day 2 — Day 1 just needs Web + JPA + Postgres driver).
  4. Add the chosen formatter (Spotless or Google Java Format) to the build file with a `check`/`apply` task, run it once.
  5. Commit the skeleton on the feature branch with a conventional-style commit message.
- **Checkpoint:** `git log --oneline` shows at least one commit on `feature/project-skeleton`; running the formatter's check task exits 0.
- **Interview angle:** "How do you structure your git workflow and keep code style consistent across a team?" — he now has a concrete, first-hand answer.
- **Answer key:** not needed for this day (git/setup commands are given directly, not a design decision — no answer key section required).

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL included in the drafted file; confirm HTTP 200 and that the fetched content matches what's claimed about it.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/01-day1-git-and-style.md
git commit -m "docs: add day 1 curriculum (git workflow, style tooling, skeleton start)"
```

---

### Task 3: Day 2 — Finish CRUD skeleton + Docker Compose

**Files:**
- Create: `docs/curriculum/02-day2-crud-docker.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for official sources on:
- Spring Data JPA repository basics (`docs.spring.io` Spring Data JPA reference).
- Spring Boot REST controller basics (`docs.spring.io` Spring Boot reference, "Developing Web Applications" section).
- Docker Compose file reference (`docs.docker.com/compose/compose-file/`).
- The official `pgvector/pgvector` Docker image (`hub.docker.com` or the `pgvector/pgvector` GitHub repo's Docker instructions) — confirm the exact image tag documented there.
- Spring Boot's `application.yml` datasource configuration reference.

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/02-day2-crud-docker.md` with:
- **Concept primer:** what pgvector adds to plain Postgres (a vector column type + similarity search operators), why running Postgres via Docker Compose instead of a local install.
- **Reference links:** from Step 1, verified URLs.
- **Exercise (guided TODO + hints):**
  1. Define a simple domain entity relevant to the eventual RAG app (e.g. `Document` with id/filename/uploadedAt) with a JPA repository.
  2. Write a controller exposing basic CRUD endpoints for it. Give the class/method signatures as scaffolding; leave method bodies as TODOs with hints (e.g. "use `repository.findAll()`... what HTTP status for a 404 on `findById`?").
  3. Write a `docker-compose.yml` with a `pgvector/pgvector` service (verified image tag from Step 1) and the Spring Boot app service, wired via environment variables (no hardcoded credentials — reference `.env` from Day 1's `.gitignore`).
  4. Wire `application.yml` datasource properties to the Compose Postgres service.
- **Checkpoint:** `docker-compose up` starts both containers; `curl localhost:8080/documents` (or the equivalent chosen path) returns `200` with a JSON array (empty is fine); `git log` shows this merged from a feature branch (first practiced PR-style merge per Day 1's workflow).
- **Interview angle:** "Why colocate Postgres+pgvector instead of a separate vector database?" — trade-off note: fewer moving parts vs. a dedicated vector DB's specialized indexing at scale.
- **Answer key:** include a `## Answer Key (attempt it yourself first!)` section with the full controller method bodies and a complete `docker-compose.yml`.

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/02-day2-crud-docker.md
git commit -m "docs: add day 2 curriculum (CRUD skeleton, docker compose, pgvector)"
```

---

### Task 4: Day 3 — Groq + embeddings provider hello-world connectivity

**Files:**
- Create: `docs/curriculum/03-day3-groq-embeddings-hello-world.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for official sources on:
- Groq API docs, specifically its OpenAI-compatibility page (`console.groq.com/docs`).
- Spring AI's OpenAI-compatible client configuration docs (`docs.spring.io/spring-ai`), specifically how to point `base-url` at a non-OpenAI OpenAI-compatible endpoint.
- Whichever embeddings provider the spec names (Gemini API embeddings docs at `ai.google.dev`, or Hugging Face Inference API docs at `huggingface.co/docs`) — verify the actual embeddings endpoint docs, not just the general API landing page.
- Spring Boot's externalized configuration / `.env` handling (e.g. via `spring-dotenv` or `System.getenv` — verify whichever approach is documented officially; if using a third-party `.env` loader library, verify its actual GitHub repo, not a guess).

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/03-day3-groq-embeddings-hello-world.md` with:
- **Concept primer:** Groq serves an OpenAI-compatible chat completions API (fast inference, generous free tier, no embeddings endpoint); embeddings need a second provider.
- **Reference links:** verified URLs from Step 1.
- **Exercise (worked example + variation, genuinely new territory):**
  1. Show the full Spring AI `application.yml` config pointing the OpenAI client at Groq's base URL and model name, reading the API key from `.env`/environment.
  2. Show a minimal `@RestController` "hello world" endpoint that calls the chat model and returns the raw completion.
  3. Show the equivalent minimal call to the chosen embeddings provider (raw HTTP client is fine if Spring AI doesn't have a ready integration for it) returning a vector's dimension count.
  4. **Variation task:** have him change the Groq model name to a different available Groq model and re-run, then change the embeddings request's input text and confirm the returned vector dimension stays constant (teaches: model swap is config-only; embedding dimension is fixed per model).
- **Checkpoint:** `curl localhost:8080/hello-groq` returns a real natural-language completion; `curl localhost:8080/hello-embedding` returns a JSON array of floats with the provider's documented dimension count.
- **Interview angle:** "How do you handle secrets/API keys across environments?" — `.env` kept out of git (Day 1), read via Spring's externalized config, never hardcoded.

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/03-day3-groq-embeddings-hello-world.md
git commit -m "docs: add day 3 curriculum (Groq + embeddings hello world)"
```

---

### Task 5: Day 4 — RAG fundamentals deep-dive

**Files:**
- Create: `docs/curriculum/04-day4-rag-fundamentals.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for official/authoritative sources on:
- What retrieval-augmented generation is — prefer a primary source (a well-known RAG paper's abstract/page, e.g. the original Lewis et al. RAG paper on arXiv) alongside one accessible vendor explainer (e.g. an official Spring AI or a major cloud provider's RAG concepts page) — verify both.
- Cosine similarity — a reputable reference (e.g. a university course page, or a well-established docs page such as scikit-learn's pairwise metrics docs which documents cosine similarity precisely).
- Text chunking strategies — Spring AI's own `TokenTextSplitter`/document splitting docs, or LangChain's text splitter docs as a well-documented reference point (verify whichever is cited is real and matches the claim).
- Prompt construction / prompt engineering basics — an official vendor guide (e.g. OpenAI's or Anthropic's prompt engineering documentation, since the technique is provider-agnostic).

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/04-day4-rag-fundamentals.md` with:
- **Concept primer:** the RAG pattern end-to-end (why it beats stuffing everything into one prompt or fine-tuning for this use case); what an embedding vector represents; why cosine similarity (not Euclidean distance) is the standard similarity metric for text embeddings; chunking trade-offs (too small loses context, too large dilutes relevance and wastes tokens).
- **Reference links:** verified URLs from Step 1.
- **Exercise (worked example + variation, standalone — not yet wired into Spring):**
  1. Provide a small standalone script (plain Java `main` method or a scratch class, no Spring needed) that takes two hardcoded short text strings, calls the Day 3 embeddings endpoint/logic for both, and computes cosine similarity between the two vectors by hand (show the formula implemented directly, not via a library, so the math is visible).
  2. **Variation task:** have him run it against three text pairs he writes himself — one near-duplicate pair, one same-topic-different-wording pair, one unrelated pair — and record the three similarity scores, building intuition for what the numbers mean.
  3. Briefly show (as a worked example, no exercise) a naive fixed-size chunking function on a paragraph of text, and discuss what breaks if a chunk boundary falls mid-sentence.
- **Checkpoint:** the standalone script runs and prints three similarity scores where the near-duplicate pair scores highest and the unrelated pair scores lowest.
- **Interview angle:** "Walk me through what happens when a user asks your app a question" — he should now be able to narrate the embed → compare → retrieve → prompt pipeline conceptually before writing a line of the real pipeline.

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/04-day4-rag-fundamentals.md
git commit -m "docs: add day 4 curriculum (RAG fundamentals deep-dive)"
```

---

### Task 6: Day 5-6 — File upload + Tika extraction

**Files:**
- Create: `docs/curriculum/05-06-file-upload-tika.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for:
- Spring's official multipart file upload guide (`spring.io` guides or `docs.spring.io` reference).
- Apache Tika's official docs (`tika.apache.org`), specifically the `Tika` facade class / `AutoDetectParser` usage for plain text extraction.
- Maven Central or the Tika project's own docs for the exact `tika-core`/`tika-parsers` dependency coordinates (verify the artifact actually exists at the version cited).

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/05-06-file-upload-tika.md` with:
- **Concept primer:** why extraction is needed before chunking/embedding (raw PDFs/DOCX aren't plain text); what Tika abstracts away (format detection + parsing across many file types).
- **Reference links:** verified URLs from Step 1.
- **Exercise (guided TODO + hints, builds on familiar Spring MVC patterns):**
  1. Add the Tika dependency (verified coordinates).
  2. Scaffold a `POST /documents/upload` endpoint accepting `MultipartFile`, with the extraction call left as a TODO (hint: `new Tika().parseToString(inputStream)`).
  3. Scaffold persisting the extracted text alongside the `Document` entity from Day 2 (add a `content` column) — leave the repository save call as a TODO.
- **Checkpoint:** `curl -F file=@sample.pdf localhost:8080/documents/upload` returns the created `Document` with non-empty extracted text; a `.txt` and a `.pdf` sample both work.
- **Interview angle:** "How do you handle unstructured/heterogeneous input formats?" — Tika's format auto-detection as the answer.
- **Answer key:** full controller method body and entity/repository wiring.

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/05-06-file-upload-tika.md
git commit -m "docs: add day 5-6 curriculum (file upload, Tika extraction)"
```

---

### Task 7: Day 7-9 — Chunking + embedding pipeline → pgvector

**Files:**
- Create: `docs/curriculum/07-09-chunking-embedding-pipeline.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for:
- pgvector's official README/docs (`github.com/pgvector/pgvector`) for the `vector` column type, distance operators (`<=>` for cosine), and index types (IVFFlat/HNSW).
- Spring Data JPA custom-query docs for how to run a native query with the `vector` type from a repository.
- Spring AI's `TokenTextSplitter` docs (if using it) or, if hand-rolling chunking, no library citation needed there — but still cite the general chunking-strategy reference already verified on Day 4 (link back to it rather than re-verifying).

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/07-09-chunking-embedding-pipeline.md` with:
- **Concept primer:** the pipeline shape (extracted text → chunks → each chunk embedded → stored with a foreign key back to its source document); why storing chunk-level (not document-level) embeddings is what enables precise retrieval + citation later.
- **Reference links:** verified URLs from Step 1, plus an internal link back to Day 4's chunking discussion.
- **Exercise (guided TODO + hints for the Spring/persistence wiring; worked example for the pgvector-specific query since it's new):**
  1. Add a `Chunk` entity (`id`, `documentId`, `content`, `embedding vector(N)`) where `N` is the verified dimension from Day 3 — TODO the entity fields with hints.
  2. TODO a chunking function (fixed-size with overlap, per Day 4's discussion) — hint at the overlap parameter's purpose.
  3. TODO a pipeline method: for each chunk, call the embeddings provider, persist the `Chunk` row.
  4. **Worked example:** show the full native pgvector similarity query (`ORDER BY embedding <=> :queryVector LIMIT :k`) as a `@Query` in the repository, since the syntax is unfamiliar territory, not something to leave as a blank-page TODO.
- **Checkpoint:** after uploading a multi-paragraph document, a manual query (`SELECT count(*) FROM chunk WHERE document_id = ...`) shows multiple chunk rows, each with a non-null `embedding`; a manual similarity query against a hardcoded query vector returns rows ordered by distance.
- **Interview angle:** "How would you scale vector search beyond a few thousand rows?" — mention IVFFlat/HNSW indexing from the pgvector docs as the next step, even though this project's dataset is small enough not to need it.
- **Answer key:** full `Chunk` entity, chunking function, and pipeline method.

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/07-09-chunking-embedding-pipeline.md
git commit -m "docs: add day 7-9 curriculum (chunking + embedding pipeline)"
```

---

### Task 8: Day 10-13 — /ask endpoint (RAG pipeline) + iteration

**Files:**
- Create: `docs/curriculum/10-13-ask-endpoint-rag-pipeline.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for:
- Spring AI's `ChatClient`/prompt template docs (`docs.spring.io/spring-ai`) for constructing a prompt with retrieved context injected.
- Any official guidance on citation/grounding prompt patterns (e.g. an OpenAI or Anthropic doc on grounding answers in provided context) — verify the specific page, don't guess.

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/10-13-ask-endpoint-rag-pipeline.md` with:
- **Concept primer:** assembling the full pipeline (embed the question → pgvector similarity search from Day 7-9 → build a prompt that includes the top-k chunks with their source document names → call Groq → parse/return the answer plus which documents were cited).
- **Reference links:** verified URLs from Step 1, plus internal links back to Day 3 (Groq calls) and Day 7-9 (similarity query).
- **Exercise (guided TODO + hints — this composes prior building blocks rather than introducing new unfamiliar concepts):**
  1. Scaffold `POST /ask` accepting `{ "question": "..." }`.
  2. TODO: embed the question (reuse Day 3's embedding call).
  3. TODO: run the Day 7-9 similarity query for top-k chunks.
  4. TODO: build a prompt template that instructs the model to answer only from the provided context and cite source filenames — give the prompt's required *shape* (system instruction + context block + question) as a hint, leave exact wording to him.
  5. TODO: call Groq via the Day 3 chat client, return `{ answer, citedDocuments: [...] }`.
- **Iteration sub-section** (explicitly covering the extra day): guidance on what to tune if answers are poor — chunk size/overlap (back to Day 7-9), top-k value, prompt wording strictness — framed as a debugging checklist, not new code.
- **Checkpoint:** `curl -X POST localhost:8080/ask -d '{"question": "..."}'` against a known uploaded document returns an answer whose content is verifiably grounded in that document, with at least one cited filename matching the uploaded document.
- **Interview angle:** "How do you keep an LLM from hallucinating facts not in your data?" — the grounding/citation prompt pattern as the direct answer.
- **Answer key:** one full worked prompt template and the complete endpoint method.

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/10-13-ask-endpoint-rag-pipeline.md
git commit -m "docs: add day 10-13 curriculum (/ask RAG pipeline + iteration)"
```

---

### Task 9: Day 14 — Rate-limit/retry resilience

**Files:**
- Create: `docs/curriculum/14-rate-limit-resilience.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for:
- Groq's documented rate limits page (`console.groq.com/docs/rate-limits` or equivalent — verify exact path).
- Spring Retry's official docs (`docs.spring.io` or `github.com/spring-projects/spring-retry`) for `@Retryable`/backoff configuration, OR Resilience4j's official docs if that's the more standard Spring Boot pairing — verify which is more current and cite that one.

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/14-rate-limit-resilience.md` with:
- **Concept primer:** free-tier APIs throttle (requests/min, tokens/min); a 429 response needs graceful backoff, not a crash or a silent bad answer.
- **Reference links:** verified URLs from Step 1.
- **Exercise (guided TODO + hints — familiar Spring pattern, new only in what it's applied to):**
  1. Add the retry library dependency.
  2. TODO: wrap the Groq and embeddings calls with retry + exponential backoff on 429/5xx, with a max-attempts cap.
  3. TODO: on final failure, return a clear error response rather than letting the exception surface raw.
- **Checkpoint:** a deliberately induced failure (e.g. temporarily pointing the base URL at an invalid endpoint, or mocking a 429) shows retry attempts in the logs before failing gracefully with a clean error response.
- **Interview angle:** "Tell me about a time you handled a flaky external dependency" — this is a first-hand, demonstrable answer, not a hypothetical.
- **Answer key:** full retry-wrapped client code.

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/14-rate-limit-resilience.md
git commit -m "docs: add day 14 curriculum (rate-limit/retry resilience)"
```

---

### Task 10: Day 15 — Spring Security (JWT) [optional]

**Files:**
- Create: `docs/curriculum/15-jwt-security.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for:
- Spring Security's official reference docs (`docs.spring.io/spring-security`), specifically the OAuth2 Resource Server / JWT section.
- The `jjwt` library's official GitHub repo/docs if used for token issuance, or Spring Authorization Server's docs if that's the recommended current approach — verify which is actually current/recommended before citing.

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/15-jwt-security.md` with a clear **"Optional — cut this day first if you're behind schedule"** banner at the top.
- **Concept primer:** stateless auth via JWT — what's in a token, why signature verification replaces server-side session lookup.
- **Reference links:** verified URLs from Step 1.
- **Exercise (guided TODO + hints):**
  1. TODO: a `/login` endpoint issuing a signed JWT for a hardcoded or simple in-memory user.
  2. TODO: a security filter/config requiring a valid JWT on the `/ask` and `/documents/*` endpoints.
- **Checkpoint:** an unauthenticated `curl` to `/ask` returns 401; the same request with a valid token from `/login` succeeds.
- **Interview angle:** stateless vs. session-based auth trade-offs — a standard interview question this day directly prepares him for.
- **Answer key:** full login endpoint + security filter chain config.

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/15-jwt-security.md
git commit -m "docs: add day 15 curriculum (JWT security, optional)"
```

---

### Task 11: Day 16 — Frontend or Swagger/Postman polish

**Files:**
- Create: `docs/curriculum/16-frontend-api-docs-polish.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for:
- springdoc-openapi's official docs (`springdoc.org`) for auto-generating Swagger UI from a Spring Boot app.
- Postman's official docs on creating/exporting a collection, as the lighter-weight alternative.

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/16-frontend-api-docs-polish.md` presenting **two options** (his choice, not a TODO he must both complete):
- **Option A (lower effort, recommended given the timeline):** add springdoc-openapi, verify Swagger UI renders all endpoints built so far, annotate `/ask` and `/documents/upload` with example request/response bodies.
- **Option B (more polish, more time):** a minimal single-page frontend (plain HTML/JS, no framework needed) with an upload form and a question box calling `/ask`.
- **Reference links:** verified URLs from Step 1.
- **Checkpoint:** Option A — Swagger UI loads at its default path and every endpoint is invocable from it. Option B — the page successfully uploads a file and displays an `/ask` response in the browser.
- **Interview angle:** having a live, clickable demo surface (either one) is what actually gets used in an interview walkthrough — frame this day as "what you'll actually have open on screen."

- [ ] **Step 3: Verify all embedded links resolve**

Re-fetch every URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/16-frontend-api-docs-polish.md
git commit -m "docs: add day 16 curriculum (Swagger/Postman or frontend polish)"
```

---

### Task 12: Day 17 — README, sample-question cache, demo dataset prep

**Files:**
- Create: `docs/curriculum/17-readme-demo-prep.md`

- [ ] **Step 1: Research and verify reference links**

WebSearch + WebFetch for:
- A well-regarded README structure reference (e.g. the "Make a README" guide, or GitHub's own docs on READMEs) — verify the specific page.
- No new technical library citations expected this day; reuse/link back to Day 1's `.env`/setup material rather than re-deriving it.

- [ ] **Step 2: Draft the content**

Write `docs/curriculum/17-readme-demo-prep.md` with:
- **Concept primer:** why a demo needs a curated, *small* dataset and a few pre-validated questions — free-tier rate limits mean an interview-time live query against a huge corpus risks a slow or throttled response; a small known-good demo set avoids that.
- **Reference links:** verified URL from Step 1.
- **Exercise (guided TODO + hints):**
  1. TODO: write the project README covering setup (clone, `.env` keys needed, `docker-compose up`, sample curl commands) — give the required section headings as scaffolding, leave prose to him.
  2. TODO: pick 3-5 sample documents and pre-write 5-8 questions against them, run each once, save the question+answer pairs as a static `sample-qa.md` reference he can fall back to if a live call is slow/throttled during a demo.
- **Checkpoint:** a person unfamiliar with the project can follow the README from a clean checkout to a working `curl /ask` call with no undocumented steps.
- **Interview angle:** treating documentation and demo-reliability as part of the deliverable, not an afterthought, is itself worth mentioning.
- **Answer key:** not applicable — this day's output is inherently personal/project-specific content, not a fixed correct answer.

- [ ] **Step 3: Verify the embedded link resolves**

Re-fetch the README-guide URL; confirm HTTP 200 and correct content.

- [ ] **Step 4: Commit**

```bash
git add docs/curriculum/17-readme-demo-prep.md
git commit -m "docs: add day 17 curriculum (README, sample Q&A cache, demo prep)"
```

---

### Task 13: Day 18 — Buffer + mock interview run-through

**Files:**
- Create: `docs/curriculum/18-buffer-mock-interview.md`

- [ ] **Step 1: Draft the content**

Write `docs/curriculum/18-buffer-mock-interview.md` with:
- **Purpose statement:** this day has no new material — it exists to absorb slippage from prior days and to rehearse presenting the project.
- **Buffer guidance:** a short checklist of what to do if behind ("first cut Day 15 JWT, then trim Day 16 to Swagger-only, then reduce Day 17's sample question count") pointing back at the days already marked optional/cuttable.
- **Mock interview run-through:** a structured self-run exercise — set a 10-15 minute timer, walk through: (1) architecture overview out loud, (2) live demo of upload + ask, (3) answer 3 prepared "gotcha" questions collected from every prior day's "Interview angle" note (he should compile these himself by skimming back through Days 1-17 — this is the exercise, not a TODO to fill in code).
- **Checkpoint:** he completes one full run-through under time without needing to check notes for the live demo steps.
- No reference links required — this day is process, not new technical content.

- [ ] **Step 2: Commit**

```bash
git add docs/curriculum/18-buffer-mock-interview.md
git commit -m "docs: add day 18 curriculum (buffer day, mock interview run-through)"
```

---

### Task 14: Cross-file consistency pass

**Files:**
- Modify: `docs/curriculum/00-overview.md` (only if inconsistencies are found)

- [ ] **Step 1: Read every file in `docs/curriculum/`**

Confirm: every day file follows the structure (primer, links, checkpoint, exercise, interview angle, answer key where applicable); every internal cross-reference (e.g. Day 7-9 linking back to Day 4) points to the correct actual filename; the overview's link index and schedule table match the files that actually exist; no file contains "TBD"/"TODO" language outside of intentional exercise-scaffolding TODOs.

- [ ] **Step 2: Fix any drift found, then commit if changes were made**

```bash
git add docs/curriculum/
git commit -m "docs: fix cross-file consistency in curriculum set"
```

(Skip the commit if Step 1 found nothing to change.)
