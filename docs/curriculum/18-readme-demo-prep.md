# Day 18 — README, Sample-Question Cache, Demo Dataset Prep

**Focus:** The code is done. Today you make it *showable*: a README that
takes a stranger from a clean checkout to a working `curl /ask` call with
no undocumented steps, and a small pre-validated set of questions (with
the answers your own app gave) that you can fall back on if a live call is
slow or throttled in front of an interviewer. No new feature code today.
The outputs are two files, `README.md` and `sample-qa.md`, plus a short
demo-prep checklist you walk through once.

**Pacing (about 6 hours):**

- Carry-over fixes (below): ~45 minutes.
- README draft: ~1.5 hours.
- Clean-checkout test and fixing what it finds: ~1.5–2 hours. This is the
  step that always takes longer than planned, because it is where the
  undocumented steps surface.
- Sample questions run against the live app and saved to `sample-qa.md`:
  ~1 hour.
- Demo-prep checklist: ~1 hour.
- Buffer: ~30 minutes.

**You skipped or finished Day 16 (JWT)?** Both work. The main path below
assumes no security. If you did Day 16, there is one clearly marked note
("If you completed Day 16") in the README exercise; ignore it otherwise.

**Do the [carry-over fixes](#carry-over-fixes-before-the-clean-clone-test)
first.** The clean-clone test at the end of today fails on four things in
the app as it stands, and none of them is a README problem. Fixing them
before writing the README means the test finds README gaps instead.

**Which demo surface?** Day 17 had you build either Swagger UI (Option A)
or a one-page frontend (Option B). Your README should point at whichever
one you built. If you built both, point at the one you would actually
open in front of someone.

## Carry-over fixes before the clean-clone test

A review of the app found four problems that a stranger's fresh clone will
hit, and that your own machine hides because it has a warm database, a
`.env` full of keys and a build that already worked. Each one is something
today's checkpoint would otherwise fail on. Fix them on their own branch
and merge it before starting the README branch:

```bash
git checkout -b fix/carry-over-clean-clone
```

**1. `mvnw` isn't executable in Git.** The `Dockerfile` runs
`RUN ./mvnw -q -DskipTests package`. Git records a file's executable bit,
and `mvnw` is stored as mode `100644` (not executable). Whether that bites
depends on the machine that builds the image; a clone on Linux, macOS or
WSL fails the build with `/bin/sh: 1: ./mvnw: Permission denied`. (Observed
by building your `Dockerfile` from a `git archive` of the repository, which
keeps the stored modes.) Record the bit with
[`git update-index --chmod=+x`](https://git-scm.com/docs/git-update-index)
and commit it:

```bash
git update-index --chmod=+x mvnw
git commit -m "fix: make mvnw executable"
```

*Check:* [`git ls-files -s mvnw`](https://git-scm.com/docs/git-ls-files)
prints a line that starts with `100755` instead of `100644`.

**2. The Jina key never reaches the container.** `ChunkingPipeline` reads
`${JINA_API_KEY}` and your `app` service in `docker-compose.yml` passes
`GROQ_API_KEY` and `HF_API_KEY`, but not the Jina key, and `.env.example`
doesn't list it either. Your `.dockerignore` keeps `.env` out of the image,
so the `environment:` block is the *only* way a key reaches the container.
Result: the container exits at startup with `Could not resolve placeholder
'JINA_API_KEY'`, and a reader copying `.env.example` has no idea the key
exists. Add the key to both files, next to the others.

*Check:* `docker compose config` prints `JINA_API_KEY` under the `app`
service's `environment:` (with the value from your `.env`), and
`grep JINA .env.example` finds the placeholder. If you have done or will do
[Day 19](19-oop-retrospective.md#8-update-what-days-17-and-18-produced),
it adds `EMBEDDINGS_PROVIDER` to the same two files; you don't need it
today.

**3. The app won't start without a Hugging Face key.** `HelloAiController`
(the Day 4 hello-world endpoints) takes `@Value("${HF_API_KEY}")` with no
default, and Spring refuses to start on an unresolvable placeholder. Run
the image with the Jina key and no `HF_API_KEY` variable at all and it
stops with `Could not resolve placeholder 'HF_API_KEY'`. (With your current
`docker-compose.yml` you may not see it, because Compose passes an *empty*
`HF_API_KEY` when the variable isn't in `.env`, and an empty value
resolves. It bites a Jina-only reader who drops the line, runs the jar
directly, or follows a README that stops listing the key.) Two fixes;
pick one:

- *Keep the endpoints, make the key optional* (the Answer Key does this).
  Give the placeholder an empty default, `${HF_API_KEY:}` (Spring's
  [`@Value` default syntax](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html)),
  and make `/hello-embedding` answer with a clear `503` JSON error when the
  key is blank, instead of calling Hugging Face with `Bearer ` and a
  confusing failure.
- *Delete the Day 4 hello controller*, if you no longer use it. Then no
  Hugging Face key is read anywhere, and the key can leave `.env.example`
  and the compose file. Deleting also removes the Day 17 `@Hidden` and the
  two hello rows in its endpoint table, and means you skip Day 19's
  `HelloAiController` steps.

*Check:* the key-grep from step 1 of the exercise below
(`grep -rhoE '\$\{[A-Z_]+(:[^}]*)?\}' src/main | sort -u`) shows
`${HF_API_KEY:}` with its default (or no `HF_API_KEY` at all if you deleted
the controller), and with the key blank, the app starts and
`curl localhost:8080/hello-embedding` returns a `503` with an `error`
message naming `HF_API_KEY`. Day 19 later routes this endpoint through the
`EmbeddingProvider`, which removes the Hugging Face dependency here
altogether.

**4. A fresh database has no pgvector extension.** Nothing in the app or
the compose file runs `CREATE EXTENSION vector`. You ran it by hand on Day
8–10, so your volume has it. A stranger's empty volume doesn't, and
(observed on an empty volume) the app still starts, logs `ERROR: type
"vector" does not exist` while Hibernate tries to create `chunk`, and
never creates the table, so every upload then fails. Fix it where the
database is created: the official Postgres image runs `*.sql` files from
`/docker-entrypoint-initdb.d` on first start
([Docker Official Image: postgres, Initialization scripts](https://hub.docker.com/_/postgres)).
Put the one SQL line in `docker/initdb/01-vector.sql` and mount that
folder, read-only, into the `db` service.

Two things to know:

- **Init scripts run only when the data directory is empty** (same
  source). On your existing volume the script does nothing, which is fine,
  because your extension already exists. It matters for the clone.
- **Make the healthcheck wait for the real server.** `pg_isready` with no
  `-h` connects through a Unix-domain socket (libpq's default: see
  [the `host` parameter](https://www.postgresql.org/docs/current/libpq-connect.html)),
  and during first-time initialization the image runs a temporary server
  that is reachable only that way. Observed with a deliberately slow init
  script: the container reported *healthy* after 4 seconds while TCP port
  connections were refused for another 12, so `depends_on:
  condition: service_healthy` could start the app too early. Adding
  `-h localhost`
  ([`pg_isready -h`](https://www.postgresql.org/docs/current/app-pg-isready.html))
  makes the check use TCP, and it only turned healthy once the server was
  really up. Your script is instant, so you'd rarely hit this, but it costs
  one flag.

*Check:* the script only fires on an empty volume, so test with a throwaway
Compose project name, which gets its own new volume. Stop your stack first,
because both would publish port 5432:

```bash
docker compose down
docker compose -p vectorcheck up -d --wait db
docker compose -p vectorcheck exec db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select extname from pg_extension"'
# -> plpgsql
#    vector
docker compose -p vectorcheck down -v
docker compose up -d      # your own stack again
```

(`-p` is Compose's
[project-name option](https://docs.docker.com/reference/cli/docker/compose/),
[`--wait`](https://docs.docker.com/reference/cli/docker/compose/up/) waits
for the service to be healthy, and `down -v` throws the throwaway volume
away. Your own volume is untouched.) The full clean-clone test below checks
the same thing end to end.

If you'd rather not add a file, the manual alternative is a README step: once
the `db` container is healthy, run
`docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "CREATE EXTENSION IF NOT EXISTS vector;"'`
and then `docker compose restart app` so Hibernate can create the table.
It works, but it is one more step for a stranger to get wrong, so the init
script is the better trade.

**Carry-over checkpoint:** `git ls-files -s mvnw` shows `100755`;
`docker compose config` lists `JINA_API_KEY` for the `app`; the throwaway
project above lists `vector`; `docker compose up --build` on your own stack
still starts cleanly. Commit each fix separately, merge through a PR
(`fix: carry over clean-clone blockers`), then branch the README work
from the updated `main`. The answer key for these four fixes is at the
bottom of this file.

## Concept primer

### Why a demo wants a small, known-good dataset

A live demo has a failure mode a normal development session does not: an
audience. You are talking, someone is watching the screen, and a
10-second hang or an error body reads as "it doesn't work," whatever the
cause. Two things in this stack make that risk real:

- **Free-tier APIs throttle.** Day 15 covered it: when you exceed a
  limit, Groq's API "returns a `429 Too Many Requests` HTTP status code,"
  and limits apply at the organization level and vary by plan. Your
  `/ask` call hits the model API on every request; uploading a document
  hits your embeddings API *once per chunk* (your `ChunkingPipeline`
  embeds each chunk in its own call, inside the upload request). Every
  `/ask` also makes one embeddings call, for the question itself, before
  the model call. A large corpus uploaded just before the demo, or a rapid
  string of questions, is exactly how you meet the limit with someone
  watching.
- **A big or unfamiliar corpus makes answers harder to verify.** With the
  six synthetic Acme Robotics documents in `docs/curriculum/sample-data/`
  you know the right answer to every question before you ask it, because
  each fact (a dollar amount, a deadline, a threshold) appears in exactly
  one document. That is what lets you say "and that's the correct
  answer, cited to the right file" instead of hoping.

So the demo set is deliberately small and fixed: the six files you have
used since Day 6–7, no new documents. Today's job is to *rehearse* against
it, and to write down what good looks like.

### Why save question-and-answer pairs at all

Model output varies between runs, so a saved answer is not a test, and
you should never present it as a live result. It is a **fallback and a
reference**: if the live call is slow, you can say "this is what it
returned when I ran it this morning," and show the saved pair. It is also
a record of the *expected key fact* for each question, so on the day you
can tell a grounded answer from a wrong one at a glance. You wrote the
expected fact down from the source document, not from the model.

### A README is the demo that works when you are not there

Interviewers, and anyone you send the repository to, often read the
README *before* they ever see the app run. GitHub's own documentation
frames a README as the place that tells visitors what the project does,
why it is useful, how to get started, where to get help, and who
maintains it. Make a README's guide adds a practical rule of thumb: be
explicit in installation steps because the reader may be a novice, and
keep usage examples as small as possible, linking out for longer ones.

The test of a README is not whether it reads well. It is whether someone
who has never seen the project can follow it, top to bottom, with a
terminal and nothing else. Today's checkpoint is exactly that test.

## Reference links

**README structure**

- [Make a README](https://www.makeareadme.com/) — Danny Guo's guide. The
  section list in "Suggestions for a good README" (Name, Description,
  Badges, Visuals, Installation, Usage, Support, Roadmap, Contributing,
  Authors and acknowledgment, License, Project status) is the source for
  the heading scaffold below, trimmed to what a demo project needs. It
  also says installation steps should be explicit and usage examples
  small.
- [GitHub Docs — About READMEs](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes)
  — what a README should cover (what the project does, why it is useful,
  how to get started, where to get help, who maintains it) and that
  GitHub surfaces a README from the repository root, `docs/`, or `.github/`
  automatically.

**Commands used in the README and the clean-checkout test**

- [`docker compose up`](https://docs.docker.com/reference/cli/docker/compose/up/)
  — "Builds, (re)creates, starts, and attaches to containers for a
  service"; `--build` rebuilds images first, `-d` runs detached.
- [`docker compose down`](https://docs.docker.com/reference/cli/docker/compose/down/)
  — "Stop and remove containers, networks"; `-v` also removes the named
  volumes declared in the Compose file. Your database lives in a named
  volume, so `-v` is what makes a *repeat* test inside the clone truly
  clean (see the checkpoint).
- [curl manual](https://curl.se/docs/manpage.html) — `-F` posts
  `multipart/form-data`, and `@filename` attaches a file as an upload
  (used for `/documents/upload`); `-d` sends the request body and `-H`
  adds a header (used for `/ask`).
- [Docker Official Image: postgres — Initialization scripts](https://hub.docker.com/_/postgres)
  — `*.sql` files placed in `/docker-entrypoint-initdb.d` run on first
  start, but only when the container starts with an empty data directory,
  in sorted filename order. Used for the `vector` extension below.
- [`git ls-files`](https://git-scm.com/docs/git-ls-files) and
  [`git log`](https://git-scm.com/docs/git-log) — used by the secrets
  check. `ls-files` lists tracked files by default; `git log --all
  --oneline -- <path>` lists commits on any ref that touched a path.

**Limits you will mention in the README**

- [Groq — Rate Limits](https://console.groq.com/docs/rate-limits) — the
  `429` status and `retry-after` header (already cited on Day 15).
- [Spring Boot — Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)
  — lists `spring.servlet.multipart.max-file-size` as "Max file size.
  1MB" and `spring.servlet.multipart.max-request-size` as "Max request
  size. 10MB". The defaults are also visible in source, in Spring Boot
  4.1.1's
  [`MultipartProperties`](https://github.com/spring-projects/spring-boot/blob/v4.1.1/module/spring-boot-servlet/src/main/java/org/springframework/boot/servlet/autoconfigure/MultipartProperties.java):
  `maxFileSize = DataSize.ofMegabytes(1)` and
  `maxRequestSize = DataSize.ofMegabytes(10)`.
- [Jina AI — Embedding API](https://jina.ai/embeddings/) — only relevant
  if, like some learners, you swapped your embeddings provider from
  Hugging Face to Jina AI after Day 4; it is the page where that provider's
  API key and free trial are described.

**Carry-over fixes (above)**

- [`git update-index`](https://git-scm.com/docs/git-update-index) —
  `--chmod=(+|-)x` sets the execute permission on the updated files, which
  is how `mvnw` becomes mode `100755` without leaving the repository.
  [`git ls-files --stage`](https://git-scm.com/docs/git-ls-files) shows the
  stored mode.
- [Docker Official Image: postgres](https://hub.docker.com/_/postgres) —
  scripts in `/docker-entrypoint-initdb.d` run only "if you start the
  container with a data directory that is empty" (same page as above).
- [`pg_isready`](https://www.postgresql.org/docs/current/app-pg-isready.html)
  (`-h` takes a host name, or a socket directory if it begins with a slash)
  and libpq's
  [`host` parameter](https://www.postgresql.org/docs/current/libpq-connect.html)
  (no host means a Unix-domain socket). The "temporary server is
  socket-only" part is an observation, not a documented guarantee.
- [Compose file reference — `volumes`](https://docs.docker.com/reference/compose-file/services/#volumes)
  — the short syntax `HOST:CONTAINER_PATH:ro` for a read-only bind mount;
  [`docker compose up --wait`](https://docs.docker.com/reference/cli/docker/compose/up/)
  and the [`-p` project-name option](https://docs.docker.com/reference/cli/docker/compose/).
- [Spring Framework — `@Value`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html)
  — the `${name:default}` syntax.

**Internal (already verified on earlier days)**

- [Day 1 — `.gitignore` and the `.env` habit](01-day1-git-and-style.md)
  — why `.env` is gitignored. Link to it from the README rather than
  re-explaining it.
- [Day 4 — keys in `.env`, read via `spring.config.import`](04-day4-groq-embeddings-hello-world.md)
  — how the keys reach your app.
- [Day 6–7 — the scanned-PDF failure mode](06-07-file-upload-tika.md)
- [Day 8–10 — optional multipart limit (fix 5)](08-10-chunking-embedding-pipeline.md)
- [Day 11–14 — the two known-answer questions](11-14-ask-endpoint-rag-pipeline.md)
- [Day 16 — `/login` and bearer tokens](16-jwt-security.md) (only if you did it)
- [Day 17 — the demo surface](17-frontend-api-docs-polish.md)

## Exercise (guided TODO + hints)

Do today's work on a feature branch, per Day 1:

```bash
git checkout -b docs/readme-and-demo-prep
```

**There is no answer key for the exercise.** The output is your own
project's README and your own app's answers, not a fixed correct result,
so there is nothing to check against; use the checkpoint at the bottom
instead. (The answer key at the bottom of this file covers only the four
carry-over fixes above.)

### 1. TODO — write the project README

Create `README.md` at the repository root with the headings below, in this
order. The headings are scaffolding; the prose is yours. Each section has
a hint, not an answer.

```markdown
# <Project name>

<One sentence: what it is, who it is for.>

## What it does
## How it works
## Prerequisites
## Setup
### 1. Clone
### 2. Configure your keys
### 3. Start everything
## Try it
### Upload the sample documents
### Ask a question
## Demo surface
## Sample questions
## Known limitations
## Tech stack
```

Hints, section by section:

- **What it does / How it works.** Two or three sentences each. For "how
  it works", the pipeline in one line is enough (upload → extract text →
  chunk → embed → store in pgvector; ask → embed question → nearest
  chunks → grounded prompt → answer with cited documents). A diagram is
  optional. Do not paste code.
- **Prerequisites.** What a stranger must have *before* step 1: Docker,
  Git, and the API keys with where to get them. For your own keys, link
  the pages you used on Day 4 rather than describing sign-up flows.
- **2. Configure your keys.** Write down the keys *your app actually
  reads*, not the ones the curriculum used. Your project may differ from
  the curriculum here (for example, a different embeddings provider than
  Hugging Face), so find the list in your code rather than recalling it.
  This one-liner prints every `${VARIABLE}` placeholder the application
  resolves, from your Spring config file (`application.properties` or
  `application.yml`) and from `@Value` annotations:

  ```bash
  grep -rhoE '\$\{[A-Z_]+(:[^}]*)?\}' src/main | sort -u
  ```

  The `(:[^}]*)?` part matches placeholders that carry a default, such as
  `${EMBEDDINGS_PROVIDER:huggingface}` or `${HF_API_KEY:}`, as well as
  plain `${NAME}` ones. A placeholder with a default will not stop the app
  booting without it, but it is still a name a reader may need to set, so
  decide for each whether it belongs in `.env.example`.

  Compare that list three ways: against the names in `.env.example`,
  against the `environment:` block of the `app` service in
  `docker-compose.yml`, and against what your README tells the reader to
  put in `.env`. Every name your app needs at startup must appear in all
  three, or a fresh clone will fail to start. A key needed only by a
  leftover scaffold from an earlier day still counts if the app refuses
  to boot without it: either list it, or remove the dead code (carry-over
  fix 3 above is exactly this case for `HF_API_KEY`). Link back
  to [Day 1](01-day1-git-and-style.md) for why `.env` is gitignored, and
  say plainly that `.env.example` holds placeholders, never real keys.
- **3. Start everything.** The command is `docker compose up --build`
  from the repository root, and say what "ready" looks like (the log line
  `Started SpringBootAiProjectApplication`). Then explain the database in one
  sentence: the `vector` extension is created automatically the first time
  the database volume is created (carry-over fix 4 above), so a reader
  has nothing to run by hand. Add the one catch for someone re-running it:
  init scripts only run on an *empty* volume, so a volume created before
  the script existed needs `docker compose down -v` (which deletes its
  data) before the extension appears. If you chose the manual alternative in fix 4 instead, the manual
  `CREATE EXTENSION` step and the `docker compose restart app` after it
  belong here, in the README. The `./mvnw` permission problem is carry-over
  fix 1; if a reader reports `permission denied` at the build step, that
  fix didn't make it into the commit they cloned.
- **Upload the sample documents.** The upload endpoint takes a multipart
  form field named `file` at `/documents/upload`. A loop over all six
  files saves typing and shows the status code per file:

  ```bash
  for f in docs/curriculum/sample-data/*.txt docs/curriculum/sample-data/*.pdf; do
    curl -s -o /dev/null -w "$f -> %{http_code}\n" \
      -F "file=@$f" localhost:8080/documents/upload
  done
  ```

  Each line should end `-> 201`. Say in the README that this call
  embeds every chunk of every file, so it takes a while and uses
  embeddings quota.
- **Ask a question.** One `curl` against `/ask`, with the request body
  `{"question": "..."}` and `Content-Type: application/json`, and a
  sentence on what the response contains (`answer`, `citedDocuments`).
  Reuse the Day 11–14 equipment-stipend question here.
- **Demo surface.** One or two sentences and the URL: Swagger UI at
  `http://localhost:8080/swagger-ui.html` (Day 17, Option A) or the page
  at `http://localhost:8080/` (Day 17, Option B). Remove the heading if you
  built neither.
- **Sample questions.** A pointer to `sample-qa.md` (exercise 2).
- **Known limitations.** Be honest and specific. At minimum: the scanned
  vendor contract cannot be read (no OCR; Day 6–7), the free-tier rate
  limits (and what `/ask` returns after retries are exhausted: Day 15's
  failure response, a `503` JSON error, or your own fallback text if
  your code differs; Day 15), and the upload size limit (see the
  checklist below). A short, accurate limitations section reads as
  engineering judgment, not as weakness.
- **Tech stack.** One line: Spring Boot 4.1.1, Spring AI 2.0.1, Java 17,
  Postgres with pgvector, Groq for the LLM, your embeddings provider,
  Apache Tika.

> **If you completed Day 16 (JWT):** your `/ask` and `/documents/upload`
> now return `401` without a token, so the "Try it" commands above are
> wrong for a stranger. Add a step before them that gets a token from
> `/login`, and add the header to both calls. Match your Day 16 code
> exactly. With the Day 16 hardcoded demo credentials:
>
> ```bash
> curl -X POST localhost:8080/login \
>   -H "Content-Type: application/json" \
>   -d '{"username": "demo", "password": "demo-password"}'
> # -> {"token":"eyJhbGciOiJIUzI1NiJ9....."}
> export TOKEN="eyJhbGciOiJIUzI1NiJ9....."
>
> curl -X POST localhost:8080/ask \
>   -H "Content-Type: application/json" \
>   -H "Authorization: Bearer $TOKEN" \
>   -d '{"question": "What is the equipment stipend for remote work?"}'
> ```
>
> The upload loop gets the same `-H "Authorization: Bearer $TOKEN"`.
> Say in the README that the credentials are demo-only and hardcoded
> (Day 16's own code comment flags this as a shortcut), and that the token expires after
> one hour, so a README reader or you on demo day must log in again
> rather than reusing an old token.

### 2. TODO — pre-write the sample questions and save the answers

Use the fixed set already in `docs/curriculum/sample-data/`. **No new
documents.** The table below gives seven questions: the two from the Day
11–14 checkpoint, then five more covering the other three retrievable
documents. For each one, the **expected key fact** was checked against
the text of the source file itself (`pdftotext` for the PDFs), so you can
judge your app's answer against the document, not against the model.

| # | Question | Expected key fact | Source file |
|---|---|---|---|
| 1 | What is the equipment stipend for remote work? | **$500**, a one-time annual stipend for home office equipment | `remote-work-policy.txt` |
| 2 | How many requests per minute is the API rate limit? | **60** requests per minute per API key (introduced in v2.2.0) | `api-changelog.txt` |
| 3 | Who assigns the onboarding buddy, and when? | **The hiring manager**, in **Week 1** | `onboarding-checklist.pdf` |
| 4 | What is the travel per diem? | **$75 per day**, to cover meals, on approved business travel; not reduced for partial days | `expense-reimbursement-policy.pdf` |
| 5 | How soon after a Sev1 incident is resolved must the postmortem be written? | **Within 5 business days** of resolution (Sev2 needs one too) | `incident-response-runbook.pdf` |
| 6 | How quickly must the engineering director be notified of a Sev1 incident? | **Within 15 minutes** of detection (the on-call engineer is paged immediately) | `incident-response-runbook.pdf` |
| 7 | Which expenses need manager approval before payout? | Expenses of **$150 or more**; expenses **under $150** are auto-approved | `expense-reimbursement-policy.pdf` |

Questions 1 and 2 are the Day 11–14 pair, word for word. Questions 3, 5, 6
and 7 each sit next to a *different* number or role in the same document,
on purpose, so a wrong answer is easy to spot: the manager's 30-day
check-in is not the buddy assignment; the 10-minute status-page deadline
and the 15-minute director notification are not the 5-day postmortem; and
the `$150` boundary is "under $150" versus "$150 or more". A good
answer states the right one. A plausible-sounding wrong one is exactly the
failure you want to catch *now*, not in the interview.

The exact lines these were verified against (from the source files):

- `remote-work-policy.txt`: "Remote employees receive a one-time annual
  stipend of $500 to cover home office equipment..."
- `api-changelog.txt`, v2.2.0: "Introduced rate limiting of 60 requests
  per minute per API key across all endpoints."
- `onboarding-checklist.pdf`, Week 1: "The hiring manager assigns an
  onboarding buddy to help the new hire navigate their first weeks."
- `expense-reimbursement-policy.pdf`, section 3: "Employees on approved
  business travel receive a per diem of $75 per day to cover meals. The
  per diem is not reduced for partial travel days."
- `incident-response-runbook.pdf`, Postmortem Requirement: "A written
  postmortem is required for every Sev1 and Sev2 incident, due within 5
  business days of resolution."
- `incident-response-runbook.pdf`, Escalation: "A Sev1 incident pages the
  on-call engineer immediately and notifies the engineering director
  within 15 minutes of detection."
- `expense-reimbursement-policy.pdf`, section 2: "Expenses under $150 are
  automatically approved on submission. Expenses of $150 or more require
  explicit approval from the employee's manager before payout is
  processed."

Now run each question once against your live app, with all six sample
documents uploaded. A small shell function keeps it quick (questions
contain no double quotes, so the JSON is safe):

```bash
ask() {
  curl -s -X POST localhost:8080/ask \
    -H "Content-Type: application/json" \
    -d "{\"question\": \"$1\"}"
  echo
}

ask "What is the equipment stipend for remote work?"
ask "How many requests per minute is the API rate limit?"
ask "Who assigns the onboarding buddy, and when?"
ask "What is the travel per diem?"
ask "How soon after a Sev1 incident is resolved must the postmortem be written?"
ask "How quickly must the engineering director be notified of a Sev1 incident?"
ask "Which expenses need manager approval before payout?"
```

(If you did Day 16, add `-H "Authorization: Bearer $TOKEN"` to the
`curl` inside `ask`.) For each question, compare the `answer` to the
expected key fact and check that the expected source file appears in
`citedDocuments`. Remember from Day 11–14 that `citedDocuments` lists
every document that was *retrieved*, not only the ones the model used,
so extra files in the list are normal; a **missing** expected file is the
signal. If a question comes back wrong or generic, work the Day 11–14
"Iteration" checklist (look at what was retrieved first) before touching
the prompt. If you get Day 15's failure response (a `503` JSON error, or your
own fallback text), wait a minute and retry; that is the throttling this whole day is about, and a good
reason to space your runs out.

Save the results to **`sample-qa.md`** at the repository root, in this
shape (the prose and the answers are yours):

```markdown
# Sample questions and answers

Recorded <date> against the six documents in docs/curriculum/sample-data/.
Model output varies between runs. These are a reference and a fallback,
not a test.

## 1. What is the equipment stipend for remote work?
**Expected key fact:** $500 (remote-work-policy.txt)
**Answer returned:** <paste your app's answer>
**Cited documents:** <paste citedDocuments>

## 2. ...
```

Finish `sample-qa.md` with one line, kept short, for the deliberate
exception: a question about the vendor contract (for example, "What is
the liability cap in the vendor contract?"). `vendor-contract-summary-scanned.pdf`
is an image-only PDF, so extraction returned no text on Day 6–7, no chunks
were stored for it, and it can never be retrieved. A correct response is
the "I don't have enough information to answer that" message, **not** a
dollar figure or a vendor name; if the model produces specifics, it made
them up. Run it once yourself, and keep the line ready for the interviewer
who asks "what happens if I ask about a document you can't actually read?"
The honest answer is the Day 6–7 lesson: the file uploaded without an
error, but there was no text layer to read, and OCR is a separate step
that this project does not include.

### 3. Demo-prep checklist (walk through it once)

Do each item and tick it off. None of these are code, but each one is the
sort of thing that goes wrong on the day.

- [ ] **Raise the upload size limit (carry-over from Day 8–10).** Day 8–10
  taught this as optional (fix 5) and it is probably not in your
  Spring config file. Spring Boot's default for
  `spring.servlet.multipart.max-file-size` is **1MB**, and for
  `spring.servlet.multipart.max-request-size` it is **10MB** (see the
  Common Application Properties and `MultipartProperties` links above).
  An interviewer who says "try it with this PDF" will hand you something
  bigger than 1MB. Against this project, a 2MB upload with the defaults
  returns `413`. Add the two lines from Day 8–10 fix 5
  (`spring.servlet.multipart.max-file-size=10MB` and
  `spring.servlet.multipart.max-request-size=10MB`; in `application.yml`
  form that is `max-file-size: 10MB` and `max-request-size: 10MB` nested under
  `spring:` / `servlet:` / `multipart:`), restart, and
  re-try a >1MB file. Then remember the other side: a bigger PDF means
  more chunks, and each chunk is a separate embeddings call inside the
  upload request, so a large file can be slow or hit the free-tier limit.
  Mention that in the README's Known limitations rather than discovering
  it live.
- [ ] **Secrets check.** Nothing real may be in the repository. The first
  command should print only `.env.example`-style files, never a plain
  `.env`; the second should print nothing:

  ```bash
  git ls-files | grep -i env
  git log --all --oneline -- .env
  ```

  If a real key ever was committed, rotate it at the provider; deleting
  the file from history later does not un-leak it. (Why this matters:
  Day 1.)
- [ ] **Warm start.** On demo day, start Docker and the app 15 minutes
  early and ask one question, so the first thing the audience sees is not
  a cold start or a first-call retry.
- [ ] **Pre-load the data.** Upload the six sample files before the
  meeting, not during it. Uploading embeds every chunk; it is the slowest
  and most quota-hungry call you have.
- [ ] **Space out your live questions.** Plan three or four live
  questions, not twenty. Pick them from `sample-qa.md`.
- [ ] **Have the fallback open.** `sample-qa.md` in a second window, plus
  Day 17's demo surface in a browser tab already loaded.
- [ ] **(If you did Day 16)** get a fresh token just before the demo; the
  one from this morning may have expired.
- [ ] **Include one "not in the documents" question** in your plan, as Day
  17's interview angle suggests: ask something the six documents do not
  cover and let the "I don't have enough information to answer that."
  response show that grounding works.

## Checkpoint

A person unfamiliar with the project can follow your README from a clean
checkout to a working `curl /ask` call, with no undocumented steps. You
are the unfamiliar person, so make the test honest: start from nothing.

A clone contains only what is **committed**, so commit first. Commit
`README.md` and every other file your README relies on (a changed
`docker-compose.yml`, `.env.example`, an init script) on your feature
branch, in the original repository. You do not need to push: the test
clones from the local repository on disk.

```bash
# In your original repository, on the feature branch:
git add README.md sample-qa.md   # plus any compose/.env.example/init-script fixes
git commit -m "docs: add README and sample Q&A"
pwd   # note this path; it is <path-to-your-repo> below

# 0. Stop the current stack to free ports 8080 and 5432. Plain `down`
#    keeps your database volume (and your uploaded documents) intact.
docker compose down

# 1. Fresh clone of that branch, from the local path, into a throwaway directory.
git clone --branch docs/readme-and-demo-prep <path-to-your-repo> /tmp/readme-test
cd /tmp/readme-test
```

Now open `/tmp/readme-test/README.md` and follow **only** what it says,
top to bottom: create `.env` from `.env.example`, `docker compose up
--build`, upload the six sample files, ask a question. Do not copy
anything from your original working directory, and do not "just fix" a
problem silently. Every time you have to do something the README did not
tell you to do, you have found a bug in the README.

**Make each fix in your original repository, not in the clone** (a fix
made in `/tmp/readme-test` is lost, and one made uncommitted in the
original is invisible to the clone). Commit it on the same branch, then
bring the clone up to date with `git -C /tmp/readme-test pull`, or throw
it away and repeat from step 0. Before you delete it, run
`docker compose down -v` inside `/tmp/readme-test`, then
`rm -rf /tmp/readme-test`: a fresh clone at the same path is the same
Compose project, so it would reuse the old `readme-test_pgdata` volume, and
a re-run could pass on stale state. (The `rm -rf` is needed because
`git clone` refuses to write into a directory that already exists and is
not empty.)

You pass when this works in the clean checkout, using only README steps:

```bash
curl -s -X POST localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the equipment stipend for remote work?"}'
```

The response contains `$500` in `answer`, and `remote-work-policy.txt`
in `citedDocuments`. (If you did Day 16, the README's token step comes
first and the call carries `-H "Authorization: Bearer $TOKEN"`.)

Also confirm, in the same session:

1. All seven questions in `sample-qa.md` have a saved answer and cite the
   expected file, and the vendor-contract line is present.
2. Every item in the demo-prep checklist is ticked, including the
   multipart limit.
3. Everything the test relied on is committed on your feature branch (the
   clone proved it, since it had nothing else), and nothing real is in
   `.env.example`.
4. The clone built the image (carry-over fix 1), started the `app` with the
   Jina key from its own `.env` (fix 2), and created the `chunk` table on
   its own empty volume with no manual `CREATE EXTENSION` (fix 4). If any of
   those needed a hand-typed command, that fix isn't committed on the branch
   you cloned.

Step 0 frees ports 8080 and 5432 from your working stack. It does not
need `-v`: Compose names volumes after the project directory, so the clone
in `/tmp/readme-test` gets its own, empty volume (`readme-test_pgdata`,
or whatever your volume is called) and the test is already clean. Running
`down -v` in your original repository would only destroy the documents,
embeddings (which cost API quota to rebuild) and the hand-created `vector`
extension from your working database. After you fix the README and want
to repeat the test inside `/tmp/readme-test`, run `docker compose down -v`
*there* first. Without `-v` the clone's old database volume survives, the `vector` extension (and the `chunk` table) is
already in it, and the test passes even though a stranger's would not.

## Interview angle

*"How do you make sure a demo doesn't fall over?"* and, more usefully,
*"Tell me about the project's documentation."*

Documentation and demo reliability are part of the deliverable, not an
afterthought, and today is the evidence. What you can say, and defend,
because you did it:

- **"I tested the README from a clean clone, with the volumes wiped, and
  it found real gaps."** Be specific. On an empty database the app starts
  but does not create the `chunk` table until the `vector` extension
  exists, which a README written from a working machine would have
  skipped; I fixed it with an init script, not a manual step. A key your
  app reads but your example env file or Compose file omits is another one,
  and so is a build script that is not executable in Git. This is a better
  story than "I wrote a README."
- **"I designed the demo around the failure modes I knew about."** Free-tier
  throttling is real (you handled it on Day 15); so the demo uses a small
  fixed corpus, pre-loaded data, a warm start, a short list of known-answer
  questions, and a saved fallback with the expected fact for each. Say
  that the saved answers are a reference, not a test, because model
  output varies.
- **"I documented the limits honestly."** The scanned PDF that cannot be
  read, the 1MB default upload cap (and what I raised it to), and the
  per-chunk embeddings cost of large uploads. If asked "what happens if I
  ask about a document you can't read?", you have a one-line answer that
  explains the Day 6–7 behaviour instead of bluffing.
- **The honest next step, if asked:** OCR for scanned documents as a
  separate pipeline stage, and moving embedding off the request thread so
  large uploads don't block.

## Answer Key (attempt it yourself first!)

Only read this after the carry-over checkpoint passes. It covers the four
carry-over fixes only; the README and `sample-qa.md` are yours. Everything
here was built and run against a copy of the app: the `Dockerfile` failed
with `Permission denied` before fix 1 and built after it; the `app` service
configuration listed `JINA_API_KEY`; on a brand-new volume the `vector`
extension existed and Hibernate created `chunk`; the app started with a
Jina key and no `HF_API_KEY` variable at all; an empty upload returned
`400`; and deleting an uploaded document left `0` rows for its id in `chunk`.
(Jina was faked by a local server returning
`{"data":[{"embedding":[...384 numbers...]}]}`, so no real key was used.)

**Fix 1: `mvnw` executable bit**

```bash
git update-index --chmod=+x mvnw
git commit -m "fix: make mvnw executable"
git ls-files -s mvnw
# 100755 <hash> 0	mvnw
```

**Fixes 2 and 4: `docker-compose.yml`** (changes: the init-script mount, the
healthcheck's `-h localhost`, and `JINA_API_KEY`; everything else is as you
had it)

```yaml
services:
  db:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./docker/initdb:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -h localhost -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      GROQ_API_KEY: ${GROQ_API_KEY}
      HF_API_KEY: ${HF_API_KEY}
      JINA_API_KEY: ${JINA_API_KEY}
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy
volumes:
    pgdata:
```

**Fix 2: `.env.example`**

```
POSTGRES_DB=ragdb
POSTGRES_USER=raguser
POSTGRES_PASSWORD=ragpassword123
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/ragdb
SPRING_DATASOURCE_USERNAME=raguser
SPRING_DATASOURCE_PASSWORD=ragpassword123
GROQ_API_KEY=PASTE_YOUR_NEW_GROQ_KEY_HERE
JINA_API_KEY=PASTE_YOUR_NEW_JINA_KEY_HERE
HF_API_KEY=PASTE_YOUR_NEW_HF_KEY_HERE
```

**Fix 4: `docker/initdb/01-vector.sql`** (a new file; commit it)

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

**Fix 3: `HelloAiController.java`** (option 1, keep the endpoints; the
`/hello-groq` method is unchanged and the Hugging Face call keeps its
parsing)

```java
package com.manish.spring_boot_ai_project;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloAiController {

  private final ChatClient chatClient;
  private final String hfApiKey;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public HelloAiController(
      ChatClient.Builder chatClientBuilder, @Value("${HF_API_KEY:}") String hfApiKey) {
    this.chatClient = chatClientBuilder.build();
    this.hfApiKey = hfApiKey;
  }

  // 1. LLM Chat Completion (Groq)
  @GetMapping("/hello-groq")
  public String helloGroq() {
    return chatClient.prompt("Say hello in one creative sentence.").call().content();
  }

  // 2. Vector Embedding (Hugging Face) -> Returns 384-dimensional vector
  @GetMapping("/hello-embedding")
  public ResponseEntity<?> helloEmbedding() throws Exception {
    if (hfApiKey.isBlank()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("error", "HF_API_KEY is not set. Add it to .env to use this endpoint."));
    }

    String url =
        "https://router.huggingface.co/hf-inference/models/"
            + "sentence-transformers/all-MiniLM-L6-v2/pipeline/feature-extraction";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + hfApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"inputs\":\"hello world\"}"))
            .build();

    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

    // Parse array-of-arrays returned by Hugging Face: [[0.012, -0.045, ...]]
    try {
      return ResponseEntity.ok(objectMapper.readValue(response.body(), float[].class));
    } catch (Exception e) {
      float[][] nested = objectMapper.readValue(response.body(), float[][].class);
      return ResponseEntity.ok(nested[0]);
    }
  }
}
```

Option 2 is simply deleting `HelloAiController.java`. If you do, also remove
`HF_API_KEY` from `.env.example` and the `app` service in
`docker-compose.yml`, and the README's key list.
