import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TableInformation } from '@/pages/waiter/TableInformation'
import { SessionTableService, billingService } from '@/lib/api'

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => vi.fn() }
})
vi.mock('@/components/tours/SectionTour', () => ({ SectionTour: () => null }))

const { hubFlag } = vi.hoisted(() => ({ hubFlag: { current: true } }))
vi.mock('@/lib/isHubBuild', () => ({
  isHubBuild: () => hubFlag.current,
}))

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    SessionTableService: { ...actual.SessionTableService, sessionInformation: vi.fn() },
    billingService: { ...actual.billingService, getBillState: vi.fn() },
  }
})

const seatSession = {
  id: 's1',
  status: 'OPEN',
  tableNumber: 4,
  isOccupied: true,
  waiterId: 'w@x.com',
  participants: [{ userId: null, name: 'Ana' }],
  items: [],
  activityLog: [],
  createdAt: '2026-09-03T10:00:00Z',
}

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
}

describe('TableInformation — Hub seat controls', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hubFlag.current = true
    vi.mocked(billingService.getBillState).mockResolvedValue(null)
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(seatSession as never)
  })

  test('Hub build shows add / rename / remove seat controls', async () => {
    wrap(<TableInformation />)

    expect(await screen.findByRole('button', { name: /agregar asiento/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /renombrar asiento/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /quitar asiento/i })).toBeInTheDocument()
  })

  test('cloud build shows none of the seat controls', async () => {
    hubFlag.current = false
    wrap(<TableInformation />)

    expect(await screen.findByText('Ana')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /agregar asiento/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /renombrar asiento/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /quitar asiento/i })).not.toBeInTheDocument()
  })
})
