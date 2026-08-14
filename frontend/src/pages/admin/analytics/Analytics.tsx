import { SummaryCards } from './components/SummaryCards'
import { SalesChart } from './components/SalesChart'

export const Analytics = () => {
  return (
    <div className="p-6 flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-zinc-800">Analíticas</h1>
        <p className="text-sm text-zinc-500">
          Métricas de ventas, productos y mesas del restaurante.
        </p>
      </div>

      <SummaryCards />
      <SalesChart />
    </div>
  )
}
