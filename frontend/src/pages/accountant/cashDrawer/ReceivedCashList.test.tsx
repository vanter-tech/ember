import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ReceivedCashList } from './ReceivedCashList'
import { cashDrawerService, type CashDrawerEvent } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, cashDrawerService: { current: vi.fn(), receive: vi.fn(), open: vi.fn(), skip: vi.fn() } }
})

const event = (over: Partial<CashDrawerEvent>): CashDrawerEvent => ({
  id: 'e1', type: 'CASH_SALE', status: 'RECEIVED', tableNumber: 4, amount: 20, reason: null,
  createdAt: '2026-09-29T10:00:00', receivedAt: '2026-09-29T10:01:00', drawer: 'OPENED',
  createdByName: 'Alice', drawerError: null, ...over,
})

const wrap = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ReceivedCashList />
    </QueryClientProvider>,
  )

describe('ReceivedCashList', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows a failed drawer as a warning with the cause, retry and skip', async () => {
    vi.mocked(cashDrawerService.current).mockResolvedValue([
      event({ drawer: 'FAILED', drawerError: 'El agente de impresión no está conectado' }),
    ])
    wrap()
    expect(await screen.findByText('El agente de impresión no está conectado')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Reintentar apertura' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Omitir' })).toBeVisible()
  })

  test('falls back to a generic cause when the failure has no recorded error', async () => {
    vi.mocked(cashDrawerService.current).mockResolvedValue([event({ drawer: 'FAILED' })])
    wrap()
    expect(await screen.findByText(/Sin respuesta del agente/)).toBeVisible()
  })

  test('retry receives again and skip dismisses', async () => {
    vi.mocked(cashDrawerService.current).mockResolvedValue([event({ drawer: 'FAILED' })])
    vi.mocked(cashDrawerService.receive).mockResolvedValue(event({ drawer: 'OPENING' }))
    vi.mocked(cashDrawerService.skip).mockResolvedValue(event({ drawer: 'SKIPPED' }))
    wrap()
    fireEvent.click(await screen.findByRole('button', { name: 'Reintentar apertura' }))
    await waitFor(() => expect(cashDrawerService.receive).toHaveBeenCalledWith('e1'))
    fireEvent.click(screen.getByRole('button', { name: 'Omitir' }))
    await waitFor(() => expect(cashDrawerService.skip).toHaveBeenCalledWith('e1'))
  })

  test('a skipped or opened drawer is a plain received card with no warning', async () => {
    vi.mocked(cashDrawerService.current).mockResolvedValue([
      event({ id: 'a', drawer: 'OPENED' }),
      event({ id: 'b', drawer: 'SKIPPED', tableNumber: 5 }),
    ])
    wrap()
    expect(await screen.findByText('Mesa 5')).toBeVisible()
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.queryByText('Requieren atención')).toBeNull()
  })
})
