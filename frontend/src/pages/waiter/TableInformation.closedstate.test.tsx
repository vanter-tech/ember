import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TableInformation } from '@/pages/waiter/TableInformation'
import { SessionTableService, billingService } from '@/lib/api'

const navigateSpy = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigateSpy }
})

vi.mock('@/components/tours/SectionTour', () => ({ SectionTour: () => null }))

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    SessionTableService: { ...actual.SessionTableService, sessionInformation: vi.fn() },
    billingService: { ...actual.billingService, getBillState: vi.fn() },
  }
})

const sessionFixture = (status: 'OPEN' | 'CLOSED') => ({
  id: 's1',
  status,
  tableNumber: 4,
  isOccupied: status === 'OPEN',
  waiterId: 'waiter@ember.test',
  participants: [],
  items: [],
  activityLog: [],
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

describe('TableInformation closed stay-state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(billingService.getBillState).mockResolvedValue(null)
  })

  test('redirects to /waiter/tables when the session is already CLOSED on mount', async () => {
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(
      sessionFixture('CLOSED') as never,
    )
    wrap(<TableInformation />)

    await waitFor(() =>
      expect(navigateSpy).toHaveBeenCalledWith('/waiter/tables', { replace: true }),
    )
    expect(
      screen.queryByText('Mesa pagada y cerrada. Puedes imprimir la cuenta antes de salir.'),
    ).not.toBeInTheDocument()
  })

  test('shows the paid-and-closed banner on an OPEN -> CLOSED transition while mounted', async () => {
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(
      sessionFixture('OPEN') as never,
    )
    const qc = wrap(<TableInformation />)

    expect(await screen.findByRole('button', { name: /Agregar platillo/i })).toBeEnabled()

    qc.setQueryData(['sessionDetails', 's1'], sessionFixture('CLOSED'))

    expect(
      await screen.findByText(
        'Mesa pagada y cerrada. Puedes imprimir la cuenta antes de salir.',
      ),
    ).toBeVisible()
    expect(navigateSpy).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /Agregar platillo/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Transferir/i })).toBeDisabled()
  })
})
