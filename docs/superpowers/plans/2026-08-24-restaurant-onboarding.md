# Restaurant Onboarding (Admin Wizard + Waiter Tour) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A freshly created restaurant blocks its ADMIN user behind a 2-field setup wizard (business name + table count) until the app has enough data to actually work, and shows its WAITER user a one-time passive tour of the tables screen once real tables exist.

**Architecture:** Both pieces derive their state from data the app already fetches — no new backend endpoints, no new database columns. The admin wizard is a gate inside `AdminLayout`: it calls the existing `GET /settings`, and if `branding.businessName` is blank or `space.totalTables` is 0, renders a 4-screen wizard instead of `<Outlet/>`; each screen saves through the existing `PUT /settings` (the same call `BrandingSettings.tsx`/`SpaceSettings.tsx` already make, including its `syncDiningTables` side effect). The waiter tour is a `react-joyride` overlay mounted inside `Tables.tsx`, gated on a per-user "seen" flag in `localStorage` (via a Zustand `persist` store, same pattern as the existing `localeStore`).

**Tech Stack:** React 19, TypeScript, TanStack Query 5 (existing `['restaurantSettings']` query), Zustand 5 + `persist` (existing pattern), `react-joyride` (new dependency), Vitest + Testing Library (existing test setup).

**Spec:** `docs/superpowers/specs/2026-08-24-restaurant-onboarding-design.md` — this plan implements all of it. One adjustment made while mapping real files (documented here, not a silent deviation): §2.3's waiter tour originally implied visiting `TableInformation.tsx` and opening `ChargeTableModal` — a brand-new restaurant has no occupied tables to navigate to, so those screens are unreachable. Confirmed with the user: all 4 tour steps stay on `Tables.tsx` instead, using its own static UI (grid, detail panel, primary action button, assign button) — the tour drives `Tables.tsx`'s own `selectedTable` state to reveal the detail panel for steps 3–4, rather than navigating anywhere.

## Global Constraints

- **Zero backend changes.** Every task in this plan touches only `frontend/`.
- **Gating is derived, never stored.** No new field, no new table. `needsOnboarding = !branding.businessName?.trim() || !space.totalTables`, computed fresh from the existing `['restaurantSettings']` query every time.
- **The wizard only ever asks for `businessName` and `totalTables`.** Every other branding field (logo, colors, hours, RUC, wifi) stays 100% optional, editable later from Configuración exactly as today — the wizard must not grow extra fields.
- **The waiter tour never blocks anything.** It's dismissible (`skip`), shown once per user (`ember-waiter-tour-seen-{userId}` — actually a Zustand-persisted map, not a raw key per user; see Task 6), and only appears once real tables exist.
- Run `cd frontend && pnpm run build` (which runs `tsc -b && vite build`) and `cd frontend && pnpm run test:run` after every task — do not move to the next task on a red build or red suite.

---

### Task 1: Add the `react-joyride` dependency

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Install the dependency**

Run: `cd frontend && pnpm add react-joyride`

This resolves and pins the actual latest version in `package.json`/`pnpm-lock.yaml` — don't hand-edit a version number.

- [ ] **Step 2: Confirm the build still passes**

Run: `cd frontend && pnpm run build`
Expected: PASS — adding a dependency that nothing imports yet doesn't change any existing behavior.

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/pnpm-lock.yaml
git commit -m "chore(frontend): add react-joyride for the waiter tour"
```

---

### Task 2: `useOnboardingGate` hook

**Files:**
- Create: `frontend/src/hooks/useOnboardingGate.ts`
- Test: `frontend/src/hooks/useOnboardingGate.test.tsx`

**Interfaces:**
- Produces: `useOnboardingGate(): { needsOnboarding: boolean; isLoading: boolean; isError: boolean }`. Consumed by `AdminLayout.tsx` (Task 5).

- [ ] **Step 1: Write the failing test**

```tsx
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { vi, describe, test, expect } from 'vitest'
import { useOnboardingGate } from '@/hooks/useOnboardingGate'
import { SettingsService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  SettingsService: { getSettings: vi.fn() },
}))

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

