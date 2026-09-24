# Before Day 8 — Upgrade to Spring Boot 4 + Spring AI 2

**Focus:** Move the project you've built through Day 6–7 onto the versions
the rest of this curriculum is written and tested against: **Spring Boot
4.1.1, Spring AI 2.0.1, Apache Tika 4.0.0.** Budget an hour or two. It's a
dependency-and-config change, with no Java code to rewrite: every class
you've written so far compiles and runs unchanged on the new versions.

Why now and not later: your `pom.xml` is on Spring Boot 3.3.5 and Spring
AI `1.0.0-M5` (a pre-release milestone). Days 8–16 assume the newer stack,
and some of it doesn't exist on the old one. Day 15's `@Retryable` and
`@EnableResilientMethods` are Spring Framework 7 features (Boot 4 only),
and Day 15's Groq retry lesson describes how Spring AI 2's OpenAI client
behaves, which differs from 1.x. Six classes are cheap to upgrade today and
painful to upgrade after Day 15. Spring Boot 3.3 is also past its
open-source support window (see the support table linked below).

## Concept primer

### What a major-version upgrade actually changes

Most of a framework's major version bump never touches your code. It shows
up in three places, and this upgrade has all three:

1. **Renamed artifacts.** Spring Boot 4 split its starters into smaller
   modules. `spring-boot-starter-web` becomes `spring-boot-starter-webmvc`,
   and the single `spring-boot-starter-test` becomes one test starter per
   feature (`spring-boot-starter-data-jpa-test`,
   `spring-boot-starter-webmvc-test`). Spring AI renamed
   `spring-ai-openai-spring-boot-starter` to `spring-ai-starter-model-openai`
   on the way to 1.0, after the milestone you're on.
2. **Changed defaults hiding behind unchanged property names.** This is the
   dangerous kind, because nothing fails to compile. Here it's the Groq
   `base-url` (below).
3. **Changed packaging.** Tika 4 publishes `tika-parsers-standard-package`
   as a POM (a list of other dependencies), not a jar. Declaring it the old
   way fails dependency resolution.

### The `/v1` trap

Spring AI 1.x appended `/v1` to whatever `base-url` you gave it, so Day 4
had you write `https://api.groq.com/openai`. Spring AI 2 uses the official
OpenAI Java SDK under the hood, which expects the version *in* the base
URL. That's why the Spring AI Groq page tells you to set it to
`api.groq.com/openai/v1`. Keep the old value and the app still starts
fine, but every chat call fails with:

```
com.openai.errors.NotFoundException: 404: Unknown request URL: POST /openai/chat/completions
```

Notice `/openai/chat/completions`, with no `/v1`. When a 404 names a path,
compare it character-for-character with the path the provider documents
before suspecting anything else.

## Reference links

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) —
  the official list of renamed starters (the `webmvc` and per-feature
  `-test` names used below) and every other breaking change.
- [Spring Boot — support timeline](https://spring.io/projects/spring-boot#support) —
  which versions still get open-source fixes.
- [Spring AI — Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) —
  the "OpenAI Java SDK Transition" section: Spring AI now uses the
  official `openai-java` SDK for all OpenAI models.
- [Spring AI — Groq Chat](https://docs.spring.io/spring-ai/reference/api/chat/groq-chat.html) —
  "set the base-url to api.groq.com/openai/v1."
- [Apache Tika 4.0.0](https://tika.apache.org/4.0.0/index.html) — release
  documentation; the POM-only packaging is covered in
  [Day 6–7's reference section](06-07-file-upload-tika.md).

## Exercise

```bash
git checkout -b chore/upgrade-boot4-spring-ai2
```

### 1. `pom.xml`

Make these seven changes. Each is a one-line edit except the last two.

| What | From | To |
|---|---|---|
| Parent version | `3.3.5` | `4.1.1` |
| Web starter | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| Spring AI starter | `spring-ai-openai-spring-boot-starter` | `spring-ai-starter-model-openai` |
| Spring AI BOM version | `1.0.0-M5` | `2.0.1` |
| `tika-core` version | `3.0.0` | `4.0.0` |
| `tika-parsers-standard-package` | `3.0.0`, no type | `4.0.0` **plus** `<type>pom</type>` |
| Test starter | `spring-boot-starter-test` | the two starters below |

The test starter becomes two dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

And the Tika parsers dependency becomes:

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>4.0.0</version>
    <type>pom</type>
</dependency>
```

Finally, **delete the whole `<repositories>` block** (the
`spring-milestones` repository). Spring AI 2.0.1 is a GA release on Maven
Central, so the milestone repository no longer does anything.

*Hint:* change one thing at a time and run `./mvnw -DskipTests package`
after each. When something breaks, you'll know which edit did it, and
you'll have seen what each kind of breakage looks like, which is the
actual skill being practised here.

### 2. `application.properties`

Two edits, both in the Groq section:

- `spring.ai.openai.base-url=https://api.groq.com/openai` → add `/v1` to
  the end (the trap above).
- Delete `spring.ai.openai.chat.options.model=...`. In Spring AI 2 the
  model is set by `spring.ai.openai.chat.model` alone, which you already
  have.

### 3. Leave these alone

- **`docker-compose.yml`'s `pgvector/pgvector:pg17` image.** Later days
  show `pg18-trixie` for fresh setups, but your `pgdata` volume already
  holds a Postgres 17 data directory, and a Postgres 18 server won't start
  on it. pgvector works identically on 17 for everything in this
  curriculum.
- **Your Java code, including the `com.fasterxml.jackson.databind.ObjectMapper`
  import.** Boot 4 moves its own JSON support to Jackson 3 (package
  `tools.jackson`), but Jackson 2 is still on your classpath through the
  OpenAI SDK, so this import keeps compiling.
- **Your package name.** Curriculum snippets use `com.example.demo`; keep
  `com.manish.spring_boot_ai_project` and adjust the `package` line when
  you copy a snippet.

## Checkpoint

1. `./mvnw -DskipTests package spotless:check` succeeds.
2. `docker compose up --build`, then:
   - `curl localhost:8080/hello-groq` returns a sentence (not a 500).
   - `curl localhost:8080/hello-embedding` returns 384 numbers.
   - `curl -F file=@docs/curriculum/sample-data/remote-work-policy.txt localhost:8080/documents/upload`
     returns a document whose `content` starts with "Remote Work Policy",
     so Tika 4 is extracting.
3. Merge the branch through a PR, per Day 1.

If `/hello-groq` returns a 500, check the app log for the SDK exception:

| Log says | Cause |
|---|---|
| `NotFoundException: 404: Unknown request URL ... /openai/chat/completions` | `base-url` is missing `/v1` |
| `UnauthorizedException: 401: Invalid API Key` | the URL is right; `GROQ_API_KEY` isn't reaching the container (check `.env` and the compose `environment:` block) |

## Interview angle

*"Tell me about a dependency upgrade that went wrong, or nearly did."*

The `/v1` change is a good one to tell. It's a major-version upgrade where
nothing failed to compile and the app started cleanly, but one behavior
behind an unchanged property name had moved: which side adds the API
version to the URL. The strong part of the answer is the method, not the
fix. You upgraded one dependency at a time, you read the 404's path
literally instead of guessing, and you confirmed against the provider's
documented base URL. That's what separates "I bumped version numbers" from
"I know how to upgrade a framework safely."
