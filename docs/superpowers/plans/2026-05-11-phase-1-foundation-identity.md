# Phase 1: Backend Foundation + Identity Module

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Docker infrastructure, define the monolith's modular package structure, and implement a working auth system (register + login) with JWT and role-based access control.

**Architecture:** Single Spring Boot application organized in 5 domain packages (identity, catalog, session, billing, kitchen). No cross-package direct class access — modules communicate through service interfaces only. SecurityConfig lives in a top-level `config` package as a cross-cutting concern.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Security, JJWT 0.12.3, Spring Data JPA, PostgreSQL 16, Docker Compose, JUnit 5, Mockito, MockMvc.

---

## File Map

```
[project root]/
└── docker-compose.yml                                           CREATE

backend/
├── pom.xml                                                      MODIFY
├── src/main/resources/application.properties                   MODIFY
└── src/main/java/com/vanter/ember/
    ├── config/
    │   ├── SecurityConfig.java                                  CREATE
    │   └── GlobalExceptionHandler.java                         CREATE
    ├── identity/
    │   ├── controller/AuthController.java                       CREATE
    │   ├── service/
    │   │   ├── AuthService.java                                 CREATE
    │   │   ├── JwtService.java                                  CREATE
    │   │   └── EmberUserDetailsService.java                     CREATE
    │   ├── repository/UserRepository.java                       CREATE
    │   └── model/
    │       ├── User.java                                        CREATE
    │       ├── Role.java                                        CREATE
    │       └── dto/
    │           ├── RegisterRequest.java                         CREATE
    │           ├── LoginRequest.java                            CREATE
    │           └── AuthResponse.java                            CREATE
    ├── catalog/     (empty package marker)                      CREATE
    ├── session/     (empty package marker)                      CREATE
    ├── billing/     (empty package marker)                      CREATE
    └── kitchen/     (empty package marker)                      CREATE

backend/src/test/java/com/vanter/ember/
    ├── identity/service/JwtServiceTest.java                     CREATE
    ├── identity/service/AuthServiceTest.java                    CREATE
    └── identity/controller/AuthControllerTest.java              CREATE
```

---

## Task 1: Docker Compose Infrastructure

**Files:**
- Create: `docker-compose.yml` (project root, next to `backend/` and `frontend/`)

- [ ] **Step 1: Create docker-compose.yml**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: ember
      POSTGRES_USER: ember
      POSTGRES_PASSWORD: ember
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  mongodb:
    image: mongo:7
    environment:
      MONGO_INITDB_ROOT_USERNAME: ember
      MONGO_INITDB_ROOT_PASSWORD: ember
      MONGO_INITDB_DATABASE: ember
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

volumes:
  postgres_data:
  mongo_data:
  minio_data:
```

- [ ] **Step 2: Start infrastructure and verify**

```bash
docker compose up -d
docker compose ps
```

Expected: all 3 services show `running`.

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "EMB-5: add docker-compose with postgres, mongodb, minio"
```

---

## Task 2: Add Missing Dependencies

**Files:**
- Modify: `backend/pom.xml`

The current pom.xml is missing: Spring Security, JWT library, PostgreSQL driver, and Bean Validation.

- [ ] **Step 1: Add dependencies inside the `<dependencies>` block in pom.xml**

Add after the existing `spring-boot-starter-web` dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: Verify the project compiles**

```bash
cd backend
./mvnw compile -q
```

Expected: BUILD SUCCESS with no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "EMB-5: add security, jwt, postgresql, validation dependencies"
```

---

## Task 3: Application Properties + Module Structure

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/vanter/ember/catalog/package-info.java`
- Create: `backend/src/main/java/com/vanter/ember/session/package-info.java`
- Create: `backend/src/main/java/com/vanter/ember/billing/package-info.java`
- Create: `backend/src/main/java/com/vanter/ember/kitchen/package-info.java`

- [ ] **Step 1: Replace application.properties with full config**