describe('useOnboardingGate', () => {
  test('needsOnboarding is true when businessName is blank', async () => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: { businessName: '' },
      space: { totalTables: 5 },
    } as never)

    const { result } = renderHook(() => useOnboardingGate(), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.needsOnboarding).toBe(true)
  })

  test('needsOnboarding is true when totalTables is zero', async () => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: { businessName: 'Ember Grill' },
      space: { totalTables: 0 },
    } as never)

    const { result } = renderHook(() => useOnboardingGate(), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.needsOnboarding).toBe(true)
  })

  test('needsOnboarding is false once both are set', async () => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: { businessName: 'Ember Grill' },
      space: { totalTables: 5 },
    } as never)

    const { result } = renderHook(() => useOnboardingGate(), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.needsOnboarding).toBe(false)
  })

  test('needsOnboarding is false when the settings fetch fails (never force the wizard on a network error)', async () => {
    vi.mocked(SettingsService.getSettings).mockRejectedValue(new Error('network'))

    const { result } = renderHook(() => useOnboardingGate(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.needsOnboarding).toBe(false)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/hooks/useOnboardingGate.test.tsx`
Expected: FAIL — `useOnboardingGate` does not exist yet.

- [ ] **Step 3: Write the implementation**

```ts
import { useQuery } from '@tanstack/react-query'
import { SettingsService } from '@/lib/api'

/**
 * Derives the onboarding gate from the same `restaurantSettings` query every other settings
 * screen already uses — no separate fetch, no stored "onboarding complete" flag (see plan's
 * Global Constraints). A restaurant that already had tables/branding before this feature shipped
 * satisfies the condition immediately, so it never sees the wizard.
 *
 * On a failed fetch, needsOnboarding stays false (spec §4: never force the wizard because of a
 * transient network error) — the caller uses `isError` to fall back to its own normal error UI.
 */
export const useOnboardingGate = () => {
  const { data: settings, isPending, isError } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  })

  const needsOnboarding =
    !isPending &&
    !isError &&
    (!settings?.branding?.businessName?.trim() || !settings?.space?.totalTables)

  return { needsOnboarding, isLoading: isPending, isError }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/hooks/useOnboardingGate.test.tsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useOnboardingGate.ts frontend/src/hooks/useOnboardingGate.test.tsx
git commit -m "feat(frontend): add useOnboardingGate hook"
```

---

### Task 3: i18n keys for the admin wizard

**Files:**
- Modify: `frontend/src/locales/es/admin.ts`
- Modify: `frontend/src/locales/en/admin.ts`

**Interfaces:**
- Produces: translation keys consumed by `AdminOnboardingWizard.tsx` (Task 4). Reuses the already-existing `businessNameLabel` and `totalTablesLabel` keys — does not redefine them.

- [ ] **Step 1: Add the Spanish keys**

In `frontend/src/locales/es/admin.ts`, inside the `export const admin = { ... }` object, add:

```ts
  onboardingWelcomeTitle: 'Bienvenido a Ember',
  onboardingWelcomeDescription:
    'Configuremos tu restaurante en 2 pasos rápidos antes de empezar.',
  onboardingContinueButton: 'Continuar',
  onboardingBusinessNameTitle: 'Nombre de tu negocio',
  onboardingBusinessNameDescription:
    'Este es el nombre que verán tus clientes. Puedes ajustarlo después desde Configuración.',
  onboardingTablesTitle: 'Número de mesas',
  onboardingTablesDescription:
    'Cuántas mesas tiene tu restaurante — puedes cambiar este número después desde Configuración.',
  onboardingSaveErrorMessage: 'No se pudo guardar. Verifica tu conexión e intenta de nuevo.',
  onboardingDoneTitle: '¡Listo!',
  onboardingDoneDescription:
    'Tu restaurante ya está funcionando. Puedes completar el resto de la configuración (logo, colores, horarios) cuando quieras.',
  onboardingFinishButton: 'Ir al panel',
```

- [ ] **Step 2: Add the English keys**

In `frontend/src/locales/en/admin.ts`, inside the `export const admin = { ... } satisfies typeof esAdmin` object, add the matching keys in the same order (TypeScript's `satisfies` will fail the build if any key is missing or extra):

```ts
  onboardingWelcomeTitle: 'Welcome to Ember',
  onboardingWelcomeDescription:
    "Let's set up your restaurant in 2 quick steps before you get started.",
  onboardingContinueButton: 'Continue',
  onboardingBusinessNameTitle: 'Your business name',
  onboardingBusinessNameDescription:
    'This is the name your customers will see. You can change it later from Settings.',
  onboardingTablesTitle: 'Number of tables',
  onboardingTablesDescription:
    'How many tables your restaurant has — you can change this later from Settings.',
  onboardingSaveErrorMessage: "Couldn't save. Check your connection and try again.",
  onboardingDoneTitle: "You're all set!",
  onboardingDoneDescription:
    'Your restaurant is up and running. Fill in the rest (logo, colors, hours) whenever you like.',
  onboardingFinishButton: 'Go to dashboard',
```

- [ ] **Step 3: Run the build to confirm the `satisfies` check passes**

Run: `cd frontend && pnpm run build`
Expected: PASS — `tsc -b` fails loudly if `en/admin.ts`'s keys don't exactly match `es/admin.ts`'s.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/locales/es/admin.ts frontend/src/locales/en/admin.ts
git commit -m "feat(frontend): add i18n keys for the admin onboarding wizard"
```

---

### Task 4: `AdminOnboardingWizard` component

**Files:**
- Create: `frontend/src/components/onboarding/AdminOnboardingWizard.tsx`
- Test: `frontend/src/components/onboarding/AdminOnboardingWizard.test.tsx`

**Interfaces:**
- Consumes: `useTranslation('admin')` (existing), `SettingsService.getSettings`/`updateSettings` (existing, `frontend/src/lib/api.ts`), `Button`/`Input`/`Label` (existing `@/components/ui/*`).
- Produces: `<AdminOnboardingWizard />`, a self-contained component with no props. Consumed by `AdminLayout.tsx` (Task 5).

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { vi, describe, test, expect, beforeEach } from 'vitest'
import { AdminOnboardingWizard } from '@/components/onboarding/AdminOnboardingWizard'
import { SettingsService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  SettingsService: { getSettings: vi.fn(), updateSettings: vi.fn() },
}))

