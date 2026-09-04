import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { TableInformation } from '@/pages/waiter/TableInformation'
import { SessionTableService, billingService, printingService } from '@/lib/api'

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => vi.fn() }
})

vi.mock('@/components/tours/SectionTour', () => ({ SectionTour: () => null }))

vi.mock('react-hot-toast', () => ({
  default: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
}))

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    SessionTableService: { ...actual.SessionTableService, sessionInformation: vi.fn() },
    billingService: { ...actual.billingService, getBillState: vi.fn() },
    printingService: { ...actual.printingService, printBillReceipt: vi.fn() },
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

const billFixture = { id: 5, total: 30, splits: [] }

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

describe('TableInformation print bill', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(billingService.getBillState).mockResolvedValue(billFixture as never)
  })

  test('Print bill is disabled while the table is OPEN', async () => {
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(
      sessionFixture('OPEN') as never,
    )
    wrap(<TableInformation />)

    expect(await screen.findByRole('button', { name: /Imprimir cuenta/i })).toBeDisabled()
  })

  test('clicking Print bill calls printBillReceipt with the bill id and toasts on send', async () => {
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(
      sessionFixture('OPEN') as never,
    )
    vi.mocked(printingService.printBillReceipt).mockResolvedValue({
      jobId: 'j1',
      status: 'SENT',
    })
    const qc = wrap(<TableInformation />)

    await screen.findByRole('button', { name: /Imprimir cuenta/i })
    qc.setQueryData(['sessionDetails', 's1'], sessionFixture('CLOSED'))

    const btn = await screen.findByRole('button', { name: /Imprimir cuenta/i })
    await waitFor(() => expect(btn).toBeEnabled())
    fireEvent.click(btn)

    await waitFor(() =>
      expect(printingService.printBillReceipt).toHaveBeenCalledWith(5),
    )
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith('Cuenta enviada a la impresora'),
    )
  })

  test('a PENDING job toasts the no-printer message', async () => {
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(
      sessionFixture('OPEN') as never,
    )
    vi.mocked(printingService.printBillReceipt).mockResolvedValue({
      jobId: 'j1',
      status: 'PENDING',
    })
    const qc = wrap(<TableInformation />)

    await screen.findByRole('button', { name: /Imprimir cuenta/i })
    qc.setQueryData(['sessionDetails', 's1'], sessionFixture('CLOSED'))

    const btn = await screen.findByRole('button', { name: /Imprimir cuenta/i })
    await waitFor(() => expect(btn).toBeEnabled())
    fireEvent.click(btn)

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith('Cuenta en cola (sin impresora conectada)'),
    )
  })
})