```properties
spring.application.name=ember

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/ember
spring.datasource.username=ember
spring.datasource.password=ember
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.open-in-view=false

# MongoDB
spring.data.mongodb.uri=mongodb://ember:ember@localhost:27017/ember?authSource=admin

# JWT
jwt.secret=ember-secret-key-must-be-at-least-32-characters-long-for-hs256
jwt.expiration-ms=86400000

# MinIO (configured in Task — catalog module plan)
minio.url=http://localhost:9000
minio.access-key=minioadmin
minio.secret-key=minioadmin
minio.bucket=ember-media
```

- [ ] **Step 2: Create package-info.java for each future module**

`backend/src/main/java/com/vanter/ember/catalog/package-info.java`:
```java
package com.vanter.ember.catalog;
```

`backend/src/main/java/com/vanter/ember/session/package-info.java`:
```java
package com.vanter.ember.session;
```

`backend/src/main/java/com/vanter/ember/billing/package-info.java`:
```java
package com.vanter.ember.billing;
```

`backend/src/main/java/com/vanter/ember/kitchen/package-info.java`:
```java
package com.vanter.ember.kitchen;
```

- [ ] **Step 3: Verify app starts (requires docker-compose running)**

```bash
cd backend
./mvnw spring-boot:run
```

Expected: Spring Boot starts on port 8080 without errors. Stop it with Ctrl+C after it starts.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application.properties
git add backend/src/main/java/com/vanter/ember/catalog/
git add backend/src/main/java/com/vanter/ember/session/
git add backend/src/main/java/com/vanter/ember/billing/
git add backend/src/main/java/com/vanter/ember/kitchen/
git commit -m "EMB-5: configure application properties and scaffold module packages"
```

---

## Task 4: User Model

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/model/Role.java`
- Create: `backend/src/main/java/com/vanter/ember/identity/model/User.java`
- Create: `backend/src/main/java/com/vanter/ember/identity/model/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/identity/model/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/identity/model/dto/AuthResponse.java`

- [ ] **Step 1: Create Role.java**

```java
package com.vanter.ember.identity.model;

public enum Role {
    CUSTOMER, WAITER, KITCHEN, ADMIN
}
```

- [ ] **Step 2: Create User.java**

```java
package com.vanter.ember.identity.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
```

- [ ] **Step 3: Create RegisterRequest.java**

```java
package com.vanter.ember.identity.model.dto;

import com.vanter.ember.identity.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotNull(message = "Role is required")
    private Role role;
}
```

- [ ] **Step 4: Create LoginRequest.java**

```java
package com.vanter.ember.identity.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

- [ ] **Step 5: Create AuthResponse.java**

```java
package com.vanter.ember.identity.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String userId;
    private String name;
    private String role;
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd backend
./mvnw compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/
git commit -m "EMB-5: add User entity, Role enum, and auth DTOs"
```

---

## Task 5: UserRepository

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/repository/UserRepository.java`

- [ ] **Step 1: Create UserRepository.java**

```java
package com.vanter.ember.identity.repository;

import com.vanter.ember.identity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd backend
./mvnw compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/repository/
git commit -m "EMB-5: add UserRepository with findByEmail and existsByEmail"
```

---

## Task 6: JwtService (TDD)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/service/JwtService.java`
- Create: `backend/src/test/java/com/vanter/ember/identity/service/JwtServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.vanter.ember.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET =
            "ember-secret-key-must-be-at-least-32-characters-long-for-hs256";
    private static final long EXPIRATION_MS = 3_600_000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken("user@test.com", Map.of("role", "CUSTOMER"));
        assertThat(token).isNotBlank();
    }

    @Test
    void extractSubject_returnsCorrectSubject() {
        String token = jwtService.generateToken("user@test.com", Map.of());
        assertThat(jwtService.extractSubject(token)).isEqualTo("user@test.com");
    }

    @Test
    void isTokenValid_returnsTrueForFreshToken() {
        String token = jwtService.generateToken("user@test.com", Map.of());
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForExpiredToken() {
        JwtService shortLivedService = new JwtService(SECRET, -1L);
        String token = shortLivedService.generateToken("user@test.com", Map.of());
        assertThat(shortLivedService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        String token = jwtService.generateToken("user@test.com", Map.of()) + "tampered";
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd backend
./mvnw test -pl . -Dtest=JwtServiceTest -q
```

