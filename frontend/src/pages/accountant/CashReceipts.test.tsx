import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CashReceipts } from './CashReceipts'
import { cashDrawerService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, cashDrawerService: { current: vi.fn(), receive: vi.fn(), open: vi.fn() } }
})

const wrap = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <CashReceipts />
    </QueryClientProvider>,
  )

describe('CashReceipts view', () => {
  beforeEach(() => vi.clearAllMocks())

  test('has its own title and shows the pending cash payments card', async () => {
    vi.mocked(cashDrawerService.current).mockResolvedValue([
      {
        id: 'e1', type: 'CASH_SALE', status: 'PENDING', tableNumber: 4, amount: 20, reason: null,
        createdAt: '2026-09-29T10:00:00', receivedAt: null, drawer: 'NONE', createdByName: null, drawerError: null,
      },
    ])
    wrap()

    expect(await screen.findByRole('heading', { name: 'Cobros en efectivo' })).toBeVisible()
    expect(await screen.findByRole('button', { name: 'Recibir y abrir caja' })).toBeVisible()
  })
})
