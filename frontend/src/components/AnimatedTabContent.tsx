import type { ReactNode } from 'react'

interface AnimatedTabContentProps {
  /** Identity of the active tab: when it changes the content re-enters with the animation. */
  tabKey: string
  children: ReactNode
}

/**
 * Same fade/slide-in the routed pages get from AnimatedOutlet, for content that switches by local
 * state instead of by route (e.g. the Settings tabs). Keying the wrapper on the tab remounts it on
 * every tab change so the animation replays, while re-rendering the same tab (typing in a form)
 * leaves it alone.
 */
export const AnimatedTabContent = ({ tabKey, children }: AnimatedTabContentProps) => (
  <div
    key={tabKey}
    className="animate-in fade-in slide-in-from-bottom-2 duration-300 motion-reduce:animate-none"
  >
    {children}
  </div>
)
