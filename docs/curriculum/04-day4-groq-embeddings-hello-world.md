# Day 4 — Groq + Embeddings Provider Hello World

**Focus:** Prove end-to-end connectivity to two external AI providers before
building any real pipeline on top of them. First, point Spring AI's OpenAI
client at Groq and get a real chat completion back from a `@RestController`
endpoint. Second, call a Hugging Face Inference embeddings model and get a
real embedding vector back from a second endpoint — plus the `.env`-based
secrets hygiene that both calls depend on.

This day is deliberately small: two endpoints, two external calls, one
config file. But it de-risks everything that follows. Days 8–10 (embedding
pipeline) and 11–14 (`/ask`) both assume these two calls already work, so
proving them here — with real keys, real model names, and a real curl
round-trip — means later days fail for *pipeline* reasons, not *connectivity*
reasons. Today's material is genuinely new (external AI provider wiring), so
it's a worked example + variation, with the complete files consolidated for
reference at the bottom.

## Concept primer

### Groq: OpenAI-compatible chat, and nothing but chat

Groq runs LLM inference on its own custom hardware and exposes it through an
API that is, in its own words, "mostly OpenAI compatible" — the
[OpenAI compatibility page](https://console.groq.com/docs/openai) tells you
to point an existing OpenAI client at the base URL
`https://api.groq.com/openai/v1` instead of OpenAI's endpoint. The practical
upside for this project: Spring AI has a ready-made OpenAI client, so you
don't need any Groq-specific SDK. You configure the OpenAI client to talk to
Groq's URL and Groq's model names, and everything else is unchanged.

The one hard boundary to internalize today: **Groq has no embeddings
endpoint.** Its
[API reference](https://console.groq.com/docs/api-reference) documents chat
completions and audio transcription/translation — text generation, not
vector generation. Embeddings are a *different* product from a different
provider, and that's the whole reason today touches two vendors instead of
one.

### Why embeddings need a second provider

An embedding turns text into a fixed-length vector of floats (a list of
numbers). Day 5 explains *why* those vectors are useful; today you only need
to prove you can produce one. Because Groq doesn't offer embeddings, the
curriculum uses **Hugging Face Inference** for that half. Specifically, the
model `sentence-transformers/all-MiniLM-L6-v2`, whose
[model card](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)
states it maps text to a **384-dimensional** dense vector. That dimension
number matters: it's a fixed property of the model, and every embedding you
store on Day 8–10 has to match it. Today's variation task proves the point —
change the input text, and the vector changes; change nothing about the
model, and the dimension count stays 384.

The third piece is how the API keys get into the app without being committed
to git. You already created a `.env` and gitignored it on Day 1, and Day 3
used it for Postgres credentials via Docker Compose. Today Spring Boot reads
that same file *directly* through its native externalized-configuration
mechanism — no third-party `.env` library needed. That mechanism is the
subject of step 2 below.

## Reference links

**Groq**

- [Groq — OpenAI Compatibility](https://console.groq.com/docs/openai) —
  the "mostly OpenAI compatible" API; point the client at
  `https://api.groq.com/openai/v1`.
- [Groq — API Reference](https://console.groq.com/docs/api-reference) —
  chat completions and audio endpoints only; there is **no embeddings
  endpoint** anywhere in it.
- [Groq — Supported Models](https://console.groq.com/docs/models) —
  the live list of current model IDs (use this for the variation task;
  model IDs churn, so check here rather than trusting an older doc).

**Spring AI (OpenAI-compatible client)**

- [Spring AI — OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html) —
  the official reference for the OpenAI-compatible client and its
  configuration properties: `spring.ai.openai.base-url`,
  `spring.ai.openai.api-key`, and `spring.ai.openai.chat.model` (plus
  chat-specific `spring.ai.openai.chat.base-url` /
  `spring.ai.openai.chat.api-key` overrides).
- [Spring AI — Groq Chat](https://docs.spring.io/spring-ai/reference/api/chat/groq-chat.html) —
  Spring AI's own page for running Groq through the OpenAI client, using
  the same properties above. *Caution:* this page currently shows stale
  Groq model names (e.g. `llama3-8b-8192`); use the live IDs from the
  [Groq models page](https://console.groq.com/docs/models) instead.

**Hugging Face Inference (embeddings)**

- [Hugging Face Inference Providers — Feature Extraction](https://huggingface.co/docs/inference-providers/tasks/feature-extraction) —
  "feature extraction" is HF's name for the embeddings task. Documents the
  request shape (`inputs` as a string or array, optional `normalize`), the
  `Authorization: Bearer hf_...` header, and the "array of arrays" response.
- [all-MiniLM-L6-v2 model card](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) —
  the model used today; maps text to a **384-dimensional** vector space.
- [all-MiniLM-L6-v2 discussion #116](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/discussions/116) —
  confirms the current serverless endpoint path
  (`https://router.huggingface.co/hf-inference/models/{model}/pipeline/feature-extraction`)
  and that the legacy `api-inference.huggingface.co` host is decommissioned.

**Spring Boot configuration (`.env`)**

- [Spring Boot — Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html) —
  documents `spring.config.import` and the "Importing Extensionless Files"
  mechanism (the `file:...myconfig[.yaml]` hint form) that loads `.env`
  natively.
- [Spring Boot issue #24229](https://github.com/spring-projects/spring-boot/issues/24229) —
  why a bare `.env` import fails ("File extension is not known to any
  PropertySourceLoader") and therefore needs the `[.properties]` hint.

## Java pattern note

**Java pattern note:** the Spring AI chat client you inject into the
controller is the same **Dependency Injection / Inversion of Control**
pattern from [Day 2, section 7](02-day2-java-oop-refresher.md) — the
controller declares what it needs (the chat client) as a constructor
parameter instead of constructing or locating it, and Spring's container
supplies it. It's the same shape as Day 3's repository injection, just with
a different collaborator: instead of a `DocumentRepository`, the controller
depends on an *abstraction* over an external HTTP API. Notice that the
controller never mentions Groq's URL, an API key, or an HTTP client for the
chat call — those details live in configuration and inside Spring AI's
client bean. That's DIP: the controller depends on the abstraction, not on
the concrete vendor wiring.

## Exercise (worked example + variation — genuinely new territory)

Do today's work on a new feature branch, per Day 1's workflow:

```bash
git checkout -b feature/ai-hello-world
```

### 1. Add the Spring AI OpenAI starter dependency

You need Spring AI's OpenAI starter. The exact artifact and BOM version you
pin depend on your Spring Boot version, so get them from the page that
documents running Groq through the OpenAI client — the
[Spring AI Groq Chat reference](https://docs.spring.io/spring-ai/reference/api/chat/groq-chat.html)
lists the dependency alongside the property names used below. (In short:
add the Spring AI BOM to your build's dependency management, then the
`spring-ai-starter-model-openai` artifact — confirm the current version
string on that page, since Spring AI versioning tracks your Spring Boot
version.) No Groq-specific SDK and no third-party `.env` library are needed.

### 2. Configure `application.yml` — Groq + `.env`

Spring AI's OpenAI client is pointed at Groq entirely through properties.
Create or extend `src/main/resources/application.yml`:

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
  ai:
    openai:
      base-url: https://api.groq.com/openai/v1
      api-key: ${GROQ_API_KEY}
      chat:
        model: llama-3.3-70b-versatile
```

Walk through each piece:

- **`spring.config.import: optional:file:.env[.properties]`** is Spring
  Boot's native way to read a `.env` file. Spring Boot does *not* read
  `.env` files by default — a bare `.env` fails with "File extension is not
  known to any PropertySourceLoader" (that's the whole of
  [issue #24229](https://github.com/spring-projects/spring-boot/issues/24229)).
  The `[.properties]` hint tells Spring to parse the file as Java
  properties (plain `KEY=value` lines), and `optional:` lets the app start
  even if the file is absent — though the `${GROQ_API_KEY}` placeholder
  below will then fail to resolve. This is documented under "Importing
  Extensionless Files" in the
  [Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
  reference.
- **`spring.ai.openai.base-url`** is the property that points the OpenAI
  client at a non-OpenAI, OpenAI-compatible endpoint. Its default is
  `https://api.openai.com`; for Groq it must be
  `https://api.groq.com/openai/v1`, which is the base URL Groq's
  [OpenAI compatibility page](https://console.groq.com/docs/openai)
  specifies. Both this property and the two below are documented on the
  [Spring AI OpenAI Chat reference](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html).
- **`spring.ai.openai.api-key: ${GROQ_API_KEY}`** reads the Groq key from
  the environment — Spring resolves `${GROQ_API_KEY}` from whatever the
  imported `.env` (or a real environment variable) supplied. The key is
  never written into `application.yml`.
- **`spring.ai.openai.chat.model`** names the model. `llama-3.3-70b-versatile`
  is a current Groq model ID per the
  [Groq models page](https://console.groq.com/docs/models); you'll swap it
  in the variation task below. Chat auto-configuration is on by default
  (`spring.ai.model.chat=openai`, per the
  [same OpenAI Chat reference](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
  above — "It is enabled by default"), so no further toggle is required.

Then add the two keys to your `.env` (the same gitignored file Day 1 created
and Day 3 extended — it now accumulates all the project's secrets):

```
GROQ_API_KEY=<paste your Groq key>
HF_API_KEY=<paste your Hugging Face token>
```

The Hugging Face token must be a fine-grained token with **Inference
Providers** (serverless inference) permission, per the
[Feature Extraction docs](https://huggingface.co/docs/inference-providers/tasks/feature-extraction).
Because `.env` is already gitignored, none of this is committed — that's the
whole interview angle at the bottom.

### 3. The hello-world controller (chat completion)

This endpoint calls the chat model and returns the raw completion string. It
is the minimal possible round-trip: no RAG, no prompt template, just "does
the wire work."

```java
package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloAiController {

    private final ChatClient chatClient;

    public HelloAiController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/hello-groq")
    public String helloGroq() {
        return chatClient.prompt("Say hello in one sentence.")
                .call()
                .content();
    }
}
```

Two things to notice. First, the constructor takes a `ChatClient.Builder`
bean — Spring AI auto-configures one, and building a `ChatClient` from it
picks up the `spring.ai.openai.*` properties you just set. Second, nowhere
in this class is there a Groq URL, an API key, or an HTTP client: the client
abstraction hides all of it, which is the DI/DIP point from the pattern note
above. If the fluent `ChatClient` API surface looks different in your Spring
AI version, the
[OpenAI Chat reference](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
is the authoritative source for the current call syntax.

### 4. The hello-world embeddings call

Groq can't embed, so this endpoint calls Hugging Face Inference directly
with a raw HTTP client. Spring AI does not have a ready-made Hugging Face
embeddings integration in the verified reference set, so raw HTTP is the
right call here — and it keeps the dependency surface at zero (Java's
built-in `java.net.http.HttpClient` plus Jackson, which Spring Boot Web
already pulled in).

Extend the controller:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;

// fields
private final String hfApiKey;
private final HttpClient http = HttpClient.newHttpClient();
private final ObjectMapper objectMapper = new ObjectMapper();

// add the key to the constructor
public HelloAiController(ChatClient.Builder chatClientBuilder,
                         @Value("${HF_API_KEY}") String hfApiKey) {
    this.chatClient = chatClientBuilder.build();
    this.hfApiKey = hfApiKey;
}

@GetMapping("/hello-embedding")
public float[] helloEmbedding() throws Exception {
    String url = "https://router.huggingface.co/hf-inference/models/"
            + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + hfApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"inputs\":\"hello world\"}"))
            .build();

    HttpResponse<String> response = http.send(
            request, HttpResponse.BodyHandlers.ofString());

    // HF returns an array of arrays: [[0.1, -0.02, ...]] — one inner
    // array per input string. Take the first (only) vector.
    double[][] vectors = objectMapper.readValue(
            response.body(), double[][].class);

    float[] embedding = new float[vectors[0].length];
    for (int i = 0; i < embedding.length; i++) {
        embedding[i] = (float) vectors[0][i];
    }
    return embedding;
}
```

What each part does:

- **Endpoint path.** `POST
  https://router.huggingface.co/hf-inference/models/{model_id}/pipeline/feature-extraction`
  is the current serverless endpoint, with the model id
  `sentence-transformers/all-MiniLM-L6-v2`. The legacy host
  `api-inference.huggingface.co` is decommissioned — don't use it (see the
  [discussion thread](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/discussions/116)
  for confirmation of the router path).
- **Auth.** The `Authorization: Bearer hf_...` header carries the fine-grained
  token from `${HF_API_KEY}`, per the
  [Feature Extraction docs](https://huggingface.co/docs/inference-providers/tasks/feature-extraction).
- **Request body.** `{"inputs": "hello world"}` — `inputs` takes a string or
  an array of strings; the response is an array of arrays (one embedding
  vector per input string).
- **Response.** The code parses that array-of-arrays and returns the first
  vector as a `float[]`. Spring serializes it to a JSON array of floats —
  exactly what the checkpoint below checks. The **dimension count is the
  length of that array**, which for `all-MiniLM-L6-v2` is **384** (its
  [model card](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)
  is the source for that number).

### 5. Variation tasks

**Variation A — swap the Groq model.** In `application.yml`, change
`spring.ai.openai.chat.model` from `llama-3.3-70b-versatile` to
`llama-3.1-8b-instant` (another current ID on the
[Groq models page](https://console.groq.com/docs/models) — the smaller
"instant" model), restart, and hit `/hello-groq` again. The endpoint code
does not change; only one config line changes. That's the lesson to
internalize: a model swap is configuration, not code.

**Variation B — change the embedding input, confirm the dimension is
constant.** In `helloEmbedding()`, change `"hello world"` to any other text
(a whole sentence, a single word, a different language). Restart and hit
`/hello-embedding` again. The *values* inside the returned array change
(different text, different vector) — but the array **length stays 384**.
The dimension is fixed per model; it does not depend on the input. That
fixed dimension is what Day 8–10's database column will be sized to, which
is why proving it now, on real requests, matters.

## Checkpoint

1. `./mvnw spring-boot:run` (or your IDE's run config) starts with no
   "could not resolve placeholder" errors — proof the `.env` import and both
   keys resolved.
2. `curl localhost:8080/hello-groq` returns a real natural-language
   completion (a sentence, not an error and not a template).
3. `curl localhost:8080/hello-embedding` returns a JSON array of floats with
   **384** elements. If you have `jq` installed,
   `curl -s localhost:8080/hello-embedding | jq length` prints `384`; if
   not, eyeball it — the point is "a large flat array of numbers," not a
   count you memorized.
4. Commit on the feature branch with a Conventional Commits message (e.g.
   `feat: wire Groq chat and Hugging Face embeddings hello world`), push it,
   and merge via a pull request per Day 1.

## Interview angle

*"How do you handle secrets/API keys across environments?"*

After today you have a concrete, first-hand answer, not a definition. The
shape of it: secrets are **never hardcoded** in `application.yml` or source —
`application.yml` only contains `${GROQ_API_KEY}` / `${HF_API_KEY}`
placeholders. The real values live in a `.env` file that's been gitignored
since Day 1, so they never reach version control. At runtime, Spring reads
them through its native externalized configuration — the same
`spring.config.import` mechanism — which also means a *different* value can
be supplied per environment (a real environment variable in CI, a secret
manager in production) without changing a line of code or config. Worth
naming the two failure modes too: a key committed to git is a secret that
must be assumed leaked and rotated, and a key hardcoded in config can't vary
per environment — both are what this setup avoids.

## Complete files (reference)

This is a worked example, not a guided-TODO exercise — every line above was
already shown, not held back. This section only consolidates the pieces
built incrementally in steps 2–4 into complete, copy-paste-able files, so
you don't have to hand-assemble the diffs yourself.

**`application.yml`**

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
  ai:
    openai:
      base-url: https://api.groq.com/openai/v1
      api-key: ${GROQ_API_KEY}
      chat:
        model: llama-3.3-70b-versatile
```

**`HelloAiController.java`**

```java
package com.example.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class HelloAiController {

    private final ChatClient chatClient;
    private final String hfApiKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HelloAiController(ChatClient.Builder chatClientBuilder,
                             @Value("${HF_API_KEY}") String hfApiKey) {
        this.chatClient = chatClientBuilder.build();
        this.hfApiKey = hfApiKey;
    }

    @GetMapping("/hello-groq")
    public String helloGroq() {
        return chatClient.prompt("Say hello in one sentence.")
                .call()
                .content();
    }

    @GetMapping("/hello-embedding")
    public float[] helloEmbedding() throws Exception {
        String url = "https://router.huggingface.co/hf-inference/models/"
                + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + hfApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"inputs\":\"hello world\"}"))
                .build();

        HttpResponse<String> response = http.send(
                request, HttpResponse.BodyHandlers.ofString());

        double[][] vectors = objectMapper.readValue(
                response.body(), double[][].class);

        float[] embedding = new float[vectors[0].length];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) vectors[0][i];
        }
        return embedding;
    }
}
```

**`.env`** (gitignored — real values, never committed)

```
GROQ_API_KEY=<paste your Groq key>
HF_API_KEY=<paste your Hugging Face token>
```
