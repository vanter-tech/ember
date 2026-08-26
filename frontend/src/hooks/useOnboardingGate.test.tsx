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

  test('needsOnboarding flips live from true to false once the wizard finishes and invalidates the query (not frozen)', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    function localWrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    }

    vi.mocked(SettingsService.getSettings)
      .mockResolvedValueOnce({
        branding: { businessName: '' },
        space: { totalTables: 0 },
      } as never)
      .mockResolvedValue({
        branding: { businessName: 'Ember Grill' },
        space: { totalTables: 5 },
      } as never)

    const { result } = renderHook(() => useOnboardingGate(), { wrapper: localWrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.needsOnboarding).toBe(true)

    await queryClient.invalidateQueries({ queryKey: ['restaurantSettings'] })

    await waitFor(() => expect(result.current.needsOnboarding).toBe(false))
  })
})
