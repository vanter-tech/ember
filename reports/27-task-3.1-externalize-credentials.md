# Report 27 — task-3.1: Externalize sensitive default credentials

## 1. Identification
- **Report number:** 27
- **Task ID:** task-3.1 — Externalize sensitive default credentials (DB, JWT secret, MinIO) from `application.yml` to `.env` variables.
- **Predecessor task:** task-2.18 (report 26)

## 2. Objective
Remove every hardcoded credential from the committed backend configuration so that no secret is
recoverable from the git history going forward, and make the app read them from the gitignored
root `.env` — failing fast at boot rather than silently starting on a shipped default.

## 3. Modified Files
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.properties`
- `.env.example`
- `PROGRESS.md`
- `reports/27-task-3.1-externalize-credentials.md` (new)

## 4. What Changed?

### `application.yml`
- Added a `spring.config.import` list so the root `.env` is loaded as a property source:
  `optional:file:./.env[.properties]`, `optional:file:../.env[.properties]`, then the same two for
  `.env.local`. Two paths because `./mvnw spring-boot:run` runs with cwd `backend/` (`../.env`) while
  a jar launched from the repo root sees `./.env`. All four are `optional:`, so the Docker image
  (which receives real environment variables through `env_file`) boots with no `.env` present.
- Replaced hardcoded values with placeholders:
  - `spring.datasource.url` → `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/ember}`
  - `spring.datasource.username` → `${SPRING_DATASOURCE_USERNAME:ember}`
  - `spring.datasource.password` → `${SPRING_DATASOURCE_PASSWORD}` (**no default**)
  - `spring.data.mongodb.uri` → `${SPRING_DATA_MONGODB_URI}` (**no default** — the URI embeds credentials)
  - `spring.jpa.hibernate.ddl-auto` → `${DDL_AUTO:update}`
  - `jwt.secret` → `${JWT_SECRET}` (**no default**), `jwt.expiration-ms` → `${JWT_EXPIRATION_MS:86400000}`
  - `minio.url` → `${MINIO_URL:http://localhost:9000}`, `minio.access-key` → `${MINIO_ACCESS_KEY}`
    (**no default**), `minio.secret-key` → `${MINIO_SECRET_KEY}` (**no default**),
    `minio.bucket` → `${MINIO_BUCKET:ember-media}`

### `application-dev.properties`
Given the same treatment. It was outside the literal wording of the task, but it duplicated the
**identical** JWT signing secret and MinIO credentials — externalizing only `application.yml` would
have left the same secret in the repo one file over.

### `.env.example`
Key list was already complete and correct (11 keys, matching the local `.env`); no keys added. Added
a header documenting that the file is auto-imported by the backend and which five variables have no
fallback.

## 5. Why It Changed?
- **The committed JWT secret was the sharp edge.** `jwt.secret` in `application.yml` is a real 64-char
  HS256 key; anyone with repo read access could mint valid tokens for any user of any tenant,
  defeating the whole tenant-isolation chain built in task-2.9 through task-2.18.
- **No-fallback for the five true secrets is deliberate.** A `${VAR:default}` fallback on a password
  means a misconfigured deployment boots successfully against a default credential and looks healthy.
  Absent placeholders fail at context startup with the variable name in the message, which is loud,
  immediate, and safe. Non-secret coordinates (hostnames, bucket name, token TTL) keep defaults so a
  fresh checkout with `.env` copied from `.env.example` runs unmodified.
- **`.env.local` exists to resolve a real conflict.** The same `.env` feeds both docker-compose (where
  the DB hosts are the service names `postgres`/`mongodb`) and a host-side `./mvnw spring-boot:run`
  (where they must be `localhost`). Before this change `application.yml` hardcoded `localhost`, so a
  host run worked by accident; now that `.env` genuinely drives the config, a developer running on the
  host puts the `localhost` URLs in `.env.local`, which is already gitignored and, being imported last,
  takes precedence. This ordering was verified empirically, not assumed (see below).
- **`application-prod.properties` needed no change** — it was already fully externalized; this brings
  the default and `dev` profiles up to the same standard.

## 6. Verification
- `cd backend && ./mvnw test` → **367/367 passing, BUILD SUCCESS** (unchanged from the task-2.18
  baseline). Confirms `src/test/resources/application.properties` still outranks the imported `.env`,
  so tests keep their H2/fixed-secret configuration.
- Boot smoke test (`./mvnw spring-boot:run`, killed after startup): **no
  `Could not resolve placeholder` error** — the `.env` import resolves. The first run failed only on
  `UnknownHostException: postgres`, proving the value came from `.env` (docker hostname) rather than
  the yml default (`localhost`). A second run with a temporary `.env.local` overriding
  `SPRING_DATASOURCE_URL` connected to the overridden host, confirming last-import-wins precedence.
  The temporary `.env.local` was deleted afterwards.

### Side effect to be aware of
The second smoke-test boot reached a **live local PostgreSQL 16.13** instance and Flyway ran against
the `ember` database: it created `flyway_schema_history`, baselined it at V1, and applied
`V2 — tenant backfill and constraints` (success, 45 ms). That is exactly the migration task-2.15
authored for this database, so the resulting state is the intended one — but it was applied now, by
this verification run, rather than at the next manual app start.

## 7. Follow-up
Rotating the exposed JWT secret and the Postgres/Mongo/MinIO passwords is **not** covered by this
task: they remain readable in the git history of `application.yml` / `application-dev.properties`, so
removing them from HEAD does not un-expose them. New values must be generated and placed in `.env`
(and in whatever holds production configuration) for this change to be worth anything. Rotating
`JWT_SECRET` invalidates all issued tokens, forcing a re-login for every active session.
