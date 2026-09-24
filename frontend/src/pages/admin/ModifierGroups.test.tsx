import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ModifierGroups } from './ModifierGroups'
import { modifierGroupService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    modifierGroupService: { ...actual.modifierGroupService, getAll: vi.fn() },
  }
})

const wrap = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <ModifierGroups />
    </QueryClientProvider>
  )
}

describe('ModifierGroups empty state', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows the empty state when there are no modifier groups yet', async () => {
    vi.mocked(modifierGroupService.getAll).mockResolvedValue([] as never)
    wrap()
    expect(await screen.findByText('Todavía no tienes grupos de modificadores')).toBeVisible()
  })

  test('does not show the empty state when groups exist', async () => {
    vi.mocked(modifierGroupService.getAll).mockResolvedValue([
      { id: 1, name: 'Tamaños', selectionType: 'SINGLE', options: [] },
    ] as never)
    wrap()
    expect(await screen.findByText('Tamaños')).toBeVisible()
    expect(screen.queryByText('Todavía no tienes grupos de modificadores')).not.toBeInTheDocument()
  })
})
