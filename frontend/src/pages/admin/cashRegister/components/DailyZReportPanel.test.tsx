import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DailyZReportPanel } from './DailyZReportPanel'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: { ...actual.cashShiftService, dailyReport: vi.fn(), detail: vi.fn() },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {ui}
    </QueryClientProvider>,
  )

const report = (shifts: unknown[]) => {
  vi.mocked(cashShiftService.detail).mockResolvedValue({ refunds: [], voidedBills: [] } as never)
  vi.mocked(cashShiftService.dailyReport).mockResolvedValue({
    date: '2026-10-02',
    totalCashSales: 0, totalDigitalSales: 0, totalVariance: 0, totalCashIn: 0, totalCashOut: 0,
    shifts,
  } as never)
}

describe('DailyZReportPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('shows what each shift opened with next to what it was expected to hold and counted', async () => {
    report([
      {
        id: 3, shiftNumber: 3, status: 'CLOSED', openedByName: 'Ana', closedByName: 'Ana',
        openingFloat: 150, expectedCash: 400, countedCash: 395, variance: -5,
      },
    ])

    wrap(<DailyZReportPanel />)

    expect(await screen.findByText('Apertura')).toBeVisible()
    expect(screen.getByText('$150.00')).toBeVisible()
    expect(screen.getByText('$400.00')).toBeVisible()
    expect(screen.getByText('$395.00')).toBeVisible()
  })

  test('expanding a shift shows its opening and closing bill breakdown and the close notes', async () => {
    report([
      {
        id: 3, shiftNumber: 3, status: 'CLOSED', openedByName: 'Ana', closedByName: 'Ana',
        openingFloat: 150, expectedCash: 400, countedCash: 395, variance: -5,
        openingBreakdown: [{ denominationId: 'bill_100', quantity: 1 }],
        closingBreakdown: [{ denominationId: 'bill_100', quantity: 3 }],
        closeNotes: 'Faltaron C$5',
      },
    ])

    wrap(<DailyZReportPanel />)
    fireEvent.click(await screen.findByText('#3'))

    expect(await screen.findByText('Desglose de apertura')).toBeVisible()
    expect(screen.getByText('Desglose de cierre')).toBeVisible()
    expect(screen.getByText('$100.00 × 1')).toBeVisible()
    expect(screen.getByText('$100.00 × 3')).toBeVisible()
    expect(screen.getByText('Faltaron C$5')).toBeVisible()
  })

  test('a shift with no recorded breakdown says so instead of expanding to a blank row', async () => {
    report([
      { id: 4, shiftNumber: 4, status: 'CLOSED', openedByName: 'Ana', closedByName: 'Ana', openingFloat: 100 },
    ])

    wrap(<DailyZReportPanel />)
    fireEvent.click(await screen.findByText('#4'))

    expect(await screen.findByText(/no registró desglose/)).toBeVisible()
  })
})
