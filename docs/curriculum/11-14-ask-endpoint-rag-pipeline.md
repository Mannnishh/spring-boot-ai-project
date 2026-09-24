# Day 11-14 — /ask Endpoint (RAG Pipeline) + Iteration

**Focus:** Wire the full RAG loop end-to-end behind one endpoint: embed the
question with Day 4's Hugging Face call, retrieve the nearest chunks with
Day 8–10's pgvector query, assemble a grounded prompt with a new
`PromptBuilder` — the Builder pattern Day 2 only defined, now implemented
for real — call Groq via Day 4's `ChatClient`, and return an answer plus
which source documents it came from. Then spend the remaining days
iterating on retrieval and prompt quality rather than writing new code.

Every external call and every query used today was already proven working
on an earlier day (Groq and Hugging Face on Day 4, the similarity query on
Day 8–10). Nothing here is unfamiliar territory — today is about
*composing* those pieces correctly, not learning a new one — so this day
is a guided-TODO exercise with hints, and it gets a real gated Answer Key
at the bottom, unlike the worked-example days behind it.

## Concept primer

### The full pipeline, in order

`POST /ask` with `{ "question": "..." }` runs exactly six steps:

1. **Embed the question.** Same Hugging Face `feature-extraction` call
   Day 4 proved and Day 8–10's `ChunkingPipeline` already reuses — the
   question is just another piece of text to turn into a 384-dimensional
   `float[]`.
2. **Retrieve the nearest chunks.** Day 8–10's `ChunkRepository.findNearest`
   native query, called for the first time from Java rather than by hand
   in `psql`. `ORDER BY embedding <=> CAST(:queryVector AS vector) LIMIT :k`
   returns the `k` chunks closest to the question's embedding.
3. **Resolve each chunk's source document.** Every `Chunk` carries a
   `documentId` (Day 8–10's whole reason for embedding at chunk granularity
   instead of document granularity). Look each one up via
   `DocumentRepository` to get the filename that will become both the
   in-prompt citation label and the API response's `citedDocuments` entry.
