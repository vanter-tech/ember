import { useQuery } from '@tanstack/react-query'
import { analyticsService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'

const TOP_PRODUCTS_LIMIT = 10

export const ProductPerformance = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsProducts', TOP_PRODUCTS_LIMIT],
    queryFn: () => analyticsService.getProducts(undefined, undefined, TOP_PRODUCTS_LIMIT),
  })

  const products = data?.products ?? []
  const categories = data?.categories ?? []

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <Card className="border border-border/40 bg-background shadow-sm lg:col-span-2">
        <CardHeader>
          <CardTitle className="text-base font-semibold tracking-tight text-foreground">
            Productos más vendidos
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading && (
            <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
              Cargando productos...
            </div>
          )}
          {isError && (
            <div className="flex items-center justify-center py-16 text-sm text-destructive">
              Error al cargar los productos.
            </div>
          )}
          {data && products.length === 0 && (
            <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
              Sin ventas registradas.
            </div>
          )}
          {products.length > 0 && (
            <div className="flex flex-col gap-5">
              {products.map((product, index) => (
                <div key={`${product.itemId ?? 'deleted'}-${index}`} className="flex flex-col gap-1.5">
                  <div className="flex items-baseline justify-between gap-2 text-sm">
                    <span className="truncate font-medium text-foreground">
                      {index + 1}. {product.name}
                      {product.categoryName && (
                        <span className="ml-1 text-xs text-muted-foreground">
                          {product.categoryName}
                        </span>
                      )}
                    </span>
                    <span className="shrink-0 text-muted-foreground">
                      ${(product.revenue ?? 0).toFixed(2)} · {product.quantitySold ?? 0} uds
                    </span>
                  </div>
                  <div className="relative h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-primary"
                      style={{ width: `${product.revenueShare ?? 0}%` }}
                    />
                  </div>
                  <span className="text-[11px] text-muted-foreground">
                    {(product.revenueShare ?? 0).toFixed(1)}% del ingreso · acumulado{' '}
                    {(product.cumulativeShare ?? 0).toFixed(1)}%
                  </span>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="border border-border/40 bg-background shadow-sm">
        <CardHeader>
          <CardTitle className="text-base font-semibold tracking-tight text-foreground">
            Por categoría
          </CardTitle>
        </CardHeader>
        <CardContent>
          {data && categories.length === 0 && (
            <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
              Sin datos.
            </div>
          )}
          {categories.length > 0 && (
            <Table>
              <TableBody>
                {categories.map((category, index) => (
                  <TableRow key={`${category.categoryId ?? 'deleted'}-${index}`}>
                    <TableCell className="truncate font-medium text-foreground">
                      {category.name ?? 'Sin categoría'}
                    </TableCell>
                    <TableCell className="text-right tabular-nums text-muted-foreground">
                      {(category.revenueShare ?? 0).toFixed(1)}%
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
