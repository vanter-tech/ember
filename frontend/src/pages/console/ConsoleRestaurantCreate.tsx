import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import toast from 'react-hot-toast'
import { platformRestaurantService } from '@/lib/platformApi'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { ConsolePageHeader } from '@/components/console/ConsolePageHeader'

const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).+$/

const createSchema = z.object({
  name: z.string().min(1, 'El nombre es obligatorio'),
  slug: z
    .string()
    .min(1, 'El slug es obligatorio')
    .regex(
      /^[a-z0-9]+(-[a-z0-9]+)*$/,
      'El slug debe ser minúsculas y números separados por guiones'
    ),
  adminName: z.string().min(1, 'El nombre del admin es obligatorio'),
  adminEmail: z
    .string()
    .email('El email del admin no es válido')
    .min(1, 'El email del admin es obligatorio'),
  adminPassword: z
    .string()
    .min(8, 'La contraseña debe tener entre 8 y 128 caracteres')
    .max(128, 'La contraseña debe tener entre 8 y 128 caracteres')
    .regex(passwordPattern, 'La contraseña necesita mayúscula, minúscula, número y símbolo'),
})

type CreateFormInputs = z.infer<typeof createSchema>

export default function ConsoleRestaurantCreate() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const form = useForm<CreateFormInputs>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      name: '',
      slug: '',
      adminName: '',
      adminEmail: '',
      adminPassword: '',
    },
  })

  const createRestaurant = useMutation({
    mutationFn: (data: CreateFormInputs) => platformRestaurantService.create(data),
    onSuccess: (restaurant) => {
      queryClient.invalidateQueries({ queryKey: ['platformRestaurants'] })
      toast.success('Restaurante creado')
      navigate(`/console/restaurants/${restaurant.id}`, { replace: true })
    },
    onError: (error) => {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.error('El slug o el email del admin ya están en uso', {
          id: 'console-create-error',
          duration: 3000,
        })
      } else {
        toast.error('No se pudo crear el restaurante', {
          id: 'console-create-error',
          duration: 3000,
        })
      }
    },
  })

  const onSubmit = (data: CreateFormInputs) => createRestaurant.mutate(data)

  return (
    <div className="flex flex-col gap-4">
      <ConsolePageHeader title="Nuevo restaurante" />
      <Link to="/console/restaurants" className="text-sm text-[#8c1717] hover:underline">
        &larr; Restaurantes
      </Link>

      <Card className="max-w-xl">
        <CardContent className="pt-6">
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
              <FormField
                control={form.control}
                name="name"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Nombre del restaurante</FormLabel>
                    <FormControl>
                      <Input placeholder="Ember Grill" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="slug"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Slug</FormLabel>
                    <FormControl>
                      <Input placeholder="ember-grill" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="adminName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Nombre del administrador</FormLabel>
                    <FormControl>
                      <Input placeholder="Jane Doe" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="adminEmail"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email del administrador</FormLabel>
                    <FormControl>
                      <Input
                        type="email"
                        placeholder="admin@example.com"
                        autoComplete="email"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="adminPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Contraseña inicial</FormLabel>
                    <FormControl>
                      <Input
                        type="password"
                        autoComplete="new-password"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <Button
                type="submit"
                className="w-full"
                disabled={createRestaurant.isPending}
              >
                {createRestaurant.isPending ? 'Creando...' : 'Crear restaurante'}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  )
}
