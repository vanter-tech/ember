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

  test('SummaryCards: three metric cards (icon + label + value as blocks), no real text', () => {
    renderView(<SummaryCards />)

    expect(screen.getAllByTestId('skeleton-card')).toHaveLength(3)
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

  test('TableAnalytics: block title, three headline figures and ranking rows', () => {
    renderView(<TableAnalytics />)

    expect(screen.getByTestId('skeleton-card-title')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-stat')).toHaveLength(3)
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
