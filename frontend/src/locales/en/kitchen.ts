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
  entryTimeLabel: 'Entry: {{time}}',
  printButton: 'Print',
  itemStatusUpdateErrorToast: 'Could not update the dish status',
  kdsSelectAll: 'Select all',
  kdsDeselectAll: 'Deselect all',
  kdsBulkStatusPlaceholder: 'Change status to...',
  kdsSelectItemAriaLabel: 'Select {{name}}',
  printSentToast: 'Ticket sent to the printer',
  printQueuedNoAgentToast: 'Ticket queued (no printer connected)',
  printFailedToast: 'Could not print the ticket',
} satisfies typeof esKitchen
