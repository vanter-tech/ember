import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CashRegister } from './CashRegister'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: { ...actual.cashShiftService, current: vi.fn(), detail: vi.fn(), prolong: vi.fn() },
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

describe('accountant CashRegister — prolong the shift by a chosen time', () => {
  const openShift = (over: Record<string, unknown> = {}) => ({
    id: 1, shiftNumber: 3, status: 'OPEN', openingFloat: 100, openedByName: 'Ana',
    openedAt: '2026-09-24T09:00:00', ...over,
  })

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(cashShiftService.detail).mockResolvedValue({ movements: [], payments: [] } as never)
    vi.mocked(cashShiftService.prolong).mockResolvedValue(openShift() as never)
  })

  test('prolongs by the default hour when the selection is not changed', async () => {
    vi.mocked(cashShiftService.current).mockResolvedValue(openShift() as never)
    wrap()

    fireEvent.click(await screen.findByRole('button', { name: 'Prolongar' }))

    await waitFor(() => expect(cashShiftService.prolong).toHaveBeenCalledWith(1, 60))
  })

  test('prolongs by the time the accountant picks', async () => {
    vi.mocked(cashShiftService.current).mockResolvedValue(openShift() as never)
    wrap()

    fireEvent.change(await screen.findByLabelText('Tiempo a prolongar'), { target: { value: '180' } })
    fireEvent.click(screen.getByRole('button', { name: 'Prolongar' }))

    await waitFor(() => expect(cashShiftService.prolong).toHaveBeenCalledWith(1, 180))
  })

  test('offers exactly the fixed durations', async () => {
    vi.mocked(cashShiftService.current).mockResolvedValue(openShift() as never)
    wrap()

    const select = (await screen.findByLabelText('Tiempo a prolongar')) as HTMLSelectElement
    expect(Array.from(select.options).map((o) => o.value)).toEqual(['30', '60', '120', '180', '240'])
  })

  test('a shift from a previous business day cannot be prolonged, only closed', async () => {
    vi.mocked(cashShiftService.current).mockResolvedValue(openShift({ businessDay: '2020-01-01' }) as never)
    wrap()

    expect(await screen.findByRole('button', { name: 'Prolongar' })).toBeDisabled()
    expect(screen.getByText(/de un día anterior/)).toBeVisible()
    expect(cashShiftService.prolong).not.toHaveBeenCalled()
  })
})

describe('accountant CashRegister — who registered each payment and how often the shift was extended', () => {
  beforeEach(() => vi.clearAllMocks())

  test('names who registered each payment and shows the gateway reference of a digital one', async () => {
    vi.mocked(cashShiftService.current).mockResolvedValue({
      id: 1, shiftNumber: 3, status: 'OPEN', openingFloat: 100, openedByName: 'Ana',
      openedAt: '2026-09-24T09:00:00',
    } as never)
    vi.mocked(cashShiftService.detail).mockResolvedValue({
      movements: [],
      payments: [
        {
          id: 1, method: 'PHYSICAL', status: 'CONFIRMED', amount: 50, participantName: 'Luis',
          processedByName: 'Carla Mesera', tableNumber: 4,
        },
        {
          id: 2, method: 'DIGITAL', status: 'CONFIRMED', amount: 30, participantName: 'Eva',
          processedByName: 'Pedro Mesero', gatewayRef: 'GW-77', tableNumber: 5,
        },
      ],
    } as never)
    wrap()

    expect(await screen.findByText('Carla Mesera')).toBeVisible()
    expect(screen.getByText('Pedro Mesero')).toBeVisible()
    expect(screen.getByText('GW-77')).toBeVisible()
  })

  test('shows how many times the open shift was extended, only when it was', async () => {
    vi.mocked(cashShiftService.detail).mockResolvedValue({ movements: [], payments: [] } as never)
    vi.mocked(cashShiftService.current).mockResolvedValue({
      id: 1, shiftNumber: 3, status: 'OPEN', openingFloat: 100, openedByName: 'Ana',
      openedAt: '2026-09-24T09:00:00', prolongCount: 2,
    } as never)
    wrap()

    expect(await screen.findByText('Prórrogas:')).toBeVisible()
  })

  test('says nothing about extensions for a shift that was never extended', async () => {
    vi.mocked(cashShiftService.detail).mockResolvedValue({ movements: [], payments: [] } as never)
    vi.mocked(cashShiftService.current).mockResolvedValue({
      id: 1, shiftNumber: 3, status: 'OPEN', openingFloat: 100, openedByName: 'Ana',
      openedAt: '2026-09-24T09:00:00', prolongCount: 0,
    } as never)
    wrap()

    await screen.findByText('Apertura:')
    expect(screen.queryByText('Prórrogas:')).not.toBeInTheDocument()
  })
})
