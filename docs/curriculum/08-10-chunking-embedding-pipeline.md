# Day 8-10 — Chunking + Embedding Pipeline → pgvector

> **Before you start:** if your `pom.xml` is on Spring Boot 3.x or a Spring
> AI 1.x version, do the [one-time upgrade](07b-upgrade-boot4-spring-ai2.md)
> first. Everything from here on is tested against Spring Boot 4.1.1 +
> Spring AI 2.0.1.

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
  `helloEmbedding()` already returns after its `double[][]` → `float[]`
  conversion.
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

- **`embed(String)` is Day 4's call, verbatim.** Same endpoint
  (`https://router.huggingface.co/hf-inference/models/
  sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction`), same
  `Authorization: Bearer hf_...` header, same array-of-arrays response parsed
  into a `float[]`. The only difference is the input is a chunk string
  instead of `"hello world"`. Inject the key with `@Value("${HF_API_KEY}")`.
- **One `Chunk` row per chunk.** `documentId` is the FK back to the source
  `Document`; `content` is the chunk text; `embedding` is the returned
  `float[]`.
- **The `ChunkRepository`** is a one-liner like Day 3's:
  `interface ChunkRepository extends JpaRepository<Chunk, Long> {}` — you'll
  add the similarity query to it in step 4.
- **How do you run it?** The method itself is the deliverable; trigger it
  however is quickest for you — a `CommandLineRunner` bean that fetches a
  `Document` by id and calls `ingest(id, doc.getContent())`, a scratch test,
  or a temporary endpoint. Days 11–14 will call this same logic from the
  `/ask` flow, so don't over-invest in the trigger now.

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

## Checkpoint

You're done with Days 8–10 when:

1. `docker compose up` (Day 3's stack) starts the app, and the app boots
   without error — meaning the `chunk` table was created with its
   `vector(384)` column (so the `CREATE EXTENSION vector` step ran and the
   `hibernate-vector` dependency is present).
2. After uploading a **multi-paragraph** document on Day 6–7's endpoint and
   running the pipeline against it, a manual query shows **multiple** chunk
   rows, each with a non-null embedding:

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
4. Commit on the feature branch with a Conventional Commits message (e.g.
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
interface, `FixedSizeChunkingStrategy`, and the `findNearest` similarity query
are **already shown in full above** — they are not repeated here. This key
adds only the two pieces that were TODO: the `Chunk` entity and the pipeline
method.

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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    // Day 4's Hugging Face embeddings call, unchanged in shape —
    // see 04-day4-groq-embeddings-hello-world.md, step 4.
    private float[] embed(String text) {
        try {
            String url = "https://router.huggingface.co/hf-inference/models/"
                    + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + hfApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"inputs\":" + jsonString(text) + "}"))
                    .build();

            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("embedding call failed: HTTP "
                        + response.statusCode() + " " + response.body());
            }

            double[][] vectors = objectMapper.readValue(
                    response.body(), double[][].class);

            float[] embedding = new float[vectors[0].length];
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) vectors[0][i];
            }
            return embedding;
        } catch (Exception e) {
            throw new IllegalStateException("embedding call failed", e);
        }
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
```