Expected: FAIL — `JwtService` class does not exist yet.

- [ ] **Step 3: Implement JwtService.java**

```java
package com.vanter.ember.identity.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subject, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(extraClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            return extractAllClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -Dtest=JwtServiceTest -q
```

Expected: Tests run: 5, Failures: 0, Errors: 0.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/service/JwtService.java
git add backend/src/test/java/com/vanter/ember/identity/service/JwtServiceTest.java
git commit -m "EMB-5: add JwtService with TDD (generate, extract, validate)"
```

---

## Task 7: EmberUserDetailsService + SecurityConfig

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/service/EmberUserDetailsService.java`
- Create: `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`

No TDD here — both are Spring wiring components. They are exercised by the AuthController integration tests in Task 9.

- [ ] **Step 1: Create EmberUserDetailsService.java**

```java
package com.vanter.ember.identity.service;

import com.vanter.ember.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmberUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
```

- [ ] **Step 2: Create SecurityConfig.java**

```java
package com.vanter.ember.config;

import com.vanter.ember.identity.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public OncePerRequestFilter jwtAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String authHeader = request.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    chain.doFilter(request, response);
                    return;
                }
                String token = authHeader.substring(7);
                if (!jwtService.isTokenValid(token)) {
                    chain.doFilter(request, response);
                    return;
                }
                String email = jwtService.extractSubject(token);
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                chain.doFilter(request, response);
            }
        };
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd backend
./mvnw compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/service/EmberUserDetailsService.java
git add backend/src/main/java/com/vanter/ember/config/SecurityConfig.java
git commit -m "EMB-5: add UserDetailsService and JWT security filter chain"
```

---

