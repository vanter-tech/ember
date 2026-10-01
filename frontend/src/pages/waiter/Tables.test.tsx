import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Tables } from './Tables'
import { DashboardService, SessionTableService, cashShiftService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    DashboardService: { ...actual.DashboardService, getDashboardData: vi.fn() },
    cashShiftService: { ...actual.cashShiftService, current: vi.fn() },
    // Selecting an occupied table loads its session; an unmocked call goes over the real network and,
    // when a dev backend is running on localhost, its 401 would log the test user out mid-test.
    SessionTableService: {
      ...actual.SessionTableService,
      sessionInformation: vi.fn(() => Promise.resolve({ items: [] })),
      linkTable: vi.fn(),
      unlinkTable: vi.fn(),
    },
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

describe('Tables with merged tables', () => {
  const merged = [
    {
      tableId: 't3', tableNumber: 3, isOccupied: true,
      currentSession: { sessionId: 'sess-3', waiterName: 'Fe', currentParticipant: 4 },
      linkedTables: [{ tableId: 't4', tableNumber: 4 }],
    },
    {
      tableId: 't4', tableNumber: 4, isOccupied: true,
      currentSession: { sessionId: 'sess-3', waiterName: 'Fe', currentParticipant: 4 },
      linkedToTableId: 't3', linkedToTableNumber: 3, linkedTables: [],
    },
    { tableId: 't5', tableNumber: 5, isOccupied: false, linkedTables: [] },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ restaurantId: 'restaurant-1', role: 'WAITER' })
    vi.mocked(cashShiftService.current).mockResolvedValue({ status: 'OPEN' } as never)
    vi.mocked(DashboardService.getDashboardData).mockResolvedValue(merged as never)
  })

  test('merged tables are ONE wide card labelled "M3 + M4"; the linked table has no cell of its own', async () => {
    wrap()
    const card = (await screen.findByText('M3 + M4')).closest('[data-slot="card"]')!

    expect(card).toHaveClass('col-span-2')
    expect(screen.queryByText('M4')).not.toBeInTheDocument()
    expect(screen.queryByText('Unida a M3')).not.toBeInTheDocument()
  })

  test('a linked table whose primary is not on the floor still shows on its own as "Unida a M3"', async () => {
    vi.mocked(DashboardService.getDashboardData).mockResolvedValue([merged[1]] as never)
    wrap()
    fireEvent.click(await screen.findByText('M4'))

    // Once on its card and once in the detail panel.
    expect(await screen.findAllByText('Unida a M3')).toHaveLength(2)
    expect(screen.queryByRole('button', { name: 'Unir mesa' })).not.toBeInTheDocument()
  })

  test('the primary offers "Unir mesa" and lists its linked table with a Separar action', async () => {
    vi.mocked(SessionTableService.unlinkTable).mockResolvedValue(undefined)
    wrap()
    fireEvent.click(await screen.findByText('M3 + M4'))

    expect(await screen.findByRole('button', { name: 'Unir mesa' })).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Separar M4' }))
    await waitFor(() => expect(SessionTableService.unlinkTable).toHaveBeenCalledWith('sess-3', 't4'))
  })

  test('a free table has no "Unir mesa" button', async () => {
    wrap()
    fireEvent.click(await screen.findByText('M5'))

    expect(screen.queryByRole('button', { name: 'Unir mesa' })).not.toBeInTheDocument()
  })

  test('only free tables are draggable; a merged card is not', async () => {
    wrap()
    await screen.findByText('M5')

    expect(screen.getByText('M5').closest('[aria-roledescription="draggable"]')).not.toBeNull()
    expect(screen.getByText('M3 + M4').closest('[aria-roledescription="draggable"]')).toBeNull()
  })

  test('shows the long-press drag hint', async () => {
    wrap()
    expect(
      await screen.findByText('Mantén presionada una mesa libre y arrástrala sobre una ocupada para unirla.'),
    ).toBeVisible()
  })

  // The link endpoints are WAITER-only (no role hierarchy), so an ADMIN browsing the floor must not
  // be offered a merge that the server would answer with 403.
  test('an ADMIN is not offered to link or unlink tables, nor to drag them', async () => {
    useAuthStore.setState({ restaurantId: 'restaurant-1', role: 'ADMIN' })
    wrap()
    fireEvent.click(await screen.findByText('M3 + M4'))

    expect(await screen.findByRole('link', { name: 'Ver Informacion' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Unir mesa' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Separar M4' })).not.toBeInTheDocument()
    expect(screen.getByText('M5').closest('[aria-roledescription="draggable"]')).toBeNull()
  })

  test('the linked tables are listed ABOVE the "Unir mesa" button, and "Separar" is a full-size button', async () => {
    wrap()
    fireEvent.click(await screen.findByText('M3 + M4'))

    const heading = await screen.findByText('Mesas unidas')
    const linkButton = screen.getByRole('button', { name: 'Unir mesa' })
    expect(heading.compareDocumentPosition(linkButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    const unlink = screen.getByRole('button', { name: 'Separar M4' })
    expect(unlink).toHaveClass('h-10')
    expect(unlink).not.toHaveClass('h-7')
  })

  test('picking a free table in the modal links it to the open session', async () => {
    vi.mocked(SessionTableService.linkTable).mockResolvedValue(undefined)
    wrap()
    fireEvent.click(await screen.findByText('M3 + M4'))
    fireEvent.click(await screen.findByRole('button', { name: 'Unir mesa' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Mesa 5' }))
    fireEvent.click(screen.getByRole('button', { name: 'Unir' }))

    await waitFor(() => expect(SessionTableService.linkTable).toHaveBeenCalledWith('sess-3', 't5'))
  })
})
