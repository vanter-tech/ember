import { useRef } from 'react'
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
 *
 * Only an ERRORED settle freezes (see report 206): AdminLayout (the only caller) conditionally
 * mounts/unmounts a whole subtree off `isLoading`, and that subtree (TopNav) runs its own separate
 * `useQuery(['restaurantSettings'])` (settingStore) — a fresh observer joining an already-errored,
 * always-stale (staleTime 0) query triggers a background refetch, which flips the shared query's
 * `status` back to `pending` mid-flight (react-query only does this for a previously-errored query;
 * a successful query's `status` stays `success` through background refetches). Without freezing
 * that, `isLoading` flips back to true, AdminLayout unmounts TopNav, its observer detaches, the next
 * settle flips `isLoading` false again, AdminLayout remounts TopNav, which triggers another
 * refetch — an infinite mount/refetch loop (confirmed via a reduced repro: two `useQuery` observers
 * on the same key, one gating the other's mount, produced 1000+ fetches in under a second).
 * Freezing every settle (not just errors) was tried first but broke the wizard's own completion
 * flow: `needsOnboarding` needs to go live true → false the moment its save mutation invalidates
 * this same query key, and a successful settle can't exhibit the ping-pong above, so it's safe to
 * leave live (bugfix-onboarding-wizard-finish-button-navigation).
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

  const erroredOnce = useRef(false)
  if (!isPending && isError) {
    erroredOnce.current = true
  }

  if (erroredOnce.current) {
    return { needsOnboarding: false, isLoading: false, isError: true }
  }

  return { needsOnboarding, isLoading: isPending, isError }
}
