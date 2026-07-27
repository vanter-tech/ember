import { create } from "zustand";
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

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
        const client = new Client({
            webSocketFactory: () => {
                return new SockJS('http://localhost:8080/api/v1/ws')
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