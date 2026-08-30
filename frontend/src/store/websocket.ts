import { create } from "zustand";
import { Client, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from "./authStore";
import { useSessionStore } from "./sessionStore";
import { queryClient } from "@/queryClient";
import type { WaiterBillState } from "@/lib/api";

interface LowStockAlert {
    menuItemId: number
    menuItemName: string
    currentStock: number
    unit: string
    threshold: number
}

interface WebSocketState {
    stompClient: Client | null,
    isConnected: boolean,
    currentSubscription: StompSubscription | null,
    waiterSessionSubscription: StompSubscription | null,
    subscribeToSession:(sessionId: string) => void,
    subscribeToKitchen:(tenantId: string) => void,
    subscribeToWaiter:(tenantId: string) => void,
    subscribeToWaiterSession:(sessionId: string) => void,
    unsubscribeFromWaiterSession:() => void,
    inventorySubscription: StompSubscription | null,
    lastLowStockAlert: LowStockAlert | null,
    subscribeToInventory:(tenantId: string) => void,
    unsubscribeFromInventory:() => void,
    clearLowStockAlert:() => void,
    lastBillRedistribution: { departedParticipantName: string } | null,
    clearBillRedistribution:() => void,
    connect: () => void,
    disconnect: () => void
}

export const useWebsocketStore = create<WebSocketState>((set, get) => ({
    

    stompClient: null,
    isConnected: false,
    currentSubscription: null,
    waiterSessionSubscription: null,
    inventorySubscription: null,
    lastLowStockAlert: null,
    lastBillRedistribution: null,

    connect: () => {
        if (get().stompClient) return;
        const token = useAuthStore.getState().token;

        const wsUrl =
            window.ENV?.EMBW_WS_URL ||
            import.meta.env.VITE_WS_URL ||
            'http://localhost:8080/v1/ws'

        const client = new Client({
            webSocketFactory: () => {
                return new SockJS(wsUrl)
            },
            connectHeaders: {
               Authorization: `Bearer ${token}`
            }
        })

        client.onConnect = () => {
            set({isConnected: true})
        }

        client.onDisconnect = () => {
            set({isConnected: false})
        }

        client.onStompError = () => {
            set({isConnected: false})
        }

        client.activate()

        set({stompClient: client})
    },

    subscribeToSession: (sessionId: string) => {
        const currentClient = get().stompClient

        if(!currentClient || !currentClient.connected) {
            return
        }

        const existingSub = get().currentSubscription

        if(existingSub) {
            existingSub.unsubscribe()
        }
        
        const subscription = currentClient.subscribe(`/topic/session/${sessionId}`, (msg) => {
            const eventData = JSON.parse(msg.body)
            if(eventData.type === 'PARTICIPANT_JOINED'){
                const newParticipants = {
                    userId: eventData.userId,
                    name: eventData.userName
                }
                useSessionStore.getState().addParticipant(newParticipants)
            }
            if(eventData.type === 'PARTICIPANT_LEFT'){
                useSessionStore.getState().removeParticipant(eventData.userId)
            }
            if(eventData.type === 'ITEM_ADDED'){
                useSessionStore.getState().updateSession({items: eventData.sessionItems})
            }
            if(eventData.type === 'ITEM_DELETED'){
                const currentState = useSessionStore.getState()
                const updateItems = currentState.items?.filter((item) => item.id !== eventData.orderItemId)
                useSessionStore.getState().updateSession({items: updateItems})
            }
            if(eventData.type === 'ITEMS_CONFIRMED'){
                useSessionStore.getState().updateSession({items: eventData.sessionItems})
            }
            if(eventData.type === 'BILL_READY'){
                useSessionStore.getState().setBillReady(
                    { id: eventData.billId, total: eventData.total },
                    eventData.splits
                )
            }
            if(eventData.type === 'SPLIT_PAID'){
                useSessionStore.getState().markSplitStatus(eventData.participantName, eventData.status)
            }
            if(eventData.type === 'SPLIT_REFUNDED'){
                useSessionStore.getState().markSplitStatus(eventData.participantName, eventData.status)
            }
            if(eventData.type === 'SPLITS_REDISTRIBUTED'){
                useSessionStore.getState().replaceSplits(eventData.splits)
                set({ lastBillRedistribution: { departedParticipantName: eventData.departedParticipantName } })
            }
            if(eventData.type === 'BILL_VOIDED'){
                useSessionStore.getState().clearBill()
            }
            if(eventData.type === 'SESSION_CLOSED'){
                useSessionStore.getState().clearSession()
                queryClient.removeQueries({queryKey: ['sessionDetails']})
                window.location.href = '/customer/home'
            }
        })


        set({currentSubscription: subscription})
    },

    subscribeToKitchen: (tenantId: string) => {
        const currentClient = get().stompClient

        if(!currentClient || !currentClient.connected) {
            return
        }

        const existingSub = get().currentSubscription

        if(existingSub) {
            existingSub.unsubscribe()
        }

        const subscription = currentClient.subscribe(`/topic/kitchen/${tenantId}`, () => {
            queryClient.invalidateQueries({queryKey: ['kitchenOrders']})
        })

        set({currentSubscription: subscription})
    },

    subscribeToWaiterSession: (sessionId: string) => {
        const currentClient = get().stompClient

        if(!currentClient || !currentClient.connected) {
            return
        }

        const existingSub = get().waiterSessionSubscription

        if(existingSub) {
            existingSub.unsubscribe()
        }

        const subscription = currentClient.subscribe(`/topic/session/${sessionId}`, (msg) => {
            const eventData = JSON.parse(msg.body)
            if(
                eventData.type === 'ITEM_ADDED' ||
                eventData.type === 'ITEMS_CONFIRMED' ||
                eventData.type === 'ITEM_DELETED' ||
                eventData.type === 'PARTICIPANT_LEFT' ||
                eventData.type === 'SESSION_CLOSED'
            ){
                queryClient.invalidateQueries({queryKey: ['sessionDetails', sessionId]})
            }
            if(eventData.type === 'BILL_READY'){
                queryClient.setQueryData<WaiterBillState>(['bill', sessionId], {
                    id: eventData.billId,
                    total: eventData.total,
                    splits: eventData.splits,
                })
            }
            if(eventData.type === 'SPLIT_PAID'){
                queryClient.setQueryData<WaiterBillState | undefined>(['bill', sessionId], (old) =>
                    old
                        ? {
                            ...old,
                            splits: old.splits.map((split) =>
                                split.participantName === eventData.participantName
                                    ? { ...split, status: eventData.status }
                                    : split
                            ),
                            pendingDigitalPayments: (old.pendingDigitalPayments || []).filter(
                                (p) => p.participantName !== eventData.participantName
                            ),
                        }
                        : old
                )
            }
            if(eventData.type === 'SPLIT_REFUNDED'){
                queryClient.setQueryData<WaiterBillState | undefined>(['bill', sessionId], (old) =>
                    old
                        ? {
                            ...old,
                            splits: old.splits.map((split) =>
                                split.participantName === eventData.participantName
                                    ? { ...split, status: eventData.status }
                                    : split
                            ),
                        }
                        : old
                )
            }
            if(eventData.type === 'SPLITS_REDISTRIBUTED'){
                queryClient.setQueryData<WaiterBillState | undefined>(['bill', sessionId], (old) =>
                    old ? { ...old, splits: eventData.splits } : old
                )
                set({ lastBillRedistribution: { departedParticipantName: eventData.departedParticipantName } })
            }
            if(eventData.type === 'BILL_VOIDED'){
                queryClient.removeQueries({queryKey: ['bill', sessionId]})
            }
            if(eventData.type === 'DIGITAL_PAYMENT_INITIATED'){
                queryClient.setQueryData<WaiterBillState | undefined>(['bill', sessionId], (old) =>
                    old
                        ? {
                            ...old,
                            pendingDigitalPayments: [
                                ...(old.pendingDigitalPayments || []).filter(
                                    (p) => p.participantName !== eventData.participantName
                                ),
                                {
                                    id: eventData.paymentId,
                                    participantName: eventData.participantName,
                                    amount: eventData.amount,
                                },
                            ],
                        }
                        : old
                )
            }
            if(eventData.type === 'SESSION_CLOSED'){
                queryClient.removeQueries({queryKey: ['bill', sessionId]})
            }
        })

        set({waiterSessionSubscription: subscription})
    },

    unsubscribeFromWaiterSession: () => {
        const existingSub = get().waiterSessionSubscription

        if(existingSub) {
            existingSub.unsubscribe()
        }

        set({waiterSessionSubscription: null})
    },

    subscribeToInventory: (tenantId: string) => {
        const currentClient = get().stompClient

        if(!currentClient || !currentClient.connected) {
            return
        }

        const existingSub = get().inventorySubscription

        if(existingSub) {
            existingSub.unsubscribe()
        }

        const subscription = currentClient.subscribe(`/topic/inventory/${tenantId}`, (msg) => {
            const eventData = JSON.parse(msg.body)
            if(eventData.type === 'LOW_STOCK'){
                queryClient.invalidateQueries({queryKey: ['inventoryItems']})
                set({lastLowStockAlert: {
                    menuItemId: eventData.menuItemId,
                    menuItemName: eventData.menuItemName,
                    currentStock: eventData.currentStock,
                    unit: eventData.unit,
                    threshold: eventData.threshold,
                }})
            }
        })

        set({inventorySubscription: subscription})
    },

    unsubscribeFromInventory: () => {
        const existingSub = get().inventorySubscription

        if(existingSub) {
            existingSub.unsubscribe()
        }

        set({inventorySubscription: null})
    },

    clearLowStockAlert: () => {
        set({lastLowStockAlert: null})
    },

    clearBillRedistribution: () => {
        set({lastBillRedistribution: null})
    },

    subscribeToWaiter: (tenantId: string) => {
        const currentClient = get().stompClient

        if(!currentClient || !currentClient.connected) {
            return
        }

        const existingSub = get().currentSubscription

        if(existingSub) {
            existingSub.unsubscribe()
        }

        const subscription = currentClient.subscribe(`/topic/waiter/${tenantId}`, () => {
            queryClient.invalidateQueries({queryKey: ['dashboardData']})
        })

        set({currentSubscription: subscription})
    },

    disconnect:() => {

        const { stompClient, currentSubscription, waiterSessionSubscription, inventorySubscription } = get()

        if(currentSubscription) {
            currentSubscription.unsubscribe()
        }
        if(waiterSessionSubscription) {
            waiterSessionSubscription.unsubscribe()
        }
        if(inventorySubscription) {
            inventorySubscription.unsubscribe()
        }
        if(stompClient) {
            stompClient.deactivate()
        }

        set({stompClient: null, isConnected: false, currentSubscription: null, waiterSessionSubscription: null, inventorySubscription: null})

    }

}));