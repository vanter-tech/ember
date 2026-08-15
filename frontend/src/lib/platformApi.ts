import axios from 'axios'
import { usePlatformAuthStore } from '@/store/platformAuthStore'

// Hand-typed: backend-types.ts is generated from `/v3/api-docs` and has never been regenerated
// since the platform/** endpoints (EMB-PC-01+) were added, so no components['schemas'] exist for
// them yet. Mirrors PlatformLoginRequest/PlatformAuthResponse/PlatformPasswordChangeRequest
// (backend/src/main/java/com/vanter/ember/platform/model/dto).
export interface PlatformLoginRequest {
  email: string
  password: string
}

export interface PlatformAuthResponse {
  token?: string
  operatorId?: string
  name?: string
  email?: string
}

export interface PlatformPasswordChangeRequest {
  currentPassword: string
  newPassword: string
}

// Mirrors PlatformRestaurantSummaryResponse (platform/model/dto).
export interface PlatformRestaurantSummary {
  id: string
  name: string
  slug: string
  plan: 'FREE' | 'STARTER' | 'PRO' | 'ENTERPRISE'
  status: 'ACTIVE' | 'SUSPENDED' | 'INACTIVE'
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export const platformApi = axios.create({
  baseURL:
    window.ENV?.EMBW_API_URL ||
    import.meta.env.VITE_API_URL ||
    'http://localhost:8080/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

platformApi.interceptors.request.use(
  (config) => {
    const token = usePlatformAuthStore.getState().token

    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

platformApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      usePlatformAuthStore.getState().logout()
    }
    return Promise.reject(error)
  }
)

export const platformAuthService = {
  login: async (credentials: PlatformLoginRequest): Promise<PlatformAuthResponse> => {
    const { data } = await platformApi.post<PlatformAuthResponse>(
      '/platform/auth/login',
      credentials
    )
    return data
  },

  changePassword: async (details: PlatformPasswordChangeRequest): Promise<void> => {
    await platformApi.patch<void>('/platform/auth/password', details)
  },
}

export const platformRestaurantService = {
  getAll: async (page = 0, size = 10): Promise<Page<PlatformRestaurantSummary>> => {
    const { data } = await platformApi.get<Page<PlatformRestaurantSummary>>(
      '/platform/restaurants',
      { params: { page, size } }
    )
    return data
  },
}
