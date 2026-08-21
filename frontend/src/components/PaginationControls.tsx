import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './ui/button'
import { useTranslation } from '@/lib/i18n'

interface PaginationControlsProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

export const PaginationControls = ({
  page,
  totalPages,
  onPageChange,
}: PaginationControlsProps) => {
  const { t } = useTranslation('admin')
  if (totalPages <= 1) {
    return null
  }

  const navItemClass = (enabled: boolean) => `
    flex items-center justify-center w-12 h-12 rounded-full transition-all duration-300 
    ${
      enabled
        ? 'text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800 cursor-pointer text-white'
        : 'text-zinc-300 pointer-events-none'
    }`

  return (
    <div
      className="fixed bottom-24 right-4 sm:bottom-8 sm:right-8 bg-white dark:bg-zinc-900 shadow-2xl rounded-full
        px-4 py-2 flex items-center gap-2 border border-zinc-200 dark:border-zinc-800 z-50"
    >
      <Button
        type="button"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        className={navItemClass(page > 0)}
        title={t('previousLabel')}
      >
        <ChevronLeft strokeWidth={1.5} size={24} />
      </Button>
      <span className="text-sm text-zinc-500 px-1 whitespace-nowrap">
        {page + 1} / {totalPages}
      </span>
      <Button
        type="button"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
        className={navItemClass(page + 1 < totalPages)}
        title={t('nextLabel')}
      >
        <ChevronRight strokeWidth={1.5} size={24} />
      </Button>
    </div>
  )
}
