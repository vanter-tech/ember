# Hosted Production Deployment — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up Ember's hosted SaaS SKU on one GCP `e2-medium` VM running the existing Docker Compose stack, fronted by Cloudflare (Pages ×2, Tunnel, R2), reachable at `app.ember.vanter.net` / `api.ember.vanter.net`.

**Architecture:** The React SPA and the Astro landing deploy as two Cloudflare Pages projects. The Spring Boot backend + PostgreSQL run as containers on a single hardened GCP VM with no inbound ports; `cloudflared` dials out to expose `api.ember.vanter.net`. Images live in Cloudflare R2 behind `cdn.ember.vanter.net`; nightly `pg_dump` archives go to a private GCS bucket; the GCP Ops Agent feeds Cloud Monitoring/Logging. A documented path migrates to Cloud Run + Cloud SQL later with no rewrite.

**Tech Stack:** Java 17 / Spring Boot 3.5.14, React 19 + Vite, Astro 7, Docker Compose, Cloudflare (Pages / Tunnel / R2 / DNS), GCP (Compute Engine / GCS / Secret Manager / Cloud Monitoring), GitHub Actions + GHCR.

**Spec:** `docs/superpowers/specs/2026-08-28-hosted-production-deployment-design.md`

## Global Constraints

- **Branch:** `feat/hosted-production-deployment` (already created off `main` `040bb72`). All tasks commit here.
- **Backend build gate:** `cd backend && ./mvnw test` must stay green. Baseline: **899 tests, 0 failures**. Never `mvn` or bare `tsc`.
- **Frontend/landing:** `pnpm` only. Frontend gate: `cd frontend && pnpm run build && pnpm run test && pnpm run lint` (41 tests, lint exit 0). Landing gate: `cd landing && pnpm install && pnpm build`.
- **No new backend dependencies.** This plan adds none.
- **Surgical edits** (CLAUDE.md §5): change only the lines a task needs. No file rewrites, no drive-by reformatting.
- **Scoped git staging** (CLAUDE.md §4): `git add <explicit paths>` only. NEVER `git add -A` / `git add .`.
- **One squashed atomic commit per task.** Conventional Commits, lowercase (e.g. `feat(backend): resolve image URLs from a configurable public base`). NO `Co-authored-by:` / `Signed-off-by:` / AI signatures.
- **Per CLAUDE.md §4 + §6, every task's commit also includes:** a new `reports/NN-<task-id>-<slug>.md` (next free two-digit `NN`; sections: Identification, Objective, Modified Files, What Changed, Why It Changed) and the `PROGRESS.md` update (Current Execution State + Task Queue checkbox). These are part of the task, not separate tasks.
- **Secrets:** never commit real secret values. `.env.example` gets new *keys* only.
- **Phases 3–5 are operator runbook tasks** — they run `gcloud` / Cloudflare-dashboard steps on real accounts, not code. Each still ends with an explicit verification command + expected output, and appends its steps to `deploy/RUNBOOK.md`.

---

## File Structure

**Backend (Phase 1):**
- `backend/src/main/java/com/vanter/ember/config/MinioProperties.java` — add `publicUrl` field.
- `backend/src/main/java/com/vanter/ember/catalog/service/ImageUploadService.java` — build the stored URL from `minio.public-url`; drop the `bucketName` parameter; robust `deleteImage`; `Cache-Control` header.
- `backend/src/main/java/com/vanter/ember/catalog/service/CategoryService.java` + `.../MenuItemService.java` — call the new `uploadImage(file)` signature.
- `backend/src/main/java/com/vanter/ember/config/PublicPingController.java` — **new**, `GET /v1/public/ping` for the external uptime check.
- `backend/src/main/java/com/vanter/ember/config/AuthRateLimiterFilter.java` — prefer `CF-Connecting-IP` behind a trusted peer.
- `backend/src/main/resources/application.yml` — `minio.public-url` key with a dev-compatible default.
- `backend/src/main/resources/application-prod.properties` — management port on loopback, `forward-headers-strategy`, health details off.
- Tests: `ImageUploadServiceTest`, `AuthRateLimiterFilterTest`, new `PublicPingControllerTest`.

**Deployment artifacts (Phase 2):**
- `deploy/docker-compose.prod.yml` — **new**, the production stack (`postgres`, `app`, `cloudflared`, `backup`).
- `deploy/backup/Dockerfile` + `deploy/backup/backup.sh` — **new**, nightly `pg_dump` → GCS + rotation.
- `deploy/deploy.sh` — **new**, pull + up + health-gate over IAP SSH.
- `deploy/RUNBOOK.md` — **new**, provisioning + recovery procedures (grows through Phases 3–5).
- `deploy/.env.prod.example` — **new**, the key list the VM `.env` must contain.
- `.github/workflows/backend-image.yml` — **new**, build + push `ghcr.io/vanter-tech/ember-backend`.
- `frontend/scripts/gen-env-config.mjs` — **new**, writes `dist/env-config.js` from env vars during the Pages build.
- `landing/src/components/ContactForm.tsx` — POST to `/api/contact` instead of the fake delay.
- `landing/functions/api/contact.ts` — **new**, Cloudflare Pages Function forwarding the message to a webhook.
- `landing/astro.config.mjs` — `site:` → `https://ember.vanter.net`.

**No files created in Phases 3–5** except appends to `deploy/RUNBOOK.md`.

---

# PHASE 1 — Backend production hardening

Pure repo work; ship `./mvnw test` green. Valuable independent of deployment (the image-URL fix also unblocks Ember Hub — see spec §5.3).

## Task 1: Resolve image URLs from a configurable public base

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/config/MinioProperties.java`
- Modify: `backend/src/main/java/com/vanter/ember/catalog/service/ImageUploadService.java`
- Modify: `backend/src/main/java/com/vanter/ember/catalog/service/CategoryService.java`
- Modify: `backend/src/main/java/com/vanter/ember/catalog/service/MenuItemService.java`
- Modify: `backend/src/main/resources/application.yml` (minio block, ~line 100)
- Modify: `backend/src/main/resources/.env.example` (add `MINIO_PUBLIC_URL`)
- Test: `backend/src/test/java/com/vanter/ember/catalog/service/ImageUploadServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `MinioProperties.getPublicUrl(): String`
  - `ImageUploadService.uploadImage(MultipartFile file): String` — **signature changed**, `bucketName` param removed. Returns `<minio.public-url>/<uuid>.jpg`.
  - `ImageUploadService.deleteImage(String imageUrl): void` — unchanged signature; now derives the object name from the last path segment and the bucket from `minio.bucket`.

**Context:** Today `uploadImage` returns `minioProperties.getUrl() + "/" + bucketName + "/" + objectName` (the S3 API endpoint) and callers persist that absolute string. Callers also pass inconsistent bucket names — `CategoryService` and `MenuItemService.update` pass the literal `"ember-media"`, `MenuItemService.create` passes `minioProperties.getBucket()`. Against R2 with a custom domain (`cdn.ember.vanter.net`) the public URL is not the S3 endpoint and carries no bucket segment. Fix: one configurable base (`minio.public-url`), bucket always from config.

- [ ] **Step 1: Update the failing tests in `ImageUploadServiceTest`**

Replace the class body's test methods with these (keep the imports, add `org.mockito.ArgumentCaptor` and `io.minio.PutObjectArgs` is already imported):

