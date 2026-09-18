import { describe, test, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DenominationCounter } from './DenominationCounter'

describe('DenominationCounter', () => {
  test('renders all 14 denomination rows', () => {
    render(<DenominationCounter onChange={vi.fn()} />)
    // 14 quantity inputs, one per denomination row.
    expect(screen.getAllByRole('spinbutton')).toHaveLength(14)
  })

  test('starts at a total of $0.00', () => {
    render(<DenominationCounter onChange={vi.fn()} />)
    expect(screen.getByTestId('denomination-total')).toHaveTextContent('$0.00')
  })

  test('entering a quantity updates the total and calls onChange with the breakdown', () => {
    const onChange = vi.fn()
    render(<DenominationCounter onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('$100.00'), { target: { value: '2' } })

    expect(screen.getByTestId('denomination-total')).toHaveTextContent('$200.00')
    expect(onChange).toHaveBeenLastCalledWith([{ denominationId: 'bill_100', quantity: 2 }], 200)
  })

  test('a zero quantity is not included in the emitted breakdown', () => {
    const onChange = vi.fn()
    render(<DenominationCounter onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('$100.00'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('$100.00'), { target: { value: '0' } })

    expect(onChange).toHaveBeenLastCalledWith([], 0)
  })

  test('the C$10 bill and C$10 coin are counted independently', () => {
    const onChange = vi.fn()
    render(<DenominationCounter onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Billete $10.00'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Moneda $10.00'), { target: { value: '1' } })

    expect(onChange).toHaveBeenLastCalledWith(
      [
        { denominationId: 'bill_10', quantity: 1 },
        { denominationId: 'coin_10', quantity: 1 },
      ],
      20,
    )
  })
})
