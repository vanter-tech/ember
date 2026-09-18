import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TableInformation } from '@/pages/waiter/TableInformation'
import { SessionTableService, billingService } from '@/lib/api'

vi.mock('@/components/tours/SectionTour', () => ({ SectionTour: () => null }))

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    SessionTableService: { ...actual.SessionTableService, sessionInformation: vi.fn() },
    billingService: { ...actual.billingService, getBillState: vi.fn() },
  }
})

const sessionFixture = (activityLog: unknown[]) => ({
  id: 's1',
  status: 'OPEN',
  tableNumber: 4,
  isOccupied: true,
  waiterId: 'waiter@ember.test',
  participants: [],
  items: [],
  activityLog,
  createdAt: '2026-09-03T10:00:00Z',
})

const wrap = (ui: ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/waiter/tables/s1']}>
        <Routes>
          <Route path="/waiter/tables/:id" element={ui} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return qc
}

describe('TableInformation activity feed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(billingService.getBillState).mockResolvedValue(null)
  })

  test('shows "X left the table" for a PARTICIPANT_LEFT entry', async () => {
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(
      sessionFixture([
        { type: 'PARTICIPANT_LEFT', participantName: 'Ana', timestamp: '2026-09-18T10:00:00' },
      ]) as never,
    )
    wrap(<TableInformation />)

    expect(await screen.findByText('Ana ha salido de la mesa')).toBeVisible()
  })
})
