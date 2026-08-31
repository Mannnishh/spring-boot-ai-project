# Day 5 — RAG Fundamentals

**Focus:** Understand retrieval-augmented generation end-to-end — the
concepts and the one piece of math the whole pipeline depends on —
*before* wiring any of it into Spring. Today is deliberately standalone:
no Spring, no Docker, no database. You'll write one plain-Java script
that calls the embeddings endpoint you built on
[Day 4](04-day4-groq-embeddings-hello-world.md), computes cosine
similarity by hand, and watch it put three text pairs in the right
order. Then you'll look at a naive chunking function and see exactly
why it's naive.

The real pipeline (embed → compare → retrieve → prompt) gets built on
Days 8–10 and 11–14. Those days assume you already know *why* the
pieces are shaped the way they are; today is where that why comes from.
It's genuinely new material, so it's a worked example + variation, with
the consolidated script in the answer key at the bottom.

## Concept primer

### The RAG pattern, end to end

A plain LLM answers from what was baked into its weights during
training. Two familiar attempts to get *your* documents into that
process both fail for this project:

- **Stuffing everything into the prompt** breaks down as soon as the
  corpus exceeds the model's context window, and even when it fits, the
  model gets no signal about which part of a long paste actually
  matters — and you pay tokens for every irrelevant sentence on every
  single request.
- **Fine-tuning** bakes knowledge into the model's parameters, which is
  expensive, needs a training run every time your documents change, and
  still doesn't guarantee the model will *reproduce* a fact rather than
  paraphrase it into something wrong.

