# Day 6-7 — File Upload + Text Extraction (Apache Tika)

**Focus:** Add the first real ingestion path to the app: an HTTP
endpoint that accepts an uploaded file and extracts its plain text with
Apache Tika, then persists that text against the `Document` entity from
Day 3. The endpoint is one method on a controller you already know how
to write, so today is a guided TODO + hints. The actual point of the day
is *not* the upload wiring — it's what happens when you point Tika at a
scanned PDF, which is the classic real-world failure mode the whole rest
of the curriculum (chunking, embedding, retrieval) quietly assumes you
already understand.

## Concept primer

### Why extraction comes before chunking and embedding

Days 8–10 chunk a document and embed each chunk; Days 11–14 retrieve
those chunks to answer questions. Both of those steps operate on *plain
text*. But the documents users actually have — PDFs, `.docx`, HTML — are
not text files. A PDF is a container format that encodes pages, fonts,
and layout instructions; a `.docx` is a ZIP archive of XML parts. Neither
one can be handed to a chunker or an embedding model directly. You need a
step that *reduces* an arbitrary file format to the one thing downstream
code understands: a string of characters. That step is text extraction,
and it has to happen before chunking because chunking is "split this
string into pieces" — there is nothing to split until the string exists.

### What Tika abstracts away

Apache Tika is the standard Java library for that reduction. Its
[examples page](https://tika.apache.org/3.3.1/examples.html) documents
the two ideas that do the work:

- **Format detection.** `AutoDetectParser` "automatically figures out what
  kind of content you have, then calls the appropriate parser" — you hand
  it a stream and it sniffs the bytes (magic numbers, MIME, extension)
  to decide whether it's a PDF, DOCX, TXT, or one of the hundreds of other
  formats it supports.
- **A single facade for parsing.** The `Tika` facade wraps detection +
  parsing + text output in one object, so the whole extraction is a
  one-liner: `new Tika().parseToString(inputStream)` returns the extracted
  plain text as a `String`.

Your controller doesn't branch on `file.getContentType()` or write a PDF
parser and a DOCX parser and a TXT parser. It calls one method, and Tika
routes to the right parser internally. That's the abstraction: one API
surface across many heterogeneous input formats.

### The text-layer callout (read this twice)

Tika's default extraction reads a document's **text layer** — the
characters that are *stored inside* the file, the way a PDF stores the
words of a Word-generated report. A PDF that is actually a **picture of a
page** (a scan from a phone, a photograph of a paper contract) has *no
text layer*: the words exist only as pixels in an embedded image, not as
extractable characters. For those files Tika's default extraction returns
**empty text — silently**. It does not throw an error, because as far as
the parser is concerned there was simply no text to find. That silent
emptiness (rather than a crash) is exactly what makes the failure mode
sneaky, and it's the whole point of today's exercise — you will reproduce
it on purpose and see it with your own eyes.

## Sample dataset note

You don't need to source any of your own files for today (or for any
later upload/chunking/`/ask` exercise). The repository ships a fixed,
six-document corpus under
[`docs/curriculum/sample-data/`](sample-data/) — its
[`README.md`](sample-data/README.md) lists all six files and the key
facts in each one, deliberately chosen so retrieval accuracy is easy to
verify by eye. It is deliberately PDF-heavy (four of six are PDFs) and
includes one file — `vendor-contract-summary-scanned.pdf` — with **no
text layer at all**, because that's the failure mode you're here to
observe. Every later day uploads, chunks, embeds, and queries against
these same six files, so spend the two minutes to skim the README now:
knowing which fact lives in which file is what lets you eyeball retrieval
results on Days 11–14 without trusting anything.

## Reference links

**Spring multipart upload**

