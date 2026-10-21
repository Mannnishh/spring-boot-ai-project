# Day 17 — Frontend or API Docs Polish

**Focus:** Give the app a face. Everything built since Day 3 works, but
right now the only way to show it to anyone is a terminal full of `curl`
commands. Today you pick **one** of two ways to fix that, and either one
is a legitimate finish for the day:

- **Option A — Swagger UI (recommended, lower effort).** Add one library,
  `springdoc-openapi`, and the app serves a clickable, always-up-to-date
  API page generated from your own controllers. You then annotate `/ask`
  and `/documents/upload` with example request and response bodies so the
  page explains itself. A short Postman variation (import the same
  generated document into Postman) is included.
- **Option B — a one-page frontend (more polish, more time).** A single
  plain HTML/JS file with an upload form and a question box that calls the
  same endpoints, served by Spring Boot itself. No framework, no build
  step.

Frame the day the way you'd frame it in an interview: this is **what
you'll actually have open on screen** when someone says "show me." Option
A is the page a technical interviewer wants to poke at; Option B is the
page you'd show a non-engineer. You do not need both. If you finish one
with time to spare, the best use of the leftover hours is Day 18 (README
and demo prep), not the other option.

**Pacing (about 6 hours):** Option A is roughly 3.5–4 hours including the
Postman variation, plus about 45 minutes if you did the optional Day 16
(JWT). Option B is roughly 5–6 hours, most of it spent on the two JS
handlers and on getting the multipart upload right.

**You skipped or finished Day 16?** Both options work either way. The
main path below assumes an app with no security (Days 1–15). If you did
the optional Day 16, each option has a clearly marked subsection titled
"If you completed Day 16 (JWT)" — ignore those if you didn't.

## Concept primer

### Three different things that all get called "Swagger"

People say "Swagger" for three separate pieces. Keeping them apart makes
the rest of today (and any debugging) much easier:

