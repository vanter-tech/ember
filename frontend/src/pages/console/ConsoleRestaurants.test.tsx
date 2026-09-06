import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ConsoleRestaurants from '@/pages/console/ConsoleRestaurants'
import { platformRestaurantService } from '@/lib/platformApi'

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformRestaurantService: { ...actual.platformRestaurantService, getAll: vi.fn() },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  )

const page = (rows: unknown[]) => ({
  content: rows,
  totalElements: rows.length,
  totalPages: 1,
  size: 10,
  number: 0,
})

const row = (over: Record<string, unknown> = {}) => ({
  id: 'r-1',
  name: 'Tenant Grill',
  slug: 'tenant-grill',
  plan: 'PRO',
  status: 'ACTIVE',
  hubStatus: 'ONLINE',
  createdAt: '2026-09-01T00:00:00Z',
  ...over,
})

describe('ConsoleRestaurants', () => {
  beforeEach(() => vi.clearAllMocks())

  test('renders the Hub status for each row', async () => {
    vi.mocked(platformRestaurantService.getAll).mockResolvedValue(
      page([row({ hubStatus: 'OFFLINE' })]) as never
    )
    wrap(<ConsoleRestaurants />)
    expect(await screen.findByText('OFFLINE')).toBeVisible()
  })

  test('"Ver eliminados" refetches with includeDeleted=true', async () => {
    vi.mocked(platformRestaurantService.getAll).mockResolvedValue(page([row()]) as never)
    wrap(<ConsoleRestaurants />)
    await screen.findByText('Tenant Grill')

    fireEvent.click(screen.getByLabelText('Ver eliminados'))

    await waitFor(() =>
      expect(platformRestaurantService.getAll).toHaveBeenLastCalledWith(0, 10, true)
    )
  })
})
