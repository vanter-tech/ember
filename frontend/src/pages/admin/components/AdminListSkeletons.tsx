import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from '@/components/skeletons/LoadingStatus'

// A category card as it is: image area, name with its two action buttons, description, and the
// product-count chip in the footer. Everything is a placeholder block.
const CategoryCardSkeleton = () => (
  <div data-testid="skeleton-category-card" className="flex flex-col overflow-hidden rounded-2xl border border-zinc-100 bg-white shadow-sm">
    <Skeleton data-testid="skeleton-category-image" className="h-48 w-full rounded-none" />
    <div className="flex items-center justify-between p-5 pb-1">
      <Skeleton className="h-7 w-40" />
      <div data-testid="skeleton-category-actions" className="flex gap-2">
        <Skeleton className="size-9" />
        <Skeleton className="size-9" />
      </div>
    </div>
    <div className="m-4 flex flex-1 flex-col gap-2">
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-2/3" />
    </div>
    <div data-testid="skeleton-category-footer" className="flex items-center border-t border-zinc-100 p-4">
      <Skeleton className="h-6 w-28 rounded-full" />
    </div>
  </div>
)

export const CategoryGridSkeleton = ({ label, count = 6 }: { label: string; count?: number }) => (
  <>
    <LoadingStatus label={label} />
    <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }, (_, i) => (
        <CategoryCardSkeleton key={i} />
      ))}
    </div>
  </>
)

// A stock card as it is: item name with its edit button, and the "stock unit" line.
export const InventoryGridSkeleton = ({ label, count = 10 }: { label: string; count?: number }) => (
  <>
    <LoadingStatus label={label} />
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
      {Array.from({ length: count }, (_, i) => (
        <Card key={i} data-testid="skeleton-inventory-card" className="flex flex-col gap-3 rounded-3xl p-4">
          <div className="flex items-center justify-between">
            <Skeleton className="h-6 w-44" />
            <Skeleton data-testid="skeleton-inventory-action" className="size-9" />
          </div>
          <Skeleton className="h-4 w-24" />
        </Card>
      ))}
    </div>
  </>
)

// A menu item card as it is: image with its status chip, name with edit/delete buttons,
// description, modifier-group chips, and the big price with its switch in the footer.
const MenuItemCardSkeleton = () => (
  <Card data-testid="skeleton-menu-item-card" className="flex flex-col gap-0 overflow-hidden rounded-2xl border border-zinc-100 py-0 shadow-sm">
    <div className="relative h-48">
      <Skeleton data-testid="skeleton-menu-item-image" className="h-full w-full rounded-none" />
      <Skeleton className="absolute left-4 top-4 h-6 w-20 rounded-full" />
    </div>
    <div className="flex flex-1 flex-col gap-3 p-5 pb-3">
      <div className="flex items-center justify-between">
        <Skeleton className="h-7 w-40" />
        <div data-testid="skeleton-menu-item-actions" className="flex gap-2">
          <Skeleton className="size-9 rounded-full" />
          <Skeleton className="size-9 rounded-full" />
        </div>
      </div>
      <Skeleton className="h-4 w-full" />
      <div className="flex gap-1.5">
        <Skeleton className="h-6 w-24 rounded-full" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
    </div>
    <div className="flex items-center justify-between p-4">
      <Skeleton data-testid="skeleton-menu-item-price" className="h-10 w-28" />
      <Skeleton data-testid="skeleton-menu-item-switch" className="h-8 w-14 rounded-xl" />
    </div>
  </Card>
)

export const MenuItemGridSkeleton = ({ label, count = 6 }: { label: string; count?: number }) => (
  <>
    <LoadingStatus label={label} />
    <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }, (_, i) => (
        <MenuItemCardSkeleton key={i} />
      ))}
    </div>
  </>
)

// A modifier group card as it is: name with its edit button, the selection-type chip, option chips.
export const ModifierGroupGridSkeleton = ({ label, count = 8 }: { label: string; count?: number }) => (
  <>
    <LoadingStatus label={label} />
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
      {Array.from({ length: count }, (_, i) => (
        <Card key={i} data-testid="skeleton-modifier-card" className="flex flex-col gap-3 rounded-3xl border border-l-4 border-zinc-200 p-4">
          <div className="flex items-center justify-between">
            <Skeleton className="h-6 w-40" />
            <Skeleton data-testid="skeleton-modifier-action" className="size-9" />
          </div>
          <Skeleton data-testid="skeleton-modifier-type" className="h-6 w-32 rounded-full" />
          <div className="flex flex-wrap gap-1.5">
            {[24, 20, 28].map((w, j) => (
              <Skeleton key={j} data-testid="skeleton-modifier-option" className="h-6 rounded-full" style={{ width: `${w * 4}px` }} />
            ))}
          </div>
        </Card>
      ))}
    </div>
  </>
)
