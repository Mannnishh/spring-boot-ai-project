# Day 20–21 — Buffer Day, then a Mock Interview Run-Through

**Focus:** Two process days, no new technical content. **Day 20** is
slack: it exists so that a day that ran long earlier in the plan does not
turn into a half-finished project on Day 21. **Day 21** is a rehearsal:
you talk through the project out loud, demo it live, and answer questions
about it, against a timer, as if the interviewer were in the room. There
is no new code on either day.

**Pacing (about 6 hours per day):**

- **Day 20:** as much of the six hours as you need to finish whatever you
  are behind on, using the checklist below. If you are *not* behind, see
  "If you are on schedule" below instead of inventing new work.
- **Day 21:**
  - Prepare: ~2.5 hours (set up the demo, compile your gotcha questions,
    jot architecture notes).
  - Timed run-throughs: ~2.5 hours (at 10–15 minutes each, plus a reset
    and a review after each, that is three or four attempts).
  - Review and fix: ~1 hour.

## Day 20 — Buffer

### Purpose

Nothing new is introduced today. Every day from Day 1 to Day 19 has a
checkpoint, and some of them will have taken longer than six hours. Day 20
is where that slippage lands. The rule for the day is simple: **finish
what is broken or missing before you polish anything.** A project that
runs from a clean checkout, with every earlier checkpoint passing, is a
better Day 21 demo than a project with one extra feature.

### If you are behind: cut in this order

Work down the list and stop as soon as you are caught up. Day 16 is the
only day the schedule marks optional. Days 17 and 18 come next because each
has a built-in lighter path (Day 17's Swagger-only Option A; a shorter
sample-question list on Day 18). Each item points back to its day so you
can see exactly what you would be dropping.

