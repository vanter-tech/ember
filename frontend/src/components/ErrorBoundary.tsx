import { Component, type ReactNode } from 'react'
import { useTranslation } from '@/lib/i18n'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

const ErrorFallback = () => {
  const { t } = useTranslation('common')
  return (
    <div className="flex flex-col items-center justify-center h-screen text-center">
      <h1 className="text-6xl font-bold text-red-600">{t('errorBoundaryTitle')}</h1>
      <h2 className="text-2xl font-semibold mt-4">{t('errorBoundaryHeading')}</h2>
      <p className="mt-2 text-gray-600">
        {t('errorBoundaryMessage')}
      </p>
    </div>
  )
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  render() {
    if (this.state.hasError) {
      return <ErrorFallback />
    }

    return this.props.children
  }
}
