import { describe, expect, test } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { NotFound } from './NotFound'

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <NotFound />
    </MemoryRouter>,
  )

describe('NotFound', () => {
  test('says the page does not exist, with the 404 mark and a heading', () => {
    renderAt('/nope')

    expect(screen.getByRole('heading', { level: 1, name: 'Página no encontrada' })).toBeVisible()
    expect(screen.getByText('La dirección que buscas no existe o fue movida.')).toBeVisible()
    expect(screen.getByText('404')).toBeInTheDocument()
  })

  test('shows the address that failed', () => {
    renderAt('/admin/algo-que-no-existe')

    expect(screen.getByTestId('not-found-path')).toHaveTextContent('/admin/algo-que-no-existe')
  })

  test('offers a way home through the root route, which redirects by role', () => {
    renderAt('/nope')

    expect(screen.getByRole('link', { name: 'Ir al inicio' })).toHaveAttribute('href', '/')
  })

  test('wears the same shell as the login screens: brand footer and language picker', () => {
    renderAt('/nope')

    expect(screen.getByText('Vanter')).toBeVisible()
    expect(screen.getByText(/Desarrollado por/)).toBeVisible()
    expect(screen.getAllByRole('button').length).toBeGreaterThan(0)
  })
})