function renderWizard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminOnboardingWizard />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('AdminOnboardingWizard', () => {
  beforeEach(() => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: {},
      space: { totalTables: 0 },
    } as never)
    vi.mocked(SettingsService.updateSettings).mockResolvedValue(undefined)
  })

  test('walks through business name and table count, saving each via PUT /settings', async () => {
    const user = userEvent.setup()
    renderWizard()

    await screen.findByText('Bienvenido a Ember')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByText('Nombre de tu negocio')
    await user.type(screen.getByLabelText('Nombre Comercial'), 'Ember Grill')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await waitFor(() =>
      expect(SettingsService.updateSettings).toHaveBeenCalledWith(
        expect.objectContaining({ branding: expect.objectContaining({ businessName: 'Ember Grill' }) })
      )
    )

    await screen.findByText('Número de mesas')
    const tablesInput = screen.getByLabelText('Cantidad Total de Mesas')
    await user.clear(tablesInput)
    await user.type(tablesInput, '8')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await waitFor(() =>
      expect(SettingsService.updateSettings).toHaveBeenCalledWith(
        expect.objectContaining({ space: { totalTables: 8 } })
      )
    )

    await screen.findByText('¡Listo!')
  })

  test('shows an inline error and does not advance when saving fails', async () => {
    vi.mocked(SettingsService.updateSettings).mockRejectedValueOnce(new Error('network'))
    const user = userEvent.setup()
    renderWizard()

    await screen.findByText('Bienvenido a Ember')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await user.type(screen.getByLabelText('Nombre Comercial'), 'Ember Grill')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByText('No se pudo guardar. Verifica tu conexión e intenta de nuevo.')
    expect(screen.queryByText('Número de mesas')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/components/onboarding/AdminOnboardingWizard.test.tsx`
Expected: FAIL — `AdminOnboardingWizard` does not exist yet.

- [ ] **Step 3: Write the implementation**

```tsx
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { SettingsService } from '@/lib/api'
import type { components } from '@/lib/backend-types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useTranslation } from '@/lib/i18n'

type SettingsPayload = components['schemas']['SettingsPayload']
type WizardStep = 'welcome' | 'businessName' | 'tables' | 'done'

export const AdminOnboardingWizard = () => {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const { data: settings } = useQuery({
    queryKey: ['restaurantSettings'],
    queryFn: () => SettingsService.getSettings(),
  })

  const [step, setStep] = useState<WizardStep>('welcome')
  const [businessName, setBusinessName] = useState('')
  const [totalTables, setTotalTables] = useState(1)

  const mutation = useMutation({
    mutationFn: (payload: SettingsPayload) => SettingsService.updateSettings(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] }),
  })

  const saveBusinessName = () => {
    if (!settings) return
    mutation.mutate(
      { ...settings, branding: { ...settings.branding, businessName } },
      { onSuccess: () => setStep('tables') }
    )
  }

  const saveTables = () => {
    if (!settings) return
    mutation.mutate(
      { ...settings, space: { totalTables } },
      { onSuccess: () => setStep('done') }
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-50 p-6">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-sm border border-zinc-100 p-8">
        {step === 'welcome' && (
          <div className="space-y-6 text-center">
            <h1 className="text-2xl font-bold text-[#7a1315]">{t('onboardingWelcomeTitle')}</h1>
            <p className="text-zinc-600">{t('onboardingWelcomeDescription')}</p>
            <Button className="w-full" onClick={() => setStep('businessName')}>
              {t('onboardingContinueButton')}
            </Button>
          </div>
        )}

        {step === 'businessName' && (
          <div className="space-y-6">
            <h1 className="text-xl font-bold">{t('onboardingBusinessNameTitle')}</h1>
            <p className="text-zinc-600 text-sm">{t('onboardingBusinessNameDescription')}</p>
            <div className="space-y-2">
              <Label htmlFor="onboarding-business-name">{t('businessNameLabel')}</Label>
              <Input
                id="onboarding-business-name"
                value={businessName}
                onChange={(e) => setBusinessName(e.target.value)}
                placeholder="Ember Fine Dining"
              />
            </div>
            {mutation.isError && (
              <p className="text-sm text-red-600">{t('onboardingSaveErrorMessage')}</p>
            )}
            <Button
              className="w-full"
              disabled={!businessName.trim() || mutation.isPending}
              onClick={saveBusinessName}
            >
              {mutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                t('onboardingContinueButton')
              )}
            </Button>
          </div>
        )}

        {step === 'tables' && (
          <div className="space-y-6">
            <h1 className="text-xl font-bold">{t('onboardingTablesTitle')}</h1>
            <p className="text-zinc-600 text-sm">{t('onboardingTablesDescription')}</p>
            <div className="space-y-2">
              <Label htmlFor="onboarding-total-tables">{t('totalTablesLabel')}</Label>
              <Input
                id="onboarding-total-tables"
                type="number"
                min={1}
                max={200}
                value={totalTables}
                onChange={(e) => setTotalTables(Number(e.target.value))}
              />
            </div>
            {mutation.isError && (
              <p className="text-sm text-red-600">{t('onboardingSaveErrorMessage')}</p>
            )}
            <Button
              className="w-full"
              disabled={totalTables < 1 || mutation.isPending}
              onClick={saveTables}
            >
              {mutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                t('onboardingContinueButton')
              )}
            </Button>
          </div>
        )}

        {step === 'done' && (
          <div className="space-y-6 text-center">
            <h1 className="text-2xl font-bold text-[#7a1315]">{t('onboardingDoneTitle')}</h1>
            <p className="text-zinc-600">{t('onboardingDoneDescription')}</p>
            <Link to="/admin/settings">
              <Button className="w-full">{t('onboardingFinishButton')}</Button>
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}
```

`Label`'s `htmlFor` needs to match the `Input`'s `id` for `getByLabelText` to resolve in the test — check `frontend/src/components/ui/label.tsx`'s existing usage elsewhere in the codebase (e.g. `SpaceSettings.tsx` already does `<Label htmlFor="totalTables">`/`<Input id="totalTables">`) to confirm this pairing is how the shared `Label`/`Input` components are meant to be wired; if `BrandingSettings.tsx`'s `Label` (no `htmlFor` today) doesn't associate the same way, `getByLabelText('Nombre Comercial')` in Step 1's test may need `id="onboarding-business-name"` added consistently as above — this implementation already does that for both fields.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/components/onboarding/AdminOnboardingWizard.test.tsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/onboarding/AdminOnboardingWizard.tsx frontend/src/components/onboarding/AdminOnboardingWizard.test.tsx
git commit -m "feat(frontend): add AdminOnboardingWizard component"
```

---

### Task 5: Wire the gate into `AdminLayout`

**Files:**
- Modify: `frontend/src/layouts/AdminLayout.tsx`
- Test: `frontend/src/layouts/AdminLayout.test.tsx` (new)

**Interfaces:**
- Consumes: `useOnboardingGate` (Task 2), `AdminOnboardingWizard` (Task 4).

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi, describe, test, expect } from 'vitest'
import { AdminLayout } from '@/layouts/AdminLayout'
import { SettingsService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  SettingsService: { getSettings: vi.fn(), updateSettings: vi.fn() },
}))

