import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ShiftHistoryTable } from './ShiftHistoryTable'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: {
      ...actual.cashShiftService,
      history: vi.fn(),
      detail: vi.fn(),
    },
  }
})

const wrap = (ui: ReactNode, qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
  render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)

describe('ShiftHistoryTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('expanding a closed shift shows a payments table with the table number, not the participant name', async () => {
    vi.mocked(cashShiftService.history).mockResolvedValue({
      content: [
        {
          id: 3, shiftNumber: 3, status: 'CLOSED', openedByName: 'Ana', closedByName: 'Ana',
          expectedCash: 200, countedCash: 200, variance: 0,
        },
      ],
      totalPages: 1,
    } as never)
    vi.mocked(cashShiftService.detail).mockResolvedValue({
      shift: { id: 3, shiftNumber: 3, status: 'CLOSED' },
      movements: [],
      payments: [
        {
          id: 1, tableNumber: 5, amount: 100, method: 'PHYSICAL', status: 'CONFIRMED',
          remaining: 100, refundedAmount: 0, participantName: 'Carla',
        },
      ],
    } as never)

    wrap(<ShiftHistoryTable />)
    fireEvent.click(await screen.findByText('#3'))

    expect(await screen.findByText('#5')).toBeVisible()
    expect(screen.queryByText('Carla')).not.toBeInTheDocument()
    expect(screen.getByText('$100.00')).toBeVisible()
    expect(screen.getByText('Efectivo')).toBeVisible()
    expect(screen.getByText('Confirmado')).toBeVisible()
  })
})
