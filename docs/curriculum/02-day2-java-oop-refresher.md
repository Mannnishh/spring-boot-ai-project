# Day 2 — Advanced Java/OOP Refresher

**Focus:** SOLID, interfaces vs. abstract classes, generics,
streams/lambdas/`Optional`, exception-hierarchy design, a preview of the
three design patterns this project actually uses, and Dependency
Injection as a worked example.

Today is deliberately standalone — no Spring, no Docker, no AI. Every
code sample here runs as plain Java in a scratch directory. The reason
this day exists before any framework code: from Day 3 onward, Spring
does a lot of this *for* you, and it's much easier to recognize what the
framework is doing if you've built the same thing by hand once. The
vocabulary established today (DI/IoC, Strategy, Builder, Decorator) is
referenced by name on Days 3, 8–10, 11–14, and 15, so it's worth getting
the definitions precise now rather than approximately.

## Concept primer

### 1. SOLID

SOLID is five object-oriented design principles collected by Robert C.
Martin. Here is each one in his own words (from
[Solid Relevance](https://blog.cleancoder.com/uncle-bob/2020/10/18/Solid-Relevance.html)),
followed by what it looks like in the Spring web app you're building.

**S — Single Responsibility Principle.** *"Gather together the things
that change for the same reasons. Separate things that change for
different reasons."* Martin's fuller treatment is that a module should
have [one and only one reason to change](https://blog.cleancoder.com/uncle-bob/2014/05/08/SingleReponsibilityPrinciple.html),
and that "reasons to change" ultimately trace back to *people* — the
stakeholder who would ask for that change.

> **In this project:** a `@RestController` handles HTTP concerns —
> parsing the request, choosing a status code, shaping the JSON
> response. It should not also contain the SQL or JPA query that loads
> documents. Those change for different reasons and at different times:
> the response shape changes when the API contract changes; the query
> changes when the schema changes. Controller → service → repository is
> not ceremony, it's SRP applied to a web request.

**O — Open/Closed Principle.** *"A Module should be open for extension
but closed for modification."* Martin's practical framing is that you
should be able to
[extend the behavior of a system without modifying that system](https://blog.cleancoder.com/uncle-bob/2014/05/12/TheOpenClosedPrinciple.html)
— adding features by writing new code rather than editing old code.

> **In this project:** on Day 8–10 you'll add a second chunking
> algorithm. If adding it means editing the class that runs the
> pipeline, you've violated OCP. If it means writing one new class that
> implements an existing interface, you haven't. Today's exercise is
> exactly this shape in miniature.

**L — Liskov Substitution Principle.** *"A program that uses an
interface must not be confused by an implementation of that
interface."* The underlying idea was formalized by Barbara Liskov and
Jeannette Wing in
[A Behavioral Notion of Subtyping](https://www.cs.cmu.edu/~wing/publications/LiskovWing94.pdf),
whose Subtype Requirement states that a property provable about objects
of a supertype must also hold for objects of its subtypes.

> **In this project:** if `FixedSizeChunker` and `SentenceChunker` both
> implement `Chunker`, then any code holding a `Chunker` must work
> correctly with either one. A `SentenceChunker` that throws
> `UnsupportedOperationException` for some inputs the interface claims
> to accept, or that silently returns an empty list where the contract
> promises at least one chunk, breaks LSP — the caller now has to know
> which implementation it actually got, which defeats the whole point
> of the interface.

**I — Interface Segregation Principle.** *"Keep interfaces small so that
users don't end up depending on things they don't need."*

> **In this project:** resist a single fat `DocumentService` interface
> with `upload`, `extractText`, `chunk`, `embed`, `search`, and `ask` on
> it. The `/ask` endpoint needs `search`; it does not need `upload`.
> Separate, narrow interfaces mean a change to the upload signature
> can't force a recompile — or a re-mock in a test — of everything that
> only ever wanted to search.

**D — Dependency Inversion Principle.** *"Depend in the direction of
abstraction. High level modules should not depend upon low level
details."*

> **In this project:** your `/ask` handler should depend on an
> `EmbeddingClient` interface, not on a concrete
> `GeminiEmbeddingClient`. Then swapping Gemini for Hugging Face is a
> new class plus one line of wiring, not a rewrite of the endpoint.
> DIP is the principle; **dependency injection** (section 7) is the
> mechanism that makes it practical.

A note on how these interact: OCP is the goal, DIP is the main
mechanism for reaching it, and LSP is the constraint that keeps the
mechanism honest — a substitutable abstraction is the only kind you can
safely extend against.

### 2. Interfaces vs. abstract classes

Oracle's Java Tutorials give a direct decision list on the
[Abstract Methods and Classes](https://docs.oracle.com/javase/tutorial/java/IandI/abstract.html)
page.

Reach for an **abstract class** when:

- you want to share *code* among several closely related classes;
- subclasses will have many common methods or fields, or need access
  modifiers other than `public` (`protected`, `private`);
- you want non-static, non-final fields — i.e. shared mutable state.

Reach for an **interface** when:

- unrelated classes will implement it (the tutorial's examples are
  `Comparable` and `Cloneable`, implemented across totally unrelated
  types);
- you want to specify the behavior of a type without caring who
  implements it;
- you want multiple inheritance of type.

The hard constraint underneath all of this, quoting the same page: "you
can extend only one class, whether or not it is abstract, whereas you
can implement any number of interfaces." An abstract class spends your
one inheritance slot; an interface doesn't.

**Composition over inheritance.** The general preference —
[composition over inheritance](https://en.wikipedia.org/wiki/Composition_over_inheritance)
— is to get code reuse by *holding a reference to* another object and
delegating to it, rather than by extending it. Inheritance couples you
to a superclass's implementation permanently and at compile time;
composition lets you swap the collaborator at runtime and keeps the two
classes free to evolve separately. Practically, in this project: when
you catch yourself writing `extends`, ask whether the relationship is
really "is-a" or just "needs the behavior of." If it's the latter,
inject the collaborator instead. Sections 6 and 7 are both applications
of this preference.

### 3. Generics

A raw `List` holds `Object`. A `List<Document>` holds `Document`. The
[Why Use Generics?](https://docs.oracle.com/javase/tutorial/java/generics/why.html)
page names three concrete benefits, and the first two are the ones that
bite you daily:

1. **Stronger type checks at compile time.** "A Java compiler applies
   strong type checking to generic code and issues errors if the code
   violates type safety. Fixing compile-time errors is easier than
   fixing runtime errors, which can be difficult to find." Put a
   `String` into a `List<Document>` and it won't compile. Put one into
   a raw `List` and it compiles fine, then throws `ClassCastException`
   at some unrelated line, at runtime, in production.
2. **Elimination of casts.** The tutorial's own comparison — *before*,
   with a raw `List`:

   ```java
   import java.util.ArrayList;
   import java.util.List;

   List list = new ArrayList();
   list.add("hello");
   String s = (String) list.get(0);   // cast required
   ```

   and *after*, with a parameterized one:

   ```java
   import java.util.ArrayList;
   import java.util.List;

   List<String> list = new ArrayList<String>();
   list.add("hello");
   String s = list.get(0);            // no cast
   ```

   (These are two alternatives, not two steps — they declare the same
   `list` and `s` names, so try one or the other, not both in the same
   scope. The `import` lines go at the top of the file; the three
   statements go inside a method body.)

3. **Enabling generic algorithms** — one implementation that works over
   collections of different types, type-safely.

**Generic methods.** Per the
[Generic Methods](https://docs.oracle.com/javase/tutorial/java/generics/methods.html)
page, the type parameter list goes in angle brackets *before* the
return type. Here's one you could genuinely reuse, combining generics
with the `Optional` and lambda material below:

```java
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class Finder {

    private Finder() {}

    public static <T> Optional<T> firstMatching(
            List<T> items, Predicate<T> predicate) {
        for (T item : items) {
            if (predicate.test(item)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
```

`<T>` sits before the return type `Optional<T>`. The method works on a
`List<Document>`, a `List<String>`, or a list of anything else, and the
compiler knows in each case what type comes back — no casts, no
`Object`.

### 4. Streams, lambdas, and `Optional`

**Lambdas.** A lambda expression lets you
["treat functionality as method argument, or code as data"](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html),
expressing an instance of a single-method class compactly. It only
works against a *functional interface* — "any interface that contains
only one abstract method." `Predicate<T>` in the example above is one;
so are `Function`, `Supplier`, and `Comparator`.

**Streams.** The
[`java.util.stream` package](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html)
documentation is precise about what a stream is and isn't: "A stream is
not a data structure that stores elements; instead, it conveys elements
from a source such as a data structure, an array, a generator function,
or an I/O channel, through a pipeline of computational operations."

Operations come in two kinds. *Intermediate* operations (`filter`,
`map`) return a new stream and "are always **lazy**" — calling
`filter()` filters nothing; it builds a stream that will filter when
traversed. *Terminal* operations (`toList`, `sum`, `forEach`) are eager
and actually run the pipeline: "Traversal of the pipeline source does
not begin until the terminal operation of the pipeline is executed."
This is why a stream pipeline with no terminal operation does nothing
at all — a classic first-week-back bug.

**Before / after.** An imperative loop that trims titles, keeps the long
ones, and upper-cases the survivors:

```java
// requires: java.util.ArrayList, java.util.List, java.util.Locale
static List<String> normalizeLongTitles(List<String> titles) {
    List<String> result = new ArrayList<>();
    for (String title : titles) {
        String trimmed = title.trim();
        if (trimmed.length() > 10) {
            result.add(trimmed.toUpperCase(Locale.ROOT));
        }
    }
    return result;
}
```

The same thing as a pipeline:

```java
// requires: java.util.List, java.util.Locale
static List<String> normalizeLongTitles(List<String> titles) {
    return titles.stream()
            .map(String::trim)
            .filter(t -> t.length() > 10)
            .map(t -> t.toUpperCase(Locale.ROOT))
            .toList();
}
```

(As with the pairs below, these are two versions of the *same* method —
the second replaces the first, they can't coexist in one class.)

Read the pipeline top to bottom and it states *what* happens; the loop
states *how*. Two mechanical notes:
[`Stream.toList()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html)
was added in Java 16 and "the returned List is unmodifiable" — if you
need a mutable list, or you're on Java 8–15, use
`.collect(Collectors.toList())` instead. And order matters: trimming
before filtering is not the same as filtering before trimming, so keep
the stages in the same order the loop had them if you want identical
output.

**`Optional`.**
[`java.util.Optional`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html)
is "a container object which may or may not contain a non-`null`
value." Its own API note is the whole design rationale: "`Optional` is
primarily intended for use as a method return type where there is a
clear need to represent 'no result', and where using `null` is likely
to cause errors. A variable whose type is `Optional` should never
itself be `null`."

The point is that the *signature* tells the caller a miss is possible.
`Document findByTitle(String)` looks total and lies; every caller has
to remember a null check that the compiler will never demand.
`Optional<Document> findByTitle(String)` cannot be dereferenced without
first dealing with the empty case.

Before — the "null means not found" convention:

```java
// requires: java.util.List (field `documents` is a List<Document>)
public Document findByTitle(String title) {
    for (Document d : documents) {
        if (d.title().equals(title)) {
            return d;
        }
    }
    return null;
}
```

After — the absence is part of the type:

```java
// requires: java.util.List, java.util.Optional
public Optional<Document> findByTitle(String title) {
    return documents.stream()
            .filter(d -> d.title().equals(title))
            .findFirst();
}
```

(These are two versions of the *same* method — same name, same
parameter, so the same erasure. The second replaces the first; they
can't coexist in one class.)

Callers then choose their own handling, without a null check in sight:

```java
int pages = repo.findByTitle("handbook")
        .map(Document::pageCount)
        .orElse(0);

Document doc = repo.findByTitle("handbook")
        .orElseThrow(() -> new DocumentNotFoundException("handbook"));
```

`map` applies a function only if a value is present; `orElse` supplies
a default; `orElseThrow` "returns the value, otherwise throws an
exception produced by the exception supplying function." Use
`Optional.of(x)` when `x` is definitely non-null (it throws
`NullPointerException` otherwise) and `Optional.ofNullable(x)` when
`x` might be null.

### 5. Exception hierarchy design

Java's rule is mechanical, and Oracle's
[Catch or Specify Requirement](https://docs.oracle.com/javase/tutorial/essential/exceptions/catchOrDeclare.html)
page states it exactly:

- **Checked exceptions** are "exceptional conditions that a well-written
  application should anticipate and recover from." They *are* subject to
  Catch or Specify — you must either `catch` them or declare them with
  `throws`, or the code will not compile. "All exceptions are checked
  exceptions, except for those indicated by `Error`, `RuntimeException`,
  and their subclasses."
- **Runtime exceptions** are "internal to the application... These
  usually indicate programming bugs, such as logic errors or improper
  use of an API." They are *not* subject to Catch or Specify.
- **Errors** are "external to the application, and that the application
  usually cannot anticipate or recover from" — also not subject to it.
- "Errors and runtime exceptions are collectively known as **unchecked
  exceptions**."

Oracle's bottom-line guidance for choosing between them: *"If a client
can reasonably be expected to recover from an exception, make it a
checked exception. If a client cannot do anything to recover from the
exception, make it an unchecked exception."*

**When to write your own.** The
[Creating Exception Classes](https://docs.oracle.com/javase/tutorial/essential/exceptions/creating.html)
page says to write your own only if you answer yes to one of these:
you need an exception type the platform doesn't provide; it would help
users to distinguish your exceptions from another vendor's; your code
throws more than one related exception; or your package needs to be
independent and self-contained. Otherwise, reuse a built-in one.

Applied here: `IllegalArgumentException` is the right answer for "chunk
size must be positive" — the platform already has that type and there's
nothing domain-specific about it. But "no document with that title" is
a domain concept this application owns, callers will want to
distinguish it from every other failure, and it's one of a family of
related document failures — so it earns a class:

```java
public class DocumentNotFoundException extends RuntimeException {

    private final String title;

    public DocumentNotFoundException(String title) {
        super("No document found with title: " + title);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
```

Note the two deliberate choices. It extends `RuntimeException`, so it's
unchecked: a caller asking for a document that doesn't exist can't
retry its way out of that, and in a Spring web app the handling is
"map it to a 404," which happens in one exception-handler class rather
than via `throws` clauses threaded through every layer. (This is a
convention, not a law — Oracle's recover-or-not rule is the actual
test, and reasonable teams land on both sides of it for "not found."
What matters is that you pick deliberately and stay consistent.) And it
carries the `title` as a field, not just inside the message string, so
a handler can build a structured error response without parsing prose.

### 6. Pattern preview: Strategy, Builder, Decorator

Definitions only for now — **you'll implement each of these for real in
this project: Strategy on Day 8–10, Builder on Day 11–14, Decorator on
Day 15.** Each of those days carries a short "Java pattern note" that
assumes you have these definitions. All three come from
[*Design Patterns: Elements of Reusable Object-Oriented Software*](https://www.informit.com/store/design-patterns-elements-of-reusable-object-oriented-9780201633610)
by Gamma, Helm, Johnson, and Vlissides — the "Gang of Four" book.

**Strategy** (behavioral) lets you
["define a family of algorithms, put each of them into a separate class, and make their objects interchangeable"](https://refactoring.guru/design-patterns/strategy),
so that — in the Gang of Four's phrasing —
[the algorithm varies independently from the clients that use it](https://en.wikipedia.org/wiki/Strategy_pattern).
The client holds a reference to the interface and calls it; which
concrete algorithm it got is selected elsewhere, often at runtime. You
have already used this without naming it: a
[`Comparator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html)
passed to `Collections.sort` is a sorting strategy — "Comparators can
be passed to a sort method... to allow precise control over the sort
order."
*→ Day 8–10: pluggable chunking strategies.*

**Builder** (creational) exists to
["separate the construction of a complex object from its representation"](https://en.wikipedia.org/wiki/Builder_pattern),
so that the same construction process can produce different
representations — letting you
["construct complex objects step by step"](https://refactoring.guru/design-patterns/builder)
instead of through one constructor with a dozen positional arguments.
In Java this usually shows up as a fluent builder with a terminal
`build()` call; the JDK's own
[`Locale.Builder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Locale.Builder.html)
is a textbook example ("`Builder` is used to build instances of
`Locale` from values configured by the setters"), and it also shows the
second payoff: unlike a raw constructor, the builder can validate as it
goes.
*→ Day 11–14: assembling the RAG prompt from a question, retrieved
chunks, and citation instructions.*

**Decorator** (structural)
["lets you attach new behaviors to objects by placing these objects inside special wrapper objects that contain the behaviors"](https://refactoring.guru/design-patterns/decorator).
The wrapper implements the *same interface* as the thing it wraps, so
callers can't tell the difference and decorators can be stacked. The
Gang of Four's framing is that it
[provides a flexible alternative to subclassing for extending functionality](https://en.wikipedia.org/wiki/Decorator_pattern)
— behavior added per-object at runtime, not per-class at compile time.
Java's I/O library is the canonical example:
[`FilterInputStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/FilterInputStream.html)
"wraps some other input stream, which it uses as its basic source of
data, possibly transforming the data along the way or providing
additional functionality."
*→ Day 15: a retry/rate-limit wrapper around the LLM client.*

**Two distinctions worth keeping straight**, because they're easy to
blur and both come up in interviews:

- **Strategy vs. Decorator.** Both are "an interface plus
  implementations." A Strategy *replaces* the algorithm — you pick one
  of N. A Decorator *wraps* an existing implementation and delegates to
  it, adding behavior around the call. Mechanical test: if a class
  implements interface `X` *and* holds a reference to another `X` that
  it delegates to, it's a decorator. If a class holds a reference to an
  interface it does not itself implement, and calls it to get one job
  done, it's a client of a strategy.
- **Strategy vs. State.** Strategy is about interchangeable algorithms
  chosen by the client; State is about an object changing its own
  behavior as its internal state changes, typically switching itself
  between state objects. Same class diagram, different intent — and
  intent is what defines a pattern.

### 7. Dependency Injection as a concrete case of Inversion of Control

This is the one pattern you get a full worked example of today, because
from Day 3 onward Spring does it for you constantly and you want to
recognize it on sight.

Spring's own reference documentation defines both terms in one
sentence, on the
[IoC container introduction](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html)
page:

> "Dependency injection (DI) is a specialized form of IoC, whereby
> objects define their dependencies (that is, the other objects they
> work with) only through constructor arguments, arguments to a factory
> method, or properties that are set on the object instance after it is
> constructed or returned from a factory method. The IoC container then
> injects those dependencies when it creates the bean. This process is
> fundamentally the inverse (hence the name, Inversion of Control) of
> the bean itself controlling the instantiation or location of its
> dependencies by using direct construction of classes or a mechanism
> such as the Service Locator pattern."

Unpack that carefully, because the two terms are not synonyms:

- **Inversion of Control** is the general idea: control over *when* and
  *how* your collaborators get created is taken away from your class
  and given to something outside it.
- **Dependency Injection** is one specific way of doing IoC: the
  collaborators are handed in from outside — most commonly through the
  constructor.
- **The container** (Spring's `ApplicationContext`) is just an
  automated injector. The pattern works fine without one, which is what
  today's exercise proves.

**Constructor injection specifically.** Spring documents three injection
styles, and is explicit about which to prefer, on the
[Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
page: "The Spring team generally advocates constructor injection, as it
lets you implement application components as immutable objects and
ensures that required dependencies are not `null`. Furthermore,
constructor-injected components are always returned to the client
(calling) code in a fully initialized state." The rule of thumb it
gives is "constructors for mandatory dependencies and setter methods or
configuration methods for optional dependencies." That same page notes
the payoff for a class like the one you'll write below: "there is
nothing special about this class. It is a POJO that has no dependencies
on container specific interfaces, base classes, or annotations."

One detail to file away for Day 3: with constructor injection you often
don't even write `@Autowired`. Per Spring's
[`@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html)
documentation, "an `@Autowired` annotation on such a constructor is not
necessary if the target bean defines only one constructor." So a
Spring-managed version of today's example is byte-for-byte the same
class with a `@Service` annotation on top — which is exactly the
recognition this day is setting up.

## Reference links

**SOLID**

- [Solid Relevance — Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2020/10/18/Solid-Relevance.html) —
  one-line statements of all five principles from the person who
  collected them.
- [The Single Responsibility Principle — Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2014/05/08/SingleReponsibilityPrinciple.html) —
  the "one reason to change" formulation and why reasons trace back to
  people.
- [The Open-Closed Principle — Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2014/05/12/TheOpenClosedPrinciple.html) —
  extending behavior without modifying the system.
- [A Behavioral Notion of Subtyping — Liskov & Wing (1994)](https://www.cs.cmu.edu/~wing/publications/LiskovWing94.pdf) —
  the formal source of the substitution property behind LSP (a research
  paper; skim the boxed "Subtype Requirement" near the start and move
  on — the rest is heavier going than this project needs).

**Java language and library docs (Oracle, official)**

- [Abstract Methods and Classes](https://docs.oracle.com/javase/tutorial/java/IandI/abstract.html) —
  the decision list for abstract class vs. interface.
- [Why Use Generics?](https://docs.oracle.com/javase/tutorial/java/generics/why.html) —
  compile-time type checks, elimination of casts, generic algorithms.
- [Generic Methods](https://docs.oracle.com/javase/tutorial/java/generics/methods.html) —
  type-parameter syntax and a worked `compare` example.
- [Lambda Expressions](https://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html) —
  lambdas and functional interfaces.
- [`java.util.stream` package summary](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/package-summary.html) —
  what a stream is, and intermediate (lazy) vs. terminal (eager)
  operations.
- [`Stream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html) —
  `filter`, `map`, and `toList` (Java 16+, returns an unmodifiable
  list).
- [`Optional`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html) —
  `of`, `ofNullable`, `empty`, `map`, `orElse`, `orElseThrow`, and the
  API note on using it as a return type.
- [The Catch or Specify Requirement](https://docs.oracle.com/javase/tutorial/essential/exceptions/catchOrDeclare.html) —
  checked vs. unchecked, stated precisely.
- [Unchecked Exceptions — The Controversy](https://docs.oracle.com/javase/tutorial/essential/exceptions/runtime.html) —
  the "can the client recover?" rule of thumb.
- [Creating Exception Classes](https://docs.oracle.com/javase/tutorial/essential/exceptions/creating.html) —
  the four questions for whether to define your own.

**Design patterns**

- [*Design Patterns: Elements of Reusable Object-Oriented Software*](https://www.informit.com/store/design-patterns-elements-of-reusable-object-oriented-9780201633610) —
  Gamma, Helm, Johnson & Vlissides (Addison-Wesley); the canonical
  source for Strategy, Builder, and Decorator. Publisher's page.
- [Strategy](https://refactoring.guru/design-patterns/strategy) and
  [Strategy pattern (Wikipedia)](https://en.wikipedia.org/wiki/Strategy_pattern) —
  family of interchangeable algorithms; algorithm varies independently
  of its clients.
- [Builder](https://refactoring.guru/design-patterns/builder) and
  [Builder pattern (Wikipedia)](https://en.wikipedia.org/wiki/Builder_pattern) —
  separating construction from representation, step by step.
- [Decorator](https://refactoring.guru/design-patterns/decorator) and
  [Decorator pattern (Wikipedia)](https://en.wikipedia.org/wiki/Decorator_pattern) —
  wrapping an object to add behavior; a flexible alternative to
  subclassing.
- [`Comparator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html) —
  Strategy in the JDK.
- [`Locale.Builder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Locale.Builder.html) —
  Builder in the JDK.
- [`FilterInputStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/FilterInputStream.html) —
  Decorator in the JDK.
- [Composition over inheritance](https://en.wikipedia.org/wiki/Composition_over_inheritance) —
  the general preference all three of these patterns rest on.

**Spring: Dependency Injection / Inversion of Control**

- [The IoC Container — Introduction to the Spring IoC Container and Beans](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html) —
  Spring's own definition of IoC and DI, and `BeanFactory` vs.
  `ApplicationContext`.
- [Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html) —
  constructor-based vs. setter-based DI, and why the Spring team
  advocates constructor injection.
- [Using `@Autowired`](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html) —
  including why a single-constructor class doesn't need the annotation.

## Exercise (worked example + variation — new/rusty territory)

This is standalone plain Java. Make a scratch directory outside your
Spring project (or a `day2/` folder in it that you don't commit to the
app's source set) and put each class in its own `.java` file. No build
tool needed — `javac *.java && java Main` is enough.

### Part 1 — Worked example: hand-rolled dependency injection

Read this all the way through and get it running before changing
anything. Five files, all in the default package (no `package`
declaration), all in the same directory.

**`Notifier.java`** — the abstraction. One method, one job.

```java
public interface Notifier {
    void send(String recipient, String message);
}
```

**`EmailNotifier.java`** — first implementation.

```java
public class EmailNotifier implements Notifier {

    private final String fromAddress;

    public EmailNotifier(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String recipient, String message) {
        System.out.println("[email] from=" + fromAddress
                + " to=" + recipient
                + " body=" + message);
    }
}
```

**`SmsNotifier.java`** — second implementation, same interface.

```java
public class SmsNotifier implements Notifier {

    private final String shortCode;

    public SmsNotifier(String shortCode) {
        this.shortCode = shortCode;
    }

    @Override
    public void send(String recipient, String message) {
        System.out.println("[sms] from=" + shortCode
                + " to=" + recipient
                + " body=" + message);
    }
}
```

**`DocumentUploadService.java`** — the consumer. This is the important
one.

```java
public class DocumentUploadService {

    private final Notifier notifier;

    // Constructor injection: the collaborator is handed in, not
    // created here. Note there is no `new EmailNotifier(...)`
    // anywhere in this file — and no mention of any concrete
    // notifier type at all.
    public DocumentUploadService(Notifier notifier) {
        this.notifier = notifier;
    }

    public void upload(String ownerContact, String filename) {
        // Real work would go here: store the file, kick off indexing.
        System.out.println("Stored document: " + filename);
        notifier.send(ownerContact,
                "Document '" + filename + "' was uploaded.");
    }
}
```

**`Main.java`** — the *composition root*: the one place that knows
which concrete implementations exist and wires them together.

```java
public class Main {
    public static void main(String[] args) {

        Notifier notifier = new EmailNotifier("noreply@example.com");

        DocumentUploadService service =
                new DocumentUploadService(notifier);

        service.upload("friend@example.com", "handbook.pdf");
    }
}
```

Run it:

```bash
javac *.java && java Main
```

Expected output:

```
Stored document: handbook.pdf
[email] from=noreply@example.com to=friend@example.com body=Document 'handbook.pdf' was uploaded.
```

Now change one line in `Main.java` —
`Notifier notifier = new SmsNotifier("55512");` — and rerun. The
service's behavior changes; `DocumentUploadService.java` was not
touched.

**What just happened, in the vocabulary from section 7:**
`DocumentUploadService` does not control the creation or location of
its dependency — that control has been *inverted* out to `Main`. The
mechanism of that inversion is *dependency injection* through the
constructor. `Main` is playing exactly the role Spring's
`ApplicationContext` will play from Day 3 onward: it's the injector.
The only thing Spring adds is doing the wiring automatically instead of
you writing the `new` calls by hand.

Two properties worth noticing, because they're the reasons Spring
recommends this style: `notifier` is `final`, so the service is
immutable after construction; and it can't be `null`, because there is
no way to obtain a `DocumentUploadService` without supplying one.

**Why this is also testable:** to unit-test `upload()` without sending
anything, you pass in a fake `Notifier` that records what it was
asked to send. That is only possible because the dependency comes in
from outside. A class that does `new EmailNotifier(...)` internally is
a class you cannot test without sending email.

### Part 2 — Variation task: add a third implementation

1. Write **`ConsoleNotifier.java`**: a third `Notifier` implementation
   that just prints the message to stdout with a `[console]` prefix and
   no configuration in its constructor.
2. Swap it into `Main.java` and rerun.
3. **Constraint: do not open `DocumentUploadService.java`.** Not one
   character. If you find yourself needing to edit it, something is
   wrong with your `ConsoleNotifier` — it isn't conforming to the
   `Notifier` interface the service already depends on.
4. Then write **one sentence**, in a comment at the top of
   `ConsoleNotifier.java`, naming which SOLID principle this
   demonstrates and why.

*Which principle — read this only after you've written your own
sentence:* the **Open/Closed Principle** — the system was
extended with new behavior by adding new code, and the existing
`DocumentUploadService` was closed to modification throughout. (Watch
for the supporting cast, too: the reason OCP was achievable here is
that `DocumentUploadService` depends on the `Notifier` abstraction
rather than a concrete class, which is DIP; and it works only because
`ConsoleNotifier` honors `Notifier`'s contract, which is LSP.)

*Note that `Main.java` did change.* That's fine and expected — the
composition root is supposed to change when you add a component. It's
the *consumer* that must stay closed. Knowing the difference between
"the wiring changed" and "the business logic changed" is the whole
point.

### Part 3 — Variation task: streams and `Optional`

Two small rewrites. The requirement in both cases is **identical
observable behavior** — same output, same ordering.

Both use one shared record. Put it in its own file:

```java
// Document.java
public record Document(String title, int pageCount) {}
```

**(a) Loop → stream pipeline.** This one is deliberately *not* the
title-normalization example from section 4 — that one is already solved
up there, and copying it back down here would teach you nothing. This
is a different shape: filter a `List<Document>` on two conditions, sort
the survivors, then extract a field into a formatted label.

Put this in `Catalog.java`, run it, and record the output.

```java
// Catalog.java
import java.util.ArrayList;
import java.util.List;

public class Catalog {

    /** Long policy documents, largest first, as display labels. */
    public static List<String> policyLabels(List<Document> docs) {
        List<Document> matches = new ArrayList<>();
        for (Document d : docs) {
            if (d.title().startsWith("policy-") && d.pageCount() >= 5) {
                matches.add(d);
            }
        }
        matches.sort((a, b) -> Integer.compare(b.pageCount(), a.pageCount()));

        List<String> labels = new ArrayList<>();
        for (Document d : matches) {
            labels.add(d.title() + " (" + d.pageCount() + "p)");
        }
        return labels;
    }

    public static void main(String[] args) {
        List<Document> docs = List.of(
                new Document("policy-onboarding", 7),
                new Document("runbook-incident-response", 25),
                new Document("policy-expense-reimbursement", 4),
                new Document("policy-remote-work", 12),
                new Document("changelog-api", 31));
        System.out.println(policyLabels(docs));
    }
}
```

Run it with `javac Document.java Catalog.java && java Catalog`.
Expected output:

```
[policy-remote-work (12p), policy-onboarding (7p)]
```

Trace why, before you rewrite anything: `runbook-incident-response` and
`changelog-api` are dropped by the prefix test;
`policy-expense-reimbursement` passes the prefix test but is dropped by
the page-count test (4 < 5); and the sort genuinely reorders — the two
survivors arrive in the order 7 then 12 and leave as 12 then 7.

Now rewrite `policyLabels` as a single stream pipeline that returns the
same list. You need four stages: `filter` (you can use one `filter`
with `&&`, or two chained — both are correct, and two is usually easier
to read), `sorted`, `map`, and `toList`.

*The one piece worth a hint*, because it's the only part you can't get
to from section 4: `sorted` takes a `Comparator`, and you don't have to
write one by hand —
[`Comparator.comparingInt`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html)
builds one from a key extractor, and every `Comparator` has a
`reversed()`. That's the same `Comparator` interface from the Strategy
preview in section 6: you're selecting a sorting algorithm's ordering
by passing in a strategy object rather than by editing the sort. You'll
need `import java.util.Comparator;`.

*Watch out for:* `List.of(...)` returns an immutable list, which is why
the imperative version copies into an `ArrayList` before calling
`sort`. A stream's `sorted` has no such problem — it never touches the
source. And note the sort must stay *between* the filtering and the
label-building: sorting formatted strings instead of documents would
order them alphabetically by title, not numerically by page count.

**(b) `null` return → `Optional<T>`.** Here is a repository that uses
the null convention.

```java
// DocumentRepository.java
import java.util.List;

public class DocumentRepository {

    private final List<Document> documents = List.of(
            new Document("handbook", 42),
            new Document("runbook", 17));

    // Returns null when nothing matches.
    public Document findByTitle(String title) {
        for (Document d : documents) {
            if (d.title().equals(title)) {
                return d;
            }
        }
        return null;
    }
}
```

And a caller that has to remember the null check:

```java
DocumentRepository repo = new DocumentRepository();

Document found = repo.findByTitle("handbook");
int pages = (found == null) ? 0 : found.pageCount();
System.out.println(pages);          // 42

Document missing = repo.findByTitle("nope");
int missingPages = (missing == null) ? 0 : missing.pageCount();
System.out.println(missingPages);   // 0
```

Change `findByTitle` to return `Optional<Document>` (a stream with
`filter` and `findFirst` is the shortest route), then rewrite both
call sites with `map` and `orElse` so that no `== null` comparison
remains anywhere. The two printed numbers must still be `42` and `0`.

Then add a third call site that uses `orElseThrow` with the
`DocumentNotFoundException` from section 5, and confirm that asking for
a missing title now produces a message naming the title instead of a
bare `NullPointerException` somewhere downstream. That contrast —
a precise, named failure at the point of the problem versus an
anonymous NPE three frames later — is the actual argument for
`Optional`, and it's worth seeing once rather than taking on faith.

## Checkpoint

You're done with Day 2 when all four of these hold:

1. `javac *.java && java Main` compiles and runs with
   `ConsoleNotifier` wired in, and `git diff` (or your editor's
   history) shows **zero changes to `DocumentUploadService.java`**
   since Part 1. This is the single most important checkpoint of the
   day.
2. `ConsoleNotifier.java` carries a one-sentence comment naming the
   Open/Closed Principle and saying why it applies.
3. The stream version of `policyLabels` prints exactly the same list as
   the loop version did, for the same input —
   `[policy-remote-work (12p), policy-onboarding (7p)]` — and the method
   body contains no `for` loop and no intermediate `ArrayList`.
4. `findByTitle` returns `Optional<Document>`, every call site is free
   of `== null` checks, the two printed page counts are still `42` and
   `0`, and the `orElseThrow` call site reports the missing title by
   name.

## Interview angle

*"What does Inversion of Control mean, and how does Spring implement
it?"*

You now have a hand-built example to point at, which is a much stronger
answer than a definition. The shape of a good response:

- **The general idea first.** Inversion of Control means a class no
  longer controls when or how its collaborators are created; that
  control moves outside the class. Dependency injection is one specific
  form of IoC — Spring's own docs call it "a specialized form of IoC" —
  where the collaborators are supplied from outside, typically as
  constructor arguments.
- **Then the concrete example.** "I wrote a `DocumentUploadService`
  that takes a `Notifier` interface in its constructor. Adding a third
  notifier implementation required writing one new class and changing
  one line in the composition root — the service itself never changed.
  That's the Open/Closed Principle falling out of the Dependency
  Inversion Principle."
- **Then how Spring does the same thing.** Spring's IoC container —
  `ApplicationContext`, a superset of `BeanFactory` — takes over the
  role my `main` method was playing. It discovers the beans, works out
  what each constructor needs, and injects it. With a single
  constructor you don't even need `@Autowired`.
- **Then why it's worth doing.** Immutable components (the field can be
  `final`), no possibility of a missing dependency at runtime, and
  testability — you can inject a fake `Notifier` in a unit test, which
  is impossible if the class news up its own collaborators.

If they push further, the natural follow-up is "so where do you draw
the line?" — and the honest answer is Spring's own rule of thumb:
constructor injection for mandatory dependencies, setters for optional
ones with sensible defaults, and a long constructor argument list is a
smell that the class has too many responsibilities (which is SRP
telling you to split it).

## Vocabulary you'll see referenced again

Later days assume today's definitions and refer to them by these exact
names.

| Term | Definition established today | Next used |
|---|---|---|
| Inversion of Control (IoC) | Control over creating/locating collaborators moves out of the class | Day 3 |
| Dependency Injection (DI) | A specialized form of IoC; collaborators supplied from outside, preferably via constructor | Day 3 |
| Composition root | The single place that knows the concrete implementations and wires them (your `main`; Spring's container) | Day 3 |
| Open/Closed Principle | Extend behavior by adding new code, without modifying existing code | Days 8–10, 15 |
| Dependency Inversion Principle | Depend on abstractions, not concrete details | Day 3, Day 19 |
| Strategy | A family of interchangeable algorithms behind one interface; the algorithm varies independently of its clients | Days 8–10 |
| Builder | Separates step-by-step construction of a complex object from its representation | Days 11–14 |
| Decorator | Wraps an object in another object implementing the same interface, adding behavior around it | Day 15 |
