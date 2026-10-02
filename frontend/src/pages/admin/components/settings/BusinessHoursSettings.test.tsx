import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BusinessHoursSettings } from './BusinessHoursSettings'
import { SettingsService, restaurantAdminService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    SettingsService: { ...actual.SettingsService, getSettings: vi.fn(), updateSettings: vi.fn() },
    restaurantAdminService: { ...actual.restaurantAdminService, getPlan: vi.fn() },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {ui}
    </QueryClientProvider>,
  )

describe('BusinessHoursSettings — restaurant time zone', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(SettingsService.getSettings).mockResolvedValue({ businessHours: { schedule: [] } } as never)
  })

  test('states the time zone the opening hours are read in', async () => {
    vi.mocked(restaurantAdminService.getPlan).mockResolvedValue({ timezone: 'America/Managua' } as never)

    wrap(<BusinessHoursSettings />)

    expect(await screen.findByText('Zona horaria del restaurante: America/Managua')).toBeVisible()
  })

  test('shows a placeholder, not text, while the time zone loads', async () => {
    vi.mocked(restaurantAdminService.getPlan).mockReturnValue(new Promise(() => {}) as never)

    wrap(<BusinessHoursSettings />)

    expect(await screen.findByTestId('skeleton-timezone')).toBeInTheDocument()
    expect(screen.queryByText(/Zona horaria/)).not.toBeInTheDocument()
  })
})
