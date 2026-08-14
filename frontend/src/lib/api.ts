import type { components } from '@/lib/backend-types'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { useUIStore } from '@/store/uiStore'
export type LoginRequest = components['schemas']['LoginRequest']
export type LoginResponse = components['schemas']['AuthResponse']
export type CategoryResponse = components['schemas']['CategoryResponse']
export type CategoryRequest = components['schemas']['CategoryRequest']
export type MenuItemResponse = components['schemas']['MenuItemResponse']
export type MenuItemRequest = components['schemas']['MenuItemRequest']
export type SettingsResponse = components['schemas']['SettingsPayload']
export type DashboardResponse = components['schemas']['TableStatusResponse']
export type CreateSession = components['schemas']['SessionCreatedResponse']
export type infoSession = components['schemas']['SessionDetailResponseDto']
export type calculateBill = components['schemas']['CalculateBillRequest']
export type JoinSessionCode = components['schemas']['JoinSessionCodeRequest']
export type sessionResponse = components['schemas']['Session']
export type joinSessionResponse = components['schemas']['JoinSessionResponse']
export type menuResponse = components['schemas']['MenuDTO']
export type orderItemDTO = components['schemas']['OrderItemDto']
export type participantDTO = components['schemas']['ParticipantDto']
export type tableStatus = components['schemas']['SessionStatusDto']
export type kitchenOrdersDisplayByTables = components['schemas']['KitchenDisplayEntry']
export type kitchenOrders = components['schemas']['KitchenOrder']
export type OrderItemStatus = components['schemas']['UpdateItemStatusRequest']['status']

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export type RestaurantResponse = components['schemas']['Restaurant']
export type UpdateRestaurantPlanRequest = components['schemas']['UpdateRestaurantPlanRequest']

export interface PublicBranding {
  slug: string
  businessName: string
  primaryThemeColor: string
  openingTime: string
  closingTime: string
}

// Hand-written like Page<T> above: the analytics DTOs (task-5.13-5.16) predate the last
// backend-types.ts regen, so they aren't in components['schemas'] yet.
export interface AnalyticsSummaryResponse {
  totalRevenue: number
  activeSessions: number
  averageOrderValue: number
  paidBillCount: number
  from: string
  to: string
}

declare global {
  interface Window {
    ENV: {
      EMBW_API_URL?: string
      EMBW_WS_URL?: string
    }
  }
}

export const api = axios.create({
  baseURL:
    window.ENV?.EMBW_API_URL ||
    import.meta.env.VITE_API_URL ||
    'http://localhost:8080/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().token

    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Matches the exact ProblemDetail.detail strings written by SecurityConfig.jwtAuthFilter's
// writeSuspendedTenantResponse (backend). @PreAuthorize role denials also return a 403, but with
// detail "Access denied" (GlobalExceptionHandler.handleAccessDenied) — that path is left alone.
const isTenantSuspendedDetail = (detail: unknown): detail is string =>
  typeof detail === 'string' &&
  (detail.startsWith('This tenant account is') || detail === 'Tenant account not found.')

api.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      useAuthStore.getState().logout()
    }
    if (error.response && error.response.status === 403) {
      const detail = error.response.data?.detail
      if (isTenantSuspendedDetail(detail)) {
        useUIStore.getState().openModal('TENANT_SUSPENDED', { detail })
      }
    }
    return Promise.reject(error)
  }
)

export const authService = {
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const { data } = await api.post<LoginResponse>('/auth/login', credentials)
    return data
  },

  register: async (
    details: components['schemas']['RegisterRequest']
  ): Promise<LoginResponse> => {
    const { data } = await api.post<LoginResponse>('/auth/register', details)
    return data
  },
}

export const categoryService = {
  getAll: async (): Promise<CategoryResponse[]> => {
    const { data } = await api.get<CategoryResponse[]>('/catalog/categories')
    return data
  },
  create: async (details: FormData): Promise<CategoryResponse> => {
    const { data } = await api.post<CategoryRequest>(
      '/catalog/categories',
      details,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    )
    return data
  },
  delete: async (id: number): Promise<void> => {
    await api.delete<void>(`/catalog/categories/${id}`)
  },
  update: async (id: number, details: FormData): Promise<CategoryResponse> => {
    const { data } = await api.put<CategoryResponse>(
      `/catalog/categories/${id}`,
      details,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    )

    return data
  },
}

export const menuItemService = {
  getAll: async (id: number): Promise<Page<MenuItemResponse>> => {
    const { data } = await api.get<Page<MenuItemResponse>>(
      `/catalog/items?id=${id}`
    )
    return data
  },
  create: async (details: FormData): Promise<MenuItemResponse> => {
    const { data } = await api.post<MenuItemRequest>(
      '/catalog/items',
      details,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    )

    return data
  },
  toggleAvailability: async (id: number): Promise<MenuItemResponse> => {
    const { data } = await api.patch<MenuItemResponse>(
      `catalog/items/${id}/availability`
    )
    return data
  },
  update: async (id: number, details: FormData): Promise<MenuItemResponse> => {
    const { data } = await api.put<MenuItemResponse>(
      `/catalog/items/${id}`,
      details,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      }
    )
    return data
  },
  delete: async (id: number): Promise<void> => {
    await api.delete<void>(`/catalog/items/${id}`)
  },
}

