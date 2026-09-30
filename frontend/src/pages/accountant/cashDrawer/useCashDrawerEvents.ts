import { useQuery } from '@tanstack/react-query'
import { cashDrawerService } from '@/lib/api'

export const CASH_DRAWER_QUERY_KEY = ['cashDrawerEvents']

/** Polled (not WebSocket): the shared subscription slot in websocket.ts is already taken — see CashRegisterWebSocketListener. */
export const useCashDrawerEvents = (enabled = true) =>
  useQuery({
    queryKey: CASH_DRAWER_QUERY_KEY,
    queryFn: cashDrawerService.current,
    refetchInterval: 5_000,
    enabled,
  })
