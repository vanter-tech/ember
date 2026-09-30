import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Tables } from './Tables'
import { DashboardService, cashShiftService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    DashboardService: { ...actual.DashboardService, getDashboardData: vi.fn() },
    cashShiftService: { ...actual.cashShiftService, current: vi.fn() },
  }
})

const ws = vi.hoisted(() => ({
  isConnected: false,
  stompClient: null,
  subscribeToWaiterSession: vi.fn(),
  unsubscribeFromWaiterSession: vi.fn(),
}))
vi.mock('@/store/websocket', () => ({ useWebsocketStore: () => ws }))

const wrap = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <Tables />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('Tables empty state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ restaurantId: 'restaurant-1' })
    vi.mocked(cashShiftService.current).mockResolvedValue({ status: 'OPEN' } as never)
  })

  test('shows the empty state when the caja is open but no tables are configured', async () => {
    vi.mocked(DashboardService.getDashboardData).mockResolvedValue([] as never)
    wrap()
    expect(await screen.findByText('Todavía no hay mesas configuradas')).toBeVisible()
  })

  test('does not show the empty state when tables exist', async () => {
    vi.mocked(DashboardService.getDashboardData).mockResolvedValue([
      { tableId: 't1', tableNumber: 1, isOccupied: false },
    ] as never)
    wrap()
    expect(await screen.findByText('M1')).toBeVisible()
    expect(screen.queryByText('Todavía no hay mesas configuradas')).not.toBeInTheDocument()
  })
})

describe('Tables with the caja closed', () => {
  const occupied = [
    {
      tableId: 't3',
      tableNumber: 3,
      isOccupied: true,
      currentSession: { sessionId: 'sess-3', waiterName: 'Fe', currentParticipant: 2 },
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(cashShiftService.current).mockResolvedValue(null as never)
    vi.mocked(DashboardService.getDashboardData).mockResolvedValue(occupied as never)
  })

  // A table stuck open for days is exactly the case where the caja is closed too, so the admin
  // must still be able to open its detail (to close it) without a shift.
  test('an ADMIN can still select an occupied table and reach its detail', async () => {
    useAuthStore.setState({ restaurantId: 'restaurant-1', role: 'ADMIN' })
    wrap()

    fireEvent.click(await screen.findByText('M3'))

    expect(await screen.findByRole('link', { name: 'Ver Informacion' })).toHaveAttribute('href', '/sess-3')
    expect(screen.queryByText('Necesita abrir la caja para poder asignar mesa.')).not.toBeInTheDocument()
  })

  test('a WAITER still cannot select tables until the caja is open', async () => {
    useAuthStore.setState({ restaurantId: 'restaurant-1', role: 'WAITER' })
    wrap()

    fireEvent.click(await screen.findByText('M3'))

    expect(screen.getByText('Necesita abrir la caja para poder asignar mesa.')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Ver Informacion' })).not.toBeInTheDocument()
  })
})
