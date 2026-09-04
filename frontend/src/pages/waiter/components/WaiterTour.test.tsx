import { render, screen } from '@testing-library/react'
import { describe, test, expect, beforeEach } from 'vitest'
import { WaiterTour } from '@/pages/waiter/components/WaiterTour'
import { useAuthStore } from '@/store/authStore'
import { useSectionTourStore } from '@/store/sectionTourStore'
import { useUIStore } from '@/store/uiStore'

describe('WaiterTour', () => {
  beforeEach(() => {
    useAuthStore.setState({ userId: 'waiter-1' })
    useSectionTourStore.setState({ seenByKey: {} })
    useUIStore.setState({ requestedTourSection: null, activeTourSection: null, cashShiftAlertOpen: false })
    document.body.innerHTML =
      '<div id="waiter-tour-grid"></div><div id="waiter-tour-panel"></div>'
  })

  test('does not render when there are no tables yet', () => {
    render(<WaiterTour tableIds={[]} onSelectFirstTable={() => {}} />)

    expect(screen.queryByText('Tus mesas')).not.toBeInTheDocument()
  })

  test('does not render when this user already saw the tour', () => {
    useSectionTourStore.getState().markTourSeen('waiter-tables', 'waiter-1')

    render(<WaiterTour tableIds={['table-1']} onSelectFirstTable={() => {}} />)

    expect(screen.queryByText('Tus mesas')).not.toBeInTheDocument()
  })

  test('renders the first step for a first-time user with tables', () => {
    render(<WaiterTour tableIds={['table-1']} onSelectFirstTable={() => {}} />)

    expect(screen.getByText('Tus mesas')).toBeInTheDocument()
  })

  test('renders again for a user who already saw it when a replay is requested', () => {
    useSectionTourStore.getState().markTourSeen('waiter-tables', 'waiter-1')
    useUIStore.setState({ requestedTourSection: 'waiter-tables' })

    render(<WaiterTour tableIds={['table-1']} onSelectFirstTable={() => {}} />)

    expect(screen.getByText('Tus mesas')).toBeInTheDocument()
  })

  // QA_SIMULATION_REPORT.md E-17: observed live — CashShiftSentinel's stale-shift alert and this
  // tour's first step appeared stacked on top of each other on the very first /waiter/tables
  // render after login. The tour must hold off while that alert is up.
  test('does not render while a cash-shift alert is open, even with tables present', () => {
    useUIStore.setState({ cashShiftAlertOpen: true })

    render(<WaiterTour tableIds={['table-1']} onSelectFirstTable={() => {}} />)

    expect(screen.queryByText('Tus mesas')).not.toBeInTheDocument()
  })
})
