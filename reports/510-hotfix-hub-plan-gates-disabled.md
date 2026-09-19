# Report 510 — Hotfix: Ember Hub onboarding wizard rejected by plan gates

## 1. Identification
- **Report number:** 510
- **Task ID:** ad-hoc live bug (Ember Hub, reported on a friend's PC with a fresh license)
- **Predecessor task:** report 508 — hotfix 0.2.6.1 prep (branch `fix/hub-plan-gates` is cut from `fix/guest-join-customer-identity`, so it ships with 0.2.6.1). Report 509 lives on `feat/hub-backup-restore` (chronological numbering).

## 2. Objective
After logging in on a freshly activated Hub, saving the business name in the admin onboarding wizard failed with "No se pudo guardar. Verifica tu conexión…". Make the Hub — including already-installed Hubs — save it normally.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/restaurant/service/PlanGateService.java`
- `backend/src/main/resources/application-hub.yml`
- `backend/src/test/java/com/vanter/ember/restaurant/service/PlanGateServiceTest.java`
- `PROGRESS.md`
- `reports/510-hotfix-hub-plan-gates-disabled.md`

## 4. What Changed?
- `PlanGateService` gets a field-injected flag `@Value("${ember.plans.enforced:true}") boolean enforced`; when false, `requirePlanAtLeast` and `requireTableCapacity` return immediately (no repository access). Default `true`, so the cloud behaviour is unchanged.
- `application-hub.yml` sets `ember.plans.enforced: false`.
- 3 new tests: both gates are no-ops when not enforced, and a guard test that reads `application-hub.yml` and asserts the flag is `false` (so the Hub config can't silently regress).

## 5. Why It Changed?
Root cause (from code; the live 402 was not observed on the affected PC): `HubProvisioningRunner` seeds the Hub's restaurant through `RestaurantRepository.insertWithId`, which hard-codes `plan = 'FREE'`. Since PLAN-GATING-PHASE1, `SettingService.updateSettings` requires STARTER+ to change branding and caps FREE at 1 table. The onboarding wizard saves `branding.businessName` (then the table count), so it got a 402 `PLAN_LIMIT_EXCEEDED`, and `AdminOnboardingWizard` shows one generic connection-error message for any failure. The Hub is a paid on-premise license and must not have SaaS tier limits. Turning enforcement off by profile also fixes Hubs that are already installed (their stored plan stays `FREE`, which no longer matters), unlike seeding a higher plan at provisioning time.

Known follow-up (not done): the wizard's generic error text hides real causes (402, 4xx); it should surface the backend detail.

Verification: `./mvnw test` → **1317/1317** (1314 + 3 new), BUILD SUCCESS.