export const SettingsService = {
  getSettings: async (): Promise<SettingsResponse> => {
    const { data } = await api.get<SettingsResponse>('/settings')
    return data
  },
  updateSettings: async (details: SettingsResponse): Promise<void> => {
    await api.put<SettingsResponse>('/settings', details)
  },
}

export const DashboardService = {
  getDashboardData: async (): Promise<DashboardResponse[]> => {
    const { data } = await api.get<DashboardResponse[]>('/dashboard/status')
    return data
  },
}

export const SessionTableService = {
  createSession: async (
    tableId: string,
    maxParticipants: number
  ): Promise<CreateSession> => {
    const { data } = await api.post<CreateSession>('/sessions', {
      tableId: tableId,
      maxParticipants: maxParticipants,
    })
    return data
  },

  getQrToken: async (sessionId: string): Promise<{ qrToken: string }> => {
    const { data } = await api.get<{ qrToken: string }>(
      `/sessions/${sessionId}/qr`
    )
    return data
  },

  sessionInformation: async (sessionId: string): Promise<infoSession> => {
    const { data } = await api.get<infoSession>(`/sessions/${sessionId}`)
    return data
  },

  sessionStatus: async(sessionId: string): Promise<tableStatus> => {
    const { data } = await api.get<tableStatus>(`/sessions/${sessionId}/status`)
    return data
  },

  closeEmptySession: async (sessionId: string): Promise<void> => {
    await api.delete<void>(`sessions/${sessionId}/cancel`)
  },

  calculateBill: async (
    sessionId: string,
    splitMethod: 'BY_CONSUMPTION' | 'EQUAL_PARTS'
  ): Promise<calculateBill> => {
    const { data } = await api.post<calculateBill>(
      `billing/sessions/${sessionId}/bill`,
      { splitMethod }
    )
    return data
  },

  joinSessionViaCode: async (joinCode: string): Promise<joinSessionResponse> => {
    const { data } = await api.post<joinSessionResponse>(`/sessions/join`, {
      joinCode,
    })
    return data
  },

  addItem: async (sessionId: string, itemId: number): Promise<orderItemDTO> => {
    const { data } = await api.post<orderItemDTO>(
      `/sessions/${sessionId}/items`,
      { menuItemId: itemId }
    )
    return data
  },

  confirmMyOrders: async(sessionId: string, userId: string): Promise<void> => {
     await api.post<void>(`/sessions/${sessionId}/participants/${userId}/confirm`)
  },

  deleteItem: async (sessionId: string, itemId: string): Promise<void> => {
    await api.delete<void>(`sessions/${sessionId}/items/${itemId}`)
  },
}

export const menuServices = {
  getMenu: async (): Promise<menuResponse[]> => {
    const { data } = await api.get<menuResponse[]>('/menu')
    return data
  }, 
}

export const kitchenServices = {
  getOrdersByTables: async (): Promise<kitchenOrdersDisplayByTables[]> => {
    const { data } = await api.get<kitchenOrdersDisplayByTables[]>('/kitchen/display')
    return data
  },
  getOrders: async(): Promise<Page<kitchenOrders>> => {
    const { data } = await api.get<Page<kitchenOrders>>('/kitchen/orders')
    return data
  },
  updateItemStatus: async (orderId: string, itemId: string, status: OrderItemStatus): Promise<kitchenOrders> => {
    const { data } = await api.patch<kitchenOrders>(
      `/kitchen/orders/${orderId}/items/${itemId}/status`,
      { status }
    )
    return data
  }

}

export const restaurantAdminService = {
  getPlan: async (): Promise<RestaurantResponse> => {
    const { data } = await api.get<RestaurantResponse>('/admin/restaurant')
    return data
  },
  updatePlan: async (plan: UpdateRestaurantPlanRequest['plan']): Promise<RestaurantResponse> => {
    const { data } = await api.patch<RestaurantResponse>('/admin/restaurant/plan', { plan })
    return data
  },
}

export const publicService = {
  getBranding: async (slug: string): Promise<PublicBranding> => {
    const { data } = await api.get<PublicBranding>(
      `/public/restaurants/${slug}/branding`
    )
    return data
  },
}

export const analyticsService = {
  getSummary: async (from?: string, to?: string): Promise<AnalyticsSummaryResponse> => {
    const { data } = await api.get<AnalyticsSummaryResponse>(
      '/admin/analytics/summary',
      { params: { from, to } }
    )
    return data
  },
}
