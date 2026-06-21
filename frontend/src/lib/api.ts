import type {components} from '@/lib/backend-types'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'
import { data } from 'react-router-dom'
import { id } from 'zod/v4/locales'
export type LoginRequest = components['schemas']['LoginRequest']
export type LoginResponse = components['schemas']['AuthResponse']
export type TableResponse = components['schemas']['RestaurantTableResponse']
export type CategoryResponse = components['schemas']['CategoryResponse']
export type CategoryRequest = components['schemas']['CategoryRequest']

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
  'http://localhost:8080/api/v1',
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