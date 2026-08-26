import { describe, test, expect, beforeEach } from 'vitest'
import { useSectionTourStore } from '@/store/sectionTourStore'

describe('sectionTourStore', () => {
  beforeEach(() => {
    useSectionTourStore.setState({ seenByKey: {} })
  })

  test('hasSeenTour is false for a section/user that has not seen it', () => {
    expect(useSectionTourStore.getState().hasSeenTour('analytics', 'user-1')).toBe(false)
  })

  test('markTourSeen makes hasSeenTour true for that section/user pair only', () => {
    useSectionTourStore.getState().markTourSeen('analytics', 'user-1')

    expect(useSectionTourStore.getState().hasSeenTour('analytics', 'user-1')).toBe(true)
    expect(useSectionTourStore.getState().hasSeenTour('analytics', 'user-2')).toBe(false)
    expect(useSectionTourStore.getState().hasSeenTour('staff', 'user-1')).toBe(false)
  })
})