RAG is a third shape: keep the knowledge *outside* the model, in a
searchable store, and only inject the relevant pieces into the prompt at
query time. The original
[Lewis et al. paper](https://arxiv.org/abs/2005.11401) describes it as
combining a *retriever* with a *sequence-to-sequence generator* so the
model can access and manipulate knowledge beyond what's stored in its
parameters — the parametric memory (the weights) is augmented with
non-parametric memory (your documents). Spring AI's
[RAG reference](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
names the practical benefits: it's how you get around an LLM's limits on
long-form content, factual accuracy, and context-awareness, by querying a
vector store and appending the retrieved documents to the prompt.

The full loop, which you'll narrate in the interview angle below:

1. **Embed** the user's question into a vector.
2. **Compare** that vector against the vectors of your stored chunks.
3. **Retrieve** the top-k most similar chunks.
4. **Prompt** the model with instructions + those chunks + the question.
5. **Answer** — grounded in retrieved text, with sources you can cite.

### What an embedding vector represents

An embedding turns text into a fixed-length list of floats — a point in
a high-dimensional space where *semantic* similarity shows up as
*geometric* closeness. "A $500 equipment stipend for remote employees"
and "remote workers get five hundred dollars for gear" are different
strings of characters but land near each other as vectors, because they
mean the same thing. Microsoft's
[RAG concepts page](https://learn.microsoft.com/en-us/azure/foundry/concepts/retrieval-augmented-generation)
describes embeddings as the numeric representation that lets a search
system match on meaning rather than on keywords. The vector itself isn't
human-readable — you never inspect individual coordinates for meaning.
What matters is only the *relationship* between vectors: distance and
direction. Day 4 pinned the concrete shape for this project — the
`sentence-transformers/all-MiniLM-L6-v2` model returns a
**384-dimensional** vector, and that dimension is fixed per model, not
per input.

### Why cosine similarity, not Euclidean distance

Given two vectors, you can measure "how similar" two ways:

- **Euclidean distance** is the straight-line length between the two
  points. It is sensitive to *magnitude*: a long, wordy paragraph and a
  two-word summary of the same idea sit far apart in raw distance even
  though they're about the same thing.
- **Cosine similarity** measures the *angle* between the vectors — the
  direction they point in — and ignores magnitude. The formula is the
  normalized dot product: `cos(a, b) = (a · b) / (‖a‖ ‖b‖)`, which is
  exactly how scikit-learn documents its
  [`cosine_similarity`](https://scikit-learn.org/stable/modules/generated/sklearn.metrics.pairwise.cosine_similarity.html)
  function (`K(X, Y) = <X, Y> / (||X|| * ||Y||)`), and its page notes
  it's equivalent to the plain dot product on L2-normalized data.

For text embeddings, direction is the signal you care about. A longer
text produces a larger-magnitude vector without necessarily being "more
similar" to anything, so magnitude is mostly noise. Cosine similarity
normalizes that away, returning a value in `[-1, 1]` where `1` is "same
direction" (maximally similar), `0` is "orthogonal" (no relation), and
`-1` is "opposite." Many embedding providers — including Hugging Face's
optional `normalize` flag from Day 4 — return unit-length vectors, in
which case cosine similarity and the raw dot product coincide, which is
why cosine is the de facto standard similarity metric for embeddings.

### Chunking trade-offs

A document is usually too long to embed as one blob: the model has a
context window, and a whole-document embedding averages an entire
multi-topic document into one point, which is a poor match for a query
about one specific sentence. So documents are split into chunks, and
each chunk gets its own embedding. The trade-off is the size of the
chunk:

- **Too small** → each chunk loses surrounding context. A question whose
  answer spans two adjacent sentences matches neither half, because
  neither chunk contains the complete thought.
- **Too large** → a chunk mixes several topics, so its embedding is a
  vague average of all of them; it matches *any* query weakly rather
  than the right query strongly, and you waste tokens feeding irrelevant
  content to the model.

Real chunkers are token-aware and sentence-aware rather than just
chopping a string at a character count. Spring AI's
[`TokenTextSplitter`](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/transformer/splitter/TokenTextSplitter.html)
"split[s] text into chunks of a target size in tokens," configured with
a chunk size, an encoding, and whether to keep separators — documented
in the
[ETL pipeline reference](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html).
LangChain's
[`RecursiveCharacterTextSplitter`](https://docs.langchain.com/oss/python/integrations/splitters/recursive_text_splitter)
takes the same idea one step further: it walks a list of separators
(default `["\n\n", "\n", " ", ""]`) so it tries to split on paragraph
boundaries first, then sentences, then words — keeping paragraphs,
sentences, and words together rather than cutting through them. Part 3
of the exercise shows what happens when you skip all of that and cut a
string at a fixed character count.

## Reference links

**RAG**

- [Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks — Lewis et al. (arXiv)](https://arxiv.org/abs/2005.11401) —
  the original RAG paper abstract: a retriever combined with a
  sequence-to-sequence generator, accessing knowledge beyond what's in
  the model's parameters.
- [Spring AI — Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html) —
  official reference; the `QuestionAnswerAdvisor`/`Advisor` API that
  queries a `VectorStore` and appends retrieved documents to the prompt.
- [Microsoft Foundry — Retrieval Augmented Generation (RAG)](https://learn.microsoft.com/en-us/azure/foundry/concepts/retrieval-augmented-generation) —
  a vendor explainer: RAG "combines search with large language models so
  responses are grounded in your data," covering grounding data, the
  index, and embeddings.

**Cosine similarity**

- [scikit-learn — `cosine_similarity`](https://scikit-learn.org/stable/modules/generated/sklearn.metrics.pairwise.cosine_similarity.html) —
  the precise definition `K(X, Y) = <X, Y> / (||X|| * ||Y||)` (the
  normalized dot product), plus a worked example.

**Text chunking**

- [Spring AI — `TokenTextSplitter` (Javadoc)](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/transformer/splitter/TokenTextSplitter.html) —
  "A `TextSplitter` that splits text into chunks of a target size in
  tokens," with `withChunkSize`, `withEncodingType`, `withKeepSeparator`,
  `withMaxNumChunks`, and `withMinChunkLengthToEmbed`.
- [Spring AI — ETL Pipeline](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html) —
  `TokenTextSplitter` as a `TextSplitter` implementation splitting by
  token count, with configurable encodings (`CL100K_BASE`, `P50K_BASE`,
  `O200K_BASE`).
- [LangChain — `RecursiveCharacterTextSplitter`](https://docs.langchain.com/oss/python/integrations/splitters/recursive_text_splitter) —
  "the recommended one for generic text"; splits on a separator list
  (`["\n\n", "\n", " ", ""]`) to keep paragraphs/sentences/words
  together, with `chunk_size` and `chunk_overlap`.

**Prompt construction**

- [OpenAI — Prompt Engineering guide](https://developers.openai.com/api/docs/guides/prompt-engineering) —
  "the process of writing effective instructions for a model" to get
  consistent output.
- [Anthropic — Prompt Engineering overview](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/overview) —
  when to prompt-engineer, and the "define success criteria and evaluate
  empirically" discipline before touching prompt text.

## Exercise (worked example + variation — standalone, not yet wired into Spring)

No Spring, no build tool, no project directory. Make a scratch folder,
put the file there, and run `javac *.java && java Main`. The script
reuses the **Day 4 embeddings call pattern** — the same Hugging Face
Inference endpoint and model
(`sentence-transformers/all-MiniLM-L6-v2`), the same
`Authorization: Bearer` header, the same array-of-arrays response shape —
see
[Day 4, steps 3–4](04-day4-groq-embeddings-hello-world.md) for the full
walkthrough. The only differences: the API key is read from the
`HF_API_KEY` environment variable (`System.getenv`), and instead of
Jackson (which Day 4 had because Spring Boot Web pulls it in), the
response is parsed with a few lines of dependency-free string handling so
this compiles with nothing but the JDK.

Set the key first, same one Day 4 used:

```bash
export HF_API_KEY=hf_...   # your fine-grained Inference token from Day 4
```

### Part 1 — Worked example: cosine similarity by hand

Read this through and get it running before changing anything. This is
the whole file, with two hardcoded strings:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {

    public static void main(String[] args) throws Exception {
        double[] a = embed("Remote employees receive a $500 equipment stipend.");
        double[] b = embed("Remote employees receive a $500 equipment allowance.");

        System.out.println("a.length = " + a.length); // 384, per Day 4
        System.out.printf("cosine = %.4f%n", cosineSimilarity(a, b));
    }

    // --- cosine similarity, implemented directly (no library) ---

    static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "vector lengths differ: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0; // a zero vector has no direction; cosine is undefined
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // --- Day 4 embeddings call, dependency-free ---

    static double[] embed(String text) throws Exception {
        String url = "https://router.huggingface.co/hf-inference/models/"
                + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

        String apiKey = System.getenv("HF_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "HF_API_KEY environment variable is not set");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"inputs\":" + jsonString(text) + "}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("embedding call failed: HTTP "
                    + response.statusCode() + " " + response.body());
        }
        return parseEmbedding(response.body());
    }

    static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    static double[] parseEmbedding(String body) {
        // HF returns an array of arrays: [[0.1, -0.02, ...]].
        String flat = body.strip();
        if (!flat.startsWith("[[") || !flat.endsWith("]]")) {
            throw new IllegalStateException("unexpected response: " + flat);
        }
        String inner = flat.substring(2, flat.length() - 2).trim();
        if (inner.isEmpty()) {
            return new double[0];
        }
        String[] parts = inner.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Double.parseDouble(parts[i].trim());
        }
        return vector;
    }
}
```

The formula is the only part that matters and it's spelled out in the
three running sums inside `cosineSimilarity`: **dot product** (`dot`),
the two **norms** (`normA`, `normB`, via the square root of the sum of
squares), and the division `dot / (√normA · √normB)`. That is
`cos(a,b) = (a·b) / (‖a‖‖b‖)` — the same expression documented on the
[scikit-learn page](https://scikit-learn.org/stable/modules/generated/sklearn.metrics.pairwise.cosine_similarity.html).
Nothing is hidden in a library call.

Run it:

```bash
javac *.java && java Main
```

The two strings are near-duplicates, so expect a value close to `1`
(something like `0.9x`). The exact number varies per model version and
run — do **not** memorize it. The lesson is the *ordering* across pairs,
which is what the variation task is for.

### Part 2 — Variation: three pairs, recorded scores

Now replace the two hardcoded strings in `main` with three pairs **you
write yourself**:

1. **Near-duplicate** — same sentence with a tiny change (one word
   swapped for a synonym, a typo, an added comma).
2. **Same topic, different wording** — a real paraphrase: different
   sentence structure and vocabulary, same meaning.
3. **Unrelated** — a completely different subject.

Restructure `main` so it loops over the pairs and prints one score per
pair. The rest of the file — `cosineSimilarity`, `embed`, `parseEmbedding`
— does not change. Here's the shape (the pairs are yours to write):

```java
String[][] pairs = {
        {"<near-duplicate A>", "<near-duplicate B>"},
        {"<paraphrase A>", "<paraphrase B>"},
        {"<unrelated A>", "<unrelated B>"},
};

for (String[] pair : pairs) {
    double[] a = embed(pair[0]);
    double[] b = embed(pair[1]);
    System.out.printf("%.4f  %s%n", cosineSimilarity(a, b), pair[1]);
}
```

Record the three scores in a scratch note. What you should observe, in
order: the **near-duplicate scores highest**, the **unrelated pair
scores lowest**, and the **paraphrase sits in between** — usually
well above the unrelated pair but below the near-duplicate. That
ordering *is* the whole point: it's the evidence that the embeddings
are capturing meaning, not spelling, and it's exactly the property the
retrieval step on Days 8–10 and 11–14 leans on. (If your paraphrase
scores suspiciously close to `0`, check that your two strings are
actually the same topic — "different wording" should mean different
wording, not a different fact.)

### Part 3 — Worked example: naive fixed-size chunking (no exercise)

Here is the simplest possible chunker — cut a string into fixed-width
slices by *character* count:

```java
import java.util.ArrayList;
import java.util.List;

static List<String> chunk(String text, int chunkSize) {
    List<String> chunks = new ArrayList<>();
    for (int start = 0; start < text.length(); start += chunkSize) {
        int end = Math.min(start + chunkSize, text.length());
        chunks.add(text.substring(start, end));
    }
    return chunks;
}
```

Feed it the first two sentences of the remote-work policy, with a chunk
size of 20 characters:

```
"Remote employees receive a $500 equipment stipend. The reimbursement form is due within 30 days."
```

and it produces (verify with `chunk(paragraph, 20)`):

```
[Remote employees rec]
[eive a $500 equipmen]
[t stipend. The reimb]
[ursement form is due]
[ within 30 days.]
```

The breakage is visible right there, twice over:

- **Mid-word cuts.** The first chunk ends at `"rec"` and the next begins
  `"eive"`. A chunk starting or ending inside a word embeds a
  half-token; the model that tokenized `"receive"` for its embedding
  never sees `"receive"` as a whole.
- **Mid-sentence cuts.** Chunk 2 ends mid-phrase and chunk 3 picks up
  `"t stipend. The reimb..."`. A question like *"what is the equipment
  stipend?"* now has its answer split across two chunks: the phrase
  "equipment stipend" straddles a boundary, so **neither** chunk
  contains the complete fact the question targets. Retrieval compares
  the question vector against each chunk's vector individually — a fact
  cut in half matches both halves weakly and neither strongly.

That's the entire argument for the token-aware, separator-aware
chunkers linked above: they size chunks by *tokens* (not characters) and
try to break at paragraph/sentence/word boundaries, which is the
`TokenTextSplitter` / `RecursiveCharacterTextSplitter` behavior. This
naive function is deliberately left naive — it exists so the failure
mode is obvious before Day 8–10 replaces it with the real thing.

## Checkpoint

You're done with Day 5 when:

1. `javac *.java && java Main` compiles and runs with no errors, and
   prints the vector length (`384`) at least once.
2. The script prints **three** cosine similarity scores, one per pair,
   and the **near-duplicate pair scores highest** while the **unrelated
   pair scores lowest**.
3. `chunk(paragraph, 20)` runs on the policy paragraph and you can point
   at (in the output) one chunk boundary that lands mid-word and one
   that lands mid-sentence.

If the ordering comes out wrong, re-read your three pairs before
touching the formula: the script is doing the right math, and a
wrongly-ordered result almost always means one of the "unrelated"
sentences actually shares vocabulary with the others, or the
"near-duplicate" pair isn't actually a near-duplicate.

## Interview angle

*"Walk me through what happens when a user asks your app a question."*

After today you can narrate the whole pipeline conceptually, before a
line of it exists in the Spring project. The shape of a good answer:

1. **Embed** — the question is turned into the same 384-dimensional
   vector space as the stored chunks, via the same embedding model.
2. **Compare** — that vector is compared against every stored chunk
   vector using cosine similarity (angle, not raw distance, so a long
   chunk and a short chunk with the same meaning still match).
3. **Retrieve** — the top-k most similar chunks are pulled out, carrying
   their source document names with them.
4. **Prompt** — those chunks are injected into the prompt with an
   instruction to answer only from the provided context and cite the
   sources, then the assembled prompt goes to the LLM (Groq, from
   Day 4).
5. **Answer** — the model answers grounded in the retrieved text, and
   the endpoint returns the answer alongside which documents were
   cited.

The follow-up almost always asks *why* those choices — and that's the
rest of today's primer: RAG instead of stuffing the prompt or
fine-tuning, cosine instead of Euclidean distance, and chunk-size
trade-offs. Being able to defend each of those three in one sentence is
the difference between "I wired up a vector DB" and "I understand the
pipeline I built."

## Answer Key (attempt it yourself first!)

Only read this after your script prints three scores in the right order.
This is the consolidated, copy-paste version: the by-hand cosine
similarity, the naive chunking function, and the three-pair variation
(plus the chunking demo) in one file.

**`Main.java`**

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        String[][] pairs = {
                {"Remote employees receive a $500 equipment stipend.",
                 "Remote employees receive a $500 equipment allowance."},
                {"Remote employees receive a $500 equipment stipend.",
                 "Staff working from home get five hundred dollars for gear."},
                {"Remote employees receive a $500 equipment stipend.",
                 "The server uptime is monitored every five minutes."},
        };

        for (String[] pair : pairs) {
            double[] a = embed(pair[0]);
            double[] b = embed(pair[1]);
            System.out.printf("%.4f  %s%n", cosineSimilarity(a, b), pair[1]);
        }

        String paragraph = "Remote employees receive a $500 equipment stipend. "
                + "The reimbursement form is due within 30 days.";
        System.out.println();
        System.out.println("chunks(20): " + chunk(paragraph, 20));
    }

    // --- cosine similarity, implemented directly (no library) ---

    static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "vector lengths differ: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0; // a zero vector has no direction; cosine is undefined
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // --- naive fixed-size chunking (characters, not tokens) ---

    static List<String> chunk(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += chunkSize) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
        }
        return chunks;
    }

    // --- Day 4 embeddings call, dependency-free ---

    static double[] embed(String text) throws Exception {
        String url = "https://router.huggingface.co/hf-inference/models/"
                + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

        String apiKey = System.getenv("HF_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "HF_API_KEY environment variable is not set");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"inputs\":" + jsonString(text) + "}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("embedding call failed: HTTP "
                    + response.statusCode() + " " + response.body());
        }
        return parseEmbedding(response.body());
    }

    static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    static double[] parseEmbedding(String body) {
        // HF returns an array of arrays: [[0.1, -0.02, ...]].
        String flat = body.strip();
        if (!flat.startsWith("[[") || !flat.endsWith("]]")) {
            throw new IllegalStateException("unexpected response: " + flat);
        }
        String inner = flat.substring(2, flat.length() - 2).trim();
        if (inner.isEmpty()) {
            return new double[0];
        }
        String[] parts = inner.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Double.parseDouble(parts[i].trim());
        }
        return vector;
    }
}
```
