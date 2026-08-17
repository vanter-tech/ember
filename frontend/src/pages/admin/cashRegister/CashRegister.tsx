import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ShiftHistoryTable } from './components/ShiftHistoryTable'
import { DailyZReportPanel } from './components/DailyZReportPanel'

export const CashRegister = () => {
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Caja</h1>
        <p className="text-sm text-muted-foreground">Historial de turnos y corte diario de caja.</p>
      </div>

      <Tabs defaultValue="history">
        <TabsList>
          <TabsTrigger value="history">Historial de turnos</TabsTrigger>
          <TabsTrigger value="daily-report">Corte diario (Z)</TabsTrigger>
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
