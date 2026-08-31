# Spring Boot + AI Refresher — Learning Curriculum Design

## Purpose

Produce a self-contained, written curriculum document set that a friend (rusty
on both Spring Boot and AI/ML fundamentals) can follow independently to build
a document-Q&A (RAG) application, ending with a live-demo-ready project he can
speak to in interviews. This spec covers the *curriculum's* design — not the
application's implementation plan, which is authored separately once this
spec is approved.

## Audience & constraints

- **Learner:** the user's friend. Rusty on Spring Boot; new/rusty on AI/RAG
  concepts (embeddings, vector search, prompt construction).
- **Timeline:** 21 days, ~6 hours/day (~126 hours total).
- **Mode:** self-guided. He may or may not have Claude Code available while
  working — the document must stand on its own, not depend on live AI
  scaffolding.
- **Goal framing:** interview readiness — each stage should leave him able to
  explain *why*, not just *what*, and produce a runnable demo by day 21.
- **Habits, not just output:** advanced Java/OOP fundamentals (SOLID,
  interfaces vs. abstract classes, composition over inheritance, generics,
  streams/lambdas/Optional, exception-hierarchy design) must be reinforced
  from the start, not bolted on at the end, since they're the habits that
  are supposed to carry into how the rest of the project gets built.

## Application scope (carried over from prior discussion, not re-litigated here)

- Spring Boot app + Postgres/pgvector, both via Docker Compose.
- Groq (OpenAI-compatible endpoint) for LLM calls via Spring AI.
- Gemini or Hugging Face for embeddings (Groq has no embeddings API).
- Apache Tika for text extraction from uploaded files.
- Chunking + embedding pipeline storing vectors in pgvector.
- `/ask` endpoint: embed question → similarity search → build prompt → call
  Groq → return answer + citations.
- Rate-limit/retry handling for free-tier API limits.
- Optional Spring Security (JWT).
- Frontend or Swagger/Postman collection for demoing.
- README with setup steps.

## Pedagogical method

1. **Day-by-day structure**, dependency-ordered (git/style foundations →
   Spring skeleton → AI connectivity → ingestion → RAG pipeline → resilience
   → polish). Matches how the app naturally has to be built, so there's no
   artificial reordering to learn around.