4. **Assemble the prompt.** Not string concatenation — a `PromptBuilder`
   (this day's Java pattern note) that puts a system instruction, the
   labeled context chunks, and the question together in order.
5. **Call Groq.** The same `ChatClient` Day 4 wired up — `chatClient
   .prompt(promptString).call().content()` — just with a longer,
   structured prompt string instead of `"Say hello in one sentence."`.
6. **Return the answer plus its citations.** `{ answer, citedDocuments }`.

### Why the prompt string needs structure, not just concatenation

A naive version of step 4 is one big `String` built with `+=` in the
controller method: system text, then a loop appending chunk text, then the
question, all interleaved with the retrieval and Groq-calling logic. That
works for a demo, but it mixes three different concerns in one method —
*what* the model is told to do, *what* context it's given, and *what's
asked* — and makes it easy to get the order or the labeling wrong under
pressure (which system instruction wins if you write it twice? did every
chunk actually get its filename label?). Pulling prompt assembly into its
own object with one method per part, each returning the object so calls
chain, makes the *order* of assembly visible and the *whole thing*
independently testable without touching retrieval or Groq at all.

### Grounding: keeping the model from answering outside its context

Retrieval finding the right chunk doesn't guarantee the model *uses* it —
a model asked "what's the equipment stipend?" can still answer from its
own training data instead of the passage you handed it, and that answer
might be wrong for *your* company even if it sounds plausible. The fix is
prompt-level, not code-level: tell the model, explicitly, to answer only
from what it was given. Anthropic's own guidance on this (verified below)
names three techniques this day's system instruction leans on directly:
**external knowledge restriction** ("explicitly instruct Claude to only
use information from provided documents and not its general knowledge"),
**allowing "I don't know"** (giving the model explicit permission to admit
the context doesn't cover the question, rather than guessing), and
**verifying with citations** ("make [the] response auditable by having it
cite quotes and sources for each of its claims"). The `PromptBuilder`'s
system instruction, written in the exercise below, is where all three get
encoded — once, in one place, instead of re-typed per request.

## Reference links

**Spring AI**

- [Spring AI — ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html) —
  the fluent API reference; confirms `.prompt(String)` / `.prompt()`,
  `.system(...)`, `.user(...)`, prompt templates with `{placeholder}`
  substitution via `.param(...)`, and `.call().content()`. Today's
  `PromptBuilder` produces one assembled `String` passed to
  `chatClient.prompt(String)` — the same single-argument call Day 4
  already used — rather than using this page's `.system()`/`.user()`
  builder split; that page is the reference if you want to compare
  approaches.

**Grounding / citation prompt patterns**

- [Anthropic — Reduce hallucinations](https://platform.claude.com/docs/en/test-and-evaluate/strengthen-guardrails/reduce-hallucinations) —
  the official guidance this day's grounding instruction is built from:
  "allow Claude to say 'I don't know'", "external knowledge restriction"
  (only use information from provided documents), and "verify with
  citations" (cite quotes/sources for each claim). *(Note: this page
  previously lived at `docs.anthropic.com/...`; that URL now 301-redirects
  to the `platform.claude.com` address above — use the new one.)*

**Internal (already verified on earlier days)**

- [Day 4 — Groq `ChatClient` + Hugging Face embeddings](04-day4-groq-embeddings-hello-world.md) —
  the exact `chatClient.prompt(String).call().content()` call and the
  Hugging Face `feature-extraction` call reused verbatim in step 2 below.
- [Day 8–10 — chunking + `findNearest` similarity query](08-10-chunking-embedding-pipeline.md) —
  the `Chunk` entity, `ChunkRepository.findNearest`, and the `CAST(...
  AS vector)` requirement reused in step 3 below.
- [Day 2 — Builder pattern definition](02-day2-java-oop-refresher.md) —
  section 6, the definition this day's Java pattern note implements for
  real.

## Java pattern note

**Java pattern note:** `PromptBuilder` is the **Builder** pattern
[previewed on Day 2, section 6](02-day2-java-oop-refresher.md) —
"separate the construction of a complex object from its representation,"
here shown as chainable `withX(...)` methods that each return `this`, with
a terminal `build()` producing the final `String`. It exists because the
prompt has several ordered, sometimes-optional parts (a system
instruction, zero-or-more retrieved chunks, the question) that are easier
to reason about assembled one step at a time than as one inline
concatenation buried inside the endpoint method. It's the same shape as
the JDK's own [`Locale.Builder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Locale.Builder.html)
— chainable setters, terminal `build()` — just building a `String` instead
of a `Locale`.

## Exercise (guided TODO + hints — composing prior building blocks)

Do today's work on a new feature branch, per Day 1's workflow:

```bash
git checkout -b feature/ask-endpoint
```

### 1. Scaffold `POST /ask`

Request and response shapes first:

```java
package com.example.demo;

public record AskRequest(String question) {}
```

```java
package com.example.demo;

import java.util.List;

public record AskResponse(String answer, List<String> citedDocuments) {}
```

```java
package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

    private static final int TOP_K = 5;

    private final ChatClient chatClient;
    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;

    public AskController(ChatClient.Builder chatClientBuilder,
                         ChunkRepository chunkRepository,
                         DocumentRepository documentRepository) {
        this.chatClient = chatClientBuilder.build();
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        // TODOs 2-5 below build this method up, in order.
        throw new UnsupportedOperationException("not implemented");
    }
}
```

*Hint:* `TOP_K` is a hardcoded constant on purpose for now — the Iteration
section below covers pulling it into `application.yml` once you actually
need to tune it without recompiling (the same `@Value` pattern Day 4 used
for `HF_API_KEY`).

### 2. TODO — embed the question

Reuse Day 4's Hugging Face call, unchanged in shape — same endpoint
(`https://router.huggingface.co/hf-inference/models/sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction`),
same `Authorization: Bearer hf_...` header, same Jackson-built request body
and shape-tolerant parsing. `ChunkingPipeline.embed()` from Day 8–10 is the
exact template —
copy its shape into a private `embed(String)` method on `AskController`
(inject `@Value("${HF_API_KEY}")` the same way).

```java
// TODO: float[] queryEmbedding = embed(request.question());
```

*Hint:* yes, this duplicates `ChunkingPipeline.embed()`. That's a real
seam — once both classes have their own copy, the natural next refactor is
extracting a shared `EmbeddingClient` bean both inject. Not required today
(don't over-build it); worth naming if you're asked about it.

### 3. TODO — run the similarity query

`ChunkRepository.findNearest` (Day 8–10) takes the query vector as a
pgvector text literal — `"[0.1,-0.02,...]"` — not a raw `float[]`. You need
a small formatter first:

```java
// Worked — the one new mechanical piece today; the scientific-notation
// trap below isn't obvious until you hit it.
private String toVectorLiteral(float[] embedding) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
        if (i > 0) {
            sb.append(",");
        }
        // %.8f, not embedding[i] directly: Float.toString() switches to
        // scientific notation ("1.0E-4") for small-magnitude values, and
        // pgvector's literal parser doesn't accept that form.
        sb.append(String.format(java.util.Locale.ROOT, "%.8f", embedding[i]));
    }
    return sb.append("]").toString();
}
```

Then:

```java
// TODO: List<Chunk> nearest =
//     chunkRepository.findNearest(toVectorLiteral(queryEmbedding), TOP_K);
```

### 4. TODO — the `PromptBuilder`

First, a small record to carry a retrieved chunk's text alongside the
filename it came from (resolved via `documentRepository.findById(chunk
.getDocumentId())`):

```java
package com.example.demo;

public record ContextChunk(String sourceFilename, String content) {}
```

Now the builder itself. The chainable setters are scaffolding — write
them, they're one line each. `build()` is the actual exercise: assemble
`systemInstruction`, the labeled `context`, and `question` into one prompt
string that instructs the model to answer *only* from the given context
and cite the source filename(s) it used. The exact wording is yours; see
the concept primer's grounding section above for what it has to
accomplish (external-knowledge restriction, permission to say "I don't
know", citation).

```java
package com.example.demo;

import java.util.List;

public class PromptBuilder {

    private String systemInstruction = "";
    private List<ContextChunk> context = List.of();
    private String question = "";

    public PromptBuilder withSystemInstruction(String systemInstruction) {
        // TODO: store it
        return this;
    }

    public PromptBuilder withContext(List<ContextChunk> context) {
        // TODO: store it
        return this;
    }

    public PromptBuilder withQuestion(String question) {
        // TODO: store it
        return this;
    }

    public String build() {
        // TODO: assemble systemInstruction + each ContextChunk (labeled
        // with its sourceFilename, so the model can name it back) +
        // question into one prompt String. Order matters: instruction
        // first, context next, question last (the most recent thing the
        // model reads before it has to answer).
        throw new UnsupportedOperationException("not implemented");
    }
}
```

*Hints:*

- Label every context entry with its filename in a way the model can
  parrot back verbatim, e.g. a `[filename]` prefix before each chunk's
  text — the exact bracket/heading style doesn't matter, consistency does.
- The system instruction needs at minimum: (1) answer only using the
  context below, nothing from general knowledge; (2) if the context
  doesn't contain the answer, say so explicitly rather than guessing; (3)
  name the source filename(s) used.
- `citedDocuments` in the final response does **not** have to come from
  parsing the model's prose. It's more reliable to compute it directly
  from the filenames of the chunks that were actually retrieved and fed
  into the prompt — those are known and deterministic, whereas relying on
  the model to format a machine-parseable citation list is fragile. Still
  instruct the model to cite filenames in its answer text (so a human
  reading `answer` sees the citation inline) — just don't make the
  `citedDocuments` field depend on getting that text-parsing right.

### 5. TODO — call Groq, return the response

```java
// TODO: String prompt = new PromptBuilder()
//     .withSystemInstruction(SYSTEM_INSTRUCTION)
//     .withContext(context)          // List<ContextChunk>, from step 4
//     .withQuestion(request.question())
//     .build();
//
// TODO: String answer = chatClient.prompt(prompt).call().content();
//
// TODO: List<String> citedDocuments = context.stream()
//     .map(ContextChunk::sourceFilename)
//     .distinct()
//     .toList();
//
// TODO: return new AskResponse(answer, citedDocuments);
```

## Iteration

The remaining days are for tuning, not new code. If the checkpoint below
gives you a wrong, empty, or generic answer, work through this checklist
in order — most bad answers trace back to retrieval, not the prompt:

1. **Look at what was actually retrieved before touching the prompt.**
   Temporarily log the `context` list's filenames and chunk text for a
   failing question. If the right chunk isn't in there at all, the
   problem is retrieval (steps 2–4 below), and no amount of prompt
   rewording fixes it — the model was never shown the fact.
2. **Chunk size/overlap (back to Day 8–10).** If the right document *is*
   retrieved but the specific fact is missing or cut off mid-sentence, the
   chunk boundary likely split it. Day 8–10's default is
   `FixedSizeChunkingStrategy(1000, 100)` — try a larger `overlap` first
   (cheap, doesn't change chunk count much); a larger `chunkSize` is the
   next lever, but remember Day 8–10's whole argument for chunking at all
   was avoiding one vector that averages too many topics — don't grow it
   back toward document-sized chunks. Chunk settings only apply to
   documents ingested *after* the change, so after each change empty both
   tables and re-upload the six files (the two commands at the end of Day
   8–10's step 5). Otherwise you're testing the old chunks.
3. **Top-k.** If the right chunk exists in the table (confirm with a
   direct SQL query, same as Day 8–10's checkpoint) but doesn't make the
   top `TOP_K = 5`, raise it. If the model gives a hedgy or wrong answer
   *despite* the right chunk being retrieved, irrelevant chunks in the
   context may be diluting or confusing it — lower `TOP_K` instead.
4. **Prompt wording strictness.** Only after 1–3 check out: if the model
   still answers from outside the context even with the right chunk
   present, the "external knowledge restriction" line is too weak —
   tighten it. If it refuses to answer questions the context *does*
   cover, the instruction may be too strict (e.g. demanding an exact
   phrase match) — loosen it. This is trial and error against real
   questions; there's no formula, only re-running the checkpoint after
   each wording change.
5. **Citation accuracy.** Because `citedDocuments` is computed from the
   retrieved context rather than parsed from the model's own text, it
   will always list every document that was fed in, whether or not the
   model's answer actually drew on all of them. If that's misleading for
   your questions, a stricter version would extract the model's own
   "sources used" statement and cross-check it against the retrieved
   filenames — flagging a mismatch as a sign the instruction from step 4
   above isn't strict enough. Not required for the checkpoint below, worth
   naming as the next step if asked.

## Checkpoint

With all six `docs/curriculum/sample-data/` files uploaded, and therefore
chunked and embedded automatically by Day 8–10's step 5 (including
`vendor-contract-summary-scanned.pdf`, which contributes no retrievable
chunks; its empty extraction means it never appears in any retrieval
result):

```bash
curl -X POST localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the equipment stipend for remote work?"}'
```

returns an `answer` containing `$500` and a `citedDocuments` list
containing `remote-work-policy.txt`.

```bash
curl -X POST localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "How many requests per minute is the API rate limit?"}'
```

returns an `answer` containing `60` and a `citedDocuments` list containing
`api-changelog.txt`.

These are two distinct known-answer questions against two distinct,
topically non-overlapping documents (per the sample dataset's design —
see `docs/curriculum/sample-data/README.md`). Passing both, with each
citing the *correct* document rather than whichever one happens to be
first, confirms retrieval is actually discriminating between sources —
not just always returning the same chunks regardless of the question.

## Interview angle

*"How do you keep an LLM from hallucinating facts not in your data?"*

The direct answer is the grounding/citation prompt pattern built today:
retrieval alone doesn't guarantee the model uses what it's given, so the
prompt has to explicitly restrict the model to the provided context,
explicitly permit it to say "I don't have enough information" instead of
guessing, and explicitly require it to cite which source(s) it used —
[Anthropic's own guidance](https://platform.claude.com/docs/en/test-and-evaluate/strengthen-guardrails/reduce-hallucinations)
names exactly these three techniques. The stronger answer names *where*
that logic lives: encoded once in the `PromptBuilder`'s system
instruction rather than re-typed per request, so every `/ask` call gets
the same grounding rules — and structural, not just textual: `Chunk`
storing `documentId` back to its source `Document` (Day 8–10) is what
makes an *independently verifiable* `citedDocuments` field possible at
all, one that doesn't have to trust the model got its own citation
formatting right.

## Answer Key (attempt it yourself first!)

Only read this after the checkpoint's two curl commands pass. The
`AskRequest`/`AskResponse` records, `ContextChunk` record, and
`toVectorLiteral` formatter are **already shown in full above** — they
are not repeated here. This key adds the two pieces that were TODO: the
complete `PromptBuilder` (with example prompt wording) and the complete
`AskController.ask` method.

**`PromptBuilder.java`**

```java
package com.example.demo;

import java.util.List;

public class PromptBuilder {

    private String systemInstruction = "";
    private List<ContextChunk> context = List.of();
    private String question = "";

    public PromptBuilder withSystemInstruction(String systemInstruction) {
        this.systemInstruction = systemInstruction;
        return this;
    }

    public PromptBuilder withContext(List<ContextChunk> context) {
        this.context = context;
        return this;
    }

    public PromptBuilder withQuestion(String question) {
        this.question = question;
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append(systemInstruction).append("\n\n");
        sb.append("Context:\n");
        for (ContextChunk chunk : context) {
            sb.append("[").append(chunk.sourceFilename()).append("]\n");
            sb.append(chunk.content()).append("\n\n");
        }
        sb.append("Question: ").append(question);
        return sb.toString();
    }
}
```

Example system instruction, encoding the three grounding techniques from
the concept primer (this is the wording the exercise left up to you —
yours doesn't have to match, it has to accomplish the same three things):

```java
private static final String SYSTEM_INSTRUCTION = """
        You are an internal Q&A assistant. Answer the question using ONLY
        the context below. Each context entry is labeled with its source
        document's filename in square brackets.

        Rules:
        - Do not use any knowledge beyond what appears in the context.
        - If the context does not contain the answer, respond exactly:
          "I don't have enough information to answer that." Do not guess.
        - After your answer, name the filename(s) of the context entries
          you actually used.
        """;
```

**`AskController.java`** (complete)

```java
package com.example.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class AskController {

    private static final int TOP_K = 5;

    private static final String SYSTEM_INSTRUCTION = """
            You are an internal Q&A assistant. Answer the question using ONLY
            the context below. Each context entry is labeled with its source
            document's filename in square brackets.

            Rules:
            - Do not use any knowledge beyond what appears in the context.
            - If the context does not contain the answer, respond exactly:
              "I don't have enough information to answer that." Do not guess.
            - After your answer, name the filename(s) of the context entries
              you actually used.
            """;

    private final ChatClient chatClient;
    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final String hfApiKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AskController(ChatClient.Builder chatClientBuilder,
                         ChunkRepository chunkRepository,
                         DocumentRepository documentRepository,
                         @Value("${HF_API_KEY}") String hfApiKey) {
        this.chatClient = chatClientBuilder.build();
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.hfApiKey = hfApiKey;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        float[] queryEmbedding = embed(request.question());

        List<Chunk> nearest = chunkRepository.findNearest(
                toVectorLiteral(queryEmbedding), TOP_K);

        List<ContextChunk> context = nearest.stream()
                .map(chunk -> new ContextChunk(filenameFor(chunk), chunk.getContent()))
                .toList();

        String prompt = new PromptBuilder()
                .withSystemInstruction(SYSTEM_INSTRUCTION)
                .withContext(context)
                .withQuestion(request.question())
                .build();

        String answer = chatClient.prompt(prompt).call().content();

        List<String> citedDocuments = context.stream()
                .map(ContextChunk::sourceFilename)
                .distinct()
                .toList();

        return new AskResponse(answer, citedDocuments);
    }

    private String filenameFor(Chunk chunk) {
        return documentRepository.findById(chunk.getDocumentId())
                .map(Document::getFilename)
                .orElse("unknown");
    }

    // pgvector text literal: "[v1,v2,...]". %.8f avoids Float.toString()
    // falling back to scientific notation ("1.0E-4"), which the vector
    // literal parser rejects.
    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format(Locale.ROOT, "%.8f", embedding[i]));
        }
        return sb.append("]").toString();
    }

    // Day 8–10's ChunkingPipeline.embed(): Jackson-built request body, status
    // check, and both response shapes ([...] and [[...]]) accepted.
    private float[] embed(String text) {
        try {
            String url = "https://router.huggingface.co/hf-inference/models/"
                    + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + hfApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("inputs", text))))
                    .build();

            HttpResponse<String> response = http.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("embedding call failed: HTTP "
                        + response.statusCode() + " " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vector = root.get(0).isArray() ? root.get(0) : root;
            float[] embedding = new float[vector.size()];
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) vector.get(i).asDouble();
            }
            return embedding;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("embedding call failed", e);
        }
    }
}
```

Commit on the feature branch with a Conventional Commits message (e.g.
`feat: add /ask RAG endpoint with prompt builder`), push, and merge via a
pull request per Day 1.
