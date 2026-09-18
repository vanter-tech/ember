import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { OpenShiftDialog } from './OpenShiftDialog'
import { useUIStore } from '@/store/uiStore'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: { ...actual.cashShiftService, open: vi.fn().mockResolvedValue({ id: 1 }) },
  }
})

const wrap = (ui: ReactNode, qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
  render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)

describe('OpenShiftDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'OPEN_SHIFT', modalPayload: undefined })
  })

  test('submits the counted breakdown and total, not a typed amount', async () => {
    wrap(<OpenShiftDialog />)

    fireEvent.change(screen.getByLabelText('$100.00'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Abrir caja' }))

    await waitFor(() =>
      expect(cashShiftService.open).toHaveBeenCalledWith(
        200,
        [{ denominationId: 'bill_100', quantity: 2 }],
      ),
    )
  })
})
