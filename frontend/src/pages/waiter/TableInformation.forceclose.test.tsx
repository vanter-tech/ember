import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TableInformation } from '@/pages/waiter/TableInformation'
import { SessionTableService, billingService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { useUIStore } from '@/store/uiStore'

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
  }
})

const sessionFixture = (status: 'OPEN' | 'CLOSED') => ({
  id: 's1',
  status,
  tableNumber: 3,
  isOccupied: status === 'OPEN',
  waiterId: 'fe3@ember.com',
  participants: [],
  items: [],
  activityLog: [],
  createdAt: '2026-09-04T10:27:19Z',
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/waiter/tables/s1']}>
        <Routes>
          <Route path="/waiter/tables/:id" element={ui} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )

describe('TableInformation — admin closes a stuck table', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: null, modalPayload: null })
    vi.mocked(billingService.getBillState).mockResolvedValue({ id: 5, total: 30, splits: [] } as never)
  })

  test('an ADMIN sees the button on an open table, and it opens the confirmation with the table', async () => {
    useAuthStore.setState({ role: 'ADMIN' })
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(sessionFixture('OPEN') as never)
    wrap(<TableInformation />)

    fireEvent.click(await screen.findByRole('button', { name: /Cerrar mesa \(admin\)/ }))

    await waitFor(() => expect(useUIStore.getState().activeModal).toBe('FORCE_CLOSE_TABLE'))
    expect(useUIStore.getState().modalPayload).toMatchObject({ sessionId: 's1', tableNumber: 3 })
  })

  test('a WAITER never sees it', async () => {
    useAuthStore.setState({ role: 'WAITER' })
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(sessionFixture('OPEN') as never)
    wrap(<TableInformation />)

    await waitFor(() => expect(SessionTableService.sessionInformation).toHaveBeenCalled())
    await screen.findByRole('button', { name: /Agregar|Añadir|Item|Plato/i })
    expect(screen.queryByRole('button', { name: /Cerrar mesa \(admin\)/ })).not.toBeInTheDocument()
  })

  test('an ADMIN does not see it on a table that is already closed', async () => {
    useAuthStore.setState({ role: 'ADMIN' })
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(sessionFixture('CLOSED') as never)
    wrap(<TableInformation />)

    await waitFor(() => expect(SessionTableService.sessionInformation).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /Cerrar mesa \(admin\)/ })).not.toBeInTheDocument()
  })
})
