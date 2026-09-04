import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TransferTableModal } from '@/pages/waiter/components/TransferTableModal'
import { useUIStore } from '@/store/uiStore'
import { SessionTableService } from '@/lib/api'

const navigate = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigate }
})

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    SessionTableService: {
      ...actual.SessionTableService,
      listWaiters: vi.fn(),
      transferTable: vi.fn(),
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

describe('TransferTableModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({
      activeModal: 'TRANSFER_TABLE',
      modalPayload: { sessionId: 's1', currentWaiterEmail: 'me@x.com' },
    })
    vi.mocked(SessionTableService.listWaiters).mockResolvedValue([
      { id: 'u1', name: 'Ana', email: 'ana@x.com' },
      { id: 'u2', name: 'Yo', email: 'me@x.com' },
    ])
  })

  test('excludes the current waiter from the list', async () => {
    wrap(<TransferTableModal />)
    expect(await screen.findByText('Ana')).toBeVisible()
    expect(screen.queryByText('Yo')).not.toBeInTheDocument()
  })

  test('submitting transfers and navigates to /waiter/tables', async () => {
    vi.mocked(SessionTableService.transferTable).mockResolvedValue(undefined)
    wrap(<TransferTableModal />)
    fireEvent.click(await screen.findByText('Ana'))
    fireEvent.click(screen.getByRole('button', { name: 'Transferir' }))
    await waitFor(() =>
      expect(SessionTableService.transferTable).toHaveBeenCalledWith('s1', 'u1'),
    )
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/waiter/tables'))
  })
})
