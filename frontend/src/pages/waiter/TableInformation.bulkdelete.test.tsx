import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { TableInformation } from '@/pages/waiter/TableInformation'
import { SessionTableService, billingService } from '@/lib/api'

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
    SessionTableService: {
      ...actual.SessionTableService,
      sessionInformation: vi.fn(),
      removeItems: vi.fn(),
    },
    billingService: { ...actual.billingService, getBillState: vi.fn() },
  }
})

const item = (id: string, name: string, status: string) => ({
  id, name, price: 5, participantName: 'Ana', participantId: null, status, modifiers: [],
})

const session = (status = 'OPEN') => ({
  id: 's1',
  status,
  tableNumber: 4,
  isOccupied: true,
  waiterId: 'w@x.com',
  participants: [{ userId: null, name: 'Ana' }],
  items: [item('a', 'Tacos', 'PENDING'), item('b', 'Sopa', 'PENDING'), item('c', 'Flan', 'PREPARING')],
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
}

describe('TableInformation — select all + bulk delete', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(billingService.getBillState).mockResolvedValue(null)
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(session() as never)
    vi.mocked(SessionTableService.removeItems).mockResolvedValue(undefined)
  })

  test('"Seleccionar todos" selects only the items that can still be removed', async () => {
    const user = userEvent.setup()
    wrap(<TableInformation />)

    await user.click(await screen.findByRole('button', { name: 'Seleccionar todos' }))

    expect(screen.getByRole('checkbox', { name: 'Seleccionar Tacos' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Seleccionar Sopa' })).toBeChecked()
    // Already being prepared: stays as it is today — it cannot be removed, so it cannot be picked.
    expect(screen.getByRole('checkbox', { name: 'Seleccionar Flan' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Seleccionar Flan' })).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'Eliminar seleccionados (2)' })).toBeVisible()
  })

  test('the same button deselects everything again', async () => {
    const user = userEvent.setup()
    wrap(<TableInformation />)

    await user.click(await screen.findByRole('button', { name: 'Seleccionar todos' }))
    await user.click(screen.getByRole('button', { name: 'Deseleccionar todos' }))

    expect(screen.getByRole('checkbox', { name: 'Seleccionar Tacos' })).not.toBeChecked()
    expect(screen.queryByRole('button', { name: /Eliminar seleccionados/ })).not.toBeInTheDocument()
  })

  test('items can also be picked one by one', async () => {
    const user = userEvent.setup()
    wrap(<TableInformation />)

    await user.click(await screen.findByRole('checkbox', { name: 'Seleccionar Sopa' }))

    expect(screen.getByRole('button', { name: 'Eliminar seleccionados (1)' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Seleccionar todos' })).toBeVisible()
  })

  test('confirming deletes the selected items in ONE request and clears the selection', async () => {
    const user = userEvent.setup()
    wrap(<TableInformation />)

    await user.click(await screen.findByRole('button', { name: 'Seleccionar todos' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar seleccionados (2)' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('¿Eliminar 2 platos?')).toBeVisible()
    expect(SessionTableService.removeItems).not.toHaveBeenCalled()
    await user.click(within(dialog).getByRole('button', { name: 'Eliminar' }))

    await waitFor(() => expect(SessionTableService.removeItems).toHaveBeenCalledWith('s1', ['a', 'b']))
    expect(SessionTableService.removeItems).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /Eliminar seleccionados/ })).not.toBeInTheDocument()
    expect(toast.success).toHaveBeenCalled()
  })

  test('cancelling the confirmation deletes nothing and keeps the selection', async () => {
    const user = userEvent.setup()
    wrap(<TableInformation />)

    await user.click(await screen.findByRole('button', { name: 'Seleccionar todos' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar seleccionados (2)' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancelar' }))

    expect(SessionTableService.removeItems).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Eliminar seleccionados (2)' })).toBeVisible()
  })

  test('if the server refuses the batch because an item reached the kitchen, it says so and refreshes', async () => {
    const user = userEvent.setup()
    vi.mocked(SessionTableService.removeItems).mockRejectedValue(
      Object.assign(new Error('conflict'), { isAxiosError: true, response: { status: 409 } }),
    )
    wrap(<TableInformation />)

    await user.click(await screen.findByRole('button', { name: 'Seleccionar todos' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar seleccionados (2)' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Eliminar' }))

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        'Un plato ya pasó a cocina, así que no se eliminó nada. Revisa la lista e inténtalo de nuevo.',
      ),
    )
    expect(vi.mocked(SessionTableService.sessionInformation).mock.calls.length).toBeGreaterThan(1)
  })

  test('with the session closed the selection controls are disabled', async () => {
    vi.mocked(SessionTableService.sessionInformation).mockResolvedValue(session('CLOSED') as never)
    wrap(<TableInformation />)

    expect(await screen.findByRole('button', { name: 'Seleccionar todos' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Seleccionar Tacos' })).toBeDisabled()
  })
})
