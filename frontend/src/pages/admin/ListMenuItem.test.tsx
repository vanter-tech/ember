import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ListMenuItem } from './ListMenuItem'
import { menuItemService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, menuItemService: { ...actual.menuItemService, getAll: vi.fn() } }
})

const wrap = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/admin/inventory/categories/1/items']}>
        <Routes>
          <Route path="/admin/inventory/categories/:id/items" element={<ListMenuItem />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('ListMenuItem empty state', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows the empty state when the category has no dishes yet', async () => {
    vi.mocked(menuItemService.getAll).mockResolvedValue({ content: [], totalPages: 0 } as never)
    wrap()
    expect(await screen.findByText('Esta categoría no tiene platillos')).toBeVisible()
  })

  test('does not show the empty state when dishes exist', async () => {
    vi.mocked(menuItemService.getAll).mockResolvedValue({
      content: [{ id: 1, name: 'Hamburguesa', description: '', price: 10, available: true, imageUrl: null }],
      totalPages: 1,
    } as never)
    wrap()
    expect(await screen.findByText('Hamburguesa')).toBeVisible()
    expect(screen.queryByText('Esta categoría no tiene platillos')).not.toBeInTheDocument()
  })
})

describe('ListMenuItem modifier group badges', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows the assigned modifier groups on the dish card', async () => {
    vi.mocked(menuItemService.getAll).mockResolvedValue({
      content: [
        {
          id: 1,
          name: 'Hamburguesa',
          description: '',
          price: 10,
          available: true,
          imageUrl: null,
          modifierGroups: [
            { id: 3, name: 'Término de cocción', selectionType: 'SINGLE_REQUIRED' },
            { id: 5, name: 'Extras', selectionType: 'MULTI_OPTIONAL' },
          ],
        },
      ],
      totalPages: 1,
    } as never)
    wrap()
    expect(await screen.findByText('Término de cocción')).toBeVisible()
    expect(screen.getByText('Extras')).toBeVisible()
  })

  test('shows no modifier badges when the dish has none', async () => {
    vi.mocked(menuItemService.getAll).mockResolvedValue({
      content: [{ id: 1, name: 'Hamburguesa', description: '', price: 10, available: true, imageUrl: null, modifierGroups: [] }],
      totalPages: 1,
    } as never)
    wrap()
    await screen.findByText('Hamburguesa')
    expect(screen.queryByTestId('menu-item-modifier-groups')).not.toBeInTheDocument()
  })
})
