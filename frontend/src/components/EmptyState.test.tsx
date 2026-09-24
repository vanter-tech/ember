import { describe, test, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Warehouse } from 'lucide-react'
import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  test('renders the title and, when given, the description', () => {
    render(<EmptyState icon={Warehouse} title="Nada por aquí" description="Agrega el primero." />)
    expect(screen.getByText('Nada por aquí')).toBeVisible()
    expect(screen.getByText('Agrega el primero.')).toBeVisible()
  })

  test('omits the description paragraph when none is given', () => {
    render(<EmptyState icon={Warehouse} title="Nada por aquí" />)
    expect(screen.getByText('Nada por aquí')).toBeVisible()
    expect(screen.queryByText('Agrega el primero.')).not.toBeInTheDocument()
  })

  test('renders the optional action', () => {
    render(<EmptyState icon={Warehouse} title="Nada por aquí" action={<button>Crear</button>} />)
    expect(screen.getByRole('button', { name: 'Crear' })).toBeVisible()
  })
})
