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

const registerSchema = z.object({
    name: z.string().min(1, 'Name is required'),
    email: z.string().email('Invalid email address').min(1, 'Email is required'),
    password: z.string().min(6, 'Password must be at least 6 characters long')
});

type RegisterFormInputs = z.infer<typeof registerSchema>;

export const Register = () => {
    const navigate = useNavigate();
    const {setAuth} = useAuthStore();

    const form = useForm<RegisterFormInputs>({
        resolver: zodResolver(registerSchema),
        defaultValues:{
            name: '',
            email: '',
            password: ''
        }
    });

    const onSubmit = async (data: RegisterFormInputs) => {
        try{
            const response = await authService.register(data);
            setAuth(response);
            toast.success('Registration successful!');
            navigate('/login', { replace: true });
        }catch (error) {
            if (axios.isAxiosError(error) && error.response?.status === 429){
                toast.error('Too many registration attempts. Please try again later.');
            } else{
                toast.error('An error occurred during registration.');
            }
        }


    };

    return (
        <div className="flex items-center justify-center min-h-screen bg-slate-50">
            <Card className="w-full max-w-md shadow-lg">
                <CardHeader>
                    <CardTitle className="text-2xl font-bold">Register</CardTitle>
                    <CardDescription >
                        Create an account to get started.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <Form {...form}>
                        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
                            <FormField
                                control={form.control}
                                name="name"
                                render={({field}) => (
                                    <FormItem>
                                        <FormLabel>Name</FormLabel>
                                        <FormControl>
                                            <Input placeholder="Your name" {...field} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />

                            <FormField
                                control={form.control}
                                name="email"
                                render={({field})=> (
                                    <FormItem>
                                        <FormLabel>Email</FormLabel>
                                        <FormControl>
                                            <Input placeholder="Your email" {...field}/>
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />

                            <FormField
                                control={form.control}
                                name="password"
                                render={({field})=> (
                                    <FormItem>
                                        <FormLabel>Password</FormLabel>
                                        <FormControl>
                                            <Input type="password" placeholder="Your password" {...field}/>
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />

                            <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                                Register
                            </Button>
                            <Button asChild variant="outline" className="w-full text-center">
                                <Link to="/login">Already have an account? Login</Link>
                            </Button>

                        </form>
                    </Form>
                </CardContent>
            </Card>
        </div>
    )
}

