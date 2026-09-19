import { describe, test, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Bill } from '@/pages/customer/Bill'
import { useSessionStore } from '@/store/sessionStore'
import { useAuthStore } from '@/store/authStore'
import { billingService, loyaltyAccountService } from '@/lib/api'

const renderBill = () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Bill />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Bill (customer)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(loyaltyAccountService, 'me').mockRejectedValue(new Error('no loyalty'))
    useAuthStore.setState({ token: 't', role: 'CUSTOMER', userId: 'u1', name: 'Ana' })
    // What a reload restores from localStorage: a bill whose split is stale (UNPAID) because the
    // SPLIT_PAID / SESSION_CLOSED frames arrived while this page had no live subscription.
    useSessionStore.setState({
      id: 'sess-1',
      participants: [{ userId: 'u1', name: 'Ana' }] as never,
      bill: { id: 1, total: 20 } as never,
      billSplits: [{ participantName: 'Ana', amount: 20, status: 'UNPAID' }] as never,
    })
  })

  test('replaces a stale persisted split with the server state, so a paid share is not payable again', async () => {
    vi.spyOn(billingService, 'getBillState').mockResolvedValue({
      id: 1,
      total: 20,
      splits: [{ participantName: 'Ana', amount: 20, status: 'PAID' }],
    } as never)

    renderBill()

    expect(await screen.findByText('Pagado')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Pagar mi parte/ })).not.toBeInTheDocument()
  })

  test('drops a persisted bill the server no longer has', async () => {
    vi.spyOn(billingService, 'getBillState').mockResolvedValue(null)

    renderBill()

    expect(await screen.findByText(/Aún no se ha solicitado la cuenta/)).toBeInTheDocument()
    expect(useSessionStore.getState().bill).toBeUndefined()
  })
})
