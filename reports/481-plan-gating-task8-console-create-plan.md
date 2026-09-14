# Report 481

## 1. Identification
- **Report Number:** 481
- **Task ID:** PLAN-GATING-PHASE1 Task 8 — Console: assign a plan when creating a tenant
- **Predecessor Task:** report 480 (staff-roles gate, 6th and last gate)

## 2. Objective
Let a platform operator pick a plan when creating a tenant from the Console, per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 8). Every tenant created before this defaulted to FREE with no way to choose otherwise.

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantCreateRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- Modify: `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- Modify: `frontend/src/lib/platformApi.ts`
- Modify: `frontend/src/pages/console/ConsoleRestaurantCreate.tsx`

## 4. What Changed?
`PlatformRestaurantCreateRequest` gains an optional `plan` field (no `@NotNull` — omitted/null is a valid request). `PlatformRestaurantService.create` now builds the `Restaurant` with `.plan(request.getPlan() != null ? request.getPlan() : RestaurantPlan.FREE)` — the ternary is required, not stylistic: `Restaurant.plan` has `@Builder.Default` to `FREE`, but Lombok's default only applies when the builder method is never called at all, so passing `null` straight through would persist `null` into a `NOT NULL` column. `ConsoleRestaurantCreate.tsx` gets a new `plan` `<Select>` field (FREE/STARTER/PRO/ENTERPRISE) between Slug and Nombre del administrador, defaulting to FREE.

**Unplanned fix, found by the build (not the plan):** the zod schema's `plan: z.enum([...]).default('FREE')` made zod's inferred type `plan?: ... | undefined` on the schema's *input* side while the form's value type expects `plan: ...` (required) — a known `zodResolver` + `.default()` typing mismatch that `tsc -b` caught as 8 compile errors across the whole form (`resolver`, `handleSubmit`, every `control={form.control}`). Fixed by dropping `.default('FREE')` from the zod schema entirely; the `useForm`'s own `defaultValues.plan: 'FREE'` already supplies the initial value at runtime, so nothing is lost.

## 5. Why It Changed?
Eighth task of PLAN-GATING-PHASE1 — the first of the two Console tasks. Task 9 (changing a tenant's plan after creation) is next; only after that does Task 10 remove the tenant self-service endpoint.

## 6. Verification
- TDD: `create_defaultsToFreeWhenPlanOmitted` and `create_usesRequestedPlanWhenProvided` written first, confirmed RED (compile error — `setPlan` didn't exist), GREEN after adding the field.
- `./mvnw test` (full backend suite) — **1296/1296** (+2 new).
- `pnpm run build` — clean (after the zod-default fix above). `pnpm run lint` — 0 errors. `pnpm vitest run` — **142/142** (no test covers `ConsoleRestaurantCreate.tsx` today, so nothing needed updating there).
- No live browser check — worth confirming next time the Console is exercised live that the plan selector actually persists.
