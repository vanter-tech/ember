import { describe, expect, test } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TableCardView } from './TableCardView'

describe('TableCardView (the card as drawn on the grid and under the finger while dragging)', () => {
  test('a free table shows its number and an empty participant count', () => {
    render(<TableCardView table={{ tableId: 't5', tableNumber: 5, isOccupied: false }} linked={[]} />)

    expect(screen.getByText('M5')).toBeVisible()
    expect(screen.getByText('0')).toBeVisible()
  })

  test('an occupied merged table shows the joined label, the participants and the brand colour', () => {
    render(
      <TableCardView
        table={{
          tableId: 't3', tableNumber: 3, isOccupied: true,
          currentSession: { sessionId: 's', waiterName: 'Fe', currentParticipant: 4 },
        }}
        linked={[{ tableId: 't4', tableNumber: 4 }]}
        data-testid="card"
      />,
    )

    expect(screen.getByText('M3 + M4')).toBeVisible()
    expect(screen.getByText('4')).toBeVisible()
    expect(screen.getByTestId('card')).toHaveClass('bg-[#8c1717]')
  })

  test('extra classes and props go to the card, so the drag overlay can fill the original box', () => {
    render(
      <TableCardView
        table={{ tableId: 't5', tableNumber: 5, isOccupied: false }}
        linked={[]}
        className="h-full w-full"
        data-testid="card"
      />,
    )

    expect(screen.getByTestId('card')).toHaveClass('h-full', 'w-full')
  })
})