## Task 8: AuthService (TDD)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- Create: `backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthService authService;

    @Test
    void register_savesUserAndReturnsToken() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("ana@test.com");
        req.setPassword("secret");
        req.setRole(Role.CUSTOMER);

        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("user-1");
            return u;
        });
        when(jwtService.generateToken(eq("ana@test.com"), anyMap())).thenReturn("jwt-token");

        AuthResponse response = authService.register(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getName()).isEqualTo("Ana");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@test.com");
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already in use");
    }

    @Test
    void login_returnsTokenForValidCredentials() {
        User user = User.builder()
                .id("user-1").name("Ana").email("ana@test.com")
                .passwordHash("hashed").role(Role.CUSTOMER).build();
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("secret");

        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.generateToken(eq("ana@test.com"), anyMap())).thenReturn("jwt-token");

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void login_throwsForUnknownEmail() {
        LoginRequest req = new LoginRequest();
        req.setEmail("unknown@test.com");
        req.setPassword("any");

        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_throwsForWrongPassword() {
        User user = User.builder()
                .email("ana@test.com").passwordHash("hashed").role(Role.CUSTOMER).build();
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("wrong");

        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd backend
./mvnw test -Dtest=AuthServiceTest -q
```

Expected: FAIL — `AuthService` class does not exist yet.

- [ ] **Step 3: Implement AuthService.java**

```java
package com.vanter.ember.identity.service;

import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(
                user.getEmail(),
                Map.of("role", user.getRole().name(), "userId", user.getId())
        );

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtService.generateToken(
                user.getEmail(),
                Map.of("role", user.getRole().name(), "userId", user.getId())
        );

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -Dtest=AuthServiceTest -q
```

Expected: Tests run: 5, Failures: 0, Errors: 0.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/service/AuthService.java
git add backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java
git commit -m "EMB-5: add AuthService with register and login logic (TDD)"
```

---

## Task 9: AuthController + GlobalExceptionHandler (TDD)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/identity/controller/AuthController.java`
- Create: `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/vanter/ember/identity/controller/AuthControllerTest.java`

- [ ] **Step 1: Write the failing controller tests**

```java
package com.vanter.ember.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.service.AuthService;
import com.vanter.ember.identity.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    @Test
    void register_returns200WithTokenAndName() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("ana@test.com");
        req.setPassword("secret");
        req.setRole(Role.CUSTOMER);

        when(authService.register(any())).thenReturn(
                AuthResponse.builder().token("jwt-token").userId("u-1").name("Ana").role("CUSTOMER").build()
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_returns409WhenEmailAlreadyExists() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("ana@test.com");
        req.setPassword("secret");
        req.setRole(Role.CUSTOMER);

        when(authService.register(any()))
                .thenThrow(new IllegalArgumentException("Email already in use"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_returns400ForInvalidBody() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("");
        req.setEmail("not-an-email");
        req.setPassword("x");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200WithToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("secret");

        when(authService.login(any())).thenReturn(
                AuthResponse.builder().token("jwt-token").userId("u-1").name("Ana").role("CUSTOMER").build()
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_returns401ForBadCredentials() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("wrong");

        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd backend
./mvnw test -Dtest=AuthControllerTest -q
```

Expected: FAIL — `AuthController` class does not exist yet.

- [ ] **Step 3: Create GlobalExceptionHandler.java**

```java
package com.vanter.ember.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }
}
```

- [ ] **Step 4: Create AuthController.java**

```java
package com.vanter.ember.identity.controller;

import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./mvnw test -Dtest=AuthControllerTest -q
```

Expected: Tests run: 5, Failures: 0, Errors: 0.

- [ ] **Step 6: Run all tests**

```bash
./mvnw test -q
```

Expected: All tests pass (JwtServiceTest + AuthServiceTest + AuthControllerTest).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/controller/
git add backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java
git add backend/src/test/java/com/vanter/ember/identity/controller/
git commit -m "EMB-5: add AuthController and GlobalExceptionHandler (TDD)"
```

---

## Task 10: Smoke Test Against Running App

Verifies the full stack end-to-end with the real database.

- [ ] **Step 1: Ensure infrastructure is running**

```bash
docker compose ps
```

Expected: postgres, mongodb, minio all `running`.

- [ ] **Step 2: Start the app**

```bash
cd backend
./mvnw spring-boot:run
```

Expected: Started EmberApplication on port 8080.

- [ ] **Step 3: Register a customer**

In a new terminal:

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana","email":"ana@test.com","password":"secret123","role":"CUSTOMER"}' | jq .
```

Expected response:
```json
{
  "token": "<jwt-string>",
  "userId": "<uuid>",
  "name": "Ana",
  "role": "CUSTOMER"
}
```

- [ ] **Step 4: Login with the same user**

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@test.com","password":"secret123"}' | jq .
```

Expected: Same shape as register response with a valid JWT.

- [ ] **Step 5: Verify duplicate email returns 409**

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Ana\",\"email\":\"ana@test.com\",\"password\":\"secret123\",\"role\":\"CUSTOMER\"}" | jq .status
```

Expected: `409` (or use Postman/Insomnia if jq is not available — send the same body and verify the HTTP status code in the response panel)

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "EMB-5: identity module complete — register, login, JWT auth working"
```

---

## What Comes Next

| Plan | Covers |
|---|---|
| Phase 2 | `catalog` module — menu CRUD, table management, MinIO image upload |
| Phase 3 | `session` module — table sessions, QR, WebSocket real-time ordering |
| Phase 4 | `kitchen` module — order queue display, item status updates |
| Phase 5 | `billing` module — bill calculation, split logic, payment gateway |
| Phase 6 | React Web — Admin, Waiter, Kitchen interfaces |
| Phase 7 | React Native — Customer mobile app |
