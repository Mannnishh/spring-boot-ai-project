package com.manish.spring_boot_ai_project;

import io.jsonwebtoken.Jwts;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  public static final String HARDCODED_USERNAME = "demo";
  public static final String HARDCODED_PASSWORD = "demopassword";
  public static final long EXPIRATION_MILLIS = 3_600_000; // 1 hour

  private final SecretKey jwtSigningKey;

  public AuthController(SecretKey jwtSigningKey) {
    this.jwtSigningKey = jwtSigningKey;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    // 1. Verify credentials (demo / demo-password)
    if (!HARDCODED_USERNAME.equals(request.username())
        || !HARDCODED_PASSWORD.equals(request.password())) {
      return ResponseEntity.status(401).build();
    }
    // 2. Sign a JWT with JJWT fluent builder
    String token =
        Jwts.builder()
            .subject(request.username())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MILLIS))
            .signWith(jwtSigningKey)
            .compact();
    // 3. Return the compact Base64URL token string
    return ResponseEntity.ok(new LoginResponse(token));
  }
}
