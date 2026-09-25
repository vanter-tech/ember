import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { FocusedCard } from './FocusedCard'
import { kitchenServices, printingService, type kitchenOrders } from '@/lib/api'

vi.mock('react-hot-toast', () => ({
  default: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
}))

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    kitchenServices: {
      ...actual.kitchenServices,
      updateItemStatus: vi.fn(),
      updateItemsStatus: vi.fn(),
    },
    printingService: {
      ...actual.printingService,
      printKitchenTicket: vi.fn(),
    },
  }
})

const sampleOrder: kitchenOrders = {
  id: 'ko-1',
  sessionId: 'sess-1',
  tableNumber: 5,
  createdAt: '2026-09-13T12:00:00',
  items: [
    { itemId: 'item-1', name: 'Tacos', status: 'PENDING', modifiers: [] },
    { itemId: 'item-2', name: 'Burrito', status: 'PREPARING', modifiers: [] },
  ],
} as unknown as kitchenOrders

const wrap = (order: kitchenOrders) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <FocusedCard order={order} />
    </QueryClientProvider>,
  )

describe('FocusedCard bulk status selection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('the status dropdown is hidden until at least one item is selected', () => {
    wrap(sampleOrder)

    expect(screen.queryByText('Cambiar estado a...')).not.toBeInTheDocument()
  })

  test('selecting one item reveals the dropdown; choosing a status bulk-updates just that item', async () => {
    vi.mocked(kitchenServices.updateItemsStatus).mockResolvedValue(sampleOrder)
    wrap(sampleOrder)

    await userEvent.click(screen.getByLabelText('Seleccionar Tacos'))
    expect(screen.getByText('Cambiar estado a...')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(screen.getByRole('option', { name: 'Listo' }))

    await waitFor(() =>
      expect(kitchenServices.updateItemsStatus).toHaveBeenCalledWith('ko-1', ['item-1'], 'READY'),
    )
  })

  test('"Seleccionar todo" selects every visible item, and toggles to "Deseleccionar todo"', async () => {
    vi.mocked(kitchenServices.updateItemsStatus).mockResolvedValue(sampleOrder)
    wrap(sampleOrder)

    await userEvent.click(screen.getByText('Seleccionar todo'))
    expect(screen.getByText('Deseleccionar todo')).toBeInTheDocument()

    await userEvent.click(screen.getByText('Deseleccionar todo'))
    await waitFor(() => expect(screen.getByText('Seleccionar todo')).toBeInTheDocument())

    await userEvent.click(screen.getByText('Seleccionar todo'))
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(screen.getByRole('option', { name: 'Listo' }))

    await waitFor(() =>
      expect(kitchenServices.updateItemsStatus).toHaveBeenCalledWith('ko-1', ['item-1', 'item-2'], 'READY'),
    )
  })

  test('selection clears after a successful bulk update', async () => {
    vi.mocked(kitchenServices.updateItemsStatus).mockResolvedValue(sampleOrder)
    wrap(sampleOrder)

    await userEvent.click(screen.getByText('Seleccionar todo'))
    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(screen.getByRole('option', { name: 'Listo' }))

    await waitFor(() => expect(screen.queryByText('Cambiar estado a...')).not.toBeInTheDocument())
    expect(screen.getByText('Seleccionar todo')).toBeInTheDocument()
  })
})

describe('FocusedCard manual ticket reprint', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('clicking Imprimir reprints the ticket and toasts on send', async () => {
    vi.mocked(printingService.printKitchenTicket).mockResolvedValue({
      jobId: 'j1',
      status: 'SENT',
    })
    wrap(sampleOrder)

    await userEvent.click(screen.getByText('Imprimir'))

    await waitFor(() => expect(printingService.printKitchenTicket).toHaveBeenCalledWith('ko-1'))
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith('Ticket enviado a la impresora'),
    )
  })

  test('a PENDING reprint job toasts the no-printer message', async () => {
    vi.mocked(printingService.printKitchenTicket).mockResolvedValue({
      jobId: 'j1',
      status: 'PENDING',
    })
    wrap(sampleOrder)

    await userEvent.click(screen.getByText('Imprimir'))

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith('Ticket en cola (sin impresora conectada)'),
    )
  })

  test('does not render the removed Cliente placeholder or Anular button', () => {
    wrap(sampleOrder)

    expect(screen.queryByText(/Por-iterar/)).not.toBeInTheDocument()
    expect(screen.queryByText('Anular')).not.toBeInTheDocument()
  })
})
