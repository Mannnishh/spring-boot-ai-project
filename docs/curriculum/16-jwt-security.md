# Day 16 — Spring Security (JWT) [optional]

> **Optional — cut this day first if you're behind schedule.** Nothing on
> Day 17 onward depends on this day. If you're behind, skip straight to
> Day 17 and come back only if you have slack left — the interview angle
> below is worth reading even if you never write the code.

**Focus:** Put a lock on the front door. Every endpoint built since Day 3
— `/documents/*`, `/ask` — has been wide open to anyone who can reach the
port. Today adds a `/login` endpoint that issues a signed JSON Web Token
for a hardcoded user, and a Spring Security filter chain that requires a
valid token on `/ask` and `/documents/*` before the request reaches those
controllers at all. The mechanics (a controller, a config class) are
familiar; what's new is the trust model underneath them, so this is a
guided-TODO day with a real gated Answer Key, same shape as Day 11–14.

## Concept primer

### What's actually inside a JWT

A JWT is three Base64URL-encoded segments joined by dots:
`header.payload.signature`. The header names the signing algorithm
(`{"alg":"HS256"}`); the payload is a JSON object of **claims** — `sub`
(who this token is for), `iat` (issued-at timestamp), `exp` (expiry
timestamp), plus whatever custom claims an application adds; the
signature is an HMAC computed over the header and payload using a secret
key only the server knows. Critically, the header and payload are only
*encoded*, not encrypted — anyone can Base64-decode a JWT and read its
claims in plain text. The signature doesn't hide the contents; it proves
the contents haven't been tampered with since a party holding the secret
key produced them.

### Why signature verification replaces a session lookup

Traditional session auth works like this: on login, the server generates
an opaque session ID, stores the session's state (who's logged in, when
it expires) in a server-side store — an in-memory map, Redis, a database
table — and hands the client that ID as a cookie. Every subsequent
request carries the cookie, and the server does a **lookup**: is this ID
in the store, and what does it say? That lookup is a stateful operation —
it requires the server (or a shared store every server instance can
reach) to remember something about every logged-in user.

A JWT flips this. The token itself carries the claims that used to live
in server-side session state (`sub`, `exp`, whatever else). Instead of
looking anything up, the server **recomputes the signature** over the
token's header and payload using the same secret key it signed with, and
compares it to the signature on the token. If they match, the claims are
provably untampered — the server doesn't need to remember having issued
this specific token; it only needs to remember the one secret key that
lets it verify *any* token it (or another instance holding that key)
issued. That's the sense in which JWT auth is "stateless": authenticating
a request costs one cryptographic comparison, not a round trip to a
shared store, and any number of app instances can verify the same token
independently as long as they all hold the same key — no session
replication, no sticky routing, no shared cache.

### What this buys you, and what it costs

The tradeoffs sharpen into the interview angle below, but the shape is:
gaining independence from a shared session store also means giving up the
session store's easiest feature — **instant revocation**. Deleting a
session row invalidates it immediately; a JWT, once issued, is valid
until it expires no matter what the server does, unless you build some
other form of state back in (a blocklist of revoked token IDs) — which
partially undoes the "no server-side state" benefit that was the whole
point.

## Reference links

**Spring Security — OAuth2 Resource Server (JWT)**

