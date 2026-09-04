import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AddItemModal } from '@/pages/waiter/components/AddItemModal'
import { useUIStore } from '@/store/uiStore'
import { SessionTableService, inventoryMenuItemService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    inventoryMenuItemService: { listAll: vi.fn() },
    SessionTableService: { ...actual.SessionTableService, addWaiterItem: vi.fn() },
  }
})

const wrap = (ui: ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('AddItemModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'ADD_ITEM', modalPayload: { sessionId: 's1', participants: [] } })
    vi.mocked(inventoryMenuItemService.listAll).mockResolvedValue([
      { id: 10, name: 'Pizza', price: 12, available: true, modifierGroups: [] },
      { id: 11, name: 'Ensalada', price: 8, available: true, modifierGroups: [] },
    ])
  })

  test('filters the list by search text', async () => {
    wrap(<AddItemModal />)
    expect(await screen.findByText('Pizza')).toBeVisible()
    fireEvent.change(screen.getByPlaceholderText('Buscar platillo...'), {
      target: { value: 'ens' },
    })
    expect(screen.queryByText('Pizza')).not.toBeInTheDocument()
    expect(screen.getByText('Ensalada')).toBeVisible()
  })

  test('submits the selected item with participantName null for "Mesa"', async () => {
    vi.mocked(SessionTableService.addWaiterItem).mockResolvedValue(undefined)
    wrap(<AddItemModal />)
    fireEvent.click(await screen.findByText('Pizza'))
    fireEvent.click(screen.getByText('Agregar a la comanda'))
    await waitFor(() =>
      expect(SessionTableService.addWaiterItem).toHaveBeenCalledWith('s1', {
        menuItemId: 10,
        selectedOptionIds: [],
        participantName: null,
      }),
    )
  })
})
