import { Skeleton } from '@/components/ui/skeleton'

// A table as placeholder blocks: a header row and rows of cells over `columns` equal columns.
export const TableSkeleton = ({ columns, rows }: { columns: number; rows: number }) => {
  const grid = { gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }
  return (
    <div data-testid="skeleton-table" className="flex flex-col gap-4">
      <div className="grid gap-4" style={grid}>
        {Array.from({ length: columns }, (_, c) => (
          <Skeleton key={c} className="h-4 w-16" />
        ))}
      </div>
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} data-testid="skeleton-table-row" className="grid gap-4" style={grid}>
          {Array.from({ length: columns }, (_, c) => (
            <Skeleton key={c} className="h-5 w-full max-w-28" />
          ))}
        </div>
      ))}
    </div>
  )
}
