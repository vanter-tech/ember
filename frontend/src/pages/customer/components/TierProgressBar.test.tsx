import { describe, test, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TierProgressBar } from './TierProgressBar'

describe('TierProgressBar', () => {
  test('exposes the progress toward the next tier', () => {
    render(<TierProgressBar percent={62.4} />)

    const bar = screen.getByRole('progressbar', { name: 'Progreso hacia el siguiente nivel' })
    expect(bar).toHaveAttribute('aria-valuenow', '62')
  })

  test('keeps the value within 0-100 and treats a missing percentage as 0', () => {
    const { rerender } = render(<TierProgressBar percent={140} />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')

    rerender(<TierProgressBar percent={-5} />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0')

    rerender(<TierProgressBar />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0')
  })
})
