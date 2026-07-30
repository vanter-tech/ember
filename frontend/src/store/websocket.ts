import { create } from "zustand";
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuthStore } from "./authStore";



interface WebSocketState {
    stompClient: Client | null,
    isConnected: boolean,
    connect: () => void,
    disconnect: () => void
}

export const useWebsocketStore = create<WebSocketState>((set, get) => ({
    

    stompClient: null,
    isConnected: false,

    connect: () => {
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

    disconnect:() => {

        const currentClient = get().stompClient
        if(currentClient){currentClient.deactivate()}
        set({stompClient: null, isConnected: false})

    }

}));