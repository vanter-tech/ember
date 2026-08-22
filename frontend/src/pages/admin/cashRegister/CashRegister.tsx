import { useState } from 'react'
import { ShiftHistoryTable } from './components/ShiftHistoryTable'
import { DailyZReportPanel } from './components/DailyZReportPanel'
import { CashRegisterBar, type CashRegisterSection } from './components/CashRegisterBar'
import { useTranslation } from '@/lib/i18n'

export const CashRegister = () => {
  const { t } = useTranslation('admin')
  const [section, setSection] = useState<CashRegisterSection>('history')
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">{t('cashRegisterTitle')}</h1>
        <p className="text-sm text-muted-foreground">{t('cashRegisterSubtitle')}</p>
      </div>

      <div className="flex flex-col md:flex-row gap-8">
        <div className={`w-full shrink-0 ${sidebarCollapsed ? 'md:w-fit' : 'md:w-64'}`}>
          <CashRegisterBar
            section={section}
            onSectionChange={setSection}
            collapsed={sidebarCollapsed}
            onToggleCollapsed={() => setSidebarCollapsed((prev) => !prev)}
          />
        </div>
        <div className="flex-1 min-w-0">
          {section === 'history' ? <ShiftHistoryTable /> : <DailyZReportPanel />}
        </div>
      </div>
    </div>
  )
}