```java
    @Test
    void uploadImage_returnsUrlUnderConfiguredPublicBase() throws Exception {
        when(minioProperties.getPublicUrl()).thenReturn("https://cdn.example.test");
        when(minioProperties.getBucket()).thenReturn("ember-media-prod");
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", buffer);
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", buffer.toByteArray());

        String url = imageUploadService.uploadImage(file);

        assertThat(url).startsWith("https://cdn.example.test/");
        assertThat(url).endsWith(".jpg");
        assertThat(url).doesNotContain("ember-media-prod"); // no bucket segment in the public URL
    }

    @Test
    void uploadImage_setsImmutableCacheControlHeader() throws Exception {
        when(minioProperties.getPublicUrl()).thenReturn("https://cdn.example.test");
        when(minioProperties.getBucket()).thenReturn("ember-media-prod");
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", buffer);
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", buffer.toByteArray());

        imageUploadService.uploadImage(file);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().extraHeaders().get("Cache-Control"))
                .containsExactly("public, max-age=31536000, immutable");
    }

    @Test
    void uploadImage_throwsForInvalidMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> imageUploadService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void uploadImage_throwsWhenFileTooLarge() {
        byte[] largeContent = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "image", "big.jpg", "image/jpeg", largeContent);

        assertThatThrownBy(() -> imageUploadService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void deleteImage_usesLastPathSegmentAndConfiguredBucket() throws Exception {
        when(minioProperties.getBucket()).thenReturn("ember-media-prod");

        imageUploadService.deleteImage("https://cdn.example.test/uuid-abc.jpg");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().object()).isEqualTo("uuid-abc.jpg");
        assertThat(captor.getValue().bucket()).isEqualTo("ember-media-prod");
    }

    @Test
    void deleteImage_ignoresNullOrBlank() throws Exception {
        imageUploadService.deleteImage(null);
        imageUploadService.deleteImage("  ");
        // no interaction with minioClient
    }
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `cd backend && ./mvnw test -Dtest=ImageUploadServiceTest`
Expected: FAIL — `uploadImage(MultipartFile)` does not resolve (old signature takes two args), `getPublicUrl()` undefined.

- [ ] **Step 3: Add `publicUrl` to `MinioProperties`**

```java
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {

    private String url;
    private String accessKey;
    private String secretKey;
    private String bucket;
    /** Public base a browser fetches images from (CDN / custom domain), no bucket segment. */
    private String publicUrl;
}
```

- [ ] **Step 4: Rewrite `ImageUploadService.uploadImage` / `deleteImage`**

`uploadImage` — drop the `bucketName` parameter, use `minioProperties.getBucket()` for `putObject`, add the cache header, return the public URL:

```java
    public String uploadImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !allowed_types.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
        if (file.getSize() > max_file_size) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size: of 5MB " + max_file_size);
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .size(880, 800)
                    .outputQuality(0.75)
                    .imageType(BufferedImage.TYPE_INT_RGB)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);

            byte[] imageBytes = outputStream.toByteArray();
            String objectName = UUID.randomUUID() + ".jpg";

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                            .contentType("image/jpeg")
                            .headers(Map.of("Cache-Control", "public, max-age=31536000, immutable"))
                            .build());

            return minioProperties.getPublicUrl() + "/" + objectName;

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
        }
    }
```

Add `import java.util.Map;` and remove the now-unused `extension(...)` helper only if it is unused elsewhere (grep first; leave it if referenced).

`deleteImage` — object name from the last path segment, bucket from config, null-safe:

```java
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        String path = URI.create(imageUrl).getPath();
        String objectName = path.substring(path.lastIndexOf('/') + 1);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete image: " + e.getMessage(), e);
        }
    }
