import { Outlet, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'

interface AnimatedOutletProps {
  className?: string
}

/**
 * Wraps the routed page in a fade/slide-in that replays on every navigation (keyed by
 * pathname), without remounting the layout around it (nav, websocket connections, etc.
 * stay untouched — only the page content itself re-enters).
 */
export const AnimatedOutlet = ({ className }: AnimatedOutletProps) => {
  const location = useLocation()

  return (
    <div
      key={location.pathname}
      className={cn('animate-in fade-in slide-in-from-bottom-2 duration-300', className)}
    >
      <Outlet />
    </div>
  )
}
