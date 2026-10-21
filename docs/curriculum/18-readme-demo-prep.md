# Day 18 — README, Sample-Question Cache, Demo Dataset Prep

**Focus:** The code is done. Today you make it *showable*: a README that
takes a stranger from a clean checkout to a working `curl /ask` call with
no undocumented steps, and a small pre-validated set of questions (with
the answers your own app gave) that you can fall back on if a live call is
slow or throttled in front of an interviewer. No new feature code today.
The outputs are two files, `README.md` and `sample-qa.md`, plus a short
demo-prep checklist you walk through once.

**Pacing (about 6 hours):**

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

**Which demo surface?** Day 17 had you build either Swagger UI (Option A)
or a one-page frontend (Option B). Your README should point at whichever
one you built. If you built both, point at the one you would actually
open in front of someone.

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
  embeds each chunk in its own call, inside the upload request). A large
  corpus uploaded just before the demo, or a rapid string of questions,
  is exactly how you meet the limit with someone watching.
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
  volumes declared in the Compose file. You need `-v` for a *truly* clean
  test, because your database lives in a named volume.
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

**There is no answer key today.** The output is your own project's
README and your own app's answers, not a fixed correct result, so there
is nothing to check against. Use the checkpoint at the bottom instead.

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
  resolves, from `application.properties` and from `@Value` annotations:

  ```bash
  grep -rhoE '\$\{[A-Z_]+\}' src/main | sort -u
  ```

  Compare that list three ways: against the names in `.env.example`,
  against the `environment:` block of the `app` service in
  `docker-compose.yml`, and against what your README tells the reader to
  put in `.env`. Every name your app needs at startup must appear in all
  three, or a fresh clone will fail to start. A key needed only by a
  leftover scaffold from an earlier day still counts if the app refuses
  to boot without it: either list it, or remove the dead code. Link back
  to [Day 1](01-day1-git-and-style.md) for why `.env` is gitignored, and
  say plainly that `.env.example` holds placeholders, never real keys.
- **3. Start everything.** The command is `docker compose up --build`
  from the repository root, and say what "ready" looks like (the log line
  `Started SpringBootAiProjectApplication`). Then handle the database:
  **your `chunk` table needs the pgvector `vector` extension, and a fresh
  database does not have it.** Day 8–10 had you run
  `CREATE EXTENSION IF NOT EXISTS vector;` by hand once. A stranger will
  not know that, and (observed against this project's code on an empty
  database) the app still starts, logs `type "vector" does not exist`, and
  does not create the `chunk` table, so uploads then fail. Two ways to
  fix it; pick one and document it:
  - *Document the manual step.* Run it once after the database container
    is healthy, then restart the app so Hibernate can create the table:

    ```bash
    docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "CREATE EXTENSION IF NOT EXISTS vector;"'
    docker compose restart app
    ```
  - *Automate it (preferred; one fewer step to document).* Put the one
    SQL line in a file such as `docker/initdb/01-vector.sql` and mount
    that directory into the `db` service at
    `/docker-entrypoint-initdb.d` (see the Postgres image link above).
    The catch, from the same source: init scripts run only when the data
    directory is empty, so you must test it with `docker compose down -v`
    first, or the old volume skips them.
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
  limits (and that `/ask` returns a "busy or unavailable" message after
  retries are exhausted; Day 15), and the upload size limit (see the
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
the prompt. If you get the "busy or unavailable" message, wait a minute
and retry; that is the throttling this whole day is about, and a good
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
  `application.properties`. Spring Boot's default for
  `spring.servlet.multipart.max-file-size` is **1MB**, and for
  `spring.servlet.multipart.max-request-size` it is **10MB** (see the
  Common Application Properties and `MultipartProperties` links above).
  An interviewer who says "try it with this PDF" will hand you something
  bigger than 1MB. Against this project, a 2MB upload with the defaults
  returns `413`. Add the two lines from Day 8–10 fix 5
  (`spring.servlet.multipart.max-file-size=10MB` and
  `spring.servlet.multipart.max-request-size=10MB`), restart, and
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

```bash
# 0. Stop and wipe the current stack, including the database volume.
docker compose down -v

# 1. Fresh clone in a throwaway directory (use your own repo URL).
git clone <your-repo-url> /tmp/readme-test
cd /tmp/readme-test
```

Now open `/tmp/readme-test/README.md` and follow **only** what it says,
top to bottom: create `.env` from `.env.example`, `docker compose up
--build`, upload the six sample files, ask a question. Do not copy
anything from your original working directory, and do not "just fix" a
problem silently. Every time you have to do something the README did not
tell you to do, you have found a bug in the README: fix the README (and
re-run from step 0 if the fix changes the order).

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
3. `README.md` and `sample-qa.md` are committed on your feature branch
   (a Conventional Commit such as `docs: add README and sample Q&A`),
   and nothing real is in `.env.example`.

Step 0 matters for two reasons: it frees ports 8080 and 5432 from your
working stack, and `-v` discards its database volume. After you fix the
README and want to repeat the test inside `/tmp/readme-test`, run
`docker compose down -v` *there* first. Without `-v` the old database
volume survives, the `vector` extension (and the `chunk` table) is
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
  skipped. A key your app reads but your example env file or Compose file
  omits is another one. This is a better story than "I wrote a README."
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