```

- [ ] **Step 5: Fix the four call sites**

`CategoryService` — inject `MinioProperties` is NOT needed (bucket is now internal). Replace both `imageUploadService.uploadImage(request.getImage(), "ember-media")` with `imageUploadService.uploadImage(request.getImage())`.

`MenuItemService`:
- line ~45: `imageUploadService.uploadImage(image, minioProperties.getBucket())` → `imageUploadService.uploadImage(image)`
- line ~77: `imageUploadService.uploadImage(request.getImageUrl(), "ember-media")` → `imageUploadService.uploadImage(request.getImageUrl())`
- If `minioProperties` is now unused in `MenuItemService` (grep the file), remove the `private final MinioProperties minioProperties;` field and its import. If still used, leave it.

- [ ] **Step 6: Add the config key**

`application.yml`, minio block:

```yaml
minio:
  url: ${MINIO_URL:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket: ${MINIO_BUCKET:ember-media}
  # Public base browsers fetch images from. Dev/local MinIO serves at <url>/<bucket>;
  # prod points at the R2 custom domain (no bucket segment). See application-prod.properties.
  public-url: ${MINIO_PUBLIC_URL:http://localhost:9000/ember-media}
```

`backend/src/main/resources/.env.example` — add under the MinIO section:
```
# Public base for image URLs handed to browsers (CDN / custom domain). Prod: https://cdn.ember.vanter.net
MINIO_PUBLIC_URL=http://localhost:9000/ember-media
```

- [ ] **Step 7: Run the focused tests**

Run: `cd backend && ./mvnw test -Dtest=ImageUploadServiceTest`
Expected: PASS (6 tests).

- [ ] **Step 8: Run the full suite**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS, **899** tests, 0 failures. (No test asserts the old two-arg `uploadImage` or the old S3-endpoint URL shape outside `ImageUploadServiceTest`; if any slice/controller test does, update it to the new signature — grep `uploadImage(` across `src/test`.)

- [ ] **Step 9: Commit** (+ report + PROGRESS.md per Global Constraints)

```bash
git add backend/src/main/java/com/vanter/ember/config/MinioProperties.java \
        backend/src/main/java/com/vanter/ember/catalog/service/ImageUploadService.java \
        backend/src/main/java/com/vanter/ember/catalog/service/CategoryService.java \
        backend/src/main/java/com/vanter/ember/catalog/service/MenuItemService.java \
        backend/src/main/resources/application.yml \
        backend/src/main/resources/.env.example \
        backend/src/test/java/com/vanter/ember/catalog/service/ImageUploadServiceTest.java \
        reports/NN-hpd-01-image-public-url.md PROGRESS.md
git commit -m "feat(backend): resolve image URLs from a configurable public base"
```

---

## Task 2: Public ping endpoint for the external uptime check

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/config/PublicPingController.java`
- Test: `backend/src/test/java/com/vanter/ember/config/PublicPingControllerTest.java`

**Interfaces:**
- Produces: `GET /v1/public/ping` → `200`, body `pong`, `Content-Type: text/plain`. No auth (matches the existing `/public/**` permitAll rule in `SecurityConfig`).

**Context:** Phase 1 Task 3 moves all actuator endpoints (including `/actuator/health`) onto a loopback-only management port, so they are not reachable through the Tunnel. The Cloud Monitoring uptime check (spec §7) needs a public liveness URL. `/public/**` is already `permitAll`.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PublicPingController.class)
@Import(PublicPingController.class)
class PublicPingControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void ping_returnsPongWithoutAuth() throws Exception {
        mvc.perform(get("/public/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }
}
```

> Note: `@WebMvcTest` here will pull the security filter chain. If the project's `@WebMvcTest`s normally need `@AutoConfigureMockMvc(addFilters = false)` or a test security import (grep an existing `*ControllerTest` in `config`/`restaurant`), mirror that pattern so the test exercises the controller, not the whole security stack.

- [ ] **Step 2: Run it, expect failure**

Run: `cd backend && ./mvnw test -Dtest=PublicPingControllerTest`
Expected: FAIL — `PublicPingController` does not exist.

- [ ] **Step 3: Create the controller**

```java
package com.vanter.ember.config;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated liveness probe for the external (Cloud Monitoring) uptime check. */
@RestController
@RequestMapping("/public")
public class PublicPingController {

    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    public String ping() {
        return "pong";
    }
}
```

- [ ] **Step 4: Run the test, expect pass**

Run: `cd backend && ./mvnw test -Dtest=PublicPingControllerTest`
Expected: PASS.

- [ ] **Step 5: Full suite**

Run: `cd backend && ./mvnw test`
Expected: 900 tests, 0 failures.

- [ ] **Step 6: Commit** (+ report + PROGRESS.md)

```bash
git add backend/src/main/java/com/vanter/ember/config/PublicPingController.java \
        backend/src/test/java/com/vanter/ember/config/PublicPingControllerTest.java \
        reports/NN-hpd-02-public-ping.md PROGRESS.md
git commit -m "feat(backend): add unauthenticated /public/ping liveness endpoint"
```

---

## Task 3: Production profile — management port, forwarded headers, health details

**Files:**
- Modify: `backend/src/main/resources/application-prod.properties`
- Test: `backend/src/test/java/com/vanter/ember/config/ProdManagementPortConfigTest.java` (create)

**Interfaces:**
- Produces: under the `prod` profile, actuator binds to port `8081` (loopback publish only, done in Phase 2); `/actuator/health` and `/actuator/prometheus` are served on `8081`, NOT on `8080`. Main API stays on `8080` (context path `/v1/`).

**Context:** `SecurityConfig` currently `permitAll`s `/actuator/**` and `application.yml` sets `show-details: always`. Routing `api.ember.vanter.net` → `:8080` wholesale would publish `/v1/actuator/prometheus`. Moving the management endpoints to their own port keeps them off the Tunnel entirely; the Ops Agent scrapes `localhost:8081` on the VM (Phase 3). This is why Task 2 exists.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.web.server.LocalManagementPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "SPRING_DATASOURCE_PASSWORD=test", "JWT_SECRET=test-secret-value-at-least-32-bytes-long!!",
                "PLATFORM_JWT_SECRET=test-secret-value-at-least-32-bytes-long!!",
                "MINIO_ACCESS_KEY=x", "MINIO_SECRET_KEY=x", "HUB_LICENSE_PRIVATE_KEY="
        })
@ActiveProfiles("prod")
class ProdManagementPortConfigTest {

    @LocalServerPort int serverPort;
    @LocalManagementPort int managementPort;

    @Test
    void managementRunsOnASeparatePort() {
        assertThat(managementPort).isNotEqualTo(serverPort);
    }
}
```

> Note: `@ActiveProfiles("prod")` needs a reachable database. If the suite has an existing convention for prod-profile `@SpringBootTest` (e.g. Testcontainers or an H2 override — grep `@ActiveProfiles("prod")` in `src/test`), follow it. If prod-profile boot tests are not feasible in this environment, replace this with a plain assertion test that reads `application-prod.properties` and asserts `management.server.port` is set to `8081` and `server.forward-headers-strategy=framework` — a config-file contract test. Pick whichever the codebase already does elsewhere.

- [ ] **Step 2: Run it, expect failure**

Run: `cd backend && ./mvnw test -Dtest=ProdManagementPortConfigTest`
Expected: FAIL — management port equals server port (no separate port configured).

- [ ] **Step 3: Add the prod settings**

Append to `backend/src/main/resources/application-prod.properties`:

```properties
# Behind the Cloudflare Tunnel: trust X-Forwarded-* for scheme/host reconstruction.
server.forward-headers-strategy=framework

# Actuator on its own port. The prod compose publishes this on 127.0.0.1 only, so it is
# reachable by the on-VM Ops Agent (metrics scrape) but never via the Tunnel or the LAN.
management.server.port=8081
management.endpoint.health.show-details=never
```

- [ ] **Step 4: Run the test, expect pass**

Run: `cd backend && ./mvnw test -Dtest=ProdManagementPortConfigTest`
Expected: PASS.

- [ ] **Step 5: Full suite** (prod profile is not the default, so nothing else changes)

Run: `cd backend && ./mvnw test`
Expected: 901 tests, 0 failures.

- [ ] **Step 6: Commit** (+ report + PROGRESS.md)

```bash
git add backend/src/main/resources/application-prod.properties \
        backend/src/test/java/com/vanter/ember/config/ProdManagementPortConfigTest.java \
        reports/NN-hpd-03-prod-management-port.md PROGRESS.md
git commit -m "feat(backend): isolate actuator on a loopback management port for prod"
```

---

## Task 4: Rate limiter — trust `CF-Connecting-IP` behind a configured proxy

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/config/AuthRateLimiterFilter.java`
- Test: `backend/src/test/java/com/vanter/ember/config/AuthRateLimiterFilterTest.java`

**Interfaces:**
- Produces: when `request.getRemoteAddr()` is in `ember.ratelimit.trusted-proxies`, the client IP used for bucket keys is `CF-Connecting-IP` (if present and an IP literal), else the existing rightmost-non-proxy `X-Forwarded-For` hop. Untrusted peer → unchanged (uses `getRemoteAddr()`).

**Context:** Read `AuthRateLimiterFilter.resolveClientIp(...)`. With Cloudflare Tunnel the immediate peer is the `cloudflared` container; `X-Forwarded-For` from that path is not a reliable client-IP chain, but Cloudflare guarantees `CF-Connecting-IP` is the true client and strips any client-supplied value at the edge. This is a security-sensitive filter (see `SecurityAuditTest`); keep the change minimal and inside the existing `trustedPeer` gate.

- [ ] **Step 1: Add failing tests**

Read `AuthRateLimiterFilterTest` first to reuse its request/fixture helpers (it constructs `MockHttpServletRequest`, sets `setRemoteAddr`, and configures `RateLimitProperties` with `trustedProxies`). Add:

```java
    @Test
    void trustedPeer_prefersCfConnectingIpForBucketKey() {
        // properties with trustedProxies = ["10.0.0.0/8"], maxRequests small
        // two requests: remoteAddr 10.0.0.5, header "CF-Connecting-IP: 203.0.113.9"
        // -> both counted under the same 203.0.113.9 bucket; a third from a DIFFERENT
        //    CF-Connecting-IP (203.0.113.10) is NOT rejected (separate bucket).
    }

    @Test
    void untrustedPeer_ignoresCfConnectingIp() {
        // remoteAddr 198.51.100.7 (NOT in trustedProxies), header "CF-Connecting-IP: 203.0.113.9"
        // -> bucket keyed by 198.51.100.7, not the header.
    }
```

Fill these in against the real fixture style in the file — same shape as the existing `X-Forwarded-For` trust tests already present there.

- [ ] **Step 2: Run them, expect failure**

Run: `cd backend && ./mvnw test -Dtest=AuthRateLimiterFilterTest`
Expected: FAIL — `CF-Connecting-IP` is ignored today; buckets key by the `X-Forwarded-For` result / peer.

- [ ] **Step 3: Implement**

Add the constant near `FORWARDED_FOR`:

```java
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";
```

In `resolveClientIp`, immediately after the `if (!trustedPeer) { return peer; }` guard:

```java
        String cfHeader = request.getHeader(CF_CONNECTING_IP);
        if (cfHeader != null && !cfHeader.isBlank()) {
            String cfIp = normalizeIp(cfHeader);
            if (isIpLiteral(cfIp)) {
                return cfIp;
            }
        }
```

(The existing `X-Forwarded-For` walk stays as the fallback below it.)

- [ ] **Step 4: Run the tests, expect pass**

Run: `cd backend && ./mvnw test -Dtest=AuthRateLimiterFilterTest`
Expected: PASS (existing + 2 new).

- [ ] **Step 5: Full suite + security audit test**

Run: `cd backend && ./mvnw test`
Expected: 903 tests, 0 failures. Confirm `SecurityAuditTest` is green.

- [ ] **Step 6: Commit** (+ report + PROGRESS.md)

```bash
git add backend/src/main/java/com/vanter/ember/config/AuthRateLimiterFilter.java \
        backend/src/test/java/com/vanter/ember/config/AuthRateLimiterFilterTest.java \
        reports/NN-hpd-04-cf-connecting-ip.md PROGRESS.md
git commit -m "feat(backend): trust CF-Connecting-IP for rate-limit keys behind a proxy"
```

- [ ] **Step 7: Security review**

Invoke the `security-review` skill on this diff (client-IP trust is a spoofing surface). Address findings before moving on.

---

# PHASE 2 — Deployment artifacts

Repo files that the infra phases consume. No live cloud calls here.

## Task 5: Production Docker Compose file

**Files:**
- Create: `deploy/docker-compose.prod.yml`
- Create: `deploy/.env.prod.example`
- Test: manual `docker compose config` validation (no unit test).

**Interfaces:**
- Produces: a Compose project with services `postgres`, `app`, `cloudflared`, `backup`. Consumed by `deploy/deploy.sh` (Task 7) and Phase 3/5.

- [ ] **Step 1: Write `deploy/docker-compose.prod.yml`**

```yaml
name: ember-prod

services:
  postgres:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: ember
      POSTGRES_USER: ember
      POSTGRES_PASSWORD: ${SPRING_DATASOURCE_PASSWORD:?set in /opt/ember/.env}
    volumes:
      - ember_pgdata:/var/lib/postgresql/data
    command:
      - "postgres"
      - "-c"
      - "shared_buffers=512MB"
      - "-c"
      - "effective_cache_size=1536MB"
      - "-c"
      - "max_connections=50"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ember -d ember"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: ghcr.io/vanter-tech/ember-backend:${EMBER_IMAGE_TAG:-latest}
    restart: unless-stopped
    env_file:
      - /opt/ember/.env
    environment:
      SPRING_PROFILES_ACTIVE: prod
      JAVA_TOOL_OPTIONS: "-Xms512m -Xmx1536m"
    ports:
      - "127.0.0.1:8081:8081"   # actuator/management — loopback only, for the Ops Agent
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/v1/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s

  cloudflared:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      TUNNEL_TOKEN: ${TUNNEL_TOKEN:?set in /opt/ember/.env}
    depends_on:
      - app

  backup:
    build: ./backup
    restart: unless-stopped
    environment:
      PGHOST: postgres
      PGUSER: ember
      PGPASSWORD: ${SPRING_DATASOURCE_PASSWORD:?set in /opt/ember/.env}
      PGDATABASE: ember
      GCS_BUCKET: ${BACKUP_GCS_BUCKET:?set in /opt/ember/.env}
      DAILY_RETENTION: "14"
      WEEKLY_RETENTION: "8"
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  ember_pgdata:
```

Note: `app` does **not** publish `8080` — only `cloudflared` reaches it, over the default compose network by service name (`http://app:8080` is set as the Tunnel's public-hostname target in the Cloudflare dashboard, Task 17).

- [ ] **Step 2: Write `deploy/.env.prod.example`**

```
# Copied to /opt/ember/.env on the VM (mode 600) by deploy.sh, populated from GCP Secret Manager.
# NEVER commit real values.

# --- Datastore ---
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/ember
SPRING_DATASOURCE_USERNAME=ember
SPRING_DATASOURCE_PASSWORD=

# --- Auth secrets (rotate: these were once in a tracked .env) ---
JWT_SECRET=
PLATFORM_JWT_SECRET=
HUB_LICENSE_PRIVATE_KEY=

# --- Object storage (Cloudflare R2) ---
MINIO_URL=https://<accountid>.r2.cloudflarestorage.com
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=
MINIO_BUCKET=ember-media-prod
MINIO_PUBLIC_URL=https://cdn.ember.vanter.net

# --- CORS ---
EMBER_CORS_ALLOWED_ORIGINS=https://app.ember.vanter.net

# --- Rate limiter: trust the docker bridge network cloudflared sits on ---
EMBER_RATELIMIT_TRUSTED_PROXIES=172.16.0.0/12

# --- Cloudflare Tunnel ---
TUNNEL_TOKEN=

# --- Backups ---
BACKUP_GCS_BUCKET=ember-backups-<project>

# --- Image tag deployed (set by deploy.sh) ---
EMBER_IMAGE_TAG=latest
```

- [ ] **Step 3: Validate the compose file syntactically**

Run: `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod.example config -q`
Expected: exits 0, no error (it only parses/interpolates; `build: ./backup` will warn that the context is missing until Task 6 — acceptable, or run this check again after Task 6).

- [ ] **Step 4: Commit**

```bash
git add deploy/docker-compose.prod.yml deploy/.env.prod.example \
        reports/NN-hpd-05-prod-compose.md PROGRESS.md
git commit -m "feat(deploy): add production docker compose stack"
```

---

## Task 6: Nightly `pg_dump` → GCS backup container

**Files:**
- Create: `deploy/backup/Dockerfile`
- Create: `deploy/backup/backup.sh`
- Test: local dry run against the dev `postgres` container.

**Interfaces:**
- Consumes: env `PGHOST/PGUSER/PGPASSWORD/PGDATABASE`, `GCS_BUCKET`, `DAILY_RETENTION`, `WEEKLY_RETENTION`.
- Produces: objects `gs://<bucket>/postgres/YYYY-MM-DD.dump.gz`; prunes per retention.

- [ ] **Step 1: Write `deploy/backup/Dockerfile`**

```dockerfile
FROM google/cloud-sdk:slim
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client cron \
    && rm -rf /var/lib/apt/lists/*
COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh \
    && echo "0 8 * * * root /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1" > /etc/cron.d/ember-backup \
    && chmod 0644 /etc/cron.d/ember-backup
CMD ["bash", "-c", "cron && tail -F /var/log/backup.log"]
```

Auth: the container inherits the VM's attached service account via the metadata server — `gcloud storage` picks it up with no key file (the SA needs `roles/storage.objectAdmin` on the backup bucket, granted in Task 12).

- [ ] **Step 2: Write `deploy/backup/backup.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

STAMP="$(date -u +%F)"                     # YYYY-MM-DD
DOW="$(date -u +%u)"                       # 1..7, 7 = Sunday
TMP="/tmp/${STAMP}.dump.gz"

echo "[$(date -u +%FT%TZ)] dumping ${PGDATABASE}"
pg_dump -Fc "${PGDATABASE}" | gzip > "${TMP}"

gcloud storage cp "${TMP}" "gs://${GCS_BUCKET}/postgres/${STAMP}.dump.gz"
if [ "${DOW}" = "7" ]; then
  gcloud storage cp "${TMP}" "gs://${GCS_BUCKET}/postgres-weekly/${STAMP}.dump.gz"
fi
rm -f "${TMP}"

prune() {   # $1 = prefix, $2 = keep count
  mapfile -t objs < <(gcloud storage ls "gs://${GCS_BUCKET}/$1/" 2>/dev/null | sort)
  local excess=$(( ${#objs[@]} - $2 ))
  for (( i=0; i<excess; i++ )); do
    echo "pruning ${objs[$i]}"
    gcloud storage rm "${objs[$i]}"
  done
}
prune "postgres" "${DAILY_RETENTION}"
prune "postgres-weekly" "${WEEKLY_RETENTION}"
echo "[$(date -u +%FT%TZ)] done"
```

- [ ] **Step 3: Dry-run against dev Postgres**

Start the dev DB (`docker compose up -d postgres`), then:
Run:
```bash
docker build -t ember-backup-test deploy/backup
docker run --rm --network ember_default \
  -e PGHOST=postgres -e PGUSER=ember -e PGPASSWORD=ember -e PGDATABASE=ember \
  -e GCS_BUCKET=dummy -e DAILY_RETENTION=14 -e WEEKLY_RETENTION=8 \
  ember-backup-test bash -c 'pg_dump -Fc "$PGDATABASE" | gzip > /tmp/t.gz && ls -l /tmp/t.gz'
```
Expected: a non-empty `/tmp/t.gz` is listed (the `gcloud storage cp` step is skipped in this dry run — it needs a real bucket + SA, exercised in Task 12).

- [ ] **Step 4: Commit**

```bash
git add deploy/backup/Dockerfile deploy/backup/backup.sh \
        reports/NN-hpd-06-backup-container.md PROGRESS.md
git commit -m "feat(deploy): add nightly pg_dump-to-GCS backup container"
```

---

## Task 7: Deploy script + RUNBOOK skeleton

**Files:**
- Create: `deploy/deploy.sh`
- Create: `deploy/RUNBOOK.md`

**Interfaces:**
- Consumes: `deploy/docker-compose.prod.yml`, an image tag, GCP Secret Manager secrets, a target VM name/zone.
- Produces: an idempotent redeploy — refresh `/opt/ember/.env`, `docker compose pull`, `up -d`, then gate on `app` health.

- [ ] **Step 1: Write `deploy/deploy.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

VM="${EMBER_VM:-ember-prod}"
ZONE="${EMBER_ZONE:-us-central1-a}"
TAG="${1:-latest}"
SECRET="${EMBER_ENV_SECRET:-ember-prod-env}"   # Secret Manager secret holding the full .env body

echo ">> pushing /opt/ember/.env from Secret Manager secret '${SECRET}'"
gcloud secrets versions access latest --secret "${SECRET}" \
  | gcloud compute ssh "${VM}" --zone "${ZONE}" --tunnel-through-iap --command \
    "sudo install -m 600 /dev/stdin /opt/ember/.env && echo 'EMBER_IMAGE_TAG=${TAG}' | sudo tee -a /opt/ember/.env >/dev/null"

echo ">> deploying tag ${TAG}"
gcloud compute ssh "${VM}" --zone "${ZONE}" --tunnel-through-iap --command "
  set -e
  cd /opt/ember
  sudo docker compose -f docker-compose.prod.yml pull
  sudo docker compose -f docker-compose.prod.yml up -d
  echo 'waiting for app health...'
  for i in \$(seq 1 20); do
    if sudo docker compose -f docker-compose.prod.yml exec -T app wget -qO- http://localhost:8080/v1/actuator/health | grep -q UP; then
      echo 'app healthy'; exit 0
    fi
    sleep 6
  done
  echo 'app did NOT become healthy'; sudo docker compose -f docker-compose.prod.yml logs --tail=80 app; exit 1
"
```

- [ ] **Step 2: Write `deploy/RUNBOOK.md`** with these sections (fill the command bodies from this plan's Phase 3–5 tasks as they are executed):

```markdown
# Ember Hosted Production — Runbook

## Topology
(diagram — copy from the spec §3)

## First-time provisioning
### GCP (maintainer)   — Phase 3
### Cloudflare (colleague) — Phase 4

## Routine deploy
`./deploy/deploy.sh <image-tag>` from a machine with `gcloud` auth + IAP access.

## Recovery
### App container unhealthy
### Postgres data corruption  — restore latest dump (see "Restore test")
### VM lost                    — recreate from snapshot + `docker compose up -d`

## Secret rotation
## Restore test (run quarterly)
```

- [ ] **Step 3: Shellcheck both scripts**

Run: `shellcheck deploy/deploy.sh deploy/backup/backup.sh` (or `docker run --rm -v "$PWD:/mnt" koalaman/shellcheck deploy/deploy.sh deploy/backup/backup.sh`)
Expected: no errors (warnings acceptable if justified).

- [ ] **Step 4: Commit**

```bash
git add deploy/deploy.sh deploy/RUNBOOK.md reports/NN-hpd-07-deploy-script.md PROGRESS.md
git commit -m "feat(deploy): add deploy script and runbook skeleton"
```

---

## Task 8: CI — build & push the backend image to GHCR

**Files:**
- Create: `.github/workflows/backend-image.yml`

**Interfaces:**
- Produces: on a `v*` tag push, `ghcr.io/vanter-tech/ember-backend:<tag>` and `:latest`.

- [ ] **Step 1: Write the workflow**

```yaml
name: backend-image

on:
  push:
    tags: ["v*"]
  workflow_dispatch:

permissions:
  contents: read
  packages: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: maven
      - name: Package (skip tests — the lint/test workflow gates that)
        working-directory: backend
        run: ./mvnw -B -DskipTests package
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Derive tag
        id: t
        run: echo "tag=${GITHUB_REF_NAME#v}" >> "$GITHUB_OUTPUT"
      - uses: docker/build-push-action@v6
        with:
          context: backend
          push: true
          tags: |
            ghcr.io/vanter-tech/ember-backend:${{ steps.t.outputs.tag }}
            ghcr.io/vanter-tech/ember-backend:latest
```

> Confirm `backend/Dockerfile` builds from a pre-built jar (it does today — it copies `target/*.jar`). If it does its own `mvnw package` inside the image, drop the "Package" step above and let the Docker build do it; keep whichever avoids building twice.

- [ ] **Step 2: Validate YAML**

Run: `docker run --rm -v "$PWD:/w" -w /w rhysd/actionlint:latest -color` (or `actionlint .github/workflows/backend-image.yml`)
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/backend-image.yml reports/NN-hpd-08-ci-backend-image.md PROGRESS.md
git commit -m "ci: build and push backend image to ghcr on tag"
```

---

## Task 9: Frontend — generate `env-config.js` during the Pages build

**Files:**
- Create: `frontend/scripts/gen-env-config.mjs`
- Modify: `frontend/package.json` (add a `build:pages` script)
- Test: local run.

**Interfaces:**
- Consumes: env `EMBW_API_URL`, `EMBW_WS_URL`.
- Produces: `frontend/dist/env-config.js` with `window.ENV = { EMBW_API_URL, EMBW_WS_URL }` — the same contract `frontend/entrypoint.sh` writes for Docker/dev (unchanged, still used by the Hub).

- [ ] **Step 1: Write `frontend/scripts/gen-env-config.mjs`**

```js
import { writeFileSync } from 'node:fs';

const api = process.env.EMBW_API_URL ?? 'http://localhost:8080/api/v1';
const ws = process.env.EMBW_WS_URL ?? 'http://localhost:8080/v1/ws';

const body = `window.ENV = {\n  EMBW_API_URL: ${JSON.stringify(api)},\n  EMBW_WS_URL: ${JSON.stringify(ws)}\n};\n`;
writeFileSync('dist/env-config.js', body);
console.log('wrote dist/env-config.js', { api, ws });
```

- [ ] **Step 2: Add the script to `frontend/package.json`**

In `"scripts"`, add:
```json
    "build:pages": "tsc -b && vite build && node scripts/gen-env-config.mjs",
```

- [ ] **Step 3: Run it**

Run: `cd frontend && EMBW_API_URL=https://api.ember.vanter.net/v1 EMBW_WS_URL=wss://api.ember.vanter.net/v1/ws pnpm run build:pages`
Expected: build succeeds; `frontend/dist/env-config.js` contains the two `wss://`/`https://` values.

- [ ] **Step 4: Guard rails**

Run: `cd frontend && pnpm run build && pnpm run test && pnpm run lint`
Expected: unchanged — build OK, 41 tests pass, lint exit 0. (The new script is not imported by app code.)

- [ ] **Step 5: Commit**

```bash
git add frontend/scripts/gen-env-config.mjs frontend/package.json \
        reports/NN-hpd-09-pages-env-config.md PROGRESS.md
git commit -m "feat(frontend): generate env-config.js for the cloudflare pages build"
```

---

## Task 10: Landing — real contact submit + production `site:`

**Files:**
- Modify: `landing/src/components/ContactForm.tsx`
- Create: `landing/functions/api/contact.ts`
- Modify: `landing/astro.config.mjs`
- Test: `cd landing && pnpm build`.

**Interfaces:**
- Produces: `POST /api/contact` (same-origin Pages Function) → `204` on success, `400` on invalid body. Forwards `{name,email,message}` to `CONTACT_WEBHOOK_URL` (a Pages project env var the colleague sets — Slack/Discord/email-API webhook).

- [ ] **Step 1: Replace the fake submit in `ContactForm.tsx`**

In `handleSubmit`, swap the `await new Promise(...timeout...)` block for a real call:

```tsx
    setErrors({});
    setSubmitting(true);
    try {
      const res = await fetch('/api/contact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name.trim(), email: email.trim(), message: message.trim() }),
      });
      if (!res.ok) throw new Error(String(res.status));
      window.location.href = '/thank-you';
    } catch {
      setSubmitting(false);
      setErrors({ form: 'No pudimos enviar tu mensaje. Intentá de nuevo.' });
    }
```

- [ ] **Step 2: Write `landing/functions/api/contact.ts`**

```ts
interface Env {
  CONTACT_WEBHOOK_URL?: string;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const onRequestPost: PagesFunction<Env> = async ({ request, env }) => {
  let body: { name?: string; email?: string; message?: string };
  try {
    body = await request.json();
  } catch {
    return new Response('bad json', { status: 400 });
  }
  const name = (body.name ?? '').trim();
  const email = (body.email ?? '').trim();
  const message = (body.message ?? '').trim();
  if (!name || !message || !EMAIL_RE.test(email)) {
    return new Response('invalid', { status: 400 });
  }
  if (env.CONTACT_WEBHOOK_URL) {
    await fetch(env.CONTACT_WEBHOOK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: `Ember contacto\nNombre: ${name}\nCorreo: ${email}\n\n${message}` }),
    });
  }
  return new Response(null, { status: 204 });
};
```

- [ ] **Step 3: Set the production `site:` in `landing/astro.config.mjs`**

`site: 'https://ember.vanter.com'` → `site: 'https://ember.vanter.net'`.

- [ ] **Step 4: Build the landing**

Run: `cd landing && pnpm install && pnpm build`
Expected: build succeeds; `landing/dist/` produced. (Pages Functions under `landing/functions/` are deployed by Cloudflare, not bundled by Astro — the build ignores them, that is expected.)

- [ ] **Step 5: Commit**

```bash
git add landing/src/components/ContactForm.tsx landing/functions/api/contact.ts \
        landing/astro.config.mjs reports/NN-hpd-10-landing-contact.md PROGRESS.md
git commit -m "feat(landing): submit contact form to a pages function; set prod site url"
```

---

# PHASE 3 — GCP provisioning (maintainer runbook)

Runs on a real GCP project with billing + `gcloud` auth. Each task appends its exact commands to `deploy/RUNBOOK.md` and commits that.

## Task 11: Project baseline + VM + firewall

- [ ] **Step 1: Set project vars & enable APIs**

```bash
export PROJECT=<your-project-id>
export REGION=us-central1
export ZONE=us-central1-a
gcloud config set project "$PROJECT"
gcloud services enable compute.googleapis.com secretmanager.googleapis.com \
  monitoring.googleapis.com logging.googleapis.com iap.googleapis.com
```

- [ ] **Step 2: Firewall — IAP SSH only, no HTTP/S**

```bash
gcloud compute firewall-rules create allow-iap-ssh \
  --direction=INGRESS --action=ALLOW --rules=tcp:22 \
  --source-ranges=35.235.240.0/20 --network=default
# Ensure NO rule opens 80/443/8080 to 0.0.0.0/0:
gcloud compute firewall-rules list --format="table(name,sourceRanges.list(),allowed[].map().firewall_rule().list())"
```
Expected: only `allow-iap-ssh` (plus default egress); nothing exposing 80/443/8080.

- [ ] **Step 3: Create the VM**

```bash
gcloud compute instances create ember-prod \
  --zone="$ZONE" --machine-type=e2-medium \
  --image-family=ubuntu-2404-lts-amd64 --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB --boot-disk-type=pd-standard \
  --no-address \
  --shielded-secure-boot --shielded-vtpm \
  --metadata=enable-oslogin=TRUE
```
(`--no-address` = no public IP.)

- [ ] **Step 4: Base setup on the VM**

```bash
gcloud compute ssh ember-prod --zone "$ZONE" --tunnel-through-iap --command '
  set -e
  sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2
  sudo systemctl enable --now docker
  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab
  sudo mkdir -p /opt/ember && sudo chmod 755 /opt/ember
  sudo apt-get install -y unattended-upgrades && sudo dpkg-reconfigure -f noninteractive unattended-upgrades
'
```

- [ ] **Step 5: Copy the compose file to the VM**

```bash
gcloud compute scp deploy/docker-compose.prod.yml deploy/backup/Dockerfile deploy/backup/backup.sh \
  ember-prod:/tmp/ --zone "$ZONE" --tunnel-through-iap
gcloud compute ssh ember-prod --zone "$ZONE" --tunnel-through-iap --command '
  sudo mkdir -p /opt/ember/backup
  sudo mv /tmp/docker-compose.prod.yml /opt/ember/
  sudo mv /tmp/Dockerfile /tmp/backup.sh /opt/ember/backup/
'
```

- [ ] **Step 6: Verify & record**

Run: `gcloud compute instances describe ember-prod --zone "$ZONE" --format="value(networkInterfaces[0].accessConfigs)"`
Expected: empty (no external IP).
Append all commands above to `deploy/RUNBOOK.md` § "First-time provisioning → GCP".

- [ ] **Step 7: Commit**

```bash
git add deploy/RUNBOOK.md reports/NN-hpd-11-gcp-vm.md PROGRESS.md
git commit -m "docs(deploy): record GCP VM + firewall provisioning"
```

---

## Task 12: GCS backup bucket, service-account role, disk snapshots

- [ ] **Step 1: Backup bucket (private, versioned, lifecycle backstop)**

```bash
gcloud storage buckets create "gs://ember-backups-${PROJECT}" \
  --location="$REGION" --uniform-bucket-level-access --public-access-prevention
gcloud storage buckets update "gs://ember-backups-${PROJECT}" --versioning
```

- [ ] **Step 2: Grant the VM's service account write access to just that bucket**

```bash
SA=$(gcloud compute instances describe ember-prod --zone "$ZONE" \
     --format="value(serviceAccounts[0].email)")
gcloud storage buckets add-iam-policy-binding "gs://ember-backups-${PROJECT}" \
  --member="serviceAccount:${SA}" --role=roles/storage.objectAdmin
```
Also ensure the instance has the `devstorage.read_write` (or cloud-platform) scope; if it was created without it:
`gcloud compute instances set-service-account ember-prod --zone "$ZONE" --scopes=cloud-platform` (requires a stop/start).

- [ ] **Step 3: Daily disk snapshot policy**

```bash
gcloud compute resource-policies create snapshot-schedule ember-daily-snap \
  --region="$REGION" --max-retention-days=7 \
  --daily-schedule --start-time=07:00 --on-source-disk-delete=keep-auto-snapshots
gcloud compute disks add-resource-policies ember-prod \
  --zone="$ZONE" --resource-policies=ember-daily-snap
```

- [ ] **Step 4: Verify**

Run: `gcloud storage buckets describe "gs://ember-backups-${PROJECT}" --format="value(iam_configuration.publicAccessPrevention)"`
Expected: `enforced`.
Run: `gcloud compute disks describe ember-prod --zone "$ZONE" --format="value(resourcePolicies)"`
Expected: lists `ember-daily-snap`.

- [ ] **Step 5: Commit** (append commands to RUNBOOK)

```bash
git add deploy/RUNBOOK.md reports/NN-hpd-12-gcs-backups.md PROGRESS.md
git commit -m "docs(deploy): record GCS backup bucket + snapshot policy"
```

---

## Task 13: Secrets + first stack bring-up (local health only)

**Depends on:** the Cloudflare track having produced `TUNNEL_TOKEN` + R2 credentials (Phase 4 Tasks 16–17). If those are not ready, do Steps 1–2 with placeholder R2/tunnel values, bring up only `postgres` + `app`, verify health, and revisit `cloudflared` in Task 20.

- [ ] **Step 1: Assemble the `.env` body and store it as one secret**

Fill a local `prod.env` from `deploy/.env.prod.example` with real values (rotated `JWT_SECRET`, `PLATFORM_JWT_SECRET`, a fresh strong `SPRING_DATASOURCE_PASSWORD`, R2 keys, `TUNNEL_TOKEN`, `HUB_LICENSE_PRIVATE_KEY`, `BACKUP_GCS_BUCKET=ember-backups-<project>`). Then:

```bash
gcloud secrets create ember-prod-env --replication-policy=automatic --data-file=prod.env
shred -u prod.env   # do not keep it on disk
```

> Rotating `HUB_LICENSE_PRIVATE_KEY` invalidates every previously issued Hub `license.key`. No paying Hub is deployed — generate a fresh keypair now and update `ember-hub/keys/hub-public-key.der` in the Hub build (tracked as Hub follow-up, not this plan).

- [ ] **Step 2: Deploy**

```bash
./deploy/deploy.sh latest
```
Expected: script prints `app healthy` and exits 0. If `cloudflared` has a placeholder token it will crash-loop — acceptable at this step; `app` + `postgres` health is what gates.

- [ ] **Step 3: Confirm Flyway ran on the empty DB**

```bash
gcloud compute ssh ember-prod --zone "$ZONE" --tunnel-through-iap --command \
  "sudo docker compose -f /opt/ember/docker-compose.prod.yml exec -T postgres psql -U ember -d ember -c 'select version, description, success from flyway_schema_history order by installed_rank;'"
```
Expected: rows `V1`…`V5`, all `success = t`.

- [ ] **Step 4: Confirm the backup job works end to end**

```bash
gcloud compute ssh ember-prod --zone "$ZONE" --tunnel-through-iap --command \
  "sudo docker compose -f /opt/ember/docker-compose.prod.yml exec -T backup /usr/local/bin/backup.sh"
gcloud storage ls "gs://ember-backups-${PROJECT}/postgres/"
```
Expected: one `YYYY-MM-DD.dump.gz` object listed.

- [ ] **Step 5: Commit** (RUNBOOK § secret rotation + bring-up)

```bash
git add deploy/RUNBOOK.md reports/NN-hpd-13-secrets-bringup.md PROGRESS.md
git commit -m "docs(deploy): record secret store and first stack bring-up"
```

---

## Task 14: Ops Agent + uptime check + alerts

- [ ] **Step 1: Install the Ops Agent + Prometheus scrape**

```bash
gcloud compute ssh ember-prod --zone "$ZONE" --tunnel-through-iap --command '
  curl -sSO https://dl.google.com/cloudagents/add-google-cloud-ops-agent.sh
  sudo bash add-google-cloud-ops-agent.sh --also-install
'
```
Then append to `/etc/google-cloud-ops-agent/config.yaml` on the VM a `metrics` receiver of type `prometheus` scraping `http://localhost:8081/actuator/prometheus`, and restart: `sudo systemctl restart google-cloud-ops-agent`. (Exact YAML block goes in the RUNBOOK.)

- [ ] **Step 2: Uptime check on the public ping**

Console → Monitoring → Uptime checks → Create:
- Protocol HTTPS, host `api.ember.vanter.net`, path `/v1/public/ping`, check every 5 min, regions: multiple.
(Or `gcloud monitoring uptime create ember-api-ping --resource-type=uptime-url --resource-labels=host=api.ember.vanter.net --path=/v1/public/ping --period=5`.)

- [ ] **Step 3: Alert policies**

Create policies notifying an email + SMS channel for: uptime check failing; `instance/cpu/utilization` > 0.9 for 10 min; `agent/disk/percent_used` > 85; `agent/memory/percent_used` > 90.

- [ ] **Step 4: Verify**

Run: `gcloud monitoring dashboards list` is not needed — instead confirm metrics arrive:
Console → Monitoring → Metrics Explorer → `prometheus.googleapis.com/jvm_memory_used_bytes` shows data within ~5 min.
Deliberately `docker compose stop app` on the VM for ~6 min → the uptime alert fires an email → `start` again.

- [ ] **Step 5: Commit**

```bash
git add deploy/RUNBOOK.md reports/NN-hpd-14-monitoring.md PROGRESS.md
git commit -m "docs(deploy): record ops agent, uptime check and alert policies"
```

---

# PHASE 4 — Cloudflare track (colleague runbook)

The colleague runs these on the team's Cloudflare account. Outputs (tokens, credentials, hostnames) hand back to the GCP track. Each task appends to `deploy/RUNBOOK.md` § "Cloudflare".

## Task 15: Delegate `ember.vanter.net` + DNS records

- [ ] **Step 1:** In the team's Cloudflare account, add `ember.vanter.net` as a zone (type: partial/subdomain is not needed — add it as a full zone; Cloudflare gives two nameservers).
- [ ] **Step 2:** The `vanter.net` owner adds `ember  NS  <ns1/ns2 from step 1>` in the `vanter.net` DNS.
- [ ] **Step 3:** Verify delegation: `dig +short NS ember.vanter.net` returns the Cloudflare nameservers.
- [ ] **Step 4:** Confirm Universal SSL is Active for the zone (covers `ember.vanter.net` + `*.ember.vanter.net`). No Advanced Certificate Manager needed.
- [ ] **Step 5:** Record nameservers + delegation date in the RUNBOOK. Commit.

## Task 16: R2 media bucket + `cdn.` domain + credentials

- [ ] **Step 1:** R2 → create bucket `ember-media-prod`.
- [ ] **Step 2:** Bucket → Settings → Public access → connect custom domain `cdn.ember.vanter.net`. Enable public access.
- [ ] **Step 3:** R2 → Manage API Tokens → create a token scoped to `ember-media-prod`, permission *Object Read & Write*. Note the **Access Key ID**, **Secret Access Key**, and the **S3 endpoint** `https://<accountid>.r2.cloudflarestorage.com`.
- [ ] **Step 4:** Verify: `curl -I https://cdn.ember.vanter.net/` returns a Cloudflare response (403/404 is fine — it means the domain routes to the bucket).
- [ ] **Step 5:** Hand `MINIO_URL` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` to the GCP track (they go into `ember-prod-env`, Task 13). Record in RUNBOOK (values redacted). Commit.

## Task 17: Cloudflare Tunnel → `api.ember.vanter.net`

- [ ] **Step 1:** Zero Trust → Networks → Tunnels → Create tunnel (name `ember-prod`). Copy the **tunnel token**.
- [ ] **Step 2:** Add a Public Hostname: `api.ember.vanter.net` → service `HTTP` `app:8080`. (The `cloudflared` container resolves `app` on the compose network.)
- [ ] **Step 3:** Enable WebSockets for the tunnel (Zero Trust → Settings → Network → WebSockets = On; it is on by default).
- [ ] **Step 4:** Hand `TUNNEL_TOKEN` to the GCP track (into `ember-prod-env`, Task 13).
- [ ] **Step 5:** After the GCP track brings up `cloudflared` (Task 20), verify: `curl -s https://api.ember.vanter.net/v1/public/ping` → `pong`. Record in RUNBOOK. Commit.

## Task 18: Two Pages projects

- [ ] **Step 1 — `ember-app`:** Pages → Create → Connect to Git → repo. Settings: **Root directory** `frontend`, build command `pnpm run build:pages`, output `dist`. Env vars: `EMBW_API_URL=https://api.ember.vanter.net/v1`, `EMBW_WS_URL=wss://api.ember.vanter.net/v1/ws`, `NODE_VERSION=20`. Custom domain `app.ember.vanter.net`.
- [ ] **Step 2 — `ember-landing`:** Pages → Create → same repo. **Root directory** `landing`, build command `pnpm build`, output `dist`, env `NODE_VERSION=22`, `CONTACT_WEBHOOK_URL=<team webhook>`. Custom domain `ember.vanter.net` (+ `www` redirect optional).
- [ ] **Step 3:** Enable path-scoped builds so a `landing/**`-only change does not rebuild `ember-app` and vice versa (Pages → Settings → Builds → "Path" / build watch paths).
- [ ] **Step 4:** Verify: `https://app.ember.vanter.net/` serves the SPA; `https://app.ember.vanter.net/env-config.js` contains the `wss://api.ember.vanter.net` values; `https://ember.vanter.net/` serves the landing.
- [ ] **Step 5:** Record project names + settings in RUNBOOK. Commit.

## Task 19: Edge hardening

- [ ] **Step 1:** SSL/TLS → Edge Certificates → enable **HSTS** (max-age 6 months, includeSubDomains once confident), **Always Use HTTPS**, **Automatic HTTPS Rewrites**.
- [ ] **Step 2:** Security → WAF → enable the Cloudflare Managed Ruleset for the zone.
- [ ] **Step 3:** WAF → Rate limiting rules: add a rule on `(http.request.uri.path matches "^/v1/auth/")` → 20 req / 1 min / IP → block for 1 min.
- [ ] **Step 4:** WAF → custom rule: `(http.host eq "api.ember.vanter.net" and starts_with(http.request.uri.path, "/v1/actuator"))` → **Block** (defense in depth; actuator is already on the loopback-only port and not routed, this rule makes a misconfig fail closed).
- [ ] **Step 5:** Verify: `curl -s -o /dev/null -w "%{http_code}" https://api.ember.vanter.net/v1/actuator/health` → `403`. `curl -s https://api.ember.vanter.net/v1/public/ping` → `pong`. Record + commit.

---

# PHASE 5 — Integration & end-to-end verification

## Task 20: Wire the real Tunnel + R2, full infra smoke

**Depends on:** Phase 3 complete, Phase 4 Tasks 15–18 complete.

- [ ] **Step 1:** Update `ember-prod-env` with the real `TUNNEL_TOKEN`, `MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` (edit the secret: `gcloud secrets versions add ember-prod-env --data-file=prod.env` then `shred -u prod.env`).
- [ ] **Step 2:** `./deploy/deploy.sh latest` → expect `app healthy`, and `cloudflared` no longer crash-looping (`docker compose logs cloudflared` shows a registered connection).
- [ ] **Step 3 — smoke checklist** (run from a machine off the VM's network; record each result in RUNBOOK):
  - `nmap -Pn -p 22,80,443,8080,8081 <vm-has-no-public-ip>` — N/A (no public IP); instead confirm `gcloud compute instances describe` shows no `accessConfigs`.
  - `curl -s https://api.ember.vanter.net/v1/public/ping` → `pong`.
  - `curl -s -o /dev/null -w "%{http_code}" -X OPTIONS https://api.ember.vanter.net/v1/auth/login -H "Origin: https://app.ember.vanter.net" -H "Access-Control-Request-Method: POST"` → `200`/`204` with `access-control-allow-origin: https://app.ember.vanter.net`.
  - `curl -s -o /dev/null -w "%{http_code}" https://api.ember.vanter.net/v1/actuator/health` → `403` (WAF) or `404` — never `200`.
  - WebSocket: from the browser console on `https://app.ember.vanter.net`, confirm the STOMP client connects to `wss://api.ember.vanter.net/v1/ws` (no error in Network tab).
  - Image round-trip: log in as an admin, create a category with an image, then `curl -sI` the returned `imgUrl` → `200`, host `cdn.ember.vanter.net`, header `cache-control: public, max-age=31536000, immutable`.
- [ ] **Step 4:** Commit RUNBOOK updates + report.

## Task 21: Restore test

- [ ] **Step 1:** On the VM, pull the latest dump and restore into a throwaway DB:

```bash
gcloud compute ssh ember-prod --zone "$ZONE" --tunnel-through-iap --command '
  set -e
  LATEST=$(gcloud storage ls gs://ember-backups-'"$PROJECT"'/postgres/ | sort | tail -1)
  gcloud storage cp "$LATEST" /tmp/latest.dump.gz
  gunzip -f /tmp/latest.dump.gz
  sudo docker run --rm --name pg-restore-test --network ember-prod_default -e POSTGRES_PASSWORD=x -d postgres:16
  sleep 8
  sudo docker cp /tmp/latest.dump pg-restore-test:/tmp/d.dump
  sudo docker exec pg-restore-test bash -c "createdb -U postgres emberrestore && pg_restore -U postgres -d emberrestore /tmp/d.dump"
  sudo docker exec pg-restore-test psql -U postgres -d emberrestore -c "select count(*) from flyway_schema_history;"
  sudo docker rm -f pg-restore-test
'
```
Expected: `pg_restore` completes without fatal errors; the `flyway_schema_history` count is ≥ 5.

- [ ] **Step 2:** Document the full restore procedure (into the *real* DB, for a genuine disaster) in `deploy/RUNBOOK.md` § "Recovery → Postgres data corruption".
- [ ] **Step 3:** Commit RUNBOOK + report.

## Task 22: End-to-end product walkthrough + PROGRESS.md close-out

- [ ] **Step 1:** Against `https://app.ember.vanter.net`, on at least two devices:
  - Provision a restaurant through the onboarding wizard as a new admin.
  - Create a category + a menu item with a photo (confirm it renders from `cdn.`).
  - Log in as WAITER on a second device; open a table session.
  - As a customer (phone), join with the 5-char code, add an item, confirm order.
  - As KITCHEN, see the order arrive in real time; move it `PENDING → PREPARING → READY`.
  - As WAITER, produce and pay a bill (cash).
  - Confirm the cash-shift sentinel behaves (no false stale alert).
- [ ] **Step 2:** Record the walkthrough result + any defects in a report.
- [ ] **Step 3:** Update `PROGRESS.md`: mark the hosted-production sub-project complete; note the live URLs; note remaining follow-ups (Hub `hub-public-key.der` regen after the `HUB_LICENSE_PRIVATE_KEY` rotation; `release`-branch model if adopted; migration-to-managed trigger).
- [ ] **Step 4:** Final commit.

```bash
git add deploy/RUNBOOK.md reports/NN-hpd-22-e2e-walkthrough.md PROGRESS.md
git commit -m "docs(deploy): hosted production live — e2e walkthrough recorded"
```

---

## Self-Review

**Spec coverage:**

| Spec section | Task(s) |
|---|---|
| §3 topology, §4.1 VM | 11 |
| §4.2 prod compose (postgres/app/cloudflared/backup) | 5, 13, 20 |
| §5.1 forward-headers, CORS, trusted-proxies | 3, 5 (env), 20 (CORS verify) |
| §5.2 management port isolation | 2, 3 |
| §5.3 media URL indirection + cache header + bucket fix | 1 |
| §5.4 CF-Connecting-IP | 4 |
| §5.5 secrets + rotation | 13 |
| §6 R2 media bucket + config | 16, 13 (config), 20 (verify) |
| §7 Ops Agent, uptime check, alerts | 14 |
| §8 pg_dump→GCS + snapshots + restore test + RUNBOOK | 6, 12, 21, 7 |
| §9 two Pages projects, env-config, contact form, site: | 9, 10, 18 |
| §10 DNS delegation + TLS | 15 |
| §11 deploy pipeline (GHCR + deploy.sh) | 8, 7 |
| §14 testing strategy | per-task tests + 20, 21, 22 |
| §16 open items | contact form → 10; mgmt-port/health → 2+3; GHCR → 8; release branch → 22 close-out note |

No spec section is unimplemented. §12 (cost) and §13 (migration path) are informational — no task needed; §13 is captured as a RUNBOOK note in Task 22.

**Placeholder scan:** `NN` in commit/report paths is deliberate (assigned at commit time per CLAUDE.md §4); every code step carries real code. Phase 3–5 steps carry real `gcloud`/`curl` commands with expected output. Task 4 Step 1 and Task 3 Step 1 explicitly tell the implementer to adapt test code to the existing fixture style rather than inventing — acceptable because the surrounding assertions are concrete.

**Type consistency:** `uploadImage(MultipartFile)` (1-arg) is used consistently in Tasks 1 and its call-site edits. `getPublicUrl()` matches the `publicUrl` field. `MINIO_PUBLIC_URL` env / `minio.public-url` property / `getPublicUrl()` accessor are consistent. `management.server.port=8081` matches the `127.0.0.1:8081:8081` publish and the Ops Agent scrape target across Tasks 3, 5, 14. `/v1/public/ping` is consistent across Tasks 2, 14, 19, 20.
