import { Link } from 'react-router-dom'
import { useTranslation } from '@/lib/i18n'

export const NotFound = () => {
    const { t } = useTranslation('common')
    return (
        <div className="flex flex-col items-center justify-center h-screen text-center">
            <h1 className="text-6xl font-bold text-red-600">404</h1>
            <h2 className="text-2xl font-semibold mt-4">{t('notFoundHeading')}</h2>
            <p className="mt-2 text-gray-600">
                {t('notFoundMessage')}
            </p>
            <Link to="/" className="mt-4 text-blue-500 hover:text-blue-700">
                {t('notFoundBackLink')}
            </Link>
        </div>
    )
}