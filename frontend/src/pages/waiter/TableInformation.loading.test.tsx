import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { TableInformation } from './TableInformation'

vi.mock('@/components/tours/SectionTour', () => ({ SectionTour: () => null }))

describe('TableInformation while the session is loading', () => {
  beforeEach(() => {
    vi.spyOn(api, 'get').mockReturnValue(new Promise(() => {}) as never)
  })
  afterEach(() => vi.restoreAllMocks())

  const renderPage = () =>
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/waiter/tables/s1']}>
          <Routes>
            <Route path="/waiter/tables/:id" element={<TableInformation />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )

  test('draws the whole page frame with placeholder blocks only: no real text, not even card titles', () => {
    renderPage()

    expect(screen.getByTestId('skeleton-detail-header')).toBeInTheDocument()
    expect(screen.getAllByTestId('skeleton-card-title')).toHaveLength(4)
    for (const title of ['Detalles de pedidos', 'Participantes', 'Actividad', 'Resumen']) {
      expect(screen.queryByText(title)).not.toBeInTheDocument()
    }
  })

  test('each card has the placeholder of its content: order lines, participants, activity, bill', () => {
    renderPage()

    expect(screen.getAllByTestId('skeleton-order-row')).toHaveLength(3)
    expect(screen.getAllByTestId('skeleton-participant').length).toBeGreaterThanOrEqual(2)
    expect(screen.getAllByTestId('skeleton-activity-entry').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByTestId('skeleton-bill')).toBeInTheDocument()
  })

  test('announces the loading state to assistive tech', () => {
    renderPage()

    expect(screen.getByRole('status')).toHaveTextContent('Cargando datos del panel...')
  })
})
