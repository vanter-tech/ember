import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from './LoadingStatus'

/** One row of a settings form, as the real tabs draw them. */
export type SettingsRow =
  | 'field' // label + input
  | 'pair' // two fields side by side
  | 'triple' // three fields side by side
  | 'toggle' // bordered row: label + helper text, and a switch
  | 'chips' // label, a row of chips and an add button
  | 'rule' // two small fields and a switch (e.g. a tax rule)
  | 'day' // a weekday: switch + label and two time fields
  | 'preview' // label and two buttons

interface SettingsFormSkeletonProps {
  /** Text announced to screen readers while loading. */
  label: string
  /** The rows of this tab's form, in order. Defaults to `fields` plain fields. */
  layout?: SettingsRow[]
  fields?: number
  /** Toggle tabs lay their rows out in two columns. */
  twoColumns?: boolean
}

const Field = () => (
  <div data-testid="skeleton-field" className="flex flex-col gap-2">
    <Skeleton className="h-4 w-32" />
    <Skeleton className="h-10 w-full" />
  </div>
)

const Toggle = () => (
  <div data-testid="skeleton-toggle" className="flex items-center justify-between rounded-xl border border-zinc-200 p-4">
    <div className="flex flex-col gap-2">
      <Skeleton className="h-4 w-40" />
      <Skeleton className="h-3 w-56" />
    </div>
    <Skeleton className="h-6 w-11 rounded-full" />
  </div>
)

const renderRow = (row: SettingsRow, key: number) => {
  switch (row) {
    case 'field':
      return <Field key={key} />
    case 'pair':
      return (
        <div key={key} className="grid grid-cols-1 gap-6 md:grid-cols-2">
          <Field />
          <Field />
        </div>
      )
    case 'triple':
      return (
        <div key={key} className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <Field />
          <Field />
          <Field />
        </div>
      )
    case 'toggle':
      return <Toggle key={key} />
    case 'chips':
      return (
        <div key={key} data-testid="skeleton-chips" className="flex flex-col gap-2">
          <Skeleton className="h-4 w-36" />
          <div className="flex flex-wrap gap-2">
            <Skeleton className="h-8 w-16 rounded-full" />
            <Skeleton className="h-8 w-16 rounded-full" />
            <Skeleton className="h-8 w-16 rounded-full" />
            <Skeleton className="h-9 w-24" />
          </div>
        </div>
      )
    case 'rule':
      return (
        <div key={key} className="grid grid-cols-[1fr_8rem_auto_auto] items-end gap-3">
          <Field />
          <Field />
          <div data-testid="skeleton-toggle">
            <Skeleton className="h-6 w-11 rounded-full" />
          </div>
          <Skeleton className="size-9" />
        </div>
      )
    case 'day':
      return (
        <div key={key} className="flex items-center gap-4">
          <div data-testid="skeleton-toggle" className="flex w-40 items-center gap-3">
            <Skeleton className="h-6 w-11 rounded-full" />
            <Skeleton className="h-4 w-20" />
          </div>
          <div data-testid="skeleton-field" className="flex-1">
            <Skeleton className="h-10 w-full" />
          </div>
          <div data-testid="skeleton-field" className="flex-1">
            <Skeleton className="h-10 w-full" />
          </div>
        </div>
      )
    case 'preview':
      return (
        <div key={key} data-testid="skeleton-preview" className="flex flex-col gap-2">
          <Skeleton className="h-4 w-32" />
          <div className="flex gap-3">
            <Skeleton className="h-10 w-36" />
            <Skeleton className="h-10 w-36" />
          </div>
        </div>
      )
  }
}

// What a Settings tab looks like before its data arrives, drawn with the frame of the real card:
// round icon + title + description, a divider, the tab's own rows, and the two footer buttons.
// Everything is a placeholder block; the layout says which rows this tab has.
export const SettingsFormSkeleton = ({ label, layout, fields = 5, twoColumns = false }: SettingsFormSkeletonProps) => {
  const rows = layout ?? Array.from({ length: fields }, (): SettingsRow => 'field')
  return (
    <Card className="border-zinc-100 shadow-sm">
      <LoadingStatus label={label} />
      <CardHeader data-testid="skeleton-settings-header" className="flex flex-row items-center gap-4 space-y-0 p-6">
        <Skeleton className="size-12 rounded-full" />
        <div className="flex flex-col gap-2">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-4 w-72 max-w-full" />
        </div>
      </CardHeader>
      <div className="m-auto w-full border-t border-[#7a1315]/20" />
      <CardContent
        className={twoColumns ? 'grid grid-cols-1 gap-x-12 gap-y-6 p-6 md:grid-cols-2' : 'flex flex-col gap-6 p-6'}
      >
        {rows.map(renderRow)}
      </CardContent>
      <CardFooter data-testid="skeleton-settings-footer" className="flex justify-end gap-3 border-t pt-6">
        <Skeleton className="h-10 w-36" />
        <Skeleton className="h-10 w-36" />
      </CardFooter>
    </Card>
  )
}
