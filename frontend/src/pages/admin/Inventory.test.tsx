import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Inventory } from './Inventory'
import { inventoryService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, inventoryService: { ...actual.inventoryService, getAll: vi.fn() } }
})

const ws = vi.hoisted(() => ({
  connect: vi.fn(),
  disconnect: vi.fn(),
  isConnected: false,
  stompClient: null,
  subscribeToInventory: vi.fn(),
  unsubscribeFromInventory: vi.fn(),
  lastLowStockAlert: null,
  clearLowStockAlert: vi.fn(),
}))

vi.mock('@/store/websocket', () => ({ useWebsocketStore: () => ws }))

const wrap = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <Inventory />
    </QueryClientProvider>
  )
}

describe('Inventory empty state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ restaurantId: 'restaurant-1' })
  })

  test('shows the empty state when there are no inventory items yet', async () => {
    vi.mocked(inventoryService.getAll).mockResolvedValue([] as never)
    wrap()
    expect(await screen.findByText('Todavía no tienes productos en inventario')).toBeVisible()
  })

  test('does not show the empty state when items exist', async () => {
    vi.mocked(inventoryService.getAll).mockResolvedValue([
      { id: 1, menuItemName: 'Harina', currentStock: 10, unit: 'kg', lowStockThreshold: 2 },
    ] as never)
    wrap()
    expect(await screen.findByText('Harina')).toBeVisible()
    expect(screen.queryByText('Todavía no tienes productos en inventario')).not.toBeInTheDocument()
  })
})

describe('Inventory card details', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ restaurantId: 'restaurant-1' })
  })

  test('shows when the stock was last updated and flags a dish hidden from the menu', async () => {
    vi.mocked(inventoryService.getAll).mockResolvedValue([
      {
        id: 1, menuItemId: 5, menuItemName: 'Tacos', unit: 'u', currentStock: 12, lowStockThreshold: 3,
        menuItemAvailable: false, updatedAt: '2026-10-02T08:30:00',
      },
      {
        id: 2, menuItemId: 6, menuItemName: 'Burger', unit: 'u', currentStock: 9, lowStockThreshold: 3,
        menuItemAvailable: true, updatedAt: '2026-10-01T18:00:00',
      },
    ] as never)

    wrap()

    expect(await screen.findByText('Actualizado: 02/10/2026, 08:30')).toBeVisible()
    expect(screen.getByText('Actualizado: 01/10/2026, 18:00')).toBeVisible()
    expect(screen.getAllByText('No disponible en la carta')).toHaveLength(1)
  })
})
