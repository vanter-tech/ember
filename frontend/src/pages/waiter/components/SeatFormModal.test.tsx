import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SeatFormModal } from '@/pages/waiter/components/SeatFormModal'
import { useUIStore } from '@/store/uiStore'
import { SessionTableService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    SessionTableService: {
      ...actual.SessionTableService,
      addSeat: vi.fn(),
      renameSeat: vi.fn(),
      removeSeat: vi.fn(),
    },
  }
})

const wrap = (ui: ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('SeatFormModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(SessionTableService.addSeat).mockResolvedValue({} as never)
    vi.mocked(SessionTableService.renameSeat).mockResolvedValue({} as never)
    vi.mocked(SessionTableService.removeSeat).mockResolvedValue({} as never)
  })

  test('add mode posts addSeat with the typed name', async () => {
    useUIStore.setState({ activeModal: 'SEAT_FORM', modalPayload: { sessionId: 's1', mode: 'add' } })
    wrap(<SeatFormModal />)

    fireEvent.change(screen.getByLabelText(/nombre del asiento|seat name/i), {
      target: { value: 'Ana' },
    })
    fireEvent.click(screen.getByRole('button', { name: /agregar asiento|add seat/i }))

    await waitFor(() => expect(SessionTableService.addSeat).toHaveBeenCalledWith('s1', 'Ana'))
  })

  test('rename mode posts renameSeat with the original name', async () => {
    useUIStore.setState({
      activeModal: 'SEAT_FORM',
      modalPayload: { sessionId: 's1', mode: 'rename', from: 'Asiento 2' },
    })
    wrap(<SeatFormModal />)

    fireEvent.change(screen.getByLabelText(/nombre del asiento|seat name/i), {
      target: { value: 'Beto' },
    })
    fireEvent.click(screen.getByRole('button', { name: /guardar|save/i }))

    await waitFor(() =>
      expect(SessionTableService.renameSeat).toHaveBeenCalledWith('s1', 'Asiento 2', 'Beto'),
    )
  })

  test('delete confirm posts removeSeat', async () => {
    useUIStore.setState({
      activeModal: 'DELETE_SEAT',
      modalPayload: { sessionId: 's1', name: 'Asiento 3' },
    })
    wrap(<SeatFormModal />)

    fireEvent.click(screen.getByRole('button', { name: /quitar|remove/i }))

    await waitFor(() =>
      expect(SessionTableService.removeSeat).toHaveBeenCalledWith('s1', 'Asiento 3'),
    )
  })
})
