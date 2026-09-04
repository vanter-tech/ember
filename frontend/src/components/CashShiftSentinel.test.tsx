import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CashShiftSentinel } from '@/components/CashShiftSentinel'
import { useUIStore } from '@/store/uiStore'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: {
      ...actual.cashShiftService,
      current: vi.fn(),
      detail: vi.fn(),
      close: vi.fn(),
      prolong: vi.fn(),
    },
  }
})

const wrap = (ui: ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('CashShiftSentinel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: null, modalPayload: null })
    // businessDay strictly before today -> STALE alert path
    vi.mocked(cashShiftService.current).mockResolvedValue({
      id: 7,
      businessDay: '2020-01-01',
      overdue: true,
      effectiveDeadline: '2020-01-01T23:59:00Z',
    } as never)
    vi.mocked(cashShiftService.detail).mockResolvedValue({
      movements: [],
      payments: [],
    } as never)
  })

  test('the stale-shift "Cerrar caja" button opens the CloseShiftDialog', async () => {
    wrap(<CashShiftSentinel />)

    const closeButton = await screen.findByRole('button', {
      name: /Cerrar caja del 2020-01-01/,
    })
    fireEvent.click(closeButton)

    // CloseShiftDialog's title ("Arqueo de turno") must now be on screen.
    expect(await screen.findByText('Arqueo de turno')).toBeVisible()
  })
})
