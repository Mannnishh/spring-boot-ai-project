# Day 1 — Git Workflow, Code Style, IDE Setup & Skeleton Start

**Focus:** Git/branching/commit hygiene + code style tooling + IDE setup
(morning). Spring Boot CRUD skeleton starts (afternoon).

No Spring Boot code gets written until the habits around it are in place.
Everything below is about the workflow you'll use for the next 21 days —
and it's also a ready-made answer to an interview question about how you
work on a team.

## Concept primer

### Why feature branches, even solo

On a solo project it's tempting to commit straight to `main` — there's no
one to review your work, so what's the branch for? A few reasons that hold
up even without a team:

- **Isolation.** A branch is where in-progress, possibly-broken work lives.
  `main` stays in a state you could hand to someone (or a future employer's
  screen-share) at any moment.
- **Reviewable units of work.** Even solo, opening a pull request against
  your own branch gives you a diff view of the whole feature before it
  lands — a different, better vantage point than reading files as you write
  them.
- **The habit transfers directly.** This is exactly the branch-per-feature,
  PR-to-merge workflow GitHub documents as [GitHub flow](https://docs.github.com/en/get-started/quickstart/github-flow),
  and it's the same shape you'll use on any team. Practicing it now, with a
  low-stakes solo repo, means it's muscle memory by the time it matters in
  an interview or a new job.

Under the hood, a Git branch is just a lightweight, movable pointer to a
commit — that's *why* branching and merging are cheap operations in Git in
the first place, unlike in older version control systems. The Git SCM book
covers this well: [Git Branching — Branches in a Nutshell](https://git-scm.com/book/en/v2/Git-Branching-Branches-in-a-Nutshell).

### What `.gitignore` protects against

Two different problems, one file:

1. **Noise.** Build output (`target/`, `build/`) and IDE metadata
   (`.idea/`, `.vscode/`) are regenerated locally and don't belong in
   history — committing them bloats diffs and creates merge conflicts over
   files nobody actually edited by hand.
2. **Secrets.** `.env` files hold API keys (Groq, Hugging Face — see Day 4). A
   `.gitignore` entry for `.env` is the first, cheapest line of defense
   against ever pushing a real credential to a public GitHub repo. It's not
   a complete secrets story (Day 4 covers that properly), but it's the
   habit that has to start on day one, before the first key exists.

### Why automated formatting is worth wiring in now

A code formatter (Spotless or Google Java Format) that runs as a build task
removes an entire category of pull-request bikeshedding — brace placement,
import ordering, line length — by making it not a matter of opinion. The
build fails (or a `check` task reports non-zero) if code isn't formatted;
running `apply` fixes it automatically. There's nothing to debate in code
review because there's nothing left to disagree about. This is a small
thing to set up on Day 1, but it's also a concrete answer to "how do you
keep code style consistent across a team" — see the interview angle below.

### Why Conventional Commits, specifically

"Write clear commit messages" is advice everyone gives and nobody can check
mechanically. [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
is a *specific, machine-parseable* format for commit messages:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

For example: `feat(upload): add multipart endpoint` or
`fix(auth): handle expired JWT gracefully`. The common `type` values are
`feat` (a new feature), `fix` (a bug fix), `docs`, `refactor`, `test`, and
`chore`; the optional `scope` in parentheses names the affected area of the
codebase. A `!` after the type/scope (or a `BREAKING CHANGE:` footer)
flags a breaking change.

The reason this matters beyond tidiness: because the format is
machine-parseable, tooling can walk your commit history and generate a
changelog, decide the next semantic version number, or filter history by
type (`git log --grep="^feat"`) — none of which is possible from freeform
prose, no matter how well-written. This curriculum uses Conventional
Commits for every example commit from here on.

## Reference links

- [GitHub flow](https://docs.github.com/en/get-started/quickstart/github-flow) —
  GitHub's own docs on the feature-branch-and-pull-request workflow.
- [Git Branching — Branches in a Nutshell](https://git-scm.com/book/en/v2/Git-Branching-Branches-in-a-Nutshell) —
  the official Git SCM book chapter on what a branch actually is.
- [Conventional Commits v1.0.0 specification](https://www.conventionalcommits.org/en/v1.0.0/) —
  the full spec this curriculum uses for commit messages.
- [GitHub docs: Ignoring files](https://docs.github.com/en/get-started/getting-started-with-git/ignoring-files) —
  how `.gitignore` works and where to put one.
- [github/gitignore](https://github.com/github/gitignore) — GitHub's
  official collection of `.gitignore` templates, including
  [`Java.gitignore`](https://github.com/github/gitignore/blob/main/Java.gitignore),
  [`Global/JetBrains.gitignore`](https://github.com/github/gitignore/blob/main/Global/JetBrains.gitignore)
  (covers `.idea/`), and
  [`Global/VisualStudioCode.gitignore`](https://github.com/github/gitignore/blob/main/Global/VisualStudioCode.gitignore)
  (covers `.vscode/`).
- [Spring Initializr](https://start.spring.io) — the web UI for generating
  a Spring Boot project skeleton by picking dependencies (Web, JPA,
  PostgreSQL Driver, etc.), a build system (Maven or Gradle), and a Java
  version.
- [Spring Initializr reference docs — generating a project](https://docs.spring.io/initializr/docs/current/reference/html/#generating-project) —
  covers the same generation via `curl`/HTTPie from the command line
  instead of the web form.
- [Spotless](https://github.com/diffplug/spotless) — the code formatter
  this curriculum uses, with setup instructions for
  [Gradle](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
  and [Maven](https://github.com/diffplug/spotless/tree/main/plugin-maven).
- [`git config` docs — `core.autocrlf`](https://git-scm.com/docs/git-config#Documentation/git-config.txt-coreautocrlf) —
  Git's official documentation of the line-ending setting used below.
- [IntelliJ IDEA download page](https://www.jetbrains.com/idea/download/) —
  official download for Windows/macOS/Linux.
- [The Unified IntelliJ IDEA (JetBrains blog)](https://blog.jetbrains.com/idea/2025/12/intellij-idea-unified-release/) —
  explains what's free vs. what's part of the paid Ultimate subscription
  (see IDE setup note below).
- [JetBrains Student Pack](https://www.jetbrains.com/academy/student-pack/) —
  free Ultimate subscription for verified students/teachers.

## Windows setup note

Before touching anything else, run:

```bash
git config --global core.autocrlf true
```

Windows text editors traditionally use CRLF line endings; Git repositories
(this one included) standardize on LF. Without this setting, every file in
the repo will show as "modified" the moment you check it out, purely
because of line-ending differences — not because you changed anything.
`core.autocrlf true` tells Git to convert LF → CRLF on checkout and back to
LF on commit automatically, so your working copy looks normal in a Windows
editor while the repository itself stays consistent. (If you're on
macOS/Linux instead, the equivalent is `core.autocrlf input` — convert on
commit only, since those platforms already use LF.) This is documented
directly on Git's official `core.autocrlf` config page linked above.

## IDE setup note

**Recommended: IntelliJ IDEA.** As of the 2025.3 "unified" release,
JetBrains merged what used to be two separate products — Community Edition
and Ultimate — into one installer. The free tier (what used to be called
Community Edition) now includes everything this project needs out of the
box: the Spring Boot project wizard, Spring Initializr integration, run
configurations, and — as of that same release — database connections,
schema browsing, and full SQL support. None of that requires payment or
an account. An optional **Ultimate subscription** (free 30-day trial, and
free indefinitely with a JetBrains student/teacher license if you have
one) unlocks additional "extended tooling" beyond that free baseline, but
nothing in this curriculum depends on it. Download it from the
[official download page](https://www.jetbrains.com/idea/download/).

**Lighter-weight alternative: VS Code.** If you already prefer VS Code,
install the Java Extension Pack and the Spring Boot Extension Pack — it's
a perfectly capable alternative for everything in this curriculum, just
with a bit more manual extension setup than IntelliJ's batteries-included
Spring tooling.

## Exercise (guided TODO — familiar territory)

Work through these in order. Each has a hint if you get stuck.

1. **Install your IDE.**
   Install IntelliJ IDEA (the free tier is sufficient — see the IDE note
   above), or confirm VS Code has the Java Extension Pack and Spring Boot
   Extension Pack installed if you're going that route.
   *Hint: IntelliJ's installer needs ~2.5 GB free disk space and 2 GB RAM
   minimum, per the download page.*

2. **Initialize the repo and add a `.gitignore`.**
   Run `git init` if this isn't already a Git repo. Create a `.gitignore`
   that covers at minimum: `.env`, `target/` (Maven build output) or
   `build/` (Gradle build output), `.idea/` (IntelliJ project metadata),
   and `.vscode/` (VS Code project metadata).
   *Hint: don't write these patterns from scratch — copy the relevant
   sections from GitHub's own `Java.gitignore`, `Global/JetBrains.gitignore`,
   and `Global/VisualStudioCode.gitignore` templates (linked above) into
   one combined `.gitignore` file.*

3. **Create a feature branch.**
   `git checkout -b feature/project-skeleton`
   *Hint: this is the branch you'll commit the skeleton to in step 6 — the
   idea is that `main` never sees a direct commit, even solo (see the
   concept primer above).*

4. **Generate the project skeleton with Spring Initializr.**
   Use whichever route you prefer — they all produce the same result:
   - Web UI: visit [start.spring.io](https://start.spring.io), pick
     Maven or Gradle, Java, and the latest stable Spring Boot version, then
     add the **Spring Web**, **Spring Data JPA**, and **PostgreSQL Driver**
     dependencies. Click Generate to download a zip.
   - `curl`, if you'd rather stay in the terminal:
     ```bash
     curl "https://start.spring.io/starter.zip" \
       -d dependencies=web,data-jpa,postgresql \
       -d type=maven-project \
       -d javaVersion=17 \
       -o project.zip
     ```
     (swap `type=maven-project` for `type=gradle-project` if you prefer
     Gradle — both are real, generation-ready values on the live service).
   - IntelliJ's built-in wizard: **File → New → Project → Spring
     Initializr**, which drives the same start.spring.io service without
     leaving the IDE.

   Unzip into your project directory, if you didn't generate directly
   inside it.
   *Note: this is deliberately just Web + JPA + Postgres driver for now —
   the exact pgvector dependency gets pinned on Day 3 once the Docker
   Postgres/pgvector setup is in place.*

5. **Add a formatter to the build and run it once.**
   Add Spotless with Google Java Format to whichever build file you have.

   Gradle (`build.gradle`):
   ```gradle
   plugins {
     id 'com.diffplug.spotless' version '8.10.1'
   }

   spotless {
     java {
       googleJavaFormat()
     }
   }
   ```
   Run `./gradlew spotlessApply` once to format the generated skeleton,
   then `./gradlew spotlessCheck` to confirm it now passes clean.

   Maven (`pom.xml`, inside `<build><plugins>`):
   ```xml
   <plugin>
     <groupId>com.diffplug.spotless</groupId>
     <artifactId>spotless-maven-plugin</artifactId>
     <version>3.10.1</version>
     <configuration>
       <java>
         <googleJavaFormat/>
       </java>
     </configuration>
   </plugin>
   ```
   Run `mvn spotless:apply` once, then `mvn spotless:check` to confirm.
   *Hint: check the [Spotless releases page](https://github.com/diffplug/spotless/releases)
   for whatever the current plugin version is if the ones above have moved
   on by the time you get here — the `java { googleJavaFormat() }` /
   `<java><googleJavaFormat/></java>` shape itself is stable.*

6. **Commit the skeleton on the feature branch.**
   Stage everything and commit using a Conventional Commits message:
   ```bash
   git add .
   git commit -m "feat: scaffold spring boot project skeleton"
   ```
   *Hint: `feat` because this is new functionality (the skeleton itself)
   landing for the first time — not a `fix`, `chore`, or `docs` change.*

## Checkpoint

- `git log --oneline` shows at least one commit on `feature/project-skeleton`
  whose message matches the Conventional Commits `type: description`
  format (e.g. `feat: scaffold spring boot project skeleton`).
- Running the formatter's check task exits `0`:
  `./gradlew spotlessCheck` or `mvn spotless:check`.

## Interview angle

*"How do you structure your git workflow and keep code style consistent
across a team?"* — after today you have a concrete, first-hand answer
instead of a textbook one: feature branches off `main` merged via
reviewed pull requests, Conventional Commits so history and (eventually)
changelogs are machine-generatable rather than hand-maintained, and an
automated formatter wired into the build so style is enforced by tooling,
not code-review debate.
