import { create } from "zustand";
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from "./authStore";
import { useSessionStore } from "./sessionStore";


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

        const client = new Client({
            webSocketFactory: () => {
                return new SockJS('http://localhost:8080/v1/ws')
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
                useSessionStore.getState().addParticipant(eventData)
            }
            if(eventData.type === 'ITEM_ADDED'){
                useSessionStore.getState().updateSession({items: eventData.sessionItems})
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