import {Navigate, Outlet} from 'react-router-dom'
import {useAuthStore} from '../store/authStore'

interface ProtectedRouteProps {
    allowedRoles: string[]
}

export const ProtectedRoute = ({ allowedRoles }: ProtectedRouteProps) => {
    const { token, role } = useAuthStore()

    if (!token){
        return <Navigate to="/login" replace />
    }

    if (role && !allowedRoles.includes(role)) {
        return (
            <div className="flex flex-col items-center justify-center h-screen text-center">
                <h1 className="text-6xl font-bold text-red-600">403</h1>
                <h2 className="text-2xl font-semibold mt-4">Acceso Denegado</h2>
                <p className="mt-2 text-gray-600">
                Tu rol ({role}) no tiene los permisos necesarios para entrar a esta zona.
                </p>
            </div>
        )
    }
    
    return <Outlet />
}