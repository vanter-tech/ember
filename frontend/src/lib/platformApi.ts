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