2. **Per-day contents**, each day's doc file includes:
   - **Concept primer** — short, plain-language explanation of what's new
     that day.
   - **Reference links** — every tool, API, or concept mentioned links to
     real, verified official documentation (see Citation Policy below).
   - **Checkpoint** — a concrete, runnable success criterion (a curl command,
     a test, a docker-compose command) proving the day's work is functionally
     correct before moving on.
   - **Exercise** — the hands-on work, styled per the split below.
   - **Interview angle** — one short note connecting the day's work to a
     question or story he could raise in an interview (e.g., "why pgvector
     instead of a dedicated vector DB," "how do you handle third-party API
     rate limits").

3. **Exercise style split:**
   - Genuinely new AI/RAG concepts (embeddings, chunking, prompt
     construction, similarity search) → **worked example + variation**: full
     working code shown, then a modification task (swap a parameter, change
     a provider, tune a threshold) to build understanding through change
     rather than blank-page recall.
   - Familiar Spring Boot/CRUD territory (controllers, entities, Docker
     Compose, JWT wiring) → **guided TODO with hints**: scaffolding and
     function signatures given, logic left for him to write, with a
     trade-offs note and an answer-key reference he's told not to open until
     he's attempted it.

4. **Day 1 foundations:** before any Spring Boot code, Day 1 morning covers
   git branching workflow (feature branches, commit hygiene via Conventional
   Commits), a `.gitignore` that excludes secrets from the start, code style
   tooling (e.g. Spotless/Google Java Format) wired into the build, and IDE
   setup (IntelliJ IDEA Community, or VS Code + Java/Spring extensions). This
   is intentionally light — habit-setting, not a deep dive — and doubles as
   an interview-ready answer about branching/code-review practices.

5. **Java/OOP reinforcement thread (new addition):** a dedicated Day 2 —
   before any Spring-specific code — covers the advanced Java/OOP material
   this project actually exercises: SOLID principles, interfaces vs.
   abstract classes, composition over inheritance, generics, streams/
   lambdas/Optional, exception-hierarchy design, and a light preview of the
   specific design patterns (Strategy, Builder, Decorator) that reappear
   later. Every later day that exercises one of these patterns gets a short
   "Java pattern note" tying the day's work back to that Day 2 vocabulary
   (e.g. dependency injection as Inversion of Control on the CRUD day,
   Strategy for pluggable chunking, Builder for prompt construction,
   Decorator for the retry wrapper). A capstone day near the end (Day 19)
   has him refactor one existing piece to intentionally apply a pattern not
   yet used, turning the thread into a demonstrated habit rather than a
   one-off lecture.

## Citation policy (new requirement)

Every technical claim, tool, or API mentioned must link to its real,
authoritative source (official docs, RFC, vendor API reference) — verified
at authoring time via web search/fetch, never recalled from memory or
guessed. This is a hard requirement: fabricated or unverified URLs are worse
than no citation, since the whole point is that the friend can click through
and verify independently. If a specific claim can't be traced to a real
source, the doc says so rather than inventing one.

## 21-day schedule

```
Day 1     Git/branching/commit hygiene + code style tooling + IDE setup (AM)
          Spring Boot CRUD skeleton starts (PM)
Day 2     Advanced Java/OOP refresher (standalone, not yet wired into Spring)
          SOLID, interfaces vs. abstract classes, composition over inheritance,
          generics, streams/lambdas/Optional, exception-hierarchy design,
          preview of Strategy/Builder/Decorator patterns used later
Day 3     Finish CRUD skeleton + Docker Compose (Spring Boot + Postgres/pgvector)
          Checkpoint: docker-compose up, CRUD endpoints return 200s, first feature-branch PR merged
          Java pattern note: dependency injection as Inversion of Control
Day 4     Groq + Gemini/HF "hello world" connectivity, .env secrets hygiene
          Checkpoint: curl returns a real completion + a real embedding vector
Day 5     RAG fundamentals deep-dive (standalone scripts, not yet wired into Spring)
          Embeddings/vector space, cosine similarity, chunking trade-offs, prompt construction patterns
Day 6-7   File upload + Tika text extraction
Day 8-10  Chunking strategy + embedding pipeline → pgvector storage
          Java pattern note: Strategy pattern for pluggable chunking strategies
Day 11-14 /ask endpoint: embed → similarity search → prompt build → Groq → cited answer
          + iteration day for answer quality (chunk size, prompt tweaks, citation format)
          Java pattern note: Builder pattern for prompt construction
Day 15    Rate-limit/retry resilience for free-tier APIs  [core]
          Java pattern note: Decorator pattern for the retry wrapper
Day 16    Spring Security (JWT)  [optional — cut first if behind]
Day 17    Frontend or Swagger/Postman polish
Day 18    README, sample-question cache, demo dataset prep
Day 19    Java/OOP + design-patterns retrospective: review where each pattern
          appeared across the build, then refactor one piece to intentionally
          apply a pattern not yet used (e.g. a Factory/Adapter so a second
          LLM or embeddings provider could be swapped in without touching
          the /ask endpoint)
Day 20    Buffer (absorb slippage from any prior day)
Day 21    Mock interview run-through of the live demo
```

## Deliverable format

A `docs/curriculum/` directory in this repo:

- `00-overview.md` — goal, prerequisites, how to use this curriculum,
  schedule table, link index.
- `01-day1-git-and-style.md` through `21-day21-mock-interview.md` — one file
  per day (or per day-range where days are grouped, e.g. `06-07-upload-and-extraction.md`),
  each following the per-day structure above.
- Answer-key content for guided-TODO exercises lives in a clearly separated
  section (or sub-file) per day, not interleaved with the exercise prompt.

## Out of scope for this spec

- The application's own implementation plan (endpoints, entities, pipeline
  code) — written separately, informed by this curriculum's day boundaries,
  once each day's content is authored.
- Any actual Spring Boot/AI code — this spec only covers the curriculum
  documents themselves.

## Testing / validation

- Each day's checkpoint must be independently verifiable (a command that
  either passes or fails), not just "read and understand."
- Citation links are checked at authoring time (fetched/verified), not just
  pattern-matched to look like real URLs.
