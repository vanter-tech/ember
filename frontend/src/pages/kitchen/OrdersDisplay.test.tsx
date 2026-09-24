import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { OrdersDisplays } from './OrdersDisplay'
import { kitchenServices } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    kitchenServices: { ...actual.kitchenServices, getOrdersByTables: vi.fn() },
  }
})

const ws = vi.hoisted(() => ({ isConnected: true }))
vi.mock('@/store/websocket', () => ({ useWebsocketStore: (selector: (s: typeof ws) => unknown) => selector(ws) }))

const wrap = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <OrdersDisplays />
    </QueryClientProvider>
  )
}

describe('OrdersDisplays empty state', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows the empty state when the queue has no orders', async () => {
    vi.mocked(kitchenServices.getOrdersByTables).mockResolvedValue([] as never)
    wrap()
    expect(await screen.findByText('No hay pedidos en cola')).toBeVisible()
  })

  test('does not show the empty state when there are orders', async () => {
    vi.mocked(kitchenServices.getOrdersByTables).mockResolvedValue([
      {
        orders: [
          { id: '1', sessionId: 's1', createdAt: new Date().toISOString(), items: [], tableNumber: 3, status: 'PENDING' },
        ],
      },
    ] as never)
    wrap()
    await screen.findAllByText('3')
    expect(screen.queryByText('No hay pedidos en cola')).not.toBeInTheDocument()
  })
})
