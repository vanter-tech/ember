import { create } from 'zustand';

export type ModalType = 'CREATE_CATEGORY' | 'EDIT_CATEGORY' | 'DELETE_CATEGORY' |
                        'CREATE_ITEMS' | 'EDIT_ITEMS' | 'DELETE_ITEMS' |
                         'PARTICIPANTS_QR' | 'JOIN_TABLE' | 'TENANT_SUSPENDED' |
                         'OPEN_SHIFT' | 'CASH_MOVEMENT' | 'CLOSE_SHIFT' |
                         'CHARGE_TABLE' | 'CREATE_STAFF' | 'EDIT_STAFF' | 'DELETE_STAFF' |
                         'VOID_BILL' | 'REFUND_PAYMENT' | 'CREATE_REWARD' | 'EDIT_REWARD' |
                         'CREATE_PRINT_AGENT' | 'ADD_PRINTER' |
                         'CREATE_MODIFIER_GROUP' | 'EDIT_MODIFIER_GROUP' |
                         'CREATE_INVENTORY_ITEM' | 'EDIT_INVENTORY_ITEM' | null;

export type SettingsType = 'BRANDING' | 'MENU' | 'BILLING' | 'PAYMENT_GATEWAY' | 'TICKET' | 'PRINTING' | 'HARDWARE'|
                            'SPACE'| 'HORARIO'| 'FIDELIZACION' | 'LOYALTY_REWARDS'| null;

// Which section of the /admin/inventory hub (InventoryHub.tsx) is currently mounted, kept here
// (rather than TopNav deriving it from the URL like every other admin route) so the "+" button
// stays decoupled from the hub's own nested-route structure.
export type InventoryHubSection = 'categories' | 'modifiers' | 'stock' | null

interface UIState {
  activeModal: ModalType
  modalPayload: any
  searchTerm: string
  // Whether TopNav's global-search results panel (a Popover anchored to the search input,
  // GlobalSearchResults.tsx) is open — separate from the modal system above, which is for
  // full-screen create/edit dialogs.
  isGlobalSearchOpen: boolean
  activeInventoryHubSection: InventoryHubSection
  // Which SectionTour (see components/tours/SectionTour.tsx) is currently mounted — set by the
  // tour itself on mount/unmount so TopNav's "?" button knows whether one exists for the page
  // currently on screen, and which sectionId to replay without TopNav having to know per-route.
  activeTourSection: string | null
  // Set only by the "?" button; SectionTour clears it once its own tour finishes/skips.
  requestedTourSection: string | null
  openModal: (modal: ModalType, payload?: any) => void
  closeModal: () => void
  setSearchTerm: (value: string) => void
  setGlobalSearchOpen: (open: boolean) => void
  setActiveInventoryHubSection: (section: InventoryHubSection) => void
  setActiveTourSection: (sectionId: string | null) => void
  requestTour: (sectionId: string) => void
  clearTourRequest: () => void
}

export const useUIStore = create<UIState>((set) => ({

    activeModal: null,
    modalPayload: null,
    searchTerm: '',
    isGlobalSearchOpen: false,
    activeInventoryHubSection: null,
    activeTourSection: null,
    requestedTourSection: null,


  openModal: (modal, payload = null) => set({

    activeModal: modal,
    modalPayload: payload

  }),

  closeModal: () => set({
    activeModal: null,
    modalPayload: null
  }),

  setSearchTerm: (value) => set({ searchTerm: value }),

  setGlobalSearchOpen: (open) => set({ isGlobalSearchOpen: open }),

  setActiveInventoryHubSection: (section) => set({ activeInventoryHubSection: section }),

  setActiveTourSection: (sectionId) => set({ activeTourSection: sectionId }),

  requestTour: (sectionId) => set({ requestedTourSection: sectionId }),

  clearTourRequest: () => set({ requestedTourSection: null })

}));

interface SettingsState {
  activeSettings: SettingsType
  openSettings: (settings: SettingsType) => void
  closeSettings: () => void
}

export const useSettingsStore = create<SettingsState>((set) => ({

    activeSettings: 'BRANDING',
    openSettings: (settings) => set({ activeSettings: settings }),
    closeSettings: () => set({ activeSettings: "BRANDING" })
}));