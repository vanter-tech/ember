import { SummaryCards } from './components/SummaryCards'
import { SalesChart } from './components/SalesChart'
import { ProductPerformance } from './components/ProductPerformance'
import { TableAnalytics } from './components/TableAnalytics'

export const Analytics = () => {
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">
          Analíticas
        </h1>
        <p className="text-sm text-muted-foreground">
          Métricas de ventas, productos y mesas del restaurante.
        </p>
      </div>

      <SummaryCards />
      <SalesChart />
      <ProductPerformance />
      <TableAnalytics />
    </div>
  )
}
