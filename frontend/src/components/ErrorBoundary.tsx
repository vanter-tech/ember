import { Component, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center h-screen text-center">
          <h1 className="text-6xl font-bold text-red-600">Error</h1>
          <h2 className="text-2xl font-semibold mt-4">Algo salió mal</h2>
          <p className="mt-2 text-gray-600">
            Ocurrió un error inesperado. Intenta recargar la página.
          </p>
        </div>
      )
    }

    return this.props.children
  }
}