1. **The OpenAPI document** — a JSON (or YAML) file that describes your
   API: every path, HTTP method, request body, response, and data shape.
   The [OpenAPI Specification](https://spec.openapis.org/oas/latest.html)
   defines "a standard, language-agnostic interface to HTTP APIs," and the
   spec says such a description can be consumed by documentation, code
   generation, and testing tools. Your app will serve this at
   `/v3/api-docs`.
2. **Swagger UI** — a web page that reads an OpenAPI document and renders
   it as interactive documentation. Per
   [swagger.io](https://swagger.io/tools/swagger-ui/), it "allows anyone to
   visualize and interact with the API's resources." The "Try it out"
   button on each operation sends a real HTTP request to your running app.
3. **springdoc-openapi** — the Spring Boot library that *produces* the
   OpenAPI document from your code at runtime and bundles Swagger UI to
   display it. You add one dependency; it scans your `@RestController`s,
   reads the method signatures, the request/response types, and any
   annotations you add, and builds the document. No separate YAML file to
   maintain, so the docs cannot silently drift away from the code.

The practical consequence: **without any annotations at all**, springdoc
already documents every endpoint with the right paths, methods, and
record shapes (`AskRequest`, `AskResponse`, `CreateDocumentRequest`,
`Document`). Annotations are polish layered on top — human-readable
summaries, example bodies, and the response codes the framework can't
infer on its own.

### Which springdoc? The major version tracks the Spring Boot generation

springdoc's own FAQ gives the compatibility rule: *"springdoc-openapi 3.x
is compatible with spring-boot 4."* (Its table lists Boot `4.0.x` ↔
springdoc `3.0.x`; springdoc `3.1.x` was built against Boot 4.1.x — its
release notes for 3.1.0 say "Upgrade Spring Boot to version 4.1.0.")
Spring Boot 3.x apps use the 2.x line instead. Our app is Boot 4.1.1, so
today uses **springdoc 3.1.1** — the latest release at the time of
writing, and the version this day was built and booted against. If you
paste a "springdoc 2.x" snippet from an older tutorial, it's for the wrong
Boot generation.

### A real trap you will probably hit: `@Schema` and a stale transitive jar

Adding springdoc is one dependency, and Swagger UI will render fine. The
trap shows up the moment you add a `@Schema` annotation to a record:
`/v3/api-docs` starts returning HTTP 500 with a
`java.lang.NoSuchMethodError: ... Schema.$dynamicRef()` in the log. The
cause is the classpath, not your code, and it is two separate problems.
springdoc needs `swagger-annotations-jakarta` **2.2.55**, but Spring AI's
chat client drags in an *older* copy of that same artifact (2.2.38) that is
**closer to your project in the dependency tree**, and Maven's rule is that
["Maven picks the 'nearest definition'"](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html).
Separately, the OpenAI Java SDK brings a *different* artifact,
`swagger-annotations` 2.2.31 (no `jakarta`), which defines the same
`io.swagger.v3.oas.annotations.*` classes: a duplicate on the classpath,
not a version contest. Either way the old `Schema` class can win, and
springdoc calls a method that doesn't exist on it. This is a good example of a failure whose stack trace points
at a library you never touched. The fix (two small `pom.xml` changes) is
in the exercise as a "find it yourself" step with hints, and in the Answer
Key. Reading `mvn dependency:tree` output is the skill being practiced.

### Option B: why a static page served by Spring Boot, and what CORS is

A browser only lets a page's JavaScript read responses from the **same
origin** it was loaded from; MDN defines two URLs as same-origin when
their [protocol, port, and host are the same](https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy).
Anything else — say, a page opened from `file:///...` or a dev server on
`localhost:5173` calling your API on `localhost:8080` — is cross-origin,
and the browser blocks the script from reading the response unless the
server opts in with CORS headers (MDN:
[CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS)).
Spring can be configured to send those headers, but it is extra
configuration and one more thing that fails confusingly (the error
details are deliberately hidden from JavaScript).

**This day's choice for Option B: put the page in Spring Boot's static
resources.** By default
[Spring Boot serves static content](https://docs.spring.io/spring-boot/reference/web/servlet.html)
from `/static` on the classpath, and it uses an `index.html` found there
as the welcome page at `/`. So the page and the API are served by the
same process on the same port: the browser sees one origin, the page can
call `fetch('/ask', ...)` with a relative path, and **no CORS
configuration is needed at all**. The trade-off is that you get no hot
reload or framework tooling — fine for one file. If you later move to a
separate front-end dev server, you'd add CORS at that point (see the
Spring reference link below).

Two small rules the page follows because they matter in real apps too:

- **Don't set `Content-Type` yourself on a `FormData` upload.** MDN is
  explicit that doing so prevents the browser from adding the multipart
  boundary string, and the upload then fails on the server.
- **Write model output with `textContent`, never `innerHTML`.** An LLM's
  answer is untrusted text (it can echo content from an uploaded
  document). MDN advises against `innerHTML` for text because it parses
  raw HTML and "can be susceptible to XSS attacks"; `textContent` inserts
  plain text.

### Postman, the light alternative

If you only need a way to call the API and share the calls, Postman can
build a collection straight from the same OpenAPI document: you import a
link or raw text and choose to import it as a collection. Collections can
be exported as JSON files to share. It's less impressive in a demo than a live Swagger page, but it's
worth ten minutes because "I keep a Postman collection for it" is a
normal thing to say about an API.

## Reference links

**springdoc-openapi (Swagger UI generation for Spring Boot)**

- [springdoc.org — Getting Started](https://springdoc.org/#getting-started) —
  the dependency to add (`org.springdoc:springdoc-openapi-starter-webmvc-ui`),
  and the default paths: Swagger UI at `/swagger-ui.html`, the JSON
  OpenAPI document at `/v3/api-docs`, YAML at `/v3/api-docs.yaml`.
- [springdoc FAQ — compatibility matrix with Spring Boot](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) —
  the source for "springdoc 3.x is compatible with spring-boot 4."
- [springdoc.org — Adding API information and security documentation](https://springdoc.org/#adding-api-information-and-security-documentation) —
  where `@OpenAPIDefinition` and `@SecurityScheme` go (springdoc
  recommends declaring them inside a Spring-managed bean, which is why
  `OpenApiConfig` below is a `@Configuration` class).
- [springdoc FAQ — hiding an operation or a controller](https://springdoc.org/#how-can-i-hide-an-operation-or-a-controller-from-documentation) —
  `@Hidden` on a `@RestController` or method.
- [springdoc FAQ — file upload](https://springdoc.org/#is-file-upload-supported) —
  confirms `MultipartFile` (including `@RequestPart`) is a supported
  upload type and shows `@Parameter(description = ...)` on the file
  parameter, which is what `DocumentController.upload` uses.
- [springdoc FAQ — how to define a SecurityScheme](https://springdoc.org/#how-can-i-define-securityscheme)
  and [add an authorization header to requests](https://springdoc.org/#how-do-i-add-authorization-header-in-requests) —
  `@SecurityScheme`, and `@SecurityRequirement` on protected operations.
  Used only in the Day 16 subsection.
- [springdoc-openapi v3.1.1 release notes](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.1.1) —
  swagger-core 2.2.55 / swagger-ui 5.32.14, and "Manage the swagger
  artifacts in `springdoc-openapi-bom`, so that modules holding only the
  annotations stay in lockstep," which is what makes the BOM import in the
  pom fix work.
- [springdoc-openapi v3.1.0 release notes](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.1.0) —
  "Upgrade Spring Boot to version 4.1.0."
- [`springdoc-openapi-bom` 3.1.1 (Maven Central POM)](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-bom/3.1.1/springdoc-openapi-bom-3.1.1.pom) —
  the BOM; its `dependencyManagement` pins
  `io.swagger.core.v3:swagger-annotations-jakarta` to `${swagger-api.version}`
  (`2.2.55`).

**OpenAPI annotations (the `io.swagger.v3.oas.annotations` package)**

- [swagger-core wiki — Swagger 2.X Annotations](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations) —
  `@Operation`, `@ApiResponse`, `@Content`, `@ExampleObject`, `@Schema`,
  `@Tag`, `@SecurityScheme`, with the example shapes used below.
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html) and
  [Swagger UI](https://swagger.io/tools/swagger-ui/) — what the generated
  document and the rendered page are.
- [Swagger — Bearer Authentication](https://swagger.io/docs/specification/v3_0/authentication/bearer-authentication/) —
  the OpenAPI shape of a JWT scheme: `type: http`, `scheme: bearer`,
  `bearerFormat: JWT` (optional, documentation only), applied through a
  `security` list. Day 16 subsection only.

**Maven (for the transitive-dependency trap)**

- [Maven — Introduction to the Dependency Mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html) —
  "nearest definition" mediation, `<exclusions>`, and importing a BOM
  with `<scope>import</scope>` inside `<dependencyManagement>`.

**Postman (the lighter-weight alternative)**

- [Postman — Import data](https://learning.postman.com/docs/getting-started/importing-and-exporting/importing-data/) —
  Import → Link or raw text accepts an OpenAPI definition; for OpenAPI
  you choose whether to import it as a collection (or as a specification
  with a collection).
- [Postman — Export data](https://learning.postman.com/docs/getting-started/importing-and-exporting/exporting-data/) —
  Collections → "View more actions" → More → Export collection → Export
  JSON.

**Browser and static-page references (Option B)**

- [Spring Boot Reference — Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html) —
  the "Static Content" section (default `/static` classpath location) and
  the "Welcome Page" section (`index.html` in a static location becomes
  the welcome page).
- [MDN — Using FormData objects](https://developer.mozilla.org/en-US/docs/Web/API/FormData/Using_FormData_Objects) —
  sending a file with `fetch` and why not to set `Content-Type` manually.
- [MDN — `Node.textContent`](https://developer.mozilla.org/en-US/docs/Web/API/Node/textContent) —
  the "Differences from `innerHTML`" section.
- [MDN — `Response.ok`](https://developer.mozilla.org/en-US/docs/Web/API/Response/ok) and
  [`Element.replaceChildren()`](https://developer.mozilla.org/en-US/docs/Web/API/Element/replaceChildren) —
  the two DOM/Fetch calls the page's handlers lean on.
- [MDN — Same-origin policy](https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy) and
  [MDN — CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS) —
  what "same origin" means and why a cross-origin `fetch` gets blocked.
- [Spring Framework — CORS (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html) —
  `@CrossOrigin` and global `WebMvcConfigurer.addCorsMappings`; not needed
  for the same-origin page below, but it's where you'd go if you split the
  front end out later.

**Day 16 subsection only**

- [Spring Security — Authorize HttpServletRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) —
  `AuthorizationFilter` applies only the first matching rule, in the order
  declared, which is why the new `permitAll` rules go *before*
  `.anyRequest().authenticated()`.

**Internal (already verified on earlier days)**

- [Day 11–14 — `/ask` endpoint](11-14-ask-endpoint-rag-pipeline.md) and
  [Day 6–7 — `/documents/upload`](06-07-file-upload-tika.md) — the two
  endpoints annotated or called today.
- [Day 15 — Rate-limit resilience](15-rate-limit-resilience.md) — the
  fallback behavior the `/ask` description mentions.
- [Day 16 — JWT security](16-jwt-security.md) — the optional day the two
  "If you completed Day 16" subsections build on.
- [Sample data](sample-data/README.md) — the files to upload for the
  checkpoint.

## Java pattern note

**Java pattern note:** Today's Option A is a small, concrete example of
**annotations as declarative metadata**. `@Operation`, `@Schema`, and
`@ApiResponse` don't change what `ask()` does at all — no line of its
body is touched. A framework (springdoc) finds them by reflection at
startup and builds something new from them. That's the Open/Closed idea
from [Day 2](02-day2-java-oop-refresher.md) in a different costume: you
extend what the system produces (documentation) without modifying the
code that does the real work. It also explains why this is cheap to add
late in a project.

Option B has its own tiny version of a pattern you already know: every
request in the page goes through **one** function, `apiFetch`. Nothing
else calls `fetch` directly. That makes it a single seam — the same
reason you put the retry logic behind `EmbeddingClient`'s call on Day 15.
When Day 16's bearer token needs adding, you change one function instead
of every call site.

## Exercise

Do today's work on a new feature branch, per Day 1's workflow. You pick
one option below; the branch name is yours to choose to match:

```bash
git checkout -b feature/api-docs      # Option A
# or
git checkout -b feature/frontend      # Option B
```

Run the app the way you have since Day 3 (`docker compose up --build`) and
rebuild after each change; the examples below use `localhost:8080`.

---

## Option A — Swagger UI with springdoc (guided TODO + hints)

### A1. Add the dependency

In `pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.1</version>
</dependency>
```

`webmvc` because your app uses Spring MVC (`spring-boot-starter-webmvc`),
not WebFlux; `-ui` because you want Swagger UI bundled, not just the JSON
document.

Rebuild and start the app, then open
**http://localhost:8080/swagger-ui.html** (it redirects to
`/swagger-ui/index.html`). You should see your controllers listed
without having written a single annotation. Also fetch the raw document:

```bash
curl -s localhost:8080/v3/api-docs | head -c 400
```

> **If you completed Day 16**, every Swagger path returns `401` right now,
> in the browser and in this `curl`. Do step 1 of
> [A6](#a6-if-you-completed-day-16-jwt) (permit the doc paths in
> `SecurityConfig`) before continuing; A2–A5 depend on that page and
> document loading. Come back to the rest of A6 afterwards.

### A2. TODO — confirm every endpoint is there

Compare what Swagger UI lists against what you actually built. Write down
the list from the code first (open `DocumentController`, `AskController`,
and `HelloAiController`), *then* check the page. You're looking for
**eight operations**:

| Method | Path | Controller |
|---|---|---|
| `POST` | `/ask` | `AskController` |
| `POST` | `/documents/upload` | `DocumentController` |
| `POST` | `/documents` | `DocumentController` |
| `GET` | `/documents` | `DocumentController` |
| `GET` | `/documents/{id}` | `DocumentController` |
| `DELETE` | `/documents/{id}` | `DocumentController` |
| `GET` | `/hello-groq` | `HelloAiController` (Day 4 scaffold) |
| `GET` | `/hello-embedding` | `HelloAiController` (Day 4 scaffold) |

*Hint:* the quickest machine check is
`curl -s localhost:8080/v3/api-docs | grep -o '"/[a-zA-Z{}/-]*":{'` — one
line per path (six lines for the eight operations, because
`/documents` and `/documents/{id}` each carry two methods). Order may
vary.

Then use **Try it out → Execute** on `GET /documents`. It should return
`200` with the documents you've uploaded. This is the "invocable from the
page" half of the checkpoint.

### A3. TODO — add `@Schema` to the records (and meet the trap)

Give `AskRequest` and `AskResponse` human-readable descriptions and an
example value. Annotations come from `io.swagger.v3.oas.annotations.media`.

```java
package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A natural-language question to answer from the uploaded documents.")
public record AskRequest(
    // TODO: @Schema on the component: a description, and
    // example = "What is the equipment stipend for remote work?"
    String question) {}
```

```java
package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// TODO: a @Schema(description = ...) on the record itself
public record AskResponse(
    // TODO: @Schema(description = ..., example = "The equipment stipend is $500. Source: remote-work-policy.txt")
    String answer,
    // TODO: @Schema(description = "Distinct filenames of the documents the retrieved chunks came from.")
    List<String> citedDocuments) {}
```

Rebuild, restart, and reload `/v3/api-docs`.

**Expected: it breaks.** The endpoint returns `500`, and the app log
shows `java.lang.NoSuchMethodError: 'java.lang.String
io.swagger.v3.oas.annotations.media.Schema.$dynamicRef()'`. Nothing is
wrong with your annotations — read the "A real trap" section of the primer, then fix
it:

1. Run `mvn dependency:tree -Dverbose -Dincludes=io.swagger.core.v3` (or
   `./mvnw` — Maven on your machine, not inside Docker, is fine here) and
   find which `swagger-annotations*` jars are on the classpath and at
   which versions. springdoc needs `2.2.55`.
2. There are **two** problems in that output, not one: an old
   `swagger-annotations-jakarta` that beats springdoc's version, **and** a
   second, non-`jakarta` artifact (`swagger-annotations`) that contains
   classes with the *same package and names* as the right one. Fixing
   only the first still leaves the error.
3. Use the Maven documentation linked above: one tool *aligns* a version
   (`<scope>import</scope>` of springdoc's own BOM — springdoc 3.1.1
   publishes one that manages the swagger artifacts), and one tool
   *removes* a transitive dependency (`<exclusions>`). Which one fits
   which problem? Which dependency would you attach the exclusion to,
   given who pulls the old jar in?

If `dependency:tree` shows only 2.2.55 on your machine (for example a
later Spring AI release stopped pulling the old jar), you won't hit this;
move on to A4.

### A4. TODO — annotate `/ask` and `/documents/upload` with examples

This is the polish the checkpoint asks for. On `AskController.ask`, add:

- `@Operation(summary = ..., description = ...)`
- a **request body example**: in `@Operation(requestBody = ...)` use
  `@io.swagger.v3.oas.annotations.parameters.RequestBody` — written with
  its full name because `org.springframework.web.bind.annotation.RequestBody`
  is already imported in this class — containing an
  `@Content(mediaType = "application/json", examples = @ExampleObject(...))`
- a **200 response** with *two* `@ExampleObject`s: a normal answer and the
  "I don't have enough information to answer that." case your system
  prompt produces. Add `schema = @Schema(implementation = AskResponse.class)`
  to that `@Content` too (see the hint).
- a **503 response** documenting Day 15's failure contract (the JSON
  `error` body), if your `ask()` returns one.

On `DocumentController.upload`, add an `@Operation`, a **201** response
with a `Document` example, a **500** response (text extraction or
embedding can fail), and `@Parameter(description = ...)` on the `file`
parameter. Put `@Tag(name = ..., description = ...)` on both controller
classes so Swagger UI groups them with readable names.

*Hints:*

- `@ExampleObject(name = ..., value = ...)`'s `value` is a JSON string;
  a Java text block (`"""..."""`) keeps it readable.
- **Declaring `@Content` replaces what springdoc would have inferred.**
  If you give a response `@Content(examples = ...)` and no `schema`, the
  example appears but the response loses its link to the `AskResponse`
  schema (check the "Schemas" section at the bottom of Swagger UI — it
  disappears). Adding `schema = @Schema(implementation = X.class)` puts it
  back.
- For the 500 response with no body, use `content = @Content` (empty).
- Annotation placement: all of `@Operation` goes next to the existing
  `@PostMapping` / `@Retryable`; order among them doesn't matter.

### A5. TODO — API title, and hide the Day 4 scaffolds

Create `OpenApiConfig` — a `@Configuration` class with
`@OpenAPIDefinition(info = @Info(title = ..., version = ..., description = ...))`
(imports `io.swagger.v3.oas.annotations.OpenAPIDefinition` and
`io.swagger.v3.oas.annotations.info.Info`). The page header changes from
"OpenAPI definition" to your title.

Then put `@Hidden` (`io.swagger.v3.oas.annotations.Hidden`) on
`HelloAiController`. `/hello-groq` and `/hello-embedding` were Day 4
connectivity tests; they don't belong in a demo. After this, the path
check from A2 returns four paths, not six.

### A6. If you completed Day 16 (JWT)

> **Skip this subsection entirely if you skipped Day 16.** Everything
> above works on an unsecured app as-is.

With Day 16's `SecurityConfig` in place, the app default-denies every
route that isn't listed. That includes Swagger UI itself: **right after
you add springdoc, `/v3/api-docs`, `/swagger-ui.html`, and
`/swagger-ui/index.html` all return `401`.** Fixing this takes three
changes, and a fourth thing you do in the browser:

1. **Let the Swagger paths through the filter chain.** In your Day 16
   `SecurityConfig.securityFilterChain`, add a `permitAll` rule for
   `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, and
   `/v3/api-docs/**`. Order matters — Spring Security applies only the
   first matching rule, so it must come **before** your
   `.anyRequest().authenticated()` (the Spring Security reference linked
   above states this). Rebuild: Swagger UI and the JSON document now load
   with no token. The *operations* underneath still require one — that's
   correct and what you want.
2. **Declare a bearer-token security scheme.** Add `@SecurityScheme` to
   `OpenApiConfig` (`io.swagger.v3.oas.annotations.security.SecurityScheme`
   and `io.swagger.v3.oas.annotations.enums.SecuritySchemeType`): name it
   `bearerAuth`, type `HTTP`, scheme `bearer`, bearer format `JWT`. This is
   exactly the OpenAPI shape in the Swagger bearer-authentication page.
   It makes the **Authorize** button appear.
3. **Mark which operations need it.** Put
   `@SecurityRequirement(name = "bearerAuth")` on `AskController` and
   `DocumentController` (class level). Without this, the button exists but
   Swagger UI doesn't attach the token to those calls.
4. **Use it.** In Swagger UI, call `POST /login` (Day 16's
   `AuthController` shows up automatically) with
   `{"username":"demo","password":"demo-password"}`, copy the `token`
   string from the response, click **Authorize**, paste the token, and now `POST /ask` and `POST /documents/upload` work
   from the page. Paste only the token itself: for a scheme of type
   `http` / `bearer`, the Swagger UI JavaScript that springdoc bundles
   (5.32.14) builds the `Authorization: Bearer <token>` header itself. If
   a call still returns `401`, look at the `curl` command Swagger UI shows
   under the request after you click Execute and check that header.

*Notes:* (a) Day 16's code samples use `package com.example.demo;`. Your
real classes are in `com.manish.spring_boot_ai_project` — edit the files
you already have rather than pasting new ones. (b) The token is signed
with a key generated at startup (Day 16's `JwtConfig`), so after a
restart, re-run `/login` and re-authorize. (c) `/hello-groq` and
`/hello-embedding` are not listed in `SecurityConfig`, so if you haven't
hidden them yet they'd show up in the page and then 401 when invoked.

### A7. Variation — the same API in Postman (about 30 minutes)

Do this only after A1–A5. Steps 1 and 4 follow Postman's import and export
documentation linked above; steps 2 and 3 use Postman's request builder,
which those two pages don't cover, so if the UI has moved, use Postman's
own Learning Center to find the equivalent controls:

1. In Postman: **Import**, choose **Link or raw text**, paste
   `http://localhost:8080/v3/api-docs`, import it as a **Postman
   Collection**.
2. Run `GET /documents` from the new collection. Run `POST /documents`
   with a JSON body to create a document without a file upload.
3. For `POST /documents/upload`, switch the request body to **form-data**,
   add a key named `file`, change its type from Text to **File**, and pick
   one of the files from `docs/curriculum/sample-data/`. (The key must be
   `file`: the controller reads `@RequestParam("file")`.)
4. Export the collection (**Export collection → Export JSON**) and keep
   the file somewhere you can find it for Day 18; the annotated examples
   from A4 become the example bodies in the collection.

*Think about it:* what did you get from Swagger UI that Postman doesn't
give you (a live, always-current picture of the code), and the reverse
(saved environments, request history, easy sharing)?

---

## Option B — a one-page frontend (guided TODO + hints)

### B1. The choice already made for you, and the file to create

The page lives at `src/main/resources/static/index.html` (create the
`static` folder). Per the primer, that location is served by Spring Boot
at `/`, same origin as the API, so **no CORS configuration is needed** and
every call below uses a relative path. Don't open the file from disk
(`file:///...`); you'd be cross-origin and every call would fail.

### B2. Start from this skeleton

The HTML, CSS, `apiFetch`, and `loadDocuments` are done for you. The two
`TODO` handlers are the exercise.

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Document Q&amp;A</title>
  <style>
    :root { --bg: #f6f7f9; --card: #fff; --text: #1c1f24; --muted: #5d6673; --accent: #2457d6; --border: #d9dde3; }
    @media (prefers-color-scheme: dark) {
      :root { --bg: #14171c; --card: #1e232a; --text: #e8eaed; --muted: #9aa3af; --accent: #7aa2ff; --border: #323943; }
    }
    body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.5 system-ui, sans-serif; }
    main { max-width: 42rem; margin: 0 auto; padding: 1.5rem 1rem 3rem; }
    section { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 1rem 1.25rem; margin-top: 1rem; }
    h1 { font-size: 1.5rem; margin: 0; }
    h2 { font-size: 1.05rem; margin: 0 0 .75rem; }
    textarea { width: 100%; box-sizing: border-box; min-height: 5rem; font: inherit; padding: .5rem; color: inherit; background: var(--bg); border: 1px solid var(--border); border-radius: 6px; }
    button { font: inherit; padding: .45rem 1rem; margin-top: .5rem; color: #fff; background: var(--accent); border: 0; border-radius: 6px; cursor: pointer; }
    button:disabled { opacity: .6; cursor: progress; }
    .status { color: var(--muted); margin: .5rem 0 0; min-height: 1.5rem; }
    #answer { white-space: pre-wrap; margin: .75rem 0 0; }
    ul { margin: .5rem 0 0; padding-left: 1.25rem; }
  </style>
</head>
<body>
<main>
  <h1>Document Q&amp;A</h1>
  <p class="status">Upload documents, then ask questions answered from them.</p>

  <section>
    <h2>1. Upload a document</h2>
    <form id="upload-form">
      <input type="file" id="file" required>
      <button type="submit">Upload</button>
    </form>
    <p id="upload-status" class="status" role="status"></p>
  </section>

  <section>
    <h2>2. Ask a question</h2>
    <form id="ask-form">
      <textarea id="question" required placeholder="What is the equipment stipend for remote work?"></textarea>
      <button type="submit">Ask</button>
    </form>
    <p id="ask-status" class="status" role="status"></p>
    <p id="answer"></p>
    <ul id="sources"></ul>
  </section>

  <section>
    <h2>Documents loaded</h2>
    <ul id="documents"></ul>
  </section>
</main>

<script>
  // Every request goes through this one function, so a change that affects all
  // calls (a base URL, an auth header) is made in exactly one place.
  function apiFetch(path, options = {}) {
    return fetch(path, options);
  }

  const uploadForm = document.getElementById('upload-form');
  const fileInput = document.getElementById('file');
  const uploadStatus = document.getElementById('upload-status');
  const askForm = document.getElementById('ask-form');
  const questionInput = document.getElementById('question');
  const askStatus = document.getElementById('ask-status');
  const answerEl = document.getElementById('answer');
  const sourcesEl = document.getElementById('sources');
  const documentsEl = document.getElementById('documents');

  // Given: GET /documents -> [{ id, filename, content, uploadedAt }, ...]
  async function loadDocuments() {
    const res = await apiFetch('/documents');
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const docs = await res.json();
    documentsEl.replaceChildren(...docs.map((d) => {
      const li = document.createElement('li');
      li.textContent = '#' + d.id + '  ' + d.filename;
      return li;
    }));
  }

  uploadForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    // TODO 1 — upload:
    //  - take the chosen file from fileInput.files
    //  - build a FormData with ONE field named "file" holding that file
    //  - POST it to /documents/upload (no Content-Type header!)
    //  - on success show: Uploaded "<filename>" (id <id>).  in uploadStatus,
    //    then refresh the list with loadDocuments()
    //  - on failure show "Upload failed: ..." in uploadStatus
  });

  askForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    // TODO 2 — ask:
    //  - read the question from questionInput (trim it; ignore if empty)
    //  - show "Thinking..." in askStatus and clear the previous answer/sources
    //  - POST JSON { "question": ... } to /ask
    //  - on success: put data.answer in answerEl and one <li> per
    //    data.citedDocuments entry in sourcesEl
    //  - on failure show "Ask failed: ..." in askStatus
  });

  loadDocuments().catch((err) => {
    documentsEl.textContent = 'Could not load documents: ' + err.message;
  });
</script>
</body>
</html>
```

*Hints:*

- The response shapes are exactly your Java records:
  `AskRequest(String question)` → send `{"question": "..."}`;
  `AskResponse(String answer, List<String> citedDocuments)` → read
  `data.answer` and `data.citedDocuments`; `upload` returns the saved
  `Document` (`id`, `filename`, `content`, `uploadedAt`) with status `201`.
  [`Response.ok`](https://developer.mozilla.org/en-US/docs/Web/API/Response/ok)
  is true for any status in 200–299, so `201` passes the `res.ok` check.
- The multipart field name is whatever the controller's
  `@RequestParam("file")` says: **`file`**. A different name gives a `400`.
- Pass the `FormData` object directly as `body`, and **don't add a
  `Content-Type` header**. For the JSON call, you *do* set
  `'Content-Type': 'application/json'`.
- Use `element.textContent = ...` for everything that came from the
  server (primer: model output is untrusted). [`replaceChildren(...)`](https://developer.mozilla.org/en-US/docs/Web/API/Element/replaceChildren)
  with freshly created `<li>` elements, as `loadDocuments` does, replaces
  a list's contents without parsing any HTML.
- Expect `/ask` to take a few seconds: embed → pgvector → Groq. That's why
  there's a "Thinking..." state. Optionally disable the button while a
  request is in flight (`button:disabled` is already styled).

### B3. Rebuild and try it

Rebuild and restart, then open **http://localhost:8080/**. Upload
`docs/curriculum/sample-data/remote-work-policy.txt` and ask "What is the
equipment stipend for remote work?"

### B4. If you completed Day 16 (JWT)

> **Skip this subsection if you skipped Day 16.**

With Day 16's filter chain, the page itself is blocked (`401` on `/`)
and so is every call it makes. Two changes, both small:

1. **Serve the page without a token.** The HTML contains no secrets; the
   *data* is what's protected. In `SecurityConfig`, add `/` and
   `/index.html` to the `permitAll` rule that already lists `/login`. (A
   browser may also request `/favicon.ico`; that isn't permitted, so a
   `401` for it in the console is harmless.)
2. **Add a sign-in box and attach the token in `apiFetch`.** Add a small
   form (username `demo`, a password field) above the upload section that
   POSTs `{username, password}` to `/login` and keeps the returned
   `token` in a JavaScript variable. Then change `apiFetch` to add an
   `Authorization: Bearer <token>` header when a token exists. Because
   every call already goes through `apiFetch`, **nothing else in the file
   changes** — which is the point of having that seam. The Answer Key has
   the code.

A note on where the token lives: the Answer Key keeps it in a plain
variable, so a page refresh signs you out. Storing a JWT in
`localStorage` survives refreshes but makes the token readable by any
script on the page, which is why it's a debated choice. For a demo, the
in-memory version is the safer default.

---

## Checkpoint

You're done with Day 17 when the check for **your** option passes.

### Option A

1. **Swagger UI loads at its default path.**
   ```bash
   curl -sL -o /dev/null -w '%{http_code}\n' localhost:8080/swagger-ui.html
   # -> 200   (it redirects to /swagger-ui/index.html; -L follows it)
   ```
   Then open `http://localhost:8080/swagger-ui.html` in a browser. You see
   your own API title, a **Documents** group and an **Ask** group.
   *(With Day 16: also run the A6 steps first; the `curl` above passes
   without a token once the `permitAll` rule is in.)*
2. **Every endpoint is in the document.**
   ```bash
   curl -s localhost:8080/v3/api-docs | grep -o '"/[a-zA-Z{}/-]*":{'
   # -> "/documents":{   "/documents/upload":{   "/ask":{   "/documents/{id}":{
   #    (plus "/hello-groq" and "/hello-embedding" if you did not hide them)
   ```
3. **The example bodies are in the document.**
   ```bash
   curl -s localhost:8080/v3/api-docs | grep -o '"examples"' | wc -l
   # -> 3 (or more): /ask request, /ask response, upload response
   ```
4. **Every endpoint is invocable from the page.** In Swagger UI, use
   **Try it out → Execute** on each of `GET /documents`,
   `GET /documents/{id}`, `POST /documents`, `DELETE /documents/{id}`,
   `POST /documents/upload` (choose a file), and `POST /ask` (the example
   body is pre-filled). Each returns a real response from your running
   app. *(With Day 16: log in and **Authorize** first, per A6 step 4.)*

If step 2 fails with a `500` instead of JSON, you're at the `NoSuchMethodError`
from A3 — fix the pom, don't change the annotations.

### Option B

1. **The page is served, and calls the right endpoints.**
   ```bash
   curl -s -o /dev/null -w '%{http_code} %{content_type}\n' localhost:8080/
   # -> 200 text/html;charset=UTF-8
   curl -s localhost:8080/ | grep -o "apiFetch('/[a-z/]*'"
   # -> apiFetch('/documents'   apiFetch('/documents/upload'   apiFetch('/ask'
   ```
2. **Upload works in the browser.** Open `http://localhost:8080/`, choose
   `docs/curriculum/sample-data/remote-work-policy.txt`, click Upload. The
   status line reads `Uploaded "remote-work-policy.txt" (id N).` and the
   file appears under "Documents loaded".
3. **`/ask` displays in the browser.** Type "What is the equipment stipend
   for remote work?" and click Ask. After a few seconds the answer appears
   (it should mention the $500 stipend) with `remote-work-policy.txt`
   listed as a source.
4. **Errors are visible, not silent.** Create an empty file
   (`: > empty.txt`) and upload it. The status line should read
   `Upload failed: HTTP 400` if you applied
   [Day 8–10's carry-over fix 2](08-10-chunking-embedding-pipeline.md#carry-over-fixes-from-days-37)
   (an empty upload answered with `400` before Tika runs), and
   `Upload failed: HTTP 500` if you did not (Apache Tika throws
   `ZeroByteFileException` on a zero-byte stream and the controller
   returns `500`). Either way it must show an error instead of doing
   nothing.

*(With Day 16: sign in first; before signing in, step 1's `curl` still
returns `200` once `/` is permitted, and the document list reads "Could not
load documents: HTTP 401".)*

## Interview angle

*"Walk me through your project."* — and the follow-ups it invites.

Today decides what's on your screen when that question comes. Whichever
option you built, open it **before** the interview starts and lead with
the live thing, not the slides: upload a document, ask a question whose
answer is in it, point at the cited filename, then ask something that's
*not* in the documents and let the "I don't have enough information to
answer that" response show that the grounding rule from Day 11–14
actually holds. A clickable demo gets used; a README does not.

Things you can say, and defend, because you did them today:

- **"The API docs are generated from the code, not written beside it."**
  With springdoc the OpenAPI document is produced at runtime from the
  controllers and records, so it can't be stale. The annotations only add
  explanation and examples. If asked about the downside: annotation
  clutter in controllers, and the generated document is only as good as
  the types (which is a reason to keep request/response records explicit,
  as this project does).
- **A real dependency-conflict story.** "Adding springdoc worked until I
  put a `@Schema` on a record; then `/v3/api-docs` returned 500 with a
  `NoSuchMethodError` from a class I never imported. `mvn dependency:tree`
  showed an older `swagger-annotations-jakarta` pulled in by Spring AI,
  winning Maven's nearest-definition rule over springdoc's, plus a
  second, non-`jakarta` jar from the OpenAI SDK defining the same
  classes. I fixed it by importing springdoc's BOM to align the managed
  version and excluding the duplicate-package jar." Most
  candidates have a "dependency hell" story that's vague; yours has
  specific artifacts and a reason.
- **(Option B) "Why no CORS config?"** Because the page is served from
  the same origin as the API, from Spring Boot's static resources. You can
  also say what would change if the front end moved to its own dev server
  (add CORS), and why model output is written with `textContent`
  (untrusted text, XSS).
- **(If you did Day 16) "How does Swagger UI work against a secured
  API?"** The documentation paths are public, the operations are not;
  the `bearerAuth` scheme tells Swagger UI to send the JWT you got from
  `/login`.

## Answer Key (attempt it yourself first!)

Only read this after your option's checkpoint passes, or after you've been
stuck on a step for a full 30 minutes. Everything here was compiled and
booted against Spring Boot 4.1.1 / springdoc 3.1.1 before being written
down. It is one reasonable design, not the only correct one.

### Option A

**`pom.xml`** — three changes (the dependency from A1, the BOM import, and
the exclusion):

```xml
<!-- 1. in <dependencies>: the starter -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.1</version>
</dependency>

<!-- 2. on your EXISTING Spring AI starter: drop the javax-flavoured duplicate -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
    <exclusions>
        <exclusion>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- 3. in <dependencyManagement><dependencies>, next to spring-ai-bom -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-bom</artifactId>
    <version>3.1.1</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Why each: (3) springdoc's BOM manages `swagger-annotations-jakarta` at
`2.2.55`, overriding the `2.2.38` that `spring-ai-client-chat` brings;
(2) `openai-java-core` brings `io.swagger.core.v3:swagger-annotations:2.2.31`,
a non-`jakarta` artifact that defines the same `io.swagger.v3.oas.annotations.*`
classes, so aligning versions alone isn't enough — it has to go. The
dependency tree before the fix (abridged, `mvn dependency:tree -Dverbose
-Dincludes=io.swagger.core.v3`):

```
+- org.springframework.ai:spring-ai-starter-model-openai:jar:2.0.1:compile
|  +- org.springframework.ai:spring-ai-openai:jar:2.0.1:compile
|  |  \- com.openai:openai-java-core:jar:4.49.0:compile
|  |     \- io.swagger.core.v3:swagger-annotations:jar:2.2.31:compile
|  \- org.springframework.ai:spring-ai-client-chat:jar:2.0.1:compile
|     \- io.swagger.core.v3:swagger-annotations-jakarta:jar:2.2.38:compile
\- org.springdoc:springdoc-openapi-starter-webmvc-ui:jar:3.1.1:compile
   \- ...swagger-core-jakarta:jar:2.2.55:compile
      +- (io.swagger.core.v3:swagger-annotations-jakarta:jar:2.2.55:compile - omitted for conflict with 2.2.38)
```

and after (only 2.2.55 remains):

```
+- org.springframework.ai:spring-ai-starter-model-openai:jar:2.0.1:compile
|  \- org.springframework.ai:spring-ai-client-chat:jar:2.0.1:compile
|     \- io.swagger.core.v3:swagger-annotations-jakarta:jar:2.2.55:compile
```

**`AskRequest.java`**

```java
package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A natural-language question to answer from the uploaded documents.")
public record AskRequest(
    @Schema(
            description = "The question. Retrieval embeds this exact text.",
            example = "What is the equipment stipend for remote work?")
        String question) {}
```

**`AskResponse.java`**

```java
package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A grounded answer plus the filenames of the documents it was drawn from.")
public record AskResponse(
    @Schema(
            description = "The model's answer, based only on the retrieved context.",
            example = "The equipment stipend is $500. Source: remote-work-policy.txt")
        String answer,
    @Schema(description = "Distinct filenames of the documents the retrieved chunks came from.")
        List<String> citedDocuments) {}
```

**`OpenApiConfig.java`** (new)

```java
package com.manish.spring_boot_ai_project;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Document Q&A (RAG) API",
            version = "0.0.1",
            description =
                "Upload documents, then ask questions answered from their content with cited sources."))
public class OpenApiConfig {}
```

**`AskController.java`** — add these imports and annotations; the method
body is unchanged. Your existing retry annotations (any `@Retryable` /
`@Recover` you wrote on Day 15) stay exactly as they are: Day 19 replaces
them, so do not touch them today. Curriculum Day 15's `ask()` has no
controller-level retry at all and returns a `503` with a JSON `error`
body when the AI service is unavailable; if your own code has a `200`
fallback message instead, that is what yours will show.

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Ask", description = "Question answering over the ingested documents (RAG)")
public class AskController {
  // ... fields and constructor unchanged ...

  // ... your existing Day 15 annotations (if any) stay here, unchanged ...
  @Operation(
      summary = "Ask a question about the uploaded documents",
      description =
          "Embeds the question, retrieves the nearest chunks from pgvector, and asks the LLM "
              + "to answer using only that context. If the AI service is still unavailable "
              + "after the retries from Day 15, the endpoint returns 503 with an error message.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples =
                          @ExampleObject(
                              name = "Remote work stipend",
                              value =
                                  """
                                  {"question": "What is the equipment stipend for remote work?"}
                                  """))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "A grounded answer with the documents it was drawn from",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AskResponse.class),
                    examples = {
                      @ExampleObject(
                          name = "Answered",
                          value =
                              """
                              {
                                "answer": "The equipment stipend is $500. Source: remote-work-policy.txt",
                                "citedDocuments": ["remote-work-policy.txt"]
                              }
                              """),
                      @ExampleObject(
                          name = "Not in the documents",
                          value =
                              """
                              {
                                "answer": "I don't have enough information to answer that.",
                                "citedDocuments": ["remote-work-policy.txt", "onboarding-checklist.pdf"]
                              }
                              """)
                    })),
        @ApiResponse(
            responseCode = "503",
            description = "The AI service is unavailable after retries",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                """
                                {"error": "The AI service is temporarily unavailable. Please try again shortly."}
                                """)))
      })
  @PostMapping("/ask")
  public ResponseEntity<?> ask(@RequestBody AskRequest request) {
    // ... unchanged ... (use your own signature; Day 15 widened the return type to ResponseEntity<?>)
  }
}
```

The second example's `citedDocuments` is non-empty on purpose: in your
`ask()`, `citedDocuments` is built from *every retrieved chunk's* source
file, not from what the model chose to use, so even an "I don't know"
answer lists the files that were searched. (That's a Day 11–14 iteration
topic, noted here so the example is honest.)

**`DocumentController.java`** — add these imports and annotations:

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/documents")
@Tag(name = "Documents", description = "Upload, list, and delete source documents")
public class DocumentController {
  // ... fields and constructor unchanged ...

  @Operation(
      summary = "Upload a document",
      description =
          "Extracts text with Apache Tika, stores the document, then chunks, embeds, and "
              + "indexes it so /ask can retrieve from it.",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "Document stored and indexed",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Document.class),
                    examples =
                        @ExampleObject(
                            name = "remote-work-policy.txt",
                            value =
                                """
                                {
                                  "id": 1,
                                  "filename": "remote-work-policy.txt",
                                  "content": "Remote Work Policy ...",
                                  "uploadedAt": "2026-09-01T10:15:30Z"
                                }
                                """))),
        @ApiResponse(
            responseCode = "500",
            description = "Text extraction or embedding failed",
            content = @Content)
      })
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Document> upload(
      @Parameter(description = "The file to ingest (.txt, .pdf, .docx, ...)")
          @RequestParam("file")
          MultipartFile file)
      throws Exception {
    // ... unchanged ...
  }
```

**`HelloAiController.java`** — hide the Day 4 scaffolds:

```java
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
public class HelloAiController {
  // ... unchanged ...
}
```

What the generated document then contains for the multipart upload: a
`multipart/form-data` request body with a single binary property `file`
carrying your `@Parameter` description, which is what Swagger UI renders
as a file-picker.

#### Answer key: If you completed Day 16 (JWT)

**`SecurityConfig.java`** — in the filter chain, add the Swagger paths
**before** `.anyRequest()` (order is what matters; here, directly after
`/login`):

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/login").permitAll()
        .requestMatchers(
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs",
                "/v3/api-docs/**")
        .permitAll()
        .requestMatchers("/ask", "/documents/**").authenticated()
        .anyRequest().authenticated())
```

**`OpenApiConfig.java`** — add the scheme (and its two imports):

```java
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@Configuration
@OpenAPIDefinition(/* ... unchanged ... */)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
```

**`AskController.java` and `DocumentController.java`** — on each class,
next to `@Tag`:

```java
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@SecurityRequirement(name = "bearerAuth")
```

Verified result: with only springdoc added, `GET /v3/api-docs` returned
`401`; after these changes `/v3/api-docs`, `/v3/api-docs/swagger-config`,
and `/swagger-ui/index.html` return `200` and `/swagger-ui.html` returns
`302` to it, all without a token, while `POST /ask` without a token is
still `401`. The generated document contains
`"securitySchemes":{"bearerAuth":{"type":"http","scheme":"bearer","bearerFormat":"JWT"}}`
and a `"security":[{"bearerAuth":[]}]` entry on the `/ask`, `/documents`,
`/documents/upload`, and `/documents/{id}` operations; `/login` has none,
which is what you want.

### Option B

**The two handlers** — replace the `TODO` bodies in `index.html`:

```js
  uploadForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const file = fileInput.files[0];
    if (!file) return;
    const body = new FormData();
    body.append('file', file); // "file" must match @RequestParam("file")
    uploadStatus.textContent = 'Uploading and indexing...';
    try {
      // No Content-Type header: the browser adds multipart + boundary itself.
      const res = await apiFetch('/documents/upload', { method: 'POST', body });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const doc = await res.json();
      uploadStatus.textContent = 'Uploaded "' + doc.filename + '" (id ' + doc.id + ').';
      await loadDocuments();
    } catch (err) {
      uploadStatus.textContent = 'Upload failed: ' + err.message;
    }
  });

  askForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const question = questionInput.value.trim();
    if (!question) return;
    askStatus.textContent = 'Thinking...';
    answerEl.textContent = '';
    sourcesEl.replaceChildren();
    try {
      const res = await apiFetch('/ask', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question }),
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      askStatus.textContent = '';
      answerEl.textContent = data.answer; // textContent, not innerHTML
      sourcesEl.replaceChildren(...data.citedDocuments.map((name) => {
        const li = document.createElement('li');
        li.textContent = name;
        return li;
      }));
    } catch (err) {
      askStatus.textContent = 'Ask failed: ' + err.message;
    }
  });
```

This page was run against a live build of the app (served from the
`static` folder, alongside springdoc): `GET /` returned `200 text/html`,
and its script issued `GET /documents`, `POST /documents/upload`
(multipart), `GET /documents` again, and `POST /ask` (JSON) in that order.
If `/ask` shows Day 15's failure response instead of an answer (the page
prints a `503` as `Ask failed: HTTP 503`, or your own fallback text if your
code returns one with a `200`), the page is fine: the call
reached the controller and the upstream AI call failed, so check your
API keys and the app log.

#### Answer key: If you completed Day 16 (JWT)

**`SecurityConfig.java`** — permit the page itself:

```java
.requestMatchers("/", "/index.html", "/login").permitAll()
```

**`index.html`** — add a sign-in section just above "1. Upload a
document":

```html
  <section>
    <h2>0. Sign in</h2>
    <form id="login-form">
      <input id="username" value="demo" autocomplete="username">
      <input id="password" type="password" autocomplete="current-password">
      <button type="submit">Sign in</button>
    </form>
    <p id="login-status" class="status" role="status"></p>
  </section>
```

and replace `apiFetch` with this (the sign-in handler sits right after it):

```js
  let token = null; // kept in memory only, so a page refresh signs you out

  function apiFetch(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    return fetch(path, { ...options, headers });
  }

  document.getElementById('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const status = document.getElementById('login-status');
    try {
      const res = await apiFetch('/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: document.getElementById('username').value,
          password: document.getElementById('password').value,
        }),
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      token = (await res.json()).token;
      status.textContent = 'Signed in.';
      await loadDocuments();
    } catch (err) {
      status.textContent = 'Sign-in failed: ' + err.message;
    }
  });
```

Type `demo-password` into the password box and sign in. Run against a Day 16
build: before signing in, `GET /documents` was `401`; after, the page's
`/login` call returned a token, and every later call (`GET /documents`,
`POST /documents/upload`, `POST /ask`) carried `Authorization: Bearer ...`
and reached its controller.

---

Commit on your feature branch with a Conventional Commits message (e.g.
`feat: add Swagger UI via springdoc with annotated /ask and /documents/upload`
for Option A, or `feat: add single-page frontend for upload and ask` for
Option B), push, and merge via a pull request per Day 1.
