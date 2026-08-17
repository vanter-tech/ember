import { create } from 'zustand';

export type ModalType = 'CREATE_CATEGORY' | 'EDIT_CATEGORY' | 'DELETE_CATEGORY' |
                        'CREATE_ITEMS' | 'EDIT_ITEMS' | 'DELETE_ITEMS' |
                         'PARTICIPANTS_QR' | 'JOIN_TABLE' | 'TENANT_SUSPENDED' |
                         'OPEN_SHIFT' | 'CASH_MOVEMENT' | 'CLOSE_SHIFT' |
                         'CHARGE_TABLE' | null;

export type SettingsType = 'BRANDING' | 'MENU' | 'BILLING' | 'HARDWARE'|
                            'SPACE'| 'HORARIO'| null;

interface UIState {
  activeModal: ModalType
  modalPayload: any
  searchTerm: string
  openModal: (modal: ModalType, payload?: any) => void
  closeModal: () => void
  setSearchTerm: (value: string) => void
}

export const useUIStore = create<UIState>((set) => ({

    activeModal: null,
    modalPayload: null,
    searchTerm: '',


  openModal: (modal, payload = null) => set({

    activeModal: modal,
    modalPayload: payload

  }),

  closeModal: () => set({
    activeModal: null,
    modalPayload: null
  }),

  setSearchTerm: (value) => set({ searchTerm: value })

}));

interface SettingsState {
  activeSettings: SettingsType
  openSettings: (settings: SettingsType) => void
  closeSettings: () => void
}

export const useSettingsStore = create<SettingsState>((set) => ({

    activeSettings: null,
    openSettings: (settings) => set({ activeSettings: settings }),
    closeSettings: () => set({ activeSettings: "BRANDING" })
}));