# Day 19 — Java/OOP + Design-Patterns Retrospective, and One More Pattern

**Focus:** A deliberate pause. For eighteen days you have been using design
patterns whose names you met on [Day 2](02-day2-java-oop-refresher.md).
Today you (1) go back and connect each one to the exact place it showed up
in your own code, and (2) apply one more pattern for the first time:
putting your embeddings provider behind an `EmbeddingProvider` interface,
so that switching between Hugging Face and Jina AI is a configuration
change instead of a code edit. No new product features. The `/ask`
endpoint's behavior must be identical when you are done.

**Pacing (about 6 hours):**

- Retrospective worksheet (no code): ~1 hour.
- Reading the two new patterns and the links: ~1 hour.
- Refactor exercise: ~3 hours. Most of it goes to the call-site hunt and to
  re-ingesting your documents after a provider switch.
- Checkpoint and interview-answer write-up: ~1 hour.

## Concept primer

### Why a retrospective at all

A design pattern is a name for a shape you have already seen. Day 2
defined four of them in the abstract; since then each has shown up in
code you wrote, under a different disguise. Naming them matters for two
practical reasons: in a code review "this is a Strategy" replaces a
paragraph of explanation, and in an interview the question is almost never
"define the Decorator pattern" but "where have you used it?" The worksheet
below makes you produce that answer from your own code, with one sentence
about what the *naive alternative* would have cost. The vocabulary below is
the Day 2 vocabulary, unchanged:

