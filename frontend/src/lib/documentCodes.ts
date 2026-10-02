/**
 * The code a bill or kitchen ticket carries on paper (ELPO-000123 / ELPO-KDS-000045), so the screen
 * and the printout always name the same document. Documents issued before numbering existed have no
 * code and keep showing the identifier they always had.
 */
export const billCode = (code?: string | null, id?: number | string | null): string =>
  code ?? (id != null ? `#${id}` : '')

export const kitchenTicketCode = (order: { ticketCode?: string | null; id?: string | null }): string =>
  order.ticketCode ?? (order.id ? `#${order.id.substring(0, 6).toUpperCase()}` : '')
