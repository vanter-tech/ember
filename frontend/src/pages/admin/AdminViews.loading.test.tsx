import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { Category } from './Category'
import { Inventory } from './Inventory'
import { ListMenuItem } from './ListMenuItem'
import { ModifierGroups } from './ModifierGroups'

const renderView = (ui: ReactNode, path = '/') =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="*" element={ui} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )

describe('Admin list skeletons mirror their real cards', () => {
  beforeEach(() => {
    vi.spyOn(api, 'get').mockReturnValue(new Promise(() => {}) as never)
  })
  afterEach(() => vi.restoreAllMocks())

  test('Category: cards with image area, title + two action buttons, description lines and a footer chip', () => {
    renderView(<Category />, '/admin/inventory/categories')

    const cards = screen.getAllByTestId('skeleton-category-card')
    expect(cards.length).toBeGreaterThanOrEqual(6)
    expect(screen.getAllByTestId('skeleton-category-image')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-category-actions')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-category-footer')).toHaveLength(cards.length)
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('Inventory: cards with name + edit button and a stock line, enough to fill the window', () => {
    renderView(<Inventory />, '/admin/inventory')

    const cards = screen.getAllByTestId('skeleton-inventory-card')
    expect(cards.length).toBeGreaterThanOrEqual(8)
    expect(screen.getAllByTestId('skeleton-inventory-action')).toHaveLength(cards.length)
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('Menu items: cards with image + status chip, name with 2 buttons, description, modifier chips, price and switch', () => {
    renderView(<ListMenuItem />, '/admin/inventory/categories/1/items')

    const cards = screen.getAllByTestId('skeleton-menu-item-card')
    expect(cards.length).toBeGreaterThanOrEqual(6)
    expect(screen.getAllByTestId('skeleton-menu-item-image')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-menu-item-actions')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-menu-item-price')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-menu-item-switch')).toHaveLength(cards.length)
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })

  test('Modifier groups: cards with name + edit button, a selection-type chip and option chips', () => {
    renderView(<ModifierGroups />, '/admin/inventory/modifiers')

    const cards = screen.getAllByTestId('skeleton-modifier-card')
    expect(cards.length).toBeGreaterThanOrEqual(6)
    expect(screen.getAllByTestId('skeleton-modifier-action')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-modifier-type')).toHaveLength(cards.length)
    expect(screen.getAllByTestId('skeleton-modifier-option').length).toBeGreaterThanOrEqual(cards.length * 2)
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando/)
  })
})
