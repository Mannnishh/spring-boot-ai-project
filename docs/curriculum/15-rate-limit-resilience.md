# Day 15 — Rate-Limit / Retry Resilience

**Focus:** Make the `/ask` endpoint survive the thing that *will* eventually
happen to a free-tier AI integration: the provider says "slow down." A `429
Too Many Requests` (or a transient `5xx`, or a dropped connection) needs
graceful backoff and a clean error response — not a stack trace leaking to
the caller, and not a crash. Today wraps Day 11–14's two external calls
(Groq via `ChatClient`, Hugging Face via raw `HttpClient`) with
retry-with-backoff, and turns whatever survives the retries into a `503`
instead of a `500`.

One thing to set expectations before you start: **the two calls don't need
the same fix.** The Groq call already runs through a retry loop you didn't
write — Spring AI 2.x talks to OpenAI-compatible APIs through the official
OpenAI Java SDK, which retries rate limits and server errors on its own and
only needs *tuning*. The Hugging Face call is raw
`java.net.http.HttpClient` with nothing wrapping it, so that half needs a
decorator you build by hand. Recognizing which is which — instead of
reflexively writing a wrapper for both — is most of today's lesson.

## Concept primer

### Why free-tier APIs throttle, and what a 429 actually means

Groq's own framing, from its rate-limits documentation: "Rate limits help
ensure service stability, fair access, and protection against misuse so
that we can serve reliable and fast inference for all." Free and low
tiers get tight limits precisely because they share the same underlying
hardware as everyone else. Groq measures limits across several dimensions
— RPM/RPD (requests per minute/day), TPM/TPD (tokens per minute/day),
ITPM/OTPM (input/output tokens per minute), plus audio-specific ones — and
the free tier's numbers are small: some models allow as few as **10 RPM
and 1.2K TPM** on the free tier, scaling up on paid plans. Hugging Face
Inference has its own quotas on the embeddings side, for the same reason.

When you exceed a limit, Groq's API "returns a `429 Too Many Requests` HTTP
status code," along with headers that describe exactly how close you were
and how long to wait: `x-ratelimit-remaining-requests`,
`x-ratelimit-remaining-tokens`, `x-ratelimit-reset-requests`,
`x-ratelimit-reset-tokens`, and — only present on an actual 429 —
`retry-after`, in seconds.

A `429` is not an error in the sense a `400` or `401` is. Nothing is wrong
with your request; the request would very likely succeed if you sent it a
few seconds later. That's the whole distinction this day is built around:
**some failures are worth retrying (transient — a 429, a 5xx, a dropped
connection) and some aren't (non-transient — bad API key, malformed
request; retrying won't fix either).** Treating every failure the same way
— crash, or worse, silently return a bad answer — throws away that
distinction. The fix is retry-with-backoff for the transient kind, and a
clean, honest error response once retries are exhausted for either kind.

### Two calls, two different starting points

