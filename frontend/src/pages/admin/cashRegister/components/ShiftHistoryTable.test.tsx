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

  test('expanding a closed shift shows its denomination breakdown and notes', async () => {
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
      shift: {
        id: 3, shiftNumber: 3, status: 'CLOSED',
        openingBreakdown: [{ denominationId: 'bill_100', quantity: 1 }],
        closingBreakdown: [{ denominationId: 'bill_100', quantity: 2 }],
        closeNotes: 'Todo cuadró',
      },
      movements: [], payments: [],
    } as never)

    wrap(<ShiftHistoryTable />)
    fireEvent.click(await screen.findByText('#3'))

    expect(await screen.findByText('Todo cuadró')).toBeVisible()
    expect(screen.getByText('$100.00 × 1')).toBeVisible()
    expect(screen.getByText('$100.00 × 2')).toBeVisible()
  })
})
