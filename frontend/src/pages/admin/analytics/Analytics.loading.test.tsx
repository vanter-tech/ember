import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { Analytics } from './Analytics'
import { SummaryCards } from './components/SummaryCards'
import { SalesChart } from './components/SalesChart'
import { ProductPerformance } from './components/ProductPerformance'
import { TableAnalytics } from './components/TableAnalytics'

vi.mock('@/components/tours/SectionTour', () => ({ SectionTour: () => null }))

const renderView = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )

describe('Analytics blocks while their data is loading', () => {
  beforeEach(() => {
    vi.spyOn(api, 'get').mockReturnValue(new Promise(() => {}) as never)
  })
  afterEach(() => vi.restoreAllMocks())

  test('SummaryCards: four metric cards (icon + label + value as blocks), no real text', () => {
    renderView(<SummaryCards />)

    expect(screen.getAllByTestId('skeleton-card')).toHaveLength(4)
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('SalesChart: the whole card is placeholders — title, the 4 period pills and the chart', () => {
    renderView(<SalesChart />)

    expect(screen.getByTestId('skeleton-chart')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-pill')).toHaveLength(4)
    expect(screen.getByTestId('skeleton-card-title')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('ProductPerformance: both cards (top products and by category), each with block title and rows', () => {
    renderView(<ProductPerformance />)

    expect(screen.getAllByTestId('skeleton-card-title')).toHaveLength(2)
    expect(screen.getAllByTestId('skeleton-bar-row').length).toBeGreaterThanOrEqual(5)
    expect(screen.getAllByTestId('skeleton-category-row').length).toBeGreaterThanOrEqual(3)
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('TableAnalytics: block title, four headline figures and ranking rows', () => {
    renderView(<TableAnalytics />)

    expect(screen.getByTestId('skeleton-card-title')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-stat')).toHaveLength(4)
    expect(screen.getAllByTestId('skeleton-bar-row').length).toBeGreaterThan(0)
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('the Analytics page shows its title as a block and no real text of any block', () => {
    renderView(<Analytics />)

    expect(screen.getByTestId('skeleton-page-header')).toBeInTheDocument()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-card-title').length).toBeGreaterThanOrEqual(4)
    expect(screen.getAllByRole('status').length).toBeGreaterThanOrEqual(4)
  })
})

describe('SalesChart when the period changes after the first load', () => {
  afterEach(() => vi.restoreAllMocks())

  test('keeps the real period buttons (it is a reload, not the first load)', async () => {
    vi.spyOn(api, 'get').mockImplementation(((url: string, config?: { params?: { granularity?: string } }) =>
      url.includes('/sales') && config?.params?.granularity !== 'day'
        ? new Promise(() => {})
        : Promise.resolve({ data: { buckets: [] } })) as never)
    renderView(<SalesChart />)

    await userEvent.click(await screen.findByRole('button', { name: 'Semana' }))

    await waitFor(() => expect(screen.getByTestId('skeleton-chart')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Semana' })).toBeInTheDocument()
    expect(screen.queryAllByTestId('skeleton-pill')).toHaveLength(0)
  })
})

describe('Analytics fields beyond revenue', () => {
  afterEach(() => vi.restoreAllMocks())

  const mockApi = (data: Record<string, unknown>) =>
    vi.spyOn(api, 'get').mockImplementation(((url: string) => {
      const key = Object.keys(data).find((k) => url.includes(k))
      return Promise.resolve({ data: key ? data[key] : {} })
    }) as never)

  test('SummaryCards shows how many bills were paid', async () => {
    mockApi({ '/summary': { totalRevenue: 500, activeSessions: 2, averageOrderValue: 25, paidBillCount: 20 } })
    renderView(<SummaryCards />)

    expect(await screen.findByText('Cuentas pagadas')).toBeVisible()
    expect(screen.getByText('20')).toBeVisible()
    expect(screen.queryAllByTestId('skeleton-card')).toHaveLength(0)
  })

  test('SalesChart states the paid bill count of the period under its title', async () => {
    mockApi({ '/sales': { granularity: 'day', paidBillCount: 42, buckets: [] } })
    renderView(<SalesChart />)

    expect(await screen.findByText('42 cuentas pagadas en el período')).toBeVisible()
  })

  test('ProductPerformance states how many products and units were sold', async () => {
    mockApi({ '/products': { productCount: 9, totalQuantity: 130, products: [], categories: [] } })
    renderView(<ProductPerformance />)

    expect(await screen.findByText('9 productos · 130 unidades vendidas')).toBeVisible()
  })

  test('TableAnalytics shows the total number of turnovers', async () => {
    mockApi({
      '/tables': {
        activeTableCount: 5, totalTurnovers: 37, totalRevenue: 150, averageTurnoverRate: 0.4,
        averageSessionDurationMinutes: 30, tables: [],
      },
    })
    renderView(<TableAnalytics />)

    expect(await screen.findByText('Rotaciones totales')).toBeVisible()
    expect(screen.getByText('37')).toBeVisible()
  })

  test('the page header shows the span of data and how many bills were issued', async () => {
    mockApi({
      '/range': { firstBillAt: '2026-09-01T10:00:00', lastBillAt: '2026-10-02T21:30:00', billCount: 124 },
      '/summary': { totalRevenue: 0, activeSessions: 0, averageOrderValue: 0, paidBillCount: 0 },
    })
    renderView(<Analytics />)

    expect(await screen.findByText('Datos del 01/09/2026 al 02/10/2026 · 124 cuentas emitidas')).toBeVisible()
  })

  test('while the range loads, its line is a placeholder with no real text', async () => {
    vi.spyOn(api, 'get').mockImplementation(((url: string) =>
      url.includes('/range')
        ? new Promise(() => {})
        : Promise.resolve({ data: { totalRevenue: 0, activeSessions: 0, averageOrderValue: 0, paidBillCount: 0 } })) as never)
    renderView(<Analytics />)

    expect(await screen.findByTestId('skeleton-range')).toBeInTheDocument()
    expect(screen.queryByText(/Datos del/)).not.toBeInTheDocument()
  })

  test('a tenant that was never billed says so instead of showing dates', async () => {
    mockApi({
      '/range': { firstBillAt: null, lastBillAt: null, billCount: 0 },
      '/summary': { totalRevenue: 0, activeSessions: 0, averageOrderValue: 0, paidBillCount: 0 },
    })
    renderView(<Analytics />)

    expect(await screen.findByText('Aún no hay cuentas emitidas.')).toBeVisible()
  })
})
