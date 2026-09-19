import { describe, test, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CustomerLayout } from '@/layouts/CustomerLayout'
import { useSessionStore } from '@/store/sessionStore'
import { useAuthStore } from '@/store/authStore'
import { SessionTableService } from '@/lib/api'

const ws = vi.hoisted(() => ({
  connect: vi.fn(),
  disconnect: vi.fn(),
  subscribeToSession: vi.fn(),
  isConnected: true,
  stompClient: { connected: true } as { connected: boolean } | null,
  lastBillRedistribution: null,
  clearBillRedistribution: vi.fn(),
}))

vi.mock('@/store/websocket', () => ({ useWebsocketStore: () => ws }))
vi.mock('@/components/FloatingNav', () => ({ FloatingNav: () => null }))

const renderLayout = (path = '/customer/menu/sess-1/bill') => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/customer" element={<CustomerLayout />}>
            <Route path="home" element={<div>HOME PAGE</div>} />
            <Route path="menu/:id/bill" element={<div>BILL PAGE</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('CustomerLayout (session realtime + closed-session guard)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.clearAllMocks()
    ws.isConnected = true
    ws.stompClient = { connected: true }
    // A guest never fills a login form: setAuth stored the GuestJoinResponse identity.
    useAuthStore.setState({ token: 't', role: 'CUSTOMER', userId: 'guest-1', name: 'Invitado 42' })
    useSessionStore.setState({ id: 'sess-1' })
  })

  test('subscribes a guest to the session topic on any customer route, not just the menu', async () => {
    vi.spyOn(SessionTableService, 'sessionStatus').mockResolvedValue({ status: 'OPEN' } as never)

    renderLayout()

    expect(await screen.findByText('BILL PAGE')).toBeInTheDocument()
    expect(ws.subscribeToSession).toHaveBeenCalledWith('sess-1')
  })

  test('does not subscribe while the socket is not connected', async () => {
    ws.isConnected = false
    vi.spyOn(SessionTableService, 'sessionStatus').mockResolvedValue({ status: 'OPEN' } as never)

    renderLayout()

    expect(await screen.findByText('BILL PAGE')).toBeInTheDocument()
    expect(ws.subscribeToSession).not.toHaveBeenCalled()
  })

  test('a CLOSED session clears the stored session and leaves the view', async () => {
    vi.spyOn(SessionTableService, 'sessionStatus').mockResolvedValue({ status: 'CLOSED' } as never)

    renderLayout()

    expect(await screen.findByText('HOME PAGE')).toBeInTheDocument()
    expect(useSessionStore.getState().id).toBeUndefined()
  })

  test('a session the server no longer knows also leaves the view', async () => {
    vi.spyOn(SessionTableService, 'sessionStatus').mockRejectedValue(new Error('404'))

    renderLayout()

    expect(await screen.findByText('HOME PAGE')).toBeInTheDocument()
    expect(useSessionStore.getState().id).toBeUndefined()
  })

  test('with no stored session it neither subscribes nor asks the server', async () => {
    useSessionStore.setState({ id: undefined })
    const status = vi.spyOn(SessionTableService, 'sessionStatus')

    renderLayout()

    expect(await screen.findByText('BILL PAGE')).toBeInTheDocument()
    await waitFor(() => expect(ws.subscribeToSession).not.toHaveBeenCalled())
    expect(status).not.toHaveBeenCalled()
  })
})