| Pattern | Day 2 definition (short) | Where it appeared | Naive alternative it replaced |
|---|---|---|---|
| **Dependency Injection / Inversion of Control** | A class declares its collaborators as constructor arguments; something outside creates and supplies them. | [Day 3](03-day3-crud-docker.md#java-pattern-note): the repository injected into the controller. | `new`-ing the collaborator inside the class, or reaching for a global. |
| **Strategy** | A family of interchangeable algorithms behind one interface; the client holds the interface, and which implementation it got is decided elsewhere. | [Day 8–10](08-10-chunking-embedding-pipeline.md#java-pattern-note): `ChunkingStrategy`. | An `if/else` on a chunking-mode flag inside the pipeline. |
| **Builder** | Construct a complex object step by step, with a terminal `build()`. | [Day 11–14](11-14-ask-endpoint-rag-pipeline.md#java-pattern-note): `PromptBuilder`. | One long string concatenation inside the endpoint method. |
| **Decorator** | A wrapper that adds behavior around a call while keeping the caller's contract. | [Day 15](15-rate-limit-resilience.md#java-pattern-note): the retry around the embeddings call. | A hand-written retry loop pasted into every caller. |

### Two patterns that are new today: Factory Method and Adapter

Both come from the same book as the other four: *Design Patterns* by
Gamma, Helm, Johnson, and Vlissides (the "Gang of Four"; the
[publisher's page](https://www.informit.com/store/design-patterns-elements-of-reusable-object-oriented-9780201633610)).

**Factory Method** (creational). Refactoring.guru's summary: Factory Method
["provides an interface for creating objects in a superclass"](https://refactoring.guru/design-patterns/factory-method)
and "allows subclasses to alter the type of objects that will be created."
The problem it addresses is the one you are about to have: "Adding a new
class to the program isn't that simple if the rest of the code is already
coupled to existing classes." The Gang of Four's one-line intent, as
[quoted on Wikipedia](https://en.wikipedia.org/wiki/Factory_method_pattern),
is "Define an interface for creating an object, but let subclasses decide
which class to instantiate."

*A precision worth having ready for an interview:* strictly, the Gang of
Four's Factory Method is the **subclass** version of that idea. What you will
write today is one method whose return type is the interface and whose body
picks the concrete class from a configuration value. Refactoring.guru's
[comparison page](https://refactoring.guru/design-patterns/factory-comparison)
calls that shape a **Simple Factory**: "a class that has one creation method
with a large conditional." Day 2 already showed you the everyday sense of
the term from Spring's own documentation: DI means dependencies arrive
through "constructor arguments, arguments to a factory method, or
properties," and a `@Bean` method in a `@Configuration` class is exactly
that, a method Spring calls to obtain an object, which may have an interface
as its declared return type
([Spring: `@Bean`](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html)).
So the honest description of today's code is: *a factory method, in
Spring's sense, that is technically a Simple Factory in the Gang of Four's
taxonomy.* The point of the exercise is the same either way: the rest of the
code stops knowing which concrete class it is talking to.

**Adapter** (structural). Refactoring.guru:
["Adapter is a structural design pattern that allows objects with incompatible interfaces to collaborate."](https://refactoring.guru/design-patterns/adapter)
Wikipedia's [overview](https://en.wikipedia.org/wiki/Adapter_pattern) gives
the mechanism: it "converts the (incompatible) interface of a class
(adaptee) into another interface (target) clients require." Here the
"incompatible interfaces" are the two vendors' HTTP APIs: Hugging Face wants
`{"inputs": "..."}` and answers with a bare JSON array of numbers; Jina
wants `{"model": ..., "input": [...]}` and answers with
`{"data": [{"embedding": [...]}]}`. Your code wants one thing:
`float[] embed(String text)`. Each provider class you write is an Adapter
from one vendor's API to that one method.

**How the patterns fit together in today's refactor.** One interface, four
different pattern vocabularies, each answering a different question:

| Piece | Pattern | The question it answers |
|---|---|---|
| `EmbeddingProvider` (the interface) and the code that depends on it | **Strategy** (Day 8–10 vocabulary) | "Which algorithm do I use?" — the callers don't know. |
| `HuggingFaceEmbeddingProvider`, `JinaEmbeddingProvider` | **Adapter** | "How do I make this vendor's API look like our interface?" |
| `EmbeddingProviderConfig.embeddingProvider(...)` | **Factory Method** (Simple Factory) | "Who decides which implementation exists at runtime?" |
| `@Retryable` on each `embed()` | **Decorator** (Day 15, via Spring's proxy) | "How do we add retry without the caller noticing?" |

Day 2's mechanical test still applies to tell Strategy from Decorator: a
class that implements `EmbeddingProvider` *and holds another
`EmbeddingProvider` it delegates to* is a decorator; the two provider
classes hold no such reference, so they are strategy implementations.

**Java pattern note:** today is the first time you apply a pattern
*proactively*, before a requirement forces it. Days 8–15 each introduced a
pattern at the moment the code needed it. Here the trigger is a change you
have already lived through, and that is the honest motivation: when you
swapped Hugging Face for Jina AI you edited the body of `embed()` in
`ChunkingPipeline` in place. That worked, and it also had three costs: the
Hugging Face code is gone (you cannot flip back without digging through git
history), the class that does chunking and saving also owns a vendor's wire
format, and any other place that called the old provider (see the hunt in
step 1) had to be found and edited by hand. The interface turns that same
swap into a one-line configuration change and keeps both providers working
side by side.

### Same dimension is not enough: switching providers means re-embedding

This is the one fact today that is easy to get wrong, and the database will
not catch it for you.

Your `chunk.embedding` column is declared `vector(384)`. A pgvector column
declared with a number of dimensions fixes the **length** of every vector
stored in it (the [pgvector README](https://github.com/pgvector/pgvector)
shows the syntax: `embedding vector(3)` for a 3-dimensional column). It
cannot know what the numbers *mean*. Both providers in this exercise were
configured to return 384 floats (Hugging Face's `all-MiniLM-L6-v2` natively,
and Jina's `jina-embeddings-v3` with the request parameter `dimensions: 384`), so a
Jina vector slots into the same column as a Hugging Face vector without any
error, and the cosine-distance query (`<=>`) happily computes a distance
between them. That distance is meaningless: each embedding model learns its
own coordinate system, so dimension 17 of one model has no relationship to
dimension 17 of another.

You can watch the column's behavior yourself in any `psql` session against
your `chunk` table (adjust the column list to your entity; this only needs
the `embedding` column to be fillable). Two 384-wide vectors that have nothing
to do with each other are accepted and compared without complaint, while a
100-wide one is rejected:

```sql
-- two unrelated 384-dimensional vectors: both accepted, and <=> returns a number
INSERT INTO chunk (document_id, content, embedding) VALUES
  (1, 'from model A', (SELECT array_agg(random())::real[]::vector FROM generate_series(1,384))),
  (1, 'from model B', (SELECT array_agg(random() - 0.5)::real[]::vector FROM generate_series(1,384)));

-- a 100-dimensional vector: rejected
INSERT INTO chunk (document_id, content, embedding)
  VALUES (1, 'wrong length', (SELECT array_agg(random())::real[]::vector FROM generate_series(1,100)));
-- ERROR:  expected 384 dimensions, not 100
```

(Run `TRUNCATE chunk;` afterwards; these are throwaway rows.)

The providers say the same thing in their own documentation:

- Jina's embeddings page, in its FAQ, says
  ["embeddings from different model families are not comparable, so you have to re-embed your corpus"](https://jina.ai/embeddings/)
  rather than mixing vectors from two providers in one index.
- LlamaIndex's embeddings guide says
  ["if you change your embedding model after indexing, you must re-index"](https://developers.llamaindex.ai/python/framework/module_guides/models/embeddings/)
  and "Embeddings from different models are not interchangeable."

The practical consequences, which the checkpoint makes you live through:

1. **After you switch `embeddings.provider`, every stored chunk must be
   re-embedded** with the new provider. Questions are embedded with the new
   provider at `/ask` time, so a half-migrated table (old chunks from
   provider A, new question from provider B) returns confidently ranked,
   essentially arbitrary results. It will not throw an error. That is what
   makes it dangerous.
2. **Never mix providers within one table.** If you want to keep two
   providers' vectors side by side you need separate columns or tables, plus
   a record of which model produced each vector. Today's version avoids that
   by re-ingesting. Storing the model id on each `Chunk` row is a worthwhile
   stretch and a good interview talking point, not part of the required work.
3. **The interface can enforce the part the database can't see:** the
   length. Each provider in the answer key checks that the vector it got
   back has exactly the dimensions it promised, and fails loudly instead of
   letting a wrong-sized vector reach `vector(384)`.

### Where Day 15's retry lives after the refactor

[Day 15](15-rate-limit-resilience.md) put `@Retryable` on
`EmbeddingClient.embed()` and explained why `EmbeddingClient` had to be its
own bean: Spring's retry works through a **proxy**, and "a proxy only
intercepts calls that come from *outside* the bean." Spring's AOP reference
says the same for all proxy-based advice:
["self invocation via an explicit or implicit `this` reference will bypass the advice."](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

After today's refactor the annotation **moves with the HTTP call**: it sits
on `embed()` in each provider class (`HuggingFaceEmbeddingProvider`,
`JinaEmbeddingProvider`). The reasoning:

- The retry policy depends on how a *vendor* signals "try again" (HTTP `429`
  or a `5xx`, per Day 15), which is HTTP-response knowledge that belongs
  next to the HTTP code, not in `ChunkingPipeline` or `AskController`.
- The callers hold the interface, and the object Spring injects into them is
  the proxied provider bean, so every call from the pipeline or the
  controller goes through the proxy. They get retry without knowing it.
- The one new way to lose retry silently: the factory must hand the provider
  to **Spring** as a bean. Today's factory is a `@Bean` method, so Spring
  receives the object and wraps it. If you instead wrote a plain class with
  `new HuggingFaceEmbeddingProvider(...)` and called it yourself, the object
  would never be a bean, never be proxied, and `@Retryable` would do nothing
  without any error. The checkpoint includes a way to see this for yourself.

Reference for the annotation itself: the
[Spring Framework resilience reference](https://docs.spring.io/spring-framework/reference/core/resilience.html)
(Day 15's source) — `@EnableResilientMethods` is what turns on the proxies.

## Reference links

**New patterns (introduced today)**

- [Factory Method — Refactoring.guru](https://refactoring.guru/design-patterns/factory-method) —
  intent, the "coupled to existing classes" problem.
- [Factory Method pattern — Wikipedia](https://en.wikipedia.org/wiki/Factory_method_pattern) —
  the Gang of Four intent sentence.
- [Factory comparison — Refactoring.guru](https://refactoring.guru/design-patterns/factory-comparison) —
  Simple Factory vs. Factory Method vs. Abstract Factory; where a
  one-method conditional factory sits.
- [Adapter — Refactoring.guru](https://refactoring.guru/design-patterns/adapter) —
  incompatible interfaces collaborating; the third-party-library example.
- [Adapter pattern — Wikipedia](https://en.wikipedia.org/wiki/Adapter_pattern) —
  adaptee / target / client vocabulary.
- [*Design Patterns: Elements of Reusable Object-Oriented Software*](https://www.informit.com/store/design-patterns-elements-of-reusable-object-oriented-9780201633610) —
  Gamma, Helm, Johnson & Vlissides; the canonical source (publisher's page).

**Spring**

- [Java-based configuration: `@Bean`](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html) —
  a `@Bean` method declares a bean of its return type; an interface is a
  valid return type; also the note on declared types and type prediction.
- [Proxying mechanisms (AOP)](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html) —
  the self-invocation rule that decides where `@Retryable` can live.
- [Resilience (`@Retryable`, `@EnableResilientMethods`)](https://docs.spring.io/spring-framework/reference/core/resilience.html) —
  Day 15's source for the retry annotation.
- [Spring Boot: property conditions](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html) and
  [`@ConditionalOnProperty` Javadoc](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/condition/ConditionalOnProperty.html) —
  the alternative selection mechanism described in the exercise
  (`havingValue`, `matchIfMissing`).

**Embeddings providers and vectors**

- [Jina AI — Embedding API](https://jina.ai/embeddings/) — the provider's
  page: the `https://api.jina.ai/v1/embeddings` endpoint with Bearer auth,
  the `task` parameter and its `text-matching` / `retrieval.query` /
  `retrieval.passage` options, and the FAQ line about re-embedding.
- [Jina AI — OpenAPI specification](https://api.jina.ai/openapi.json) — the
  machine-readable API definition; its `EmbeddingsV3Request` schema lists
  `model`, `input`, `task`, and `dimensions` (range 1–1024), and
  `EmbeddingResponse` / `SingleEmbeddingData` define the response as `data[]`
  items with `index` and `embedding`. The `429` and `5xx` error rows in the
  spec's error table are what the transient-vs-non-transient split below
  relies on.
- [LlamaIndex — Embeddings](https://developers.llamaindex.ai/python/framework/module_guides/models/embeddings/) —
  "if you change your embedding model after indexing, you must re-index."
- [pgvector README](https://github.com/pgvector/pgvector) — `vector(n)`
  columns, and the `<=>` cosine-distance operator.
- Hugging Face request/response details: no new source today. The
  endpoint URL and request shape are exactly what
  [Day 4](04-day4-groq-embeddings-hello-world.md) used and
  [Day 15](15-rate-limit-resilience.md) kept, and were verified there.

**Internal (the four patterns being reviewed)**

- [Day 2 — Java/OOP refresher](02-day2-java-oop-refresher.md): section 6
  (Strategy, Builder, Decorator definitions), section 7 (DI/IoC).
- [Day 3 — CRUD & Docker](03-day3-crud-docker.md#java-pattern-note): DI/IoC.
- [Day 8–10 — Chunking & embedding pipeline](08-10-chunking-embedding-pipeline.md#java-pattern-note): Strategy.
- [Day 11–14 — `/ask` endpoint](11-14-ask-endpoint-rag-pipeline.md#java-pattern-note): Builder.
- [Day 15 — Rate-limit resilience](15-rate-limit-resilience.md#java-pattern-note): Decorator.

## Exercise

Two parts, in order: a no-code worksheet (reflection), then a guided refactor
(familiar Spring territory, so guided TODOs and hints, with the full code in the
answer key at the bottom).

### Part 1 — Retrospective worksheet (no code)

Open a scratch file (not part of the app; `notes/day19-retrospective.md` or
anywhere you like) and write **one sentence per cell** in the two right-hand
columns. "Where" means a concrete file, class, or method in *your* repo, not
the curriculum's. "Why" means the specific thing the naive alternative would
have cost you. The hint column is a prompt, not the answer.

| Pattern | Where it is in your code | Why this shape over the naive alternative | Hint |
|---|---|---|---|
| **DI / IoC** (Day 3) | | | Look at `DocumentController`'s constructor. `DocumentControllerTest` uses `@MockitoBean` for the repository, the Tika bean and `ChunkingPipeline`. What would that test need to do if the controller created its own `ChunkingPipeline` with `new`? |
| **Strategy** (Day 8–10) | | | Why a `ChunkingStrategy` interface instead of an `if/else` on a chunking-mode flag? Count the places you would edit to add a sentence-aware chunker in each design. |
| **Builder** (Day 11–14) | | | `PromptBuilder` has three `withX(...)` steps. What goes wrong with a constructor taking `(String, List<ContextChunk>, String)`, where two parameters have the same type and swapping them still compiles? What does the `build()` step get to validate? |
| **Decorator** (Day 15) | | | Retry is added without `AskController` or `ChunkingPipeline` containing a loop. Where does the loop (or the proxy) live? Apply Day 2's mechanical test: does the wrapper implement the same interface as the thing it wraps? Be honest about how well your code meets it. |

Then answer these three in a few sentences each. They are the part
interviewers actually probe:

1. **Which of the four did you not need?** Pick one and argue that the
   project would have been fine, or better, without it. (There is no wrong
   pick; "no, all four earned their place, because..." is also an answer if
   you can say what each saved.)
2. **Which one bit you?** Which pattern's *implementation* caused a real
   bug or confusing moment (the `CAST` trap, the proxy not applying, a
   constructor wiring error)? What was the underlying rule?
3. **Spot the near-miss.** Strategy and Decorator are both "an interface
   plus implementations." Using Day 2's mechanical test, say which of your
   classes is which, and name one class in your project that you were tempted
   to call a pattern but is really just a class.

Keep the notes. the interview write-up below reuses them.

### Part 2 — Refactor: put embeddings behind an interface (guided TODO + hints)

Work on a feature branch, per Day 1's workflow:

```bash
git checkout -b refactor/embedding-provider-interface
```

**What you are building.** Today's end state:

```
ChunkingPipeline ─┐
AskController   ──┼──▶  EmbeddingProvider  ◀── HuggingFaceEmbeddingProvider
HelloAiController ┘     (interface)         ◀── JinaEmbeddingProvider
                              ▲
                  EmbeddingProviderConfig picks one
                  from the property embeddings.provider
```

Class names are prescribed (not suggested) because the checkpoint greps for
them: `EmbeddingProvider`, `HuggingFaceEmbeddingProvider`,
`JinaEmbeddingProvider`, `EmbeddingProviderConfig`.

#### 1. Find every place your code calls an embeddings API

Your code and the curriculum's code differ here, so do not trust a
description of where the calls are; search. From the project root:

```bash
grep -rnE "huggingface\.co|api\.jina\.ai|HF_API_KEY|JINA_API_KEY|\bembed\(" src/main
```

Write down every hit that is *a call* (or the code that makes one), not
just a mention. Depending on how far your code has drifted from the
curriculum you will find some mix of these:

| Where | What it does |
|---|---|
| `ChunkingPipeline.embed(...)` | Embeds each chunk during ingestion (Day 8–10). In the curriculum this method is private; if you extracted `EmbeddingClient` on Day 15 it may delegate there instead. |
| `AskController` | Embeds the question. In the curriculum this goes through `EmbeddingClient` (Day 15). In your own code it may call `chunkingPipeline.embed(request.question())` directly, which makes the controller depend on the *ingestion* class just to reach an embeddings call. That coupling is one of the things this refactor removes. |
| `HelloAiController.helloEmbedding()` | The Day 4 scaffold still contains a complete, separate copy of the Hugging Face call, and its constructor requires `HF_API_KEY` at startup. If you moved to Jina and only edited `ChunkingPipeline`, this copy is still calling Hugging Face. |

If the grep finds a call site that is not in this table, it goes behind the
interface too. **The goal is that exactly two classes contain vendor URLs and
vendor JSON shapes when you are done: the two provider classes.**

#### 2. TODO — define the interface

Create `EmbeddingProvider` with three methods:

- `float[] embed(String text)`: one piece of text in, one vector out.
- `int dimensions()`: the length of every vector this provider returns.
- `String modelId()`: a human-readable `provider:model` string, for logs.

*Hints:*

- Keep it this small. Everything provider-specific (URL, key, JSON shape,
  batching) belongs in the implementations, and the interface should only
  say what callers need. The easiest way to ruin an adapter boundary is to
  let a vendor concept leak into the interface (a `task` parameter, a
  `Map<String, Object> options`).
- `embed` does **not** declare `throws Exception`. Day 15's two unchecked
  exceptions, `TransientEmbeddingException` and
  `NonTransientEmbeddingException`, are how failures cross the boundary.
  If your app does not have them yet (your own retry may catch plain
  `Exception`), the answer key lists them: they are two tiny classes.
- `dimensions()` exists so something can check the vector against the
  `vector(384)` column. See the primer: the database checks only length,
  and only at insert time.

#### 3. TODO — the first implementation: your *current* provider

Whichever provider your code uses **today** becomes implementation #1.
If you are still on Hugging Face (the curriculum's path), that is
`HuggingFaceEmbeddingProvider`. If you moved to Jina AI, your existing
`embed()` is `JinaEmbeddingProvider`. Do this one first, then write the
other, so the refactor itself is a pure move: behavior must not change
before you have a second provider in play.

For either provider the class must:

- implement `EmbeddingProvider`;
- take its API key and endpoint URL through its **constructor** (it is not a
  `@Component`; the factory in step 5 builds it);
- carry the Day 15 `@Retryable` on `embed(...)`, retrying only
  `TransientEmbeddingException`, with Day 15's numbers
  (`maxRetries = 4, delay = 500, multiplier = 2, maxDelay = 4000`);
- map failures the Day 15 way: `429` or `5xx` or a network failure is
  `TransientEmbeddingException`; any other non-`200` is
  `NonTransientEmbeddingException`;
- check that the vector it parsed has exactly 384 elements, and throw
  `NonTransientEmbeddingException` if not.

#### 4. TODO — the second implementation (a worked example's worth of detail)

Write the other provider now. The wire formats are the only new
information, so here they are side by side. Both are the real shapes used
by the code you already have:

| | Hugging Face | Jina AI |
|---|---|---|
| URL | `https://router.huggingface.co/hf-inference/models/sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction` | `https://api.jina.ai/v1/embeddings` |
| Auth header | `Authorization: Bearer <HF_API_KEY>` | `Authorization: Bearer <JINA_API_KEY>` |
| Request body | `{"inputs": "<text>"}` | `{"model": "jina-embeddings-v3", "task": "text-matching", "dimensions": 384, "input": ["<text>"]}` |
| Response body | a JSON array: `[0.01, ...]`, or nested `[[0.01, ...]]`; accept both | `{"data": [{"index": 0, "embedding": [0.01, ...]}], ...}`; take `data[0].embedding` |
| Vector size | 384 (fixed by the model) | `dimensions: 384` asks for 384; Jina's OpenAPI spec allows any value from 1 to 1024 for this field |

*Hints:*

- Jina's `task` field selects a task-specific mode of `jina-embeddings-v3`.
  Per Jina's API definition the choices are `retrieval.query`,
  `retrieval.passage`, `text-matching`, `classification` and `separation`.
  Use `text-matching` (what your existing code sends) so that this
  refactor changes nothing about the vectors you already get. See the
  variations at the end for what the retrieval-specific tasks would mean for
  the interface.
- The two classes will look very alike: the same `429` / `5xx` check, the
  same JSON-array-to-`float[]` loop. That duplication is acceptable for
  today; pulling it into a shared helper is an optional cleanup (do it
  *after* the checkpoint passes).
- If you only ever want one provider *in production*, you still write both.
  The second implementation is what proves the interface boundary is real.

#### 5. TODO — selection: a factory method

You need exactly one `EmbeddingProvider` bean, chosen from configuration.
Two mechanisms are both valid, and **the point is the interface boundary,
not which selection mechanism you pick**:

- **(a) A factory method.** One `@Bean` method in a `@Configuration` class
  reads `embeddings.provider`, constructs the matching class with its key and
  URL, and returns it typed as `EmbeddingProvider`. This is what the answer
  key uses. It is the "factory method" of the primer, and it makes the
  class-name grep in the checkpoint trivial to read, because both concrete
  names appear in exactly one config file.
- **(b) Spring's own bean selection.** Annotate each provider class
  `@Component` plus
  `@ConditionalOnProperty(name = "embeddings.provider", havingValue = "jina")`
  (and `"huggingface"` with `matchIfMissing = true` on the default one), so
  Spring registers only the matching class
  ([Boot's property conditions](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html);
  `havingValue` is "the string representation of the expected value," and
  `matchIfMissing` says whether the condition matches when the property is
  not set, per the
  [Javadoc](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/condition/ConditionalOnProperty.html)).
  `@Qualifier` with a name, or `@Profile`, are variations on the same idea.
  The providers then need their keys through `@Value` constructor
  parameters, and they are real components, so Spring wraps `@Retryable`
  without extra work.

Trade-off to be able to state: (a) puts all selection logic and the fail-fast
check in one readable place, and an unknown value fails at startup with a
message you wrote; (b) is less code but spreads the decision across
annotations on two classes, and the default behavior when the property is
missing or misspelled is easy to get wrong (with no `matchIfMissing`, *no*
bean matches and startup fails with a generic "no qualifying bean"
message).

*Hints for (a):*

- Read only the selected provider's key. A learner who uses Jina only
  should not have to set `HF_API_KEY` to boot. Use `@Value("${HF_API_KEY:}")`
  with an empty default, and throw `IllegalStateException` with a clear
  message in the branch that needs a blank key.
- Make the default provider (`embeddings.provider` unset) whichever one you
  consider the project's default; the answer key picks `huggingface`.
  Expose the choice in `application.properties` as
  `embeddings.provider=${EMBEDDINGS_PROVIDER:huggingface}`. That form works
  with a real environment variable *and* with your `.env` file (your
  `spring.config.import` line reads `.env` as a properties file, so a plain
  `${...}` placeholder is the reliable way to reference it).
- Log one line at startup naming the chosen provider (`modelId()`), so the
  checkpoint can show which one was wired.
- Optional but useful: let the two URLs be overridden by properties
  (`embeddings.huggingface.url`, `embeddings.jina.url`) with the real URLs as
  defaults. That is how you point either provider at a local fake server in
  a test, and it costs two `@Value` parameters.

#### 6. TODO — move the call sites behind the interface

For every call site from step 1:

- `ChunkingPipeline`: delete its own `embed(...)` and any
  `@Retryable`/`@Recover`/HTTP/JSON code and fields that only served it
  (key, `HttpClient`, `ObjectMapper`); take an `EmbeddingProvider` in the
  constructor and call it in `ingest(...)`.
- `AskController`: replace the field/constructor parameter that reached
  the embeddings call (`EmbeddingClient`, or `ChunkingPipeline` in your own
  code) with an `EmbeddingProvider`. The controller should no longer need
  `ChunkingPipeline` at all unless it uses it for something else.
- `HelloAiController`: inject `EmbeddingProvider`, and make
  `/hello-embedding` return `embeddingProvider.embed("hello world")`. This
  also removes its startup dependency on `HF_API_KEY`.
- If you extracted `EmbeddingClient` on Day 15, it is now
  `HuggingFaceEmbeddingProvider`; delete the old class once nothing
  references it.
- If `AskController.ask(...)` itself carries a `@Retryable`/`@Recover` pair
  (a shape from trying Day 15 on your own), take it off now that the provider
  owns the retry. Checked on a copy of such a controller: the controller-level
  annotation retries *every* exception, including a provider `400` that
  Day 15 says must not be retried, so one bad request hit the provider
  three times and then came back as a `200` carrying the fallback text; and
  with a provider that is genuinely down, its five attempts get multiplied
  by the controller's three. Day 15's interview angle makes the same point
  about stacked retry loops.

*About `@Retryable` and `@Recover`:* the answer key uses Day 15's annotation
(`org.springframework.resilience.annotation.Retryable`, enabled by
`@EnableResilientMethods`). Your own app may still be on the older
`org.springframework.retry.annotation.Retryable` with `@EnableRetry` and a
`@Recover` method (the Day 15 primer explains why Spring Retry is archived
and what replaced it). For this refactor the proxy rule is identical in both:
the annotation goes on `embed(...)` of the provider *bean*. Keep whichever
one you have working, with one difference to remember: Spring Retry's
`@Recover` method must live in the same class as the `@Retryable` method, so
if you keep it, a copy goes into each provider that carries the annotation.

#### 7. Run it, then re-ingest

Start the app with each setting in turn (`EMBEDDINGS_PROVIDER=huggingface`,
then `EMBEDDINGS_PROVIDER=jina`, set in `.env` or your shell; if you start the
app through Docker Compose, add `EMBEDDINGS_PROVIDER` and both keys to the
`app` service's `environment:` block the same way `HF_API_KEY` already is).
Both startups must log the one-line provider message.

Then do the part the primer warned about. The chunks already in your
database were embedded by *one* provider; after switching, the question
embedding comes from *another*. Remove the stale vectors and ingest again:

```bash
# in your Postgres container (adjust the service/user/db names to your compose file)
docker compose exec db psql -U <user> -d <db> -c "TRUNCATE chunk;"
```

then upload the six sample files again (the `docs/curriculum/sample-data/`
files, same `curl -F file=@...` calls you used on Day 8–10). You can see
the failure mode first if you want to: switch the provider *without*
truncating, ask the equipment-stipend question from Day 11–14, and see what
comes back. It will not error; it will just be wrong or arbitrary.

### Part 3 — Variations (optional, after the checkpoint passes)

1. *Asymmetric tasks.* Jina's `jina-embeddings-v3` has separate
   `retrieval.query` and `retrieval.passage` tasks (documented on
   [Jina's embeddings page](https://jina.ai/embeddings/)). Using them would mean
   the interface knows whether it is embedding a document or a question, for
   example `embedDocument(String)` and `embedQuery(String)` (Hugging Face's
   `all-MiniLM-L6-v2` would implement both the same way). Sketch which call
   sites map to which, and note that changing the task changes the vectors, so
   it forces a re-ingest too.
2. *Store the model.* Add an `embedding_model` column to `Chunk` and fill it
   from `modelId()`. Make `/ask` refuse (or log loudly) if the active
   provider's `modelId()` differs from what the retrieved chunks were embedded
   with.
3. *A third adapter.* Write `FakeEmbeddingProvider`, a deterministic
   offline one (for example, hash each word into a 384-slot vector). Its
   vectors carry no real meaning, so it is for plumbing tests only. Adding it
   should touch exactly two places: the new class and the factory's `switch`. If
   it touches `ChunkingPipeline` or `AskController`, the boundary leaked.
4. *Retry as an explicit decorator.* Move `@Retryable` out of the providers
   into a `RetryingEmbeddingProvider implements EmbeddingProvider` that wraps
   another one, and have the factory return the wrapper. Apply Day 2's test:
   it implements the interface *and* holds one, so it is a decorator. Now both
   providers get the same retry without repeating the annotation.

## Checkpoint

You're done with Day 19 when all four of these pass.

1. **The boundary grep is clean.** From the project root:

   ```bash
   grep -rnE "HuggingFaceEmbeddingProvider|JinaEmbeddingProvider" src/main \
     | grep -vE "/(HuggingFaceEmbeddingProvider|JinaEmbeddingProvider|EmbeddingProviderConfig)\.java:"
   ```

   **Expected: no output at all** (and exit status `1`, which is `grep`
   saying "nothing matched"). The two concrete class names may appear only
   in their own source files and in the factory/config class. If a line is
   printed, it is a file that still knows a concrete provider. Typical
   culprits: a leftover import in `ChunkingPipeline`, or an `AskController`
   constructor that still names the old class.

2. **No vendor knowledge outside the adapters.** A second grep, for the
   vendors' URLs and key names rather than your class names:

   ```bash
   grep -rnE "huggingface\.co|api\.jina\.ai" src/main \
     | grep -vE "/(HuggingFaceEmbeddingProvider|JinaEmbeddingProvider)\.java:"
   ```

   **Expected: no output.** Before the refactor, your own code prints one
   line per file that still holds a vendor URL (for example
   `ChunkingPipeline.java:63:    String url = "https://api.jina.ai/v1/embeddings";` and
   `HelloAiController.java:37:        "https://router.huggingface.co/hf-inference/models/"`),
   which is a handy before/after. (The `HF_API_KEY` / `JINA_API_KEY` names
   legitimately appear in `EmbeddingProviderConfig` and `application.properties`,
   which is why this grep looks at URLs instead.) If `HelloAiController`
   shows up here, step 6 is not finished.

3. **Selection works, in both directions.** Start the app twice, once with
   `EMBEDDINGS_PROVIDER=huggingface` and once with `EMBEDDINGS_PROVIDER=jina`
   (with a real key for the provider in use). Each startup log contains the
   factory's one line, naming the provider that was wired:

   ```
   Embeddings provider: huggingface:sentence-transformers/all-MiniLM-L6-v2 (384 dimensions)
   Embeddings provider: jina:jina-embeddings-v3 (384 dimensions)
   ```

   Also try `EMBEDDINGS_PROVIDER=nonsense`: the app must refuse to start,
   with your message naming the two valid values (it is the last `Caused by:`
   line of the stack trace Spring prints).

4. **`/ask` still works after a switch, only after re-ingesting.** With
   `EMBEDDINGS_PROVIDER=jina` (or whichever you switched to), the table
   truncated and the six sample files re-uploaded:

   ```bash
   curl -X POST localhost:8080/ask -H "Content-Type: application/json" \
     -d '{"question":"What is the equipment stipend?"}'
   ```

   returns `200` with an answer naming `$500` and citing
   `remote-work-policy.txt`, the same result as Day 11–14. Then switch back
   (`EMBEDDINGS_PROVIDER=huggingface`), re-ingest again, and get the same
   answer. Then run `./mvnw test`; the existing tests must still pass.

**Optional, to see the proxy rule for yourself.** Temporarily point
`embeddings.huggingface.url` at an invalid host (for example
`https://router.huggingface.invalid/x`) and ask a question. The log shows five
`WARN` lines from the provider, with gaps of roughly 0.5 s, 1 s, 2 s and 4 s
(Day 15's checkpoint, unchanged), before the `503`. Now change the factory to
build the provider outside Spring (call `new HuggingFaceEmbeddingProvider(...)`
from a plain class and keep a reference to it yourself) and repeat: one `WARN`
and an immediate failure. Same class, same annotation, no proxy. Revert it.

## Interview angle

*"How would you support a second LLM or embeddings provider without touching the
`/ask` endpoint?"*

Today produces a demonstrable answer, not a hypothetical one. Prepare it
from your own repository:

- **The boundary.** "Embeddings sit behind an `EmbeddingProvider` interface.
  The ingestion pipeline and the `/ask` controller depend only on that
  interface, and each vendor is an adapter that translates its HTTP API into
  `float[] embed(String)`. A factory method reads one config property and
  decides which adapter exists at runtime." Show the `git diff --stat` of the
  refactor and point at the grep from the checkpoint: it is evidence, not
  assertion.
- **The honest origin.** "I had already swapped Hugging Face for Jina by
  editing the method in place. I refactored so that the same change is a
  config value, and I kept both providers working." Saying *why* the second
  provider mattered (the interface only becomes real once something else
  implements it) is the senior-sounding part.
- **The trap you can name.** "Switching embedding providers is not just a
  config change: vectors from different models aren't comparable even when the
  dimension matches, and a `vector(384)` column accepts both. After a
  switch you have to re-embed the corpus, or retrieval silently degrades.
  Production versions record the model id per row and re-index in the
  background." Providers' own documentation says this (Jina's FAQ and
  LlamaIndex's guide, cited above), and you reproduced it.
- **Where retry lives.** "The retry is a decorator applied to the provider
  bean through Spring's proxy, so it only works because the factory hands
  the object to the container; calling it through `this` or constructing it
  by hand would bypass it." That is the Day 15 lesson applied to a new shape.
- **The LLM half.** For chat models the abstraction already exists: Spring
  AI's `ChatClient` (Day 4) is the interface, and the Groq setup in your
  configuration is an OpenAI-compatible endpoint selected by base URL, key
  and model properties. Swapping the chat provider is a configuration
  change, which is the same outcome by a different mechanism. Be ready to
  say that you did the *embeddings* half by hand because the embeddings call
  in this project was raw `HttpClient`, with nothing to configure.

Also bring your Part 1 worksheet: "which pattern was unnecessary" and "which
one bit you" are the questions an interviewer asks after "have you used the
Decorator pattern?"

## Answer Key (attempt it yourself first!)

Only read this after your own checkpoint passes. It is one reasonable design
and uses the curriculum's package name (`com.example.demo`) and Day 15's
retry annotation; change the package to yours. It was compiled and exercised
against a copy of a real Boot 4.1.1 / Spring AI 2.0.1 project, with each
provider pointed at a local fake server returning that provider's response
shape.

**`EmbeddingProvider.java`**

```java
package com.example.demo;

public interface EmbeddingProvider {

    /** Embeds one piece of text into a vector of exactly {@link #dimensions()} floats. */
    float[] embed(String text);

    /** Length of every vector this provider returns; must match the vector(384) column. */
    int dimensions();

    /** Human-readable "provider:model" string, for logs. */
    String modelId();
}
```

**`TransientEmbeddingException.java`** and **`NonTransientEmbeddingException.java`**
(unchanged from [Day 15](15-rate-limit-resilience.md); only needed if your
app doesn't have them yet)

```java
package com.example.demo;

public class TransientEmbeddingException extends RuntimeException {

    public TransientEmbeddingException(String message) {
        super(message);
    }

    public TransientEmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
package com.example.demo;

public class NonTransientEmbeddingException extends RuntimeException {

    public NonTransientEmbeddingException(String message) {
        super(message);
    }
}
```

**`HuggingFaceEmbeddingProvider.java`** (Day 15's `EmbeddingClient`, renamed,
implementing the interface, with its URL and key passed in)

```java
package com.example.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.Retryable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HuggingFaceEmbeddingProvider implements EmbeddingProvider {

    public static final String DEFAULT_URL = "https://router.huggingface.co/hf-inference/models/"
            + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceEmbeddingProvider.class);
    private static final int DIMENSIONS = 384;

    private final String apiKey;
    private final String url;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HuggingFaceEmbeddingProvider(String apiKey, String url) {
        this.apiKey = apiKey;
        this.url = url;
    }

    @Override
    @Retryable(includes = TransientEmbeddingException.class,
            maxRetries = 4, delay = 500, multiplier = 2, maxDelay = 4000)
    public float[] embed(String text) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("inputs", text))))
                    .build();

            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 429 || status >= 500) {
                log.warn("embedding call failed with a retryable status: HTTP {} - {}",
                        status, response.body());
                throw new TransientEmbeddingException(
                        "embedding call failed: HTTP " + status + " " + response.body());
            }
            if (status != 200) {
                throw new NonTransientEmbeddingException(
                        "embedding call failed: HTTP " + status + " " + response.body());
            }

            // A single string input comes back flat, [0.01, ...]; accept the
            // nested [[0.01, ...]] form too.
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vector = root.get(0).isArray() ? root.get(0) : root;
            float[] embedding = new float[vector.size()];
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) vector.get(i).asDouble();
            }
            if (embedding.length != DIMENSIONS) {
                throw new NonTransientEmbeddingException(modelId() + " returned "
                        + embedding.length + " dimensions, expected " + DIMENSIONS);
            }
            return embedding;
        } catch (TransientEmbeddingException | NonTransientEmbeddingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("embedding call failed with a network error, treating as retryable: {}",
                    e.toString());
            throw new TransientEmbeddingException("embedding call failed", e);
        }
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public String modelId() {
        return "huggingface:sentence-transformers/all-MiniLM-L6-v2";
    }
}
```

**`JinaEmbeddingProvider.java`** (the same shape as the Hugging Face adapter;
only the wire format differs)

```java
package com.example.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.Retryable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JinaEmbeddingProvider implements EmbeddingProvider {

    public static final String DEFAULT_URL = "https://api.jina.ai/v1/embeddings";

    private static final Logger log = LoggerFactory.getLogger(JinaEmbeddingProvider.class);
    private static final String MODEL = "jina-embeddings-v3";
    private static final int DIMENSIONS = 384;

    private final String apiKey;
    private final String url;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JinaEmbeddingProvider(String apiKey, String url) {
        this.apiKey = apiKey;
        this.url = url;
    }

    @Override
    @Retryable(includes = TransientEmbeddingException.class,
            maxRetries = 4, delay = 500, multiplier = 2, maxDelay = 4000)
    public float[] embed(String text) {
        try {
            // "dimensions" asks Jina for a 384-wide vector so it fits the
            // vector(384) column; "text-matching" is the symmetric task.
            Map<String, Object> payload = Map.of(
                    "model", MODEL,
                    "task", "text-matching",
                    "dimensions", DIMENSIONS,
                    "input", List.of(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status == 429 || status >= 500) {
                log.warn("embedding call failed with a retryable status: HTTP {} - {}",
                        status, response.body());
                throw new TransientEmbeddingException(
                        "embedding call failed: HTTP " + status + " " + response.body());
            }
            if (status != 200) {
                throw new NonTransientEmbeddingException(
                        "embedding call failed: HTTP " + status + " " + response.body());
            }

            // {"data": [{"index": 0, "embedding": [...]}], "usage": {...}}
            JsonNode vector = objectMapper.readTree(response.body())
                    .path("data").path(0).path("embedding");
            if (!vector.isArray()) {
                throw new NonTransientEmbeddingException(
                        "unexpected response shape, no data[0].embedding array: " + response.body());
            }
            float[] embedding = new float[vector.size()];
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) vector.get(i).asDouble();
            }
            if (embedding.length != DIMENSIONS) {
                throw new NonTransientEmbeddingException(modelId() + " returned "
                        + embedding.length + " dimensions, expected " + DIMENSIONS);
            }
            return embedding;
        } catch (TransientEmbeddingException | NonTransientEmbeddingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("embedding call failed with a network error, treating as retryable: {}",
                    e.toString());
            throw new TransientEmbeddingException("embedding call failed", e);
        }
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public String modelId() {
        return "jina:" + MODEL;
    }
}
```

**`EmbeddingProviderConfig.java`** (the factory method; the only file besides
the providers that names a concrete class)

```java
package com.example.demo;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderConfig.class);

    @Bean
    public EmbeddingProvider embeddingProvider(
            @Value("${embeddings.provider:huggingface}") String provider,
            @Value("${HF_API_KEY:}") String hfApiKey,
            @Value("${JINA_API_KEY:}") String jinaApiKey,
            @Value("${embeddings.huggingface.url:" + HuggingFaceEmbeddingProvider.DEFAULT_URL + "}")
            String hfUrl,
            @Value("${embeddings.jina.url:" + JinaEmbeddingProvider.DEFAULT_URL + "}")
            String jinaUrl) {

        EmbeddingProvider chosen = switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "huggingface" ->
                    new HuggingFaceEmbeddingProvider(requireKey(hfApiKey, "HF_API_KEY"), hfUrl);
            case "jina" ->
                    new JinaEmbeddingProvider(requireKey(jinaApiKey, "JINA_API_KEY"), jinaUrl);
            default -> throw new IllegalStateException("Unknown embeddings.provider '" + provider
                    + "'; expected 'huggingface' or 'jina'");
        };

        log.info("Embeddings provider: {} ({} dimensions)", chosen.modelId(), chosen.dimensions());
        return chosen;
    }

    private static String requireKey(String key, String name) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "embeddings.provider needs " + name + " to be set, but it is empty");
        }
        return key;
    }
}
```

**`application.properties`** (add one line; `EMBEDDINGS_PROVIDER` can then come
from the environment or from `.env`)

```properties
embeddings.provider=${EMBEDDINGS_PROVIDER:huggingface}
```

**`SpringBootAiProjectApplication.java`** (or whatever your main class is
called): the class carries `@EnableResilientMethods` from Day 15. If you are on
Spring Retry's `@EnableRetry`, keep it and use that library's `@Retryable` in
the two providers.

**`ChunkingPipeline.java`** (everything that embedded is gone; if your class has
other methods that don't embed, such as Day 8–10's `remove(...)`, they stay
exactly as they were)

```java
package com.example.demo;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ChunkingPipeline {

    private final ChunkingStrategy chunkingStrategy;
    private final ChunkRepository chunkRepository;
    private final EmbeddingProvider embeddingProvider;

    public ChunkingPipeline(ChunkingStrategy chunkingStrategy,
                            ChunkRepository chunkRepository,
                            EmbeddingProvider embeddingProvider) {
        this.chunkingStrategy = chunkingStrategy;
        this.chunkRepository = chunkRepository;
        this.embeddingProvider = embeddingProvider;
    }

    public void ingest(Long documentId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<String> chunks = chunkingStrategy.chunk(text);
        for (String chunk : chunks) {
            float[] embedding = embeddingProvider.embed(chunk);
            chunkRepository.save(new Chunk(documentId, chunk, embedding));
        }
    }
}
```

**`AskController.java`** — only these parts change. Starting from Day 15's
complete controller (which injected `EmbeddingClient`):

```java
// field
private final EmbeddingProvider embeddingProvider;      // was: EmbeddingClient embeddingClient

// constructor
public AskController(ChatClient.Builder chatClientBuilder,
                     ChunkRepository chunkRepository,
                     DocumentRepository documentRepository,
                     EmbeddingProvider embeddingProvider) {
    this.chatClient = chatClientBuilder.build();
    this.chunkRepository = chunkRepository;
    this.documentRepository = documentRepository;
    this.embeddingProvider = embeddingProvider;
}

// first line inside the try block of ask(...)
float[] queryEmbedding = embeddingProvider.embed(request.question());
```

Nothing else in the controller changes: the `catch (OpenAIException |
TransientEmbeddingException | NonTransientEmbeddingException e)` still works
because the exceptions are the ones the interface's contract promises. If your
controller instead called `chunkingPipeline.embed(request.question())`, the
change is the same three lines, and the `ChunkingPipeline` field and
constructor parameter go away (and with them the controller's dependency on
the ingestion class).

**`HelloAiController.java`** (the Day 4 scaffold, now with no vendor code)

```java
package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloAiController {

    private final ChatClient chatClient;
    private final EmbeddingProvider embeddingProvider;

    public HelloAiController(ChatClient.Builder chatClientBuilder,
                             EmbeddingProvider embeddingProvider) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingProvider = embeddingProvider;
    }

    @GetMapping("/hello-groq")
    public String helloGroq() {
        return chatClient.prompt("Say hello in one sentence.")
                .call()
                .content();
    }

    @GetMapping("/hello-embedding")
    public float[] helloEmbedding() {
        return embeddingProvider.embed("hello world");
    }
}
```

Commit on the feature branch with a Conventional Commits message (for example
`refactor: put embeddings behind an EmbeddingProvider interface`), push, and
merge via a pull request per Day 1. Delete the now-unused `EmbeddingClient`
(Day 15) or the in-place `embed()` code in the same commit, so no dead copy of
a vendor call is left behind.
