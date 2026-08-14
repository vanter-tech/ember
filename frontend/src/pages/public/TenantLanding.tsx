import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { publicService } from '@/lib/api'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

export const TenantLanding = () => {
  const { slug } = useParams()

  const {
    data: branding,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['publicBranding', slug],
    queryFn: () => publicService.getBranding(slug!),
    enabled: !!slug,
    retry: false,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-slate-50">
        <Loader2 className="h-8 w-8 animate-spin text-[#920703]" />
      </div>
    )
  }

  if (isError || !branding) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen text-center bg-slate-50">
        <h1 className="text-2xl font-semibold">Restaurante no encontrado</h1>
        <p className="mt-2 text-gray-600">
          No pudimos encontrar un restaurante con esa dirección.
        </p>
      </div>
    )
  }

  const accentColor = branding.primaryThemeColor || '#920703'

  return (
    <div className="flex items-center justify-center min-h-screen bg-slate-50">
      <Card className="w-full max-w-md shadow-lg">
        <CardHeader>
          <CardTitle
            className="text-3xl text-center font-bold"
            style={{ color: accentColor }}
          >
            {branding.businessName}
          </CardTitle>
          {(branding.openingTime || branding.closingTime) && (
            <CardDescription className="text-center">
              Horario: {branding.openingTime} - {branding.closingTime}
            </CardDescription>
          )}
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <Button asChild className="w-full" style={{ backgroundColor: accentColor }}>
            <Link to="/login">Iniciar sesión</Link>
          </Button>
          <Button asChild variant="outline" className="w-full">
            <Link to="/register">Registrarme</Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
