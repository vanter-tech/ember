import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ConsoleRestaurantCreate from '@/pages/console/ConsoleRestaurantCreate'
import { platformRestaurantService } from '@/lib/platformApi'

vi.mock('react-hot-toast', () => ({
  default: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
}))

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformRestaurantService: { ...actual.platformRestaurantService, create: vi.fn() },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  )

const fillEverythingButTheMode = () => {
  fireEvent.change(screen.getByLabelText('Nombre del restaurante'), { target: { value: 'Hub Grill' } })
  fireEvent.change(screen.getByLabelText('Slug'), { target: { value: 'hub-grill' } })
  fireEvent.change(screen.getByLabelText('Nombre del administrador'), { target: { value: 'Jane' } })
  fireEvent.change(screen.getByLabelText('Email del administrador'), { target: { value: 'jane@hub.test' } })
  fireEvent.change(screen.getByLabelText('Contraseña inicial'), { target: { value: 'Secret#123' } })
}

describe('ConsoleRestaurantCreate', () => {
  beforeEach(() => vi.clearAllMocks())

  test('the mode is required: no default, and the form does not submit without it', async () => {
    wrap(<ConsoleRestaurantCreate />)

    fillEverythingButTheMode()
    fireEvent.click(screen.getByRole('button', { name: 'Crear restaurante' }))

    // FormMessage renders no text in this codebase (no children on its <p>), so the observable
    // signal of the validation error is the control's aria-invalid.
    await waitFor(() =>
      expect(screen.getByLabelText('Modo de uso')).toHaveAttribute('aria-invalid', 'true')
    )
    await waitFor(() => expect(platformRestaurantService.create).not.toHaveBeenCalled())
  })

  test('shows the mode selector with an operator-friendly label', () => {
    wrap(<ConsoleRestaurantCreate />)

    expect(screen.getByText('Modo de uso')).toBeInTheDocument()
  })
})
