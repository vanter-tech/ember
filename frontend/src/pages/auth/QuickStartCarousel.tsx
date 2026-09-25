import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'

const FADE = '72px'

interface QuickStartCarouselProps {
  itemCount: number
  children: ReactNode
}

/**
 * Horizontal snap carousel with no visible scrollbar. Edges fade out (only on the side that still
 * has content) and prev/next arrows appear only when the row actually overflows; when everything
 * fits it stays centered like a plain row.
 */
export const QuickStartCarousel = ({ itemCount, children }: QuickStartCarouselProps) => {
  const { t } = useTranslation('auth')
  const scrollerRef = useRef<HTMLDivElement>(null)
  const [canLeft, setCanLeft] = useState(false)
  const [canRight, setCanRight] = useState(false)

  const update = useCallback(() => {
    const el = scrollerRef.current
    if (!el) return
    setCanLeft(el.scrollLeft > 4)
    setCanRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 4)
  }, [])

  useEffect(() => {
    const el = scrollerRef.current
    if (!el) return
    // ResizeObserver fires once on observe, which also covers the initial measurement.
    const observer = new ResizeObserver(update)
    observer.observe(el)
    Array.from(el.children).forEach((child) => observer.observe(child))
    el.addEventListener('scroll', update, { passive: true })
    return () => {
      observer.disconnect()
      el.removeEventListener('scroll', update)
    }
  }, [update, itemCount])

  const scrollByTile = (direction: 1 | -1) => {
    const el = scrollerRef.current
    if (!el) return
    const tile = el.firstElementChild as HTMLElement | null
    const step = (tile?.offsetWidth ?? 176) + 20
    el.scrollBy({ left: direction * step, behavior: 'smooth' })
  }

  const left = canLeft ? `transparent 0, #000 ${FADE}` : '#000 0'
  const right = canRight ? `#000 calc(100% - ${FADE}), transparent 100%` : '#000 100%'
  const mask = `linear-gradient(to right, ${left}, ${right})`

  const arrowClass =
    'absolute top-1/2 z-10 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-full border bg-white text-zinc-700 shadow-md transition hover:bg-zinc-50'

  return (
    <div className="relative w-full">
      {canLeft && (
        <button
          type="button"
          aria-label={t('quickStartPrevAria')}
          onClick={() => scrollByTile(-1)}
          className={`${arrowClass} left-0`}
        >
          <ChevronLeft className="h-5 w-5" />
        </button>
      )}
      <div
        ref={scrollerRef}
        className="flex snap-x snap-mandatory flex-row flex-nowrap gap-5 overflow-x-auto px-2 py-4 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden [&>:first-child]:ml-auto [&>:last-child]:mr-auto"
        style={{ maskImage: mask, WebkitMaskImage: mask }}
      >
        {children}
      </div>
      {canRight && (
        <button
          type="button"
          aria-label={t('quickStartNextAria')}
          onClick={() => scrollByTile(1)}
          className={`${arrowClass} right-0`}
        >
          <ChevronRight className="h-5 w-5" />
        </button>
      )}
    </div>
  )
}
