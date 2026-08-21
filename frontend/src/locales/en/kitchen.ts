import type { kitchen as esKitchen } from '../es/kitchen'

export const kitchen = {
  loadingOrders: 'Loading orders...',
  loadingOrdersError: 'Could not load the orders.',
  connected: 'Connected',
  disconnected: 'Disconnected',
  kdsSubtitle: 'Kitchen monitor - KDS',
  ticketLabel: 'Ticket: #{{code}}',
  viewDetails: 'View details',
  orderDetailsHeading: 'Order Details - M{{tableNumber}}',
  clientPlaceholder: 'Client: #-Pending',
  entryTimeLabel: 'Entry: {{time}}',
  printButton: 'Print',
  voidButton: 'Void',
} satisfies typeof esKitchen
