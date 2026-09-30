import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PendingCashList } from './PendingCashList'
import { cashDrawerService, type CashDrawerEvent } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, cashDrawerService: { current: vi.fn(), receive: vi.fn(), open: vi.fn() } }
})

const event = (over: Partial<CashDrawerEvent>): CashDrawerEvent => ({
  id: 'e1', type: 'CASH_SALE', status: 'PENDING', tableNumber: 4, amount: 20, reason: null,
  createdAt: '2026-09-29T10:00:00', receivedAt: null, drawer: 'NONE', createdByName: 'Alice', drawerError: null, ...over,
})

const wrap = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PendingCashList />
    </QueryClientProvider>,
  )

describe('PendingCashList', () => {
  beforeEach(() => vi.clearAllMocks())

  test('lists pending cash sales and receives one on click', async () => {
    vi.mocked(cashDrawerService.current).mockResolvedValue([event({})])
    vi.mocked(cashDrawerService.receive).mockResolvedValue(event({ status: 'RECEIVED', drawer: 'OPENING' }))
    wrap()
    fireEvent.click(await screen.findByRole('button', { name: 'Recibir y abrir caja' }))
    await waitFor(() => expect(cashDrawerService.receive).toHaveBeenCalledWith('e1'))
  })

  test('does not list received sales (even with a failed drawer) nor manual openings', async () => {
    vi.mocked(cashDrawerService.current).mockResolvedValue([
      event({ id: 'a', status: 'RECEIVED', drawer: 'OPENED' }),
      event({ id: 'c', status: 'RECEIVED', drawer: 'FAILED' }),
      event({ id: 'b', type: 'MANUAL', status: 'RECEIVED', drawer: 'OPENED', tableNumber: null, amount: null }),
    ])
    wrap()
    expect(await screen.findByText('No hay cobros pendientes.')).toBeVisible()
    expect(screen.queryByRole('button')).toBeNull()
  })

  test('shows the empty state when nothing is pending', async () => {
    vi.mocked(cashDrawerService.current).mockResolvedValue([])
    wrap()
    expect(await screen.findByText('No hay cobros pendientes.')).toBeVisible()
  })
})
