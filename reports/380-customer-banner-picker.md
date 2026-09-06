# Report 380 — Customer banner preset picker (backend + frontend)

## 1. Identification
- **Report number:** 380
- **Current Task ID:** let a customer choose their home banner from a fixed set of
  presets (no uploads), persisted on the account
- **Predecessor Task:** report 379 (customer home banner redesign)

## 2. Objective
Add a modal on the customer home to pick one of six built-in banner presets
(CSS-gradient only), store the choice against the user, and render it on the banner.

## 3. Modified Files
**Backend (module `identity`)**
- `db/migration/V7__user_banner_key.sql` — `ALTER TABLE users ADD COLUMN banner_key VARCHAR(20)`
- `identity/model/BannerKey.java` — enum `EMBER, SUNSET, FOREST, OCEAN, MIDNIGHT, MONO`; `@JsonValue` lowercase, `@JsonCreator` case-insensitive
- `identity/model/User.java` — `@Enumerated(STRING) BannerKey bannerKey` (nullable)
- `identity/dto/UserProfileResponse.java`, `identity/dto/UpdateProfileRequest.java` (`@NotNull BannerKey`)
- `identity/service/UserProfileService.java` — `getByEmail`, `updateBanner`
- `identity/controller/UserProfileController.java` — `GET /users/me`, `PATCH /users/me` (any authenticated caller)
- `identity/controller/UserProfileControllerTest.java` — 6 cases (401, default-null, lowercase serialisation, patch echo, unknown value → 400, missing → 400)

**Frontend**
- `lib/bannerPresets.ts` — `BANNER_KEYS`, `BannerKey`, `DEFAULT_BANNER`, `BANNER_PRESETS` (labelKey + Tailwind gradient), `resolveBannerKey`
- `lib/api.ts` — `UserProfileResponse` + `userProfileService.me` / `.updateBanner`
- `pages/customer/components/BannerPickerModal.tsx` — dialog with a 2/3-col swatch grid; clicking a swatch PATCHes and updates the `['me']` query cache, then closes
- `pages/customer/Home.tsx` — `useQuery(['me'])`, banner gradient from `BANNER_PRESETS[resolveBannerKey(profile?.bannerKey)]`, an `ImageIcon` button top-right of the banner opens the modal
- `locales/es|en/customer.ts` — `bannerPickerTitle/Description/Aria`, `bannerEmber/Sunset/Forest/Ocean/Midnight/Mono`

## 4. What Changed?
`users` gains a nullable `banner_key` column. `GET /users/me` returns
`{ name, email, bannerKey }` (bannerKey lowercase or null); `PATCH /users/me`
`{ bannerKey }` validates against the enum (unknown → 400) and saves. The customer home
fetches `/users/me`, renders the matching gradient (default `ember` when null), and the
new icon button opens `BannerPickerModal`, which writes the choice and refreshes the
cached profile so the banner updates immediately.

## 5. Why It Changed?
Report 379 left the banner background as the seam for this feature. Presets are a closed
set so customers can personalise without upload/moderation/storage risk; the key is a
short enum, so validation is free and the visual stays entirely client-side. Persisting
on the account (not `localStorage`) means the choice follows the customer across devices.

## 6. Verification
- `cd backend && ./mvnw test` — 1050 tests pass (was 1044; +6 new).
- `cd frontend && pnpm run build` — clean; `pnpm run lint` — 0 errors (16 pre-existing
  warnings); `pnpm run test:run` — 78 pass.