**Groq (`ChatClient`) already has a retry loop.** Since 2.0, Spring AI's
OpenAI module — the `spring-ai-starter-model-openai` you added on Day 4 —
is built on the official
[OpenAI Java SDK](https://github.com/openai/openai-java) (`openai-java`),
and every HTTP call goes through that SDK's client. The SDK retries on its
own, with short exponential backoff, and it classifies failures the way
you'd want: **connection errors, `408`, `409`, `429`, and `5xx` are
retried; everything else (`400`, `401`, `403`, `404`, `422`) fails
immediately.** Spring AI passes two knobs through to it —
`spring.ai.openai.max-retries` (default `3`) and `spring.ai.openai.timeout`
(default 60s). You've been getting this since Day 4 without knowing it.
Once retries run out, the SDK throws its own unchecked exceptions, all
under one base class, `com.openai.errors.OpenAIException`:
`RateLimitException` for a 429, `InternalServerException` for a 5xx,
`OpenAIIoException` for a network failure, `UnauthorizedException` for a
bad key, and so on. Today's job on the Groq side is **not** "write a
wrapper" — it's "know the wrapper is there, tune it, and catch what
survives it."

**A trap worth knowing about, because you'll hit it searching the web:**
Spring AI 1.x wired its OpenAI client through Spring AI's *own* retry
module, configured with `spring.ai.retry.*` properties and throwing
`TransientAiException` / `NonTransientAiException`. Most blog posts and
Stack Overflow answers still describe that setup, and even the current
OpenAI Chat reference page still shows a `spring.ai.retry` table. But in
2.x the OpenAI chat model never calls that retry module. `spring.ai.retry.*`
has no effect on Groq calls, and `org.springframework.ai.retry` isn't even
on your classpath (it's only an *optional* dependency of the
autoconfiguration, so the OpenAI starter doesn't bring it in). The
authoritative answer is the source:
[`OpenAiChatAutoConfiguration`](https://github.com/spring-projects/spring-ai/blob/v2.0.1/auto-configurations/models/spring-ai-autoconfigure-model-openai/src/main/java/org/springframework/ai/model/openai/autoconfigure/OpenAiChatAutoConfiguration.java)
hands `timeout` and `max-retries` straight to the SDK client, and nothing
else.

**Hugging Face (raw `HttpClient`) has no decorator at all.** Day 4/11–14's
`embed()` method is hand-rolled `java.net.http.HttpClient` — no Spring AI,
no framework retry machinery, nothing between your code and the socket.
Today's job here **is** "write a wrapper": a small class that calls the
same HTTP request, classifies the response the same way the OpenAI SDK
just showed you (transient vs. non-transient), and retries the transient
kind with backoff.

### Spring Retry is gone; here's what replaced it

The two obvious candidates for "the retry library" are Spring Retry and
Resilience4j. Checked live today: **Spring Retry's GitHub repository is
archived** — its own README banner reads "⚠️ Project Status: Archived.
This project has been superseded by Spring Framework 7 and is now
archived. Please migrate to Spring Framework's resilience features." So
it's disqualified as "the current standard" by its own maintainers'
notice, not by guesswork.

Its replacement is **Spring Framework 7's core resilience module**
(`spring-context`, `org.springframework.resilience.annotation` package) —
`@Retryable`, `@EnableResilientMethods`, and a programmatic
`RetryTemplate` in `org.springframework.core.retry`. It shipped with
Spring Framework 7.0 / Spring Boot 4.0 (GA November 2025). In other words:
the framework you're already running on now ships the retry engine
natively — no third-party dependency to add, because it's already on the
classpath transitively via `spring-boot-starter-web`. That's why today's
Hugging Face wrapper uses `@Retryable` rather than a third dependency.

**Resilience4j is still a legitimate, actively-maintained option** — its
Spring Boot 3 integration guide (`resilience4j-spring-boot3`) is current
and well documented, and it's the right choice if you need circuit
breakers, bulkheads, or rate limiters *beyond* simple retry (patterns core
Spring doesn't provide). It's not used here because, for retry alone, it
would be an extra dependency doing a job the framework — and Spring AI
itself — already does natively.

## Reference links

**Groq**

- [Groq — Rate Limits](https://console.groq.com/docs/rate-limits) —
  the `429` status code, `x-ratelimit-*` / `retry-after` headers, and the
  RPM/RPD/TPM/TPD/ITPM/OTPM dimensions; confirms free-tier limits are
  materially tighter than paid tiers.

**The OpenAI Java SDK's own retry (already wrapping the Groq `ChatClient` call)**

- [Spring AI — OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html) —
  already cited on Day 4 for the client config. It states that "starting
  from version 2.0.0-M5, Spring AI uses the official openai-java SDK under
  the hood for all OpenAI models," and its connection-properties table
  lists `spring.ai.openai.max-retries` (default `3`) and
  `spring.ai.openai.timeout` (default 60 sec). *(The same page still
  carries a `spring.ai.retry.*` table left over from 1.x. As explained in
  the primer, it doesn't apply to the OpenAI chat model in 2.x, so ignore
  it.)*
- [OpenAI Java SDK — README](https://github.com/openai/openai-java) —
  the "Retries" section (which errors get retried: connection errors,
  408, 409, 429, 5xx) and the "Error handling" section (the
  status-code-to-exception table: `RateLimitException`,
  `InternalServerException`, `OpenAIIoException`, all under
  `OpenAIException`).
- [`AbstractOpenAiProperties.java`](https://github.com/spring-projects/spring-ai/blob/v2.0.1/auto-configurations/models/spring-ai-autoconfigure-model-openai/src/main/java/org/springframework/ai/model/openai/autoconfigure/AbstractOpenAiProperties.java) —
  primary-source defaults (pinned to release `v2.0.1`):
  `DEFAULT_MAX_RETRIES = 3`, `DEFAULT_TIMEOUT = 60s`.
- [`OpenAiChatAutoConfiguration.java`](https://github.com/spring-projects/spring-ai/blob/v2.0.1/auto-configurations/models/spring-ai-autoconfigure-model-openai/src/main/java/org/springframework/ai/model/openai/autoconfigure/OpenAiChatAutoConfiguration.java) —
  where those two properties are handed to the SDK client
  (`OpenAiSetup.setupSyncClient(..., getTimeout(), getMaxRetries(), ...)`).
  This is the source for "the SDK does the retrying, not Spring AI's retry
  module."

**Spring Framework 7 core resilience (current standard — supersedes Spring Retry)**

- [Spring Framework — Resilience](https://docs.spring.io/spring-framework/reference/core/resilience.html) —
  the reference for `@Retryable`, `@EnableResilientMethods`, and
  `RetryTemplate`; part of core Spring Framework since 7.0, no separate
  dependency. States exactly what happens once retries are exhausted: "If
  all attempts have failed and the retry policy has been exhausted, the
  last original exception from the target method will be propagated to the
  caller" — the last original exception, not a wrapper, which is why
  `AskController`'s catch clause below can match the specific exception
  types directly.
- [`@Retryable` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/resilience/annotation/Retryable.html) —
  the authoritative attribute list used below: `includes`, `maxRetries`
  (default `3`), `delay` (default `1000` ms), `multiplier` (default `1.0`,
  a `double`), `maxDelay`.
- [Spring Retry — GitHub](https://github.com/spring-projects/spring-retry) —
  cited for its own archival notice: "superseded by Spring Framework 7 and
  is now archived," which is why it isn't the library used today.

**Resilience4j (verified alternative, not used here)**

- [Resilience4j — Spring Boot 3 Getting Started](https://resilience4j.readme.io/docs/getting-started-3) —
  the `resilience4j-spring-boot3` artifact and its `@Retry` annotation;
  the right tool if you need circuit breakers/bulkheads/rate limiters
  alongside retry, which is out of scope for today.

**Internal (already verified on earlier days)**

- [Day 4 — Groq `ChatClient` + Hugging Face embeddings](04-day4-groq-embeddings-hello-world.md) —
  the two calls being wrapped today.
- [Day 11–14 — `/ask` endpoint](11-14-ask-endpoint-rag-pipeline.md) —
  the `AskController` this day modifies; its `embed()` method and
  `chatClient.prompt(...)` call site are the exact starting point below.
- [Day 2 — Decorator pattern definition](02-day2-java-oop-refresher.md) —
  section 6, the definition this day's Java pattern note implements for
  real.

## Java pattern note

**Java pattern note:** a retry wrapper around an existing call, without
changing that call's interface, is the **Decorator** pattern
[previewed on Day 2, section 6](02-day2-java-oop-refresher.md) — "attach
new behaviors to objects by placing these objects inside special wrapper
objects that contain the behaviors," in a way callers can't tell apart
from the un-decorated version. Today gives you *two* instances of it, and
they look different on the surface but are the same shape underneath:

- **Hand-built, by you, today:** `EmbeddingClient` wraps the raw
  `HttpClient` call to Hugging Face. It implements no shared interface
  with anything (there's nothing pre-existing to be compatible with), but
  the shape is still "wrap a call, add behavior around it, keep the
  caller's contract" — `AskController` calls `embeddingClient.embed(text)`
  and gets a `float[]` back, exactly as it did calling the private
  `embed()` method on Day 11–14. The retry logic is invisible from the
  call site.
- **Built by someone else, already there:** the OpenAI SDK's retry loop
  around every Groq HTTP call, which you configure but never see. And,
  once you add `@Retryable` to `EmbeddingClient.embed()`, Spring's own
  AOP proxy around *your* decorator. `@Retryable` works by generating a
  dynamic proxy that
  implements the same call signature and delegates to your real method,
  catching and retrying on the way. You never see the proxy class; you
  just see that calling `embed(...)` sometimes quietly retries before
  returning or failing. That's the Gang of Four's "flexible alternative
  to subclassing" claim made literal — Spring writes the wrapper class for
  you, at runtime, from an annotation.

One consequence of the proxy-based version worth internalizing now,
because it will cost you a debugging session later if you don't: **a
proxy only intercepts calls that come from *outside* the bean.** A method
calling `this.embed(...)` from within the same class bypasses the proxy
entirely — no retry happens. That's why `EmbeddingClient` below is its own
`@Component`, injected into `AskController`, rather than a private method
on the controller the way Day 11–14 wrote it. (The same rule governs
`@Transactional` — if that ever bit you before, it's the identical cause.)

## Exercise (guided TODO + hints — familiar Spring pattern, new only in what it's applied to)

Do today's work on a new feature branch, per Day 1's workflow:

```bash
git checkout -b feature/rate-limit-resilience
```

### 1. No new dependency — turn on what's already there

Both retry engines used today are already on your classpath: the OpenAI
SDK (and its built-in retry) came in with `spring-ai-starter-model-openai`
on Day 4, and Spring Framework 7's core resilience module ships inside
`spring-context`, which `spring-boot-starter-web` already depends on.
There's nothing to add to `pom.xml`. Two things to do instead:

**a. Enable Spring's resilient-method proxies.** Unlike the SDK's retry
(which is always on), `@Retryable` needs one annotation on your main
application class:

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableResilientMethods
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
```

**b. Tune the SDK's retry for Groq's free tier.** The SDK already treats
`429` as retryable, so there's no classification to fix, only two numbers
to make explicit. Add them to `application.yml` directly under
`spring.ai.openai`:

```yaml
spring:
  ai:
    openai:
      base-url: https://api.groq.com/openai/v1
      api-key: ${GROQ_API_KEY}
      max-retries: 4
      timeout: 30s
      chat:
        model: openai/gpt-oss-120b
```

`max-retries: 4` (up from the default `3`) gives a free-tier rate limit
one more backoff window to clear before you give up. It matches the
`maxRetries = 4` you'll give the Hugging Face side in step 2: same policy,
two different engines. `timeout: 30s` (down from `60s`) bounds how long a
single hung request can block `/ask`. With retries on top, the worst case
is still well under the few minutes a human would wait before
refreshing. Writing both out, even though the defaults would roughly
work, documents the decision in the config file where the next person will
look for it.

### 2. TODO — build `EmbeddingClient`, the hand-built decorator

The Hugging Face call has no framework wrapping it, so this is the class
you write. Start from Day 11–14's private `embed()` method — the HTTP call
itself doesn't change — but move it into its own `@Component` (required
for `@Retryable`'s proxy to apply, per the pattern note above), and
classify failures the same way the OpenAI SDK does: transient (retry)
vs. non-transient (fail immediately).

```java
package com.example.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final String hfApiKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddingClient(@Value("${HF_API_KEY}") String hfApiKey) {
        this.hfApiKey = hfApiKey;
    }

    // TODO: annotate with @Retryable — retry only TransientEmbeddingException,
    //       a handful of attempts, short exponential backoff (mirror the
    //       spring.ai.openai.max-retries value above: this is the same
    //       policy, a different mechanism).
    public float[] embed(String text) {
        String url = "https://router.huggingface.co/hf-inference/models/"
                + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + hfApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("inputs", text))))
                    .build();

            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            // TODO: if status is 429 or >= 500, log a warning and throw
            //       TransientEmbeddingException (retryable).
            // TODO: else if status != 200, throw NonTransientEmbeddingException
            //       (not retryable — retrying a malformed request won't help).

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vector = root.get(0).isArray() ? root.get(0) : root; // [...] or [[...]]
            float[] embedding = new float[vector.size()];
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) vector.get(i).asDouble();
            }
            return embedding;
        } catch (Exception e) {
            // TODO: if e is already one of your two exceptions, rethrow it
            //       unchanged. Otherwise (a network-level failure — wrong
            //       host, connection refused, timeout) it's transient too:
            //       log a warning and throw TransientEmbeddingException,
            //       wrapping e as the cause.
            throw new RuntimeException(e);
        }
    }
}
```

*Hints:*

- You need two small exception classes first —
  `TransientEmbeddingException` and `NonTransientEmbeddingException`, both
  plain `RuntimeException` subclasses with a `String` (and, for the
  transient one, a `String, Throwable`) constructor. Naming them to
  mirror the transient/non-transient split the OpenAI SDK makes is
  deliberate: same distinction, one you built and one the library built.
- `@Retryable`'s `includes` attribute takes the exception *type(s)* that
  trigger a retry — everything else thrown propagates immediately. Set
  `includes = TransientEmbeddingException.class` so
  `NonTransientEmbeddingException` is never retried.
- Match the Groq side's retry count for consistency, with a short
  backoff so the checkpoint resolves in seconds:
  `maxRetries = 4, delay = 500, multiplier = 2, maxDelay = 4000` (types:
  `maxRetries`/`delay`/`maxDelay` are `long` milliseconds, `multiplier` is
  a `double`). Total attempts = 1 initial + `maxRetries` retries = 5.
- The catch-all branch matters more than it looks: a bad host or dropped
  connection throws `IOException`/`InterruptedException` from
  `http.send(...)`, not an HTTP status you can inspect — those are
  network-level failures, and they're exactly as transient as a 429 or a
  5xx (the checkpoint below induces failure this way). Rethrow your own
  two exception types unchanged in that branch (they're `RuntimeException`
  and would otherwise get caught and re-wrapped by the same broad
  `catch`), and wrap everything else as `TransientEmbeddingException`.
- A one-line `log.warn(...)` right before each `TransientEmbeddingException`
  throw is what makes the checkpoint's retry attempts visible — `@Retryable`
  doesn't log its attempts for you (and neither does the OpenAI SDK, which
  is why the Groq-side bonus checkpoint below is timed rather than read
  from the logs).
- Optional stretch: Day 8–10's `ChunkingPipeline` still has its own
  private, un-retried `embed()`. Injecting `EmbeddingClient` there too
  gives the upload path the same protection, and removes the duplicate
  Day 11–14 pointed out.

### 3. TODO — wire `AskController` to fail clean, not raw

`AskController` needs three changes from its Day 11–14 shape: inject
`EmbeddingClient` instead of calling a private `embed()`, and catch
whatever survives retries — from either call — into one clean error
response instead of letting it surface as a raw `500`.

```java
@PostMapping("/ask")
public ResponseEntity<?> ask(@RequestBody AskRequest request) {
    // TODO: wrap the existing Day 11-14 body in a try block — no other
    //       logic changes except calling embeddingClient.embed(...)
    //       instead of the old private embed(...), and returning
    //       ResponseEntity.ok(new AskResponse(...)) instead of the bare
    //       record.
    //
    // TODO: catch (OpenAIException
    //             | TransientEmbeddingException | NonTransientEmbeddingException e)
    //       and return a 503 with a small JSON error body — never let any
    //       of these three reach the caller as a raw stack trace.
    return null;
}
```

*Hints:*

- `OpenAIException` is `com.openai.errors.OpenAIException`, the SDK's
  base class for every failure it throws once its own retries run out:
  `RateLimitException` (429), `InternalServerException` (5xx),
  `OpenAIIoException` (network failure, e.g. wrong host or refused
  connection), and the non-retryable ones like `UnauthorizedException`
  (bad key). It reaches your controller unwrapped, because Spring AI's
  `ChatClient` doesn't translate it into a Spring exception type. Catching
  the base class covers all of them. It's already on your compile
  classpath via `spring-ai-starter-model-openai`, so you don't need a new
  dependency.
- Don't reach for `TransientAiException` / `NonTransientAiException` from
  `org.springframework.ai.retry`, even though older tutorials catch them.
  Per the primer, that package isn't on your classpath in 2.x, so the
  import won't compile. And even if you added it, the OpenAI client never
  throws those types.
- `ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "..."))`
  is the shape: a `503`, not a `500` — telling the caller "the backend had
  a bad moment, try again," which is true, versus a `500`'s implication
  "the server is broken," which isn't.
- Since the method can now return either an `AskResponse` or an error map,
  its return type has to widen from `AskResponse` to `ResponseEntity<?>`.

## Checkpoint

You're done with Day 15 when:

1. **Happy path still works.** `curl -X POST localhost:8080/ask -H "Content-Type: application/json" -d '{"question":"What is the equipment stipend?"}'`
   returns the same `200` with `{ answer, citedDocuments }` Day 11–14
   produced — today's changes are invisible when nothing fails.
2. **Induced failure shows retries, then a clean failure.** Temporarily
   break the Hugging Face call — in `EmbeddingClient`, change
   `router.huggingface.co` to an invalid host, e.g.
   `router.huggingface.invalid` — and restart. Repeat the same curl.
   Expected sequence:
   - The app logs **five** `WARN` lines (one initial attempt + four
     retries, per `maxRetries = 4`) from `EmbeddingClient`, each noting a
     retryable/network failure, with increasing gaps between them
     (roughly 500ms, 1s, 2s, 4s — the exponential backoff).
   - The curl call itself takes a few seconds (not instant, not minutes)
     and then returns **`503`** with a small JSON body like
     `{"error":"The AI service is temporarily unavailable. Please try again shortly."}`
     — not a stack trace, not a hung connection.
   - Revert the host string and restart; step 1's happy path passes again.
3. **Bonus (optional) — the Groq side.** With the Hugging Face host
   restored, temporarily point `spring.ai.openai.base-url` at an
   unreachable address (e.g. `http://127.0.0.1:1/openai/v1`, since nothing
   listens on port 1) and repeat the curl. Unlike step 2, **nothing appears
   in the logs**: the OpenAI SDK retries silently. What you *can* observe
   is time. The curl takes roughly 7 seconds with `max-retries: 4` (about
   3.5 seconds at the default `3`, because each extra retry roughly doubles
   the backoff) before returning the same clean `503`, this time via the
   `OpenAIException` branch of your catch clause (concretely an
   `OpenAIIoException`). That delay is your evidence the retries happened,
   and changing `max-retries` and watching it move is the quickest proof
   the setting is actually live. Revert the base URL afterward.

If step 2's failure comes back as a raw `500` with a stack trace in the
response body, the catch clause isn't matching the exception actually
thrown — check whether the network failure is reaching
`TransientEmbeddingException` or slipping through as something else.

## Interview angle

*"Tell me about a time you handled a flaky external dependency."*

This is the rare interview question you now have a genuinely first-hand
answer to, not a rehearsed one. The shape of a strong answer: distinguish
*why* the dependency failed (a rate limit — nothing wrong with the
request, resending shortly would likely succeed) from failures that
retrying can't fix (a bad API key, a malformed request), and say so
explicitly rather than treating every exception the same. Then name the
mechanics — exponential backoff with a capped number of attempts, so a
transient blip self-heals without hammering an already-struggling
dependency, and a bounded worst case instead of retrying forever. And
close with the part most answers skip: what happens when retries *don't*
work — a clean, typed error response to the caller, because a stack trace
or a hung request is its own failure on top of the original one. The
detail that makes this demonstrable rather than textbook: you can explain
that there were *two* retry layers, and why you built only one of them.
The Groq client already retried inside the OpenAI SDK, so you tuned it
rather than wrapping it a second time; stacking retry loops multiplies
attempts against a provider that's already throttling you. And you
found that out by reading the framework's source, not its docs page. The
widely documented `spring.ai.retry.*` settings turned out to do nothing
for the OpenAI client in Spring AI 2.x, because the client moved onto the
official SDK. That's the difference between "I know retry is a thing" and
"I checked where retry actually happens in my stack before adding more of
it."

## Answer Key (attempt it yourself first!)

Only read this after the checkpoint's three verification steps pass on
your own implementation. This is one reasonable design, not the only
correct one.

**`TransientEmbeddingException.java`**

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

**`NonTransientEmbeddingException.java`**

```java
package com.example.demo;

public class NonTransientEmbeddingException extends RuntimeException {

    public NonTransientEmbeddingException(String message) {
        super(message);
    }
}
```

**`EmbeddingClient.java`** (complete)

```java
package com.example.demo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final String hfApiKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddingClient(@Value("${HF_API_KEY}") String hfApiKey) {
        this.hfApiKey = hfApiKey;
    }

    @Retryable(includes = TransientEmbeddingException.class,
            maxRetries = 4, delay = 500, multiplier = 2, maxDelay = 4000)
    public float[] embed(String text) {
        String url = "https://router.huggingface.co/hf-inference/models/"
                + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + hfApiKey)
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

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vector = root.get(0).isArray() ? root.get(0) : root; // [...] or [[...]]
            float[] embedding = new float[vector.size()];
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) vector.get(i).asDouble();
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
}
```

**`DemoApplication.java`** (add `@EnableResilientMethods`)

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableResilientMethods
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
```

**`application.yml`** (Day 11–14's config, plus the two SDK retry settings)

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
  ai:
    openai:
      base-url: https://api.groq.com/openai/v1
      api-key: ${GROQ_API_KEY}
      max-retries: 4
      timeout: 30s
      chat:
        model: openai/gpt-oss-120b
```

**`AskController.java`** (complete — `PromptBuilder` and `ContextChunk` are
separate classes, unchanged from
[Day 11–14](11-14-ask-endpoint-rag-pipeline.md), and not repeated here;
everything shown below is the complete class, including the unchanged
`SYSTEM_INSTRUCTION` and `toVectorLiteral`)

```java
package com.example.demo;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.openai.errors.OpenAIException;

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
    private final EmbeddingClient embeddingClient;

    public AskController(ChatClient.Builder chatClientBuilder,
                         ChunkRepository chunkRepository,
                         DocumentRepository documentRepository,
                         EmbeddingClient embeddingClient) {
        this.chatClient = chatClientBuilder.build();
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.embeddingClient = embeddingClient;
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest request) {
        try {
            float[] queryEmbedding = embeddingClient.embed(request.question());

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

            return ResponseEntity.ok(new AskResponse(answer, citedDocuments));
        } catch (OpenAIException
                | TransientEmbeddingException | NonTransientEmbeddingException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error",
                            "The AI service is temporarily unavailable. Please try again shortly."));
        }
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
}
```

Commit on the feature branch with a Conventional Commits message (e.g.
`feat: add retry-with-backoff resilience to /ask`), push, and merge via a
pull request per Day 1.
