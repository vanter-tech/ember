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
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'

const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).+$/

const createSchema = z.object({
  name: z.string().min(1, 'Restaurant name is required'),
  slug: z
    .string()
    .min(1, 'Slug is required')
    .regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, 'Slug must be lowercase alphanumeric with single hyphens'),
  adminName: z.string().min(1, 'Admin name is required'),
  adminEmail: z.string().email('Admin email must be valid').min(1, 'Admin email is required'),
  adminPassword: z
    .string()
    .min(8, 'Password must be between 8 and 128 characters')
    .max(128, 'Password must be between 8 and 128 characters')
    .regex(
      passwordPattern,
      'Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character'
    ),
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
      toast.success('Restaurant created')
      navigate(`/console/restaurants/${restaurant.id}`, { replace: true })
    },
    onError: (error) => {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        toast.error('Slug or admin email already in use', {
          id: 'console-create-error',
          duration: 3000,
        })
      } else {
        toast.error('Failed to create restaurant', {
          id: 'console-create-error',
          duration: 3000,
        })
      }
    },
  })

  const onSubmit = (data: CreateFormInputs) => createRestaurant.mutate(data)

  return (
    <div className="flex flex-col gap-4">
      <Link to="/console/restaurants" className="text-sm text-blue-600 hover:underline">
        &larr; Restaurantes
      </Link>

      <Card className="max-w-xl">
        <CardHeader>
          <CardTitle className="text-2xl">Nuevo restaurante</CardTitle>
        </CardHeader>
        <CardContent>
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
