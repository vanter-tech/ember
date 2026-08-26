# Report 223 — task-HUB-01-01-oshi-and-hub-properties

## 1. Identification
- **Report Number:** 223
- **Task ID:** HUB-01-01
- **Predecessor Task:** report 222 (feat-global-search-panel)

## 2. Objective
First task of the Ember Hub HUB-01 plan (`docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`): add the OSHI dependency and a Spring-free `HubProperties` record that reads `EMBER_HUB_*` env vars, usable before `SpringApplication.run` exists.

## 3. Modified Files
- `backend/pom.xml`
- `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java` (new)
- `PROGRESS.md`

## 4. What Changed?
Added `com.github.oshi:oshi-core:6.6.5` to `backend/pom.xml`. New `HubProperties` record (`dataDir`, `postgresBinDir`, `licenseFile`, `publicKeyFile`, `stateFile`, `postgresPort`) with a `fromEnvironment()` static factory reading `EMBER_HUB_DATA_DIR`/`EMBER_HUB_POSTGRES_BIN_DIR`/`EMBER_HUB_LICENSE_FILE`/`EMBER_HUB_PUBLIC_KEY_FILE`/`EMBER_HUB_STATE_FILE`/`EMBER_HUB_POSTGRES_PORT`, each defaulting to a relative path/`5432` when unset. This is the first file in a new `com.vanter.ember.hub` package tree, taken verbatim from the plan.

## 5. Why It Changed?
Kicks off HUB-01 (portable-Postgres bootstrap + licensing for the offline Ember Hub SKU), per `docs/superpowers/specs/ember_hub.md` §2.2/2.3. `HubProperties` deliberately isn't a Spring `@ConfigurationProperties` bean because `EmberApplication.main` (HUB-01-07) needs these values before Spring's own `DataSource` autoconfiguration tries to connect — too late to start portable Postgres from inside the normal Spring lifecycle. `cd backend && ./mvnw test` PASS, 789/789 (up from 788).
