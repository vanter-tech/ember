import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Category } from './Category'
import { categoryService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, categoryService: { ...actual.categoryService, getAll: vi.fn() } }
})

const wrap = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <Category />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('Category empty state', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows the empty state when there are no categories yet', async () => {
    vi.mocked(categoryService.getAll).mockResolvedValue({ content: [], totalPages: 0 } as never)
    wrap()
    expect(await screen.findByText('Todavía no tienes categorías')).toBeVisible()
  })

  test('does not show the empty state when categories exist', async () => {
    vi.mocked(categoryService.getAll).mockResolvedValue({
      content: [{ id: 1, name: 'Bebidas', description: '', imgUrl: null, totalItems: 3 }],
      totalPages: 1,
    } as never)
    wrap()
    expect(await screen.findByText('Bebidas')).toBeVisible()
    expect(screen.queryByText('Todavía no tienes categorías')).not.toBeInTheDocument()
  })
})
