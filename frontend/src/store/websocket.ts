import { create } from "zustand";
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from "./authStore";
import { useSessionStore } from "./sessionStore";
import { queryClient } from "@/queryClient";

interface WebSocketState {
    stompClient: Client | null,
    isConnected: boolean,
    currentSubscription: any | null,
    subscribeToSession:(sessionId: string) => void,
    connect: () => void,
    disconnect: () => void
}

export const useWebsocketStore = create<WebSocketState>((set, get) => ({
    

    stompClient: null,
    isConnected: false,
    currentSubscription: null,

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

        client.activate()

        set({stompClient: client})
    },

    subscribeToSession: (sessionId: string) => {
        const currentClient = get().stompClient

        if(!currentClient || !currentClient.connected) {
            console.error("WebSocket is not connected")
            return
        }

        const existingSub = get().currentSubscription

        if(existingSub) {
            existingSub.unsubscribe()
        }
        
        const subscription = currentClient.subscribe(`/topic/session/${sessionId}`, (msg) => {
            const eventData = JSON.parse(msg.body)
            console.log('Message received:', eventData)
            if(eventData.type === 'PARTICIPANT_JOINED'){
                const newParticipants = {
                    userId: eventData.userId,
                    name: eventData.userName
                }
                useSessionStore.getState().addParticipant(newParticipants)
            }
            if(eventData.type === 'ITEM_ADDED'){
                useSessionStore.getState().updateSession({items: eventData.sessionItems})
            }
            if(eventData.type === 'ITEM_DELETED'){
                const currentState = useSessionStore.getState()
                const updateItems = currentState.items?.filter((item) => item.id !== eventData.orderItemId)
                useSessionStore.getState().updateSession({items: updateItems})
            }
            if(eventData.type === 'SESSION_CLOSED'){
                useSessionStore.getState().clearSession()
                queryClient.removeQueries({queryKey: ['sessionDetails']})
                window.location.href = '/customer/home'
            }
        })

        
        set({currentSubscription: subscription})
    },

    disconnect:() => {
        
        const { stompClient, currentSubscription } = get()

        if(currentSubscription) {
            currentSubscription.unsubscribe()
        }
        if(stompClient) {
            stompClient.deactivate()
        }

        set({stompClient: null, isConnected: false, currentSubscription: null})

    }

}));