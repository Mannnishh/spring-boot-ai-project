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
- **API keys:**
  - A **Groq** API key, for LLM calls (Groq has a free plan; see its
    [rate limits page](https://console.groq.com/docs/rate-limits)) —
    create one from the
    [GroqCloud API Keys console](https://console.groq.com/keys) (sign up
    first if you don't have an account).
  - A **Hugging Face** access token, for embeddings (Groq has no embeddings
    API) — create one as described in Hugging Face's
    [User access tokens](https://huggingface.co/docs/hub/security-tokens)
    page; Day 4 says which permission the token needs. Check Hugging Face's
    [Inference Providers pricing](https://huggingface.co/docs/inference-providers/pricing)
    page before relying on it: at the time of writing it says free users
    receive no monthly credits and "can use Inference Providers by
    purchasing credits, or by upgrading to PRO", so a free account must
    purchase credits before the embeddings calls work. If you would rather
    not pay, or Hugging Face's embeddings won't work for you, Jina AI is the alternative that
    [Day 19](19-oop-retrospective.md) switches between by configuration.
- **IDE:** [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) is
  recommended. Since the 2025.3 "unified" release, JetBrains ships one
  installer whose free tier (what used to be Community Edition) covers
  everything in this curriculum; the paid Ultimate subscription is
  optional (see [Day 1's IDE note](01-day1-git-and-style.md#ide-setup-note)).
  VS Code with the Java and Spring extensions is a fine alternative if you
  already prefer that setup.

## How to use this curriculum

- **One file per day** (or per day-range, where days are grouped — e.g.
  `06-07-file-upload-tika.md` covers Day 6 and Day 7 together). Work through
  them in order; the material is dependency-ordered the same way the app
  actually gets built, so there's no benefit to skipping around.
- **Each day follows the same structure:** a concept **primer**, **reference
  links** to real official documentation for everything mentioned, the
  **exercise** itself, a **checkpoint** (a concrete, runnable command or test
  that proves the day's work actually functions), and an **interview
  angle** — a short note connecting the day's work to something you could
  raise in an interview. Day 20–21 is the exception: it is a buffer day and a
  mock-interview rehearsal, so it has no primer or reference links and ends
  with a single checkpoint; its Day 21 prep sends you back through the other
  days' interview angles.
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
  and show a reasonable trade-off, not to be read before you try. Worked-example
  days (Days 2, 4, 5) end instead with the vocabulary or complete code for
  reference, and Day 1 and Day 18 have no key because their output is your
  own repository, tooling setup, or README.
- **Config files.** Days 3, 4 and 15 show Spring configuration as
  `application.yml`; the upgrade day, Day 8–10 and Day 18 show some settings
  as `application.properties` lines. They are two spellings of the same
  thing. Use whichever file your project actually has (and don't put the
  same key in both), turning `a.b.c=value` into nested YAML or back as needed.
- **Days that build on Day 2's Java/OOP material carry a short "Java pattern
  note"** tying that day's work back to the vocabulary from Day 2 (e.g.
  dependency injection as Inversion of Control, Strategy for pluggable
  chunking, Builder for prompt construction, Decorator for the retry
  wrapper). Day 19 then reviews all of them and adds two more, Factory
  Method and Adapter, together with the Dependency Inversion Principle.
- **Every technical claim, tool, or API mentioned links to a real, official
  source**, verified at authoring time. If you ever find a link that's gone
  stale, that's worth a quick note back to whoever maintains this curriculum
  — but nothing here was written from memory or guessed.

## Full schedule

| Day | Focus |
|---|---|
| Day 1 | Git/branching/commit hygiene + code style tooling + IDE setup (AM). Spring Boot CRUD skeleton starts (PM). |
| Day 2 | Advanced Java/OOP refresher (standalone, not yet wired into Spring). SOLID, interfaces vs. abstract classes, composition over inheritance, generics, streams/lambdas/Optional, exception-hierarchy design, preview of Strategy/Builder/Decorator patterns used later, dependency injection as a concrete case of Inversion of Control. |
| Day 3 | Finish CRUD skeleton + Docker Compose (Spring Boot + Postgres/pgvector). Checkpoint: `docker-compose up`, CRUD endpoints return 200s, first feature-branch PR merged. Java pattern note: dependency injection as Inversion of Control. |
| Day 4 | Groq + Hugging Face "hello world" connectivity, `.env` secrets hygiene. Checkpoint: curl returns a real completion + a real embedding vector. |
| Day 5 | RAG fundamentals deep-dive (one standalone script, not yet wired into Spring). Embeddings/vector space, cosine similarity, chunking trade-offs. |
| Day 6–7 | File upload + Tika text extraction. |
| Before Day 8 | One-time upgrade to Spring Boot 4 + Spring AI 2 (only if your project was generated on older versions). |
| Day 8–10 | Carry-over fixes from the Day 3–7 review, then chunking strategy + embedding pipeline → pgvector storage, run automatically on every upload. Java pattern note: Strategy pattern for pluggable chunking strategies. |
| Day 11–14 | `/ask` endpoint: embed → similarity search → prompt build → Groq → cited answer, plus an iteration day for answer quality (chunk size, prompt tweaks, citation format). Java pattern note: Builder pattern for prompt construction. |
| Day 15 | Rate-limit/retry resilience for free-tier APIs [core]. Java pattern note: Decorator pattern for the retry wrapper. |
| Day 16 | Spring Security (JWT) [optional — cut first if behind]. |
| Day 17 | Carry-over fixes from the Day 8–15 review (delete leaves orphan chunks, empty upload returns 500, and one known issue that Day 19 repairs), then frontend or Swagger/Postman polish. |
| Day 18 | Carry-over fixes that a clean clone would fail on (`mvnw` not executable, Jina key missing in Docker, Hugging Face key required at startup, no pgvector extension on a fresh database), then README, sample-question cache, demo dataset prep. |
| Day 19 | Java/OOP + design-patterns retrospective: review where each pattern appeared across the build, then refactor one piece to intentionally apply patterns not yet used: put the embeddings provider behind an `EmbeddingProvider` interface (Dependency Inversion, Adapter, Factory Method) so Hugging Face and Jina AI can be swapped by configuration without touching the `/ask` endpoint. |
| Day 20 | Buffer (absorb slippage from any prior day; a cut-order list if you are behind). |
| Day 21 | Mock interview run-through of the live demo. |

## Day files

1. [Day 1 — Git & Style](01-day1-git-and-style.md)
2. [Day 2 — Java/OOP Refresher](02-day2-java-oop-refresher.md)
3. [Day 3 — CRUD & Docker](03-day3-crud-docker.md)
4. [Day 4 — Groq & Embeddings Hello World](04-day4-groq-embeddings-hello-world.md)
5. [Day 5 — RAG Fundamentals](05-day5-rag-fundamentals.md)
6. [Day 6–7 — File Upload & Tika](06-07-file-upload-tika.md)
7. [Before Day 8 — Upgrade to Boot 4 + Spring AI 2](07b-upgrade-boot4-spring-ai2.md)
8. [Day 8–10 — Chunking & Embedding Pipeline](08-10-chunking-embedding-pipeline.md)
9. [Day 11–14 — /ask Endpoint & RAG Pipeline](11-14-ask-endpoint-rag-pipeline.md)
10. [Day 15 — Rate-Limit Resilience](15-rate-limit-resilience.md)
11. [Day 16 — JWT Security](16-jwt-security.md)
12. [Day 17 — Frontend & API Docs Polish](17-frontend-api-docs-polish.md)
13. [Day 18 — README & Demo Prep](18-readme-demo-prep.md)
14. [Day 19 — OOP Retrospective](19-oop-retrospective.md)
15. [Day 20–21 — Buffer & Mock Interview](20-21-buffer-mock-interview.md)