1. **Cut Day 16 (JWT) first.** The [schedule in the overview](00-overview.md#full-schedule)
   marks it "optional — cut first if behind", and [Day 16](16-jwt-security.md)
   says at the top that nothing from Day 17 onward depends on it. *You
   already skipped Day 16, so this first cut is already taken. Nothing to
   do here, and it also means every "If you completed Day 16" note in Day
   17 and Day 18 does not apply to you.*
2. **Trim [Day 17](17-frontend-api-docs-polish.md) to Swagger-only.** If
   you started the one-page frontend (Option B), stop and finish
   [Option A, Swagger UI](17-frontend-api-docs-polish.md#option-a--swagger-ui-with-springdoc-guided-todo--hints)
   instead; Day 17 itself calls A the lower-effort option and says you do
   not need both. Skip the Postman variation (A7) if you are short of time.
   You need exactly one clickable demo surface; that is all Day 18 and Day
   21 depend on.
3. **Reduce Day 18's sample-question count.** [Day 18](18-readme-demo-prep.md#2-todo--pre-write-the-sample-questions-and-save-the-answers)
   has you run seven known-answer questions and save the results to
   `sample-qa.md`. Cut that to three or four: keep the two from the Day
   11–14 checkpoint (equipment stipend and API rate limit) plus one or two
   others. **Do not cut** the vendor-contract line (the "what can't it
   read" answer), and do not skip the
   [clean-checkout test](18-readme-demo-prep.md#checkpoint). Both are
   there for Day 21.

### If cutting those three is not enough

These have no lighter path, so they are not on the list above, but they
are not equally important either, and you should know which is which:

- **[Day 15](15-rate-limit-resilience.md) is marked core.** Do not cut it.
  The demo on Day 21 can hit a free-tier limit at any moment, and the
  retry behaviour is what keeps that from being a failed demo.
- **[Day 19](19-oop-retrospective.md) has two halves.** The
  [retrospective worksheet](19-oop-retrospective.md#part-1--retrospective-worksheet-no-code)
  (no code) is what your architecture walkthrough on Day 21 is built from.
  The embeddings-provider refactor is the larger and riskier half. If you
  have to choose, do the worksheet.

### If you are on schedule

Do not add features. Use the time on things that make Day 21 go better:

- Run the [Day 18 clean-checkout test](18-readme-demo-prep.md#checkpoint)
  again from scratch, now that more has changed (Day 19's refactor), and
  fix whatever it finds.
- Re-read the [Day 18 demo-prep checklist](18-readme-demo-prep.md#3-demo-prep-checklist-walk-through-it-once)
  and tick every item.
- Run `./mvnw test` and confirm the suite passes.
- Start skimming the "Interview angle" sections (see Day 21, Part 3),
  since compiling your questions is the longest piece of Day 21's prep.

### When Day 20 is done

There is no separate checkpoint today; this file's one checkpoint is at the end of Day 21. Day 20 is done when, from a clean state, the stack starts, you
can upload a document, and `/ask` returns a cited answer for a question
you know the answer to. Everything past that is optional.

## Day 21 — Mock interview run-through

### The exercise

You will run through the project the way you would if someone said "walk me
through what you built and show me." It is **one timed session of 10–15
minutes** with three parts, spoken out loud. Speaking out loud is the whole
point: explanations that feel fine in your head tend to fall apart when you
have to say them in order, and it is much better to find that out alone.

Record yourself if you can (phone voice memo, or screen recording
with the microphone on). Reviewing a recording is the most useful thing
you can do with the time afterwards.

### Before you start the timer

Set up exactly as you would for the real thing, using the [Day 18
demo-prep checklist](18-readme-demo-prep.md#3-demo-prep-checklist-walk-through-it-once):
Docker and the app started and warmed up with one question, your Day 17
demo surface open in a browser tab, and `sample-qa.md` open in a second
window as the fallback.

One preparation step is different from Day 18's advice. Day 18 says to
upload the sample files *before* the meeting, because uploads embed every
chunk and are the slowest, most quota-hungry call you have. For a demo
that includes a live upload, pre-load five of the six files and **hold one
back to upload live**. Pick the smallest one, `remote-work-policy.txt`
(about 1 KB), so the live upload is a handful of chunks and takes seconds.
Its natural question is the equipment stipend: the answer is `$500`, cited
to `remote-work-policy.txt`.

To get back to that state between attempts, empty the tables and re-upload
the other five. The [truncate command from Day 19, step 7](19-oop-retrospective.md#7-run-it-then-re-ingest)
does the emptying; then upload every file in `docs/curriculum/sample-data/`
except `remote-work-policy.txt` and the scanned PDF (which uploads but
produces no chunks, so it is only needed if you want it to appear in the
document list).

### Prepare your material (not timed)

Two pieces of prep happen before the first timed run. They are the actual
learning in this exercise, so do them yourself rather than looking for a
finished version:

- **Architecture notes.** On one page, in your own words, write the
  request path for an upload and for a question, and next to each stage
  the design decision behind it. Build this from your
  [Day 19 retrospective worksheet](19-oop-retrospective.md#part-1--retrospective-worksheet-no-code),
  not from this file. You will not have the page in front of you during the
  timed run; the point of writing it is to find the gaps.
- **Three gotcha questions.** See Part 3.

### The timed run (10–15 minutes, no notes for the demo)

Start a timer. Do all three parts, in this order, without pausing it.

**Part 1 — Architecture overview, out loud (about 4–5 minutes).**
Explain the system as if to an engineer who has not seen it. Cover:

- The two flows: **ingestion** (upload, text extraction, chunking,
  embedding, stored in Postgres with pgvector) and **question answering**
  (embed the question, similarity search, build the prompt, call the LLM,
  return the answer with its cited documents).
- **How the OOP and design-pattern choices from Days 2–19 shaped it.** For
  each one, say where it sits in the flow above and what the naive
  alternative would have cost: dependency injection from
  [Day 3](03-day3-crud-docker.md#java-pattern-note), the Strategy for
  chunking from [Day 8–10](08-10-chunking-embedding-pipeline.md#java-pattern-note),
  the Builder for the prompt from [Day 11–14](11-14-ask-endpoint-rag-pipeline.md#java-pattern-note),
  the Decorator around the retry from [Day 15](15-rate-limit-resilience.md#java-pattern-note),
  and the Factory Method and Adapter behind the embeddings provider from
  [Day 19](19-oop-retrospective.md).
- One honest limitation (for example the scanned PDF, or that embeddings
  are computed inside the upload request).

**Part 2 — Live demo of upload plus ask (about 4–5 minutes).** Do these
five steps, in this order, from memory:

1. **Upload live.** Upload `remote-work-policy.txt` through your demo
   surface and say what is happening while it runs (extract, chunk, embed,
   store).
2. **Ask a question whose answer is in it.** "What is the equipment
   stipend for remote work?" Point at the answer (`$500`) and at the cited
   filename, and say why the citation comes from your own data rather than
   from the model.
3. **Ask a question that is not in the documents.** Let the "I don't have
   enough information to answer that." response show that the grounding
   rule holds.
4. **Ask the "what can't it read" question.** Ask about the liability cap
   in the vendor contract. The response should be the same "don't have
   enough information" message, and your spoken answer is the Day 6–7
   lesson, prepared on [Day 18](18-readme-demo-prep.md#2-todo--pre-write-the-sample-questions-and-save-the-answers):
   the file uploaded without an error, but it is an image-only PDF with no
   text layer, so nothing was stored, and OCR is a separate step this project
   does not include.
5. **If a call is slow or throttled, switch to the fallback.** Say what
   happened ("this is the free-tier rate limit; the retry I added handles
   most of it"), then open `sample-qa.md` and read the saved answer for the
   same question. Practise this step at least once on purpose, because
   switching smoothly looks competent and freezing does not. Keep the
   fallback quick: a saved answer is a reference, not a test, since model
   output varies.

**Part 3 — Three gotcha questions (about 3–4 minutes).** Answer three
prepared "gotcha" questions, each in under a minute and a half, as if an
interviewer had asked them. Ask them to yourself aloud, then answer.

The three questions are yours to collect. Fourteen of the day files covering
Day 1 to Day 19 each carry an **Interview angle** section (the
[overview](00-overview.md#how-to-use-this-curriculum) describes it as part
of every day's structure): Days [1](01-day1-git-and-style.md), [2](02-day2-java-oop-refresher.md),
[3](03-day3-crud-docker.md), [4](04-day4-groq-embeddings-hello-world.md),
[5](05-day5-rag-fundamentals.md), [6–7](06-07-file-upload-tika.md),
[the Boot 4 upgrade](07b-upgrade-boot4-spring-ai2.md),
[8–10](08-10-chunking-embedding-pipeline.md),
[11–14](11-14-ask-endpoint-rag-pipeline.md),
[15](15-rate-limit-resilience.md),
[16](16-jwt-security.md),
[17](17-frontend-api-docs-polish.md),
[18](18-readme-demo-prep.md) and
[19](19-oop-retrospective.md). Skim back through them, and for each one
write down the question it opens with and the follow-ups it invites. Then
choose three that you think are the hardest to answer well, and that are
different from each other (one about Java/OOP, one about RAG, one about
operations or trade-offs works well). **Compiling this list is the
exercise.** It is the reason this prep takes about an hour, and having
read all of them again is worth more than any list this file could give
you.

### After the run

Stop the timer and review the recording (or your memory of it) against these
questions, writing down the answer to each:

- Did you stay within 10–15 minutes, and which part ran over?
- Where did you hesitate for more than a few seconds? Those are the gaps
  to fix before the next run.
- Did you say anything you could not defend if asked a follow-up? Either
  cut it or go and learn it.
- Did every demo step work, and did the fallback in step 5 work smoothly?

Reset the state (see above) and run again. Aim for three or four runs in
the session; the second is usually the one that finds the most problems.

## Checkpoint

You pass when you complete **one full run-through, under 15 minutes, in
which you do not need to check any notes for the live demo steps.** To
make that self-checkable, the run counts only if all of these hold:

- [ ] The timer, started before Part 1 and not paused, reads **15:00 or
  less** when you finish Part 3.
- [ ] All three parts happened, in order: the architecture overview (with
  at least the five patterns above named), the live demo, and three gotcha
  answers.
- [ ] During Part 2 you did **not** open this file, your README, the Day 18
  checklist or your own notes. The only things allowed on screen are the
  demo surface and `sample-qa.md`, and `sample-qa.md` is for step 5 only.
- [ ] You did all five demo steps in the right order, with the right
  results visible on screen: `$500` with a `remote-work-policy.txt`
  citation, the "don't have enough information" response for the question
  that is not in the documents, and the same response for the vendor
  contract.
- [ ] You can say, without looking, why step 4 behaves that way.

If any box is unchecked, reset the state and run it again; Day 21 has room
for three or four attempts for exactly this reason. Do not count a run in
which you paused the timer, looked something up, or skipped a step.