function renderAdminLayout() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/admin/settings']}>
        <Routes>
          <Route path="/admin" element={<AdminLayout />}>
            <Route path="settings" element={<div>Real settings page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('AdminLayout onboarding gate', () => {
  test('shows the wizard instead of the route when onboarding is incomplete', async () => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: {},
      space: { totalTables: 0 },
    } as never)

    renderAdminLayout()

    await screen.findByText('Bienvenido a Ember')
    expect(screen.queryByText('Real settings page')).not.toBeInTheDocument()
  })

  test('shows the normal route when onboarding is already complete', async () => {
    vi.mocked(SettingsService.getSettings).mockResolvedValue({
      branding: { businessName: 'Ember Grill' },
      space: { totalTables: 5 },
    } as never)

    renderAdminLayout()

    await screen.findByText('Real settings page')
    expect(screen.queryByText('Bienvenido a Ember')).not.toBeInTheDocument()
  })

  test('does not force the wizard when the settings fetch fails', async () => {
    vi.mocked(SettingsService.getSettings).mockRejectedValue(new Error('network'))

    renderAdminLayout()

    await screen.findByText('Real settings page')
    expect(screen.queryByText('Bienvenido a Ember')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/layouts/AdminLayout.test.tsx`
Expected: FAIL — `AdminLayout` still unconditionally renders `<Outlet/>`.

- [ ] **Step 3: Update `AdminLayout.tsx`**

Replace its full contents:

```tsx
import { FloatingNav } from '@/components/FloatingNav'
import { TopNav } from '@/components/TopNav'
import { Outlet } from 'react-router-dom'
import { useOnboardingGate } from '@/hooks/useOnboardingGate'
import { AdminOnboardingWizard } from '@/components/onboarding/AdminOnboardingWizard'

export const AdminLayout = () => {
  const { needsOnboarding, isLoading } = useOnboardingGate()

  if (isLoading) {
    return null
  }

  if (needsOnboarding) {
    return <AdminOnboardingWizard />
  }

  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6">
      <TopNav />
      <main className="w-full">
        <Outlet />
      </main>
      <FloatingNav />
    </div>
  )
}
```

Returning `null` while `isLoading` (rather than showing the wizard OR the normal shell prematurely) avoids a flash of the wizard for restaurants that already have data, while the very first `GET /settings` is still in flight.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/layouts/AdminLayout.test.tsx`
Expected: PASS

- [ ] **Step 5: Run the full frontend test suite and build**

Run: `cd frontend && pnpm run test:run && pnpm run build`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/layouts/AdminLayout.tsx frontend/src/layouts/AdminLayout.test.tsx
git commit -m "feat(frontend): gate AdminLayout behind the onboarding wizard"
```

---

### Task 6: `waiterTourStore` — per-user "seen" flag

**Files:**
- Create: `frontend/src/store/waiterTourStore.ts`
- Test: `frontend/src/store/waiterTourStore.test.ts`

**Interfaces:**
- Produces: `useWaiterTourStore` — a Zustand store with `hasSeenTour(userId: string): boolean` and `markTourSeen(userId: string): void`, persisted to `localStorage` under `ember-waiter-tour-storage` (mirrors `localeStore.ts`'s `ember-locale-storage` naming and `persist` usage exactly). Consumed by `WaiterTour.tsx` (Task 8).
- Keyed **per user id** (not a single global boolean) because two different waiters can share the same browser/PC in a restaurant (spec §2.3: "bandera `ember-waiter-tour-seen-{userId}`") — implemented as one persisted `Record<string, boolean>` map rather than dynamically-named store instances, since Zustand stores are static per-app, not one-per-user.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, test, expect, beforeEach } from 'vitest'
import { useWaiterTourStore } from '@/store/waiterTourStore'

describe('waiterTourStore', () => {
  beforeEach(() => {
    useWaiterTourStore.setState({ seenByUserId: {} })
  })

  test('hasSeenTour is false for a user who has not seen it', () => {
    expect(useWaiterTourStore.getState().hasSeenTour('user-1')).toBe(false)
  })

  test('markTourSeen makes hasSeenTour true for that user only', () => {
    useWaiterTourStore.getState().markTourSeen('user-1')

    expect(useWaiterTourStore.getState().hasSeenTour('user-1')).toBe(true)
    expect(useWaiterTourStore.getState().hasSeenTour('user-2')).toBe(false)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/store/waiterTourStore.test.ts`
Expected: FAIL — `waiterTourStore` does not exist yet.

- [ ] **Step 3: Write the implementation**

```ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface WaiterTourState {
  seenByUserId: Record<string, boolean>
  hasSeenTour: (userId: string) => boolean
  markTourSeen: (userId: string) => void
}

export const useWaiterTourStore = create<WaiterTourState>()(
  persist(
    (set, get) => ({
      seenByUserId: {},
      hasSeenTour: (userId) => Boolean(get().seenByUserId[userId]),
      markTourSeen: (userId) =>
        set((state) => ({ seenByUserId: { ...state.seenByUserId, [userId]: true } })),
    }),
    {
      name: 'ember-waiter-tour-storage',
    }
  )
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/store/waiterTourStore.test.ts`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/store/waiterTourStore.ts frontend/src/store/waiterTourStore.test.ts
git commit -m "feat(frontend): add waiterTourStore for per-user tour-seen tracking"
```

---

### Task 7: i18n keys for the waiter tour

**Files:**
- Modify: `frontend/src/locales/es/waiter.ts`
- Modify: `frontend/src/locales/en/waiter.ts`

**Interfaces:**
- Produces: translation keys consumed by `WaiterTour.tsx` (Task 8).

- [ ] **Step 1: Add the Spanish keys**

In `frontend/src/locales/es/waiter.ts`, inside `export const waiter = { ... }`, add:

```ts
  tourGridTitle: 'Tus mesas',
  tourGridContent:
    'Aquí ves todas las mesas del restaurante. Rojo significa ocupada, gris significa libre.',
  tourPanelTitle: 'Detalles de la mesa',
  tourPanelContent: 'Al hacer clic en una mesa, aquí verás quién está sentado y qué han pedido.',
  tourActionTitle: 'Abrir o cobrar',
  tourActionContent:
    'Este botón abre la mesa para tomar el primer pedido, o cobra la cuenta si ya está ocupada.',
  tourAssignTitle: 'Invitar clientes',
  tourAssignContent: 'Genera un código QR para que los clientes de esta mesa se unan desde su celular.',
  tourNextButton: 'Siguiente',
  tourBackButton: 'Atrás',
  tourSkipButton: 'Saltar',
  tourLastButton: 'Entendido',
```

- [ ] **Step 2: Add the English keys**

In `frontend/src/locales/en/waiter.ts`, inside `export const waiter = { ... } satisfies typeof esWaiter`, add the matching keys:

```ts
  tourGridTitle: 'Your tables',
  tourGridContent: 'Here you see every table in the restaurant. Red means occupied, gray means free.',
  tourPanelTitle: 'Table details',
  tourPanelContent: "Click a table and you'll see who's seated there and what they've ordered.",
  tourActionTitle: 'Open or charge',
  tourActionContent:
    'This button opens the table for the first order, or charges the bill if it's already occupied.',
  tourAssignTitle: 'Invite guests',
  tourAssignContent: 'Generates a QR code so this table's guests can join from their phone.',
  tourNextButton: 'Next',
  tourBackButton: 'Back',
  tourSkipButton: 'Skip',
  tourLastButton: 'Got it',
```

- [ ] **Step 3: Run the build to confirm the `satisfies` check passes**

Run: `cd frontend && pnpm run build`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(frontend): add i18n keys for the waiter tour"
```

---

### Task 8: `WaiterTour` component and `Tables.tsx` anchors

**Files:**
- Modify: `frontend/src/pages/waiter/Tables.tsx`
- Create: `frontend/src/pages/waiter/components/WaiterTour.tsx`
- Test: `frontend/src/pages/waiter/components/WaiterTour.test.tsx`

**Interfaces:**
- Consumes: `useWaiterTourStore` (Task 6), `useAuthStore` (existing, for `userId`), `react-joyride` (Task 1).
- Produces: `<WaiterTour tableIds={string[]} onSelectFirstTable={() => void} />`. `tableIds` lets the component decide whether there's anything to tour at all (an empty array means no tables exist yet — shouldn't happen once Task 5's wizard is live, but the component must not crash if it does); `onSelectFirstTable` is called when the tour advances to the step that needs the detail panel visible, so `Tables.tsx` keeps owning its own `selectedTable` state — `WaiterTour` never touches it directly.

**On the "targeting elements that only render conditionally" problem:** `Tables.tsx`'s detail panel, its primary action button, and its assign button only exist in the DOM once `tableDetails` is truthy (i.e., a table is selected). `react-joyride` can't point at an element that isn't there. `WaiterTour` handles this via its `callback` prop: when the tour's step index advances past step 1 (the grid), it calls `onSelectFirstTable()` *before* letting `react-joyride` continue — `Tables.tsx` selects its first table in response, the panel and its buttons render, and steps 3–4 can then target them for real.

- [ ] **Step 1: Add stable anchors to `Tables.tsx`**

In `frontend/src/pages/waiter/Tables.tsx`, add `id` attributes to the four elements the tour needs to point at, and render `<WaiterTour>`. Apply these targeted edits to the existing file:

Replace:
```tsx
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 relative">
```
with:
```tsx
        <div id="waiter-tour-grid" className="grid grid-cols-2 sm:grid-cols-3 gap-4 relative">
```

Replace:
```tsx
      <div className="w-full md:w-[30%] border-t md:border-t-0 md:border-l border-zinc-200 pt-5 md:pt-0 md:pl-5">
```
with:
```tsx
      <div id="waiter-tour-panel" className="w-full md:w-[30%] border-t md:border-t-0 md:border-l border-zinc-200 pt-5 md:pt-0 md:pl-5">
```

Replace:
```tsx
                <Button className="w-full text-md">
                  {tableDetails.isOccupied ? t('chargeTableButton') : t('openTableButton')}
                </Button>
```
with:
```tsx
                <Button id="waiter-tour-action" className="w-full text-md">
                  {tableDetails.isOccupied ? t('chargeTableButton') : t('openTableButton')}
                </Button>
```

Replace:
```tsx
                <Button
                  variant={'outline'}
                  className="w-full text-md"
                  disabled={!isCajaOpen}
                  onClick={(e) => {
                    openModal('PARTICIPANTS_QR', tableDetails)
                    e.preventDefault()
                    e.stopPropagation()
                  }}
                >
                  {t('assignTableLabel')}
                </Button>
```
with:
```tsx
                <Button
                  id="waiter-tour-assign"
                  variant={'outline'}
                  className="w-full text-md"
                  disabled={!isCajaOpen}
                  onClick={(e) => {
                    openModal('PARTICIPANTS_QR', tableDetails)
                    e.preventDefault()
                    e.stopPropagation()
                  }}
                >
                  {t('assignTableLabel')}
                </Button>
```

Add the import and render call. Add to the imports at the top:
```tsx
import { WaiterTour } from './components/WaiterTour'
```

And add `<WaiterTour tableIds={dashboardData?.map((t) => t.tableId) ?? []} onSelectFirstTable={() => dashboardData?.[0] && setSelectedTable(dashboardData[0].tableId)} />` right before the closing `<ParticipantQrModal />` line, i.e. replace:
```tsx
      <ParticipantQrModal />
    </div>
  )
}
```
with:
```tsx
      <ParticipantQrModal />
      <WaiterTour
        tableIds={dashboardData?.map((table) => table.tableId) ?? []}
        onSelectFirstTable={() => {
          if (dashboardData?.[0]) {
            setSelectedTable(dashboardData[0].tableId)
          }
        }}
      />
    </div>
  )
}
```

- [ ] **Step 2: Write the failing test for `WaiterTour`**

```tsx
import { render, screen } from '@testing-library/react'
import { vi, describe, test, expect, beforeEach } from 'vitest'
import { WaiterTour } from '@/pages/waiter/components/WaiterTour'
import { useAuthStore } from '@/store/authStore'
import { useWaiterTourStore } from '@/store/waiterTourStore'

describe('WaiterTour', () => {
  beforeEach(() => {
    useAuthStore.setState({ userId: 'waiter-1' })
    useWaiterTourStore.setState({ seenByUserId: {} })
    document.body.innerHTML =
      '<div id="waiter-tour-grid"></div><div id="waiter-tour-panel"></div>'
  })

  test('does not render when there are no tables yet', () => {
    render(<WaiterTour tableIds={[]} onSelectFirstTable={() => {}} />)

    expect(screen.queryByText('Tus mesas')).not.toBeInTheDocument()
  })

  test('does not render when this user already saw the tour', () => {
    useWaiterTourStore.getState().markTourSeen('waiter-1')

    render(<WaiterTour tableIds={['table-1']} onSelectFirstTable={() => {}} />)

    expect(screen.queryByText('Tus mesas')).not.toBeInTheDocument()
  })

  test('renders the first step for a first-time user with tables', () => {
    render(<WaiterTour tableIds={['table-1']} onSelectFirstTable={() => {}} />)

    expect(screen.getByText('Tus mesas')).toBeInTheDocument()
  })
})
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/pages/waiter/components/WaiterTour.test.tsx`
Expected: FAIL — `WaiterTour` does not exist yet.

- [ ] **Step 4: Write the implementation**

```tsx
import { useState } from 'react'
import Joyride, { type CallBackProps, EVENTS, type Step, STATUS } from 'react-joyride'
import { useAuthStore } from '@/store/authStore'
import { useWaiterTourStore } from '@/store/waiterTourStore'
import { useTranslation } from '@/lib/i18n'

interface WaiterTourProps {
  tableIds: string[]
  onSelectFirstTable: () => void
}

export const WaiterTour = ({ tableIds, onSelectFirstTable }: WaiterTourProps) => {
  const { t } = useTranslation('waiter')
  const userId = useAuthStore((state) => state.userId)
  const hasSeenTour = useWaiterTourStore((state) => state.hasSeenTour)
  const markTourSeen = useWaiterTourStore((state) => state.markTourSeen)
  const [run, setRun] = useState(true)

  const steps: Step[] = [
    { target: '#waiter-tour-grid', title: t('tourGridTitle'), content: t('tourGridContent'), disableBeacon: true },
    { target: '#waiter-tour-panel', title: t('tourPanelTitle'), content: t('tourPanelContent') },
    { target: '#waiter-tour-action', title: t('tourActionTitle'), content: t('tourActionContent') },
    { target: '#waiter-tour-assign', title: t('tourAssignTitle'), content: t('tourAssignContent') },
  ]

  if (!userId || tableIds.length === 0 || hasSeenTour(userId)) {
    return null
  }

  const handleCallback = (data: CallBackProps) => {
    const { status, type, index } = data

    // Advancing from step 1 (the grid, index 0) to step 2 (the detail panel, index 1) needs a
    // table selected first — the panel and every step after it don't exist in the DOM otherwise.
    if (type === EVENTS.STEP_AFTER && index === 0) {
      onSelectFirstTable()
    }

    if (status === STATUS.FINISHED || status === STATUS.SKIPPED) {
      setRun(false)
      markTourSeen(userId)
    }
  }

  return (
    <Joyride
      steps={steps}
      run={run}
      continuous
      showSkipButton
      callback={handleCallback}
      locale={{
        back: t('tourBackButton'),
        next: t('tourNextButton'),
        skip: t('tourSkipButton'),
        last: t('tourLastButton'),
      }}
      styles={{ options: { primaryColor: '#7a1315' } }}
    />
  )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/pages/waiter/components/WaiterTour.test.tsx`
Expected: PASS

- [ ] **Step 6: Run the full frontend suite and build**

Run: `cd frontend && pnpm run test:run && pnpm run build`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/waiter/Tables.tsx frontend/src/pages/waiter/components/WaiterTour.tsx frontend/src/pages/waiter/components/WaiterTour.test.tsx
git commit -m "feat(frontend): add WaiterTour and wire it into Tables.tsx"
```

---

### Task 9: Manual verification and `PROGRESS.md`

**Files:** none (verification), then `PROGRESS.md`.

- [ ] **Step 1: Manually verify the admin wizard in a browser**

Run `pnpm run dev` in `frontend/` (and the backend, with a fresh restaurant that has no branding/tables — the platform console's create flow, per spec §1). Log in as that restaurant's admin. Confirm: the wizard shows instead of the normal admin shell; completing both steps actually creates `DiningTables` rows (verify by checking the waiter's Tables screen afterward, or the admin's own Configuración → Espacio tab shows the same count); reloading the admin app afterward shows the normal dashboard, not the wizard again.

- [ ] **Step 2: Manually verify the waiter tour in a browser**

Log in as that restaurant's waiter (same tenant, now has tables from Step 1). Confirm the tour appears on `Tables.tsx`, highlighting the grid, then (after clicking "Siguiente") the detail panel with a table now selected, then the action button, then the assign button. Skip or finish it, reload, confirm it does not reappear for the same user. Log in as a *different* waiter user (or clear `localStorage` for `ember-waiter-tour-storage` and reuse the same login) and confirm it appears again for that other identity.

- [ ] **Step 3: Update `PROGRESS.md`**

Add a bullet to **Active Context & Recent Decisions** noting this feature is implemented and manually verified, referencing this plan's file, and summarizing the derived-gating design (§2.1 of the spec) so future work touching `/settings` or restaurant creation knows this behavior depends on `branding.businessName`/`space.totalTables` staying meaningful signals of "is this restaurant set up."
