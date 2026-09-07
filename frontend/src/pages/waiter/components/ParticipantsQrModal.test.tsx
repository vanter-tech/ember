import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ParticipantQrModal } from '@/pages/waiter/components/ParticipantsQrModal'
import { useUIStore } from '@/store/uiStore'
import { SessionTableService } from '@/lib/api'

const navigate = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigate }
})

const { hubFlag } = vi.hoisted(() => ({ hubFlag: { current: true } }))
vi.mock('@/lib/isHubBuild', () => ({
  get isHubBuild() {
    return hubFlag.current
  },
}))

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    SessionTableService: {
      ...actual.SessionTableService,
      createSession: vi.fn(),
      getQrToken: vi.fn(),
    },
  }
})

const wrap = (ui: ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ParticipantQrModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hubFlag.current = true
    useUIStore.setState({ activeModal: 'PARTICIPANTS_QR', modalPayload: { tableId: 't1' } })
    vi.mocked(SessionTableService.createSession).mockResolvedValue({
      sessionId: 's1',
      joinCode: 'ABCDE',
    })
    vi.mocked(SessionTableService.getQrToken).mockResolvedValue({ qrToken: 'qr-xyz' })
  })

  test('Hub build: collects seat names and opens the table without a QR', async () => {
    wrap(<ParticipantQrModal />)

    fireEvent.click(screen.getByRole('button', { name: 'Agregar asiento' }))
    const inputs = screen.getAllByPlaceholderText(/Asiento \d/)
    expect(inputs).toHaveLength(2)
    fireEvent.change(inputs[0], { target: { value: 'Ana' } })

    fireEvent.click(screen.getByRole('button', { name: 'Abrir mesa' }))

    await waitFor(() =>
      expect(SessionTableService.createSession).toHaveBeenCalledWith('t1', 2, ['Ana', '']),
    )
    expect(SessionTableService.getQrToken).not.toHaveBeenCalled()
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/waiter/tables/s1'))
  })

  test('cloud build: still generates a QR, no seat inputs', async () => {
    hubFlag.current = false
    wrap(<ParticipantQrModal />)

    expect(screen.queryByPlaceholderText(/Asiento \d/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Abrir Mesa y Generar QR' }))

    await waitFor(() => expect(SessionTableService.createSession).toHaveBeenCalledWith('t1', 1))
    await waitFor(() => expect(SessionTableService.getQrToken).toHaveBeenCalledWith('s1'))
    expect(navigate).not.toHaveBeenCalled()
  })
})
