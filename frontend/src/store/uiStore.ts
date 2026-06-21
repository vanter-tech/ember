import { create } from 'zustand';

export type ModalType = 'CREATE_CATEGORY' | 'EDIT_CATEGORY' | 'DELETE_CATEGORY' | null;

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