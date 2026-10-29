package com.manish.spring_boot_ai_project;

import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

  // Generated once at startup, shared by the issuer (AuthController)
  // and the verifier (SecurityConfig's JwtDecoder via Spring Security).
  @Bean
  public SecretKey jwtSigningKey() {
    return Jwts.SIG.HS256.key().build();
  }
}
