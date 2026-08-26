import { render, screen, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi, describe, test, expect, beforeEach } from 'vitest'
import { GlobalSearchResults } from '@/components/GlobalSearchResults'
import { useUIStore } from '@/store/uiStore'

vi.mock('@/lib/api', () => ({
  categoryService: { getAll: vi.fn().mockResolvedValue({ content: [{ id: 1, name: 'Bebidas' }] }) },
  modifierGroupService: { getAll: vi.fn().mockResolvedValue([]) },
  inventoryService: { getAll: vi.fn().mockResolvedValue([]) },
  staffService: { getAll: vi.fn().mockResolvedValue([{ id: 'u1', name: 'Ana Pérez' }]) },
}))

function renderResults(query: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/admin/analytics']}>
        <Routes>
          <Route path="/admin/analytics" element={<GlobalSearchResults query={query} enabled />} />
          <Route path="/admin/inventory/categories" element={<div>Categories page</div>} />
          <Route path="/admin/employees" element={<div>Staff page</div>} />
          <Route path="/admin/settings" element={<div>Settings page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('GlobalSearchResults', () => {
  beforeEach(() => {
    useUIStore.setState({ isGlobalSearchOpen: true })
  })

  test('shows a hint instead of results when the query is empty', () => {
    renderResults('')

    expect(screen.getByText(/Busca categorías/)).toBeInTheDocument()
  })

  test('shows no-results copy when nothing matches', async () => {
    renderResults('xyz-nothing-matches')

    expect(await screen.findByText('Sin resultados para tu búsqueda.')).toBeInTheDocument()
  })

  test('matches a category and navigates there on click, closing the panel', async () => {
    renderResults('beb')

    const result = await screen.findByText('Bebidas')
    fireEvent.click(result)

    expect(await screen.findByText('Categories page')).toBeInTheDocument()
    expect(useUIStore.getState().isGlobalSearchOpen).toBe(false)
  })

  test('matches a staff member by name', async () => {
    renderResults('ana')

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()
  })

  test('matches a settings tab as a section shortcut', async () => {
    renderResults('facturación')

    const result = await screen.findByText('Facturación')
    fireEvent.click(result)

    expect(await screen.findByText('Settings page')).toBeInTheDocument()
  })
})
