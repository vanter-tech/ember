import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ConsoleDashboard from '@/pages/console/ConsoleDashboard'
import { platformStatsService, platformAuditLogService } from '@/lib/platformApi'

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformStatsService: { get: vi.fn() },
    platformAuditLogService: { ...actual.platformAuditLogService, getRecent: vi.fn() },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  )

describe('ConsoleDashboard', () => {
  beforeEach(() => vi.clearAllMocks())

  test('renders KPI numbers and a recent-activity row', async () => {
    vi.mocked(platformStatsService.get).mockResolvedValue({
      tenants: { active: 5, suspended: 2, deleted: 1 },
      hubs: { online: 3, stale: 1, offline: 0, never: 2 },
    })
    vi.mocked(platformAuditLogService.getRecent).mockResolvedValue({
      content: [
        {
          id: 'a-1',
          operatorId: 'o-1',
          operatorEmail: 'op@ember.local',
          restaurantId: 'r-1',
          action: 'RESTAURANT_DELETED',
          oldValue: 'SUSPENDED',
          newValue: 'DELETED',
          createdAt: '2026-09-06T11:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      number: 0,
    })

    wrap(<ConsoleDashboard />)

    expect(await screen.findByText('RESTAURANT_DELETED')).toBeVisible()
    expect(screen.getByText('5')).toBeVisible()
    expect(screen.getByText('op@ember.local')).toBeVisible()
  })

  test('shows an error message when stats fail', async () => {
    vi.mocked(platformStatsService.get).mockRejectedValue(new Error('boom'))
    vi.mocked(platformAuditLogService.getRecent).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
    })

    wrap(<ConsoleDashboard />)

    expect(await screen.findByText('No se pudieron cargar las métricas.')).toBeVisible()
  })
})
