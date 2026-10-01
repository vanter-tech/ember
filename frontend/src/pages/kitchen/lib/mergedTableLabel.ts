// Kitchen label for a merged table group: "M3+M4 - Unidas". Null when the table is not merged,
// so callers fall back to their existing individual-table rendering.
export const mergedTableLabel = (
  tableNumber: number | undefined,
  linked: number[] | undefined,
  word: string,
): string | null => {
  if (tableNumber == null || !linked || linked.length === 0) return null
  return `M${tableNumber}${linked.map((n) => `+M${n}`).join('')} - ${word}`
}
