import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from '@/lib/i18n'
import { JoinShell } from '@/pages/customer/components/JoinShell'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'

/** Login-style 404 (landing backdrop, brand footer, language picker); "/" sends each role to its own home. */
export const NotFound = () => {
    const { t } = useTranslation('common')
    const { pathname } = useLocation()

    return (
        <JoinShell>
            <Card className="relative z-10 w-full max-w-md gap-6 py-10 text-center shadow-lg animate-in fade-in slide-in-from-bottom-2 duration-300">
                <CardHeader className="px-8">
                    <p className="text-7xl font-bold tracking-tight text-[#920703]" aria-hidden="true">
                        404
                    </p>
                    <h1 className="mt-2 text-2xl font-bold text-zinc-800">{t('notFoundHeading')}</h1>
                    <p className="text-sm text-muted-foreground">{t('notFoundMessage')}</p>
                </CardHeader>
                <CardContent className="flex flex-col items-center gap-5 px-8">
                    <code
                        data-testid="not-found-path"
                        className="max-w-full truncate rounded-md bg-zinc-100 px-3 py-1 text-xs text-zinc-600"
                    >
                        {pathname}
                    </code>
                    <Button asChild className="w-full">
                        <Link to="/">{t('notFoundBackLink')}</Link>
                    </Button>
                </CardContent>
            </Card>
        </JoinShell>
    )
}
