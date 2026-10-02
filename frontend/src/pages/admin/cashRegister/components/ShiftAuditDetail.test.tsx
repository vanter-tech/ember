import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ShiftAuditDetail } from './ShiftAuditDetail'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, cashShiftService: { ...actual.cashShiftService, detail: vi.fn() } }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      {ui}
    </QueryClientProvider>,
  )

const detail = (extra: object) =>
  vi.mocked(cashShiftService.detail).mockResolvedValue({
    shift: { id: 3 },
    movements: [],
    payments: [],
    ...extra,
  } as never)

describe('ShiftAuditDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('shows a placeholder, with no real text, while the shift detail loads', () => {
    vi.mocked(cashShiftService.detail).mockReturnValue(new Promise(() => {}))

    wrap(<ShiftAuditDetail shiftId={3} />)

    expect(screen.getByTestId('skeleton-shift-audit')).toBeInTheDocument()
    expect(screen.queryByText('Reembolsos')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Cargando auditoría del turno')
  })

  test('lists each refund with its reason, who issued it and when', async () => {
    detail({
      refunds: [
        {
          id: 5, paymentId: 20, billId: 7, billCode: 'ELPO-000007', participantName: 'Alice',
          amount: 2, reason: 'Comida fría', refundedByName: 'Carla', createdAt: '2026-10-02T14:30:00',
        },
      ],
      voidedBills: [],
    })

    wrap(<ShiftAuditDetail shiftId={3} />)

    expect(await screen.findByText('ELPO-000007')).toBeVisible()
    expect(screen.getByText('Comida fría')).toBeVisible()
    expect(screen.getByText('Carla')).toBeVisible()
    expect(screen.getByText('$2.00')).toBeVisible()
    expect(screen.getByText('02/10/2026, 14:30')).toBeVisible()
    expect(screen.getByText('Sin cuentas anuladas en este turno.')).toBeVisible()
  })

  test('lists each voided bill with its table, reason, who voided it and when', async () => {
    detail({
      refunds: [],
      voidedBills: [
        {
          id: 9, billCode: 'ELPO-000009', total: 30, tableNumber: 5, tableLabel: 'M5+M6',
          voidReason: 'Mesa equivocada', voidedByName: 'Ana', voidedAt: '2026-10-02T15:10:00',
        },
      ],
    })

    wrap(<ShiftAuditDetail shiftId={3} />)

    expect(await screen.findByText('ELPO-000009')).toBeVisible()
    expect(screen.getByText('M5+M6')).toBeVisible()
    expect(screen.getByText('Mesa equivocada')).toBeVisible()
    expect(screen.getByText('Ana')).toBeVisible()
    expect(screen.getByText('$30.00')).toBeVisible()
    expect(screen.getByText('Sin reembolsos en este turno.')).toBeVisible()
  })

  test('says so when the shift had neither refunds nor voids', async () => {
    detail({ refunds: [], voidedBills: [] })

    wrap(<ShiftAuditDetail shiftId={3} />)

    expect(await screen.findByText('Sin reembolsos en este turno.')).toBeVisible()
    expect(screen.getByText('Sin cuentas anuladas en este turno.')).toBeVisible()
  })

  test('a legacy bill without a code shows its raw id', async () => {
    detail({
      refunds: [],
      voidedBills: [{ id: 12, total: 5, voidReason: 'x', voidedByName: 'Ana', voidedAt: '2026-10-02T15:10:00' }],
    })

    wrap(<ShiftAuditDetail shiftId={3} />)

    expect(await screen.findByText('#12')).toBeVisible()
  })
})
