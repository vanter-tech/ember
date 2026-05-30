import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate, Link } from 'react-router-dom'
import axios from 'axios'
import toast from 'react-hot-toast'
import { useAuthStore } from '../store/authStore'
import { authService } from '@/lib/api'

import { Button } from './ui/button'
import { Input } from './ui/input'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from './ui/card'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from './ui/form'

const loginSchema = z.object({
    email: z.string().email('Invalid email address').min(1, 'Email is required'),
    password: z.string().min(6, 'Password must be at least 6 characters'),
});

type LoginFormInputs = z.infer<typeof loginSchema>;

export const Login = () => {
    const navigate = useNavigate();
    const {setAuth} = useAuthStore();

    const form = useForm<LoginFormInputs>({
        resolver: zodResolver(loginSchema),
        defaultValues:{
            email: '',
            password: '',
        }
    });

    const onSubmit = async (data: LoginFormInputs) => {
        try {
            const response = await authService.login(data);
            setAuth(response);
            toast.success('Login successful!');
            switch (response.role) {
                case 'ADMIN':
                    navigate('/admin', { replace: true });
                    break;
                case 'CUSTOMER':
                    navigate('/customer', { replace: true });
                    break;
                case 'WAITER':
                    navigate('/waiter', { replace: true });
                    break;
                case 'KITCHEN':
                    navigate('/kitchen', { replace: true });
                    break;
                default:
                    break;
            }
        } catch (error) {
            if (axios.isAxiosError(error) && error.response?.status === 401) {
                toast.error('Unauthorized', {
                    id: 'login-error',
                    duration: 3000,
                });
            } else if (axios.isAxiosError(error) && error.response?.status === 429) {
                toast.error('Too many login attempts. Please try again later.', {
                    id: 'login-error',
                    duration: 3000,
                });
            } else {
                toast.error('Login failed', {
                    id: 'login-error',
                    duration: 3000,
                });
            }
        }
    };

    return(
        <div className="flex items-center justify-center min-h-screen bg-slate-50">
            <Card className = "w-full max-w-md shadow-lg">
                <CardHeader>
                    <CardTitle className = "text-3xl font-bold">
                        Welcome to Ember!<br />Please log in to continue.
                    </CardTitle>
                    <CardDescription>
                        Type your email and password to access your account.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <Form {...form}>
                        <form onSubmit = {form.handleSubmit(onSubmit)} className="space-y-6">
                            <FormField
                                control={form.control}
                                name="email"
                                render={({field}) => (
                                    <FormItem>
                                        <FormLabel>Email</FormLabel>
                                        <FormControl>
                                            <Input placeholder="Enter your email" {...field} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />

                            <FormField
                                control={form.control}
                                name="password"
                                render={({field}) => (
                                    <FormItem>
                                        <FormLabel>Password</FormLabel>
                                        <FormControl>
                                            <Input placeholder="Enter your password" type="password" {...field} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />

                            <Button
                                type="submit"
                                className="w-full"
                                disabled={form.formState.isSubmitting}
                            >
                                {form.formState.isSubmitting ? 'Logging in...' : 'Login'}

                            </Button>
                            <Button asChild variant="outline" className="w-full text-center">
                                <Link to="/register">Register</Link>
                            </Button>
                        </form>
                    </Form>
                </CardContent>
            </Card>
        </div>
    )
}
