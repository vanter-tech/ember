import { Link } from 'react-router-dom'

export const NotFound = () => {
    return (
        <div className="flex flex-col items-center justify-center h-screen text-center">
            <h1 className="text-6xl font-bold text-red-600">404</h1>
            <h2 className="text-2xl font-semibold mt-4">Página No Encontrada</h2>
            <p className="mt-2 text-gray-600">
                La página que estás buscando no existe.
            </p>
            <Link to="/" className="mt-4 text-blue-500 hover:text-blue-700">
                Volver a la página principal
            </Link>
        </div>
    )
}