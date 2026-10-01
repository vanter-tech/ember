// A free table dropped on an occupied table becomes a link request; anything else is not a merge.
export const resolveLinkDrop = (
  activeId: string,
  over: { id: string; sessionId?: string } | null,
): { sessionId: string; tableId: string } | null => {
  if (!over || over.id === activeId || !over.sessionId) return null
  return { sessionId: over.sessionId, tableId: activeId }
}
