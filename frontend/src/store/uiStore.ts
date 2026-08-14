import { create } from 'zustand';

export type ModalType = 'CREATE_CATEGORY' | 'EDIT_CATEGORY' | 'DELETE_CATEGORY' |
                        'CREATE_ITEMS' | 'EDIT_ITEMS' | 'DELETE_ITEMS' |
                         'PARTICIPANTS_QR' | 'JOIN_TABLE' | 'TENANT_SUSPENDED' |null;

export type SettingsType = 'BRANDING' | 'MENU' | 'BILLING' | 'HARDWARE'|
                            'SPACE'| null;

interface UIState {
  activeModal: ModalType
  modalPayload: any
  openModal: (modal: ModalType, payload?: any) => void
  closeModal: () => void
}

export const useUIStore = create<UIState>((set) => ({

    activeModal: null,
    modalPayload: null,


  openModal: (modal, payload = null) => set({

    activeModal: modal,
    modalPayload: payload
    
  }),

  closeModal: () => set({
    activeModal: null,
    modalPayload: null
  })

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