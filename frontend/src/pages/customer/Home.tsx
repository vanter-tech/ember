import { Card, CardContent } from '@/components/ui/card'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Utensils } from 'lucide-react'

export const Home = () => {

    

  return (
    <Card className="w-full border-none shadow-sm bg-white rounded-2xl">
      <CardContent className="flex flex-col gap-6 md:flex-row items-center justify-evenly p-6 ">
        <div className="flex items-center gap-5 w-full md:w-auto">
          <div className="relative">
            <Avatar className="h-20 w-20 border-2 border-gray-100">
              <AvatarImage
                src="https://i.pravatar.cc/150?u=alejandro"
                alt="Alejandra"
              />
              <AvatarFallback>AG</AvatarFallback>
            </Avatar>
          </div>
          <div className="flex flex-col">
            <h2 className="text-2xl font-bold text-gray-900">Alejandra</h2>
            <p className="text-sm text-gray-500 mt-1">
              Amante de la gastronomia y mas cosas.
            </p>
          </div>
        </div>
        <div className="w-full md:w-auto flex justify-end">
          <Button
            size={'lg'}
            className="w-full md:w-auto hover:bg-[#660000] px-8 py-6 text-base font-semibold transition-colors"
          >
            <Utensils className="mr-2 h-5 w-5" />
            Entrar a una mesa.
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
