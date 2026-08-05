import type {components} from '@/lib/backend-types'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
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
export type menuResponse = components['schemas']['MenuDTO']
export type orderItemDTO = components['schemas']['OrderItemDto']
export type participantDTO = components['schemas']['ParticipantDto']

declare global {
  interface Window {
    ENV: {
      EMBW_API_URL?: string
    }
  }
}

export const api = axios.create({
  baseURL: window.ENV?.EMBW_API_URL || 
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

api.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      console.warn('logging out')
      useAuthStore.getState().logout()
    }
    return Promise.reject(error)
  }
)

export const authService = {
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const { data } = await api.post<LoginResponse>('/auth/login', credentials)
    return data
  },

  register: async (details: components['schemas']['RegisterRequest']): Promise<LoginResponse> => {
    const { data } = await api.post<LoginResponse>('/auth/register', details)
    return data
  }
}

export const categoryService = {
  getAll: async (): Promise<CategoryResponse[]> => {
    const { data } = await api.get<CategoryResponse[]>('/catalog/categories')
    return data
  },
  create: async ( details: FormData ): Promise<CategoryResponse> => {
    const {data} = await api.post<CategoryRequest>('/catalog/categories', details,{
      headers:{
        'Content-Type': 'multipart/form-data'
      }
    })
    return data
  },
  delete: async (id: number): Promise<void> => {
    await api.delete<void>(`/catalog/categories/${id}`)
  },
  update: async(id: number, details: FormData): Promise<CategoryResponse> => {
    const {data} = await api.put<CategoryResponse>(`/catalog/categories/${id}`, details,{
      headers:{
        'Content-Type': 'multipart/form-data'
      }
    })

    return data
  }

}

export const menuItemService = {

  getAll: async( id: number ): Promise<MenuItemResponse[]> => {
    const {data} = await api.get<MenuItemResponse[]>(`/catalog/items?id=${id}`)
    return data
  },
  create: async(details: FormData): Promise<MenuItemResponse> => {
    const { data } = await api.post<MenuItemRequest>('/catalog/items', details, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    return data
  },
  toggleAvailability: async(id: number): Promise<MenuItemResponse> => {
    const { data } = await api.patch<MenuItemResponse>(`catalog/items/${id}/availability`)
    return data
  },
  update: async(id: number, details: FormData): Promise<MenuItemResponse> => {
    const { data } = await api.put<MenuItemResponse>(`/catalog/items/${id}`, details, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    return data
  },
  delete: async(id: number): Promise<void> => {
    await api.delete<void>(`/catalog/items/${id}`)
  }
    
}


export const SettingsService = {
  getSettings: async(): Promise<SettingsResponse> => {
    const { data } = await api.get<SettingsResponse>('/settings')
    return data
  },
  updateSettings: async(details: SettingsResponse): Promise<void> => {
    await api.put<SettingsResponse>('/settings', details)
  }
}

export const DashboardService = {
  getDashboardData: async(resturantid: string): Promise<DashboardResponse[]> => {
    const { data } = await api.get<DashboardResponse[]>('/dashboard/status',{
      params: {
        restaurantId: resturantid
      }
    })
    return data
  }
}

export const SessionTableService = {
  createSession: async(tableId: string, maxParticipants: number): Promise<CreateSession> => {
    const { data } = await api.post<CreateSession>('/sessions', {
        tableId: tableId,
        maxParticipants: maxParticipants
    })
    return data
  },

  getQrToken: async( sessionId: string): Promise<{ qrToken: string}> => {
    const { data } = await api.get<{qrToken: string}>(`/sessions/${sessionId}/qr`)
    return data
  },

  sessionInformation: async(sessionId: string): Promise<infoSession> => {
    const { data } = await api.get<infoSession>(`/sessions/${sessionId}`)
    return data
  },

  closeEmptySession: async(sessionId: string): Promise<void> => {
    await api.delete<void>(`sessions/${sessionId}/cancel`)
  },

  calculateBill: async(sessionId: string, splitMethod: "BY_CONSUMPTION" | "EQUAL_PARTS"): Promise<calculateBill> => {
    const { data } = await api.post<calculateBill>(`billing/sessions/${sessionId}/bill`,{ splitMethod })
    return data
  },

  joinSessionViaCode: async(joinCode: string ): Promise<sessionResponse> => {
    const { data } = await api.post<sessionResponse>(`/sessions/join`, { joinCode })
    return data
  },

  addItem: async(sessionId: string, itemId: number): Promise<orderItemDTO> => {
    const { data } = await api.post<orderItemDTO>(`/sessions/${sessionId}/items`, {menuItemId: itemId})
    return data
  }


}

export const menuServices = {
  getMenu: async(): Promise<menuResponse[]> => {
    const { data } = await api.get<menuResponse[]>('/menu')
    return data
  }
}