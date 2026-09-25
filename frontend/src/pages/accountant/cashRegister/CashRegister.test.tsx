import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CashRegister } from './CashRegister'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: { ...actual.cashShiftService, current: vi.fn(), detail: vi.fn() },
  }
})

vi.mock('@/components/tours/SectionTour', () => ({ SectionTour: () => null }))

const wrap = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <CashRegister />
    </QueryClientProvider>,
  )

describe('accountant CashRegister summary', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows cash/digital sales and the expected cash computed from the shift detail', async () => {
    vi.mocked(cashShiftService.current).mockResolvedValue({
      id: 1,
      shiftNumber: 3,
      status: 'OPEN',
      openingFloat: 100,
      openedByName: 'Ana',
      openedAt: '2026-09-24T09:00:00',
    } as never)
    vi.mocked(cashShiftService.detail).mockResolvedValue({
      movements: [
        { id: 1, type: 'CASH_IN', amount: 20, reason: 'cambio', createdByName: 'Ana' },
        { id: 2, type: 'CASH_OUT', amount: 5, reason: 'hielo', createdByName: 'Ana' },
      ],
      payments: [
        { id: 1, method: 'PHYSICAL', status: 'CONFIRMED', amount: 50, participantName: 'Luis', tableNumber: 4 },
        { id: 2, method: 'DIGITAL', status: 'CONFIRMED', amount: 30, participantName: 'Eva', tableNumber: 5 },
        { id: 3, method: 'PHYSICAL', status: 'PENDING', amount: 999, participantName: 'Pending' },
      ],
    } as never)
    wrap()

    // 100 float + 50 confirmed cash + 20 in - 5 out (the PENDING payment is ignored)
    expect(await screen.findByText('$165.00')).toBeVisible()
    expect(screen.getByText('Efectivo esperado en caja')).toBeVisible()
    expect(screen.getAllByText('$30.00').length).toBeGreaterThan(0)
  })
})
