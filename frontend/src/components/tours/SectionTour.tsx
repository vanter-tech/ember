import { useEffect, useState } from 'react'
import { Joyride, type EventData, EVENTS, type Step, STATUS } from 'react-joyride'
import { useAuthStore } from '@/store/authStore'
import { useSectionTourStore } from '@/store/sectionTourStore'
import { useUIStore } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'

interface SectionTourProps {
  /** Stable id for this tour, e.g. "admin-analytics" — keyed per-user in sectionTourStore and
   *  used by TopNav's "?" button to know which tour to (re)start. Unique across the whole app. */
  sectionId: string
  steps: Step[]
  /** False while the section's own data/DOM isn't ready to be pointed at yet (mirrors
   *  WaiterTour's original `tableIds.length === 0` guard). Defaults to true. */
  ready?: boolean
  /** Escape hatch for a step needing a side effect before the next step's target exists in the
   *  DOM (e.g. WaiterTour selecting a table to reveal its detail panel). */
  onStepAfter?: (stepIndex: number) => void
}

export const SectionTour = ({ sectionId, steps, ready = true, onStepAfter }: SectionTourProps) => {
  const { t } = useTranslation('common')
  const userId = useAuthStore((state) => state.userId)
  const hasSeenTour = useSectionTourStore((state) => state.hasSeenTour)
  const markTourSeen = useSectionTourStore((state) => state.markTourSeen)
  const requestedTourSection = useUIStore((state) => state.requestedTourSection)
  const clearTourRequest = useUIStore((state) => state.clearTourRequest)
  const setActiveTourSection = useUIStore((state) => state.setActiveTourSection)
  const [run, setRun] = useState(true)

  const isRequested = requestedTourSection === sectionId

  // Announces this section to TopNav (via useUIStore) so its "?" button knows a tour exists here
  // and which sectionId to request — the button lives outside this component's own subtree.
  useEffect(() => {
    setActiveTourSection(sectionId)
    return () => setActiveTourSection(null)
  }, [sectionId, setActiveTourSection])

  // A replay request can arrive after `run` was already flipped false by a prior finish/skip —
  // without this, `run={false}` would be passed straight to a freshly-visible Joyride and it
  // would never actually start (see WaiterTour's original bugfix, report 212).
  useEffect(() => {
    if (isRequested) {
      setRun(true)
    }
  }, [isRequested])

  if (!userId || !ready || (hasSeenTour(sectionId, userId) && !isRequested)) {
    return null
  }

  const handleEvent = (data: EventData) => {
    const { status, type, index } = data

    if (type === EVENTS.STEP_AFTER) {
      onStepAfter?.(index)
    }

    if (status === STATUS.FINISHED || status === STATUS.SKIPPED) {
      setRun(false)
      markTourSeen(sectionId, userId)
      clearTourRequest()
    }
  }

  return (
    <Joyride
      steps={steps}
      run={run}
      continuous
      onEvent={handleEvent}
      options={{ primaryColor: '#7a1315', buttons: ['back', 'close', 'skip', 'primary'] }}
      locale={{
        back: t('tourBackButton'),
        next: t('tourNextButton'),
        skip: t('tourSkipButton'),
        last: t('tourLastButton'),
      }}
    />
  )
}
