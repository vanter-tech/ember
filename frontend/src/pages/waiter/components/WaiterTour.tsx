import type { Step } from 'react-joyride'
import { SectionTour } from '@/components/tours/SectionTour'
import { useUIStore } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'

interface WaiterTourProps {
  tableIds: string[]
  onSelectFirstTable: () => void
}

export const WaiterTour = ({ tableIds, onSelectFirstTable }: WaiterTourProps) => {
  const { t } = useTranslation('waiter')
  // QA_SIMULATION_REPORT.md E-17: observed live — CashShiftSentinel's "register was never
  // closed" alert and this tour's first beacon both appeared stacked on the very first render
  // of /waiter/tables after login. Hold the tour off while that alert is up.
  const cashShiftAlertOpen = useUIStore((state) => state.cashShiftAlertOpen)

  const steps: Step[] = [
    { target: '#waiter-tour-grid', title: t('tourGridTitle'), content: t('tourGridContent'), skipBeacon: true },
    { target: '#waiter-tour-panel', title: t('tourPanelTitle'), content: t('tourPanelContent') },
    { target: '#waiter-tour-action', title: t('tourActionTitle'), content: t('tourActionContent') },
    { target: '#waiter-tour-assign', title: t('tourAssignTitle'), content: t('tourAssignContent') },
  ]

  return (
    <SectionTour
      sectionId="waiter-tables"
      steps={steps}
      ready={tableIds.length > 0 && !cashShiftAlertOpen}
      // Advancing from step 1 (the grid, index 0) to step 2 (the detail panel, index 1) needs a
      // table selected first — the panel and every step after it don't exist in the DOM otherwise.
      onStepAfter={(index) => {
        if (index === 0) {
          onSelectFirstTable()
        }
      }}
    />
  )
}
