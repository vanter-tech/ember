import type {components} from '@/lib/backend-types'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
export type LoginRequest = components['schemas']['LoginRequest']
export type LoginResponse = components['schemas']['AuthResponse']
export type TableResponse = components['schemas']['RestaurantTableResponse']

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
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

export const tableService = {
  getAll: async (): Promise<TableResponse[]> => {
    const { data } = await api.get<TableResponse[]>('/catalog/tables')
    return data
  },
}
