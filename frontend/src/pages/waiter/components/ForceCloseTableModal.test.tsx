import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { ForceCloseTableModal } from './ForceCloseTableModal'
import { useUIStore } from '@/store/uiStore'
import { billingService } from '@/lib/api'

const navigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigate }
})

vi.mock('react-hot-toast', () => ({
  default: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
}))

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, billingService: { ...actual.billingService, forceCloseTable: vi.fn() } }
})

const wrap = (ui: ReactNode, qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )

describe('ForceCloseTableModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({
      activeModal: 'FORCE_CLOSE_TABLE',
      modalPayload: { sessionId: 's1', tableNumber: 4 },
    })
  })

  test('names the table and will not close it until a reason is written', () => {
    wrap(<ForceCloseTableModal />)

    expect(screen.getByText(/mesa 4/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Cerrar mesa' })).toBeDisabled()

    fireEvent.change(screen.getByPlaceholderText('Motivo del cierre (obligatorio)'), {
      target: { value: 'ok' },
    })
    expect(screen.getByRole('button', { name: 'Cerrar mesa' })).toBeDisabled()
  })

  test('closes the table with the reason, refreshes the tables and goes back to the list', async () => {
    vi.mocked(billingService.forceCloseTable).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidate = vi.spyOn(qc, 'invalidateQueries')
    wrap(<ForceCloseTableModal />, qc)

    fireEvent.change(screen.getByPlaceholderText('Motivo del cierre (obligatorio)'), {
      target: { value: 'el cliente se fue sin pagar' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar mesa' }))

    await waitFor(() =>
      expect(billingService.forceCloseTable).toHaveBeenCalledWith('s1', 'el cliente se fue sin pagar'),
    )
    await waitFor(() => expect(toast.success).toHaveBeenCalled())
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboardData'] })
    expect(navigate).toHaveBeenCalledWith('/waiter/tables')
    expect(useUIStore.getState().activeModal).toBeNull()
  })

  test('a table that already has confirmed payments shows a specific message and stays on the page', async () => {
    vi.mocked(billingService.forceCloseTable).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409 },
    })
    wrap(<ForceCloseTableModal />)

    fireEvent.change(screen.getByPlaceholderText('Motivo del cierre (obligatorio)'), {
      target: { value: 'mesa atascada' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar mesa' }))

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(expect.stringContaining('pagos confirmados')))
    expect(navigate).not.toHaveBeenCalled()
  })
})
