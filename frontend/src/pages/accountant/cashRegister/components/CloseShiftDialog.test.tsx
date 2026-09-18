import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CloseShiftDialog } from './CloseShiftDialog'
import { useUIStore } from '@/store/uiStore'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: {
      ...actual.cashShiftService,
      close: vi.fn().mockResolvedValue({ expectedCash: 200, countedCash: 200, variance: 0 }),
    },
  }
})

const wrap = (ui: ReactNode, qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
  render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)

describe('CloseShiftDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'CLOSE_SHIFT', modalPayload: { shiftId: 7 } })
  })

  test('submits the counted breakdown, total, and notes', async () => {
    wrap(<CloseShiftDialog />)

    fireEvent.change(screen.getByLabelText('$100.00'), { target: { value: '2' } })
    fireEvent.change(screen.getByPlaceholderText('Explica la diferencia, si la hay'), {
      target: { value: 'todo cuadró' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar conteo' }))

    await waitFor(() =>
      expect(cashShiftService.close).toHaveBeenCalledWith(
        7,
        200,
        [{ denominationId: 'bill_100', quantity: 2 }],
        'todo cuadró',
      ),
    )
  })

  test('notes are optional — an empty notes field submits undefined, not an empty string', async () => {
    wrap(<CloseShiftDialog />)

    fireEvent.change(screen.getByLabelText('$50.00'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar conteo' }))

    await waitFor(() =>
      expect(cashShiftService.close).toHaveBeenCalledWith(7, 50, [{ denominationId: 'bill_50', quantity: 1 }], undefined),
    )
  })
})
