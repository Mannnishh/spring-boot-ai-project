# Day 3 — Finish CRUD Skeleton + Docker Compose

**Focus:** Finish the CRUD skeleton Day 1 started (entity, JPA
repository, REST controller), then stand up Postgres + pgvector with
Docker Compose and wire the app to it.

Two things happen today that set up the rest of the project. First, the
`Document` entity and its CRUD controller become the foundation every
later day builds on — Day 6–7 uploads into it, Day 8–10 stores chunks
against it. Second, Postgres runs via Docker Compose from here on, so
the database is one reproducible `docker compose up` away instead of a
machine-specific local install. Today's work is familiar Spring
territory (guided TODO with hints and an answer key at the bottom),
plus a Windows-specific Docker/WSL2 setup section that's genuinely new
if you've never used Docker before.

## Concept primer

### What pgvector adds to plain Postgres

Postgres is a relational database: tables, rows, SQL. By itself it has
no notion of "which rows are *most similar to* this vector." pgvector
is an extension that adds exactly that — a `vector` column type for
storing embeddings and a set of similarity/distance operators you can
use in ordinary SQL queries. The extension is distributed as, among
other things, an official
[Docker image](https://hub.docker.com/r/pgvector/pgvector) that adds
the extension on top of the standard Postgres image. Today you are
*not* using the vector type yet — you're only standing up Postgres via
that image so it's already in place, with pgvector installed, when
Day 8–10 starts storing embeddings. Enabling the extension inside your
database and querying against the `vector` type is Day 8–10's job.

### Why Postgres via Docker Compose instead of a local install

A local Postgres install lives on one machine and drifts: whatever
version your OS package manager happened to give you, whatever config
was set at install time, whatever you forgot you changed six months
ago. Docker Compose inverts that. The database is declared in a
[Compose file](https://docs.docker.com/reference/compose-file/) — the
exact image and tag, the credentials, the port, the volume — and
`docker compose up` recreates that exact environment on any machine
with Docker installed. For this project that matters for three reasons:

1. **Reproducibility.** The `pgvector/pgvector` image pins both Postgres
   and the pgvector extension at once, so the Day 8–10 vector queries
   work against the same stack no matter where you run them.
2. **One command to start and stop.** No service registration, no
   background daemon on the host that survives reboots in some unknown
   state. `docker compose up` starts it; `docker compose down` stops it.
3. **The same shape as deployment.** The Compose file also declares the
   *app* service, so the whole stack (database + Spring Boot app) is
   described in one place. That's a real interview answer later — "how
   do you run this locally?" is answered by pointing at one file.

The trade-off is a thin layer of Docker concepts up front, which is the
point of the Windows setup primer below (and the reason `docker run
hello-world` is the first exercise step).

## Windows setup primer

If you're on Windows and have never used Docker, this is the one-time
setup that makes everything else today work. Do it once and you won't
touch it again for the rest of the curriculum.

**What WSL2 actually is, in plain language.** WSL2 is a real Linux
kernel running *alongside* Windows, inside a lightweight virtual
machine, rather than a translation layer that pretends to be Linux.
Microsoft's
[Install WSL page](https://learn.microsoft.com/en-us/windows/wsl/install)
describes it this way: the current version installs WSL 2, which uses a
lightweight VM architecture to run an actual Linux kernel on Windows.

**Why Docker Desktop needs it.** Linux containers are, at the bottom,
Linux processes — namespaces, cgroups, a Linux kernel. Windows can't
run those processes natively, so something has to provide a Linux
kernel. That something is WSL2. Docker Desktop is the UI and
orchestration layer on top; WSL2 is what actually executes the
containers. Docker's
[Install Docker Desktop on Windows](https://docs.docker.com/desktop/setup/install/windows-install/)
page confirms the arrangement: WSL 2 is the *default* Linux-containers
backend, and Docker Desktop requires WSL version 2.1.5 or later.

**Setup steps (one-time):**

1. Open **PowerShell as administrator** and run:
   ```powershell
   wsl --install
   ```
   This installs WSL 2 and a default Linux distribution. **Reboot if
   prompted** — the installer will tell you if it's needed. (Microsoft's
   [Install WSL](https://learn.microsoft.com/en-us/windows/wsl/install)
   page is the reference if anything here misbehaves.)
2. **Install Docker Desktop** from the
   [official Windows install page](https://docs.docker.com/desktop/setup/install/windows-install/).
   Run the installer and restart when it asks.
3. **Confirm the WSL2 backend is selected.** Open Docker Desktop →
   Settings, and confirm the engine is using the **WSL 2 based** engine
   rather than the legacy Hyper-V backend. The Docker install page above
   covers the current Settings layout; if it looks different, the thing
   to look for is any switch between "WSL 2" and "Hyper-V" — WSL 2 is
   the one you want.

That's the whole setup. Every later `docker`/`docker compose` command in
this curriculum assumes Docker Desktop is running with that WSL2
backend.

## Reference links

**Spring / Java**

- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/) —
  the official reference for JPA repositories (`JpaRepository`, query
  methods, and the persistence model you use today).
- [Spring Boot Reference — Web (Servlet)](https://docs.spring.io/spring-boot/reference/web/servlet.html) —
  the "Developing Web Applications" section: `@RestController`,
  `@RequestMapping`, `@GetMapping`, `@PathVariable`, `@DeleteMapping`,
  and how Spring MVC serves JSON.
- [Spring Boot Application Properties appendix](https://docs.spring.io/spring-boot/appendix/application-properties/index.html) —
  the authoritative list of every `spring.*` configuration property.
- [Spring Boot Reference — Data Access (SQL Databases)](https://docs.spring.io/spring-boot/reference/data/sql.html) —
  the "DataSource Configuration" section that explicitly documents
  `spring.datasource.url`, `spring.datasource.username`,
  `spring.datasource.password`, and `spring.datasource.driver-class-name`.

**Docker / Postgres / pgvector**

- [Docker Compose file reference](https://docs.docker.com/reference/compose-file/) —
  the canonical Compose spec (services, `image`, `environment`, `ports`,
  `volumes`, `depends_on`, and `.env` variable substitution).
- [pgvector/pgvector on Docker Hub](https://hub.docker.com/r/pgvector/pgvector) —
  the official image that adds pgvector to the Postgres image.
- [pgvector README — Docker section](https://github.com/pgvector/pgvector#docker) —
  the authoritative source for the image tag. The tag it documents as
  primary is `pgvector/pgvector:pg18-trixie` (`docker pull
  pgvector/pgvector:pg18-trixie`); a version-pinned alternative is
  `0.8.6-pg18-trixie`.
- [hello-world on Docker Hub](https://hub.docker.com/_/hello-world) —
  documents the exact output that proves your Docker install works.

**Windows / WSL2**

- [Install Docker Desktop on Windows](https://docs.docker.com/desktop/setup/install/windows-install/) —
  WSL 2 is the default Linux-containers backend; requires WSL 2.1.5 or
  later.
- [Install WSL (Microsoft)](https://learn.microsoft.com/en-us/windows/wsl/install) —
  `wsl --install`, and the WSL 2 "lightweight VM running a real Linux
  kernel" explanation.

## Java pattern note

**Java pattern note:** Spring's `@Autowired`/constructor injection of
the repository into the controller is the same **Dependency
Injection / Inversion of Control** pattern you hand-built on
[Day 2](02-day2-java-oop-refresher.md) — the controller declares what
it needs (`DocumentRepository`) as a constructor parameter instead of
creating it, and Spring's container supplies it. That container is
playing the exact role your `Main` class played on Day 2: it's the
composition root. The only difference is that Spring automates the
wiring you wrote out by hand. (Same detail carries over, too: a class
with a single constructor doesn't even need the `@Autowired`
annotation — see Day 2's section 7.)

## Exercise (guided TODO + hints)

Do today's work on a new feature branch, per Day 1's workflow:

```bash
git checkout -b feature/crud-docker
```

### 1. Verify Docker works at all

```bash
docker run hello-world
```

You should see Docker's documented
[confirmation message](https://hub.docker.com/_/hello-world): "Hello
from Docker! This message shows that your installation appears to be
working correctly," followed by the client → daemon → pull → run →
stream walkthrough. Don't continue until this prints — every later step
assumes Docker can actually pull and run images. If it fails, go back
to the Windows setup primer (or Docker's install page) before touching
anything else.

### 2. Define the `Document` entity and a JPA repository

Create an entity class `Document` in your project's base package
(`com.example.demo` unless you changed it at generation). It needs, at
minimum:

- `Long id` — `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`
  (let Postgres assign the id);
- `String filename`;
- `Instant uploadedAt` (`java.time.Instant` — you'll set it automatically
  on Day 6–7; today it can be whatever the request sends, or null);
- a no-arg constructor and getters/setters — JPA requires the no-arg
  constructor, and the controller's JSON serialization uses the getters.

Then add the repository. This one is a single line:

```java
public interface DocumentRepository extends JpaRepository<Document, Long> {}
```

*Hint: that's genuinely all of it — `JpaRepository<Document, Long>`
gives you `findAll()`, `findById(id)`, `save(...)`, `existsById(id)`,
and `deleteById(id)` with no implementation, as documented in the
[Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/).*

### 3. Write the CRUD controller

Create `DocumentController`. The class and method signatures below are
the scaffolding — fill in the method bodies marked `// TODO`.

```java
package com.example.demo;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentRepository repository;

    public DocumentController(DocumentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Document> findAll() {
        // TODO: return all documents
        return null;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> findById(@PathVariable Long id) {
        // TODO: 200 with the document, or 404 if no document has this id
        return null;
    }

    @PostMapping
    public ResponseEntity<Document> create(@RequestBody Document document) {
        // TODO: save it, return 201 with the saved copy (it now has an id)
        return null;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // TODO: 204 if it existed and was deleted, 404 if nothing to delete
        return null;
    }
}
```

*Hints:*

- `findAll()` → `repository.findAll()` returns the `List<Document>`.
- `findById(id)` → `repository.findById(id)` returns
  `Optional<Document>` (you met `Optional` on Day 2 — use `map` and
  `orElse`/`orElseGet`). What HTTP status should a missing id produce?
- `create(...)` → `repository.save(document)` returns the saved entity
  with its generated `id`. What status signals "created"? (201, not 200.)
- `delete(id)` → check `existsById(id)` first so you can distinguish
  "deleted, nothing to return" (204) from "there was nothing there"
  (404).

*Annotation reference: `@RestController`, `@GetMapping`,
`@PostMapping`, `@DeleteMapping`, `@PathVariable`, and `@RequestBody`
are all covered on the Spring Boot
[web (servlet) reference](https://docs.spring.io/spring-boot/reference/web/servlet.html).*

### 4. Write `docker-compose.yml` + a `.env` file

Create `docker-compose.yml` at the project root with two services.

**The database service** (this part is spelled out because the image tag
is the one thing you shouldn't guess — it's the tag the
[pgvector README](https://github.com/pgvector/pgvector#docker) documents
as primary):

```yaml
services:
  db:
    image: pgvector/pgvector:pg18-trixie
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10
```

*A version-pinned alternative to `pg18-trixie` is
`0.8.6-pg18-trixie` — either is correct; the unpinned one tracks the
latest patch of the Postgres 18 / Debian "trixie" line. Both come from
the same [official image](https://hub.docker.com/r/pgvector/pgvector).*

**The app service** — fill in the missing `environment:` entries
(marked with the `<...>` placeholders) yourself:

```yaml
  app:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: <fill in>
      SPRING_DATASOURCE_PASSWORD: <fill in>
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy

volumes:
  pgdata:
```

*Hints:*

- No credentials go in this file. `POSTGRES_DB`/`POSTGRES_USER`/
  `POSTGRES_PASSWORD` are read from a `.env` file via Compose's variable
  substitution (documented in the
  [Compose file reference](https://docs.docker.com/reference/compose-file/)).
  So `SPRING_DATASOURCE_USERNAME` is `${POSTGRES_USER}`, and
  `SPRING_DATASOURCE_PASSWORD` is `${POSTGRES_PASSWORD}`.
- Inside the container the database host is the service name `db`, not
  `localhost` — hence `jdbc:postgresql://db:5432/...`.
- `build: .` means Compose builds the app from a `Dockerfile` in this
  directory, so add one (see the unverified-image note below). Because
  the build context is the whole project, add a `.dockerignore` next to
  it so `.env`, `.git/`, and build output never get baked into the
  image:
  ```
  .git/
  .env
  target/
  build/
  .idea/
  .vscode/
  ```
- Create `.env` (same directory as `docker-compose.yml`) with real
  *local* values:
  ```
  POSTGRES_DB=ragdb
  POSTGRES_USER=raguser
  POSTGRES_PASSWORD=change-me-to-something-long
  ```
  This file is **already gitignored** from Day 1 — that's the whole
  point of the `.env` entry in your `.gitignore`.

*Base-image note: `eclipse-temurin:17-jdk` is a real, maintained tag
(Java 17 matches the `javaVersion=17` you gave Spring Initializr on
Day 1). It is listed in the
[Eclipse Temurin official image](https://hub.docker.com/_/eclipse-temurin)
definition that Docker Hub publishes from, [`docker-library/official-images`](https://github.com/docker-library/official-images/blob/master/library/eclipse-temurin).
A minimal version:*

```dockerfile
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN ./mvnw -q -DskipTests package
CMD ["sh", "-c", "java -jar target/*.jar"]
```

*(That assumes the Maven route from Day 1; if you chose Gradle, swap
the `RUN` for `./gradlew bootJar` and run the jar from
`build/libs/`. Swap the Java version number to match whatever Day 1
generated if you didn't use 17.)*

### 5. Wire `application.yml` to the Compose Postgres service

In `src/main/resources/application.yml`, wire the datasource to the
environment variables the app service sets:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

*Hints:*

- The `${SPRING_DATASOURCE_URL}` placeholders reference the environment
  variables Compose passes to the app container — Spring resolves them
  because environment variables are part of its externalized
  configuration.
- `driver-class-name: org.postgresql.Driver` names the PostgreSQL JDBC
  driver you already added on Day 1. All four `spring.datasource.*`
  keys are documented on the
  [Data Access reference](https://docs.spring.io/spring-boot/reference/data/sql.html),
  and the full property list lives in the
  [application properties appendix](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).
- `ddl-auto: update` tells Hibernate to create/update the `document`
  table from your entity on startup. That's the dev convenience that
  makes the checkpoint below return a real (empty) table without you
  writing any SQL. It's a development setting — don't ship `update` to
  production.

## Checkpoint

1. `docker compose up` (or `docker-compose up` if you're on Compose v1)
   builds the app image, starts Postgres + pgvector, then starts the
   app.
2. `curl localhost:8080/documents` returns `200` with a JSON array —
   empty (`[]`) is fine and expected, since no rows exist yet.
3. `docker compose down` stops the stack.
4. Commit on the feature branch with a Conventional Commits message
   (e.g. `feat: finish document CRUD and wire postgres via docker compose`),
   push it, and merge it to `main` via a pull request — Day 1's
   feature-branch workflow, practiced for real. `git log` should show
   the merge, not a direct commit to `main`.

## Interview angle

*"Why colocate Postgres + pgvector instead of a separate vector
database?"* — after today you have a first-hand answer: running pgvector
as an extension inside the Postgres you already have means **one** data
store to run, back up, and secure, and your relational data (the
`document` row) lives next to its vectors instead of in a second
system you have to keep consistent. The trade-off is that a dedicated
vector database invests in specialized indexing and scaling for
billions of vectors; this project's dataset is nowhere near that
threshold, so the fewer-moving-parts option wins — and knowing *where*
that trade-off flips is exactly what a good answer names.

## Answer Key (attempt it yourself first!)

Only read this after you've written your own versions. This is one
reasonable trade-off, not the only correct answer.

**`Document.java`**

```java
package com.example.demo;

import java.time.Instant;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    private Instant uploadedAt;

    protected Document() {
        // JPA requires a no-arg constructor.
    }

    public Document(String filename, Instant uploadedAt) {
        this.filename = filename;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
```

**`DocumentRepository.java`**

```java
package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
```

**`DocumentController.java`**

```java
package com.example.demo;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentRepository repository;

    public DocumentController(DocumentRepository repository) {
        this.repository = repository;
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
    public ResponseEntity<Document> create(@RequestBody Document document) {
        Document saved = repository.save(document);
        return ResponseEntity
                .created(URI.create("/documents/" + saved.getId()))
                .body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
```

**`docker-compose.yml`**

```yaml
services:
  db:
    image: pgvector/pgvector:pg18-trixie
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy

volumes:
  pgdata:
```

**`application.yml`**

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
```

**`.env`** (gitignored — real local values, never committed)

```
POSTGRES_DB=ragdb
POSTGRES_USER=raguser
POSTGRES_PASSWORD=change-me-to-something-long
```
