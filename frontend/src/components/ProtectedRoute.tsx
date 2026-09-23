import {Navigate, Outlet} from 'react-router-dom'
import {useAuthStore} from '../store/authStore'
import {ForcePasswordChangeModal} from '../pages/auth/ForcePasswordChangeModal'

interface ProtectedRouteProps {
    allowedRoles: string[]
}

export const ProtectedRoute = ({ allowedRoles }: ProtectedRouteProps) => {
    const { token, role, mustChangePassword } = useAuthStore()

    if (!token){
        return <Navigate to="/login" replace />
    }

    if (!role) {
        return <Navigate to="/login" replace />
    }

    // F-25: an operator-issued temp password blocks every protected route until the caller sets
    // a real one — no route is safe to render with a password only an operator (and whoever they
    // told) currently knows.
    if (mustChangePassword) {
        return <ForcePasswordChangeModal />
    }

    if (!allowedRoles.includes(role)) {
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