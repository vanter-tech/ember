import { useLayoutEffect, useRef, type RefObject } from 'react'

// FLIP animation for the table grid (no animation library): after each layout change that
// `signature` reports, every card that moved or resized slides from its old box to its new one,
// a card that vanished into a merged card glides into it, and a card that reappeared on unlink
// emerges from the merged card it came from. Cards opt in with data attributes:
//   data-flip-id       stable id (the table id)
//   data-flip-members  comma list of the OTHER ids folded into this card (merged cards only)
//   data-flip-label    text shown on the ghost of a card that is folded away
//   data-flip-occupied "true" | "false", only to colour that ghost
interface Box {
  rect: DOMRect
  members: string[]
  label: string
  occupied: boolean
}

const DURATION_MS = 380
const EASING = 'cubic-bezier(0.2, 0.8, 0.2, 1)'

const readBoxes = (container: HTMLElement): Map<string, Box> => {
  const boxes = new Map<string, Box>()
  container.querySelectorAll<HTMLElement>('[data-flip-id]').forEach((node) => {
    boxes.set(node.dataset.flipId!, {
      rect: node.getBoundingClientRect(),
      members: node.dataset.flipMembers ? node.dataset.flipMembers.split(',') : [],
      label: node.dataset.flipLabel ?? '',
      occupied: node.dataset.flipOccupied === 'true',
    })
  })
  return boxes
}

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

const canAnimate = (node: HTMLElement) => typeof node.animate === 'function'

const sameBox = (a: DOMRect, b: DOMRect) =>
  Math.abs(a.left - b.left) < 1 && Math.abs(a.top - b.top) < 1 && Math.abs(a.width - b.width) < 1 && Math.abs(a.height - b.height) < 1

const flyGhost = (from: Box, to: DOMRect) => {
  const ghost = document.createElement('div')
  if (typeof ghost.animate !== 'function') return
  ghost.textContent = from.label
  Object.assign(ghost.style, {
    position: 'fixed',
    left: `${from.rect.left}px`,
    top: `${from.rect.top}px`,
    width: `${from.rect.width}px`,
    height: `${from.rect.height}px`,
    borderRadius: '1rem',
    display: 'flex',
    alignItems: 'flex-start',
    padding: '1rem',
    fontSize: '1.5rem',
    fontWeight: '700',
    pointerEvents: 'none',
    zIndex: '40',
    background: from.occupied ? '#8c1717' : '#ffffff',
    color: from.occupied ? '#ffffff' : '#000000',
    border: from.occupied ? '2px solid #8c1717' : '1px solid #f4f4f5',
  })
  document.body.appendChild(ghost)
  const dx = to.left + to.width / 2 - (from.rect.left + from.rect.width / 2)
  const dy = to.top + to.height / 2 - (from.rect.top + from.rect.height / 2)
  const animation = ghost.animate(
    [
      { transform: 'translate(0, 0) scale(1)', opacity: 1 },
      { transform: `translate(${dx}px, ${dy}px) scale(0.85)`, opacity: 0 },
    ],
    { duration: DURATION_MS, easing: EASING, fill: 'forwards' },
  )
  animation.onfinish = () => ghost.remove()
  animation.oncancel = () => ghost.remove()
}

export const useFlipGrid = (containerRef: RefObject<HTMLElement | null>, signature: string) => {
  const previous = useRef<Map<string, Box>>(new Map())

  useLayoutEffect(() => {
    const container = containerRef.current
    if (!container) return
    const next = readBoxes(container)
    const before = previous.current
    previous.current = next
    if (before.size === 0 || prefersReducedMotion()) return

    // Cards that stayed: slide (and, for a merged card, reveal its new width) from the old box.
    container.querySelectorAll<HTMLElement>('[data-flip-id]').forEach((node) => {
      if (!canAnimate(node)) return
      const id = node.dataset.flipId!
      const now = next.get(id)!.rect
      const old = before.get(id)?.rect
      if (old) {
        if (sameBox(old, now)) return
        const dx = old.left - now.left
        const dy = old.top - now.top
        const grew = now.width - old.width > 1
        node.animate(
          [
            { transform: `translate(${dx}px, ${dy}px)`, clipPath: grew ? `inset(0 ${now.width - old.width}px 0 0 round 1rem)` : 'inset(0 0 0 0 round 1rem)' },
            { transform: 'translate(0, 0)', clipPath: 'inset(0 0 0 0 round 1rem)' },
          ],
          { duration: DURATION_MS, easing: EASING },
        )
        return
      }
      // A card that was not there before: it split off a merged card (unlink). Emerge from it.
      const source = [...before.entries()].find(([, box]) => box.members.includes(id))?.[1]
      if (source) {
        node.animate(
          [
            { transform: `translate(${source.rect.left + source.rect.width / 2 - (now.left + now.width / 2)}px, ${source.rect.top - now.top}px) scale(0.85)`, opacity: 0 },
            { transform: 'translate(0, 0) scale(1)', opacity: 1 },
          ],
          { duration: DURATION_MS, easing: EASING },
        )
      }
    })

    // Cards that were folded into a merged card: a ghost of the old card glides into it.
    before.forEach((box, id) => {
      if (next.has(id)) return
      const target = [...next.values()].find((candidate) => candidate.members.includes(id))
      if (target) flyGhost(box, target.rect)
    })
    // The signature (layout identity) is the trigger; the ref is stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature])

  // A resize reflows the grid without a data change: re-measure so the next animation starts from
  // where the cards really are.
  useLayoutEffect(() => {
    const remeasure = () => {
      if (containerRef.current) previous.current = readBoxes(containerRef.current)
    }
    window.addEventListener('resize', remeasure)
    return () => window.removeEventListener('resize', remeasure)
  }, [containerRef])
}
