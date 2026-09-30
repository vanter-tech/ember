import { useEffect } from 'react'

/** F9 opens the manual-open dialog; `Ctrl+Shift+O` was rejected because Chrome owns it. */
export const useDrawerShortcut = (onTrigger: () => void) => {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key !== 'F9' || e.repeat) return
      e.preventDefault()
      onTrigger()
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onTrigger])
}
