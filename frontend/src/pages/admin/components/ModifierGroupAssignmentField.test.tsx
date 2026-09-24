import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, test, vi } from 'vitest'
import {
  ModifierGroupAssignmentField,
  type ModifierGroupAssignment,
} from '@/pages/admin/components/ModifierGroupAssignmentField'
import { modifierGroupService } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  modifierGroupService: { getAll: vi.fn() },
}))

const makeGroups = (n: number) =>
  Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    name: `Grupo ${i + 1}`,
    selectionType: 'SINGLE_REQUIRED' as const,
    active: true,
    options: [{ id: 1, name: 'a' }],
  }))

const Harness = () => {
  const [value, setValue] = useState<ModifierGroupAssignment[]>([])
  return <ModifierGroupAssignmentField value={value} onChange={setValue} />
}

const renderField = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <Harness />
    </QueryClientProvider>
  )

describe('ModifierGroupAssignmentField', () => {
  test('selects and deselects a group as a badge', async () => {
    vi.mocked(modifierGroupService.getAll).mockResolvedValue(makeGroups(3) as never)
    const user = userEvent.setup()
    renderField()

    const badge = await screen.findByRole('button', { name: /Grupo 1/ })
    expect(badge).toHaveAttribute('aria-pressed', 'false')
    await user.click(badge)
    expect(badge).toHaveAttribute('aria-pressed', 'true')
    await user.click(badge)
    expect(badge).toHaveAttribute('aria-pressed', 'false')
  })

  test('reorders selected groups', async () => {
    vi.mocked(modifierGroupService.getAll).mockResolvedValue(makeGroups(3) as never)
    const user = userEvent.setup()
    renderField()

    await user.click(await screen.findByRole('button', { name: /Grupo 1/ }))
    await user.click(screen.getByRole('button', { name: /Grupo 2/ }))
    const items = () => screen.getAllByRole('listitem').map((li) => li.textContent)
    expect(items()[0]).toContain('1.Grupo 1')

    await user.click(screen.getByRole('button', { name: 'Bajar Grupo 1' }))
    expect(items()[0]).toContain('1.Grupo 2')
    expect(items()[1]).toContain('2.Grupo 1')
  })

  test('shows a search box only when there are many groups and filters by name', async () => {
    vi.mocked(modifierGroupService.getAll).mockResolvedValue(makeGroups(10) as never)
    const user = userEvent.setup()
    renderField()

    const search = await screen.findByPlaceholderText('Buscar grupo...')
    await user.type(search, 'Grupo 10')
    expect(screen.getByRole('button', { name: /Grupo 10/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Grupo 2/ })).not.toBeInTheDocument()
  })

  test('hides the search box for a short list', async () => {
    vi.mocked(modifierGroupService.getAll).mockResolvedValue(makeGroups(3) as never)
    renderField()
    await screen.findByRole('button', { name: /Grupo 1/ })
    expect(screen.queryByPlaceholderText('Buscar grupo...')).not.toBeInTheDocument()
  })
})
