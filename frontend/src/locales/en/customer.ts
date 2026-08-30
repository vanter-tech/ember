import type { customer as esCustomer } from '../es/customer'

export const customer = {
  // Home
  homeBio: 'Lover of good food and more.',
  homeJoinTableCta: 'Join a table.',
  homeJoinTableCtaShort: 'Join a table',
  homeWelcomeBack: 'Welcome back.',
  loyaltyProgramAt: 'Loyalty program at {{restaurantName}}',
  loyaltyPointsLabel: 'points',
  loyaltyMaxTierReached: 'Highest tier reached',
  loyaltyPointsToNextTier: '{{points}} pts to {{tierName}}',
  loyaltyLastVisitLabel: 'Last visit',
  loyaltyNoVisitsYet: 'No visits yet',
  loyaltyVisitsTitle: 'Your visits',
  loyaltyNoVisitsRegistered: 'You have no visits recorded yet.',
  loyaltyVisitPoints: '+{{points}} pts',

  // Menu
  loadingItems: 'Loading dishes...',
  loadingItemsError: 'Failed to load the dishes.',
  menuTitle: 'Digital Menu',
  menuSubtitle: "Explore today's gourmet selection.",
  tableCodeLabel: 'Table code: {{code}}',
  viewBillLabel: 'View bill',

  // Bill
  billTitle: 'My Bill',
  billNotRequestedYet:
    "The bill hasn't been requested yet. Ask your waiter to calculate it when you're ready to pay.",
  billTableTotal: 'Table total',
  billYouSuffix: ' (You)',
  billStatusPaid: 'Paid',
  billStatusPending: 'Pending',
  billPointsEarned: 'You earned points! You now have {{points}} pts',
  billLoyaltyTierLabel: '{{tierName}} tier',
  billPointsToNextTier: ' — {{points}} pts to {{tierName}}',
  billWaitingConfirmation: 'Waiting for confirmation...',
  billPayMyShare: 'Pay my share (${{amount}})',

  // ComandaView
  comandaTitle: 'Order Review',
  comandaTableLabel: 'Table',
  comandaEmptyDrafts:
    "You don't have any new dishes. Add something from the menu to build your next order.",
  comandaHost: 'Host',
  comandaParticipant: 'Participant',
  comandaSubtotalLabel: 'Subtotal',
  comandaHistoryTitle: 'History',
  comandaNoHistory: "You haven't sent any orders yet.",
  comandaSentBadge: 'Sent',
  comandaServiceLabel: 'Service (10%)',
  comandaTotalLabel: 'Total',
  comandaSending: 'Sending...',
  comandaSendToKitchen: 'Send to kitchen',

  // ItemsFloatingIsland
  itemsIslandPhotoPlaceholder: 'Photo',
  itemsIslandSelectedCount: '{{count}} dishes selected',
  itemsIslandViewComanda: 'View Order',

  // JoinTableModal
  joinModalSelectOption: 'Select an option to join the table.',
  joinModalScanQrTitle: 'Scan QR code.',
  joinModalScanQrDescription: "Use your camera to scan the table's code.",
  joinModalEnterCodeTitle: 'Enter code.',
  joinModalEnterCodeDescription: "Type your table's 5-digit code.",
  joinModalCodeScreenTitle: 'Enter the code.',
  joinModalCodeScreenDescription: 'Carefully enter the code to join the table.',
  joinModalBackButton: 'Back',
  joinModalSubmitting: 'Joining',
  joinModalConfirm: 'Confirm',

  // ParticipantsList
  participantsListTitle: 'At the table',
  participantsGuestFallback: 'Guest',

  // ParticipantsPopUp
  participantsPopupTitle: 'Participants at the table',
  participantsPopupPersonSingular: 'Person',
  participantsPopupPersonPlural: 'People',

  // MobileActionsIsland
  mobileActionsViewParticipants: 'View participants',
  mobileActionsViewComanda: 'View order',
  mobileActionsAriaLabel: 'Table options',
  billPaymentSentToast: 'Payment sent. Wait for the waiter to confirm.',
  billPaymentErrorToast: 'Could not start the payment.',
  billSplitRedistributedToast: '{{name}} left the table. Their share was split among those present.',
  itemDeletedToast: 'Dish removed',
  itemDeleteErrorToast: 'Error removing the dish',
  comandaSentToast: 'Order sent',
  comandaSendErrorToast: 'Error sending the order',
  joinSuccessToast: 'Joined successfully!',
  joinCodeInvalidToast: 'Invalid code, try another one.',
  joinBlockedOtherTableToast: "You're already at another table. Leave it before joining a new one.",
  genericErrorToast: 'An error occurred.',
  itemAddedToast: 'Item added successfully!',
  itemAddErrorToast: 'Failed to add item. Please try again.',

  // SelectModifiersModal
  selectModifiersDialogTitle: 'Customize your order',
  addToCartButton: 'Add (${{price}})',
  requiredSelectionHint: 'Select one option',
  limitedSelectionHint: 'Select between {{min}} and {{max}}',
} satisfies typeof esCustomer
