# Spring Boot + AI Refresher — 21-Day Curriculum

## Goal

Over the next 21 days you'll build a document-Q&A (RAG — Retrieval-Augmented
Generation) application with Spring Boot: upload documents, extract and chunk
their text, embed and store the chunks in a vector database, and answer
natural-language questions about them with cited sources — ending with a
live, demo-ready project you can run and speak to confidently in interviews.
Along the way, every day also reinforces the advanced Java/OOP habits
(SOLID, interfaces vs. abstract classes, composition over inheritance,
generics, streams/lambdas/Optional, exception-hierarchy design, and the
design patterns that show up in a real pipeline) from day one, rather than as
an afterthought — those habits are meant to carry into how you build
everything else here and beyond.

## Prerequisites

- **Java/Spring:** rusty-but-present familiarity assumed. You don't need to
  remember every annotation, but you should have built a Spring Boot app
  before. Day 1–3 re-establish the basics as you go.
- **AI/ML:** no prior experience assumed. Day 2 and Day 5 build the
  vocabulary and concepts from scratch before you touch any AI-specific code.
- **Docker:** installed and working. On Windows, use the WSL2 backend
  (Docker Desktop's WSL2 integration) — this is covered in detail on Day 3.
- **API keys** (both free-tier, no credit card required):
  - A **Groq** API key, for LLM calls — create one from the
    [GroqCloud API Keys console](https://console.groq.com/keys) (sign up
    first if you don't have an account).
  - A **Gemini** API key, for embeddings (Groq has no embeddings API) — see
    Google's [Gemini API key guide](https://ai.google.dev/gemini-api/docs/api-key)
    for how to create one in Google AI Studio. (Hugging Face's Inference API
    is a viable alternative for embeddings if you'd rather use that provider;
    Day 4 covers the swap.)
- **IDE:** [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/)
  (free) is recommended, or VS Code with the Java and Spring extensions if
  you already prefer that setup.

## How to use this curriculum

- **One file per day** (or per day-range, where days are grouped — e.g.
  `06-07-file-upload-tika.md` covers Day 6 and Day 7 together). Work through
  them in order; the material is dependency-ordered the same way the app
  actually gets built, so there's no benefit to skipping around.
- **Each day follows the same structure:** a concept **primer**, **reference
  links** to real official documentation for everything mentioned, a
  **checkpoint** (a concrete, runnable command or test that proves the day's
  work actually functions), the **exercise** itself, and an **interview
  angle** — a short note connecting the day's work to something you could
  raise in an interview.
- **Two exercise styles**, matched to how new the material is:
  - Genuinely new AI/RAG concepts get a **worked example + variation**: full
    working code is shown, then you make a targeted change (swap a
    parameter, change a provider, tune a threshold) to build understanding
    through modification rather than blank-page recall.
  - Familiar Spring Boot/CRUD territory gets a **guided TODO with hints**:
    scaffolding and method signatures are given, the logic is yours to
    write, and there's a trade-offs note plus an answer key.
- **Guided-TODO days have a hidden answer key below the exercise.** Attempt
  the exercise yourself first — the answer key is there to check your work
  and show a reasonable trade-off, not to be read before you try.
- **Days that build on Day 2's Java/OOP material carry a short "Java pattern
  note"** tying that day's work back to the vocabulary from Day 2 (e.g.
  dependency injection as Inversion of Control, Strategy for pluggable
  chunking, Builder for prompt construction, Decorator for the retry
  wrapper).
- **Every technical claim, tool, or API mentioned links to a real, official
  source**, verified at authoring time. If you ever find a link that's gone
  stale, that's worth a quick note back to whoever maintains this curriculum
  — but nothing here was written from memory or guessed.

## Full schedule

| Day | Focus |
|---|---|
| Day 1 | Git/branching/commit hygiene + code style tooling + IDE setup (AM). Spring Boot CRUD skeleton starts (PM). |
| Day 2 | Advanced Java/OOP refresher (standalone, not yet wired into Spring). SOLID, interfaces vs. abstract classes, composition over inheritance, generics, streams/lambdas/Optional, exception-hierarchy design, preview of Strategy/Builder/Decorator patterns used later. |
| Day 3 | Finish CRUD skeleton + Docker Compose (Spring Boot + Postgres/pgvector). Checkpoint: `docker-compose up`, CRUD endpoints return 200s, first feature-branch PR merged. Java pattern note: dependency injection as Inversion of Control. |
| Day 4 | Groq + Gemini/HF "hello world" connectivity, `.env` secrets hygiene. Checkpoint: curl returns a real completion + a real embedding vector. |
| Day 5 | RAG fundamentals deep-dive (standalone scripts, not yet wired into Spring). Embeddings/vector space, cosine similarity, chunking trade-offs, prompt construction patterns. |
| Day 6–7 | File upload + Tika text extraction. |
| Day 8–10 | Chunking strategy + embedding pipeline → pgvector storage. Java pattern note: Strategy pattern for pluggable chunking strategies. |
| Day 11–14 | `/ask` endpoint: embed → similarity search → prompt build → Groq → cited answer, plus an iteration day for answer quality (chunk size, prompt tweaks, citation format). Java pattern note: Builder pattern for prompt construction. |
| Day 15 | Rate-limit/retry resilience for free-tier APIs [core]. Java pattern note: Decorator pattern for the retry wrapper. |
| Day 16 | Spring Security (JWT) [optional — cut first if behind]. |
| Day 17 | Frontend or Swagger/Postman polish. |
| Day 18 | README, sample-question cache, demo dataset prep. |
| Day 19 | Java/OOP + design-patterns retrospective: review where each pattern appeared across the build, then refactor one piece to intentionally apply a pattern not yet used (e.g. a Factory/Adapter so a second LLM or embeddings provider could be swapped in without touching the `/ask` endpoint). |
| Day 20 | Buffer (absorb slippage from any prior day). |
| Day 21 | Mock interview run-through of the live demo. |

## Day files

1. [Day 1 — Git & Style](01-day1-git-and-style.md)
2. [Day 2 — Java/OOP Refresher](02-day2-java-oop-refresher.md)
3. [Day 3 — CRUD & Docker](03-day3-crud-docker.md)
4. [Day 4 — Groq & Embeddings Hello World](04-day4-groq-embeddings-hello-world.md)
5. [Day 5 — RAG Fundamentals](05-day5-rag-fundamentals.md)
6. [Day 6–7 — File Upload & Tika](06-07-file-upload-tika.md)
7. [Day 8–10 — Chunking & Embedding Pipeline](08-10-chunking-embedding-pipeline.md)
8. [Day 11–14 — /ask Endpoint & RAG Pipeline](11-14-ask-endpoint-rag-pipeline.md)
9. [Day 15 — Rate-Limit Resilience](15-rate-limit-resilience.md)
10. [Day 16 — JWT Security](16-jwt-security.md)
11. [Day 17 — Frontend & API Docs Polish](17-frontend-api-docs-polish.md)
12. [Day 18 — README & Demo Prep](18-readme-demo-prep.md)
13. [Day 19 — OOP Retrospective](19-oop-retrospective.md)
14. [Day 20–21 — Buffer & Mock Interview](20-21-buffer-mock-interview.md)
