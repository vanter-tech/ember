import { useQuery } from '@tanstack/react-query'
import { analyticsService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const TOP_PRODUCTS_LIMIT = 10

export const ProductPerformance = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsProducts', TOP_PRODUCTS_LIMIT],
    queryFn: () => analyticsService.getProducts(undefined, undefined, TOP_PRODUCTS_LIMIT),
  })

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <Card className="lg:col-span-2">
        <CardHeader>
          <CardTitle className="text-sm font-medium text-zinc-500">
            Productos más vendidos (Pareto)
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading && <div className="text-zinc-500">Cargando productos...</div>}
          {isError && (
            <div className="text-red-500">Error al cargar los productos.</div>
          )}
          {data && data.products.length === 0 && (
            <div className="text-zinc-500">Sin ventas registradas.</div>
          )}
          {data && data.products.length > 0 && (
            <div className="flex flex-col gap-3">
              {data.products.map((product, index) => (
                <div key={`${product.itemId ?? 'deleted'}-${index}`} className="flex flex-col gap-1">
                  <div className="flex items-baseline justify-between gap-2 text-sm">
                    <span className="font-medium text-zinc-800 truncate">
                      {index + 1}. {product.name}
                      {product.categoryName && (
                        <span className="ml-1 text-xs text-zinc-400">
                          {product.categoryName}
                        </span>
                      )}
                    </span>
                    <span className="shrink-0 text-zinc-500">
                      ${product.revenue.toFixed(2)} · {product.quantitySold} uds
                    </span>
                  </div>
                  <div className="relative h-2 w-full overflow-hidden rounded bg-zinc-100">
                    <div
                      className="h-full rounded bg-primary"
                      style={{ width: `${product.revenueShare}%` }}
                    />
                  </div>
                  <span className="text-[11px] text-zinc-400">
                    {product.revenueShare.toFixed(1)}% del ingreso · acumulado{' '}
                    {product.cumulativeShare.toFixed(1)}%
                  </span>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-sm font-medium text-zinc-500">
            Por categoría
          </CardTitle>
        </CardHeader>
        <CardContent>
          {data && data.categories.length === 0 && (
            <div className="text-zinc-500">Sin datos.</div>
          )}
          {data && data.categories.length > 0 && (
            <div className="flex flex-col gap-2">
              {data.categories.map((category, index) => (
                <div
                  key={`${category.categoryId ?? 'deleted'}-${index}`}
                  className="flex items-center justify-between text-sm"
                >
                  <span className="truncate text-zinc-700">
                    {category.name ?? 'Sin categoría'}
                  </span>
                  <span className="shrink-0 text-zinc-500">
                    {category.revenueShare.toFixed(1)}%
                  </span>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
