// How many placeholder table cards to draw while the floor loads so they fill the window: the grid
// has 2 columns on a phone and 3 from the `sm` breakpoint (640px), and a card is 160px high plus a
// 16px gap. Rows are clamped so a tiny window still looks like a floor and a huge one stays cheap.
const CARD_HEIGHT = 160
const GAP = 16
const ABOVE_GRID = 160 // header, legend and padding above the grid
const MIN_ROWS = 4
const MAX_ROWS = 8

export const tableSkeletonCount = (width: number, height: number): number => {
  const columns = width >= 640 ? 3 : 2
  const rows = Math.min(MAX_ROWS, Math.max(MIN_ROWS, Math.ceil((height - ABOVE_GRID) / (CARD_HEIGHT + GAP))))
  return columns * rows
}
