import type { OrderItemStatus } from '@/lib/api'

export const NEXT_STATUS: Record<OrderItemStatus, OrderItemStatus | null> = {
  DRAFT: 'PENDING',
  PENDING: 'PREPARING',
  PREPARING: 'READY',
  READY: 'DELIVERED',
  DELIVERED: null,
}

export const STATUS_LABEL: Record<OrderItemStatus, string> = {
  DRAFT: 'Borrador',
  PENDING: 'Pendiente',
  PREPARING: 'Preparando',
  READY: 'Listo',
  DELIVERED: 'Entregado',
}

export const NEXT_ACTION_LABEL: Record<OrderItemStatus, string> = {
  DRAFT: 'Confirmar',
  PENDING: 'Iniciar preparación',
  PREPARING: 'Marcar listo',
  READY: 'Entregar',
  DELIVERED: '',
}
