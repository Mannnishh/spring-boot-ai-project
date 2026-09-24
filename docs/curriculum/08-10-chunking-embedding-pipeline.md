# Day 8-10 — Chunking + Embedding Pipeline → pgvector

> **Before you start:** if your `pom.xml` is on Spring Boot 3.x or a Spring
> AI 1.x version, do the [one-time upgrade](07b-upgrade-boot4-spring-ai2.md)
> first. Everything from here on is tested against Spring Boot 4.1.1 +
> Spring AI 2.0.1.
>
> Then do the [carry-over fixes](#carry-over-fixes-from-days-37) below.
> They repair Day 3–7 problems that start to matter today.

**Focus:** Turn the extracted text from Day 6–7 into *retrievable* data.
You'll add a pluggable `ChunkingStrategy`, split each document's text into
chunks, embed every chunk with the Day 4 Hugging Face call, and persist each
one as a `Chunk` row in Postgres with a `vector(384)` column — then query the
nearest chunks with pgvector's cosine-distance operator. The pattern note is
the Strategy pattern you previewed on Day 2, now used for real.

This day is the bridge between "I can extract text" (Day 6–7) and "I can
answer questions from it" (Day 11–14). Days 11–14 assume the chunk table
already exists and is queryable, so the deliverable here is a working,
*queried* `chunk` table — not just code that compiles.

## Carry-over fixes from Days 3–7

A review of the Day 3–7 code (yours, and this curriculum's own answer keys)
found problems that start to matter today, because today is when stored
text becomes chunks. Fix them first, on their own branch, and merge that
before starting the Day 8 branch:

```bash
git checkout -b fix/carry-over-days-3-7
```

**1. Tika silently truncates long documents.** `Tika.parseToString` stops
at 100,000 characters by default and returns what it has, with no error.
A 150,000-character file is stored as 99,999 characters, and today's
chunker can only chunk what was stored, so the rest of the document would
never be retrievable. Lift the cap where Day 6–7 creates the bean:

```java
package com.example.demo;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TikaConfig {

    @Bean
    public Tika tika() {
        Tika tika = new Tika();
        tika.setMaxStringLength(-1); // default 100_000 silently truncates long documents
        return tika;
    }
}
```

`-1` means no limit. A production system would set a generous cap and
reject larger files, since the whole text is held in memory.

**2. An empty upload returns 500.** Tika throws `ZeroByteFileException`
for a zero-byte file. Sending an empty file is the client's mistake, so
answer `400` before calling Tika, at the top of `upload(...)`:

```java
if (file.isEmpty()) {
    return ResponseEntity.badRequest().build();
}
```

**3. `POST /documents` lets a client overwrite any document.** Day 3's
`create` binds the request JSON straight onto the `Document` entity, `id`
included, and when `id` is set, `save()` updates that row instead of
inserting a new one. Sending `{"id":1,"filename":"x","content":"gone"}`
replaces document 1 and still answers `201 Created`. From today that gets
worse: document 1's chunks would keep describing text that no longer
exists. Accept a record holding only the fields a client may choose, and
let the server decide `id` and `uploadedAt`:

```java
package com.example.demo;

public record CreateDocumentRequest(String filename, String content) {}
```

```java
@PostMapping
public ResponseEntity<Document> create(@RequestBody CreateDocumentRequest request) {
    Document document = new Document(request.filename(), request.content(), Instant.now());
    Document saved = repository.save(document);
    return ResponseEntity.created(URI.create("/documents/" + saved.getId())).body(saved);
}
```

This bug is called *over-posting* or *mass assignment*. It's worth
knowing by name for interviews, and it's the reason APIs take request
records (like Day 11–14's `AskRequest`) rather than entities.

**4. `./mvnw test` fails.** The test Spring Initializr generated
(`contextLoads`) starts the whole application, so it needs Postgres and
both API keys, and fails without them. A Dockerfile that builds with
`-DskipTests` hides this. Delete the generated `*ApplicationTests.java`
and replace it with a *slice* test that starts only the web layer and
mocks everything beneath it, so it runs anywhere:

```java
package com.example.demo;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DocumentRepository repository;

    @MockitoBean
    private Tika tika;

    @Test
    void unknownIdReturns404() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.empty());
        mvc.perform(get("/documents/42")).andExpect(status().isNotFound());
    }

    @Test
    void emptyUploadReturns400() throws Exception {
        MockMultipartFile empty =
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        mvc.perform(multipart("/documents/upload").file(empty))
                .andExpect(status().isBadRequest());
    }
}
```

`@WebMvcTest` lives in `org.springframework.boot.webmvc.test.autoconfigure`
on Spring Boot 4 (older guides show a different package), and it comes
from the `spring-boot-starter-webmvc-test` dependency the upgrade guide
added. Every bean the controller's constructor takes needs a
`@MockitoBean`. You'll add one more in step 5 today.

**5. (Optional) Uploads over 1 MB are rejected** with `413`, Spring's
default limit. Real PDFs are often bigger:

```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

**Carry-over checkpoint:** `./mvnw test` passes with no database running;
uploading an empty file returns `400`; the overwrite `curl` from fix 3
creates a *new* document and leaves document 1 alone. Merge through a PR
(`fix: carry over days 3-7 review fixes`), then branch Day 8 from the
updated `main`.

## Concept primer

### The pipeline shape: text → chunks → embeddings → rows

Day 6–7 gave you a `Document` whose `content` column holds a big blob of
extracted plain text. That blob is not yet usable for retrieval: an embedding
model has a fixed context window, and — more importantly — a single embedding
of a whole multi-paragraph document is one point in vector space that
*averages* every topic in the document together. It matches any query weakly
and no query strongly. (Day 5's
[chunking trade-offs](05-day5-rag-fundamentals.md) section walks through why
this averaging is bad.)

So the pipeline slices before it embeds:

1. **Chunk** the document's `content` into smaller pieces via a
   `ChunkingStrategy`.
2. **Embed** each chunk with the same Day 4 Hugging Face call
   (`sentence-transformers/all-MiniLM-L6-v2`), producing a 384-dimensional
   `float[]`.
3. **Store** each chunk as its own row, carrying:
   - `content` — the chunk's text (what you'll later feed the model);
   - `documentId` — a foreign key back to the source `Document`;
   - `embedding` — the `vector(384)` column that makes the row searchable.

The result is a many-to-one relationship: one `Document` → many `Chunk` rows.

### Why chunk-level (not document-level) embeddings

The foreign key is the whole point, and it's worth saying out loud: **you are
embedding chunks, not documents.** That choice is what buys you two things
Days 11–14 need:

- **Precise retrieval.** A query like *"what is the equipment stipend?"*
  should match the *paragraph* about the stipend, not the whole 40-page
  policy. If the whole document were one vector, every query against that
  document would return the same embedding and you could never tell *which*
  part answered the question. Chunk-level vectors let the similarity search
  return the specific passage, not just "this file somewhere."
- **Citation.** Because each chunk row stores `documentId` back to its source
  `Document`, the retrieved chunk carries its provenance with it. The answer
  on Day 11–14 can say "found in `remote-work-policy.txt`" — which is only
  possible because retrieval returns a row that *knows where it came from*.
  A document-level embedding can tell you which file matched; it cannot tell
  you which sentence inside it did.

This is the same "keep knowledge outside the model, in a searchable store"
idea from Day 5 — the store is just now granular enough to be useful.

### What pgvector adds here (vs. Day 3)

Day 3 stood up Postgres via the `pgvector/pgvector` Docker image but
deliberately used **none** of pgvector's features — the `vector` type was
waiting for today. pgvector adds a `vector(N)` column type and a set of
distance operators you can use in ordinary SQL, documented in the
[pgvector README](https://github.com/pgvector/pgvector). Today you use:

- the `vector(384)` column type for `embedding` (N must match the 384 the
  model produces — fixed per model, verified on Day 4);
- the `<=>` **cosine distance** operator to order rows by similarity to a
  query vector.

Note the direction: `<=>` is cosine *distance* (0 = identical), the inverse
of Day 5's cosine *similarity* (1 = identical). `ORDER BY embedding <=> :q
LIMIT :k` therefore returns the *k nearest* chunks — smallest distance first.
That's exactly what "retrieve the top-k chunks" means.

## Reference links

**pgvector**

- [pgvector README](https://github.com/pgvector/pgvector) — the `vector`
  column type, the distance operators (`<->` L2, `<#>` inner product, `<=>`
  cosine distance, `<+>` L1), the
  `SELECT * FROM items ORDER BY embedding <=> '[3,1,2]' LIMIT 5` query shape,
  and the HNSW/IVFFlat index sections used in the interview angle.
- [pgvector-java README](https://github.com/pgvector/pgvector-java) — the
  official Java client's README; the "Hibernate" section documents that
  Hibernate 6.4+ has a built-in `vector` module (the `org.hibernate.orm:
  hibernate-vector` artifact) and that the mapping is
  `@JdbcTypeCode(SqlTypes.VECTOR)` + `@Array(length = N)` on a `float[]`
  field. It also shows the `ivfflat` index over `vector_cosine_ops`.
- [Hibernate 6.4 User Guide — Vector module](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#vector-module) —
  the canonical mapping: annotate a `float[]` with `@JdbcTypeCode(SqlTypes.
  VECTOR)` and give the dimension with `@Array(length = ...)`; the column DDL
  is `vector(384)`.

**Spring Data JPA (native queries)**

- [Spring Data JPA — Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html) —
  the canonical page for `@Query` with `nativeQuery = true` and `@Param`.
  *Note: the older `/repositories/query-methods.html` path 404s now; use this
  `jpa/query-methods.html` URL.*

**Text chunking (the real thing — for the strategy swap discussed below)**

- [Spring AI — `TokenTextSplitter` (Javadoc)](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/transformer/splitter/TokenTextSplitter.html) —
  a token-aware splitter, the production-grade contrast to today's
  fixed-size chunker. *Caution: this class currently carries deprecation
  notices in its Javadoc — treat its exact API as version-dependent and
  verify against your Spring AI version before relying on it.*
- [Spring AI — ETL Pipeline](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html) —
  where `TokenTextSplitter` is documented as a `TextSplitter` implementation.

**Internal (already verified on earlier days)**

- [Day 5 — chunking discussion](05-day5-rag-fundamentals.md) — the naive
  fixed-size chunker this day replaces, plus why token-aware/sentence-aware
  chunking is the real fix.
- [Day 4 — 384-dimension embeddings call](04-day4-groq-embeddings-hello-world.md) —
  the exact HF call and the verified 384 dimension reused below.
- [Day 3 — `Document` entity + pgvector Compose](03-day3-crud-docker.md) —
  the `Document` entity and the Postgres/pgvector stack already running.
- [Day 2 — Strategy pattern vocabulary](02-day2-java-oop-refresher.md) —
  section 6, the definition today's pattern note applies.

## Java pattern note

**Java pattern note:** the `ChunkingStrategy` interface is the **Strategy**
pattern from [Day 2, section 6](02-day2-java-oop-refresher.md) — "define a
family of algorithms, put each into a separate class, and make their objects
interchangeable." The pipeline depends on the interface, not on any concrete
chunker; which concrete algorithm it got is decided elsewhere (a Spring bean
definition, shown in the exercise). That's the open/closed part: a second
strategy — a sentence-aware or token-aware chunker wrapping Spring AI's
`TokenTextSplitter` — can be swapped in later by adding a class and changing
one bean, *without touching the pipeline method at all.* The pipeline code
doesn't change when the chunking algorithm changes; that's the point.

## Exercise (guided TODO + hints; worked examples for the two new things)

Do today's work on a new feature branch, per Day 1's workflow:

```bash
git checkout -b feature/chunking-embedding
```

### 1. Worked example — the Strategy interface + fixed-size implementation

This part is new material (the Strategy pattern made concrete), so it's
written out in full — read it, then create the files. Note the **overlap**
parameter: consecutive chunks share `overlap` characters, so a fact that
would otherwise be cut in half at a chunk boundary appears whole in one of
the neighboring chunks. This is the one deliberate improvement over Day 5's
naive chunker, which had no overlap and therefore split facts across chunks
with nothing to catch them.

**`ChunkingStrategy.java`**

```java
package com.example.demo;

import java.util.List;

/**
 * Splits a document's extracted text into retrievable pieces.
 * A Strategy (Day 2, section 6): the pipeline depends on this interface,
 * so concrete chunkers are interchangeable without touching the pipeline.
 */
public interface ChunkingStrategy {
    List<String> chunk(String text);
}
```

**`FixedSizeChunkingStrategy.java`**

```java
package com.example.demo;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size character chunker with overlap. Still naive (it can cut
 * mid-word, mid-sentence) — the interface exists so a token-aware or
 * sentence-aware strategy can replace this one later.
 */
public class FixedSizeChunkingStrategy implements ChunkingStrategy {

    private final int chunkSize;
    private final int overlap;

    public FixedSizeChunkingStrategy(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be in [0, chunkSize)");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            // Step forward by (chunkSize - overlap), so the next chunk
            // shares `overlap` characters with this one.
            start = end - overlap;
        }
        return chunks;
    }
}
```

Then expose one as a Spring bean so the pipeline can inject it (a second
strategy later just replaces the returned instance):

```java
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChunkingConfig {

    @Bean
    public ChunkingStrategy chunkingStrategy() {
        // 1000 chars with 100 chars of overlap; tune later (Day 11–14
        // iteration notes cover when and how).
        return new FixedSizeChunkingStrategy(1000, 100);
    }
}
```

### 2. TODO — the `Chunk` entity

Create `Chunk`. The `embedding` field is where pgvector enters: the column is
`vector(384)` — **384 because `all-MiniLM-L6-v2` returns a 384-dim vector**
(verified on Day 4, not a guess), and every stored vector must match that
dimension or pgvector rejects the row.

```java
package com.example.demo;

// TODO: @Entity
public class Chunk {

    // TODO: Long id — @Id + @GeneratedValue(strategy = GenerationType.IDENTITY)

    // TODO: Long documentId — plain FK column back to Document.id
    //       (a @ManyToOne Document would also work; a plain Long keeps the
    //       entity self-contained and is all the pipeline needs today)

    // TODO: String content — @Column(columnDefinition = "TEXT")
    //       (same column type Day 6–7 used for Document.content)

    // TODO: float[] embedding — the vector column:
    //         @Column(columnDefinition = "vector(384)")
    //         @JdbcTypeCode(SqlTypes.VECTOR)
    //         @Array(length = 384)
    //       imports: org.hibernate.annotations.JdbcTypeCode,
    //                org.hibernate.annotations.Array,
    //                org.hibernate.type.SqlTypes

    // TODO: no-arg constructor (JPA requires it) + getters/setters
}
```

*Hints:*

- **The `hibernate-vector` dependency.** `@JdbcTypeCode(SqlTypes.VECTOR)`
  needs Hibernate's vector module, which is **not** pulled in transitively.
  Add `org.hibernate.orm:hibernate-vector` to `pom.xml` (version is managed
  by Spring Boot's Hibernate BOM if you leave it off; if your build
  complains, pin it to your Hibernate version). This is documented in the
  [pgvector-java README](https://github.com/pgvector/pgvector-java) and the
  [Hibernate 6.4 User Guide](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#vector-module).
- **`@Array(length = 384)`** is what tells Hibernate the vector's dimension
  when it generates the `vector(384)` DDL. The `@Column(columnDefinition =
  "vector(384)")` spells out the same thing explicitly; keeping both is
  belt-and-suspenders and matches the Hibernate guide.
- **`float[]`** (not `double[]`, not `String`) matches what Day 4's
  `helloEmbedding()` already returns.
- Day 3's `ddl-auto: update` will create the `chunk` table from this entity
  on startup — but **only if the `vector` extension is enabled first**. Run
  this once in your `ragdb` database (psql or `docker exec`):

  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;
  ```

  This is the piece Day 3 explicitly deferred to today. Without it, Hibernate
  errors when it tries to create a `vector(384)` column.

### 3. TODO — the pipeline method

Create a service that, given a document id and its extracted text, runs the
whole shape from the concept primer. Inject the `ChunkingStrategy` (the
interface — never the concrete class), the `ChunkRepository`, and reuse the
Day 4 embeddings call.

```java
package com.example.demo;

import org.springframework.stereotype.Service;

@Service
public class ChunkingPipeline {

    private final ChunkingStrategy chunkingStrategy;
    private final ChunkRepository chunkRepository;

    public ChunkingPipeline(ChunkingStrategy chunkingStrategy,
                            ChunkRepository chunkRepository) {
        this.chunkingStrategy = chunkingStrategy;
        this.chunkRepository = chunkRepository;
    }

    public void ingest(Long documentId, String text) {
        // TODO:
        //   1. List<String> chunks = chunkingStrategy.chunk(text);
        //   2. for each chunk: float[] embedding = embed(chunk);
        //   3. chunkRepository.save(new Chunk(documentId, chunk, embedding));
        throw new UnsupportedOperationException("not implemented");
    }

    // TODO (optional): private float[] embed(String text) — the Day 4 HF
    // feature-extraction call, unchanged in shape (see Day 4, step 4).
}
```

*Hints:*

- **`embed(String)` is Day 4's call, with two fixes.** Same endpoint
  (`https://router.huggingface.co/hf-inference/models/
  sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction`) and
  same `Authorization: Bearer hf_...` header. Inject the key with
  `@Value("${HF_API_KEY}")`. Two things change now that the input is real
  document text instead of `"hello world"`:
  - **Build the request body with Jackson, not by hand:**
    `objectMapper.writeValueAsString(Map.of("inputs", text))`. Extracted
    text can contain tabs, form feeds and other control characters, and a
    hand-written escaper that misses one produces invalid JSON, which
    Hugging Face rejects with a `400`.
  - **Accept both response shapes.** For a single string input, Hugging
    Face returns a flat array `[0.01, …]`. Day 4's code expected it wrapped
    as `[[0.01, …]]`, and you may already have hit that. Parse with
    `readTree` and unwrap only if the first element is itself an array.
    The answer key shows both fixes.
- **Check the status code before parsing.** A bad key, a model that's
  still loading, or a rate limit comes back as a JSON *error object*.
  Parsing that as numbers produces a confusing Jackson error that hides the
  real cause, so throw with the status and body instead.
- **One `Chunk` row per chunk.** `documentId` is the FK back to the source
  `Document`; `content` is the chunk text; `embedding` is the returned
  `float[]`.
- **The `ChunkRepository`** is a one-liner like Day 3's:
  `interface ChunkRepository extends JpaRepository<Chunk, Long> {}` — you'll
  add the similarity query to it in step 4.
- **How does it get run?** Step 5 calls it from the upload endpoint, so
  every uploaded document is chunked and embedded as it arrives.

### 4. Worked example — the native pgvector similarity query

This syntax is unfamiliar territory, so it's shown complete rather than left
as a TODO. Add it to `ChunkRepository`. The query orders chunks by cosine
*distance* to a query vector and takes the top `k`:

```java
package com.example.demo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    @Query(value = "SELECT * FROM chunk "
            + "ORDER BY embedding <=> CAST(:queryVector AS vector) LIMIT :k",
           nativeQuery = true)
    List<Chunk> findNearest(@Param("queryVector") String queryVector,
                            @Param("k") int k);
}
```

Two things to understand, both verified live against pgvector (see the
interview angle for the "why you should care" framing):

- **`<=>` is cosine distance.** Smaller = more similar. `ORDER BY ... <=>
  ... LIMIT k` returns the `k` nearest chunks. It is the *inverse* of Day 5's
  cosine similarity, which is why "nearest" means "smallest distance first."
- **The `CAST(:queryVector AS vector)` is not optional.** pgvector's `<=>`
  operator is defined as `vector <=> vector`. If you write
  `embedding <=> :queryVector` with a JDBC `String`/`text` parameter, Postgres
  rejects it with `operator does not exist: vector <=> text`. The explicit
  cast coerces the bound string (in pgvector's `'[0.1, -0.02, ...]'` text
  form) to `vector` first. The pgvector README's own examples use a bare
  *literal* like `'[3,1,2]'`, which Postgres auto-types; a bound parameter is
  typed and needs the cast. This is exactly why the `@Query` can't be the
  naive `<=> :queryVector` form you might guess first.

*Note on native-query result mapping:* `SELECT *` returns the `embedding`
column too, and Hibernate maps it back to `float[]` through the same
`@JdbcTypeCode(SqlTypes.VECTOR)` declared on the entity — that's what makes
`List<Chunk>` (rather than `List<Object[]>`) work here.

### 5. TODO — run the pipeline on every upload, and clean up on delete

Nothing calls `ingest(...)` yet. Wire it into the upload endpoint so every
document is chunked and embedded the moment it arrives. That's also what
makes a document uploaded *during a demo* answerable on Day 11–14 without a
manual step.

In `DocumentController`:

- inject `ChunkingPipeline`;
- in `upload(...)`, right after `repository.save(...)`, call
  `chunkingPipeline.ingest(saved.getId(), saved.getContent())`;
- in `delete(...)`, remove the document's chunks before the document
  itself. Otherwise they stay in the `chunk` table, keep appearing in
  similarity search, and Day 11–14 would cite a document that no longer
  exists.

For the delete, give `ChunkRepository` a derived delete query and the
pipeline a method that calls it:

```java
@Transactional
void deleteByDocumentId(Long documentId);
```

*Hints:*

- `@Transactional` here is
  `org.springframework.transaction.annotation.Transactional`. A derived
  *delete* query has to run inside a transaction, and query methods you
  declare yourself don't get a read-write one automatically. Leave it off
  and the delete fails with `No EntityManager with actual transaction
  available`.
- Add `@MockitoBean private ChunkingPipeline chunkingPipeline;` to the
  carry-over `DocumentControllerTest`. Without it, the test fails with
  `No qualifying bean of type ChunkingPipeline`. That's the test doing its
  job: the controller now depends on something new.
- Ingestion runs inside the upload request, one Hugging Face call per
  chunk. That's fine for the sample files (one or two chunks each). A large
  document means many calls in a row, and if one fails, the upload returns
  an error with the document saved but only partly chunked. Day 15 adds
  retries; a production system would move ingestion to a background job.
- **Re-ingest the six sample files.** They were uploaded before chunking
  existed, so they have no chunks. Empty both tables and upload them again:

  ```bash
  docker compose exec db sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "TRUNCATE document, chunk RESTART IDENTITY;"'

  for f in docs/curriculum/sample-data/*.txt docs/curriculum/sample-data/*.pdf; do
    curl -s -o /dev/null -w "%{http_code} $f\n" -F "file=@$f" localhost:8080/documents/upload
  done
  ```

  The scanned PDF still uploads, but its empty text produces no chunks.
  That's the Day 6–7 lesson carried forward: it can never be retrieved.

## Checkpoint

You're done with Days 8–10 when:

1. `docker compose up` (Day 3's stack) starts the app, and the app boots
   without error — meaning the `chunk` table was created with its
   `vector(384)` column (so the `CREATE EXTENSION vector` step ran and the
   `hibernate-vector` dependency is present).
2. After re-uploading the six sample files (step 5), a manual query shows
   **multiple** chunk rows for `remote-work-policy.txt`, the longest sample
   file, each with a non-null embedding:

   ```sql
   SELECT count(*) FROM chunk WHERE document_id = <the document's id>;
   -- expect > 1 (the document was long enough to produce several chunks)

   SELECT count(*) FROM chunk
   WHERE document_id = <the document's id> AND embedding IS NOT NULL;
   -- expect the same count: every chunk row carries an embedding
   ```

3. A manual similarity query against a hardcoded query vector returns rows
   ordered by distance (smallest first). The literal must be 384 floats; for
   a quick sanity check you can compare against a known-similar chunk:

   ```sql
   SELECT id, document_id, left(content, 60) AS content_head
   FROM chunk
   ORDER BY embedding <=> '[<384 comma-separated floats>]'
   LIMIT 5;
   ```

   *Trick for a no-paste check:* use a stored chunk's own embedding as the
   query vector — `ORDER BY embedding <=> (SELECT embedding FROM chunk LIMIT 1)`
   — and that chunk should rank first with distance 0.
4. `DELETE /documents/<id>` removes that document's chunks too:
   `SELECT count(*) FROM chunk WHERE document_id = <id>;` returns `0`
   afterwards. Then upload the file again so all six are back for Day 11–14.
5. `./mvnw test` still passes.
6. Commit on the feature branch with a Conventional Commits message (e.g.
   `feat: add chunking strategy and embedding pipeline to pgvector`), push,
   and merge via a pull request per Day 1.

If the app boots but the similarity query errors with
`operator does not exist: vector <=> text`, you dropped the `CAST` — that's
the one live-verified trap in this day, and the fix is in step 4.

## Interview angle

*"How would you scale vector search beyond a few thousand rows?"*

The honest answer starts with "this dataset is small enough that I didn't
need to." With a few thousand rows, a full sequential scan over the chunk
table is already fast — pgvector will happily `ORDER BY embedding <=> :q`
over the whole table and you'll never notice. The *next* step, once the table
grows, is an **index** that avoids scanning every row. pgvector offers two
approximate indexes, both documented in the
[pgvector README](https://github.com/pgvector/pgvector):

- **IVFFlat** — `CREATE INDEX ON chunk USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 100);` divides vectors into lists and searches only the lists
  closest to the query. Faster build, less memory, lower query performance.
- **HNSW** — `CREATE INDEX ON chunk USING hnsw (embedding vector_cosine_ops);`
  builds a multilayer graph with better query performance (speed/recall
  trade-off) than IVFFlat, at the cost of slower builds and more memory.

Both are *approximate* (they trade a little recall for a lot of speed), and
both need `vector_cosine_ops` to match the `<=>` cosine-distance operator —
using the wrong ops class (`vector_l2_ops`, `vector_ip_ops`) silently indexes
the wrong distance metric. The named next step, plus knowing *why* it's a
later step rather than today's, is the strong answer: you don't add an index
for a dataset that scans in milliseconds.

## Answer Key (attempt it yourself first!)

Only read this after the checkpoint queries pass. The `ChunkingStrategy`
interface and `FixedSizeChunkingStrategy` are **already shown in full
above** and aren't repeated here. This key adds the pieces that were TODO:
the `Chunk` entity, the pipeline, `ChunkRepository` with the step 5 delete
query, and `DocumentController` with the carry-over fixes and the step 5
wiring.

**`Chunk.java`**

```java
package com.example.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "vector(384)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 384)
    private float[] embedding;

    protected Chunk() {
        // JPA requires a no-arg constructor.
    }

    public Chunk(Long documentId, String content, float[] embedding) {
        this.documentId = documentId;
        this.content = content;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getContent() {
        return content;
    }

    public float[] getEmbedding() {
        return embedding;
    }
}
```

**`ChunkingPipeline.java`**

```java
package com.example.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ChunkingPipeline {

    private final ChunkingStrategy chunkingStrategy;
    private final ChunkRepository chunkRepository;
    private final String hfApiKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChunkingPipeline(ChunkingStrategy chunkingStrategy,
                            ChunkRepository chunkRepository,
                            @Value("${HF_API_KEY}") String hfApiKey) {
        this.chunkingStrategy = chunkingStrategy;
        this.chunkRepository = chunkRepository;
        this.hfApiKey = hfApiKey;
    }

    public void ingest(Long documentId, String text) {
        List<String> chunks = chunkingStrategy.chunk(text);
        for (String chunk : chunks) {
            float[] embedding = embed(chunk);
            chunkRepository.save(new Chunk(documentId, chunk, embedding));
        }
    }

    public void remove(Long documentId) {
        chunkRepository.deleteByDocumentId(documentId);
    }

    // Day 4's Hugging Face embeddings call, with the request body built by
    // Jackson and both response shapes accepted.
    private float[] embed(String text) {
        try {
            String url = "https://router.huggingface.co/hf-inference/models/"
                    + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + hfApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("inputs", text))))
                    .build();

            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("embedding call failed: HTTP "
                        + response.statusCode() + " " + response.body());
            }

            // A single string input comes back flat, [0.01, ...]; accept the
            // nested [[0.01, ...]] form too.
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

**`ChunkRepository.java`** (step 4's query plus step 5's delete)

```java
package com.example.demo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    @Query(value = "SELECT * FROM chunk "
            + "ORDER BY embedding <=> CAST(:queryVector AS vector) LIMIT :k",
           nativeQuery = true)
    List<Chunk> findNearest(@Param("queryVector") String queryVector,
                            @Param("k") int k);

    @Transactional
    void deleteByDocumentId(Long documentId);
}
```

**`DocumentController.java`** (carry-over fixes 2 and 3, plus step 5)

```java
package com.example.demo;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentRepository repository;
    private final Tika tika;
    private final ChunkingPipeline chunkingPipeline;

    public DocumentController(DocumentRepository repository, Tika tika,
                              ChunkingPipeline chunkingPipeline) {
        this.repository = repository;
        this.tika = tika;
        this.chunkingPipeline = chunkingPipeline;
    }

    @GetMapping
    public List<Document> findAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> findById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Document> create(@RequestBody CreateDocumentRequest request) {
        Document document = new Document(request.filename(), request.content(), Instant.now());
        Document saved = repository.save(document);
        return ResponseEntity.created(URI.create("/documents/" + saved.getId())).body(saved);
    }

    @PostMapping("/upload")
    public ResponseEntity<Document> upload(@RequestParam("file") MultipartFile file)
            throws IOException, TikaException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String extracted = tika.parseToString(file.getInputStream());
        Document document = new Document(
                file.getOriginalFilename(), extracted, Instant.now());
        Document saved = repository.save(document);
        chunkingPipeline.ingest(saved.getId(), saved.getContent());
        return ResponseEntity
                .created(URI.create("/documents/" + saved.getId()))
                .body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        chunkingPipeline.remove(id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
```
