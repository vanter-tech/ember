import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ShiftHistoryTable } from './components/ShiftHistoryTable'
import { DailyZReportPanel } from './components/DailyZReportPanel'
import { useTranslation } from '@/lib/i18n'

export const CashRegister = () => {
  const { t } = useTranslation('admin')
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">{t('cashRegisterTitle')}</h1>
        <p className="text-sm text-muted-foreground">{t('cashRegisterSubtitle')}</p>
      </div>

      <Tabs defaultValue="history">
        <TabsList>
          <TabsTrigger value="history">{t('shiftHistoryTab')}</TabsTrigger>
          <TabsTrigger value="daily-report">{t('dailyReportTab')}</TabsTrigger>
        </TabsList>
        <TabsContent value="history" className="mt-6">
          <ShiftHistoryTable />
        </TabsContent>
        <TabsContent value="daily-report" className="mt-6">
          <DailyZReportPanel />
        </TabsContent>
      </Tabs>
    </div>
  )
}
