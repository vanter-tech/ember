import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { CashDrawerWatcher } from './CashDrawerWatcher'
import { beep } from './beep'
import { cashDrawerService, type CashDrawerEvent } from '@/lib/api'

vi.mock('react-hot-toast', () => ({ default: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }) }))
vi.mock('./beep', () => ({ beep: vi.fn() }))
vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, cashDrawerService: { current: vi.fn(), receive: vi.fn(), open: vi.fn() } }
})

const pending = (id: string): CashDrawerEvent => ({
  id, type: 'CASH_SALE', status: 'PENDING', tableNumber: 4, amount: 20, reason: null,
  createdAt: '2026-09-29T10:00:00', receivedAt: null, drawer: 'NONE', createdByName: null, drawerError: null,
})

const wrap = (client: QueryClient) =>
  render(
    <QueryClientProvider client={client}>
      <CashDrawerWatcher />
    </QueryClientProvider>,
  )

describe('CashDrawerWatcher', () => {
  beforeEach(() => vi.clearAllMocks())

  test('does not alert for the backlog on first load, but alerts (toast + beep) for a receipt that arrives later', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    vi.mocked(cashDrawerService.current).mockResolvedValue([pending('a')])
    wrap(client)
    await waitFor(() => expect(cashDrawerService.current).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 20))
    expect(toast).not.toHaveBeenCalled()
    expect(beep).not.toHaveBeenCalled()

    vi.mocked(cashDrawerService.current).mockResolvedValue([pending('b'), pending('a')])
    await client.invalidateQueries({ queryKey: ['cashDrawerEvents'] })
    await waitFor(() => expect(toast).toHaveBeenCalledTimes(1))
    expect(beep).toHaveBeenCalledTimes(1)
  })

  test('F9 opens the manual dialog and the reason is required before opening', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    vi.mocked(cashDrawerService.current).mockResolvedValue([])
    wrap(client)
    fireEvent.keyDown(window, { key: 'F9' })
    const confirm = await screen.findByRole('button', { name: 'Abrir' })
    expect(confirm).toBeDisabled()
    fireEvent.change(screen.getByPlaceholderText('Ej. cambio, retiro…'), { target: { value: 'cambio' } })
    vi.mocked(cashDrawerService.open).mockResolvedValue({ ...pending('m'), type: 'MANUAL', status: 'RECEIVED', drawer: 'OPENING' })
    fireEvent.click(confirm)
    await waitFor(() => expect(cashDrawerService.open).toHaveBeenCalledWith('cambio'))
  })
})
