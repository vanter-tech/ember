import { describe, expect, test, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TableAnalytics } from './TableAnalytics'
import { analyticsService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, analyticsService: { ...actual.analyticsService, getTables: vi.fn() } }
})

const wrap = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TableAnalytics />
    </QueryClientProvider>,
  )

describe('TableAnalytics merged tables', () => {
  beforeEach(() => vi.clearAllMocks())

  test('a table that was merged shows "Fusionada con M4, M5"; an unmerged one shows nothing extra', async () => {
    vi.mocked(analyticsService.getTables).mockResolvedValue({
      activeTableCount: 5,
      totalTurnovers: 2,
      totalRevenue: 150,
      averageTurnoverRate: 0.4,
      averageSessionDurationMinutes: 30,
      tables: [
        { tableId: 'a', tableNumber: 3, turnoverCount: 1, revenue: 100, revenueShare: 66, averageSessionDurationMinutes: 30, mergedWithTableNumbers: [4, 5] },
        { tableId: 'b', tableNumber: 7, turnoverCount: 1, revenue: 50, revenueShare: 33, averageSessionDurationMinutes: 20, mergedWithTableNumbers: [] },
      ],
    } as never)

    wrap()

    expect(await screen.findByText('Fusionada con M4, M5')).toBeVisible()
    expect(screen.getAllByText(/Fusionada con/)).toHaveLength(1)
  })
})
