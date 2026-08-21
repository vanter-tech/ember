import { useQuery } from '@tanstack/react-query'
import { Tag, Trophy } from 'lucide-react'
import { analyticsService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import { useTranslation } from '@/lib/i18n'

const TOP_PRODUCTS_LIMIT = 10

export const ProductPerformance = () => {
  const { t } = useTranslation('admin')
  const { data, isLoading, isError } = useQuery({
    queryKey: ['analyticsProducts', TOP_PRODUCTS_LIMIT],
    queryFn: () => analyticsService.getProducts(undefined, undefined, TOP_PRODUCTS_LIMIT),
  })

  const products = data?.products ?? []
  const categories = data?.categories ?? []

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <Card className="border border-border/40 bg-background py-6 shadow-sm lg:col-span-2">
        <CardHeader>
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
              <Trophy className="h-4 w-4 text-primary" strokeWidth={2} />
            </div>
            <CardTitle className="text-base font-semibold tracking-tight text-foreground">
              {t('topProductsTitle')}
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading && (
            <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
              {t('loadingProducts')}
            </div>
          )}
          {isError && (
            <div className="flex items-center justify-center py-16 text-sm text-destructive">
              {t('loadingProductsError')}
            </div>
          )}
          {data && products.length === 0 && (
            <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
              {t('noSalesRegistered')}
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
                      {t('productRevenueSummary', {
                        revenue: (product.revenue ?? 0).toFixed(2),
                        quantity: product.quantitySold ?? 0,
                      })}
                    </span>
                  </div>
                  <div className="relative h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-primary"
                      style={{ width: `${product.revenueShare ?? 0}%` }}
                    />
                  </div>
                  <span className="text-[11px] text-muted-foreground">
                    {t('productShareSummary', {
                      share: (product.revenueShare ?? 0).toFixed(1),
                      cumulative: (product.cumulativeShare ?? 0).toFixed(1),
                    })}
                  </span>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card className="border border-border/40 bg-background py-6 shadow-sm">
        <CardHeader>
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
              <Tag className="h-4 w-4 text-primary" strokeWidth={2} />
            </div>
            <CardTitle className="text-base font-semibold tracking-tight text-foreground">
              {t('byCategoryTitle')}
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent>
          {data && categories.length === 0 && (
            <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
              {t('noDataLabel')}
            </div>
          )}
          {categories.length > 0 && (
            <Table>
              <TableBody className='gap-5 flex flex-col'>
                {categories.map((category, index) => (
                  <TableRow key={`${category.categoryId ?? 'deleted'}-${index}`} className='flex flex-col'>
                    <TableCell className="truncate font-medium text-foreground justify-between pl-0 pt-0 flex">
                      <span>{index + 1}. {category.name ?? t('uncategorizedLabel')}</span>
                      <span className="shrink-0 text-muted-foreground" >{(category.revenueShare ?? 0).toFixed(1)}%</span>
                    </TableCell>
                    <TableCell className="text-right tabular-nums text-muted-foreground p-0">
                      <div className="relative h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-primary"
                      style={{ width: `${category.revenueShare ?? 0}%` }}
                    />
                  </div>
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
