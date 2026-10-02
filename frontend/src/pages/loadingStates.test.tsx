import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { platformApi } from '@/lib/platformApi'
import { useSessionStore } from '@/store/sessionStore'
import { Staff } from './admin/staff/Staff'
import { CashRegister } from './accountant/cashRegister/CashRegister'
import { CashRegister as AdminCashRegister } from './admin/cashRegister/CashRegister'
import { CashReceipts } from './accountant/CashReceipts'
import { ShiftHistoryTable } from './admin/cashRegister/components/ShiftHistoryTable'
import { DailyZReportPanel } from './admin/cashRegister/components/DailyZReportPanel'
import { OrdersDisplays } from './kitchen/OrdersDisplay'
import { Menu } from './customer/Menu'
import ConsoleRestaurantDetail from './console/ConsoleRestaurantDetail'

const renderView = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )

describe('Other views while their data is loading', () => {
  beforeEach(() => {
    vi.spyOn(api, 'get').mockReturnValue(new Promise(() => {}) as never)
  })
  afterEach(() => vi.restoreAllMocks())

  test('Staff mirrors its page: header, 5 filter pills, member cards and 3 KPI cards, no real text', () => {
    renderView(<Staff />)

    expect(screen.getByTestId('skeleton-page-header')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Todos' })).not.toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-pill')).toHaveLength(5)
    const cards = screen.getAllByTestId('skeleton-staff-card')
    expect(cards.length).toBeGreaterThanOrEqual(8)
    expect(screen.getAllByTestId('skeleton-staff-avatar')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-staff-actions')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-kpi')).toHaveLength(3)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando personal...')
  })

  test('the accountant cash register mirrors its page: header, shift card with 4 stat tiles, movements and payments tables', () => {
    renderView(<CashRegister />)

    expect(screen.getByTestId('skeleton-page-header')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument() // no real text while loading
    expect(screen.getByTestId('skeleton-shift-card')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-stat-tile')).toHaveLength(4)
    expect(screen.getAllByTestId('skeleton-table')).toHaveLength(2)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando caja...')
  })

  test('the cash receipts view shows placeholders, never "0", "$0.00" or "no pending" while loading', () => {
    renderView(<CashReceipts />)

    expect(screen.getByTestId('skeleton-page-header')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Cobros en efectivo' })).not.toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-summary-tile')).toHaveLength(2)
    expect(screen.getAllByTestId('skeleton-receipt-card').length).toBeGreaterThanOrEqual(2)
    expect(screen.queryByText('No hay cobros pendientes.')).not.toBeInTheDocument()
    expect(screen.queryByText('Cobros pendientes')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Cargando cobros en efectivo...')
  })

  test('the admin cash register is all blocks on first load: header, manual-open button, sidebar and the history table', () => {
    renderView(<AdminCashRegister />)

    expect(screen.getByTestId('skeleton-page-header')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByTestId('skeleton-manual-open')).toBeInTheDocument()
    expect(screen.getByTestId('skeleton-cash-sidebar')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-table')).toHaveLength(1)
    expect(screen.getAllByTestId('skeleton-table-row').length).toBeGreaterThanOrEqual(8)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando turnos...')
  })

  test('ShiftHistoryTable alone (page change) shows a table of placeholders in its card', () => {
    renderView(<ShiftHistoryTable />)

    expect(screen.getAllByTestId('skeleton-table')).toHaveLength(1)
    expect(screen.getAllByTestId('skeleton-table-row').length).toBeGreaterThanOrEqual(8)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando turnos...')
  })

  test('DailyZReportPanel keeps the real date picker; the 5 figures and the shifts table are placeholders', () => {
    const { container } = renderView(<DailyZReportPanel />)

    expect(container.querySelector('input[type="date"]')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-card')).toHaveLength(5)
    expect(screen.getAllByTestId('skeleton-table')).toHaveLength(1)
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('the customer menu shows dish-card placeholders once past the welcome step', () => {
    useSessionStore.setState({ hasSeenMenuWelcome: true })
    renderView(<Menu />)

    expect(screen.getAllByTestId('skeleton-card').length).toBeGreaterThan(0)
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('the console restaurant detail shows a header and metric placeholders', () => {
    vi.spyOn(platformApi, 'get').mockReturnValue(new Promise(() => {}) as never)
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/console/restaurants/r1']}>
          <Routes>
            <Route path="/console/restaurants/:id" element={<ConsoleRestaurantDetail />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(screen.getAllByTestId('skeleton-card')).toHaveLength(3)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando restaurante...')
  })

  test('the kitchen display keeps its header and mirrors the real board: small queue cards + the focused card with its 3 status columns', () => {
    renderView(<OrdersDisplays />)

    // the header of the real screen is there from the first frame, as placeholder blocks (no text)
    expect(screen.getByTestId('skeleton-kds-header')).toBeInTheDocument()
    expect(screen.queryByText('Ember')).not.toBeInTheDocument()
    expect(screen.queryByText('Monitor de cocina - KDS')).not.toBeInTheDocument()
    // small order cards in a row + the big focused card below
    expect(screen.getAllByTestId('skeleton-queue-card').length).toBeGreaterThanOrEqual(2)
    expect(screen.getAllByTestId('skeleton-focused-card')).toHaveLength(1)
    // the focused card has its three status columns (pending / preparing / ready)
    expect(screen.getAllByTestId('skeleton-kds-column')).toHaveLength(3)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando pedidos...')
  })
})