- [Spring Security Reference — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
  (current release, Spring Security 7.1.1 as fetched today) — the
  official page for validating JWT bearer tokens. Two sections drive
  today's config directly:
  - ["Trusting a Single Symmetric Key"](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html#oauth2resourceserver-jwt-decoder-secret-key) —
    `NimbusJwtDecoder.withSecretKey(this.key).build()`, the exact call
    today's `JwtDecoder` bean uses. No `issuer-uri` or external
    authorization server involved — the app trusts a key it holds
    directly, which fits a self-issued/self-verified token.
  - The page also states: *"If the application doesn't expose a
    `JwtDecoder` bean, then Spring Boot will expose the [issuer-uri-based]
    default one."* — confirming that defining our own `JwtDecoder` bean
    is enough; Spring Boot's autoconfiguration steps aside and uses ours.
- [`NimbusJwtDecoder` — Javadoc](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.html) —
  confirms the static factory methods (`withSecretKey`, `withPublicKey`,
  `withJwkSetUri`, `withIssuerLocation`) and that `withSecretKey(...)`
  returns a `SecretKeyJwtDecoderBuilder` whose `.macAlgorithm(...)` takes
  one of `HS256`/`HS384`/`HS512` — today's config sets it explicitly to
  `HS256` to match jjwt's signing algorithm below, rather than relying on
  an unstated default.

**Token issuance — jjwt (verified current and actively maintained)**

The brief's second question — jjwt vs. Spring Authorization Server — has
a clear answer for this use case. **Spring Authorization Server** is a
full OAuth2/OIDC *authorization server*: it exists to run a
`/oauth2/authorize` + `/oauth2/token` flow for third-party clients
registered against it, with consent screens, refresh-token rotation, and
the rest of the OAuth2 machinery. That's real infrastructure for a
different problem — "let other applications delegate login to us" — not
"let our one API issue and check its own tokens for a hardcoded demo
user," which is what the brief asks for. For that, a small signing
library is the right-sized tool, and **jjwt** is it:

- [jjwt — GitHub](https://github.com/jwtk/jjwt) — verified live today:
  not archived, most recent push within the last month (checked via the
  GitHub API), 11k+ stars. Current release **0.13.0**, confirmed against
  [Maven Central's `jjwt-api` metadata](https://repo.maven.apache.org/maven2/io/jsonwebtoken/jjwt-api/maven-metadata.xml)
  (`<latest>`/`<release>` both `0.13.0`).
- The README's quickstart is the source for today's `Jwts.builder()...
  .signWith(key).compact()` call below — jjwt's fluent builder API,
  introduced in the 0.12.x line and unchanged in shape through 0.13.0.
  jjwt is only used to *issue* tokens today; *verifying* them is Spring
  Security's job (`NimbusJwtDecoder`, above) — the two libraries don't
  need to agree on anything beyond the raw signing key and the HS256
  algorithm.

**Internal (already verified on earlier days)**

- [Day 11–14 — `/ask` endpoint](11-14-ask-endpoint-rag-pipeline.md) —
  the endpoint today's filter chain gates.
- [Day 6–7 — `/documents/upload`](06-07-file-upload-tika.md) — the other
  endpoint family today gates (`/documents/**`).
- [Day 1 — Git workflow](01-day1-git-and-style.md) — the feature-branch +
  Conventional Commits workflow this day follows too.

## Exercise (guided TODO + hints)

Do today's work on a new feature branch, per Day 1's workflow:

```bash
git checkout -b feature/jwt-security
```

### 1. Add the dependencies

`spring-boot-starter-oauth2-resource-server` pulls in Spring Security
itself (`spring-boot-starter-security`) plus the JOSE/JWT decoding
support (`NimbusJwtDecoder` and friends) transitively — confirmed by its
own POM on Maven Central — so it's the only Spring dependency you need to
add. jjwt is not a Spring project, so it's pinned to an explicit verified
version:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.13.0</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.13.0</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.13.0</version>
    <scope>runtime</scope>
</dependency>
```

*Note:* `jjwt-impl` and `jjwt-jackson` are `runtime` scope on purpose —
your code only calls into the `jjwt-api` interfaces (`Jwts`, `Jwts.SIG`);
the implementation and JSON serializer are wired in by the JVM at
runtime, the same split as an SLF4J API/binding pair.

### 2. One shared signing key, one bean

`AuthController` (issues tokens) and the `JwtDecoder` (verifies them)
both need the *same* secret key. Rather than re-deriving it in two
places, generate it once as a Spring bean and inject it into both:

```java
package com.example.demo;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    // Generated once at startup, shared by the issuer (AuthController,
    // via jjwt) and the verifier (SecurityConfig's JwtDecoder, via
    // Spring Security). Tokens are only valid for the lifetime of this
    // process — restart the app and every previously issued token stops
    // verifying, because the key that would confirm its signature is
    // gone. Fine for this exercise; a real deployment loads a fixed key
    // from configuration/secrets storage instead of generating a fresh
    // one per restart.
    @Bean
    public SecretKey jwtSigningKey() {
        return Jwts.SIG.HS256.key().build();
    }
}
```

### 3. TODO — `POST /login`

Request/response shapes first:

```java
package com.example.demo;

public record LoginRequest(String username, String password) {}
```

```java
package com.example.demo;

public record LoginResponse(String token) {}
```

The controller, with the credential check and token signing left as
TODOs:

```java
package com.example.demo;

import javax.crypto.SecretKey;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    // Hardcoded per the brief. A real app checks a UserDetailsService-
    // backed store with a hashed password (BCryptPasswordEncoder), never
    // a literal String.equals — and even String.equals itself is a known
    // weak spot here: it short-circuits on the first differing
    // character, so its running time leaks how much of a guess was
    // correct (a timing attack). The constant-time fix is
    // MessageDigest.isEqual(byte[], byte[]), not String.equals — naming
    // both gaps is part of today's interview angle.
    private static final String HARDCODED_USERNAME = "demo";
    private static final String HARDCODED_PASSWORD = "demo-password";
    private static final long EXPIRATION_MILLIS = 3_600_000; // 1 hour

    private final SecretKey jwtSigningKey;

    public AuthController(SecretKey jwtSigningKey) {
        this.jwtSigningKey = jwtSigningKey;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // TODO: if request.username()/request.password() don't match the
        // hardcoded pair, return ResponseEntity.status(401).build().
        //
        // TODO: otherwise, sign a JWT with jjwt — subject = username,
        // issuedAt = now, expiration = now + EXPIRATION_MILLIS,
        // signWith(jwtSigningKey), then .compact() to get the token
        // string — and return it wrapped in a LoginResponse via
        // ResponseEntity.ok(...).
        throw new UnsupportedOperationException("not implemented");
    }
}
```

*Hints:*

- jjwt's fluent builder, from the verified quickstart above:
  `Jwts.builder().subject(username).issuedAt(new Date()).expiration(new
  Date(System.currentTimeMillis() + EXPIRATION_MILLIS)).signWith
  (jwtSigningKey).compact()`. `signWith(Key)` infers HS256 from the key
  itself — you don't pass the algorithm a second time.
- `ResponseEntity<LoginResponse>` as the return type means the 401 branch
  is `ResponseEntity.status(401).build()` (a `LoginResponse`-typed empty
  body), and the success branch is `ResponseEntity.ok(new
  LoginResponse(token))`.

### 4. TODO — the security filter chain

```java
package com.example.demo;

import javax.crypto.SecretKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        // TODO: NimbusJwtDecoder.withSecretKey(jwtSigningKey)
        //     .macAlgorithm(MacAlgorithm.HS256)
        //     .build();
        // — the "Trusting a Single Symmetric Key" pattern from the
        // reference doc linked above, explicit about the algorithm so it
        // matches jjwt's HS256 signature exactly.
        throw new UnsupportedOperationException("not implemented");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // TODO, in order:
        // 1. http.csrf(AbstractHttpConfigurer::disable) — CSRF defends
        //    cookie/session-carried auth against a browser silently
        //    replaying a user's own credentials; this API sends its
        //    token in an Authorization header a form/image tag can't
        //    forge, so there's nothing for CSRF protection to defend.
        // 2. .authorizeHttpRequests(auth -> auth
        //        .requestMatchers("/login").permitAll()
        //        .requestMatchers("/ask", "/documents/**").authenticated()
        //        .anyRequest().authenticated())
        //    — default-deny: name every public route explicitly (just
        //    /login today) and let the catch-all require authentication
        //    for everything else, including routes this day doesn't
        //    otherwise mention (e.g. bare GET /documents). Every
        //    authorizeHttpRequests example on the reference doc above
        //    ends its chain with .anyRequest().authenticated() for
        //    exactly this reason — fail closed by default, not open.
        // 3. .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        //    — wires the bearer-token filter using the JwtDecoder bean
        //    above (no issuer-uri needed: we're both issuer and verifier
        //    in the same process).
        // 4. return http.build();
        throw new UnsupportedOperationException("not implemented");
    }
}
```

*Hint:* `@EnableWebSecurity` is technically optional once a
`SecurityFilterChain` bean exists — Spring Boot's security
autoconfiguration picks it up either way — but the reference doc's own
custom-config example includes it explicitly, and so does this one, for
clarity about what's happening.

## Checkpoint

Start the app, then confirm all four:

```bash
# 1. Unauthenticated /ask is rejected.
curl -i -X POST localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the equipment stipend for remote work?"}'
# -> HTTP/1.1 401
```

```bash
# 2. Bad credentials are rejected.
curl -i -X POST localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username": "demo", "password": "wrong"}'
# -> HTTP/1.1 401
```

```bash
# 3. Correct credentials return a token.
curl -X POST localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username": "demo", "password": "demo-password"}'
# -> {"token":"eyJhbGciOiJIUzI1NiJ9....."}
# Copy the token string and export it:
export TOKEN="eyJhbGciOiJIUzI1NiJ9....."
```

```bash
# 4. The same /ask request, with the token, succeeds.
curl -i -X POST localhost:8080/ask \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question": "What is the equipment stipend for remote work?"}'
# -> HTTP/1.1 200, the same grounded answer Day 11-14 produced
```

Repeat step 1 and step 4 against `POST /documents/upload` (Day 6–7) to
confirm the second gated route behaves the same way: 401 without a token,
success with one.

## Interview angle

*"Stateless vs. session-based authentication — what's the tradeoff, and
when would you pick one over the other?"*

Session-based auth keeps a record of every logged-in user server-side
(session store), and the client holds only an opaque pointer to it (the
session ID/cookie). That gives you the thing JWTs are structurally bad
at: **instant, centralized revocation** — delete the session row and the
user is logged out everywhere, immediately, including mid-token-lifetime.
The cost is that every server instance answering requests needs access to
that shared store, so scaling out means either sticky sessions (route a
user back to the instance holding their session) or a shared cache like
Redis — an extra piece of infrastructure and a network hop on every
authenticated request.

JWT auth inverts the tradeoff. The token is self-contained and
cryptographically verifiable, so any instance holding the signing key
can authenticate a request with zero lookups and zero shared
infrastructure — which is exactly why it fits horizontally-scaled,
stateless services well. The cost is the one session auth doesn't have:
a JWT is valid until it expires no matter what the server does *after*
issuing it. Revoking one early requires bolting a denylist of
token-IDs-not-to-trust back onto the server — which reintroduces the
server-side state and the lookup-per-request cost JWT auth exists to
avoid. In practice, most production JWT setups split the difference:
short-lived access tokens (minutes, to bound how long a compromised or
"revoked" token stays usable) plus a longer-lived, server-tracked refresh
token that *can* be revoked, rather than betting everything on one
long-lived stateless token.

*A related angle worth having ready about today's own `/login` endpoint:*
comparing credentials with `String.equals` isn't just an
authentication-*source* problem (hardcoded constants vs. a real
`UserDetailsService`) — `String.equals` itself is a variable-time
comparison. It short-circuits and returns as soon as it hits the first
differing character, so how long the call takes leaks how many leading
characters of a guessed password were correct, a textbook timing attack
against a naive credential check. The production-correct comparison is
constant-time — `MessageDigest.isEqual(byte[], byte[])`, which always
inspects every byte no matter where a mismatch occurs — and naming that,
unprompted, is a strong signal in a security-focused interview even
though it's an easy detail to overlook.

## Answer Key (attempt it yourself first!)

Only read this after the checkpoint's four curl commands pass.
`LoginRequest`, `LoginResponse`, and `JwtConfig` are already shown in
full above — not repeated here. This key fills in `AuthController.login`
and both `SecurityConfig` beans.

**`AuthController.java`** (complete)

```java
package com.example.demo;

import java.util.Date;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private static final String HARDCODED_USERNAME = "demo";
    private static final String HARDCODED_PASSWORD = "demo-password";
    private static final long EXPIRATION_MILLIS = 3_600_000; // 1 hour

    private final SecretKey jwtSigningKey;

    public AuthController(SecretKey jwtSigningKey) {
        this.jwtSigningKey = jwtSigningKey;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        if (!HARDCODED_USERNAME.equals(request.username())
                || !HARDCODED_PASSWORD.equals(request.password())) {
            return ResponseEntity.status(401).build();
        }

        String token = Jwts.builder()
                .subject(request.username())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MILLIS))
                .signWith(jwtSigningKey)
                .compact();

        return ResponseEntity.ok(new LoginResponse(token));
    }
}
```

**`SecurityConfig.java`** (complete)

```java
package com.example.demo;

import javax.crypto.SecretKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/ask", "/documents/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

Commit on the feature branch with a Conventional Commits message (e.g.
`feat: add JWT login and security filter chain for /ask and /documents`),
push, and merge via a pull request per Day 1.
