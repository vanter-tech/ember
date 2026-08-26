import { render, screen } from '@testing-library/react'
import { describe, test, expect, beforeEach } from 'vitest'
import { SectionTour } from '@/components/tours/SectionTour'
import { useAuthStore } from '@/store/authStore'
import { useSectionTourStore } from '@/store/sectionTourStore'
import { useUIStore } from '@/store/uiStore'

const steps = [
  { target: '#section-tour-anchor', title: 'Analíticas', content: 'Aquí ves tus métricas.', skipBeacon: true },
]

describe('SectionTour', () => {
  beforeEach(() => {
    useAuthStore.setState({ userId: 'admin-1' })
    useSectionTourStore.setState({ seenByKey: {} })
    useUIStore.setState({ requestedTourSection: null, activeTourSection: null })
    document.body.innerHTML = '<div id="section-tour-anchor"></div>'
  })

  test('does not render when ready is false', () => {
    render(<SectionTour sectionId="analytics" steps={steps} ready={false} />)

    expect(screen.queryByText('Analíticas')).not.toBeInTheDocument()
  })

  test('does not render when this user already saw this section\'s tour', () => {
    useSectionTourStore.getState().markTourSeen('analytics', 'admin-1')

    render(<SectionTour sectionId="analytics" steps={steps} />)

    expect(screen.queryByText('Analíticas')).not.toBeInTheDocument()
  })

  test('renders the first step for a first-time user', () => {
    render(<SectionTour sectionId="analytics" steps={steps} />)

    expect(screen.getByText('Analíticas')).toBeInTheDocument()
  })

  test('does not render an already-seen tour for a different sectionId', () => {
    useSectionTourStore.getState().markTourSeen('staff', 'admin-1')

    render(<SectionTour sectionId="analytics" steps={steps} />)

    expect(screen.getByText('Analíticas')).toBeInTheDocument()
  })

  test('renders again when a replay is requested for this section', () => {
    useSectionTourStore.getState().markTourSeen('analytics', 'admin-1')
    useUIStore.setState({ requestedTourSection: 'analytics' })

    render(<SectionTour sectionId="analytics" steps={steps} />)

    expect(screen.getByText('Analíticas')).toBeInTheDocument()
  })

  test('registers itself as the active tour section on mount and clears it on unmount', () => {
    const { unmount } = render(<SectionTour sectionId="analytics" steps={steps} />)

    expect(useUIStore.getState().activeTourSection).toBe('analytics')

    unmount()

    expect(useUIStore.getState().activeTourSection).toBeNull()
  })
})