- [Spring Guides — Uploading Files](https://spring.io/guides/gs/uploading-files) —
  the official guide: "a server application that can receive HTTP
  multipart file uploads" via a `@PostMapping` controller and a
  `MultipartFile`-backed upload form.
- [Spring Framework Reference — Multipart Forms](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html) —
  the canonical page for what today uses: `@RequestParam("file")
  MultipartFile file` with `file.getBytes()`/`getInputStream()`, plus
  `List<MultipartFile>`, `Map<String, MultipartFile>`, `@RequestPart`,
  and `jakarta.servlet.http.Part`.

**Apache Tika**

- [Apache Tika — Examples (3.3.1)](https://tika.apache.org/3.3.1/examples.html) —
  documents the two claims above: "Parsing using the Tika Facade" shows
  `Tika tika = new Tika(); ... tika.parseToString(stream);` returning
  plain text, and "Parsing using the Auto-Detect Parser" states that
  `AutoDetectParser` auto-detects the content type and calls the
  appropriate parser.

**Maven coordinates (verified on Maven Central)**

- [tika-core — maven-metadata.xml](https://repo.maven.apache.org/maven2/org/apache/tika/tika-core/maven-metadata.xml) —
  `<latest>`/`<release>` is **4.0.0**; the `4.0.0/` directory contains
  the jar and pom.
- [tika-parsers — maven-metadata.xml](https://repo.maven.apache.org/maven2/org/apache/tika/tika-parsers/maven-metadata.xml) —
  4.0.0 is current. **Nuance:** as of 4.0.0, every `tika-parsers*`
  artifact — `tika-parsers`, `tika-parsers-standard`, and
  `tika-parsers-standard-package` — is a **POM-only** aggregator: its
  [4.0.0 directory](https://repo.maven.apache.org/maven2/org/apache/tika/tika-parsers/4.0.0/)
  contains only a `.pom` file, no jar. A plain `<dependency>` (default
  `type=jar`) on any of them will fail to resolve. Tika's own
  [4.0.0 release notes](https://tika.apache.org/4.0.0/index.html) spell
  this out: *"tika-parsers-standard-package is now a pom, not a jar.
  Users must add `<type>pom</type>` in Maven or `@pom` in Gradle."*
- Exact verified coordinates (current, 4.0.0):
  `org.apache.tika:tika-core:4.0.0` (a real jar) plus
  `org.apache.tika:tika-parsers-standard-package:4.0.0` declared with
  `<type>pom</type>` (below) — this is Tika's own documented pattern for
  pulling in the standard parser set (PDF, DOCX, HTML, plain text, and
  more). The previous 3.x stable —
  `org.apache.tika:tika-core:3.3.2` / `org.apache.tika:tika-parsers:3.3.2`
  (a real jar in 3.x, before the split) — is also verified and matches
  the 3.3.x examples page above; the
  facade/`parseToString`/`AutoDetectParser` API is unchanged across
  3.x → 4.x.

**OCR (the remedy for scanned PDFs — read, don't implement)**

- [TesseractOCRParser — API (3.3.2)](https://tika.apache.org/3.3.2/api/org/apache/tika/parser/ocr/TesseractOCRParser.html) —
  Tika's OCR parser is "powered by tesseract-ocr engine"; to enable it
  you create a `TesseractOCRConfig` and pass it through a `ParseContext`,
  and "Tesseract-ocr must be installed and on system path or the path to
  its root folder must be provided".
- [Tesseract OCR Parser — configuration (4.0.x)](https://tika.apache.org/docs/4.0.x/configuration/parsers/tesseract-ocr-parser.html) —
  the parser options (language, timeout, etc.), including the
  `default-parser` note that without it "only image files are OCR'd" —
  i.e. adding `default-parser` lets Tika OCR the embedded images inside a
  scanned/image-only PDF rather than just standalone image files.

## Java pattern note

**Java pattern note:** the controller now constructor-injects a second
collaborator — `Tika` — alongside the `DocumentRepository` you added on
[Day 3](03-day3-crud-docker.md). That's the same **Dependency Injection /
Inversion of Control** from [Day 2, section 7](02-day2-java-oop-refresher.md):
the controller declares what it needs (a repository, a text extractor)
and Spring's container supplies it, so the controller never constructs
either one itself. The "one request per endpoint method" shape — each
`POST /documents/upload` maps to exactly one parse + one save — is just
the Spring MVC request lifecycle you already used on Day 3, not a new
pattern.

## Exercise (guided TODO + hints — familiar Spring MVC territory)

Do today's work on a new feature branch, per Day 1's workflow:

```bash
git checkout -b feature/file-upload-tika
```

### 1. Add the Tika dependency

Add both artifacts to `pom.xml` with the verified coordinates (Maven
shown; if Day 1 generated a Gradle build, use the equivalent
`implementation 'org.apache.tika:tika-core:4.0.0'` plus
`implementation 'org.apache.tika:tika-parsers-standard-package:4.0.0@pom'`
lines):

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>4.0.0</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>4.0.0</version>
    <type>pom</type>
</dependency>
```

**Don't drop that `<type>pom</type>` line.** As of Tika 4.0.0, the
parser-bundle artifacts publish only a `.pom` (no jar) — a plain
`<dependency>` without it will fail with a "could not find artifact"
error, since Maven defaults to looking for a jar. `tika-core` is a real
jar and doesn't need it.

*Version note:* 4.0.0 is the current release per the
[tika-core metadata](https://repo.maven.apache.org/maven2/org/apache/tika/tika-core/maven-metadata.xml)
and matches the pattern documented in
[Tika's 4.0.0 release notes](https://tika.apache.org/4.0.0/index.html).
3.3.2 is the last 3.x stable and matches the
[examples page](https://tika.apache.org/3.3.1/examples.html) cited
above — in 3.x, `org.apache.tika:tika-parsers:3.3.2` is still a real
jar (the POM-only split happened for 4.0.0), so no `<type>pom</type>`
is needed if you pin to 3.3.2 instead.

Because Spring Boot has **no Tika auto-configuration**, also declare the
`Tika` bean once so the controller can inject a single shared instance
(creating a `Tika` per request re-runs its parser detection every time —
wasteful). Add a small config class:

```java
package com.example.demo;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TikaConfig {

    @Bean
    public Tika tika() {
        return new Tika();
    }
}
```

### 2. Scaffold `POST /documents/upload` (MultipartFile)

Add an upload endpoint to the `DocumentController` you wrote on Day 3.
The signature is the scaffolding — the body is yours:

```java
@PostMapping("/upload")
public ResponseEntity<Document> upload(@RequestParam("file") MultipartFile file)
        throws IOException, TikaException {
    String extracted = null; // TODO: extract text with Tika
    // TODO: build a Document(file.getOriginalFilename(), extracted, Instant.now()),
    //       save it, return 201 with the saved copy (it now has an id)
    return null;
}
```

*Hints:*

- `@RequestParam("file") MultipartFile file` binds the multipart form
  field named `file` to a `MultipartFile`, whose `getInputStream()` gives
  you the uploaded bytes — documented on the
  [Multipart Forms reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html)
  and the
  [uploading-files guide](https://spring.io/guides/gs/uploading-files).
- The extraction is a single line. The library's canonical form, straight
  from the
  [Tika examples](https://tika.apache.org/3.3.1/examples.html), is
  `new Tika().parseToString(inputStream)`; here you call the same method
  on the injected bean, so it becomes
  `tika.parseToString(file.getInputStream())` — one shared, warm `Tika`
  instance instead of a new one per request.
- `file.getOriginalFilename()` is the uploaded file's name; `Instant.now()`
  stamps the upload time (the `uploadedAt` field Day 3 already declared).
- `repository.save(document)` returns the saved entity with its generated
  `id`. Return `201` with a `Location` header (you already did this shape
  in Day 3's `create` — same `ResponseEntity.created(...)` pattern).

### 3. Persist extracted text alongside the Day 3 `Document` entity

The Day 3 `Document` has `id`, `filename`, and `uploadedAt`. Add a
`content` field to hold the extracted text. The field is a plain
`String`, but declare it as a database `TEXT` column (not the default
`varchar(255)`, which would silently truncate real documents):

```java
@Column(columnDefinition = "TEXT")
private String content;
```

Add it to the constructor, and add a getter and setter. The
`DocumentRepository` needs **no changes** — `JpaRepository` already
persists every mapped field. Leave the `repository.save(...)` call in
the controller as a TODO (step 2's second TODO) rather than wiring it
here.

### 4. Upload all six files and record what comes back

Start the app (`./mvnw spring-boot:run`, or `docker compose up` if you're
running the Day 3 stack), then upload **every** file in
`docs/curriculum/sample-data/`. For each one, record the filename and
whether the returned `content` is non-empty. Use the
[`README.md`](sample-data/README.md) key facts as your expected-result
key:

| File | Expected `content` |
|---|---|
| `remote-work-policy.txt` | non-empty; contains `$500` |
| `api-changelog.txt` | non-empty; four dated version entries |
| `expense-reimbursement-policy.pdf` | non-empty; contains `$150` / `30-day` |
| `onboarding-checklist.pdf` | non-empty; day-1/week-1/30-day/90-day milestones |
| `incident-response-runbook.pdf` | non-empty; `Sev1`–`Sev4` levels |
| `vendor-contract-summary-scanned.pdf` | **empty or near-empty** |

```bash
curl -F file=@docs/curriculum/sample-data/remote-work-policy.txt localhost:8080/documents/upload
# ... repeat for the other five filenames ...
```

*Note:* `-F file=@...` sends the multipart field named `file`, which is
what `@RequestParam("file")` expects. If you're on Windows PowerShell,
`curl -F file=@docs/...` works the same in modern Windows' bundled
`curl.exe`.

## The scanned-PDF failure mode

This is the whole point of the day, not a footnote. When you uploaded
the five text-bearing files, `content` came back populated. When you
uploaded `vendor-contract-summary-scanned.pdf`, `content` came back
**empty or near-empty** — a `Document` was still created (the file
uploaded fine, the row saved fine), but the extracted text is blank.

Confirm that yourself by inspecting the JSON response for the scanned
PDF versus any other file. Then internalize what you're looking at:

- **This is expected, not a bug** in your code or in the dataset. The
  file was created as an image — a picture of a page, the way a
  phone-scanned paper contract would come out. It has **no text layer**,
  only pixel data in an embedded image. Tika's default parser looks for
  the text layer, finds none, and returns nothing. It does not error
  because there was no text *to* extract — silent emptiness is the
  failure mode.
- **What production systems do about it:** run an **OCR pass** — optical
  character recognition — that looks at the *pixels* and reconstructs the
  characters. Tika ships exactly this as its
  [TesseractOCRParser](https://tika.apache.org/3.3.2/api/org/apache/tika/parser/ocr/TesseractOCRParser.html),
  which wraps the tesseract-ocr engine: you configure a
  `TesseractOCRConfig` via a `ParseContext`, and tesseract must be
  installed and on the system path. Tika's
  [tesseract parser config page](https://tika.apache.org/docs/4.0.x/configuration/parsers/tesseract-ocr-parser.html)
  is the reference for wiring it up, including the `default-parser`
  setting that makes Tika OCR the *embedded images inside* an image-only
  PDF rather than just standalone image files. OCR runs *before* (or as
  part of) extraction, turning the image back into a text layer.

**Scope: read and understand, do not implement.** Wiring up OCR is a
real but separate piece of work — installing the tesseract native binary
on the host/container, configuring the parser, and accepting OCR's
accuracy trade-offs — and it doesn't fit this project's timeline. What
an interview question is actually probing for is *not* "can you OCR a
PDF," it's *knowing why* it's needed and *what the fix looks like*: name
the text-layer limitation, then name Tesseract-via-Tika's-OCR-parser as
the remedy. You now have first-hand evidence for both halves.

## Checkpoint

You're done with Day 6-7 when:

1. `curl -F file=@docs/curriculum/sample-data/remote-work-policy.txt localhost:8080/documents/upload`
   returns a created `Document` whose `content` is non-empty and contains
   `$500` (the equipment stipend fact).
2. `curl -F file=@docs/curriculum/sample-data/vendor-contract-summary-scanned.pdf localhost:8080/documents/upload`
   returns a created `Document` whose `content` is **empty or
   near-empty** — confirming the scanned-PDF failure mode was
   *reproduced*, not avoided.
3. Repeat the upload for the remaining four files so **all six** are
   loaded for Days 8–10 and 11–14 (each returns a created `Document`
   with non-empty `content`).
4. Commit on the feature branch with a Conventional Commits message
   (e.g. `feat: add multipart upload with Tika text extraction`), push,
   and merge via a pull request per Day 1.

If the scanned PDF comes back with non-empty text, you've either uploaded
a different file or something is running OCR behind the scenes — stop and
re-check the filename, because the *empty* result is the lesson.

## Interview angle

*"How do you handle unstructured, heterogeneous input formats — and what
happens when a 'document' is actually just a scanned image?"*

Most candidates can give the first half of the answer and stop there:
"Tika auto-detects the format and parses it, so I don't write a parser
per file type." That's real, and it's what format detection + the
`Tika` facade give you. But the follow-up is where the strong candidates
separate: a scanned PDF has **no text layer**, so auto-detection still
finds "it's a PDF" and the parser still returns *empty text without
erroring*. Recognizing that limitation — and naming the fix (an OCR pass
with Tesseract via Tika's `TesseractOCRParser`, run before or as part of
extraction) — is the second half most people don't have ready. Having
*observed* the empty extraction on a real file (not just read about it)
is what lets you tell that story concretely instead of reciting it.

## Answer Key (attempt it yourself first!)

Only read this after all six uploads return and you've confirmed the
scanned PDF's empty `content` with your own curl. This is one reasonable
trade-off, not the only correct answer.

**`Document.java`** (Day 3's entity, plus the `content` column)

```java
package com.example.demo;

import java.time.Instant;
import jakarta.persistence.Column;
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

    @Column(columnDefinition = "TEXT")
    private String content;

    private Instant uploadedAt;

    protected Document() {
        // JPA requires a no-arg constructor.
    }

    public Document(String filename, String content, Instant uploadedAt) {
        this.filename = filename;
        this.content = content;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
```

**`DocumentRepository.java`** (unchanged from Day 3)

```java
package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
```

**`TikaConfig.java`**

```java
package com.example.demo;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TikaConfig {

    @Bean
    public Tika tika() {
        return new Tika();
    }
}
```

**`DocumentController.java`** (Day 3's CRUD methods plus the new upload
endpoint — the Day 3 `create(@RequestBody Document)` JSON endpoint is
replaced by `upload`, since multipart upload is now the real ingestion
path; keep or remove the JSON one as you prefer, they don't conflict)

```java
package com.example.demo;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentRepository repository;
    private final Tika tika;

    public DocumentController(DocumentRepository repository, Tika tika) {
        this.repository = repository;
        this.tika = tika;
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

    @PostMapping("/upload")
    public ResponseEntity<Document> upload(@RequestParam("file") MultipartFile file)
            throws IOException, TikaException {
        String extracted = tika.parseToString(file.getInputStream());
        Document document = new Document(
                file.getOriginalFilename(), extracted, Instant.now());
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
